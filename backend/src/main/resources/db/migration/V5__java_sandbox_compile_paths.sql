-- The sandbox layout (PRD §18): the source is mounted read-only under /box/src, the
-- compile step writes its artifacts into a writable tmpfs at /box/out (the rootfs is
-- read-only). The stored compile command must therefore name its input and output
-- explicitly — this supersedes the V4 seed value.
UPDATE languages
SET compile_cmd = 'javac -d /box/out /box/src/Main.java'
WHERE name = 'Java 21' AND compile_cmd = 'javac Main.java';
