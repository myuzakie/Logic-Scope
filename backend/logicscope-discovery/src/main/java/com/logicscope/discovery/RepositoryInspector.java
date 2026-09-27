package com.logicscope.discovery;

import com.logicscope.domain.CapabilityReport;

import java.nio.file.Path;

public interface RepositoryInspector {
    CapabilityReport inspect(Path repository);
}
