# Judge runtime image for Python 3.12 (PRD §18/§30). Base pinned by digest
# (AGENTS.md §11); the tag is documentation only. Rebuild and re-pin via CI when patching.
FROM python:3.12-alpine@sha256:4c47124a8391cb7a9f571164147d154777cf012a4ece5f86097130d7a4478111

# Non-root judge user for every sandbox process.
RUN addgroup -S judge && adduser -S judge -G judge

# Mount points used by the worker (source in, build out, per-run input). They must exist
# in the image: the sandbox rootfs is read-only, so the runtime cannot create them.
RUN mkdir -p /box/src /box/out /in

USER judge
WORKDIR /box
