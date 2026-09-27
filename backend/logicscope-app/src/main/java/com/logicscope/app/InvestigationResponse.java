package com.logicscope.app;

import com.logicscope.source.InvestigationResult;
import com.logicscope.source.SourceMatch;

import java.util.List;

/** Response body for POST /api/investigate. */
record InvestigationResponse(
        String repositoryPath,
        String query,
        String status,
        int totalMatches,
        boolean truncated,
        List<SourceMatch> matches) {

    static InvestigationResponse from(String repositoryPath, String query, InvestigationResult result) {
        return new InvestigationResponse(repositoryPath, query, result.status(), result.totalMatches(),
                result.truncated(), result.matches());
    }
}
