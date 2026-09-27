package com.logicscope.cli;

import com.logicscope.discovery.MavenRepositoryInspector;
import com.logicscope.discovery.RepositoryInspector;
import com.logicscope.domain.CapabilityAssessment;
import com.logicscope.domain.CapabilityReport;
import com.logicscope.domain.CapabilityStatus;
import com.logicscope.domain.ProjectRecord;
import com.logicscope.domain.RepositoryCapability;
import com.logicscope.project.FileProjectRegistry;
import com.logicscope.project.ProjectService;
import com.logicscope.project.WorkspaceManager;
import com.logicscope.project.GitCloneOperation;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public final class LogicScopeCli {
    private static final String USAGE = "Usage:\n"
            + "  logicscope init [target-project]\n"
            + "  logicscope run [target-project]\n"
            + "  logicscope scan <repository>\n"
            + "  logicscope load <path-or-https-url>\n"
            + "  logicscope projects\n"
            + "  logicscope inspect <project-id>";

    private final RepositoryInspector inspector;
    private final AppArtifactLocator artifactLocator;
    private final ServerRuntime serverRuntime;
    private final ProjectService projectService;
    private final PrintStream output;
    private final PrintStream error;
    private final Supplier<Path> currentDirectory;

    public LogicScopeCli(RepositoryInspector inspector, PrintStream output, PrintStream error) {
        this(inspector, new AppArtifactLocator(), ServerRuntime.createDefault(output, error),
                createDefaultProjectService(inspector), output, error);
    }

    LogicScopeCli(RepositoryInspector inspector, AppArtifactLocator artifactLocator, ServerRuntime serverRuntime,
                  PrintStream output, PrintStream error) {
        this(inspector, artifactLocator, serverRuntime,
                createDefaultProjectService(inspector), output, error, () -> Path.of("."));
    }

    LogicScopeCli(RepositoryInspector inspector, AppArtifactLocator artifactLocator, ServerRuntime serverRuntime,
                  PrintStream output, PrintStream error, Supplier<Path> currentDirectory) {
        this(inspector, artifactLocator, serverRuntime,
                createDefaultProjectService(inspector), output, error, currentDirectory);
    }

    LogicScopeCli(RepositoryInspector inspector, AppArtifactLocator artifactLocator, ServerRuntime serverRuntime,
                  ProjectService projectService, PrintStream output, PrintStream error) {
        this(inspector, artifactLocator, serverRuntime, projectService, output, error, () -> Path.of("."));
    }

    LogicScopeCli(RepositoryInspector inspector, AppArtifactLocator artifactLocator, ServerRuntime serverRuntime,
                  ProjectService projectService, PrintStream output, PrintStream error,
                  Supplier<Path> currentDirectory) {
        this.inspector = inspector;
        this.artifactLocator = artifactLocator;
        this.serverRuntime = serverRuntime;
        this.projectService = projectService;
        this.output = output;
        this.error = error;
        this.currentDirectory = currentDirectory;
    }

    private static ProjectService createDefaultProjectService(RepositoryInspector inspector) {
        WorkspaceManager workspaceManager = new WorkspaceManager();
        return new ProjectService(
                new FileProjectRegistry(workspaceManager),
                workspaceManager,
                new GitCloneOperation(),
                inspector);
    }

    public static void main(String[] args) {
        int exitCode = new LogicScopeCli(new MavenRepositoryInspector(), System.out, System.err).run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    int run(String[] args) {
        if (args.length == 0 || "help".equals(args[0]) || "--help".equals(args[0])) {
            error.println(USAGE);
            return 2;
        }
        return switch (args[0]) {
            case "init" -> handleInit(args);
            case "run" -> handleRun(args);
            case "scan" -> handleScan(args);
            case "load" -> handleLoad(args);
            case "projects" -> handleProjects(args);
            case "inspect" -> handleInspect(args);
            default -> {
                error.println("Unknown command: " + args[0]);
                error.println(USAGE);
                yield 2;
            }
        };
    }

    private int handleInit(String[] args) {
        if (args.length > 1 && args[1].isBlank()) {
            error.println(USAGE);
            return 2;
        }
        Path target = (args.length < 2 ? currentDirectory.get() : Path.of(args[1]))
                .toAbsolutePath().normalize();
        Path configDir = target.resolve(".logicscope");
        Path configFile = configDir.resolve("config.yaml");

        try {
            if (!Files.exists(target)) {
                error.println("Target path does not exist: " + target);
                return 1;
            }
            if (!Files.isDirectory(target)) {
                error.println("Target path is not a directory: " + target);
                return 1;
            }
            if (Files.exists(configFile)) {
                error.println("Configuration already exists: " + configFile);
                return 1;
            }

            Files.createDirectories(configDir);
            Files.writeString(configFile, buildDefaultConfig());

            output.println("LogicScope initialized at: " + configFile);
            return 0;
        } catch (IOException e) {
            error.println("Failed to initialize LogicScope: " + e.getMessage());
            return 1;
        }
    }

    private int handleRun(String[] args) {
        Path target;
        if (args.length < 2 || args[1].isBlank()) {
            target = currentDirectory.get().toAbsolutePath().normalize();
        } else {
            target = Path.of(args[1]).toAbsolutePath().normalize();
        }

        Path configFile = target.resolve(".logicscope").resolve("config.yaml");
        if (!Files.isRegularFile(configFile)) {
            error.println("No LogicScope project configuration was found in this directory.");
            error.println("Run:");
            error.println("  logicscope init " + target);
            return 1;
        }

        ProjectConfig config;
        try {
            config = ProjectConfig.load(configFile);
        } catch (IllegalArgumentException exception) {
            error.println(exception.getMessage());
            return 1;
        } catch (IOException exception) {
            error.println("Failed to read configuration: " + exception.getMessage());
            return 1;
        }

        Optional<Path> appJar = artifactLocator.locate();
        if (appJar.isEmpty()) {
            error.println("LogicScope application artifact not found.");
            error.println("Build it first:");
            error.println("  mvn -f backend/pom.xml -pl logicscope-app -am package");
            error.println("Or set LOGICSCOPE_HOME to the LogicScope repository root.");
            return 1;
        }

        output.println("Starting LogicScope server for project: " + target);
        output.println("Configuration: " + configFile);
        output.println("Application: " + appJar.get());
        return serverRuntime.startAndAwait(appJar.get(), config.port(), target);
    }

    private int handleScan(String[] args) {
        if (args.length != 2 || args[1].isBlank()) {
            error.println(USAGE);
            return 2;
        }
        Path repository = Path.of(args[1]);
        CapabilityReport report = inspector.inspect(repository);
        printReport(report);
        return report.isSupported() ? 0 : 1;
    }

    private static String buildDefaultConfig() {
        return "project:\n"
                + "  root: .\n"
                + "runtime:\n"
                + "  type: local\n"
                + "server:\n"
                + "  port: 4377\n";
    }

    private void printReport(CapabilityReport report) {
        output.println("LogicScope Repository Scan");
        output.println();
        output.println("Repository");
        output.println(report.repository().path());
        output.println();
        output.println("Capabilities");
        output.println("----------------------------------------");

        printAssessment("Java", report.assessment(RepositoryCapability.JAVA), true);
        printAssessment("Maven", report.assessment(RepositoryCapability.MAVEN), false);
        printAssessment("Spring Boot", report.assessment(RepositoryCapability.SPRING_BOOT), true);
        printAssessment("Maven Multi-module", report.assessment(RepositoryCapability.MAVEN_MULTI_MODULE), true);

        output.println("----------------------------------------");
        output.println();
        output.println("STATUS: " + (report.isSupported() ? "SUPPORTED" : "UNSUPPORTED"));
        if (!report.isSupported()) {
            output.println();
            output.println("Reason:");
            output.println(reason(report));
        }
    }

    private void printAssessment(String label, CapabilityAssessment assessment, boolean includeValue) {
        output.println(label);
        if (includeValue && !assessment.detectedValue().isBlank()) {
            String valueLabel = RepositoryCapability.SPRING_BOOT.equals(assessment.capability())
                    ? "Version" : RepositoryCapability.MAVEN_MULTI_MODULE.equals(assessment.capability())
                    ? "Modules" : "Version";
            output.println("  " + valueLabel + ": " + assessment.detectedValue());
        } else if (includeValue && assessment.status() == CapabilityStatus.UNKNOWN) {
            output.println("  Version: UNKNOWN");
        }
        output.println("  Status: " + statusLabel(assessment));
        output.println();
    }

    private static String statusLabel(CapabilityAssessment assessment) {
        if (RepositoryCapability.MAVEN_MULTI_MODULE.equals(assessment.capability())
                && assessment.status() == CapabilityStatus.UNSUPPORTED) {
            return "NOT DETECTED";
        }
        return assessment.status().name();
    }

    private static String reason(CapabilityReport report) {
        CapabilityAssessment maven = report.assessment(RepositoryCapability.MAVEN);
        if (maven.status() != CapabilityStatus.SUPPORTED) {
            return "No supported Maven project was detected.";
        }
        CapabilityAssessment java = report.assessment(RepositoryCapability.JAVA);
        if (java.status() != CapabilityStatus.SUPPORTED) {
            return "Java project evidence was not detected.";
        }
        CapabilityAssessment springBoot = report.assessment(RepositoryCapability.SPRING_BOOT);
        if (springBoot.status() != CapabilityStatus.SUPPORTED) {
            return "Spring Boot was not detected.";
        }
        return report.diagnostics().isEmpty() ? "Repository is outside the supported capability envelope."
                : report.diagnostics().getFirst();
    }

    // ---- load / projects / inspect ----

    private int handleLoad(String[] args) {
        if (args.length != 2 || args[1].isBlank()) {
            error.println(USAGE);
            return 2;
        }
        String source = args[1];
        try {
            ProjectRecord record;
            if (source.startsWith("https://")) {
                output.println("Loading Git repository: " + source);
                record = projectService.loadGit(source);
            } else {
                Path path = Path.of(source).toAbsolutePath().normalize();
                output.println("Loading local path: " + path);
                record = projectService.loadLocal(path);
            }
            printProjectRecord(record);
            return record.status().name().equals("FAILED") ? 1 : 0;
        } catch (IllegalArgumentException e) {
            error.println("Load failed: " + e.getMessage());
            return 1;
        } catch (IOException e) {
            error.println("Registry error: " + e.getMessage());
            return 1;
        }
    }

    private int handleProjects(String[] args) {
        try {
            List<ProjectRecord> records = projectService.list();
            if (records.isEmpty()) {
                output.println("No projects registered.");
                output.println("Use: logicscope load <path-or-https-url>");
            } else {
                output.println("Registered projects (" + records.size() + "):");
                output.println();
                for (ProjectRecord record : records) {
                    printProjectRecord(record);
                }
            }
            return 0;
        } catch (IOException e) {
            error.println("Registry error: " + e.getMessage());
            return 1;
        }
    }

    private int handleInspect(String[] args) {
        if (args.length != 2 || args[1].isBlank()) {
            error.println(USAGE);
            return 2;
        }
        String id = args[1];
        try {
            CapabilityReport report = projectService.inspect(id);
            output.println("Inspection result for project: " + id);
            output.println("Note: capability detection is based on metadata only.");
            output.println("      Results do not indicate runtime readiness.");
            output.println();
            printReport(report);
            return report.isSupported() ? 0 : 1;
        } catch (IllegalArgumentException e) {
            error.println("Inspect failed: " + e.getMessage());
            return 1;
        } catch (IOException e) {
            error.println("Registry error: " + e.getMessage());
            return 1;
        }
    }

    private void printProjectRecord(ProjectRecord record) {
        output.println("Project ID  : " + record.id().value());
        output.println("Name        : " + record.name());
        output.println("Source type : " + record.sourceType().name());
        if (record.sourceUrl() != null) {
            output.println("Source URL  : " + record.sourceUrl());
        }
        if (record.localPath() != null) {
            output.println("Local path  : " + record.localPath());
        }
        if (record.commitSha() != null) {
            output.println("Commit SHA  : " + record.commitSha());
        }
        output.println("Status      : " + record.status().name());
        if (record.failureReason() != null) {
            output.println("Failure     : " + record.failureReason());
        }
        output.println();
    }
}
