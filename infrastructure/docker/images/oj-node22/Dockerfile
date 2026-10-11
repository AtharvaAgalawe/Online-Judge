# Judge runtime image for JavaScript on Node 22 (PRD §18/§30). Base pinned by digest
# (AGENTS.md §11); the tag is documentation only. Rebuild and re-pin via CI when patching.
FROM node:22-alpine@sha256:0a7108bf6c7bf5de370ffb1a3ed6be93d405b43ff159f681a8d18c0e2bc2e402

# Non-root judge user for every sandbox process.
RUN addgroup -S judge && adduser -S judge -G judge

# Mount points used by the worker (source in, build out, per-run input). They must exist
# in the image: the sandbox rootfs is read-only, so the runtime cannot create them.
RUN mkdir -p /box/src /box/out /in

USER judge
WORKDIR /box
