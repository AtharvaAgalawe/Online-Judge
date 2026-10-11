# Judge runtime image for C 17 and C++ 17 via GCC 13 (PRD §18/§30). Base pinned by digest
# (AGENTS.md §11); the tag is documentation only. Rebuild and re-pin via CI when patching.
FROM gcc:13-bookworm@sha256:b59036579f0622d1b3aa5286aaf2fcef9b9c6f88f31c6762236fc2f136f7f650

# Non-root judge user for every sandbox process.
RUN groupadd --system judge && useradd --system --gid judge --create-home judge

# Mount points used by the worker (source in, build out, per-run input). They must exist
# in the image: the sandbox rootfs is read-only, so the runtime cannot create them.
RUN mkdir -p /box/src /box/out /in

USER judge
WORKDIR /box
