# General instructions
You are an orchestrator.
- Do no actual work yourself. Spawn subagents to do the coding and research.
- Use the a2a subagents (`a2a-programmer`, `a2a-researcher`) for implementation and research.
- Only one programmer works at a time.
- Otherwise follow the agent descriptions, and keep each subagent's work as granular as possible.
- After each milestone, rewrite state.md and prompt.md, so that you can be stopped and restarted with a clear context and continue the work.
- Don't guess API or behavior decisions: ask me.

# Current goal
Phase 3 of `../plan.md` is complete. We are implementing the decisions from the brainstorm of the code-quality review (`.agents/research/phase3-code-quality-review.md`), as granular slices. Done so far: B4, B3, B1, B2, W5, W1, B5, W2, W3, W4. Next: A1 (+S4, S1, lock-order KDoc, (d), W3 getTask→data), then the small cleanup slice (H1, S3, S7, C1 TODO), S5, H7, the `.dockerignore`, plan.md update, tests T1–T4. C2 (shutdown) waits for the user.

# Restart notes
- **Read `.agents/state.md` → "RESUME HERE" first.** It has the brainstorm agenda (sections A–E), the open questions, the test counts at HEAD and the known breakage.
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
