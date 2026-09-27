# Architecture

LogicScope is local-first and intentionally starts as a modular monolith. The backend has one deployable Spring Boot application, `logicscope-app`, with internal Maven modules for domain, discovery, CLI, trace, source, semantic analysis, evidence, replay, and persistence.

## Module status

### Implemented modules

**`logicscope-domain`** — framework-independent domain model. Contains value types and enums: `RepositoryCapability`, `CapabilityStatus`, `CapabilityAssessment`, `CapabilityReport`, `RepositoryLocation`, `EvidenceStatus`, `InvestigationId`, `SourceLocation`, `TraceId`, `ProjectId`, `ProjectRecord`, `ProjectSourceType`, `ProjectStatus`. Has no Spring Boot, OpenTelemetry, PostgreSQL, Hibernate, IBM Bob, JavaParser, or HTTP-client dependency.

**`logicscope-discovery`** — capability detection. `MavenRepositoryInspector` reads `pom.xml` files and local Java source to produce a `CapabilityReport`. See [Known limitations of MavenRepositoryInspector](#known-limitations-of-mavenrepositoryinspector) below.

**`logicscope-project`** — project registration, Git clone, workspace management, and capability inspection. `ProjectService` accepts a local path or a public HTTPS Git URL, records a `ProjectRecord` with commit SHA, and delegates inspection to `MavenRepositoryInspector`. `FileProjectRegistry` persists records as JSON under `LOGICSCOPE_DATA_DIR` (default `~/.logicscope`). Only HTTPS Git URLs are accepted; credentials are not accepted in URLs. Capability detection results reflect metadata only and do not indicate runtime readiness.

**`logicscope-cli`** — command-line interface. `LogicScopeCli` provides `scan`, `load`, `projects`, and `inspect` commands. `scan <repository>` runs `MavenRepositoryInspector` directly on a path. `load <path-or-https-url>` registers a project. `projects` lists registered projects. `inspect <project-id>` runs capability inspection on a registered project. Exits with code `0` (success/supported), `1` (failure/unsupported), or `2` (usage error).

**`logicscope-app`** — runnable Spring Boot 3.x application. Exposes `GET /api/health`, `POST /api/scan`, and `POST /api/investigate` on port `4377`; the dashboard is served at `/dashboard/`.

**`logicscope-source`** — framework-independent Java source investigation. Performs case-insensitive literal searches in `.java` files, skipping symlinks and generated/vendor directories. It reports at most 50 matching lines, with snippets up to 800 characters, and skips files larger than 1 MiB. Results are ordered by relative path and line; class/method labels use conservative lexical heuristics. This module does not parse Java or perform AI, trace, replay, database, or MCP operations.

**`logicscope-persistence`** — persistence adapters. PostgreSQL and Flyway connectivity.

### Scaffolding-only modules

The following Maven modules exist in the repository but contain no implemented production logic:

- **`logicscope-trace`** — planned runtime trace ingestion.
- **`logicscope-semantic`** — planned semantic and AI analysis.
- **`logicscope-evidence`** — planned runtime evidence handling.
- **`logicscope-replay`** — planned replay functionality.

These modules are reserved placeholders. Their `pom.xml` files register them in the build, but no production classes exist.

## Dependency direction

```text
logicscope-app
  -> logicscope-discovery, logicscope-project, logicscope-trace, logicscope-source
  -> logicscope-domain

logicscope-cli
  -> logicscope-project
  -> logicscope-discovery
  -> logicscope-domain

logicscope-project
  -> logicscope-discovery
  -> logicscope-domain

logicscope-semantic, logicscope-evidence, logicscope-replay, logicscope-persistence
  -> logicscope-domain
```

The domain module stays framework-independent. It has no Spring Boot, OpenTelemetry, PostgreSQL, Hibernate, IBM Bob, JavaParser, or HTTP-client dependency.

## Planned investigation flow

The following flow is the intended future direction. Source investigation and the dashboard are implemented; the runtime-driven stages remain planned:

```text
repository capability discovery   [IMPLEMENTED]
source investigation              [IMPLEMENTED]
  -> runtime trace                [PLANNED]
  -> execution slice              [PLANNED]
  -> semantic analysis            [PLANNED]
  -> runtime evidence             [PLANNED]
  -> replay                       [PLANNED]
```

## Known limitations of MavenRepositoryInspector

`MavenRepositoryInspector` reads standard Maven metadata only. The following limitations are present in the current implementation:

1. **No parent POM property resolution.** Version values that are defined in a parent POM outside the repository are not resolved. Unresolved `${property}` references are returned as-is or reported as blank.

2. **One level of module traversal only.** The inspector reads module names from the root `pom.xml` `<modules>` block and reads one `pom.xml` per declared module. It does not recurse into sub-modules of sub-modules.

3. **Repository-wide Java file scanning may be expensive.** When no Maven compiler metadata is present, the inspector walks the entire repository tree looking for `.java` files. On large repositories this can be slow.

4. **BOM-only Spring Boot detection may be missed.** Spring Boot presence is detected from a `<parent>`, a direct `<dependency>`, or the `spring-boot-maven-plugin`. A Spring Boot dependency managed exclusively through an imported BOM without a direct reference may not be detected.

## CapabilityStatus values

`CapabilityStatus` defines four values:

| Value | Meaning |
|---|---|
| `SUPPORTED` | Detected and within the supported envelope |
| `SUPPORTED_WITH_LIMITATIONS` | Detected but subject to known limitations (defined; currently unused) |
| `UNSUPPORTED` | Explicitly not detected or outside the supported envelope |
| `UNKNOWN` | Detection logic is not implemented for this capability |

`SUPPORTED_WITH_LIMITATIONS` is defined in the domain model but is not currently assigned by any detection logic.
