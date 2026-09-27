package com.logicscope.project;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Executes {@code git} commands to clone a public HTTPS repository and read its HEAD SHA.
 *
 * <p>Only {@code https://} URLs are accepted; {@code http://}, {@code ssh://}, {@code git://},
 * {@code file://}, and bare paths are rejected to limit the attack surface.
 *
 * <p>Credentials must not appear in the URL. Any URL containing {@code @} in the authority
 * component is rejected.
 *
 * <p>Clone behavior:
 * <ul>
 *   <li>Clone is performed into a temporary staging directory first.</li>
 *   <li>If the clone succeeds the staging directory is moved to the final workspace path.</li>
 *   <li>If the clone fails the staging directory is deleted, leaving the workspace clean.</li>
 *   <li>If the final workspace directory already exists and contains a {@code .git} directory,
 *       the existing checkout is reused and the current HEAD SHA is read without pulling.</li>
 * </ul>
 *
 * <p>Error messages from {@code git} are captured and sanitized before being surfaced; the raw
 * git stderr is not forwarded to avoid leaking any credential hints.
 */
public final class GitCloneOperation {

    /** Timeout for the git clone process. */
    static final int CLONE_TIMEOUT_SECONDS = 300;
    /** Timeout for git rev-parse and other short commands. */
    static final int SHORT_TIMEOUT_SECONDS = 15;

    private final ProcessFactory processFactory;

    public GitCloneOperation() {
        this(new DefaultProcessFactory());
    }

    /** Package-private for testing with a custom process factory. */
    GitCloneOperation(ProcessFactory processFactory) {
        this.processFactory = processFactory;
    }

    /**
     * Validates the URL and clones the repository into {@code workspaceDir} if the clone
     * does not already exist. Returns the HEAD commit SHA after a successful clone or
     * when reusing an existing checkout.
     *
     * @param url          HTTPS URL of the repository
     * @param workspaceDir target directory (must not yet exist, or must already be a clone)
     * @return HEAD commit SHA
     * @throws GitCloneException if validation fails or the git process fails
     * @throws IOException       on I/O errors creating the staging directory
     */
    public String cloneOrReuse(String url, Path workspaceDir) throws GitCloneException, IOException {
        validateUrl(url);

        if (isExistingClone(workspaceDir)) {
            return readHeadSha(workspaceDir);
        }

        Path staging = workspaceDir.getParent().resolve(workspaceDir.getFileName() + ".tmp-clone");
        deleteIfExists(staging);
        try {
            runClone(url, staging);
            Files.move(staging, workspaceDir);
        } catch (GitCloneException | IOException e) {
            deleteIfExists(staging);
            throw e;
        }

        return readHeadSha(workspaceDir);
    }

    /**
     * Reads the HEAD commit SHA of the repository at {@code workspaceDir}.
     *
     * @throws GitCloneException if the git process fails
     */
    public String readHeadSha(Path workspaceDir) throws GitCloneException {
        ProcessResult result = run(SHORT_TIMEOUT_SECONDS,
                "git", "-C", workspaceDir.toString(), "rev-parse", "HEAD");
        if (result.exitCode() != 0) {
            throw new GitCloneException("Could not read HEAD SHA at " + workspaceDir
                    + ": git exited with code " + result.exitCode());
        }
        String sha = result.stdout().trim();
        if (sha.isEmpty()) {
            throw new GitCloneException("git rev-parse HEAD returned empty output at " + workspaceDir);
        }
        return sha;
    }

    /**
     * Validates that the URL is safe to clone.
     *
     * @throws GitCloneException if the URL is rejected
     */
    static void validateUrl(String url) throws GitCloneException {
        if (url == null || url.isBlank()) {
            throw new GitCloneException("Repository URL must not be blank");
        }
        URI parsed;
        try {
            parsed = new URI(url);
        } catch (URISyntaxException e) {
            throw new GitCloneException("Invalid URL syntax: " + sanitize(url));
        }
        String scheme = parsed.getScheme();
        if (!"https".equalsIgnoreCase(scheme)) {
            throw new GitCloneException(
                    "Only HTTPS URLs are supported; got scheme: " + (scheme == null ? "(none)" : scheme));
        }
        String authority = parsed.getAuthority();
        if (authority == null || authority.isBlank()) {
            throw new GitCloneException("URL has no host: " + sanitize(url));
        }
        if (authority.contains("@")) {
            throw new GitCloneException("URL must not contain credentials");
        }
        String host = parsed.getHost();
        if (host == null || host.isBlank()) {
            throw new GitCloneException("URL has no host: " + sanitize(url));
        }
        String path = parsed.getPath();
        if (path == null || path.isBlank() || path.equals("/")) {
            throw new GitCloneException("URL has no repository path: " + sanitize(url));
        }
        // Reject path traversal patterns in the URL
        if (path.contains("..") || path.contains("//")) {
            throw new GitCloneException("URL path contains suspicious pattern: " + sanitize(url));
        }
    }

    private void runClone(String url, Path targetDir) throws GitCloneException {
        ProcessResult result = run(CLONE_TIMEOUT_SECONDS,
                "git", "clone", "--no-local", url, targetDir.toString());
        if (result.exitCode() != 0) {
            // Do not include git's raw stderr (may contain auth error details).
            throw new GitCloneException("git clone failed (exit code " + result.exitCode()
                    + ") for URL: " + sanitize(url)
                    + ". Check the URL and your network connectivity.");
        }
    }

    private ProcessResult run(int timeoutSeconds, String... command) throws GitCloneException {
        try {
            return processFactory.run(timeoutSeconds, List.of(command));
        } catch (IOException e) {
            throw new GitCloneException("Failed to start git: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitCloneException("git process was interrupted");
        }
    }

    private static boolean isExistingClone(Path dir) {
        return Files.isDirectory(dir) && Files.isDirectory(dir.resolve(".git"));
    }

    private static void deleteIfExists(Path path) throws IOException {
        if (Files.exists(path)) {
            deleteRecursively(path);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        try (var stream = Files.walk(root)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    });
        }
    }

    /**
     * Strips any userinfo from a URL before surfacing it in an error message.
     */
    static String sanitize(String url) {
        if (url == null) {
            return "(null)";
        }
        // Remove user:pass@ pattern
        return url.replaceFirst("(https?://)([^@]*@)", "$1");
    }

    /** Abstraction over ProcessBuilder to enable testing without network access. */
    interface ProcessFactory {
        ProcessResult run(int timeoutSeconds, List<String> command)
                throws IOException, InterruptedException, GitCloneException;
    }

    private static final class DefaultProcessFactory implements ProcessFactory {
        @Override
        public ProcessResult run(int timeoutSeconds, List<String> command)
                throws IOException, InterruptedException {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(false)
                    .start();
            // Drain stdout
            String stdout;
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                stdout = reader.lines().collect(java.util.stream.Collectors.joining("\n"));
            }
            // Drain stderr (discarded intentionally — not safe to forward)
            try (var ignored = process.getErrorStream()) {
                ignored.transferTo(java.io.OutputStream.nullOutputStream());
            }
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("git process timed out after " + timeoutSeconds + "s");
            }
            return new ProcessResult(process.exitValue(), stdout);
        }
    }

    record ProcessResult(int exitCode, String stdout) {
    }
}
