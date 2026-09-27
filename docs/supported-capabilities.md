# Supported capabilities

## Target envelope

LogicScope is designed for:

- Java 17 or Java 21;
- Spring Boot 3.x;
- Maven, including Maven multi-module projects;
- Spring MVC and Spring DI;
- JDBC and JPA/Hibernate persistence;
- synchronous execution;
- OpenTelemetry Java Agent;
- Docker Compose and PostgreSQL for local infrastructure.

## Current detection

### Capabilities detected as SUPPORTED

The `MavenRepositoryInspector` reads standard `pom.xml` metadata and local Java source files. It currently detects and reports `SUPPORTED` for the following four capabilities:

| Capability | Detection method |
|---|---|
| `JAVA` | Presence of `.java` source files or Maven compiler properties (`java.version`, `maven.compiler.release`, `maven.compiler.source`, `maven.compiler.target`) |
| `MAVEN` | Presence of a parseable `pom.xml` at the repository root |
| `MAVEN_MULTI_MODULE` | `<modules>` block in the root `pom.xml` with at least one declared module |
| `SPRING_BOOT` | Spring Boot `<parent>`, a direct `<dependency>` with `groupId` `org.springframework.boot`, or the `spring-boot-maven-plugin` |

When Java version information is available in Maven properties or the `<release>` element of `maven-compiler-plugin`, the detected version string is included in the report.

### Capabilities that return UNKNOWN

All remaining `RepositoryCapability` values are initialized to `UNKNOWN` because no detection logic is currently implemented for them:

- `SPRING_MVC`
- `SPRING_DI`
- `JPA`
- `OPENTELEMETRY`
- `KAFKA`
- `WEBFLUX`

These capabilities are never inferred from class names, annotations, or assumptions. If detection is not implemented, the status is `UNKNOWN`.

### CapabilityStatus.SUPPORTED_WITH_LIMITATIONS

The `SUPPORTED_WITH_LIMITATIONS` status is defined in `CapabilityStatus` and is available for use, but no capability currently assigns this value. It is reserved for future use when a capability can be detected but known limitations affect reliability.

## Known limitations of current detection

The following limitations apply to `MavenRepositoryInspector` in the current implementation:

1. **No parent POM property resolution.** Version values defined in a parent POM outside the repository are not resolved. An unresolved `${property}` reference is returned as-is or reported as blank.

2. **One level of module traversal only.** The inspector reads module names from the root `pom.xml` `<modules>` block and reads one `pom.xml` per declared module. It does not recurse into sub-modules of sub-modules.

3. **Repository-wide Java file scanning may be expensive.** When no Maven compiler metadata is present, the inspector walks the entire repository tree looking for `.java` files. On large repositories this can be slow.

4. **BOM-only Spring Boot detection may be missed.** Spring Boot detection covers a `<parent>`, a direct `<dependency>`, and the `spring-boot-maven-plugin`. A project that adds the Spring Boot BOM exclusively through `<dependencyManagement>` `<scope>import</scope>` without any direct Spring Boot artifact reference may not be detected as Spring Boot.

## Explicitly unsupported

Gradle, Kafka execution, Spring WebFlux, Quarkus, Micronaut, Kubernetes, Node.js, Python, .NET, IDE extensions, SaaS/cloud execution, vector databases, automatic code repair, and counterfactual debugging are not part of this repository's supported boundary.

## Planned but not implemented

The following capabilities are within the intended target envelope but have no detection or analysis logic yet:

- Spring MVC and Spring DI detection;
- JPA and JDBC detection;
- OpenTelemetry Agent detection;
- runtime trace ingestion;
- execution slicing;
- semantic AI analysis;
- runtime evidence verification;
- replay.
