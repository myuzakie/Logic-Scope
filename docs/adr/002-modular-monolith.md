# ADR 002: Modular monolith

## Decision

The backend is one Spring Boot deployable with Maven modules representing domain boundaries.

## Rationale

Trace normalization, source analysis, semantic analysis, evidence, and replay are related parts of one local investigation workflow. Separate services would add network and deployment complexity before those boundaries are stable.

