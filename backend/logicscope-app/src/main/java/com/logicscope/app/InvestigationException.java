package com.logicscope.app;

import org.springframework.http.HttpStatus;

/** An expected source investigation failure with a safe HTTP representation. */
class InvestigationException extends RuntimeException {

    private final String error;
    private final HttpStatus status;

    InvestigationException(String error, String message, HttpStatus status) {
        super(message);
        this.error = error;
        this.status = status;
    }

    String error() {
        return error;
    }

    HttpStatus status() {
        return status;
    }
}
