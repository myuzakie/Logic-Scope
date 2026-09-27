package com.logicscope.project;

import com.logicscope.discovery.MavenRepositoryInspector;
import com.logicscope.domain.ProjectRecord;
import com.logicscope.domain.ProjectSourceType;
import com.logicscope.domain.ProjectStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the CLI-level load/projects/inspect commands via {@link ProjectService}.
 *
 * <p>These tests drive the service layer directly (not through the full CLI) so they stay
 * focused on project-management behavior without needing a running CLI binary.
 */
class ProjectServiceCliIntegrationTest {

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

    @Test
    void loadLocalAndThenListAndThenInspect() throws IOException {
        Path repo = createSpringRepo(tempDir.resolve("my-project"));

        // load
        ProjectRecord loaded = service.loadLocal(repo);
        assertEquals(ProjectStatus.READY, loaded.status());
        assertEquals("my-project", loaded.name());

        // list
        List<ProjectRecord> all = service.list();
        assertEquals(1, all.size());
        assertEquals(loaded.id(), all.get(0).id());

        // find by id
        Optional<ProjectRecord> found = service.find(loaded.id().value());
        assertTrue(found.isPresent());
        assertEquals(ProjectSourceType.LOCAL, found.get().sourceType());

        // inspect
        var report = service.inspect(loaded.id().value());
        assertTrue(report.isSupported(), "Spring+Java+Maven repo must be supported");
    }

    @Test
    void repeatedLoadLocalReturnsExistingRecord() throws IOException {
        Path repo = createSpringRepo(tempDir.resolve("stable-repo"));
        ProjectRecord first = service.loadLocal(repo);
        ProjectRecord second = service.loadLocal(repo);
        assertEquals(first.id(), second.id());
        assertEquals(1, service.list().size());
    }

    @Test
    void loadGitWithCloneFailureSavesFailedStatus() throws IOException {
        ProjectService failingService = new ProjectService(
                registry, workspace,
                new GitCloneOperation((timeout, cmd) -> new GitCloneOperation.ProcessResult(128, "")),
                new MavenRepositoryInspector());

        ProjectRecord record = failingService.loadGit("https://github.com/example/bad-repo.git");
        assertEquals(ProjectStatus.FAILED, record.status());

        // Should appear in list
        assertEquals(1, service.list().size());
    }

    @Test
    void retryAfterFailureReplacesRecord() throws IOException {
        // First attempt fails
        ProjectService failingService = new ProjectService(
                registry, workspace,
                new GitCloneOperation((timeout, cmd) -> new GitCloneOperation.ProcessResult(128, "")),
                new MavenRepositoryInspector());
        ProjectRecord failed = failingService.loadGit("https://github.com/example/retry-repo.git");
        assertEquals(ProjectStatus.FAILED, failed.status());

        // Second attempt should overwrite the failed REGISTERING stub
        // (REGISTERING record is re-created, then FAILED again with same ID)
        ProjectRecord second = failingService.loadGit("https://github.com/example/retry-repo.git");
        assertEquals(ProjectStatus.FAILED, second.status());
        // Registry should not accumulate duplicates
        assertEquals(1, registry.findAll().size());
    }

    // ---- helper ----

    private static Path createSpringRepo(Path dir) throws IOException {
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
