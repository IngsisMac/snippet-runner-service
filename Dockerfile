FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
ADD https://download.newrelic.com/newrelic/java-agent/newrelic-agent/8.15.0/newrelic-agent-8.15.0.jar /app/newrelic.jar
COPY build/libs/app.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-javaagent:/app/newrelic.jar", "-jar", "app.jar"]

