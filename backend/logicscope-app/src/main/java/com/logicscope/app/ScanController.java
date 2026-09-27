package com.logicscope.app;

import com.logicscope.discovery.RepositoryInspector;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;

/**
 * HTTP endpoint for repository capability discovery.
 *
 * <p>This endpoint is intended for localhost or trusted local use only.
 * It accepts a local filesystem path and must not be exposed as a public
 * network service without an access-control layer.
 *
 * <p>Validation rejections (400) are distinct from inspection failures: a repository
 * that cannot be parsed or does not contain Maven metadata produces a normal 200
 * response with {@code supported: false}. A 500 response indicates an unexpected
 * server error, not an unsupported repository.
 */
@RestController
@RequestMapping("/api")
class ScanController {

    static final int MAX_PATH_LENGTH = 1024;

    private final RepositoryInspector inspector;

    ScanController(RepositoryInspector inspector) {
        this.inspector = inspector;
    }

    @PostMapping("/scan")
    ResponseEntity<ScanResponse> scan(@RequestBody ScanRequest request) {
        validateRequest(request);
        var report = inspector.inspect(Path.of(request.repositoryPath()));
        return ResponseEntity.ok(ScanResponse.from(report));
    }

    private static void validateRequest(ScanRequest request) {
        String path = request == null ? null : request.repositoryPath();
        if (path == null || path.isBlank()) {
            throw new InvalidScanRequestException("repositoryPath must not be blank");
        }
        if (path.length() > MAX_PATH_LENGTH) {
            throw new InvalidScanRequestException(
                    "repositoryPath must not exceed " + MAX_PATH_LENGTH + " characters");
        }
        if (path.indexOf('\0') >= 0) {
            throw new InvalidScanRequestException("repositoryPath must not contain null bytes");
        }
    }
}
