package com.logicscope.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable record of a registered project.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code id} — stable identity derived from the normalized source.</li>
 *   <li>{@code name} — human-readable label derived from the source URL/path.</li>
 *   <li>{@code sourceType} — how the project was provided (local path or Git URL).</li>
 *   <li>{@code sourceUrl} — the Git URL as provided (credentials stripped), or {@code null}
 *       for local projects.</li>
 *   <li>{@code localPath} — canonical absolute path to the source. For local projects this
 *       is the registered path; for Git projects this is the clone directory.</li>
 *   <li>{@code commitSha} — the HEAD commit SHA at the time of load/clone, or {@code null}
 *       if not available. This records the version that was checked out; it is not
 *       updated automatically on subsequent loads.</li>
 *   <li>{@code status} — current lifecycle status.</li>
 *   <li>{@code failureReason} — human-readable explanation if status is {@code FAILED},
 *       otherwise {@code null}. Must not contain credentials.</li>
 *   <li>{@code registeredAt} — when the record was first created.</li>
 * </ul>
 */
public record ProjectRecord(
        ProjectId id,
        String name,
        ProjectSourceType sourceType,
        String sourceUrl,
        String localPath,
        String commitSha,
        ProjectStatus status,
        String failureReason,
        Instant registeredAt) {

    public ProjectRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(sourceType, "sourceType");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(registeredAt, "registeredAt");
        // sourceUrl is null for LOCAL projects — permitted
        // localPath is null when status is FAILED before a workspace was created — permitted
        // commitSha may be null — permitted
        // failureReason may be null — permitted
    }

    /** Returns a copy of this record with the given status and failure reason. */
    public ProjectRecord withStatus(ProjectStatus newStatus, String reason) {
        return new ProjectRecord(id, name, sourceType, sourceUrl, localPath, commitSha,
                newStatus, reason, registeredAt);
    }

    /** Returns a copy of this record with the commit SHA recorded. */
    public ProjectRecord withCommitSha(String sha) {
        return new ProjectRecord(id, name, sourceType, sourceUrl, localPath, sha,
                status, failureReason, registeredAt);
    }

    /** Returns a copy of this record with the local path set. */
    public ProjectRecord withLocalPath(String path) {
        return new ProjectRecord(id, name, sourceType, sourceUrl, path, commitSha,
                status, failureReason, registeredAt);
    }
}
