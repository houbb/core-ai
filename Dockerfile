FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/core-ai-*.jar app.jar
RUN mkdir -p /app/data
EXPOSE 8104
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
