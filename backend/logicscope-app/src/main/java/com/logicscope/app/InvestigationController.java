package com.logicscope.app;

import com.logicscope.source.SourceInvestigator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

@RestController
@RequestMapping("/api")
class InvestigationController {

    static final int MAX_PATH_LENGTH = 1024;
    static final int MAX_QUERY_LENGTH = 1024;

    private final SourceInvestigator investigator;

    InvestigationController(SourceInvestigator investigator) {
        this.investigator = investigator;
    }

    @PostMapping("/investigate")
    ResponseEntity<InvestigationResponse> investigate(@RequestBody InvestigationRequest request) {
        validateRequest(request);
        Path root = Path.of(request.repositoryPath()).toAbsolutePath().normalize();
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new InvestigationException("REPOSITORY_NOT_FOUND", "Repository path does not exist", HttpStatus.NOT_FOUND);
        }
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new InvestigationException("NOT_A_DIRECTORY", "Repository path must be a directory", HttpStatus.BAD_REQUEST);
        }
        var result = investigator.investigate(root, request.query());
        return ResponseEntity.ok(InvestigationResponse.from(root.toString(), request.query(), result));
    }

    private static void validateRequest(InvestigationRequest request) {
        String repositoryPath = request == null ? null : request.repositoryPath();
        String query = request == null ? null : request.query();
        if (repositoryPath == null || repositoryPath.isBlank()) {
            throw invalid("repositoryPath must not be blank");
        }
        if (repositoryPath.length() > MAX_PATH_LENGTH) {
            throw invalid("repositoryPath must not exceed " + MAX_PATH_LENGTH + " characters");
        }
        if (repositoryPath.indexOf('\0') >= 0) {
            throw invalid("repositoryPath must not contain null bytes");
        }
        if (query == null || query.isBlank()) {
            throw invalid("query must not be blank");
        }
        if (query.length() > MAX_QUERY_LENGTH) {
            throw invalid("query must not exceed " + MAX_QUERY_LENGTH + " characters");
        }
    }

    private static InvestigationException invalid(String message) {
        return new InvestigationException("INVALID_REQUEST", message, HttpStatus.BAD_REQUEST);
    }
}
