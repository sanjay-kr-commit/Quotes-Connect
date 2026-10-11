# syntax=docker/dockerfile:1

FROM gradle:9.5.1-jdk21-alpine AS builder

WORKDIR /workspace
COPY . .

RUN chmod +x ./gradlew \
    && ./gradlew :server:installDist --no-daemon

FROM eclipse-temurin:21-jre-alpine

WORKDIR /opt/quotes-connect

COPY --from=builder /workspace/server/build/install/server/ ./

EXPOSE 8080
EXPOSE 8443

ENTRYPOINT ["./bin/server"]
