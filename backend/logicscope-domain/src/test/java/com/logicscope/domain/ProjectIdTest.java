package com.logicscope.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class ProjectIdTest {

    @Test
    void sameGitUrlProducesSameId() {
        ProjectId a = ProjectId.fromGitUrl("https://github.com/example/my-repo.git");
        ProjectId b = ProjectId.fromGitUrl("https://github.com/example/my-repo.git");
        assertEquals(a, b);
    }

    @Test
    void gitUrlWithAndWithoutGitSuffixProduceSameId() {
        ProjectId withSuffix = ProjectId.fromGitUrl("https://github.com/example/my-repo.git");
        ProjectId withoutSuffix = ProjectId.fromGitUrl("https://github.com/example/my-repo");
        assertEquals(withSuffix, withoutSuffix);
    }

    @Test
    void differentGitUrlsProduceDifferentIds() {
        ProjectId a = ProjectId.fromGitUrl("https://github.com/example/repo-a.git");
        ProjectId b = ProjectId.fromGitUrl("https://github.com/example/repo-b.git");
        assertNotEquals(a, b);
    }

    @Test
    void sameLocalPathProducesSameId() {
        ProjectId a = ProjectId.fromLocalPath("/home/user/projects/my-project");
        ProjectId b = ProjectId.fromLocalPath("/home/user/projects/my-project");
        assertEquals(a, b);
    }

    @Test
    void differentLocalPathsProduceDifferentIds() {
        ProjectId a = ProjectId.fromLocalPath("/home/user/projects/repo-a");
        ProjectId b = ProjectId.fromLocalPath("/home/user/projects/repo-b");
        assertNotEquals(a, b);
    }

    @Test
    void idValueStartsWithPrefix() {
        ProjectId id = ProjectId.fromGitUrl("https://github.com/example/test.git");
        assertTrue(id.value().startsWith("proj-"), "Expected proj- prefix, got: " + id.value());
    }

    @Test
    void idValueHasExpectedLength() {
        // "proj-" (5) + 12 hex chars = 17
        ProjectId id = ProjectId.fromGitUrl("https://github.com/example/test.git");
        assertEquals(17, id.value().length());
    }

    @Test
    void blankValueIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ProjectId(""));
        assertThrows(IllegalArgumentException.class, () -> new ProjectId("   "));
    }

    @ParameterizedTest
    @CsvSource({
        "https://GitHub.com/Example/Repo.git, https://github.com/Example/Repo",
        "https://GITHUB.COM/Org/repo,         https://github.com/Org/repo"
    })
    void normalizeGitUrl_lowercasesSchemeAndHost(String input, String expected) {
        assertEquals(expected.trim(), ProjectId.normalizeGitUrl(input.trim()));
    }

    @Test
    void normalizeGitUrl_stripsCredentials() {
        String normalized = ProjectId.normalizeGitUrl("https://user:token@github.com/org/repo.git");
        assertFalse(normalized.contains("user"), "credentials should be stripped");
        assertFalse(normalized.contains("token"), "credentials should be stripped");
        assertTrue(normalized.startsWith("https://github.com/"));
    }

    @Test
    void normalizeGitUrl_preservesRepositoryPathCase() {
        String normalized = ProjectId.normalizeGitUrl("https://github.com/MyOrg/MyRepo.git");
        assertTrue(normalized.contains("MyOrg"), "repo org case should be preserved");
        assertTrue(normalized.contains("MyRepo"), "repo name case should be preserved");
    }
}
