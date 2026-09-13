FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
COPY crm-service/build.gradle crm-service/build.gradle
COPY crm-service/src crm-service/src
RUN chmod +x gradlew && ./gradlew :crm-service:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S waylo && adduser -S waylo -G waylo
COPY --from=builder /workspace/crm-service/build/libs/*.jar app.jar
RUN chown waylo:waylo app.jar
USER waylo
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "app.jar"]
