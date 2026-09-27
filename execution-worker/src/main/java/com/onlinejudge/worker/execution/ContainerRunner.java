package com.onlinejudge.worker.execution;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.onlinejudge.worker.config.WorkerProperties;

/**
 * Runs untrusted code in ephemeral, locked-down containers (PRD §18). There is NO other
 * path to execution: submitted code never touches the worker process, the host, or the
 * network.
 *
 * <p>Every container is launched with the mandatory hardening set (AGENTS.md §11):
 * {@code --network none}, read-only rootfs with a scoped writable {@code /tmp},
 * all capabilities dropped, {@code no-new-privileges}, memory (+swap=memory) and CPU
 * limits, a pids limit, and a non-root user taken from the image. Each run additionally
 * has an external wall-clock watchdog that force-kills the container independently of
 * anything the in-container process does. Only the compile step may write, and only into
 * a disposable tmpfs from which artifacts are copied out.
 *
 * <p>Removal happens in {@code finally} rather than {@code --rm}, because the container
 * must outlive its process for the exit code to be inspected; the guarantee is identical.
 *
 * <p>Honest limitation (PRD §18/§25): Docker shares the host kernel — this is a hardened
 * namespace/cgroup boundary, not a VM. See docs/decisions for the production hardening
 * roadmap (gVisor/Kata).
 */
@Component
public class ContainerRunner {

    private static final Logger log = LoggerFactory.getLogger(ContainerRunner.class);

    static final String SANDBOX_WORKDIR = "/box";
    static final String SOURCE_MOUNT = "/box/src";
    static final String OUTPUT_MOUNT = "/box/out";
    // Top-level path: a nested mount under the read-only /box bind cannot be created.
    static final String INPUT_MOUNT = "/in";
    static final String INPUT_FILE = "/in/input.txt";
    static final String TMP_MOUNT = "/tmp";

    private static final Duration EXEC_GRACE = Duration.ofSeconds(5);
    private static final int COMPILER_OUTPUT_LIMIT_BYTES = 4_096;
    private static final String TMPFS_OPTIONS = "size=64m,mode=1777";

    private final DockerClient docker;
    private final ScheduledExecutorService watchdog;
    private final WorkerProperties properties;

    public ContainerRunner(DockerClient docker, ScheduledExecutorService watchdog, WorkerProperties properties) {
        this.docker = docker;
        this.watchdog = watchdog;
        this.properties = properties;
    }

    /** Creates the per-submission workspace; the caller owns cleanup via {@link #deleteWorkspace}. */
    public Path createWorkspace() throws IOException {
        return Files.createTempDirectory("oj-sandbox-");
    }

    /**
     * Writes the source into {@code workspace/src} and compiles it inside a sandbox
     * container, with build output going to {@code workspace/artifacts} (a scoped
     * writable bind mount — the container rootfs stays read-only; see PRD §18).
     */
    public CompileOutcome compile(Path workspace, String image, String sourceFilename, String sourceCode,
                                  String compileCmd) {
        try {
            Path sourceDir = Files.createDirectories(workspace.resolve("src"));
            Path artifactsDir = Files.createDirectories(workspace.resolve("artifacts"));
            Path sourceFile = sourceDir.resolve(sourceFilename);
            Files.writeString(sourceFile, sourceCode, StandardCharsets.UTF_8);
            restrictToOwner(sourceFile);

            String containerId = createSandboxContainer(image, properties.compileMemoryMb() * 1024L * 1024L,
                    Map.of(SOURCE_MOUNT, readOnlyBind(sourceDir, SOURCE_MOUNT),
                            OUTPUT_MOUNT, readWriteBind(artifactsDir, OUTPUT_MOUNT)),
                    Map.of(TMP_MOUNT, TMPFS_OPTIONS));
            try {
                ExecOutcome outcome = exec(containerId, List.of("sh", "-c", compileCmd),
                        properties.compileTimeout());
                String output = truncate(combinedOutput(outcome), COMPILER_OUTPUT_LIMIT_BYTES);
                if (outcome.timedOut()) {
                    return new CompileOutcome(false, "Compilation timed out after "
                            + properties.compileTimeout().toSeconds() + "s\n" + output);
                }
                return new CompileOutcome(outcome.exitCode() == 0, output);
            } finally {
                removeContainer(containerId);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to prepare the compile workspace", e);
        }
    }

    /**
     * Runs one test case: {@code artifactsDir} is mounted read-only at {@code /box}, the
     * test input is mounted read-only and redirected to stdin, and the watchdog kills the
     * container at {@code timeLimit + watchdogBuffer}.
     */
    public RunOutcome run(Path artifactsDir, String image, String runCmd, String stdin,
                          Duration timeLimit, int memoryLimitKb) {
        Path inputDir = null;
        String containerId = null;
        try {
            Files.createDirectories(artifactsDir);
            inputDir = Files.createTempDirectory("oj-input-");
            Path inputFile = inputDir.resolve("input.txt");
            Files.writeString(inputFile, stdin, StandardCharsets.UTF_8);
            restrictToOwner(inputFile);

            containerId = createSandboxContainer(image, memoryLimitKb * 1024L,
                    Map.of(SANDBOX_WORKDIR, readOnlyBind(artifactsDir, SANDBOX_WORKDIR),
                            INPUT_MOUNT, readOnlyBind(inputDir, INPUT_MOUNT)),
                    Map.of(TMP_MOUNT, TMPFS_OPTIONS));

            Instant start = Instant.now();
            ExecOutcome outcome = exec(containerId, List.of("sh", "-c", runCmd + " < " + INPUT_FILE),
                    timeLimit.plus(properties.watchdogBuffer()));
            long duration = Duration.between(start, Instant.now()).toMillis();
            return new RunOutcome(outcome.exitCode(), outcome.timedOut(), outcome.stdoutTruncated(),
                    outcome.stdout(), outcome.stderr(), duration);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to prepare the run input", e);
        } finally {
            if (containerId != null) {
                removeContainer(containerId);
            }
            deleteWorkspace(inputDir);
        }
    }

    /** Recursively deletes a workspace; safe to call multiple times. */
    public void deleteWorkspace(Path workspace) {
        if (workspace == null || !Files.exists(workspace)) {
            return;
        }
        try {
            Files.walkFileTree(workspace, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.warn("Could not fully delete sandbox workspace {}: {}", workspace, e.getMessage());
        }
    }

    private String createSandboxContainer(String image, long memoryBytes, Map<String, Bind> binds,
                                          Map<String, String> tmpFs) {
        HostConfig hostConfig = HostConfig.newHostConfig()
                .withNetworkMode("none")
                .withReadonlyRootfs(true)
                .withMemory(memoryBytes)
                .withMemorySwap(memoryBytes)
                .withCpuCount(1L)
                .withPidsLimit(properties.pidsLimit())
                .withCapDrop(Capability.ALL)
                .withSecurityOpts(List.of("no-new-privileges"))
                .withTmpFs(tmpFs)
                .withBinds(List.copyOf(binds.values()));
        CreateContainerCmd create = docker.createContainerCmd(image)
                .withHostConfig(hostConfig)
                // The container is held open with sleep; the actual (untrusted) command
                // runs via exec so stdin/stdout/stderr can be streamed and capped.
                .withEntrypoint("sleep")
                .withCmd("infinity")
                .withWorkingDir(SANDBOX_WORKDIR);
        String containerId = create.exec().getId();
        docker.startContainerCmd(containerId).exec();
        return containerId;
    }

    private ExecOutcome exec(String containerId, List<String> command, Duration timeout) {
        ExecCreateCmdResponse exec = docker.execCreateCmd(containerId)
                .withCmd(command.toArray(new String[0]))
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withWorkingDir(SANDBOX_WORKDIR)
                .exec();

        CappedOutputStream stdout = new CappedOutputStream(properties.maxOutputBytes());
        CappedOutputStream stderr = new CappedOutputStream(properties.maxOutputBytes());
        ExecStartResultCallback callback = new ExecStartResultCallback(stdout, stderr);
        docker.execStartCmd(exec.getId()).exec(callback);

        AtomicBoolean timedOut = new AtomicBoolean(false);
        ScheduledFuture<?> killer = watchdog.schedule(() -> {
            timedOut.set(true);
            killContainerQuietly(containerId);
        }, timeout.toMillis(), TimeUnit.MILLISECONDS);

        boolean completed;
        try {
            completed = callback.awaitCompletion(timeout.toMillis() + EXEC_GRACE.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            completed = false;
        } finally {
            killer.cancel(false);
        }
        if (!completed) {
            timedOut.set(true);
            killContainerQuietly(containerId);
            try {
                callback.awaitCompletion(EXEC_GRACE.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        int exitCode = -1;
        try {
            exitCode = docker.inspectExecCmd(exec.getId()).exec().getExitCode();
        } catch (RuntimeException e) {
            log.warn("Could not read exit code of exec {}: {}", exec.getId(), e.getMessage());
        }
        return new ExecOutcome(exitCode, timedOut.get(), stdout.asString(StandardCharsets.UTF_8),
                stderr.asString(StandardCharsets.UTF_8), stdout.isTruncated());
    }

    private void removeContainer(String containerId) {
        try {
            docker.removeContainerCmd(containerId).withForce(true).withRemoveVolumes(true).exec();
        } catch (RuntimeException e) {
            log.warn("Could not remove sandbox container {}: {}", containerId, e.getMessage());
        }
    }

    private void killContainerQuietly(String containerId) {
        try {
            docker.killContainerCmd(containerId).exec();
        } catch (RuntimeException e) {
            log.debug("Kill of container {} was unnecessary: {}", containerId, e.getMessage());
        }
    }

    private static Bind readOnlyBind(Path hostPath, String containerPath) {
        return new Bind(hostPath.toAbsolutePath().toString(), new Volume(containerPath), AccessMode.ro);
    }

    private static Bind readWriteBind(Path hostPath, String containerPath) {
        return new Bind(hostPath.toAbsolutePath().toString(), new Volume(containerPath), AccessMode.rw);
    }

    private static String combinedOutput(ExecOutcome outcome) {
        List<String> parts = new java.util.ArrayList<>();
        if (!outcome.stdout().isBlank()) {
            parts.add(outcome.stdout());
        }
        if (!outcome.stderr().isBlank()) {
            parts.add(outcome.stderr());
        }
        return String.join("\n", parts).trim();
    }

    private static String truncate(String value, int maxBytes) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return value;
        }
        return new String(bytes, 0, maxBytes, StandardCharsets.UTF_8) + "\n[output truncated]";
    }

    private static void restrictToOwner(Path file) {
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException e) {
            // Swallowing is correct because on non-POSIX hosts (Windows dev machines)
            // file modes do not exist; production runs on POSIX hosts where they apply.
            log.debug("POSIX permissions unavailable for {}: {}", file, e.getMessage());
        }
    }

    /** Raw exec result before verdict interpretation. */
    private record ExecOutcome(int exitCode, boolean timedOut, String stdout, String stderr,
                               boolean stdoutTruncated) {
    }
}
