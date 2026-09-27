package com.logicscope.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;

class AppArtifactLocator {
    private static final String APP_JAR_NAME = "logicscope-app-0.1.0-SNAPSHOT.jar";
    private static final String RELATIVE_APP_JAR = "backend/logicscope-app/target/" + APP_JAR_NAME;

    private final Supplier<Optional<Path>> override;

    AppArtifactLocator() {
        this.override = null;
    }

    AppArtifactLocator(Supplier<Optional<Path>> override) {
        this.override = override;
    }

    Optional<Path> locate() {
        if (override != null) {
            return override.get();
        }

        String home = System.getenv("LOGICSCOPE_HOME");
        if (home != null && !home.isBlank()) {
            Path candidate = Path.of(home).resolve(RELATIVE_APP_JAR).toAbsolutePath().normalize();
            if (Files.isRegularFile(candidate)) {
                return Optional.of(candidate);
            }
        }

        Path cwdCandidate = Path.of(RELATIVE_APP_JAR).toAbsolutePath().normalize();
        if (Files.isRegularFile(cwdCandidate)) {
            return Optional.of(cwdCandidate);
        }

        return locateFromCodeSource();
    }

    private Optional<Path> locateFromCodeSource() {
        try {
            var location = LogicScopeCli.class.getProtectionDomain().getCodeSource().getLocation();
            if (location == null) {
                return Optional.empty();
            }
            Path cliJar = Path.of(location.toURI()).toAbsolutePath().normalize();
            Path repoRoot = cliJar.getParent() // target
                    .getParent() // logicscope-cli
                    .getParent() // backend
                    .getParent(); // repository root
            Path candidate = repoRoot.resolve(RELATIVE_APP_JAR);
            if (Files.isRegularFile(candidate)) {
                return Optional.of(candidate);
            }
        } catch (Exception ignored) {
            // Fall through to empty.
        }
        return Optional.empty();
    }
}
