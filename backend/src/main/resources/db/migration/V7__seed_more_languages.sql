-- Reference data for the extended language set (PRD §35). The sandbox layout (PRD §18):
-- sources are mounted read-only under /box/src and compiled artifacts land in the writable
-- /box/out (which becomes /box read-only at run time), so compiled languages must name
-- both paths explicitly. Go additionally needs a writable build cache (the rootfs is
-- read-only and /tmp is the only writable location) and -p=1: the build tool would
-- otherwise size its compiler fan-out from the host's CPU affinity and exhaust the
-- container's pids limit. Image names match the runtime images built under
-- infrastructure/docker/images (PRD §30). Idempotent.
INSERT INTO languages (name, source_filename, compile_cmd, run_cmd, docker_image, time_limit_multiplier)
VALUES
  ('C 17', 'main.c', 'gcc -O2 -std=c17 -o /box/out/main /box/src/main.c',
   './main', 'oj-gcc13', 1.00),
  ('C++ 17', 'main.cpp', 'g++ -O2 -std=c++17 -o /box/out/main /box/src/main.cpp',
   './main', 'oj-gcc13', 1.00),
  ('JavaScript (Node 22)', 'main.js', NULL,
   'node main.js', 'oj-node22', 1.00),
  ('Go 1.23', 'main.go',
   'HOME=/tmp GOCACHE=/tmp/gocache GOPATH=/tmp/gopath go build -p=1 -o /box/out/main /box/src/main.go',
   './main', 'oj-go123', 1.00)
ON CONFLICT (name) DO NOTHING;
