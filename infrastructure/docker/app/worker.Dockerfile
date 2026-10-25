# Execution-worker image (AGENTS.md §11: the only component with Docker access).
# Build context is the repository root: `docker build -f infrastructure/docker/app/worker.Dockerfile .`
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests -pl execution-worker -am package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S -g 10001 ojudge \
 && adduser -S -u 10001 -G ojudge -h /app -s /sbin/nologin ojudge \
 && mkdir -p /var/lib/oj/workspace \
 && chown -R 10001:10001 /var/lib/oj
# The worker creates its per-submission workspaces with Files.createTempDirectory and bind
# mounts them into sandbox containers. The Docker daemon resolves bind sources on the HOST,
# so the workspace must be a host directory mounted at the SAME absolute path (see
# infrastructure/docker-compose.prod.yml and docs/deployment.md).
ENV JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=/var/lib/oj/workspace"
WORKDIR /app
COPY --from=build --chown=10001:10001 \
    /build/execution-worker/target/execution-worker-*.jar /app/app.jar
USER 10001:10001
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=50", "-jar", "/app/app.jar"]
