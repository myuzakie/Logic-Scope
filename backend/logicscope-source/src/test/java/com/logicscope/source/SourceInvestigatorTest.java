package com.logicscope.source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceInvestigatorTest {

    @TempDir
    Path repository;

    private final SourceInvestigator investigator = new SourceInvestigator();

    @Test
    void findsLiteralCaseInsensitiveMatchesWithStableLocationsAndNames() throws IOException {
        write("src/z/Last.java", "package sample;\nclass Last { void execute() { Marker(); } }\n");
        write("src/a/First.java", "class First {\n    void run() { marker(); }\n}\n");
        write("src/ignored.txt", "marker");

        InvestigationResult result = investigator.investigate(repository, "MARKER");

        assertEquals("MATCHES_FOUND", result.status());
        assertEquals(2, result.totalMatches());
        assertFalse(result.truncated());
        assertEquals(List.of("src/a/First.java", "src/z/Last.java"),
                result.matches().stream().map(SourceMatch::relativePath).toList());
        assertEquals(2, result.matches().getFirst().line());
        assertEquals("First", result.matches().getFirst().className());
        assertEquals("run", result.matches().getFirst().methodName());
    }

    @Test
    void skipsExcludedDirectoriesAndSymbolicLinks() throws IOException {
        write("src/Visible.java", "class Visible { String value = \"needle\"; }\n");
        for (String directory : List.of(".git", "target", ".m2", "node_modules", "build", "out")) {
            write(directory + "/Hidden.java", "class Hidden { String value = \"needle\"; }\n");
        }
        Path outside = Files.createTempFile("outside-source", ".java");
        Files.writeString(outside, "class Outside { String value = \"needle\"; }\n");
        try {
            Files.createSymbolicLink(repository.resolve("src/Linked.java"), outside);
        } catch (UnsupportedOperationException | IOException | SecurityException ignored) {
            assertTrue(Files.exists(outside));
        }

        InvestigationResult result = investigator.investigate(repository, "needle");

        assertEquals(1, result.totalMatches());
        assertEquals("src/Visible.java", result.matches().getFirst().relativePath());
    }

    @Test
    void ignoresFilesLargerThanOneMiB() throws IOException {
        write("Large.java", "needle" + " ".repeat(1024 * 1024));
        write("Small.java", "needle\n");

        InvestigationResult result = investigator.investigate(repository, "needle");

        assertEquals(1, result.totalMatches());
        assertEquals("Small.java", result.matches().getFirst().relativePath());
    }

    @Test
    void capsMatchesAndSnippets() throws IOException {
        String line = "needle" + "x".repeat(1200);
        String contents = (line + "\n").repeat(60);
        write("Many.java", contents);

        InvestigationResult result = investigator.investigate(repository, "needle");

        assertEquals(50, result.matches().size());
        assertEquals(60, result.totalMatches());
        assertTrue(result.truncated());
        assertTrue(result.matches().stream().allMatch(match -> match.snippet().length() <= 800));
    }

    @Test
    void returnsNoMatchesForMissingLiteral() throws IOException {
        write("Example.java", "class Example {}\n");

        InvestigationResult result = investigator.investigate(repository, "absent");

        assertEquals("NO_MATCHES", result.status());
        assertEquals(0, result.totalMatches());
        assertTrue(result.matches().isEmpty());
    }

    @Test
    void matchesAtMostOncePerHitLine() throws IOException {
        write("Example.java", "needle needle needle\n");

        InvestigationResult result = investigator.investigate(repository, "needle");

        assertEquals(1, result.totalMatches());
        assertEquals(1, result.matches().getFirst().line());
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = repository.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
