package com.logicscope.app;

/**
 * Thrown when a scan request fails validation.
 * Mapped to HTTP 400 by {@link ApiExceptionHandler}.
 */
class InvalidScanRequestException extends RuntimeException {

    InvalidScanRequestException(String message) {
        super(message);
    }
}
