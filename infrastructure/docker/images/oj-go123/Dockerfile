# Judge runtime image for Go 1.23 (PRD §18/§30). Base pinned by digest (AGENTS.md §11);
# the tag is documentation only. Rebuild and re-pin via CI when patching.
FROM golang:1.23-alpine@sha256:383395b794dffa5b53012a212365d40c8e37109a626ca30d6151c8348d380b5f

# Non-root judge user for every sandbox process.
RUN addgroup -S judge && adduser -S judge -G judge

# Mount points used by the worker (source in, build out, per-run input). They must exist
# in the image: the sandbox rootfs is read-only, so the runtime cannot create them.
RUN mkdir -p /box/src /box/out /in

USER judge
WORKDIR /box
