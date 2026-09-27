package com.logicscope.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Stable identity for a registered project.
 *
 * <p>The ID is derived deterministically from the normalized source identity:
 * <ul>
 *   <li>For a Git URL: scheme and host are lowercased; path is kept as-is; trailing ".git"
 *       is stripped; credentials are never included.</li>
 *   <li>For a local path: the canonical absolute path string is used.</li>
 * </ul>
 * <p>The same source always produces the same ID, making repeated loads idempotent.
 */
public record ProjectId(String value) {

    private static final String PREFIX = "proj-";
    private static final int HEX_LENGTH = 12;

    public ProjectId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ProjectId value must not be blank");
        }
    }

    /**
     * Derives a project ID from a Git URL.
     * Normalizes scheme and hostname to lowercase, strips trailing ".git", and never
     * includes credentials.
     */
    public static ProjectId fromGitUrl(String url) {
        Objects.requireNonNull(url, "url");
        String normalized = normalizeGitUrl(url);
        return new ProjectId(PREFIX + sha256Prefix(normalized));
    }

    /**
     * Derives a project ID from a local path. The canonical absolute path string is used
     * as the identity input.
     */
    public static ProjectId fromLocalPath(String canonicalPath) {
        Objects.requireNonNull(canonicalPath, "canonicalPath");
        return new ProjectId(PREFIX + sha256Prefix(canonicalPath));
    }

    /**
     * Normalizes a Git URL for identity purposes.
     * <ul>
     *   <li>Scheme is lowercased.</li>
     *   <li>Host is lowercased.</li>
     *   <li>Path casing is preserved (Git hosting is case-sensitive).</li>
     *   <li>Trailing ".git" suffix is stripped.</li>
     *   <li>Credentials (user:pass@) are removed.</li>
     * </ul>
     */
    public static String normalizeGitUrl(String url) {
        // Remove userinfo if present: https://user:pass@host/path → https://host/path
        String cleaned = url.replaceFirst("(https?://)([^@]+@)", "$1");
        // Lower-case scheme and host, preserve path
        int schemeEnd = cleaned.indexOf("://");
        if (schemeEnd < 0) {
            // Not a full URL — treat as opaque identity
            return stripTrailingGitSuffix(cleaned);
        }
        String scheme = cleaned.substring(0, schemeEnd).toLowerCase();
        String rest = cleaned.substring(schemeEnd + 3);
        int slashIndex = rest.indexOf('/');
        if (slashIndex < 0) {
            return scheme + "://" + rest.toLowerCase();
        }
        String host = rest.substring(0, slashIndex).toLowerCase();
        String path = rest.substring(slashIndex);
        return scheme + "://" + host + stripTrailingGitSuffix(path);
    }

    private static String stripTrailingGitSuffix(String path) {
        if (path.endsWith(".git")) {
            return path.substring(0, path.length() - 4);
        }
        return path;
    }

    private static String sha256Prefix(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, HEX_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
