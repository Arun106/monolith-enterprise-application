# syntax=docker/dockerfile:1.7

FROM maven:3.9.13-eclipse-temurin-8-noble AS build

WORKDIR /workspace

COPY pom.xml ./
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress clean verify

FROM eclipse-temurin:8u492-b09-jre-noble

WORKDIR /app

COPY --from=build --chown=10001:10001 /workspace/target/Snowman.jar /app/Snowman.jar

ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/urandom"

EXPOSE 8090

USER 10001:10001

ENTRYPOINT ["java", "-jar", "/app/Snowman.jar"]
