FROM eclipse-temurin:21-jre
WORKDIR /app
COPY core-ai-backend/target/core-ai-backend-*.jar app.jar
RUN mkdir -p /app/data
EXPOSE 8104
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
