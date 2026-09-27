# ADR 003: Runtime evidence verifies hypotheses

## Decision

Semantic analysis may produce a hypothesis, but LogicScope must not present it as verified without runtime evidence.

## Rationale

The product is intended to explain observed behavior. Runtime execution determines where to look, semantic analysis describes what the executed code may mean, and evidence determines whether that explanation is true.

