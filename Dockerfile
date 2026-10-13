# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew
# All service images share this build stage, so Gradle runs once per source change.
RUN --mount=type=cache,id=waylo-gradle,target=/root/.gradle,sharing=locked \
    ./gradlew --no-daemon --max-workers=2 compileJava bootJar -x test

FROM eclipse-temurin:21-jre-alpine AS runtime
ARG SERVICE
WORKDIR /app
RUN addgroup -S waylo && adduser -S waylo -G waylo
COPY --from=build --chown=waylo:waylo /workspace/${SERVICE}/build/libs/*-SNAPSHOT.jar app.jar
USER waylo
ENTRYPOINT ["java", "-jar", "app.jar"]
