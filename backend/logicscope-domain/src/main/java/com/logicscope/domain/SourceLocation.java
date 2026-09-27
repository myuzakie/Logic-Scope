package com.logicscope.domain;

import java.util.Objects;

public record SourceLocation(String file, int line) {
    public SourceLocation {
        Objects.requireNonNull(file, "file");
        if (line < 1) {
            throw new IllegalArgumentException("Source line must be positive");
        }
    }
}
