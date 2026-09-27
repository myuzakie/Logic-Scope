package com.logicscope.cli;

import com.logicscope.discovery.MavenRepositoryInspector;
import com.logicscope.discovery.RepositoryInspector;
import com.logicscope.domain.CapabilityAssessment;
import com.logicscope.domain.CapabilityReport;
import com.logicscope.domain.CapabilityStatus;
import com.logicscope.domain.RepositoryCapability;

import java.io.PrintStream;
import java.nio.file.Path;

public final class LogicScopeCli {
    private static final String USAGE = "Usage:\n  ./logicscope scan <repository>";
    private final RepositoryInspector inspector;
    private final PrintStream output;
    private final PrintStream error;

    public LogicScopeCli(RepositoryInspector inspector, PrintStream output, PrintStream error) {
        this.inspector = inspector;
        this.output = output;
        this.error = error;
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
        if (!"scan".equals(args[0])) {
            error.println("Unknown command: " + args[0]);
            error.println(USAGE);
            return 2;
        }
        if (args.length != 2 || args[1].isBlank()) {
            error.println(USAGE);
            return 2;
        }

        Path repository = Path.of(args[1]);
        CapabilityReport report = inspector.inspect(repository);
        printReport(report);
        return report.isSupported() ? 0 : 1;
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
}
