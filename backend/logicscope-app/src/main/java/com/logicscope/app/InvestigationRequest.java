package com.logicscope.app;

/** Request body for POST /api/investigate. */
record InvestigationRequest(String repositoryPath, String query) {
}
