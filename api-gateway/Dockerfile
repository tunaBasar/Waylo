FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
COPY api-gateway/build.gradle api-gateway/build.gradle
COPY api-gateway/src api-gateway/src
RUN chmod +x gradlew && ./gradlew :api-gateway:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S waylo && adduser -S waylo -G waylo
COPY --from=builder /workspace/api-gateway/build/libs/*.jar app.jar
RUN chown waylo:waylo app.jar
USER waylo
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
