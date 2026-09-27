package com.logicscope.source;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Performs a bounded, framework-independent literal search of Java source files. */
public final class SourceInvestigator {

    public static final int MAX_MATCHES = 50;
    public static final int MAX_SNIPPET_LENGTH = 800;
    public static final long MAX_FILE_SIZE_BYTES = 1024L * 1024L;

    private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(
            ".git", "target", ".m2", "node_modules", "build", "out");
    private static final Pattern TYPE_DECLARATION = Pattern.compile(
            "\\b(?:class|interface|enum|record)\\s+([\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*)");
    private static final Pattern METHOD_DECLARATION = Pattern.compile(
            "(?:^|\\s)(?:@[\\w.]+\\s*)*(?:(?:public|protected|private|static|final|abstract|synchronized|native|default|strictfp)\\s+)*(?:<[^<>]*>\\s*)?[\\w.$<>?,\\[\\]\\s]+\\s+([\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*)\\s*\\([^;]*\\)\\s*(?:throws\\s+[\\w.,\\s]+)?\\s*\\{");

    public InvestigationResult investigate(Path repository, String query) {
        Path root = repository.toAbsolutePath().normalize();
        String needle = query.toLowerCase(Locale.ROOT);
        List<Path> javaFiles = collectJavaFiles(root);
        javaFiles.sort(Comparator.comparing(path -> root.relativize(path).toString().replace('\\', '/')));

        List<SourceMatch> matches = new ArrayList<>();
        int totalMatches = 0;
        for (Path file : javaFiles) {
            try {
                totalMatches += collectMatches(root, file, needle, matches);
            } catch (IOException | SecurityException ignored) {
                // An inaccessible source file does not make the rest of the repository unusable.
            }
        }

        return new InvestigationResult(
                totalMatches == 0 ? "NO_MATCHES" : "MATCHES_FOUND",
                totalMatches,
                totalMatches > matches.size(),
                matches);
    }

    private static List<Path> collectJavaFiles(Path root) {
        List<Path> files = new ArrayList<>();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            return files;
        }
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    if (attributes.isSymbolicLink() || !directory.normalize().startsWith(root)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (!directory.equals(root) && EXCLUDED_DIRECTORIES.contains(directory.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    if (attributes.isRegularFile() && !attributes.isSymbolicLink()
                            && file.normalize().startsWith(root)
                            && file.getFileName().toString().endsWith(".java")
                            && attributes.size() <= MAX_FILE_SIZE_BYTES) {
                        files.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exception) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException | SecurityException ignored) {
            return files;
        }
        return files;
    }

    private static int collectMatches(Path root, Path file, String needle, List<SourceMatch> results) throws IOException {
        int found = 0;
        Deque<String> enclosingClasses = new ArrayDeque<>();
        Deque<Integer> enclosingClassDepths = new ArrayDeque<>();
        String currentMethod = null;
        int braceDepth = 0;
        int methodDepth = -1;
        int lineNumber = 0;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                Matcher typeMatcher = TYPE_DECLARATION.matcher(line);
                String declaredClass = typeMatcher.find() ? typeMatcher.group(1) : null;
                Matcher methodMatcher = METHOD_DECLARATION.matcher(line);
                String declaredMethod = methodMatcher.find() ? methodMatcher.group(1) : null;
                int openBraces = count(line, '{');
                int closeBraces = count(line, '}');
                int previousDepth = braceDepth;
                if (declaredClass != null) {
                    enclosingClasses.push(declaredClass);
                    enclosingClassDepths.push(previousDepth + 1);
                }
                if (declaredMethod != null && !isControlKeyword(declaredMethod)) {
                    currentMethod = declaredMethod;
                    methodDepth = previousDepth + 1;
                }

                if (line.toLowerCase(Locale.ROOT).contains(needle)) {
                    found++;
                    if (results.size() < MAX_MATCHES) {
                        results.add(new SourceMatch(
                                root.relativize(file).toString().replace('\\', '/'),
                                lineNumber,
                                enclosingClasses.peek(),
                                currentMethod,
                                boundedSnippet(line)));
                    }
                }

                braceDepth += openBraces - closeBraces;
                if (currentMethod != null && braceDepth < methodDepth) {
                    currentMethod = null;
                    methodDepth = -1;
                }
                while (!enclosingClassDepths.isEmpty() && braceDepth < enclosingClassDepths.peek()) {
                    enclosingClassDepths.pop();
                    enclosingClasses.pop();
                }
            }
        }
        return found;
    }

    private static boolean isControlKeyword(String name) {
        return Set.of("if", "for", "while", "switch", "catch", "synchronized", "try", "return", "new")
                .contains(name);
    }

    private static String boundedSnippet(String line) {
        String trimmed = line.strip();
        if (trimmed.length() <= MAX_SNIPPET_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_SNIPPET_LENGTH);
    }

    private static int count(String value, char character) {
        int count = 0;
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == character) {
                count++;
            }
        }
        return count;
    }
}
