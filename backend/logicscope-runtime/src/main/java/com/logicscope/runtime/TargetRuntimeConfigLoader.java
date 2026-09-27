package com.logicscope.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TargetRuntimeConfigLoader {
    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

    public TargetRuntimeConfig load(Path configFile) throws IOException {
        JsonNode root = yaml.readTree(Files.readString(configFile));
        JsonNode runtime = required(root, "target");
        Path projectRoot = configFile.toAbsolutePath().normalize().getParent().getParent();
        Path workingDirectory = resolve(projectRoot, text(runtime, "workingDirectory", "."));
        List<String> command = strings(required(runtime, "command"));
        int port = integer(runtime, "port");

        JsonNode readinessNode = required(runtime, "readiness");
        TargetRuntimeConfig.Readiness readiness = new TargetRuntimeConfig.Readiness(
                TargetRuntimeConfig.Strategy.valueOf(text(readinessNode, "strategy", "HTTP").toUpperCase()),
                text(readinessNode, "healthPath", "/actuator/health"),
                Duration.ofMillis(number(readinessNode, "pollIntervalMs", 500)));

        Map<String, String> environment = stringMap(optional(runtime, "environment"));
        Duration startupTimeout = Duration.ofMillis(number(runtime, "startupTimeoutMs", 120000));
        Duration requestTimeout = Duration.ofMillis(number(runtime, "requestTimeoutMs", 2000));

        JsonNode otelNode = required(runtime, "otel");
        TargetRuntimeConfig.Protocol protocol = TargetRuntimeConfig.Protocol.valueOf(
                text(otelNode, "protocol", "GRPC").toUpperCase());
        String defaultEndpoint = protocol == TargetRuntimeConfig.Protocol.GRPC
                ? "http://127.0.0.1:4317" : "http://127.0.0.1:4318";
        TargetRuntimeConfig.Otel otel = new TargetRuntimeConfig.Otel(
                text(otelNode, "javaAgentVersion", null),
                text(otelNode, "javaAgentSha256", null),
                resolve(projectRoot, text(otelNode, "javaAgentPath", null)),
                text(otelNode, "endpoint", defaultEndpoint),
                protocol,
                text(otelNode, "serviceName", "target-application"),
                stringMap(optional(otelNode, "resourceAttributes")),
                strings(optional(otelNode, "includeProperties")));
        return new TargetRuntimeConfig(workingDirectory, command, port, readiness, environment,
                startupTimeout, requestTimeout, otel);
    }

    private static JsonNode required(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("Invalid target runtime configuration: missing " + field);
        }
        return value;
    }

    private static JsonNode optional(JsonNode node, String field) {
        return node == null ? null : node.get(field);
    }

    private static String text(JsonNode node, String field, String defaultValue) {
        JsonNode value = optional(node, field);
        if (value == null || value.isNull()) {
            if (defaultValue == null) {
                throw new IllegalArgumentException("Invalid target runtime configuration: missing " + field);
            }
            return defaultValue;
        }
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Invalid target runtime configuration: invalid " + field);
        }
        return value.asText();
    }

    private static long number(JsonNode node, String field, long defaultValue) {
        JsonNode value = optional(node, field);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        if (!value.canConvertToLong()) {
            throw new IllegalArgumentException("Invalid target runtime configuration: invalid " + field);
        }
        return value.asLong();
    }

    private static int integer(JsonNode node, String field) {
        long value = number(node, field, -1);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid target runtime configuration: invalid " + field);
        }
        return (int) value;
    }

    private static List<String> strings(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new IllegalArgumentException("Invalid target runtime configuration: expected a list");
        }
        List<String> result = new ArrayList<>();
        node.forEach(value -> {
            if (!value.isTextual()) {
                throw new IllegalArgumentException("Invalid target runtime configuration: expected text values");
            }
            result.add(value.asText());
        });
        return List.copyOf(result);
    }

    private static Map<String, String> stringMap(JsonNode node) {
        if (node == null || node.isNull()) {
            return Map.of();
        }
        if (!node.isObject()) {
            throw new IllegalArgumentException("Invalid target runtime configuration: expected a map");
        }
        Map<String, String> result = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> {
            if (!entry.getValue().isTextual()) {
                throw new IllegalArgumentException("Invalid target runtime configuration: map values must be text");
            }
            result.put(entry.getKey(), entry.getValue().asText());
        });
        return Map.copyOf(result);
    }

    private static Path resolve(Path root, String configuredPath) {
        Path path = Path.of(configuredPath);
        return (path.isAbsolute() ? path : root.resolve(path)).toAbsolutePath().normalize();
    }
}
