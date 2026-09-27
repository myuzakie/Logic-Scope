package com.logicscope.source;

/** A source-code line containing the searched literal. */
public record SourceMatch(
        String relativePath,
        int line,
        String className,
        String methodName,
        String snippet) {
}
