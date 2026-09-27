package com.logicscope.app;

import com.logicscope.discovery.RepositoryInspector;
import com.logicscope.domain.CapabilityAssessment;
import com.logicscope.domain.CapabilityReport;
import com.logicscope.domain.CapabilityStatus;
import com.logicscope.domain.RepositoryCapability;
import com.logicscope.domain.RepositoryLocation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ScanController.class, ApiExceptionHandler.class})
class ScanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RepositoryInspector inspector;

    // --- 1. Supported repository returns HTTP 200 ---

    @Test
    void supportedRepositoryReturns200WithSupportedTrue() throws Exception {
        when(inspector.inspect(any(Path.class))).thenReturn(supportedReport("/some/repo"));

        mockMvc.perform(post("/api/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repositoryPath\":\"/some/repo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supported").value(true))
                .andExpect(jsonPath("$.capabilities.JAVA.status").value("SUPPORTED"))
                .andExpect(jsonPath("$.capabilities.MAVEN.status").value("SUPPORTED"))
                .andExpect(jsonPath("$.capabilities.SPRING_BOOT.status").value("SUPPORTED"));
    }

    // --- 2. Unsupported repository returns HTTP 200 ---

    @Test
    void unsupportedRepositoryReturns200WithSupportedFalse() throws Exception {
        when(inspector.inspect(any(Path.class))).thenReturn(unsupportedReport("/some/repo"));

        mockMvc.perform(post("/api/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repositoryPath\":\"/some/repo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supported").value(false));
    }

    // --- 3. Missing repositoryPath returns HTTP 400 ---

    @Test
    void missingRepositoryPathReturns400() throws Exception {
        mockMvc.perform(post("/api/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    // --- 4. Blank repositoryPath returns HTTP 400 ---

    @Test
    void blankRepositoryPathReturns400() throws Exception {
        mockMvc.perform(post("/api/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repositoryPath\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    // --- 5. Path longer than 1024 characters returns HTTP 400 ---

    @Test
    void oversizedPathReturns400() throws Exception {
        String longPath = "/a".repeat(513); // 1026 characters
        mockMvc.perform(post("/api/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repositoryPath\":\"" + longPath + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    // --- 6. Path containing a null byte returns HTTP 400 ---

    @Test
    void nullByteInPathReturns400() throws Exception {
        // JSON does not support \0 as a literal; pass the unicode escape
        mockMvc.perform(post("/api/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repositoryPath\":\"/some/repo\\u0000evil\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    // --- 7. Unexpected inspector exception returns HTTP 500 without a stack trace ---

    @Test
    void unexpectedInspectorExceptionReturns500WithoutStackTrace() throws Exception {
        when(inspector.inspect(any(Path.class))).thenThrow(new RuntimeException("disk failure"));

        mockMvc.perform(post("/api/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repositoryPath\":\"/some/repo\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                // the raw exception message must not appear in the response body
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("disk failure"))));
    }

    // --- 8. Diagnostics are included in the successful response ---

    @Test
    void diagnosticsAreIncludedInResponse() throws Exception {
        when(inspector.inspect(any(Path.class))).thenReturn(reportWithDiagnostics("/some/repo"));

        mockMvc.perform(post("/api/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repositoryPath\":\"/some/repo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagnostics[0]").value("Declared module has no pom.xml: core"));
    }

    // --- 9. Response path is the resolved absolute path from the inspector ---
    //
    // The controller passes Path.of(request.repositoryPath()) to the inspector.
    // The inspector resolves and normalises to an absolute path internally
    // (MavenRepositoryInspector calls toAbsolutePath().normalize() and stores
    // that in RepositoryLocation). The response path comes from
    // CapabilityReport.repository().path().toString(), so it reflects the
    // inspector's resolved path, not the raw user input. This test verifies
    // that the value in the response comes from the report, not from the request.

    @Test
    void responsePathComesFromReportNotFromRawInput() throws Exception {
        // The stub returns a report whose path differs from the raw input
        // (simulating what the real inspector does with absolute resolution).
        String resolvedPath = "/resolved/absolute/repo";
        when(inspector.inspect(any(Path.class))).thenReturn(supportedReport(resolvedPath));

        mockMvc.perform(post("/api/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repositoryPath\":\"relative/repo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repositoryPath").value(resolvedPath));
    }

    // --- helpers ---

    private static CapabilityReport supportedReport(String pathString) {
        var assessments = new EnumMap<RepositoryCapability, CapabilityAssessment>(RepositoryCapability.class);
        for (RepositoryCapability cap : RepositoryCapability.values()) {
            assessments.put(cap, CapabilityAssessment.unknown(cap, "not inspected"));
        }
        assessments.put(RepositoryCapability.JAVA,
                new CapabilityAssessment(RepositoryCapability.JAVA, CapabilityStatus.SUPPORTED, "21", "detected"));
        assessments.put(RepositoryCapability.MAVEN,
                new CapabilityAssessment(RepositoryCapability.MAVEN, CapabilityStatus.SUPPORTED, "", "detected"));
        assessments.put(RepositoryCapability.SPRING_BOOT,
                new CapabilityAssessment(RepositoryCapability.SPRING_BOOT, CapabilityStatus.SUPPORTED, "3.4.5", "detected"));
        return new CapabilityReport(new RepositoryLocation(Path.of(pathString)), assessments, List.of());
    }

    private static CapabilityReport unsupportedReport(String pathString) {
        var assessments = new EnumMap<RepositoryCapability, CapabilityAssessment>(RepositoryCapability.class);
        for (RepositoryCapability cap : RepositoryCapability.values()) {
            assessments.put(cap, CapabilityAssessment.unknown(cap, "not inspected"));
        }
        assessments.put(RepositoryCapability.MAVEN,
                new CapabilityAssessment(RepositoryCapability.MAVEN, CapabilityStatus.UNSUPPORTED, "", "no pom.xml"));
        return new CapabilityReport(new RepositoryLocation(Path.of(pathString)), assessments, List.of());
    }

    private static CapabilityReport reportWithDiagnostics(String pathString) {
        var assessments = new EnumMap<RepositoryCapability, CapabilityAssessment>(RepositoryCapability.class);
        for (RepositoryCapability cap : RepositoryCapability.values()) {
            assessments.put(cap, CapabilityAssessment.unknown(cap, "not inspected"));
        }
        assessments.put(RepositoryCapability.MAVEN,
                new CapabilityAssessment(RepositoryCapability.MAVEN, CapabilityStatus.SUPPORTED, "", "detected"));
        return new CapabilityReport(new RepositoryLocation(Path.of(pathString)), assessments,
                List.of("Declared module has no pom.xml: core"));
    }
}
