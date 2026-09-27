package com.logicscope.app;

import com.logicscope.domain.CapabilityAssessment;

/**
 * Per-capability sub-record in the scan response.
 * Decouples the HTTP response shape from the domain record so future domain
 * changes do not silently break the serialised API contract.
 */
record CapabilityAssessmentView(String status, String detectedValue, String explanation) {

    static CapabilityAssessmentView from(CapabilityAssessment assessment) {
        return new CapabilityAssessmentView(
                assessment.status().name(),
                assessment.detectedValue(),
                assessment.explanation()
        );
    }
}
