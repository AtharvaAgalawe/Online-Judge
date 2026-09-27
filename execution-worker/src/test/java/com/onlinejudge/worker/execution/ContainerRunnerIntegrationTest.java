package com.onlinejudge.worker.execution;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.images.builder.ImageFromDockerfile;

import com.onlinejudge.worker.config.DockerConfig;
import com.onlinejudge.worker.config.WorkerProperties;

/**
 * Phase 8 DoD (PRD §18): submitted code compiles and runs inside the hardened sandbox,
 * and adversarial fixtures behave as designed — infinite loop killed by the external
 * watchdog, memory hog killed by the cgroup limit, no network, read-only filesystem
 * outside /tmp, thread explosion bounded, output bomb truncated. Docker-backed; the
 * images are built from the exact Dockerfiles shipped in infrastructure/docker/images.
 */
@Tag("docker")
@SpringBootTest(classes = {DockerConfig.class, ContainerRunner.class})
@EnableConfigurationProperties(WorkerProperties.class)
class ContainerRunnerIntegrationTest {

    private static final String JAVA_COMPILE = "javac -d /box/out /box/src/Main.java";
    private static final Duration RUN_LIMIT = Duration.ofSeconds(5);
    private static final int MEMORY_LIMIT_KB = 262_144;

    private static String javaImage;
    private static String pythonImage;

    @Autowired
    private ContainerRunner runner;

    @BeforeAll
    static void buildLanguageImages() {
        javaImage = new ImageFromDockerfile("oj-java21-itest", false)
                .withDockerfile(Path.of("../infrastructure/docker/images/oj-java21/Dockerfile"))
                .get();
        pythonImage = new ImageFromDockerfile("oj-python312-itest", false)
                .withDockerfile(Path.of("../infrastructure/docker/images/oj-python312/Dockerfile"))
                .get();
    }

    @Test
    void compilesAndRunsJavaAgainstPipedInput() throws IOException {
        Path workspace = runner.createWorkspace();
        try {
            CompileOutcome compile = runner.compile(workspace, javaImage, "Main.java", """
                    import java.util.Scanner;

                    public class Main {
                        public static void main(String[] args) {
                            Scanner in = new Scanner(System.in);
                            System.out.println(in.nextInt() + in.nextInt());
                        }
                    }
                    """, JAVA_COMPILE);
            assertThat(compile.success()).as(compile.compilerOutput()).isTrue();

            RunOutcome run = runner.run(workspace.resolve("artifacts"), javaImage, "java Main",
                    "2 3\n", RUN_LIMIT, MEMORY_LIMIT_KB);
            assertThat(run.exitCode()).as("stderr: %s", run.stderr()).isZero();
            assertThat(run.stdout().trim()).isEqualTo("5");
            assertThat(run.timedOut()).isFalse();
        } finally {
            runner.deleteWorkspace(workspace);
        }
    }

    @Test
    void runsPythonScriptAgainstPipedInput() throws IOException {
        Path workspace = runner.createWorkspace();
        try {
            // Interpreted languages skip the compile step; the source is the run mount.
            Path artifacts = Files.createDirectories(workspace.resolve("artifacts"));
            Files.writeString(artifacts.resolve("main.py"), """
                    import sys
                    a, b = sys.stdin.read().split()
                    print(int(a) * int(b))
                    """, StandardCharsets.UTF_8);

            RunOutcome run = runner.run(artifacts, pythonImage, "python3 main.py",
                    "6 7\n", RUN_LIMIT, MEMORY_LIMIT_KB);
            assertThat(run.exitCode()).isZero();
            assertThat(run.stdout().trim()).isEqualTo("42");
        } finally {
            runner.deleteWorkspace(workspace);
        }
    }

    @Test
    void compilationErrorIsReportedNotExecuted() throws IOException {
        Path workspace = runner.createWorkspace();
        try {
            CompileOutcome compile = runner.compile(workspace, javaImage, "Main.java",
                    "public class Main { public static void main(String[] a) { this is not java } }",
                    JAVA_COMPILE);

            assertThat(compile.success()).isFalse();
            assertThat(compile.compilerOutput()).contains("error");
        } finally {
            runner.deleteWorkspace(workspace);
        }
    }

    @Test
    void infiniteLoopIsKilledByTheExternalWatchdog() throws IOException {
        Path workspace = runner.createWorkspace();
        try {
            RunOutcome run = runner.run(scriptDir(workspace, "loop.py", "while True:\n    pass\n"),
                    pythonImage, "python3 loop.py", "", RUN_LIMIT, MEMORY_LIMIT_KB);

            assertThat(run.timedOut()).isTrue();
            assertThat(run.durationMillis()).isLessThan(RUN_LIMIT.plusSeconds(10).toMillis());
        } finally {
            runner.deleteWorkspace(workspace);
        }
    }

    @Test
    void memoryHogIsKilledByTheCgroupLimit() throws IOException {
        Path workspace = runner.createWorkspace();
        try {
            RunOutcome run = runner.run(scriptDir(workspace, "hog.py",
                            "x = bytearray(300 * 1024 * 1024)\nprint('ALLOCATED')\n"),
                    pythonImage, "python3 hog.py", "", RUN_LIMIT, 65_536);

            assertThat(run.timedOut()).isFalse();
            assertThat(run.exitCode()).isEqualTo(137);
        } finally {
            runner.deleteWorkspace(workspace);
        }
    }

    @Test
    void networkIsUnavailable() throws IOException {
        Path workspace = runner.createWorkspace();
        try {
            RunOutcome run = runner.run(scriptDir(workspace, "net.py", """
                    import socket
                    try:
                        socket.create_connection(('1.1.1.1', 80), timeout=2)
                        print('NETWORK_REACHABLE')
                    except OSError:
                        print('NO_NETWORK')
                    """), pythonImage, "python3 net.py", "", RUN_LIMIT, MEMORY_LIMIT_KB);

            assertThat(run.stdout()).contains("NO_NETWORK").doesNotContain("NETWORK_REACHABLE");
        } finally {
            runner.deleteWorkspace(workspace);
        }
    }

    @Test
    void filesystemIsReadOnlyOutsideTmp() throws IOException {
        Path workspace = runner.createWorkspace();
        try {
            RunOutcome run = runner.run(scriptDir(workspace, "fs.py", """
                    try:
                        open('/box/hack.txt', 'w')
                        print('BOX_WRITE_ALLOWED')
                    except OSError:
                        print('BOX_WRITE_DENIED')
                    try:
                        open('/usr/hack.txt', 'w')
                        print('ROOT_WRITE_ALLOWED')
                    except OSError:
                        print('ROOT_WRITE_DENIED')
                    open('/tmp/ok.txt', 'w')
                    print('TMP_WRITE_OK')
                    """), pythonImage, "python3 fs.py", "", RUN_LIMIT, MEMORY_LIMIT_KB);

            assertThat(run.stdout())
                    .contains("BOX_WRITE_DENIED")
                    .contains("ROOT_WRITE_DENIED")
                    .contains("TMP_WRITE_OK");
        } finally {
            runner.deleteWorkspace(workspace);
        }
    }

    @Test
    void threadExplosionHitsThePidsLimit() throws IOException {
        Path workspace = runner.createWorkspace();
        try {
            RunOutcome run = runner.run(scriptDir(workspace, "threads.py", """
                    import threading, time
                    count = 0
                    try:
                        for _ in range(300):
                            threading.Thread(target=time.sleep, args=(30,), daemon=True).start()
                            count += 1
                    except RuntimeError:
                        print('THREAD_LIMIT_HIT', count)
                    else:
                        print('NO_LIMIT_HIT', count)
                    """), pythonImage, "python3 threads.py", "", Duration.ofSeconds(15), MEMORY_LIMIT_KB);

            assertThat(run.stdout()).contains("THREAD_LIMIT_HIT");
            assertThat(run.timedOut()).isFalse();
        } finally {
            runner.deleteWorkspace(workspace);
        }
    }

    @Test
    void outputBombIsTruncatedAtTheCap() throws IOException {
        Path workspace = runner.createWorkspace();
        try {
            RunOutcome run = runner.run(scriptDir(workspace, "bomb.py", "print('x' * 500000)\n"),
                    pythonImage, "python3 bomb.py", "", RUN_LIMIT, MEMORY_LIMIT_KB);

            assertThat(run.exitCode()).isZero();
            assertThat(run.stdoutTruncated()).isTrue();
            assertThat(run.stdout().length()).isLessThanOrEqualTo(65_536);
        } finally {
            runner.deleteWorkspace(workspace);
        }
    }

    private static Path scriptDir(Path workspace, String filename, String code) throws IOException {
        Path artifacts = Files.createDirectories(workspace.resolve("artifacts"));
        Files.writeString(artifacts.resolve(filename), code, StandardCharsets.UTF_8);
        return artifacts;
    }
}
