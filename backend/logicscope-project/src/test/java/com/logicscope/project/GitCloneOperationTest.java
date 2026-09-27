package com.logicscope.project;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link GitCloneOperation} using a local Git fixture repository.
 *
 * <p>The fixture is created at test setup time with {@code git init} in a temporary directory;
 * no network access occurs. The production URL validation is tested separately in
 * {@link GitCloneOperationUrlValidationTest}; here we bypass URL validation and inject a
 * {@code file://} URL through a custom process factory to exercise the clone workflow.
 *
 * <p>Requires {@code git} to be available on the PATH.
 */
class GitCloneOperationTest {

    @TempDir
    Path tempDir;

    /** A bare local Git repository serving as the remote. */
    Path bareRepo;

    @BeforeEach
    void createLocalFixture() throws IOException, InterruptedException {
        // Create a source working tree with a pom.xml so inspection tests are meaningful
        Path workingTree = tempDir.resolve("source-repo");
        Files.createDirectories(workingTree.resolve("src/main/java"));
        Files.writeString(workingTree.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.fixture</groupId>
                  <artifactId>fixture-app</artifactId>
                  <version>1.0.0</version>
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
        Files.writeString(workingTree.resolve("src/main/java/App.java"), "class App {}\n");

        // Initialise the working tree as a git repository
        runGit(workingTree, "git", "init");
        runGit(workingTree, "git", "config", "user.email", "test@test.local");
        runGit(workingTree, "git", "config", "user.name", "Test");
        runGit(workingTree, "git", "add", ".");
        runGit(workingTree, "git", "commit", "-m", "fixture initial commit");

        // Create a bare clone to act as the "remote"
        bareRepo = tempDir.resolve("bare-repo.git");
        runGit(tempDir, "git", "clone", "--bare", workingTree.toString(), bareRepo.toString());
    }

    @Test
    void clonesRepositoryAndRecordsSha() throws Exception {
        Path target = tempDir.resolve("clone-target");
        GitCloneOperation op = new GitCloneOperation(cloneViaFile());
        String sha = op.cloneOrReuse("https://placeholder/ignored.git", target);

        assertTrue(Files.isDirectory(target.resolve(".git")), ".git directory must exist after clone");
        assertNotNull(sha);
        assertFalse(sha.isBlank());
        // SHA must be a 40-char hex string
        assertTrue(sha.matches("[0-9a-f]{40}"), "Expected 40-char hex SHA, got: " + sha);
    }

    @Test
    void reusingExistingCloneDoesNotReclone() throws Exception {
        Path target = tempDir.resolve("reuse-target");
        GitCloneOperation op = new GitCloneOperation(cloneViaFile());

        String sha1 = op.cloneOrReuse("https://placeholder/ignored.git", target);
        // Modify a file in the checkout to verify it is not overwritten on reuse
        Path marker = target.resolve("reuse-marker.txt");
        Files.writeString(marker, "marker");

        String sha2 = op.cloneOrReuse("https://placeholder/ignored.git", target);

        assertEquals(sha1, sha2, "SHA must be the same on reuse");
        assertTrue(Files.exists(marker), "Existing file must not be overwritten on reuse");
    }

    @Test
    void readHeadShaMatchesExpectedSha() throws Exception {
        Path target = tempDir.resolve("sha-target");
        GitCloneOperation op = new GitCloneOperation(cloneViaFile());
        String shaFromClone = op.cloneOrReuse("https://placeholder/ignored.git", target);
        String shaFromRead = op.readHeadSha(target);

        assertEquals(shaFromClone, shaFromRead);
    }

    @Test
    void cloneFailureLeavesWorkspaceClean() throws Exception {
        Path target = tempDir.resolve("fail-target");
        // Use a factory that simulates clone failure (non-zero exit)
        GitCloneOperation op = new GitCloneOperation((timeout, command) ->
                new GitCloneOperation.ProcessResult(128, ""));

        assertThrows(GitCloneException.class,
                () -> op.cloneOrReuse("https://placeholder/ignored.git", target));

        assertFalse(Files.exists(target), "Workspace must not exist after failed clone");
        assertFalse(Files.exists(target.getParent().resolve(target.getFileName() + ".tmp-clone")),
                "Staging directory must be cleaned up after failed clone");
    }

    @Test
    void cloneFailureMessageDoesNotContainRawGitOutput() throws Exception {
        Path target = tempDir.resolve("safe-fail-target");
        GitCloneOperation op = new GitCloneOperation((timeout, command) ->
                new GitCloneOperation.ProcessResult(128, "secret-token-xyz in stderr"));

        GitCloneException ex = assertThrows(GitCloneException.class,
                () -> op.cloneOrReuse("https://placeholder/ignored.git", target));

        assertFalse(ex.getMessage().contains("secret-token-xyz"),
                "Failure message must not contain raw git output");
    }

    // ---- helpers ----

    /**
     * Returns a {@link GitCloneOperation.ProcessFactory} that delegates clone commands
     * to the local bare fixture repo via the {@code file://} protocol.
     */
    private GitCloneOperation.ProcessFactory cloneViaFile() {
        return (timeoutSeconds, command) -> {
            // Swap the URL argument (second-to-last arg in "git clone --no-local <url> <dir>")
            // with a file:// URL pointing to the local bare repo.
            List<String> rewritten = rewriteCloneUrl(command, bareRepo);
            return defaultRun(timeoutSeconds, rewritten);
        };
    }

    private static List<String> rewriteCloneUrl(List<String> command, Path bareRepo) {
        if (command.size() < 4 || !"clone".equals(command.get(1))) {
            // Not a clone command — run as-is
            return command;
        }
        // Replace the URL (third arg: index 3 after "git clone --no-local <url> <dir>")
        var rewritten = new java.util.ArrayList<>(command);
        rewritten.set(command.size() - 2, bareRepo.toUri().toString());
        return rewritten;
    }

    private static GitCloneOperation.ProcessResult defaultRun(int timeoutSeconds, List<String> command)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        String stdout;
        try (var reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()))) {
            stdout = reader.lines().collect(java.util.stream.Collectors.joining("\n"));
        }
        boolean finished = process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("git process timed out");
        }
        return new GitCloneOperation.ProcessResult(process.exitValue(), stdout);
    }

    private static void runGit(Path dir, String... command) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(command)
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .start();
        try (var ignored = p.getInputStream()) {
            ignored.transferTo(java.io.OutputStream.nullOutputStream());
        }
        int exit = p.waitFor();
        if (exit != 0) {
            throw new RuntimeException("Git command failed (exit " + exit + "): " + String.join(" ", command));
        }
    }
}
