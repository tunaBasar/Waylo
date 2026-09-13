FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
COPY flight-service/build.gradle flight-service/build.gradle
COPY flight-service/src flight-service/src
RUN chmod +x gradlew && ./gradlew :flight-service:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S waylo && adduser -S waylo -G waylo
COPY --from=builder /workspace/flight-service/build/libs/*.jar app.jar
RUN chown waylo:waylo app.jar
USER waylo
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
