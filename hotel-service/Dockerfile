FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
COPY hotel-service/build.gradle hotel-service/build.gradle
COPY hotel-service/src hotel-service/src
RUN chmod +x gradlew && ./gradlew :hotel-service:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S waylo && adduser -S waylo -G waylo
COPY --from=builder /workspace/hotel-service/build/libs/*.jar app.jar
RUN chown waylo:waylo app.jar
USER waylo
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
