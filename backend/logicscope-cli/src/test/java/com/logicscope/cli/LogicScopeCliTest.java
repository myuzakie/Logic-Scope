package com.logicscope.cli;

import com.logicscope.discovery.MavenRepositoryInspector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

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
        assertTrue(result.stderr().contains("./logicscope scan <repository>"));
    }

    @Test
    void rejectsUnknownCommand() {
        CliResult result = run("something", tempDir.toString());

        assertEquals(2, result.exitCode());
        assertTrue(result.stderr().contains("Unknown command: something"));
        assertTrue(result.stderr().contains("Usage:"));
    }

    private static CliResult run(String... args) {
        var stdout = new ByteArrayOutputStream();
        var stderr = new ByteArrayOutputStream();
        int exitCode = new LogicScopeCli(new MavenRepositoryInspector(),
                new PrintStream(stdout), new PrintStream(stderr)).run(args);
        return new CliResult(exitCode, stdout.toString(), stderr.toString());
    }

    private record CliResult(int exitCode, String stdout, String stderr) {
    }
}
