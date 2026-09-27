package com.logicscope.discovery;

import com.logicscope.domain.CapabilityAssessment;
import com.logicscope.domain.CapabilityReport;
import com.logicscope.domain.CapabilityStatus;
import com.logicscope.domain.RepositoryCapability;
import com.logicscope.domain.RepositoryLocation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Detects only capabilities that can be explained from standard Maven metadata.
 * It deliberately does not inspect Java semantics or repository-specific names.
 */
public final class MavenRepositoryInspector implements RepositoryInspector {
    private static final String SPRING_BOOT_GROUP = "org.springframework.boot";

    @Override
    public CapabilityReport inspect(Path repository) {
        Objects.requireNonNull(repository, "repository");
        Path normalized = repository.toAbsolutePath().normalize();
        var assessments = unknownAssessments();
        var diagnostics = new ArrayList<String>();
        var location = new RepositoryLocation(normalized);

        if (!Files.isDirectory(normalized)) {
            assessments.put(RepositoryCapability.MAVEN,
                    assessment(RepositoryCapability.MAVEN, CapabilityStatus.UNSUPPORTED, "",
                            "Repository path is not a directory"));
            diagnostics.add("Repository path is not a directory: " + normalized);
            return new CapabilityReport(location, assessments, diagnostics);
        }

        Path rootPom = normalized.resolve("pom.xml");
        if (!Files.isRegularFile(rootPom)) {
            assessments.put(RepositoryCapability.MAVEN,
                    assessment(RepositoryCapability.MAVEN, CapabilityStatus.UNSUPPORTED, "",
                            "No pom.xml found at the repository root"));
            diagnostics.add("No root pom.xml found");
            return new CapabilityReport(location, assessments, diagnostics);
        }

        Document root;
        try {
            root = parsePom(rootPom);
        } catch (IOException | SAXException | RuntimeException exception) {
            assessments.put(RepositoryCapability.MAVEN,
                    assessment(RepositoryCapability.MAVEN, CapabilityStatus.UNSUPPORTED, "",
                            "pom.xml could not be parsed"));
            diagnostics.add("Malformed pom.xml: " + safeMessage(exception));
            return new CapabilityReport(location, assessments, diagnostics);
        }

        assessments.put(RepositoryCapability.MAVEN,
                assessment(RepositoryCapability.MAVEN, CapabilityStatus.SUPPORTED, "Maven", "Root pom.xml detected"));

        var pomFiles = new ArrayList<Path>();
        var documents = new ArrayList<Document>();
        pomFiles.add(rootPom);
        documents.add(root);

        List<String> moduleNames = directChildTexts(root.getDocumentElement(), "modules", "module");
        if (moduleNames.isEmpty()) {
            assessments.put(RepositoryCapability.MAVEN_MULTI_MODULE,
                    assessment(RepositoryCapability.MAVEN_MULTI_MODULE, CapabilityStatus.UNSUPPORTED, "",
                            "No modules are declared in the root pom.xml"));
        } else {
            assessments.put(RepositoryCapability.MAVEN_MULTI_MODULE,
                    assessment(RepositoryCapability.MAVEN_MULTI_MODULE, CapabilityStatus.SUPPORTED,
                            Integer.toString(moduleNames.size()), "Maven modules declared in root pom.xml"));
            for (String moduleName : moduleNames) {
                Path modulePom = normalized.resolve(moduleName).normalize().resolve("pom.xml");
                if (!Files.isRegularFile(modulePom)) {
                    diagnostics.add("Declared module has no pom.xml: " + moduleName);
                    continue;
                }
                try {
                    pomFiles.add(modulePom);
                    documents.add(parsePom(modulePom));
                } catch (IOException | SAXException | RuntimeException exception) {
                    diagnostics.add("Could not parse module pom.xml " + moduleName + ": " + safeMessage(exception));
                }
            }
        }

        boolean javaDetected = hasJavaEvidence(normalized, documents);
        String javaVersion = findJavaVersion(documents);
        if (javaDetected) {
            assessments.put(RepositoryCapability.JAVA,
                    assessment(RepositoryCapability.JAVA, CapabilityStatus.SUPPORTED,
                            javaVersion, javaVersion.isBlank() ? "Java source or Maven compiler metadata detected"
                                    : "Java source and version metadata detected"));
        } else {
            assessments.put(RepositoryCapability.JAVA,
                    assessment(RepositoryCapability.JAVA, CapabilityStatus.UNSUPPORTED, "",
                            "No Java source or Java compiler metadata detected"));
        }

        String springBootVersion = findSpringBootVersion(documents);
        if (!springBootVersion.isBlank()) {
            assessments.put(RepositoryCapability.SPRING_BOOT,
                    assessment(RepositoryCapability.SPRING_BOOT, CapabilityStatus.SUPPORTED,
                            springBootVersion, "Spring Boot Maven metadata detected"));
        } else {
            assessments.put(RepositoryCapability.SPRING_BOOT,
                    assessment(RepositoryCapability.SPRING_BOOT, CapabilityStatus.UNSUPPORTED, "",
                            "No Spring Boot dependency, parent, or Maven plugin detected"));
        }

        return new CapabilityReport(location, assessments, diagnostics);
    }

    private static EnumMap<RepositoryCapability, CapabilityAssessment> unknownAssessments() {
        var assessments = new EnumMap<RepositoryCapability, CapabilityAssessment>(RepositoryCapability.class);
        for (RepositoryCapability capability : RepositoryCapability.values()) {
            assessments.put(capability, CapabilityAssessment.unknown(capability,
                    "Detection is not implemented for this capability"));
        }
        return assessments;
    }

    private static CapabilityAssessment assessment(RepositoryCapability capability, CapabilityStatus status,
                                                   String value, String explanation) {
        return new CapabilityAssessment(capability, status, value, explanation);
    }

    private static Document parsePom(Path pom) throws IOException, SAXException {
        try (InputStream input = Files.newInputStream(pom)) {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new ThrowingErrorHandler());
            return builder.parse(input);
        } catch (javax.xml.parsers.ParserConfigurationException exception) {
            throw new SAXException("XML parser configuration failed", exception);
        }
    }

    private static boolean hasJavaEvidence(Path repository, List<Document> documents) {
        if (documents.stream().anyMatch(document -> !javaCompilerProperties(document).isEmpty()
                || !findElements(document, "maven-compiler-plugin").isEmpty())) {
            return true;
        }
        try (var paths = Files.walk(repository)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> !path.toString().contains("/.git/") && !path.toString().contains("/target/"))
                    .anyMatch(path -> path.toString().endsWith(".java"));
        } catch (IOException exception) {
            return false;
        }
    }

    private static Map<String, String> javaCompilerProperties(Document document) {
        Map<String, String> all = properties(document);
        var result = new LinkedHashMap<String, String>();
        for (String key : List.of("java.version", "maven.compiler.release", "maven.compiler.source", "maven.compiler.target")) {
            if (all.containsKey(key)) {
                result.put(key, all.get(key));
            }
        }
        return result;
    }

    private static String findJavaVersion(List<Document> documents) {
        for (Document document : documents) {
            Map<String, String> properties = properties(document);
            for (String key : List.of("java.version", "maven.compiler.release", "maven.compiler.source")) {
                String value = properties.get(key);
                if (value != null && !value.isBlank()) {
                    return resolveProperty(value.trim(), properties);
                }
            }
            for (Element release : findElements(document, "release")) {
                String value = text(release);
                if (!value.isBlank() && !value.startsWith("${")) {
                    return value;
                }
            }
        }
        return "";
    }

    private static String findSpringBootVersion(List<Document> documents) {
        for (Document document : documents) {
            Map<String, String> properties = properties(document);
            for (Element parent : directChildren(document.getDocumentElement(), "parent")) {
                if (SPRING_BOOT_GROUP.equals(childText(parent, "groupId"))) {
                    return resolveProperty(childText(parent, "version"), properties);
                }
            }
            for (Element dependency : findElements(document, "dependency")) {
                if (SPRING_BOOT_GROUP.equals(childText(dependency, "groupId"))) {
                    String artifact = childText(dependency, "artifactId");
                    if (artifact.startsWith("spring-boot")) {
                        return resolveProperty(childText(dependency, "version"), properties);
                    }
                }
            }
            for (Element plugin : findElements(document, "plugin")) {
                if ("spring-boot-maven-plugin".equals(childText(plugin, "artifactId"))) {
                    return resolveProperty(childText(plugin, "version"), properties);
                }
            }
        }
        return "";
    }

    private static Map<String, String> properties(Document document) {
        var result = new LinkedHashMap<String, String>();
        Element properties = directChildren(document.getDocumentElement(), "properties").stream().findFirst().orElse(null);
        if (properties == null) {
            return result;
        }
        for (Node child = properties.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element) {
                result.put(localName(element), text(element));
            }
        }
        return result;
    }

    private static String resolveProperty(String value, Map<String, String> properties) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.startsWith("${") && value.endsWith("}")) {
            return properties.getOrDefault(value.substring(2, value.length() - 1), value);
        }
        return value;
    }

    private static List<String> directChildTexts(Element parent, String containerName, String childName) {
        return directChildren(parent, containerName).stream()
                .flatMap(container -> directChildren(container, childName).stream())
                .map(MavenRepositoryInspector::text)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private static List<Element> findElements(Document document, String localName) {
        var result = new ArrayList<Element>();
        var nodes = document.getElementsByTagNameNS("*", localName);
        for (int index = 0; index < nodes.getLength(); index++) {
            if (nodes.item(index) instanceof Element element) {
                result.add(element);
            }
        }
        return result;
    }

    private static List<Element> directChildren(Element parent, String localName) {
        var result = new ArrayList<Element>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && localName.equals(localName(element))) {
                result.add(element);
            }
        }
        return result;
    }

    private static String childText(Element parent, String childName) {
        return directChildren(parent, childName).stream().findFirst().map(MavenRepositoryInspector::text).orElse("");
    }

    private static String text(Element element) {
        return element.getTextContent() == null ? "" : element.getTextContent().trim();
    }

    private static String localName(Element element) {
        return element.getLocalName() == null ? element.getNodeName() : element.getLocalName();
    }

    private static String safeMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private static final class ThrowingErrorHandler implements ErrorHandler {
        @Override
        public void warning(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void error(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            throw exception;
        }
    }
}
