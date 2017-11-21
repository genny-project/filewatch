package life.genny.main;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

//import org.drools.runtime.StatefulKnowledgeSession;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;


public class App {

	@Parameter(names = "--help", help = true)
	private boolean help = false;

	@Parameter(names = { "--folder",
			"-f" }, description = "Folder Path:- contains files which need to be published to the Vertx")
	String folder;

	@Parameter(names = { "--token", "-t" }, description = "Keycloak Security Token")
	String token;

	@Parameter(names = { "--address", "-a" }, description = "event bus address")
	String eventBusAddress = "{}";


	@Parameter(names = { "--quiet", "-q" }, description = "enables quiet mode - true/false")
	private boolean quiet = false;

	public static void main(String... args) {
		App main = new App();

		if (!main.quiet) {
			System.out.println("Genny Filewatch\n Version 0.1\n");
			
		}
		JCommander jCommander = new JCommander(main, args);
		if ((main.help)||((args.length == 0))) {
			jCommander.usage();
			return;
		}
		if (args.length > 0) {
				try {
					main.run();
				} catch (Exception e) {
					System.out.println("Error in running: " + e.getMessage());
				}
			
		}
	}

	public void run() throws IOException {
		        boolean recursive = true;

		        // register directory and process its events
		        Path dir = Paths.get(folder);
		        Filewatch watchdir = new Filewatch(dir, recursive, eventBusAddress, token);
		        
		        watchdir.processEvents();

	}


}
