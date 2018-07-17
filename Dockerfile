FROM openjdk:10-slim

COPY ./target/heimdall-1.0.jar /usr/opt/heimdall/heimdall.jar
COPY ./log4j2.xml /usr/opt/heimdall/log4j2.xml

WORKDIR /usr/opt/heimdall
EXPOSE 8080
EXPOSE 9999

CMD ["java", "-Dlog4j.configurationFile=log4j2.xml", "-jar", "heimdall.jar"]
