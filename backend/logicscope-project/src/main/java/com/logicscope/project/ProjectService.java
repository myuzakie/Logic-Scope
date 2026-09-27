package com.logicscope.project;

import com.logicscope.discovery.RepositoryInspector;
import com.logicscope.domain.CapabilityReport;
import com.logicscope.domain.ProjectId;
import com.logicscope.domain.ProjectRecord;
import com.logicscope.domain.ProjectSourceType;
import com.logicscope.domain.ProjectStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Application service for registering, loading, listing, and inspecting projects.
 *
 * <p>This class is plain Java with no framework dependencies. It can be instantiated
 * directly from the CLI or wired by a Spring context in {@code logicscope-app}.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Validate and register a local path without modifying the source.</li>
 *   <li>Validate a Git HTTPS URL, clone it into the managed workspace, record the HEAD SHA.</li>
 *   <li>Return project records with idempotent behavior: re-loading the same source
 *       returns the existing record and does not overwrite a working checkout.</li>
 *   <li>Run capability inspection on the registered local path using the discovery module.</li>
 * </ul>
 *
 * <p>Definition of {@code READY}: the source directory is available and accessible for
 * inspection. It does not imply the application is running or that all capabilities are
 * supported.
 */
public final class ProjectService {

    private final ProjectRegistry registry;
    private final WorkspaceManager workspace;
    private final GitCloneOperation cloneOperation;
    private final RepositoryInspector inspector;

    public ProjectService(ProjectRegistry registry, WorkspaceManager workspace,
                          GitCloneOperation cloneOperation, RepositoryInspector inspector) {
        this.registry = registry;
        this.workspace = workspace;
        this.cloneOperation = cloneOperation;
        this.inspector = inspector;
    }

    /**
     * Registers a local path as a project without copying or modifying the source.
     *
     * <p>If a project with the same identity already exists in the registry, the
     * existing record is returned unchanged (idempotent).
     *
     * @param path a local filesystem path
     * @return the project record (new or existing)
     * @throws IllegalArgumentException if the path does not exist or is not a directory
     * @throws IOException              on registry I/O failure
     */
    public ProjectRecord loadLocal(Path path) throws IOException {
        Path canonical = resolveLocalPath(path);
        String name = deriveName(canonical.getFileName().toString());
        ProjectId id = ProjectId.fromLocalPath(canonical.toString());

        Optional<ProjectRecord> existing = registry.findById(id.value());
        if (existing.isPresent()) {
            return existing.get();
        }

        ProjectRecord record = new ProjectRecord(
                id, name, ProjectSourceType.LOCAL,
                null, canonical.toString(),
                null, ProjectStatus.READY,
                null, Instant.now());
        registry.save(record);
        return record;
    }

    /**
     * Clones a public HTTPS Git repository into the managed workspace and registers it.
     *
     * <p>If a project with the same URL identity already exists in the registry
     * and the workspace directory already contains a valid checkout, the existing record
     * is returned and the HEAD SHA is read from the existing checkout without pulling.
     *
     * <p>On clone failure the record is saved with status {@code FAILED} and a
     * safe failure reason (no credentials, no raw git stderr).
     *
     * @param url a public HTTPS Git URL
     * @return the project record (READY or FAILED)
     * @throws IOException on registry or workspace I/O failure
     */
    public ProjectRecord loadGit(String url) throws IOException {
        try {
            GitCloneOperation.validateUrl(url);
        } catch (GitCloneException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }

        String normalizedUrl = ProjectId.normalizeGitUrl(url);
        ProjectId id = ProjectId.fromGitUrl(url);
        String name = deriveGitName(normalizedUrl);

        Optional<ProjectRecord> existing = registry.findById(id.value());
        if (existing.isPresent() && existing.get().status() == ProjectStatus.READY) {
            // Reuse existing checkout — read current HEAD but do not pull/reset
            ProjectRecord record = existing.get();
            if (record.localPath() != null && Files.isDirectory(Path.of(record.localPath()))) {
                return record;
            }
        }

        // Create a stub REGISTERING record before starting the clone so that
        // a partial state is visible if the process is interrupted.
        Path workspaceDir = workspace.workspacePathFor(id.value());
        ProjectRecord stub = new ProjectRecord(
                id, name, ProjectSourceType.GIT,
                normalizedUrl, workspaceDir.toString(),
                null, ProjectStatus.REGISTERING,
                null, Instant.now());
        registry.save(stub);

        try {
            String sha = cloneOperation.cloneOrReuse(url, workspaceDir);
            ProjectRecord ready = stub.withStatus(ProjectStatus.READY, null).withCommitSha(sha);
            registry.save(ready);
            return ready;
        } catch (GitCloneException e) {
            ProjectRecord failed = stub.withStatus(ProjectStatus.FAILED, e.getMessage());
            registry.save(failed);
            return failed;
        }
    }

    /**
     * Returns all registered projects.
     *
     * @throws IOException on registry I/O failure
     */
    public List<ProjectRecord> list() throws IOException {
        return registry.findAll();
    }

    /**
     * Returns the project record for the given ID, or empty if not found.
     *
     * @throws IOException on registry I/O failure
     */
    public Optional<ProjectRecord> find(String id) throws IOException {
        return registry.findById(id);
    }

    /**
     * Runs capability inspection on the registered project's local path.
     *
     * <p>The inspection uses the existing {@link RepositoryInspector} (Maven-based).
     * Results reflect metadata detection only — not runtime readiness.
     *
     * @param id the project ID
     * @return the capability report
     * @throws IllegalArgumentException if the project is not found, not READY, or the
     *                                  local path is not available
     * @throws IOException              on registry I/O failure
     */
    public CapabilityReport inspect(String id) throws IOException {
        ProjectRecord record = registry.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + id));
        if (record.status() != ProjectStatus.READY) {
            throw new IllegalArgumentException(
                    "Project " + id + " is not ready for inspection (status: " + record.status() + ")");
        }
        String localPath = record.localPath();
        if (localPath == null || localPath.isBlank()) {
            throw new IllegalArgumentException("Project " + id + " has no local path recorded");
        }
        Path path = Path.of(localPath);
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException(
                    "Local path for project " + id + " is not accessible: " + localPath);
        }
        return inspector.inspect(path);
    }

    // ---- helpers ----

    private static Path resolveLocalPath(Path path) {
        Path canonical = path.toAbsolutePath().normalize();
        if (!Files.exists(canonical)) {
            throw new IllegalArgumentException("Path does not exist: " + canonical);
        }
        if (!Files.isDirectory(canonical)) {
            throw new IllegalArgumentException("Path is not a directory: " + canonical);
        }
        return canonical;
    }

    private static String deriveName(String filename) {
        if (filename == null || filename.isBlank()) {
            return "unnamed";
        }
        return filename;
    }

    private static String deriveGitName(String normalizedUrl) {
        // Extract last path segment, strip .git suffix
        String path = normalizedUrl;
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0) {
            path = path.substring(lastSlash + 1);
        }
        if (path.endsWith(".git")) {
            path = path.substring(0, path.length() - 4);
        }
        return path.isBlank() ? "unnamed" : path;
    }
}
