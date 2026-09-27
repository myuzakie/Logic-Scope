package com.logicscope.app;

import com.logicscope.domain.CapabilityReport;
import com.logicscope.domain.RepositoryCapability;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Response body for POST /api/scan.
 *
 * <p>{@code repositoryPath} is the resolved absolute path as seen by the inspector,
 * which may differ from the caller-supplied path when a relative path was submitted.
 */
record ScanResponse(
        String repositoryPath,
        boolean supported,
        Map<String, CapabilityAssessmentView> capabilities,
        List<String> diagnostics) {

    static ScanResponse from(CapabilityReport report) {
        var capabilities = new LinkedHashMap<String, CapabilityAssessmentView>();
        for (RepositoryCapability capability : RepositoryCapability.values()) {
            capabilities.put(capability.name(), CapabilityAssessmentView.from(report.assessment(capability)));
        }
        return new ScanResponse(
                report.repository().path().toString(),
                report.isSupported(),
                Collections.unmodifiableMap(capabilities),
                report.diagnostics()
        );
    }
}
