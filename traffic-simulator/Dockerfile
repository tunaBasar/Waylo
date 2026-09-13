FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
COPY traffic-simulator/build.gradle traffic-simulator/build.gradle
COPY traffic-simulator/src traffic-simulator/src
RUN chmod +x gradlew && ./gradlew :traffic-simulator:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S waylo && adduser -S waylo -G waylo
COPY --from=builder /workspace/traffic-simulator/build/libs/*.jar app.jar
RUN chown waylo:waylo app.jar
USER waylo
EXPOSE 8095
ENTRYPOINT ["java", "-jar", "app.jar"]
