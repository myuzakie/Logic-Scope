package com.logicscope.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.awt.Desktop;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class ServerRuntime {
    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration HEALTH_POLL = Duration.ofMillis(500);
    private static final String DATASOURCE_EXCLUDES =
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration";

    private final ProcessFactory processFactory;
    private final HealthProbe healthProbe;
    private final PortProbe portProbe;
    private final PrintStream output;
    private final PrintStream error;
    private final BrowserOpener browserOpener;

    ServerRuntime(ProcessFactory processFactory, HealthProbe healthProbe, PortProbe portProbe,
                  PrintStream output, PrintStream error) {
        this(processFactory, healthProbe, portProbe, output, error, dashboardUri -> false);
    }

    ServerRuntime(ProcessFactory processFactory, HealthProbe healthProbe, PortProbe portProbe,
                  PrintStream output, PrintStream error, BrowserOpener browserOpener) {
        this.processFactory = processFactory;
        this.healthProbe = healthProbe;
        this.portProbe = portProbe;
        this.output = output;
        this.error = error;
        this.browserOpener = browserOpener;
    }

    static ServerRuntime createDefault(PrintStream output, PrintStream error) {
        return new ServerRuntime(new DefaultProcessFactory(), new HttpHealthProbe(), new SocketPortProbe(),
                output, error, ServerRuntime::openBrowser);
    }

    int startAndAwait(Path appJar, int port) {
        return startAndAwait(appJar, port, Path.of(".").toAbsolutePath().normalize());
    }

    int startAndAwait(Path appJar, int port, Path targetProject) {
        if (!portProbe.isAvailable(port)) {
            error.println("Port " + port + " is already in use.");
            return 1;
        }

        String javaBin = resolveJavaBinary();
        List<String> command = new ArrayList<>();
        command.add(javaBin);
        command.add("-jar");
        command.add(appJar.toString());
        command.add("--server.port=" + port);
        command.add("--logicscope.target-project=" + targetProject.toAbsolutePath().normalize());
        command.add("--spring.autoconfigure.exclude=" + DATASOURCE_EXCLUDES);
        command.add("--spring.flyway.enabled=false");

        Process process;
        try {
            process = processFactory.start(command, Map.of("SERVER_PORT", String.valueOf(port)));
        } catch (IOException exception) {
            error.println("Failed to start LogicScope server: " + exception.getMessage());
            return 1;
        }

        AtomicBoolean shuttingDown = new AtomicBoolean(false);
        Thread shutdownHook = new Thread(() -> {
            shuttingDown.set(true);
            destroyProcess(process);
        }, "logicscope-run-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        Thread stdoutPump = pumpStream(process.getInputStream(), output);
        Thread stderrPump = pumpStream(process.getErrorStream(), error);
        stdoutPump.start();
        stderrPump.start();

        URI healthUri = URI.create("http://localhost:" + port + "/api/health");
        boolean ready;
        try {
            ready = healthProbe.waitUntilReady(healthUri, HEALTH_TIMEOUT, HEALTH_POLL, () -> process.isAlive());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            destroyProcess(process);
            removeShutdownHookQuietly(shutdownHook);
            error.println("Interrupted while waiting for LogicScope server readiness.");
            return 1;
        }

        if (!ready) {
            destroyProcess(process);
            removeShutdownHookQuietly(shutdownHook);
            if (!process.isAlive()) {
                error.println("LogicScope server exited before becoming ready (exit code "
                        + process.exitValue() + ").");
            } else {
                error.println("Timed out waiting for LogicScope health endpoint: " + healthUri);
            }
            return 1;
        }

        URI dashboardUri = URI.create("http://localhost:" + port + "/dashboard/");
        output.println();
        output.println("LogicScope dashboard is ready:");
        output.println(dashboardUri);
        if (!browserOpener.open(dashboardUri)) {
            output.println("Open this URL in your browser to scan the project.");
        }
        output.println();
        output.println("Press Ctrl+C to stop.");

        try {
            int exitCode = process.waitFor();
            removeShutdownHookQuietly(shutdownHook);
            if (shuttingDown.get()) {
                return 0;
            }
            return exitCode == 0 ? 0 : 1;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            destroyProcess(process);
            removeShutdownHookQuietly(shutdownHook);
            return 1;
        }
    }

    private static boolean openBrowser(URI dashboardUri) {
        if (!Desktop.isDesktopSupported()) {
            return false;
        }
        try {
            Desktop.getDesktop().browse(dashboardUri);
            return true;
        } catch (IOException | UnsupportedOperationException | SecurityException exception) {
            return false;
        }
    }

    private static String resolveJavaBinary() {
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isBlank()) {
            Path candidate = Path.of(javaHome, "bin", "java");
            if (candidate.toFile().canExecute()) {
                return candidate.toString();
            }
        }
        return "java";
    }

    private static Thread pumpStream(InputStream input, PrintStream destination) {
        return new Thread(() -> {
            byte[] buffer = new byte[4096];
            try {
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    destination.write(buffer, 0, read);
                    destination.flush();
                }
            } catch (IOException ignored) {
                // Process ended.
            }
        }, "logicscope-process-pump");
    }

    private static void destroyProcess(Process process) {
        process.destroy();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private static void removeShutdownHookQuietly(Thread hook) {
        try {
            Runtime.getRuntime().removeShutdownHook(hook);
        } catch (IllegalStateException ignored) {
            // JVM already shutting down.
        }
    }

    @FunctionalInterface
    interface BrowserOpener {
        boolean open(URI dashboardUri);
    }

    @FunctionalInterface
    interface ProcessFactory {
        Process start(List<String> command, Map<String, String> environment) throws IOException;
    }

    @FunctionalInterface
    interface HealthProbe {
        boolean waitUntilReady(URI healthUri, Duration timeout, Duration pollInterval, AliveCheck aliveCheck)
                throws InterruptedException;
    }

    @FunctionalInterface
    interface AliveCheck {
        boolean isAlive();
    }

    @FunctionalInterface
    interface PortProbe {
        boolean isAvailable(int port);
    }

    static final class DefaultProcessFactory implements ProcessFactory {
        @Override
        public Process start(List<String> command, Map<String, String> environment) throws IOException {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.environment().putAll(environment);
            builder.redirectErrorStream(false);
            return builder.start();
        }
    }

    static final class HttpHealthProbe implements HealthProbe {
        @Override
        public boolean waitUntilReady(URI healthUri, Duration timeout, Duration pollInterval, AliveCheck aliveCheck)
                throws InterruptedException {
            long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                if (!aliveCheck.isAlive()) {
                    return false;
                }
                if (isHealthy(healthUri)) {
                    return true;
                }
                Thread.sleep(pollInterval.toMillis());
            }
            return false;
        }

        private static boolean isHealthy(URI healthUri) {
            HttpURLConnection connection = null;
            try {
                URL url = healthUri.toURL();
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(1000);
                connection.setReadTimeout(1000);
                connection.setRequestMethod("GET");
                int status = connection.getResponseCode();
                if (status != 200) {
                    return false;
                }
                try (InputStream stream = connection.getInputStream()) {
                    String body = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                    return body.contains("\"status\"") && body.contains("UP");
                }
            } catch (IOException exception) {
                return false;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
    }

    static final class SocketPortProbe implements PortProbe {
        @Override
        public boolean isAvailable(int port) {
            try (ServerSocket socket = new ServerSocket()) {
                socket.setReuseAddress(false);
                socket.bind(new InetSocketAddress("127.0.0.1", port));
                return true;
            } catch (IOException exception) {
                return false;
            }
        }
    }
}
