# LogicScope

LogicScope is a local-first debugging tool that uses runtime execution to narrow large Java applications into the source relevant to a specific behavior. AI semantic analysis can then reconstruct business decisions from that execution context, while runtime evidence is used to verify causal claims.

It is not a generic repository chatbot, a static code documentation generator, an observability replacement, a production APM, or a universal Java analyzer.

## Current milestone

This repository contains the initial runnable foundation:

- a Java 21 / Spring Boot 3.x modular-monolith backend;
- `GET /api/health` on port `4377`;
- PostgreSQL and Flyway connectivity;
- a minimal OpenTelemetry Collector configuration ready for future ingestion;
- deterministic Maven repository capability discovery, including multi-module detection;
- a CLI module (`logicscope-cli`) providing the `scan <repository>` command;
- tests using local fixtures only.

### Implemented modules

| Module | Status | Notes |
|---|---|---|
| `logicscope-domain` | Implemented | Framework-independent domain model |
| `logicscope-discovery` | Implemented | Maven-based capability detection |
| `logicscope-cli` | Implemented | `scan <repository>` command |
| `logicscope-app` | Implemented | Runnable Spring Boot application |
| `logicscope-persistence` | Implemented | Persistence adapters (Flyway, PostgreSQL) |
| `logicscope-trace` | Scaffolding only | Maven module; no production logic |
| `logicscope-source` | Scaffolding only | Maven module; no production logic |
| `logicscope-semantic` | Scaffolding only | Maven module; no production logic |
| `logicscope-evidence` | Scaffolding only | Maven module; no production logic |
| `logicscope-replay` | Scaffolding only | Maven module; no production logic |

Trace ingestion, source slicing, semantic analysis, IBM Bob integration, evidence verification, replay, instrumentation, investigation APIs, and the browser UI are intentionally not implemented yet.

## Supported capability envelope

The planned target is Java 17 or 21, Spring Boot 3.x, Maven (including multi-module Maven), Spring MVC, Spring DI, JDBC/JPA, synchronous execution, and OpenTelemetry Java Agent. The current discovery implementation reliably reports only Java, Maven, Maven multi-module, and Spring Boot when those facts can be detected from Maven metadata. All other capabilities (`SPRING_MVC`, `SPRING_DI`, `JPA`, `OPENTELEMETRY`, `KAFKA`, `WEBFLUX`) return `UNKNOWN`.

Gradle, Kafka, Spring WebFlux, Quarkus, Micronaut, Kubernetes, Node.js, Python, .NET, IDE extensions, SaaS/cloud execution, vector databases, automatic repair, and counterfactual debugging are outside the supported boundary.

## CLI usage

The `logicscope-cli` module provides the `scan` command. Run it from the project root wrapper:

```bash
./logicscope scan <path-to-repository>
```

Output includes detected capabilities (Java version, Maven, Spring Boot, Maven multi-module), an overall `SUPPORTED` or `UNSUPPORTED` status, and a reason when not supported.

Exit codes: `0` = supported, `1` = unsupported or failed, `2` = usage error.

## Build and run

Use Java 21 for the backend build. Maven cannot compile this project when it is launched with Java 17:

```bash
cd backend
java -version  # must report 21.x
mvn test
mvn -pl logicscope-app -am spring-boot:run
```

With SDKMAN, select Java 21 before running Maven:

```bash
sdk use java 21.0.8-tem
cd /home/muzakie/Workspace/projects/hackaton/logicScope
mvn -f backend/pom.xml clean test
```

To start the local infrastructure and backend together:

```bash
./scripts/dev-up.sh
curl http://localhost:4377/api/health
./scripts/health-check.sh
./scripts/dev-down.sh
```

If `docker compose` reports `unknown flag: --build`, the Docker Compose v2 CLI plugin is not installed. On Ubuntu/Debian systems using Docker's package repository, install it with:

```bash
sudo apt-get update
sudo apt-get install docker-compose-plugin
docker compose version
```

Docker documents the Linux plugin installation and a user-local manual installation as alternatives: [Install the Docker Compose plugin](https://docs.docker.com/compose/install/linux/).

Expected health response:

```json
{"status":"UP","service":"logicscope"}
```

The future per-project configuration can live outside this repository at `.logicscope/config.yaml`:

```yaml
project:
  root: .
runtime:
  type: local
observability:
  otlpEndpoint: http://localhost:4318
server:
  port: 4377
```

## Repository layout

The backend is a modular monolith. `logicscope-app` is the only runnable application; discovery, trace, source, semantic, evidence, replay, persistence, and domain are modules of that backend rather than separate services.

External public Spring Boot repositories are validation targets only. They are not vendored here and must not require repository-specific adapters.
