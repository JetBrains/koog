# Code Agent Example — Step 04: Add Sub‑agent

Run a code‑editing agent that can also delegate smart "find in codebase" queries to a sub‑agent.

## Quick start
1) Build (from repo root):
```bash
./gradlew -p examples/code-agent/step-04-add-subagent shadowJar
```
The JAR: `examples/code-agent/step-04-add-subagent/build/libs/code-agent-all.jar`.

2) Run (macOS/Linux):
```bash
OPENAI_API_KEY=sk-... \
LANGFUSE_PUBLIC_KEY=pk-... \
LANGFUSE_SECRET_KEY=sk-... \
LANGFUSE_HOST=https://langfuse.labs.jb.gg \
LANGFUSE_SESSION_ID=my-session \
java -jar examples/code-agent/step-04-add-subagent/build/libs/code-agent-all.jar \
"/absolute/path/to/project" \
"task description"
```
Windows (PowerShell): set the env vars with `$env:VAR = "..."` then run the same `java -jar ...` command.

## Arguments
- 1st: absolute path to the target project.
- 2nd: natural‑language task for the agent (quote if it has spaces).

## Environment
- Required to run: `OPENAI_API_KEY`.
- Observability (Langfuse): all four are required to enable it — `LANGFUSE_PUBLIC_KEY`, `LANGFUSE_SECRET_KEY`, `LANGFUSE_HOST`, `LANGFUSE_SESSION_ID`. If any are missing or blank, Langfuse is disabled and the program prints which ones are missing.
- Optional: `BRAVE_MODE=true` to auto‑approve shell commands (use with care).

## Notes
- JDK 17+ required. Internet access needed.
- Entry point: `src/main/kotlin/Main.kt`.
- If the JAR isn’t found, make sure the build completed and the file exists at the path above.
