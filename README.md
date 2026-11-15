# Java debugging agent (demo)

Takes a Java error from Kibana-style logs to the exact class and line, explains the root cause,
drafts a regression test, and then checks post-fix logs to confirm the error is gone.

> **About this repository.** This is a clean-room demo of a pattern I built and use professionally.
> It was written from scratch for this repository and contains no employer code, data or configuration.
> The logs and the `sample-app` are synthetic.

## What it does

```
logs (NDJSON) ─▶ group errors ─▶ locate class + line ─▶ diagnose ─▶ draft regression test ─▶ verify in post-fix logs
```

1. **Read logs.** Parses a Kibana-style export, one JSON document per line.
2. **Group errors.** Finds the root exception (the last `Caused by`), skips JDK and framework frames,
   and groups entries by exception type and application method, so a bug that fired 300 times is investigated once.
3. **Locate the source.** Maps the top application frame to the file and line and pulls the surrounding code.
4. **Diagnose.** An `Analyst` returns the root cause, a suggested fix and a regression test draft.
5. **Verify.** Re-reads logs captured after the fix and reports each error as `RESOLVED` or `STILL OCCURRING`.

## Run it

Requires JDK 17 or newer. There are no runtime dependencies.

With Maven:

```bash
mvn test                 # unit tests + an end-to-end test on the sample data
mvn compile exec:java    # runs the agent on the sample data
```

Without Maven:

```bash
javac -d build $(find src/main/java -name '*.java')
java -cp build io.github.adit12t.debugagent.DebugAgent
```

Output:

```
NullPointerException       x3  DiscountService.java:17      RESOLVED
ArithmeticException        x2  OrderService.java:25         STILL OCCURRING
Report: out/report.md
```

The full report for the sample data is in [docs/sample-report.md](docs/sample-report.md).

### Options

| Option | Default | Meaning |
|---|---|---|
| `--logs` | `sample-data/logs-before.ndjson` | Logs to investigate |
| `--after-logs` | `sample-data/logs-after.ndjson` | Logs captured after the fix, for verification |
| `--source-root` | `sample-app/src/main/java` | Where the application source lives |
| `--app-package` | `demo.orders` | Package prefix that marks "our" code in a stack trace |
| `--out` | `out` | Where the report and test drafts are written |

With Maven, pass them as `-Dexec.args="--logs my-logs.ndjson --app-package com.mycompany"`.

## Two analysts

| Analyst | When it is used | What it produces |
|---|---|---|
| `RuleBasedAnalyst` | Default. No API key needed. | Root cause and fix from rules for common exceptions, plus a regression test **skeleton** |
| `ClaudeAnalyst` | When `ANTHROPIC_API_KEY` is set | Root cause, fix and a **complete** JUnit 5 test written by Claude |

`ClaudeAnalyst` calls the Anthropic Messages API. Set `ANTHROPIC_MODEL` to choose the model.
If the call fails for any reason, the run falls back to the offline analyst.
The published sample output was produced with the offline analyst.

## Design decisions

- **Fingerprint by exception type and method, not line number.** Line numbers move when a file is edited;
  the fingerprint has to survive the fix so verification can compare before and after.
- **Root cause first.** The last `Caused by` is the real failure; wrapper exceptions only say where it surfaced.
- **Verification is part of the pipeline.** A suggested fix is not done until the logs say the error stopped.
- **The model is behind an interface.** The pipeline is deterministic and testable; only the diagnosis step is swappable.

## Layout

```
src/main/java/.../debugagent   the agent (DebugAgent is the entry point)
src/test/java/.../debugagent   JUnit 5 tests
sample-app/                    small Java app with two deliberate bugs (not compiled, only read)
sample-data/                   synthetic logs before and after a fix
docs/sample-report.md          example report
```

## Limits of the demo

- Reads log files; it does not query a live Kibana or Elasticsearch instance.
- Does not apply fixes or run the drafted tests. A developer reviews both.
- The offline rules cover a handful of exception types.
