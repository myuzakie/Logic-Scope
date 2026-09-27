package com.logicscope.domain;

import java.util.UUID;

public record InvestigationId(UUID value) {
    public InvestigationId {
        if (value == null) {
            throw new IllegalArgumentException("Investigation id cannot be null");
        }
    }
}
