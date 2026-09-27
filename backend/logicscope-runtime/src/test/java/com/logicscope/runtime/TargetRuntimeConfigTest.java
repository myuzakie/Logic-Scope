package com.logicscope.runtime;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TargetRuntimeConfigTest {
    @Test
    void validatesRuntimeBoundsAndNormalizesWorkingDirectory() {
        TargetRuntimeConfig config = new TargetRuntimeConfig(Path.of("."), List.of("java", "-jar", "app.jar"),
                8080, new TargetRuntimeConfig.Readiness(TargetRuntimeConfig.Strategy.HTTP, "/actuator/health",
                Duration.ofMillis(250)), Map.of(), Duration.ofSeconds(30), Duration.ofSeconds(2),
                new TargetRuntimeConfig.Otel("2.14.0", "a".repeat(64), Path.of("agent.jar"),
                        "http://127.0.0.1:4318", TargetRuntimeConfig.Protocol.HTTP_PROTOBUF,
                        "spring-petclinic-rest", Map.of(), List.of()));

        assertEquals(Path.of(".").toAbsolutePath().normalize(), config.workingDirectory());
        assertThrows(IllegalArgumentException.class, () -> new TargetRuntimeConfig(Path.of("."), List.of(),
                8080, config.readiness(), Map.of(), Duration.ofSeconds(1), Duration.ofSeconds(1), config.otel()));
        assertThrows(IllegalArgumentException.class, () -> new TargetRuntimeConfig.Readiness(
                TargetRuntimeConfig.Strategy.HTTP, "health", Duration.ofMillis(1)));
    }
}
