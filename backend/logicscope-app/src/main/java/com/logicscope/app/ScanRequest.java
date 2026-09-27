package com.logicscope.app;

/**
 * Request body for POST /api/scan.
 *
 * <p>{@code repositoryPath} is the local filesystem path to the repository root.
 * This endpoint is intended for localhost or trusted local use only and must not
 * be exposed as a public network service without an access-control layer.
 */
record ScanRequest(String repositoryPath) {
}
