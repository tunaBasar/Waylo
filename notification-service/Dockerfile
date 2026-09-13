FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
COPY notification-service/build.gradle notification-service/build.gradle
COPY notification-service/src notification-service/src
RUN chmod +x gradlew && ./gradlew :notification-service:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S waylo && adduser -S waylo -G waylo
COPY --from=builder /workspace/notification-service/build/libs/*.jar app.jar
RUN chown waylo:waylo app.jar
USER waylo
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
