FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY build/libs/app.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]

