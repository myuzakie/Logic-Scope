package com.logicscope.domain;

/**
 * Indicates how the project source was provided.
 */
public enum ProjectSourceType {
    /** A local filesystem path was registered; the source is not copied. */
    LOCAL,
    /** A public Git repository URL was cloned into the LogicScope workspace. */
    GIT
}
