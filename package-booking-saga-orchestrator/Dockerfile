FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
COPY package-booking-saga-orchestrator/build.gradle package-booking-saga-orchestrator/build.gradle
COPY package-booking-saga-orchestrator/src package-booking-saga-orchestrator/src
RUN chmod +x gradlew && ./gradlew :package-booking-saga-orchestrator:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S waylo && adduser -S waylo -G waylo
COPY --from=builder /workspace/package-booking-saga-orchestrator/build/libs/*.jar app.jar
RUN chown waylo:waylo app.jar
USER waylo
EXPOSE 8091
ENTRYPOINT ["java", "-jar", "app.jar"]
