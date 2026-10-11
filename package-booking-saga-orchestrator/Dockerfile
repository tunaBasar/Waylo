FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S waylo && adduser -S waylo -G waylo
COPY package-booking-saga-orchestrator/build/libs/*-SNAPSHOT.jar app.jar
RUN chown waylo:waylo app.jar
USER waylo
EXPOSE 8091
ENTRYPOINT ["java", "-jar", "app.jar"]
