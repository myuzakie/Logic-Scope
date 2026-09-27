package com.logicscope.runtime;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record TargetRuntimeConfig(
        Path workingDirectory,
        List<String> command,
        int port,
        Readiness readiness,
        Map<String, String> environment,
        Duration startupTimeout,
        Duration requestTimeout,
        Otel otel) {

    public TargetRuntimeConfig {
        workingDirectory = Objects.requireNonNull(workingDirectory, "workingDirectory").toAbsolutePath().normalize();
        command = List.copyOf(Objects.requireNonNull(command, "command"));
        if (command.isEmpty() || command.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Target command must contain non-blank arguments");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Target port must be between 1 and 65535");
        }
        readiness = Objects.requireNonNull(readiness, "readiness");
        environment = Map.copyOf(Objects.requireNonNull(environment, "environment"));
        startupTimeout = positive(startupTimeout, "startupTimeout");
        requestTimeout = positive(requestTimeout, "requestTimeout");
        otel = Objects.requireNonNull(otel, "otel");
    }

    private static Duration positive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    public record Readiness(Strategy strategy, String healthPath, Duration pollInterval) {
        public Readiness {
            strategy = Objects.requireNonNull(strategy, "strategy");
            healthPath = healthPath == null ? "" : healthPath.trim();
            pollInterval = positive(pollInterval, "pollInterval");
            if (strategy == Strategy.HTTP && (healthPath.isBlank() || !healthPath.startsWith("/"))) {
                throw new IllegalArgumentException("HTTP readiness requires an absolute health path");
            }
        }
    }

    public record Otel(String javaAgentVersion, String javaAgentSha256, Path javaAgentPath,
                       String endpoint, String serviceName, Map<String, String> resourceAttributes,
                       List<String> includeProperties) {
        public Otel {
            javaAgentVersion = requireText(javaAgentVersion, "javaAgentVersion");
            javaAgentSha256 = requireText(javaAgentSha256, "javaAgentSha256").toLowerCase();
            if (!javaAgentSha256.matches("[a-f0-9]{64}")) {
                throw new IllegalArgumentException("javaAgentSha256 must be a 64-character hexadecimal SHA-256 digest");
            }
            javaAgentPath = Objects.requireNonNull(javaAgentPath, "javaAgentPath").toAbsolutePath().normalize();
            endpoint = requireText(endpoint, "endpoint");
            serviceName = requireText(serviceName, "serviceName");
            resourceAttributes = Map.copyOf(Objects.requireNonNull(resourceAttributes, "resourceAttributes"));
            includeProperties = List.copyOf(Objects.requireNonNull(includeProperties, "includeProperties"));
        }
    }

    public enum Strategy {
        HTTP,
        TCP
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
