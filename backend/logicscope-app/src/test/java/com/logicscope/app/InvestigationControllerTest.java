package com.logicscope.app;

import com.logicscope.source.InvestigationResult;
import com.logicscope.source.SourceInvestigator;
import com.logicscope.source.SourceMatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({InvestigationController.class, ApiExceptionHandler.class})
class InvestigationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SourceInvestigator investigator;

    @TempDir
    Path tempDirectory;

    @Test
    void returnsInvestigationResults() throws Exception {
        when(investigator.investigate(any(Path.class), eq("clinic"))).thenReturn(
                new InvestigationResult("MATCHES_FOUND", 1, false,
                        List.of(new SourceMatch("src/App.java", 4, "App", "run", "clinic();"))));

        mockMvc.perform(post("/api/investigate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(tempDirectory, "clinic")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MATCHES_FOUND"))
                .andExpect(jsonPath("$.repositoryPath").value(tempDirectory.toAbsolutePath().toString()))
                .andExpect(jsonPath("$.matches[0].relativePath").value("src/App.java"))
                .andExpect(jsonPath("$.matches[0].line").value(4))
                .andExpect(jsonPath("$.totalMatches").value(1));
    }

    @Test
    void blankPathAndQueryReturnStructured400() throws Exception {
        mockMvc.perform(post("/api/investigate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repositoryPath\":\" \",\"query\":\"clinic\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
        mockMvc.perform(post("/api/investigate").contentType(MediaType.APPLICATION_JSON)
                        .content(request(tempDirectory, "  ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    @Test
    void missingRepositoryReturnsStructuredNotFound() throws Exception {
        mockMvc.perform(post("/api/investigate").contentType(MediaType.APPLICATION_JSON)
                        .content(request(tempDirectory.resolve("missing"), "clinic")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("REPOSITORY_NOT_FOUND"));
    }

    @Test
    void nonDirectoryRepositoryReturnsStructuredBadRequest() throws Exception {
        Path file = Files.createFile(tempDirectory.resolve("repository-file"));
        mockMvc.perform(post("/api/investigate").contentType(MediaType.APPLICATION_JSON)
                        .content(request(file, "clinic")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("NOT_A_DIRECTORY"));
    }

    @Test
    void unexpectedFailureDoesNotExposeDetails() throws Exception {
        when(investigator.investigate(any(Path.class), eq("clinic")))
                .thenThrow(new IllegalStateException("private filesystem detail"));
        mockMvc.perform(post("/api/investigate").contentType(MediaType.APPLICATION_JSON)
                        .content(request(tempDirectory, "clinic")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    private static String request(Path path, String query) {
        return "{\"repositoryPath\":\"" + path.toAbsolutePath().toString().replace("\\", "\\\\")
                + "\",\"query\":\"" + query + "\"}";
    }
}
