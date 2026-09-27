package com.logicscope.runtime;

import com.logicscope.runtime.TargetProcessManager.ManagedTarget;
import com.logicscope.runtime.TargetProcessManager.RuntimeStartException;
import com.logicscope.runtime.TargetProcessManager.Stage;
import com.logicscope.runtime.TargetProcessManager.State;
import com.logicscope.runtime.TargetProcessManager.TargetStatus;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetProcessManagerTest {
    @TempDir
    Path tempDir;

    private HttpServer server;
    private TargetProcessManager manager;

    @AfterEach
    void tearDown() {
        if (manager != null) {
            manager.close();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void startsWithAgentBeforeApplicationArgumentsAndStopsOwnedProcess() throws Exception {
        Path agent = createAgent();
        startHealthServer();
        FakeProcess process = new FakeProcess();
        AtomicReference<List<String>> command = new AtomicReference<>();
        AtomicReference<Map<String, String>> environment = new AtomicReference<>();
        manager = new TargetProcessManager(new JavaAgentResolver(), (args, directory, env) -> {
            command.set(args);
            environment.set(env);
            return process;
        }, port -> true, (id, stream, line) -> { });

        ManagedTarget target = manager.start(config(agent, server.getAddress().getPort(),
                Map.of("TOKEN", "${PATH}")), () -> false);

        assertEquals(State.READY, target.status().state());
        assertTrue(command.get().get(1).startsWith("-javaagent:"));
        assertEquals("-Dotel.resource.attributes=deployment.environment=local", command.get().get(5));
        assertEquals("-jar", command.get().get(6));
        assertEquals(System.getenv("PATH"), environment.get().get("TOKEN"));
        assertFalse(environment.get().containsKey("UNRELATED_SECRET"));
        manager.stop(target.id());
        assertTrue(process.destroyed.get());
        assertFalse(process.alive.get());
    }

    @Test
    void rejectsAgentChecksumBeforeStartingTarget() throws Exception {
        Path agent = createAgent();
        AtomicBoolean started = new AtomicBoolean();
        manager = new TargetProcessManager(new JavaAgentResolver(), (args, directory, env) -> {
            started.set(true);
            return new FakeProcess();
        }, port -> true, (id, stream, line) -> { });
        TargetRuntimeConfig base = config(agent, 43210, Map.of());
        TargetRuntimeConfig invalid = new TargetRuntimeConfig(base.workingDirectory(), base.command(), base.port(),
                base.readiness(), base.environment(), base.startupTimeout(), base.requestTimeout(),
                new TargetRuntimeConfig.Otel("2.14.0", "0".repeat(64), agent,
                        "http://127.0.0.1:4318", TargetRuntimeConfig.Protocol.HTTP_PROTOBUF,
                        "demo", Map.of(), List.of()));

        RuntimeStartException exception = assertThrows(RuntimeStartException.class, () -> manager.start(invalid));

        assertEquals(Stage.RESOLVE_AGENT, exception.status().stage());
        assertFalse(started.get());
    }

    @Test
    void readinessFailureCleansUpChildProcess() throws Exception {
        Path agent = createAgent();
        FakeProcess process = new FakeProcess();
        manager = new TargetProcessManager(new JavaAgentResolver(), (args, directory, env) -> process,
                port -> true, (id, stream, line) -> { });

        RuntimeStartException exception = assertThrows(RuntimeStartException.class,
                () -> manager.start(config(agent, 43211, Map.of()), () -> false));

        assertEquals(Stage.READINESS, exception.status().stage());
        assertTrue(process.destroyed.get());
        assertEquals(0, manager.managedTargetCount());
    }

    @Test
    void cancellationDuringStartupStopsOwnedProcess() throws Exception {
        Path agent = createAgent();
        FakeProcess process = new FakeProcess();
        manager = new TargetProcessManager(new JavaAgentResolver(), (args, directory, env) -> process,
                port -> true, (id, stream, line) -> { });

        RuntimeStartException exception = assertThrows(RuntimeStartException.class,
                () -> manager.start(config(agent, 43212, Map.of()), () -> true));

        assertTrue(exception.status().reason().contains("cancelled"));
        assertTrue(process.destroyed.get());
    }

    @Test
    void reportsUnexpectedCrashAsFailed() throws Exception {
        Path agent = createAgent();
        startHealthServer();
        FakeProcess process = new FakeProcess();
        manager = new TargetProcessManager(new JavaAgentResolver(), (args, directory, env) -> process,
                port -> true, (id, stream, line) -> { });
        ManagedTarget target = manager.start(config(agent, server.getAddress().getPort(), Map.of()), () -> false);
        process.exit(17);

        TargetStatus status = manager.statusSnapshot(target.id());

        assertEquals(State.FAILED, status.state());
        assertEquals(Stage.PROCESS_EXIT, status.stage());
        assertTrue(status.reason().contains("17"));
    }

    @Test
    void rejectsOccupiedPortWithoutStartingProcess() throws Exception {
        Path agent = createAgent();
        AtomicBoolean started = new AtomicBoolean();
        manager = new TargetProcessManager(new JavaAgentResolver(), (args, directory, env) -> {
            started.set(true);
            return new FakeProcess();
        }, port -> false, (id, stream, line) -> { });

        RuntimeStartException exception = assertThrows(RuntimeStartException.class,
                () -> manager.start(config(agent, 43213, Map.of()), () -> false));

        assertEquals(Stage.PORT_CHECK, exception.status().stage());
        assertFalse(started.get());
    }

    @Test
    void redactsResolvedEnvironmentValuesFromChildOutput() throws Exception {
        Path agent = createAgent();
        startHealthServer();
        FakeProcess process = new FakeProcess("credential-value\n");
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        manager = new TargetProcessManager(new JavaAgentResolver(), (args, directory, env) -> process,
                port -> true, (id, stream, line) -> captured.writeBytes((line + "\n").getBytes()));

        manager.start(config(agent, server.getAddress().getPort(), Map.of("TOKEN", "credential-value")), () -> false);
        assertTrue(process.outputRead.await(2, TimeUnit.SECONDS));

        assertFalse(captured.toString().contains("credential-value"));
        assertTrue(captured.toString().contains("[REDACTED]"));
    }

    private TargetRuntimeConfig config(Path agent, int port, Map<String, String> environment) throws IOException {
        String digest = JavaAgentResolver.sha256(agent);
        return new TargetRuntimeConfig(tempDir, List.of("java", "-jar", "petclinic-rest.jar"), port,
                new TargetRuntimeConfig.Readiness(TargetRuntimeConfig.Strategy.HTTP, "/health", Duration.ofMillis(10)),
                environment, Duration.ofMillis(80), Duration.ofMillis(50),
                new TargetRuntimeConfig.Otel("2.14.0", digest, agent, "http://127.0.0.1:4318",
                        TargetRuntimeConfig.Protocol.HTTP_PROTOBUF, "spring-petclinic-rest",
                        Map.of("deployment.environment", "local"), List.of()));
    }

    private Path createAgent() throws IOException {
        return Files.writeString(tempDir.resolve("opentelemetry-javaagent.jar"), "test-agent-artifact");
    }

    private void startHealthServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
    }

    private static final class FakeProcess extends Process {
        private final AtomicBoolean alive = new AtomicBoolean(true);
        private final AtomicBoolean destroyed = new AtomicBoolean();
        private final CountDownLatch outputRead = new CountDownLatch(1);
        private final InputStream stdout;
        private volatile int exitCode;

        private FakeProcess() {
            this("");
        }

        private FakeProcess(String output) {
            stdout = new ByteArrayInputStream(output.getBytes());
        }

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            outputRead.countDown();
            return stdout;
        }

        @Override
        public InputStream getErrorStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public int waitFor() throws InterruptedException {
            while (alive.get()) {
                Thread.sleep(5);
            }
            return exitCode;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
            long deadline = System.nanoTime() + unit.toNanos(timeout);
            while (alive.get() && System.nanoTime() < deadline) {
                Thread.sleep(5);
            }
            return !alive.get();
        }

        @Override
        public int exitValue() {
            if (alive.get()) {
                throw new IllegalThreadStateException("process is alive");
            }
            return exitCode;
        }

        @Override
        public void destroy() {
            destroyed.set(true);
            alive.set(false);
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

        private void exit(int code) {
            exitCode = code;
            alive.set(false);
        }
    }
}
