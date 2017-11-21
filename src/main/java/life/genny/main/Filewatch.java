package life.genny.main;

import java.nio.file.*;
import static java.nio.file.StandardWatchEventKinds.*;
import static java.nio.file.LinkOption.*;
import java.nio.file.attribute.*;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.io.*;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.drools.compiler.compiler.DrlParser;
import org.drools.compiler.compiler.DroolsParserException;
import org.drools.compiler.lang.descr.PackageDescr;
import org.drools.compiler.lang.descr.RuleDescr;
import org.jboss.resteasy.client.ClientRequest;
import org.kie.api.builder.Message;
import org.kie.api.builder.Results;
import org.kie.api.io.ResourceType;
import org.kie.internal.builder.conf.LanguageLevelOption;
import org.kie.internal.utils.KieHelper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import life.genny.qwanda.message.QDataRuleMessage;
import life.genny.qwanda.rule.Rule;

public class Filewatch {

	private final WatchService watcher;
	private final Map<WatchKey, Path> keys;
	private final boolean recursive;
	private boolean trace = false;
	private String eventBusAddress;
	private String token;

	Vertx vertx;
	private Buffer data = null;

	static Gson gson = new GsonBuilder()
			.registerTypeAdapter(LocalDateTime.class, new JsonDeserializer<LocalDateTime>() {
				@Override
				public LocalDateTime deserialize(final JsonElement json, final Type type,
						final JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
					return ZonedDateTime.parse(json.getAsJsonPrimitive().getAsString()).toLocalDateTime();
				}

				public JsonElement serialize(final LocalDateTime date, final Type typeOfSrc,
						final JsonSerializationContext context) {
					return new JsonPrimitive(date.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)); // "yyyy-mm-dd"
				}
			}).create();

	@SuppressWarnings("unchecked")
	static <T> WatchEvent<T> cast(WatchEvent<?> event) {
		return (WatchEvent<T>) event;
	}

	/**
	 * Register the given directory with the WatchService
	 */
	private void register(Path dir) throws IOException {
		WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
		if (trace) {
			Path prev = keys.get(key);
			if (prev == null) {
				System.out.format("register: %s\n", dir);
			} else {
				if (!dir.equals(prev)) {
					System.out.format("update: %s -> %s\n", prev, dir);
				}
			}
		}
		keys.put(key, dir);
	}

	/**
	 * Register the given directory, and all its sub-directories, with the
	 * WatchService.
	 */
	private void registerAll(final Path start) throws IOException {
		// register directory and sub-directories
		Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				register(dir);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	/**
	 * Creates a WatchService and registers the given directory
	 */
	Filewatch(Path dir, boolean recursive, String eventBusAddress, String token) throws IOException {
		this.watcher = FileSystems.getDefault().newWatchService();
		this.keys = new HashMap<WatchKey, Path>();
		this.recursive = recursive;
		this.eventBusAddress = eventBusAddress;
		this.token = token;
		VertxOptions vertxOptions = new VertxOptions();
		vertxOptions.setBlockedThreadCheckInterval(2147483647);
		vertx = Vertx.vertx(vertxOptions);
		if (recursive) {
			System.out.format("Scanning %s ...\n", dir);
			registerAll(dir);
			System.out.println("Done.");
		} else {
			register(dir);
		}

		// enable trace after initial registration
		this.trace = true;
	}

	/**
	 * Process all events for keys queued to the watcher
	 */
	public void processEvents() {
		for (;;) {

			// wait for key to be signalled
			WatchKey key;
			try {
				key = watcher.take();
			} catch (InterruptedException x) {
				return;
			}

			Path dir = keys.get(key);
			if (dir == null) {
				System.err.println("WatchKey not recognized!!");
				continue;
			}

			for (WatchEvent<?> event : key.pollEvents()) {
				WatchEvent.Kind kind = event.kind();

				// TBD - provide example of how OVERFLOW event is handled
				if (kind == OVERFLOW) {
					continue;
				}

				// Context for directory entry event is the file name of entry
				WatchEvent<Path> ev = cast(event);
				Path name = ev.context();
				Path child = dir.resolve(name);

				// print out event

				process(event.kind().name(), child);

				// if directory is created, and watching recursively, then
				// register it and its sub-directories
				if (recursive && (kind == ENTRY_CREATE)) {
					try {
						if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
							registerAll(child);
						}
					} catch (IOException x) {
						// ignore to keep sample readbale
					}
				}
			}

			// reset key and remove from set if directory no longer accessible
			boolean valid = key.reset();
			if (!valid) {
				keys.remove(key);

				// all directories are inaccessible
				if (keys.isEmpty()) {
					break;
				}
			}
		}
	}

	private void process(String fileEventName, Path child) {
		if (!child.toString().toLowerCase().endsWith(".swp")) {
			System.out.format("%s: %s\n", fileEventName, child);
			if (("ENTRY_MODIFY".equalsIgnoreCase(fileEventName)) || ("ENTRY_CREATE".equalsIgnoreCase(fileEventName))) {

				getAndSendFile(child.toString());
			}
			if (("ENTRY_DELETE".equalsIgnoreCase(fileEventName))) {
				System.out.println("Need to delete this file e.g Rule");
			}
		}
	}

	public void getAndSendFile(String filename) {

		vertx.fileSystem().readFile(filename, d -> {
			if (!d.failed()) {
				try {
					data = d.result();
					// swap in any TTOKENN
					byte[] bytes = data.getBytes();
					String fileStr = new String(bytes);

					if (isRuleFile(fileStr)) {
						System.out.println("filename is a rulefile!");
						if (isValidRule(fileStr)) {
							System.out.println("Rule is Valid");

						//	if (token != null) {
								processRule(fileStr);
						//	}

						} else {
							System.out.println("Rule is invalid!");
						}
					} else {
						if (fileStr.contains("TTOKENN")) {
							fileStr = fileStr.replaceAll("TTOKENN", token.trim());
							data = Buffer.buffer(fileStr);
							System.out.println(filename + " required token refresh");
						}
						try {
							runVertx();
							vertx.close();

						} catch (SocketException e) {
							e.printStackTrace();
						} catch (UnknownHostException e) {
							e.printStackTrace();
						}

					}

				} catch (DecodeException dE) {
					data = d.result();
				}

			} else {
				System.err.println("Error reading  file!");
			}
		});
	}

	public void runVertx() throws SocketException, UnknownHostException {

		vertx.executeBlocking(future -> {

			Buffer buf = data.getBuffer(0, data.length());

			try {
				ClientRequest request = new ClientRequest(eventBusAddress);
				// List<String> headerStringList = new ArrayList<String>();
				// headerStringList.add("Bearer "+token);
				// request.getHeaders().put("Authorization", headerStringList);
				request.getQueryParameters().add("token", token);
				request.accept("application/json");
				request.body("text/plain", buf.toString());
				request.post(String.class);
			} catch (MalformedURLException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			}

		}, res -> {
			if (res.succeeded()) {
				System.out.println("File Sent");
				;
			}
		});

	}

	private boolean isValidRule(String ruleText) {
		boolean ret = true;
		Map<String, String> drls = new HashMap<String, String>();
		drls.put("rule1", ruleText);

		KieHelper kieHelper = new KieHelper();
		for (String ruleId : drls.keySet()) {
			kieHelper.addContent(drls.get(ruleId), ResourceType.DRL);
		}
		Results results = kieHelper.verify();
		for (Message message : results.getMessages()) {
			System.out.println(">> Message ({}): {}" + message.getLevel() + message.getText());
		}

		if (results.hasMessages(Message.Level.ERROR)) {
			System.out.println("There are errors in the rule.");
			ret = false;
		}
		return ret;
	}

	private boolean isRuleFile(String fileText) {
		String rulename = "";
		Pattern regex = Pattern.compile("(rule\\s+\"(.*)\"\\s+when)", Pattern.DOTALL);
		Matcher regexMatcher = regex.matcher(fileText);
		if (regexMatcher.find()) {
			return true;
		}

		return false;
	}

	private void processRule(String ruleText) {
		DrlParser parser = new DrlParser(LanguageLevelOption.DRL6);
		try {
			PackageDescr pkgDescr = parser.parse(null, ruleText);
			System.out.println(pkgDescr.getGlobals());

			for (RuleDescr ruleDescr : pkgDescr.getRules()) {
				
				String ruleName = ruleDescr.getName();
				System.out.println("Rulename = "+ruleName);
				String aRuleText = ruleDescr.getText();
				System.out.println(aRuleText+"\n\n");
				Rule[] rules = new Rule[1];
			
				String ruleCode = "RUL_CODE";

				Rule rule = new Rule(ruleCode, ruleName, ruleText);
				rules[0] = rule;
				QDataRuleMessage ruleMsg = new QDataRuleMessage(rules);
				String ruleJson = gson.toJson(ruleMsg);
				data = Buffer.buffer(ruleJson);
	//			try {
					if (token != null) {
				//		runVertx();
						// vertx.close(); // ?
					}

//				} catch (SocketException e) {
//					e.printStackTrace();
//				} catch (UnknownHostException e) {
//					e.printStackTrace();
//				}

			}

		} catch (DroolsParserException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}