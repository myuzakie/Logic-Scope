package com.logicscope.cli;

import com.logicscope.discovery.MavenRepositoryInspector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogicScopeCliTest {
    @TempDir
    Path tempDir;

    @Test
    void scansSupportedRepositoryUsingDiscoveryEngine() throws Exception {
        Path repository = Files.createDirectory(tempDir.resolve("supported"));
        Files.createDirectories(repository.resolve("src/main/java"));
        Files.writeString(repository.resolve("src/main/java/Application.java"), "class Application {}\n");
        Files.writeString(repository.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <properties><java.version>21</java.version><spring-boot.version>3.4.5</spring-boot.version></properties>
                  <dependencies><dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId><version>${spring-boot.version}</version></dependency></dependencies>
                </project>
                """);
        CliResult result = run("scan", repository.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("STATUS: SUPPORTED"));
        assertTrue(result.stdout().contains("Version: 21"));
        assertTrue(result.stdout().contains("Spring Boot"));
    }

    @Test
    void reportsUnsupportedRepositoryWithoutStackTrace() throws Exception {
        Path repository = Files.createDirectory(tempDir.resolve("plain"));
        CliResult result = run("scan", repository.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("STATUS: UNSUPPORTED"));
        assertTrue(result.stdout().contains("No supported Maven project was detected."));
        assertFalse(result.stdout().contains("Exception"));
        assertFalse(result.stderr().contains("Exception"));
    }

    @Test
    void rejectsInvalidPath() {
        CliResult result = run("scan", tempDir.resolve("does-not-exist").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("STATUS: UNSUPPORTED"));
        assertTrue(result.stdout().contains("No supported Maven project was detected."));
    }

    @Test
    void rejectsMissingArgument() {
        CliResult result = run("scan");

        assertEquals(2, result.exitCode());
        assertTrue(result.stderr().contains("Usage:"));
        assertTrue(result.stderr().contains("logicscope scan <repository>"));
    }

    @Test
    void rejectsUnknownCommand() {
        CliResult result = run("something", tempDir.toString());

        assertEquals(2, result.exitCode());
        assertTrue(result.stderr().contains("Unknown command: something"));
        assertTrue(result.stderr().contains("Usage:"));
    }

    @Test
    void initializesTargetProject() {
        CliResult result = run("init", tempDir.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("LogicScope initialized at:"));
    }

    @Test
    void initializesCurrentDirectoryWhenPathIsOmitted() {
        CliResult result = runFrom(tempDir, "init");

        assertEquals(0, result.exitCode());
        assertTrue(Files.isRegularFile(tempDir.resolve(".logicscope/config.yaml")));
        assertTrue(result.stdout().contains("LogicScope initialized at:"));
    }

    @Test
    void failsInitWhenTargetDoesNotExist() {
        CliResult result = run("init", tempDir.resolve("nonexistent").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Target path does not exist"));
    }

    @Test
    void failsInitWhenConfigAlreadyExists() throws Exception {
        Files.createDirectories(tempDir.resolve(".logicscope"));
        Files.writeString(tempDir.resolve(".logicscope").resolve("config.yaml"), "project:\n  root: .\n");

        CliResult result = run("init", tempDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Configuration already exists"));
    }

    @Test
    void runFailsWhenConfigMissing() {
        CliResult result = run("run", tempDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("No LogicScope project configuration was found in this directory."));
        assertTrue(result.stderr().contains("logicscope init " + tempDir.toAbsolutePath().normalize()));
    }

    @Test
    void runUsesCurrentDirectoryWhenPathIsOmitted() throws Exception {
        writeConfig("server:\n  port: 4377\n");
        AppArtifactLocator missingLocator = new AppArtifactLocator(Optional::empty);
        CliResult result = runFrom(tempDir, missingLocator, "run");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("LogicScope application artifact not found"));
        assertFalse(result.stderr().contains("No LogicScope project configuration"));
    }

    @Test
    void runShowsHelpfulMessageWhenCurrentDirectoryHasNoConfig() {
        CliResult result = runFrom(tempDir, "run");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("No LogicScope project configuration was found in this directory."));
        assertTrue(result.stderr().contains("  logicscope init " + tempDir.toAbsolutePath().normalize()));
    }

    @Test
    void runFailsWhenConfigurationInvalid() throws Exception {
        writeConfig("server:\n  port: not-a-number\n");

        CliResult result = run("run", tempDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Invalid configuration"));
    }

    @Test
    void runFailsWhenApplicationArtifactMissing() throws Exception {
        writeConfig("server:\n  port: 4377\n");
        AppArtifactLocator missingLocator = new AppArtifactLocator(Optional::empty);

        CliResult result = runWith(missingLocator, ServerRuntime.createDefault(
                        new PrintStream(new ByteArrayOutputStream()),
                        new PrintStream(new ByteArrayOutputStream())),
                "run", tempDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("LogicScope application artifact not found"));
    }

    @Test
    void runFailsWhenPortOccupied() throws Exception {
        writeConfig("server:\n  port: 4377\n");
        Path fakeJar = Files.writeString(tempDir.resolve("app.jar"), "fake");
        AppArtifactLocator locator = new AppArtifactLocator(() -> Optional.of(fakeJar));

        var stdout = new ByteArrayOutputStream();
        var stderr = new ByteArrayOutputStream();
        ServerRuntime runtime = new ServerRuntime(
                (command, env) -> {
                    throw new AssertionError("process must not start when port is occupied");
                },
                (uri, timeout, poll, alive) -> false,
                port -> false,
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        int exitCode = new LogicScopeCli(new MavenRepositoryInspector(), locator, runtime,
                new PrintStream(stdout), new PrintStream(stderr)).run(new String[]{"run", tempDir.toString()});

        assertEquals(1, exitCode);
        assertTrue(stderr.toString().contains("Port 4377 is already in use"));
    }

    @Test
    void runPrintsConfirmedUrlOnlyAfterHealthPasses() throws Exception {
        writeConfig("server:\n  port: 4377\n");
        Path fakeJar = Files.writeString(tempDir.resolve("app.jar"), "fake");
        AppArtifactLocator locator = new AppArtifactLocator(() -> Optional.of(fakeJar));

        FakeProcess process = new FakeProcess();
        AtomicReference<List<String>> startedCommand = new AtomicReference<>();
        AtomicReference<URI> openedBrowserUrl = new AtomicReference<>();
        AtomicInteger healthCalls = new AtomicInteger();

        var stdout = new ByteArrayOutputStream();
        var stderr = new ByteArrayOutputStream();
        ServerRuntime runtime = new ServerRuntime(
                (command, env) -> {
                    startedCommand.set(List.copyOf(command));
                    return process;
                },
                (uri, timeout, poll, alive) -> {
                    healthCalls.incrementAndGet();
                    assertEquals(URI.create("http://localhost:4377/api/health"), uri);
                    assertTrue(alive.isAlive());
                    process.releaseWait();
                    return true;
                },
                port -> true,
                new PrintStream(stdout),
                new PrintStream(stderr),
                uri -> {
                    openedBrowserUrl.set(uri);
                    return false;
                }
        );

        Thread runner = new Thread(() -> {
            int code = new LogicScopeCli(new MavenRepositoryInspector(), locator, runtime,
                    new PrintStream(stdout), new PrintStream(stderr), () -> tempDir).run(new String[]{"run"});
            assertEquals(0, code);
        });
        runner.start();
        assertTrue(process.awaitStart(5, TimeUnit.SECONDS));
        runner.join(Duration.ofSeconds(5).toMillis());

        assertFalse(runner.isAlive());
        assertEquals(1, healthCalls.get());
        assertTrue(startedCommand.get().contains(fakeJar.toString()));
        assertTrue(startedCommand.get().stream().anyMatch(arg -> arg.equals("--server.port=4377")));
        assertTrue(startedCommand.get().stream().anyMatch(arg -> arg.equals(
                "--logicscope.target-project=" + tempDir.toAbsolutePath().normalize())));
        assertEquals(URI.create("http://localhost:4377/dashboard/"), openedBrowserUrl.get());
        assertTrue(stdout.toString().contains("LogicScope dashboard is ready:"));
        assertTrue(stdout.toString().contains("http://localhost:4377/dashboard/"));
        assertTrue(stdout.toString().contains("Open this URL in your browser"));
    }

    @Test
    void runDestroysChildProcessOnShutdownHook() throws Exception {
        writeConfig("server:\n  port: 4377\n");
        Path fakeJar = Files.writeString(tempDir.resolve("app.jar"), "fake");
        AppArtifactLocator locator = new AppArtifactLocator(() -> Optional.of(fakeJar));

        FakeProcess process = new FakeProcess(false);
        var stdout = new ByteArrayOutputStream();
        var stderr = new ByteArrayOutputStream();
        ServerRuntime runtime = new ServerRuntime(
                (command, env) -> process,
                (uri, timeout, poll, alive) -> true,
                port -> true,
                new PrintStream(stdout),
                new PrintStream(stderr)
        );

        Thread runner = new Thread(() -> new LogicScopeCli(new MavenRepositoryInspector(), locator, runtime,
                new PrintStream(stdout), new PrintStream(stderr)).run(new String[]{"run", tempDir.toString()}));
        runner.start();
        assertTrue(process.awaitStart(5, TimeUnit.SECONDS));

        process.destroy();
        runner.join(Duration.ofSeconds(5).toMillis());

        assertTrue(process.wasDestroyed());
        assertFalse(runner.isAlive());
    }

    private void writeConfig(String content) throws IOException {
        Files.createDirectories(tempDir.resolve(".logicscope"));
        Files.writeString(tempDir.resolve(".logicscope").resolve("config.yaml"), content);
    }

    private CliResult run(String... args) {
        return runFrom(Path.of("."), args);
    }

    private CliResult runFrom(Path currentDirectory, String... args) {
        return runFrom(currentDirectory, new AppArtifactLocator(), args);
    }

    private CliResult runFrom(Path currentDirectory, AppArtifactLocator locator, String... args) {
        var stdout = new ByteArrayOutputStream();
        var stderr = new ByteArrayOutputStream();
        ServerRuntime runtime = ServerRuntime.createDefault(new PrintStream(stdout), new PrintStream(stderr));
        int exitCode = new LogicScopeCli(new MavenRepositoryInspector(), locator, runtime,
                new PrintStream(stdout), new PrintStream(stderr), () -> currentDirectory).run(args);
        return new CliResult(exitCode, stdout.toString(), stderr.toString());
    }

    private CliResult runWith(AppArtifactLocator locator, ServerRuntime runtime, String... args) {
        var stdout = new ByteArrayOutputStream();
        var stderr = new ByteArrayOutputStream();
        int exitCode = new LogicScopeCli(new MavenRepositoryInspector(), locator, runtime,
                new PrintStream(stdout), new PrintStream(stderr)).run(args);
        return new CliResult(exitCode, stdout.toString(), stderr.toString());
    }

    private record CliResult(int exitCode, String stdout, String stderr) {
    }

    private static final class FakeProcess extends Process {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch waitLatch = new CountDownLatch(1);
        private final AtomicBoolean alive = new AtomicBoolean(true);
        private final AtomicBoolean destroyed = new AtomicBoolean(false);
        private final boolean exitZeroAfterWait;

        FakeProcess() {
            this(true);
        }

        FakeProcess(boolean exitZeroAfterWait) {
            this.exitZeroAfterWait = exitZeroAfterWait;
        }

        boolean awaitStart(long timeout, TimeUnit unit) throws InterruptedException {
            return started.await(timeout, unit);
        }

        void releaseWait() {
            waitLatch.countDown();
        }

        boolean wasDestroyed() {
            return destroyed.get();
        }

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            started.countDown();
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public int waitFor() throws InterruptedException {
            started.countDown();
            waitLatch.await();
            alive.set(false);
            return exitZeroAfterWait ? 0 : 1;
        }

        @Override
        public int exitValue() {
            if (alive.get()) {
                throw new IllegalThreadStateException("process still alive");
            }
            return exitZeroAfterWait ? 0 : 1;
        }

        @Override
        public void destroy() {
            destroyed.set(true);
            alive.set(false);
            waitLatch.countDown();
        }

        @Override
        public Process destroyForcibly() {
            destroy();
            return this;
        }

        @Override
        public boolean isAlive() {
            return alive.get();
        }
    }
}
