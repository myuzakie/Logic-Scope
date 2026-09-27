# Source module

JavaParser integration and source slicing are intentionally not implemented yet. The future boundary is:

```text
runtime service/route -> Spring entrypoint -> Java symbol -> bounded dependency expansion -> SourceSlice
```

The future analyzer must use actual Java syntax and symbols rather than controller/service naming conventions.

