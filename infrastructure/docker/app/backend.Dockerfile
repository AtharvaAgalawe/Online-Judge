# Backend API image (AGENTS.md §11: the backend has no Docker access, no privileged flags).
# The build context is the repository root, because the build needs the `common` module
# and the root pom: `docker build -f infrastructure/docker/app/backend.Dockerfile .`
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests -pl backend -am package

FROM eclipse-temurin:21-jre-alpine
# UID/GID 10001 is the fixed non-root identity used by every application image here
# (and by the sandbox containers the worker launches, which run as the workspace owner).
RUN addgroup -S -g 10001 ojudge \
 && adduser -S -u 10001 -G ojudge -h /app -s /sbin/nologin ojudge
WORKDIR /app
COPY --from=build --chown=10001:10001 /build/backend/target/backend-*.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"'
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/app.jar"]
