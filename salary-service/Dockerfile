# salary-service — production image.
#
# Multi-stage on purpose: the build stage carries Maven, the full JDK and ~200MB of dependency
# cache, none of which should ship. The runtime stage is a JRE and one jar.
#
# Java: the pom targets 17 (`<java.version>17</java.version>`), so any JRE >= 17 runs the artifact.
# Temurin 21 is the current LTS and is what this image runs on — raising the *target* past 17 is an
# ADR-level decision per docs/STATE.md, but the *runtime* is free to be newer.

# ── build ────────────────────────────────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependencies resolve in their own layer, keyed on the pom alone. Application code changes far more
# often than the dependency set, and without this split every code push re-downloads the world.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Tests are deliberately NOT run here. They need a Docker daemon for Testcontainers (real Postgres
# 17, never H2 — CLAUDE.md §3), which is not available inside a build container. The suite is the
# gate before you push, not during the image build; see docs/DEPLOYMENT.md.
RUN mvn -B -q clean package -DskipTests

# ── runtime ──────────────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Never root. An RCE in a dependency should not also be a root shell.
RUN addgroup -S salaryos && adduser -S -G salaryos salaryos

COPY --from=build /build/target/salary-service-*.jar app.jar
RUN chown salaryos:salaryos app.jar
USER salaryos

EXPOSE 8080

# MaxRAMPercentage rather than a fixed -Xmx: the container memory limit is set by the host (Render's
# instance size), and a hardcoded heap is either wasteful or an OOM kill depending on which plan
# this lands on. The JVM reads the cgroup limit and takes 75% of it.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseSerialGC"

# `exec` so the JVM is PID 1 and receives SIGTERM directly — otherwise the shell swallows it and the
# platform waits out its grace period on every deploy before killing the container.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
