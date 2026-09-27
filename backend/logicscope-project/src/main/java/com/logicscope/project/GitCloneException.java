package com.logicscope.project;

/**
 * Thrown when a Git clone or related git operation fails in a known, reportable way.
 *
 * <p>Messages must not contain credentials, tokens, or raw git stderr output that
 * could reveal authentication details.
 */
public final class GitCloneException extends Exception {

    public GitCloneException(String message) {
        super(message);
    }

    public GitCloneException(String message, Throwable cause) {
        super(message, cause);
    }
}
