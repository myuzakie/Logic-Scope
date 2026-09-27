package com.logicscope.project;

import com.logicscope.discovery.MavenRepositoryInspector;
import com.logicscope.domain.CapabilityReport;
import com.logicscope.domain.CapabilityStatus;
import com.logicscope.domain.ProjectRecord;
import com.logicscope.domain.ProjectSourceType;
import com.logicscope.domain.ProjectStatus;
import com.logicscope.domain.RepositoryCapability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProjectServiceTest {

    @TempDir
    Path tempDir;

    WorkspaceManager workspace;
    FileProjectRegistry registry;
    ProjectService service;

    @BeforeEach
    void setUp() {
        workspace = new WorkspaceManager(tempDir.resolve("data"));
        registry = new FileProjectRegistry(workspace);
        service = new ProjectService(registry, workspace, new GitCloneOperation(), new MavenRepositoryInspector());
    }

    // ---- loadLocal ----

    @Test
    void loadLocalRegistersLocalPath() throws IOException {
        Path repo = createMavenSpringProject(tempDir.resolve("my-repo"));
        ProjectRecord record = service.loadLocal(repo);

        assertEquals(ProjectStatus.READY, record.status());
        assertEquals(ProjectSourceType.LOCAL, record.sourceType());
        assertEquals(repo.toAbsolutePath().normalize().toString(), record.localPath());
        assertNull(record.sourceUrl());
        assertNull(record.commitSha()); // local path — no git SHA
        assertNull(record.failureReason());
    }

    @Test
    void loadLocalDoesNotModifySource() throws IOException {
        Path repo = createMavenSpringProject(tempDir.resolve("source-repo"));
        long modifiedBefore = Files.getLastModifiedTime(repo.resolve("pom.xml")).toMillis();
        service.loadLocal(repo);
        long modifiedAfter = Files.getLastModifiedTime(repo.resolve("pom.xml")).toMillis();

        assertEquals(modifiedBefore, modifiedAfter, "pom.xml must not be modified by loadLocal");
    }

    @Test
    void loadLocalIsIdempotent() throws IOException {
        Path repo = createMavenSpringProject(tempDir.resolve("idempotent-repo"));
        ProjectRecord first = service.loadLocal(repo);
        ProjectRecord second = service.loadLocal(repo);

        assertEquals(first.id(), second.id());
        assertEquals(1, registry.findAll().size());
    }

    @Test
    void loadLocalRejectsNonexistentPath() {
        Path missing = tempDir.resolve("does-not-exist");
        assertThrows(IllegalArgumentException.class, () -> service.loadLocal(missing));
    }

    @Test
    void loadLocalRejectsFile() throws IOException {
        Path file = Files.writeString(tempDir.resolve("not-a-dir.txt"), "content");
        assertThrows(IllegalArgumentException.class, () -> service.loadLocal(file));
    }

    // ---- loadGit ----

    @Test
    void loadGitRejectsInvalidUrl() {
        assertThrows(IllegalArgumentException.class,
                () -> service.loadGit("http://github.com/example/repo.git"));
        assertThrows(IllegalArgumentException.class,
                () -> service.loadGit("file:///home/user/repo"));
    }

    @Test
    void loadGitRecordsFailedStatusOnCloneError() throws IOException {
        // Use a fake clone that always fails
        ProjectService failingService = new ProjectService(
                registry, workspace,
                new GitCloneOperation((timeout, command) ->
                        new GitCloneOperation.ProcessResult(128, "")),
                new MavenRepositoryInspector());

        ProjectRecord record = failingService.loadGit("https://github.com/example/does-not-exist.git");

        assertEquals(ProjectStatus.FAILED, record.status());
        assertNotNull(record.failureReason());
        assertFalse(record.failureReason().isBlank());
    }

    @Test
    void loadGitFailureReasonDoesNotContainCredentials() throws IOException {
        ProjectService failingService = new ProjectService(
                registry, workspace,
                new GitCloneOperation((timeout, command) ->
                        new GitCloneOperation.ProcessResult(128, "secret-token in stderr")),
                new MavenRepositoryInspector());

        ProjectRecord record = failingService.loadGit("https://github.com/example/repo.git");

        assertFalse(record.failureReason().contains("secret-token"),
                "Failure reason must not expose raw git output");
    }

    // ---- list / find ----

    @Test
    void listReturnsEmptyWhenNoProjects() throws IOException {
        assertEquals(List.of(), service.list());
    }

    @Test
    void listReturnsAllRegisteredProjects() throws IOException {
        service.loadLocal(createMavenSpringProject(tempDir.resolve("repo-a")));
        service.loadLocal(createMavenSpringProject(tempDir.resolve("repo-b")));
        assertEquals(2, service.list().size());
    }

    @Test
    void findReturnsEmptyForUnknownId() throws IOException {
        assertTrue(service.find("proj-doesnotexist").isEmpty());
    }

    // ---- inspect ----

    @Test
    void inspectReturnsCapabilityReportForRegisteredProject() throws IOException {
        Path repo = createMavenSpringProject(tempDir.resolve("inspect-repo"));
        ProjectRecord record = service.loadLocal(repo);

        CapabilityReport report = service.inspect(record.id().value());

        assertNotNull(report);
        assertEquals(CapabilityStatus.SUPPORTED, report.assessment(RepositoryCapability.JAVA).status());
        assertEquals(CapabilityStatus.SUPPORTED, report.assessment(RepositoryCapability.MAVEN).status());
        assertEquals(CapabilityStatus.SUPPORTED, report.assessment(RepositoryCapability.SPRING_BOOT).status());
    }

    @Test
    void inspectThrowsForUnknownProject() {
        assertThrows(IllegalArgumentException.class, () -> service.inspect("proj-doesnotexist"));
    }

    @Test
    void inspectThrowsForFailedProject() throws IOException {
        ProjectService failingService = new ProjectService(
                registry, workspace,
                new GitCloneOperation((timeout, command) ->
                        new GitCloneOperation.ProcessResult(128, "")),
                new MavenRepositoryInspector());

        ProjectRecord failed = failingService.loadGit("https://github.com/example/repo.git");
        assertEquals(ProjectStatus.FAILED, failed.status());

        assertThrows(IllegalArgumentException.class, () -> failingService.inspect(failed.id().value()));
    }

    @Test
    void inspectReflectsActualCapabilitiesOnly() throws IOException {
        Path repo = tempDir.resolve("no-spring-repo");
        Files.createDirectories(repo.resolve("src/main/java"));
        Files.writeString(repo.resolve("src/main/java/App.java"), "class App {}\n");
        Files.writeString(repo.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <properties><java.version>21</java.version></properties>
                </project>
                """);

        ProjectRecord record = service.loadLocal(repo);
        CapabilityReport report = service.inspect(record.id().value());

        // Spring Boot must not be reported SUPPORTED if it is not in the POM
        assertNotEquals(CapabilityStatus.SUPPORTED,
                report.assessment(RepositoryCapability.SPRING_BOOT).status());
    }

    // ---- helper ----

    private static Path createMavenSpringProject(Path dir) throws IOException {
        Files.createDirectories(dir.resolve("src/main/java"));
        Files.writeString(dir.resolve("src/main/java/App.java"), "class App {}\n");
        Files.writeString(dir.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <properties><java.version>21</java.version></properties>
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-web</artifactId>
                      <version>3.4.5</version>
                    </dependency>
                  </dependencies>
                </project>
                """);
        return dir;
    }
}
