# LogicScope CLI

The CLI starts with a thin local repository scan adapter. It reuses the existing `logicscope-discovery` module and does not start Spring Boot or require Docker.

After building the backend, run:

```bash
./logicscope scan /path/to/repository
```

The planned commands beyond the current `scan` command are:

```text
logicscope init
logicscope scan
logicscope run
logicscope traces
logicscope investigate
logicscope replay
```

The discovery engine is already available in the `logicscope-discovery` Maven module and is covered by local fixture tests. No command in this directory pretends to implement commands that do not exist yet.
