package com.logicscope.discovery;

import com.logicscope.domain.CapabilityStatus;
import com.logicscope.domain.RepositoryCapability;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MavenRepositoryInspectorTest {
    private final RepositoryInspector inspector = new MavenRepositoryInspector();

    @Test
    void detectsJava21SpringBootAndMaven() throws Exception {
        var report = inspect("fixtures/java21-spring");

        assertEquals(CapabilityStatus.SUPPORTED, report.assessment(RepositoryCapability.JAVA).status());
        assertEquals("21", report.assessment(RepositoryCapability.JAVA).detectedValue());
        assertEquals(CapabilityStatus.SUPPORTED, report.assessment(RepositoryCapability.MAVEN).status());
        assertEquals(CapabilityStatus.SUPPORTED, report.assessment(RepositoryCapability.SPRING_BOOT).status());
    }

    @Test
    void reportsPlainMavenWithoutSpringBoot() throws Exception {
        var report = inspect("fixtures/plain-maven");

        assertEquals(CapabilityStatus.SUPPORTED, report.assessment(RepositoryCapability.JAVA).status());
        assertEquals(CapabilityStatus.SUPPORTED, report.assessment(RepositoryCapability.MAVEN).status());
        assertEquals(CapabilityStatus.UNSUPPORTED, report.assessment(RepositoryCapability.SPRING_BOOT).status());
    }

    @Test
    void detectsDeclaredMavenModulesAndSpringBootInModulePom() throws Exception {
        var report = inspect("fixtures/multi-module");

        assertEquals(CapabilityStatus.SUPPORTED, report.assessment(RepositoryCapability.MAVEN_MULTI_MODULE).status());
        assertEquals("1", report.assessment(RepositoryCapability.MAVEN_MULTI_MODULE).detectedValue());
        assertEquals(CapabilityStatus.SUPPORTED, report.assessment(RepositoryCapability.SPRING_BOOT).status());
        assertFalse(report.diagnostics().stream().anyMatch(value -> value.contains("Declared module")));
    }

    @Test
    void malformedRepositoryReturnsUsefulReportInsteadOfThrowing() throws Exception {
        var report = inspect("fixtures/malformed");

        assertEquals(CapabilityStatus.UNSUPPORTED, report.assessment(RepositoryCapability.MAVEN).status());
        assertFalse(report.diagnostics().isEmpty());
        assertTrue(report.diagnostics().getFirst().contains("Malformed pom.xml"));
    }

    @Test
    void missingRepositoryReturnsUsefulReportInsteadOfThrowing() {
        var report = inspector.inspect(Path.of("/path/that/does/not/exist"));

        assertEquals(CapabilityStatus.UNSUPPORTED, report.assessment(RepositoryCapability.MAVEN).status());
        assertFalse(report.diagnostics().isEmpty());
    }

    private static com.logicscope.domain.CapabilityReport inspect(String resource) throws URISyntaxException {
        var url = MavenRepositoryInspectorTest.class.getClassLoader().getResource(resource);
        if (url == null) {
            throw new IllegalStateException("Missing test fixture: " + resource);
        }
        return new MavenRepositoryInspector().inspect(Path.of(url.toURI()));
    }
}
