package com.logicscope.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Manages the workspace directory used by LogicScope to store Git clones.
 *
 * <p>The workspace root is determined from the {@code LOGICSCOPE_DATA_DIR} environment variable.
 * If the variable is not set, it defaults to {@code ~/.logicscope}.
 *
 * <p>Each project gets a subdirectory named after its project ID, e.g.
 * {@code ~/.logicscope/workspaces/proj-abc123def456/}.
 *
 * <p>Path validation rules:
 * <ul>
 *   <li>The workspace root itself is resolved to a real path before any child directory
 *       is derived from it.</li>
 *   <li>Any target path passed in from outside must canonically start with the workspace
 *       root (containment check), preventing traversal attacks.</li>
 *   <li>Symlinks within the workspace are followed normally by the OS; we do not chase
 *       symlinks out of the workspace root.</li>
 * </ul>
 */
public final class WorkspaceManager {

    private static final String ENV_DATA_DIR = "LOGICSCOPE_DATA_DIR";
    private static final String DEFAULT_DATA_SUBDIR = ".logicscope";
    private static final String WORKSPACES_SUBDIR = "workspaces";
    private static final String REGISTRY_FILENAME = "registry.json";

    private final Path dataDir;

    /** Creates a manager using the environment-configured or default data directory. */
    public WorkspaceManager() {
        this(resolveDataDir());
    }

    /** Creates a manager using the given data directory root. Used in tests. */
    WorkspaceManager(Path dataDir) {
        this.dataDir = dataDir.toAbsolutePath().normalize();
    }

    /**
     * Returns the workspace directory for the given project ID, creating it if needed.
     * The returned path is guaranteed to be a child of the workspace root.
     *
     * @param projectId the stable project ID string (e.g. {@code proj-abc123def456})
     * @throws IOException if the directory cannot be created
     * @throws IllegalArgumentException if the derived path would escape the workspace root
     */
    public Path workspaceFor(String projectId) throws IOException {
        Path target = workspacesRoot().resolve(projectId).normalize();
        assertContained(target);
        Files.createDirectories(target);
        return target;
    }

    /**
     * Returns the workspace directory for the given project ID without creating it.
     * The returned path may not exist.
     */
    public Path workspacePathFor(String projectId) {
        Path target = workspacesRoot().resolve(projectId).normalize();
        assertContained(target);
        return target;
    }

    /** Returns the path to the registry file. */
    public Path registryFile() {
        return dataDir.resolve(REGISTRY_FILENAME);
    }

    /** Returns the workspace root directory. */
    Path workspacesRoot() {
        return dataDir.resolve(WORKSPACES_SUBDIR);
    }

    /** Returns the data directory root. */
    Path dataDir() {
        return dataDir;
    }

    /**
     * Validates that {@code path} is strictly inside the workspace root.
     *
     * @throws IllegalArgumentException if the path escapes the root
     */
    private void assertContained(Path path) {
        Path root = workspacesRoot().normalize();
        Path normalized = path.normalize();
        if (!normalized.startsWith(root)) {
            throw new IllegalArgumentException(
                    "Path escapes workspace root: " + normalized + " (root: " + root + ")");
        }
    }

    private static Path resolveDataDir() {
        String env = System.getenv(ENV_DATA_DIR);
        if (env != null && !env.isBlank()) {
            return Path.of(env);
        }
        return Path.of(System.getProperty("user.home"), DEFAULT_DATA_SUBDIR);
    }
}
