package com.logicscope.runtime;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class TargetProcessManager implements AutoCloseable {
    private final JavaAgentResolver agentResolver;
    private final ProcessFactory processFactory;
    private final PortProbe portProbe;
    private final CollectorProbe collectorProbe;
    private final OutputSink outputSink;
    private final Map<String, ManagedTarget> targets = new ConcurrentHashMap<>();

    public TargetProcessManager(JavaAgentResolver agentResolver, ProcessFactory processFactory,
                                PortProbe portProbe, OutputSink outputSink) {
        this(agentResolver, processFactory, portProbe, (endpoint, protocol, timeout) -> true, outputSink);
    }

    public TargetProcessManager(JavaAgentResolver agentResolver, ProcessFactory processFactory,
                                PortProbe portProbe, CollectorProbe collectorProbe, OutputSink outputSink) {
        this.agentResolver = Objects.requireNonNull(agentResolver, "agentResolver");
        this.processFactory = Objects.requireNonNull(processFactory, "processFactory");
        this.portProbe = Objects.requireNonNull(portProbe, "portProbe");
        this.collectorProbe = Objects.requireNonNull(collectorProbe, "collectorProbe");
        this.outputSink = Objects.requireNonNull(outputSink, "outputSink");
    }

    public static TargetProcessManager createDefault() {
        return new TargetProcessManager(new JavaAgentResolver(), new DefaultProcessFactory(),
                new SocketPortProbe(), new SocketCollectorProbe(), (id, stream, line) -> { });
    }

    public ManagedTarget start(TargetRuntimeConfig config) throws RuntimeStartException {
        return start(config, () -> false);
    }

    public ManagedTarget start(TargetRuntimeConfig config, BooleanSupplier cancelled) throws RuntimeStartException {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(cancelled, "cancelled");
        String id = UUID.randomUUID().toString();
        Path agent;
        try {
            agent = agentResolver.resolve(config.otel());
        } catch (IOException exception) {
            throw failure(id, Stage.RESOLVE_AGENT, "Configured OpenTelemetry Java Agent is unavailable or invalid",
                    "Provide a local agent artifact whose SHA-256 matches the configured digest.");
        }
        if (!Files.isDirectory(config.workingDirectory())) {
            throw failure(id, Stage.VALIDATE_CONFIG, "Target working directory is unavailable",
                    "Set workingDirectory to an existing local directory.");
        }
        if (!collectorProbe.isAvailable(config.otel().endpoint(), config.otel().protocol(), config.requestTimeout())) {
            throw failure(id, Stage.COLLECTOR_CHECK, "Configured local OpenTelemetry Collector is unavailable",
                    "Start the local Collector and verify its OTLP endpoint and protocol.");
        }
        if (!portProbe.isAvailable(config.port())) {
            throw failure(id, Stage.PORT_CHECK, "Target port is already in use",
                    "Stop the process using the configured port or choose another port.");
        }

        Map<String, String> environment;
        List<String> command;
        try {
            environment = resolveEnvironment(config.environment());
            command = buildCommand(config, agent);
        } catch (IllegalArgumentException exception) {
            throw failure(id, Stage.BUILD_COMMAND, "Target command or environment configuration is invalid",
                    "Use a Java command with structured arguments and valid environment references.");
        }

        Process process;
        try {
            process = processFactory.start(command, config.workingDirectory(), environment);
        } catch (IOException exception) {
            throw failure(id, Stage.START_PROCESS, "Target process could not be started",
                    "Check the executable, arguments, working directory, and local permissions.");
        }

        ManagedTarget target = new ManagedTarget(id, process, config);
        targets.put(id, target);
        pump(process.getInputStream(), id, "stdout", environment.values());
        pump(process.getErrorStream(), id, "stderr", environment.values());
        try {
            awaitReady(target, cancelled);
            return target;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            stop(target);
            throw failure(id, Stage.READINESS, "Target startup was interrupted", "Retry startup when the operation is not cancelled.");
        } catch (RuntimeStartException exception) {
            stop(target);
            throw exception;
        }
    }

    public State status(String id) {
        ManagedTarget target = requireTarget(id);
        synchronized (target) {
            if (!target.process.isAlive() && (target.status == State.STARTING || target.status == State.READY)) {
                target.status = State.FAILED;
                target.stage = Stage.PROCESS_EXIT;
                target.reason = "Target process exited unexpectedly (code " + safeExitValue(target.process) + ")";
                target.suggestion = "Inspect the target application's local logs and startup configuration.";
            }
            return target.status;
        }
    }

    public void stop(String id) {
        stop(requireTarget(id));
    }

    private void stop(ManagedTarget target) {
        synchronized (target) {
            if (target.status == State.STOPPED || target.status == State.STOPPING) {
                return;
            }
            target.status = State.STOPPING;
        }
        destroyOwnedProcess(target.process);
        synchronized (target) {
            target.status = State.STOPPED;
        }
        targets.remove(target.id, target);
    }

    public TargetStatus statusSnapshot(String id) {
        ManagedTarget target = requireTarget(id);
        State state = status(id);
        synchronized (target) {
            return new TargetStatus(target.id, state, target.stage, target.reason, target.suggestion,
                    target.process.isAlive());
        }
    }

    public int managedTargetCount() {
        return targets.size();
    }

    @Override
    public void close() {
        for (ManagedTarget target : List.copyOf(targets.values())) {
            stop(target);
        }
    }

    private void awaitReady(ManagedTarget target, BooleanSupplier cancelled)
            throws InterruptedException, RuntimeStartException {
        long deadline = System.nanoTime() + target.config.startupTimeout().toNanos();
        synchronized (target) {
            target.status = State.STARTING;
            target.stage = Stage.READINESS;
        }
        while (System.nanoTime() < deadline) {
            if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) {
                throw failure(target.id, Stage.READINESS, "Target startup was cancelled", "Retry startup when the operation is not cancelled.");
            }
            if (!target.process.isAlive()) {
                throw failure(target.id, Stage.PROCESS_EXIT, "Target process exited before becoming ready",
                        "Inspect the target application's local logs and startup configuration.");
            }
            if (isReady(target.config)) {
                synchronized (target) {
                    target.status = State.READY;
                    target.stage = Stage.READINESS;
                    target.reason = "Target passed its configured readiness check";
                    target.suggestion = "";
                }
                return;
            }
            Thread.sleep(target.config.readiness().pollInterval().toMillis());
        }
        throw failure(target.id, Stage.READINESS, "Target did not become ready before the startup timeout",
                "Check the health path, port, startup timeout, and target dependencies.");
    }

    private boolean isReady(TargetRuntimeConfig config) {
        if (config.readiness().strategy() == TargetRuntimeConfig.Strategy.TCP) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", config.port()), timeoutMillis(config.requestTimeout()));
                return true;
            } catch (IOException exception) {
                return false;
            }
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create("http://127.0.0.1:" + config.port()
                    + config.readiness().healthPath()).toURL().openConnection();
            int timeout = timeoutMillis(config.requestTimeout());
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
            connection.setRequestMethod("GET");
            return connection.getResponseCode() >= 200 && connection.getResponseCode() < 300;
        } catch (IOException exception) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void pump(InputStream stream, String id, String streamName, java.util.Collection<String> secrets) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String sanitized = line;
                    for (String secret : secrets) {
                        if (secret != null && !secret.isBlank()) {
                            sanitized = sanitized.replace(secret, "[REDACTED]");
                        }
                    }
                    outputSink.accept(id, streamName, sanitized);
                }
            } catch (IOException ignored) {
                // The child process closed its output stream.
            }
        }, "logicscope-target-" + streamName + "-" + id);
        thread.setDaemon(true);
        thread.start();
    }

    private static Map<String, String> resolveEnvironment(Map<String, String> references) {
        Map<String, String> resolved = new LinkedHashMap<>();
        String path = System.getenv("PATH");
        if (path != null) {
            resolved.put("PATH", path);
        }
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null) {
            resolved.put("JAVA_HOME", javaHome);
        }
        references.forEach((name, reference) -> {
            if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw new IllegalArgumentException("Invalid environment variable name");
            }
            String value;
            if (reference != null && reference.matches("\\$\\{[A-Za-z_][A-Za-z0-9_]*}")) {
                String referencedName = reference.substring(2, reference.length() - 1);
                value = System.getenv(referencedName);
                if (value == null) {
                    throw new IllegalArgumentException("Environment reference is not defined");
                }
            } else {
                value = Objects.requireNonNull(reference, "environment value");
            }
            resolved.put(name, value);
        });
        return Map.copyOf(resolved);
    }

    private static List<String> buildCommand(TargetRuntimeConfig config, Path agent) {
        List<String> targetCommand = config.command();
        if (targetCommand.stream().anyMatch(argument -> argument.contains("${"))) {
            throw new IllegalArgumentException("Command environment substitutions are not supported");
        }
        String executable = Path.of(targetCommand.getFirst()).getFileName().toString().toLowerCase();
        if (!executable.equals("java") && !executable.equals("java.exe")) {
            throw new IllegalArgumentException("OpenTelemetry Java Agent requires a Java executable");
        }
        List<String> command = new ArrayList<>();
        command.add(targetCommand.getFirst());
        command.add("-javaagent:" + agent);
        command.add("-Dotel.service.name=" + config.otel().serviceName());
        command.add("-Dotel.exporter.otlp.endpoint=" + config.otel().endpoint());
        command.add("-Dotel.exporter.otlp.protocol=" + config.otel().protocol().agentValue());
        if (!config.otel().resourceAttributes().isEmpty()) {
            command.add("-Dotel.resource.attributes=" + joinAttributes(config.otel().resourceAttributes()));
        }
        for (String property : config.otel().includeProperties()) {
            if (!property.startsWith("-Dotel.") || !property.contains("=")) {
                throw new IllegalArgumentException("Invalid OpenTelemetry system property");
            }
            command.add(property);
        }
        command.addAll(targetCommand.subList(1, targetCommand.size()));
        return List.copyOf(command);
    }

    private static String joinAttributes(Map<String, String> attributes) {
        return attributes.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(","));
    }

    private static int timeoutMillis(Duration timeout) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1, timeout.toMillis()));
    }

    private static RuntimeStartException failure(String id, Stage stage, String reason, String suggestion) {
        return new RuntimeStartException(new TargetStatus(id, State.FAILED, stage, reason, suggestion, false));
    }

    private ManagedTarget requireTarget(String id) {
        ManagedTarget target = targets.get(id);
        if (target == null) {
            throw new IllegalArgumentException("Target process is not managed by LogicScope");
        }
        return target;
    }

    private static int safeExitValue(Process process) {
        try {
            return process.exitValue();
        } catch (IllegalThreadStateException exception) {
            return -1;
        }
    }

    private static void destroyOwnedProcess(Process process) {
        descendants(process).forEach(ProcessHandle::destroy);
        process.destroy();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                descendants(process).forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException exception) {
            descendants(process).forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    private static java.util.stream.Stream<ProcessHandle> descendants(Process process) {
        try {
            return process.descendants();
        } catch (UnsupportedOperationException exception) {
            return java.util.stream.Stream.empty();
        }
    }

    public interface ProcessFactory {
        Process start(List<String> command, Path workingDirectory, Map<String, String> environment) throws IOException;
    }

    @FunctionalInterface
    public interface PortProbe {
        boolean isAvailable(int port);
    }

    @FunctionalInterface
    public interface CollectorProbe {
        boolean isAvailable(String endpoint, TargetRuntimeConfig.Protocol protocol, Duration timeout);
    }

    @FunctionalInterface
    public interface OutputSink {
        void accept(String targetId, String stream, String sanitizedLine);
    }

    public static final class DefaultProcessFactory implements ProcessFactory {
        @Override
        public Process start(List<String> command, Path workingDirectory, Map<String, String> environment) throws IOException {
            ProcessBuilder builder = new ProcessBuilder(command).directory(workingDirectory.toFile());
            builder.environment().clear();
            builder.environment().putAll(environment);
            return builder.start();
        }
    }

    public static final class SocketPortProbe implements PortProbe {
        @Override
        public boolean isAvailable(int port) {
            try (java.net.ServerSocket socket = new java.net.ServerSocket()) {
                socket.setReuseAddress(false);
                socket.bind(new InetSocketAddress("127.0.0.1", port));
                return true;
            } catch (IOException exception) {
                return false;
            }
        }
    }

    public static final class SocketCollectorProbe implements CollectorProbe {
        @Override
        public boolean isAvailable(String endpoint, TargetRuntimeConfig.Protocol protocol, Duration timeout) {
            try {
                URI uri = URI.create(endpoint);
                int port = uri.getPort();
                if (port < 1) {
                    port = protocol == TargetRuntimeConfig.Protocol.GRPC ? 4317 : 4318;
                }
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(uri.getHost(), port), timeoutMillis(timeout));
                    return true;
                }
            } catch (IOException | IllegalArgumentException exception) {
                return false;
            }
        }
    }

    public enum State {
        STARTING,
        READY,
        FAILED,
        STOPPING,
        STOPPED
    }

    public enum Stage {
        VALIDATE_CONFIG,
        RESOLVE_AGENT,
        COLLECTOR_CHECK,
        PORT_CHECK,
        BUILD_COMMAND,
        START_PROCESS,
        READINESS,
        PROCESS_EXIT
    }

    public record TargetStatus(String id, State state, Stage stage, String reason,
                               String suggestion, boolean processAlive) {
    }

    public static final class ManagedTarget {
        private final String id;
        private final Process process;
        private final TargetRuntimeConfig config;
        private State status = State.STARTING;
        private Stage stage = Stage.START_PROCESS;
        private String reason = "Target process is starting";
        private String suggestion = "";

        private ManagedTarget(String id, Process process, TargetRuntimeConfig config) {
            this.id = id;
            this.process = process;
            this.config = config;
        }

        public String id() {
            return id;
        }

        public TargetStatus status() {
            return new TargetStatus(id, status, stage, reason, suggestion, process.isAlive());
        }
    }

    public static final class RuntimeStartException extends Exception {
        private final TargetStatus status;

        private RuntimeStartException(TargetStatus status) {
            super(status.stage() + ": " + status.reason() + ". " + status.suggestion());
            this.status = status;
        }

        public TargetStatus status() {
            return status;
        }
    }
}
