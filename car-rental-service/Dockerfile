FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
COPY car-rental-service/build.gradle car-rental-service/build.gradle
COPY car-rental-service/src car-rental-service/src
RUN chmod +x gradlew && ./gradlew :car-rental-service:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S waylo && adduser -S waylo -G waylo
COPY --from=builder /workspace/car-rental-service/build/libs/*.jar app.jar
RUN chown waylo:waylo app.jar
USER waylo
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
