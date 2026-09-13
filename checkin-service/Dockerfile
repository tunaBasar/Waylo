FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
COPY checkin-service/build.gradle checkin-service/build.gradle
COPY checkin-service/src checkin-service/src
RUN chmod +x gradlew && ./gradlew :checkin-service:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S waylo && adduser -S waylo -G waylo
COPY --from=builder /workspace/checkin-service/build/libs/*.jar app.jar
RUN chown waylo:waylo app.jar
USER waylo
EXPOSE 8089
ENTRYPOINT ["java", "-jar", "app.jar"]
