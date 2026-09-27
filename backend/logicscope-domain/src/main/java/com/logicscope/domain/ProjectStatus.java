package com.logicscope.domain;

/**
 * Lifecycle status of a registered project.
 *
 * <p>{@code READY} means the source is available for inspection — either the local path
 * exists and is a directory, or the Git clone succeeded. It does not imply that the
 * application is running, that capabilities are supported, or that any runtime evidence
 * has been collected.
 */
public enum ProjectStatus {
    /** The load/clone operation is in progress. */
    REGISTERING,
    /** The source is available and can be inspected. */
    READY,
    /** The load/clone operation failed; see {@code failureReason} for details. */
    FAILED
}
