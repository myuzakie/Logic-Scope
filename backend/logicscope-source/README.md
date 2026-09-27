# LogicScope Source

`logicscope-source` provides framework-independent, bounded Java source investigation for the application module.

## Behavior

- Searches `.java` files for a case-insensitive literal substring.
- Returns at most one result for each matching line, ordered by relative path and then line number.
- Includes relative path, 1-based line number, a snippet no longer than 800 characters, and optional class/method names when simple lexical heuristics can identify them.
- Caps returned matches at 50. `totalMatches` counts matching lines found and `truncated` is true when more than 50 were found.
- Skips files larger than 1 MiB, symbolic links, and directories named `.git`, `target`, `.m2`, `node_modules`, `build`, or `out`.
- Continues past individual unreadable files.

The module intentionally has no Spring dependency and does not use a Java parser, AI service, tracing, replay, persistence, or MCP integration. The application module owns HTTP validation and error mapping.
