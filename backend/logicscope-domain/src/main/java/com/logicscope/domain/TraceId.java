package com.logicscope.domain;

public record TraceId(String value) {
    public TraceId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Trace id cannot be blank");
        }
    }
}
