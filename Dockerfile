FROM openjdk:8u131-jre-alpine
RUN apk update && apk add jq && apk add curl && apk add bash
 
MAINTAINER Adam Crow <adamcrow63@gmail.com>

ENV HOME /root
USER root

ADD docker-entrypoint.sh $HOME/

ADD target/filewatch-0.0.1-SNAPSHOT-jar-with-dependencies.jar $HOME/app.jar
WORKDIR $HOME

ENTRYPOINT [ "/root/docker-entrypoint.sh" ]
