package com.logicscope.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests URL validation in {@link GitCloneOperation}.
 * No network access is required; all validation is purely local.
 */
class GitCloneOperationUrlValidationTest {

    @Test
    void acceptsValidHttpsUrl() {
        assertDoesNotThrow(() -> GitCloneOperation.validateUrl(
                "https://github.com/spring-projects/spring-boot.git"));
    }

    @Test
    void acceptsHttpsUrlWithoutGitSuffix() {
        assertDoesNotThrow(() -> GitCloneOperation.validateUrl(
                "https://github.com/example/my-repo"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://github.com/example/repo.git",
        "ssh://git@github.com/example/repo.git",
        "git://github.com/example/repo.git",
        "file:///home/user/repo",
        "ftp://example.com/repo.git"
    })
    void rejectsNonHttpsSchemes(String url) {
        assertThrows(GitCloneException.class, () -> GitCloneOperation.validateUrl(url));
    }

    @Test
    void rejectsUrlWithCredentials() {
        assertThrows(GitCloneException.class,
                () -> GitCloneOperation.validateUrl("https://user:token@github.com/example/repo.git"));
    }

    @Test
    void rejectsBlankUrl() {
        assertThrows(GitCloneException.class, () -> GitCloneOperation.validateUrl(""));
        assertThrows(GitCloneException.class, () -> GitCloneOperation.validateUrl("   "));
    }

    @Test
    void rejectsNullUrl() {
        assertThrows(GitCloneException.class, () -> GitCloneOperation.validateUrl(null));
    }

    @Test
    void rejectsUrlWithNoPath() {
        assertThrows(GitCloneException.class,
                () -> GitCloneOperation.validateUrl("https://github.com"));
        assertThrows(GitCloneException.class,
                () -> GitCloneOperation.validateUrl("https://github.com/"));
    }

    @Test
    void rejectsUrlWithPathTraversal() {
        assertThrows(GitCloneException.class,
                () -> GitCloneOperation.validateUrl("https://github.com/../etc/passwd"));
        assertThrows(GitCloneException.class,
                () -> GitCloneOperation.validateUrl("https://github.com//example/repo"));
    }

    @Test
    void sanitizeStripsCredentials() {
        String sanitized = GitCloneOperation.sanitize("https://user:secret@github.com/org/repo.git");
        assertFalse(sanitized.contains("user"), "user should be stripped");
        assertFalse(sanitized.contains("secret"), "secret should be stripped");
        assertTrue(sanitized.contains("github.com"));
    }

    @Test
    void sanitizeHandlesNormalUrl() {
        String url = "https://github.com/org/repo.git";
        assertEquals(url, GitCloneOperation.sanitize(url));
    }
}
