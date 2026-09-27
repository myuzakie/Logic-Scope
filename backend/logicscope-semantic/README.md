# Semantic module

IBM Bob integration is intentionally not implemented yet. The future input/output boundary is:

```text
SourceSlice + Runtime Context + Observed Outcome
  -> Semantic Analyzer / IBM Bob
  -> Semantic Decisions
```

Only relevant execution context should be provided; the full repository is not the default input.

