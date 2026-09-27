package com.logicscope.domain;

import java.util.Objects;

public record CapabilityAssessment(
        RepositoryCapability capability,
        CapabilityStatus status,
        String detectedValue,
        String explanation) {

    public CapabilityAssessment {
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(status, "status");
        detectedValue = detectedValue == null ? "" : detectedValue;
        explanation = explanation == null ? "" : explanation;
    }

    public static CapabilityAssessment unknown(RepositoryCapability capability, String explanation) {
        return new CapabilityAssessment(capability, CapabilityStatus.UNKNOWN, "", explanation);
    }
}
