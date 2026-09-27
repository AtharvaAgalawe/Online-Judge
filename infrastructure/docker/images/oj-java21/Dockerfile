# Judge runtime image for Java 21 (PRD §18/§30). Base pinned by digest (AGENTS.md §11);
# the tag is documentation only. Rebuild and re-pin via CI when patching.
FROM eclipse-temurin:21-jdk-alpine@sha256:0bfc69a4758a86710e5c474032d28400a8bd00874766f9e8b1642ac2fd293159

# Non-root judge user for every sandbox process.
RUN addgroup -S judge && adduser -S judge -G judge

# Mount points used by the worker (source in, build out, per-run input). They must exist
# in the image: the sandbox rootfs is read-only, so the runtime cannot create them.
RUN mkdir -p /box/src /box/out /in

USER judge
WORKDIR /box
