package com.logicscope.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record CapabilityReport(
        RepositoryLocation repository,
        Map<RepositoryCapability, CapabilityAssessment> capabilities,
        List<String> diagnostics) {

    public CapabilityReport {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(capabilities, "capabilities");
        Objects.requireNonNull(diagnostics, "diagnostics");
        var copy = new EnumMap<RepositoryCapability, CapabilityAssessment>(RepositoryCapability.class);
        copy.putAll(capabilities);
        capabilities = Collections.unmodifiableMap(copy);
        diagnostics = List.copyOf(diagnostics);
    }

    public CapabilityAssessment assessment(RepositoryCapability capability) {
        return capabilities.getOrDefault(capability, CapabilityAssessment.unknown(capability, "Not inspected"));
    }

    public boolean isSupported() {
        return assessment(RepositoryCapability.JAVA).status() == CapabilityStatus.SUPPORTED
                && assessment(RepositoryCapability.MAVEN).status() == CapabilityStatus.SUPPORTED
                && assessment(RepositoryCapability.SPRING_BOOT).status() == CapabilityStatus.SUPPORTED;
    }
}
