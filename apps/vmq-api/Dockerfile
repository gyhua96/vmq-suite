# syntax=docker/dockerfile:1

FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /workspace

COPY pom.xml ./
COPY src ./src
RUN mvn -B -DskipTests clean package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

ENV SERVER_PORT=50126 \
    SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/vmq \
    SPRING_DATASOURCE_USERNAME=vmq \
    SPRING_DATASOURCE_PASSWORD= \
    SPRING_JPA_HIBERNATE_DDL_AUTO=update \
    SPRING_JPA_SHOW_SQL=false \
    SPRING_FLYWAY_ENABLED=false

COPY --from=builder /workspace/target/vmq-api.war /app/app.war

EXPOSE 50126
ENTRYPOINT ["java", "-jar", "/app/app.war"]
