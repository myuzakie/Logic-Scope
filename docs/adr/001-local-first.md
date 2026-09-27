# ADR 001: Local-first execution

## Decision

LogicScope keeps source code and the primary investigation workflow local to the developer environment.

## Rationale

The product needs source context and runtime evidence, so uploading an entire repository to a hosted service is not a prerequisite. Docker Compose provides local PostgreSQL and OpenTelemetry infrastructure for development.

