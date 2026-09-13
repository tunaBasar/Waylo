FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
COPY discovery-service/build.gradle discovery-service/build.gradle
COPY discovery-service/src discovery-service/src
RUN chmod +x gradlew && ./gradlew :discovery-service:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S waylo && adduser -S waylo -G waylo
COPY --from=builder /workspace/discovery-service/build/libs/*.jar app.jar
RUN chown waylo:waylo app.jar
USER waylo
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "app.jar"]
