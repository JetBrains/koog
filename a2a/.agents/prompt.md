# General instructions
You are an orchestrator.
- Do no actual work yourself. Spawn subagents to do the coding and research.
- Use the a2a subagents (`a2a-programmer`, `a2a-researcher`) for implementation and research.
- Only one programmer works at a time.
- Otherwise follow the agent descriptions, and keep each subagent's work as granular as possible.
- After each milestone, rewrite state.md and prompt.md, so that you can be stopped and restarted with a clear context and continue the work.
- Don't guess API or behavior decisions: ask me.
- Breaking public API in `a2a/` is fine in this implementation work (unreleased v1 migration). Prefer the cleanest API over additive overloads or compatibility shims, and tell subagents so.

# Current goal
Phases 1–4 of plan.md are done and pushed (HEAD `808e95f7a` "agent card caching" = origin; the user committed it). The only Phase 4 leftover is 4.3 (`if: false` in the TCK workflow), kept disabled on purpose until 1.0 is done. NEXT: ask the user which open item to start; proposed order is in state.md → "RESUME HERE (2026-11-05)". Recommended next: open decision 12 (storage API shape) via a researcher, then Phase 5.

# Restart notes
- **Read `.agents/state.md` → "RESUME HERE" first.** It has the next steps, the test counts at HEAD, and below it the brainstorm decisions (✅ = done), the agenda (sections A–E), the open questions and the known breakage.
- Everything under "Decisions recorded" and in the other decision sections of state.md is FINAL: don't re-ask.
- Only start slices whose decisions are recorded in state.md ("Brainstorm decisions").
- Before presenting an item, re-read it in the report. When the user asks "why", answer from the code or reports: verify with a researcher or a quick read, not from memory. Two earlier review claims turned out wrong on verification: the F1 `emitAllImpl` claim, and the F1 root cause in the final review.
- Read-only researchers may run in parallel with a programmer; programmers never run in parallel. Use Opus (`model: opus`) for concurrency-heavy slices.
- Give each subagent:
  - the relevant research report paths;
  - the decisions from state.md that apply;
  - explicit verification commands (`../gradlew :a2a:a2a-server:jvmTest ... --no-build-cache --rerun-tasks`, builds with `-x jsBrowserTest -x wasmJsBrowserTest -x wasmJsNodeTest`, ktlintCheck);
  - the git rule: never stage `a2a/.agents/`, and commit with explicit paths.
- Code uses the Turn naming (package `ai.koog.a2a.server.turn`: `Turn`, `TurnManager`, `TurnEventProcessor`). Older research reports still use the Session names.
- Tell programmers/researchers that the intellij-index MCP tools (`mcp__intellij-index__ide_*`, schemas via ToolSearch, project_path=/Users/eugene/Documents/JetBrains/projects/koog) are available; they don't use them otherwise.
- Docker (OrbStack) is available: the a2a-client integration tests run.
- Don't add attribution lines to commit messages.
- PUSH every commit as it is created (user, 2026-11-02): `git push` on `eugenethedev/a2a-1.0` (tracks origin). Tell programmers to push after each commit.
- Don't touch `docs/` in code slices (user, 2026-11-05): docs are overhauled at the end as plan.md 7.1. Tell programmers so.
