package com.logicscope.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TargetRuntimeConfigLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void loadsStructuredTargetConfigurationAndLocalCollectorEndpoint() throws Exception {
        Path configFile = Files.createDirectories(tempDir.resolve(".logicscope"))
                .resolve("config.yaml");
        Files.writeString(configFile, """
                target:
                  workingDirectory: .
                  command: [java, -jar, spring-petclinic-rest.jar]
                  port: 9966
                  startupTimeoutMs: 45000
                  requestTimeoutMs: 1500
                  readiness:
                    strategy: http
                    healthPath: /actuator/health
                    pollIntervalMs: 250
                  environment:
                    API_TOKEN: ${API_TOKEN}
                  otel:
                    javaAgentVersion: 2.14.0
                    javaAgentSha256: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
                    javaAgentPath: .logicscope/cache/opentelemetry-javaagent.jar
                    endpoint: http://127.0.0.1:4318
                    protocol: http_protobuf
                    serviceName: spring-petclinic-rest
                    resourceAttributes:
                      deployment.environment: local
                    includeProperties: []
                """);

        TargetRuntimeConfig config = new TargetRuntimeConfigLoader().load(configFile);

        assertEquals(tempDir, config.workingDirectory());
        assertEquals(List.of("java", "-jar", "spring-petclinic-rest.jar"), config.command());
        assertEquals(Duration.ofSeconds(45), config.startupTimeout());
        assertEquals("${API_TOKEN}", config.environment().get("API_TOKEN"));
        assertEquals(TargetRuntimeConfig.Protocol.HTTP_PROTOBUF, config.otel().protocol());
        assertEquals("http://127.0.0.1:4318", config.otel().endpoint());
        assertEquals(Map.of("deployment.environment", "local"), config.otel().resourceAttributes());
    }
}
