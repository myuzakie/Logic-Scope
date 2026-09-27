package com.logicscope.source;

import java.util.List;

/** Bounded results from a repository source investigation. */
public record InvestigationResult(
        String status,
        int totalMatches,
        boolean truncated,
        List<SourceMatch> matches) {

    public InvestigationResult {
        matches = List.copyOf(matches);
    }
}
