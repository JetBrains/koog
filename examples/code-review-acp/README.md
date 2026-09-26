# Code Review ACP Agent

A Koog-based ACP agent that reviews the changes on the current git branch.
This README explains how to wire it into IntelliJ IDEA so the agent
appears in the AI Chat agent selector and runs as a subprocess of the IDE.

The same launcher works with any ACP-speaking host (Zed, Claude Code, etc.) —
only the host's configuration UI differs.

---

## Project layout

```
code-review-acp/
├── src/main/kotlin/ai/codereview/
│   ├── agent/
│   │   ├── CodeReviewAcpAgent.kt       # stdio entry point — what the host spawns
│   │   ├── CodeReviewAgent.kt          # AIAgent factory + strategy + helpers
│   │   ├── CodeReviewAgentSupport.kt   # ACP AgentSupport / AgentSession plumbing
│   │   ├── CodeReviewModels.kt
│   │   └── GitTools.kt
│   ├── app/
│   │   ├── CodeReviewPipeApp.kt        # in-process dev harness (single Gradle task)
│   │   └── CodeReviewProcessApp.kt     # subprocess-via-AGENT_PATH harness
│   └── client/
│       ├── CodeReviewClientOperations.kt
│       └── util.kt
├── build.gradle.kts
└── settings.gradle.kts
```

`CodeReviewAcpAgent.kt` is the entry point that an ACP host launches. It
reads JSON-RPC from `System.in` and writes to `System.out` — nothing else
may go to stdout, which is why `logback.xml` is configured to log to stderr.

---

## Prerequisites

- **IntelliJ IDEA 2026.1.*** (with ACP support enabled in AI Chat options).
- An **OpenAI API key** — the agent uses GPT-4o Mini.
- **git** on `PATH` — the agent shells out to git for diffs and commits.

---

## Step 1 — build the installable distribution

```bash
cd examples/code-review-acp
./gradlew installDist
```

This produces a self-contained directory:

```
build/install/code-review-agent/
├── bin/
│   ├── code-review-agent           # Unix/macOS launcher
│   └── code-review-agent.bat       # Windows launcher
└── lib/
    └── *.jar                       # All dependencies bundled
```

Verify the launcher exists:

```bash
ls -lh build/install/code-review-agent/bin/code-review-agent
```

Get its absolute path — you'll paste it into the IntelliJ config:

```bash
realpath build/install/code-review-agent/bin/code-review-agent
# e.g. /Users/you/IdeaProjects/koog/examples/code-review-acp/build/install/code-review-agent/bin/code-review-agent
```

---

## Step 2 — register the agent with IntelliJ

1. Open **IntelliJ IDEA 2026.1.***.
2. Open **AI Chat → Options → Configure ACP Agents** (also surfaced as
   **AI Assistant → Settings → ACP Agents** in some builds).
3. Paste the following JSON, substituting your absolute path and key:

```json
{
  "agent_servers": {
    "Code Review Agent": {
      "command": "/absolute/path/to/build/install/code-review-agent/bin/code-review-agent",
      "args": [],
      "env": {
        "OPENAI_API_KEY": "sk-..."
      }
    }
  }
}
```

**Windows note:** point `command` at `code-review-agent.bat` and use double-
backslashed paths (`C:\\Users\\you\\…`).

---

## Step 3 — use it

1. In IntelliJ, open the repository you want reviewed and check out the branch.
2. Open **AI Chat** (View → Tool Windows → AI Chat).
3. From the agent selector dropdown, pick **"Code Review Agent"**.
4. Send a prompt:

   - `Review the current branch against develop`
   - `Review the current branch against main`
   - `Review last 3 commits`
   - `Review against commit abc123`

The agent will:

1. Emit a one-entry plan: *Discover and group changed files* (in progress).
2. Call `git diff --name-only` against the base ref, then ask the LLM to
   group the changed files into logical review units.
3. Expand the plan with one entry per group, ordered HIGH → MEDIUM → LOW
   priority. IntelliJ renders the live plan with status transitions.
4. Review each group in turn — calling `getCommitMessages`, `getFileDiff`,
   `list_directory`, `read_file` as needed. Each group flips IN_PROGRESS →
   COMPLETED before the next one starts.
5. Stream a formatted Markdown review back at the end (sections per group,
   per-finding `file:line` references, LGTM / NEEDS_CHANGES verdict).

The agent's `cwd` is set from IntelliJ's session parameters, so `git`
operates against the IDE's currently-open project regardless of where the
launcher binary lives.

---

## Troubleshooting

- **"OPENAI_API_KEY env is not set"** — the env var was not threaded
  through the JSON config. Make sure the `env` block is present and the
  IDE was restarted after editing.
- **JSON-RPC parse errors / agent disconnects immediately** — something
  is writing to stdout besides the protocol. Check `build/install/code-
  review-agent/lib/` for extra logging dependencies; verify `logback.xml`
  in the bundled resources still routes to stderr.
- **`Git command failed`** — the IDE's session `cwd` isn't a git repo.
  Open a real repo before invoking the agent.
- **`AIAgentMaxNumberOfIterationsReachedException`** — bump
  `maxAgentIterations` in `CodeReviewAgent.kt` and re-run `installDist`.

---

## Running without IntelliJ (development)

The two `app/` entry points are for local iteration without bouncing through
the IDE:

```bash
# In-process: agent and client share the JVM
./gradlew runCodeReviewPipeApp --args="develop"

# Subprocess: client spawns the agent via AGENT_PATH
./gradlew installDist
AGENT_PATH=$(realpath build/install/code-review-agent/bin/code-review-agent) \
  ./gradlew runCodeReviewProcessApp --args="develop"
```

The strategy, prompts, and tools are identical to what IntelliJ will run —
so anything that works in the PipeApp will work in the IDE.
