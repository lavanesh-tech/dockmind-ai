# Runtime image for ai-operations-service. Build the jar first: ./mvnw -pl ai-operations-service -am package -DskipTests
FROM eclipse-temurin:21-jre-alpine
# Run as an unprivileged user, never root.
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY target/ai-operations-service-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8084
# Size the heap from the container's memory limit instead of the host's.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
