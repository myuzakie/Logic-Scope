package com.logicscope.domain;

import java.nio.file.Path;
import java.util.Objects;

public record RepositoryLocation(Path path) {
    public RepositoryLocation {
        Objects.requireNonNull(path, "path");
        path = path.toAbsolutePath().normalize();
    }
}
