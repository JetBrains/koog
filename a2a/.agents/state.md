# Orchestrator state — Phase 3 (a2a-server + JSON-RPC server transport)

Last updated: 2026-10-21, before a flush. HEAD = `65b063414` on `eugenethedev/a2a-1.0` (nothing pushed). Working tree clean except untracked/orchestrator files under `a2a/.agents/`.

Source plan: `a2a/plan.md` §3 Phase 3. **Phase 3 is complete and `plan.md` is updated** (3.1–3.20 ✅, 3.21 ⏭).

## RESUME HERE (2026-10-21)
Phase 3 is done. The user asked for a code-quality and architecture review, which is done: `.agents/research/phase3-code-quality-review.md`. **The user will brainstorm its findings in a NEW session. No fixes have started; start none until the user decides.**

Test counts at HEAD:
- a2a-server: 229 (3 stress tests skipped)
- core-jsonrpc: 15
- server-jsonrpc-http: 25
- a2a-core: 63
- a2a-client: 12 (8 Docker tests skipped)

### Brainstorm decisions (2026-10-21, FINAL unless marked open)
- A: implement the proposed fixes for B1, B2, B3, B4. B2 = MINIMAL fix (`finally { if terminal closeAll() }`) (confirmed). B3 = internal `SupervisorJob` in `TurnManager` + push dispatcher.
- W1: implement (typed in-band `TurnOutcome`, moves `rejection` out of `TurnEventProcessor`).
- W5: implement (one non-suspending close; `isTaskEventSent` private; `isOpen` not for tests).
- W2: ROOT FIX. `startTurn` gets a `beforeStart` step run under the gate after the terminal check, before the agent starts; only the inline-config `save` moves there (validation stays before). Remove `isNew`/`getAll`/`SavedInlinePushConfig`/`deleteInlinePushConfig` (R2-Q1 moot). New-task orphan cleanup moves to the monitor's NonCancellable teardown (fixes shutdown leak B6). Accepted: one push-storage save under the gate (same task only).
- W3: CHEAP FIXES only (no stream-state variant): owner-scoped read only under the gate (merge reads 1+2; standalone processor gets contextId after that read); merge the TurnNotActive read into afterCancel; read afterCancel with artifacts and return it if terminal; skip the post-cancelAndJoin re-read when `agentJob == null`. Target: 2 reads in the normal case.
- W4: ROOT FIX: `sseSession` + iterate `incoming` outside Ktor's block, cancel in `finally`; pre-stream JSON errors mapped where the session opens; remove the `onCompletion` unwrap. Programmer verifies Ktor 3.3.3 `sseSession` behavior.
- Slice order (user OK): B4 → B3 → B1 → B2 → W5 → W1 → W2 → W3 → W4; T3 tests for B1–B3 and the W1 race go into each fix's own slice.
- S5: introduce `appendHistory` storage op (default impl via get + update).
- C2: implement the shutdown flag with an explicit error (non-graceful path; error code per the orchestrator's suggestion, to be fixed with the C2 design). PLUS investigate a graceful shutdown: stop accepting new requests, wait for in-flight ones, optional grace timeout (researcher running).
- C1: add a TODO comment only, skip otherwise.
- B5: OPTION C + KDoc fix (decided). Report `.agents/research/b5-followup-errors.md`. An `A2AException` (ANY subtype, incl. `A2AInternalErrorException`, no special case) thrown by the agent BEFORE its first event of the turn = plain request error: task keeps its state, the requester gets the error code. Anything else (later exception, non-A2A exception) → FAILED as today. AgentExecutor KDoc: say so. Sub-questions DECIDED: after a crash that marks FAILED the requester still gets the FAILED task (no Python-style error); FAILED status keeps the generic message.
- C2 graceful: report `.agents/research/c2-shutdown-design.md` presented (decisions D1–D8). Waiting for the user.
- A1: FULL A1, DATA APPROACH (decided). `TurnManager.startTurn(request, ctx, inlinePushConfig, block = { storedTask, processor -> })`: per-task FIFO slot (replaces `tasksMutex` + `join()`), then under the gate: one owner-scoped read, TaskNotFound/contextId/terminal checks, inline-config save, view opened from the same read (snapshot projected by historyLength; agent gets full task), agent start. No caller callbacks under the gate. Removes `tasksMutex`, `check(taskId !in turns)`, the A2AServer pre-check. Runs as its own slice AFTER W2/W3 (opus). W2 must already add `inlinePushConfig` as a `startTurn` PARAMETER (not a `beforeStart` lambda).
- Slices done: B4 ✅ `06d396415` (server 229, 3 skipped; other in-memory storages audited, no other read-lock mutation; nested read locks in InMemoryTaskStorage are safe: RWLock nested readers don't touch writeMutex).
- B3 ✅ `a72a39ee7` (TurnManager private internal scope = user context + child SupervisorJob; dispatcher shares it; server 232, 3 skipped; jsonrpc-http 25, core-jsonrpc 15). Follow-up for T1: `agentScope()` supervisor fixtures no longer needed.
- B1 ✅ `596373761` (internal `AgentCancellationFailure : IllegalStateException` wrapper via `runAgent` + `ensureActive`; server 237, 3 skipped). Follow-up: `Turn.agentJob` KDoc mentions exception propagation; now wrapper for agent's own CE (fix in W1/W5 KDoc pass).
- B2 ✅ `b29a7b6ae` (persistAndDeliver: finally closeAll on terminal; markFailed reuses it; server 240, 3 skipped).
- S4: DECIDED (b): `TurnManager` and `Turn` stay public but `@InternalA2AApi` as a whole; `A2AServer.turnManager` → `protected val` (NOT open). No TurnManager factory/constructor param for now. TurnManager core methods are NOT made open; customization via narrow hooks (candidates: validateFollowUp(storedTask, request, ctx) non-suspending, failureStatus(task, error), follow-up policy, back-pressure policy, shutdown params) — to be decided later (researcher inventory RUNNING → `.agents/research/server-hooks-inventory.md`). Do S4 together with/right after A1.
- Still open (user: tackle ALL after the hooks research is in): C2 D1–D8 (recommendations given: D1 -32603 yes; D2 add shutdown(grace), cancel()=shutdown(ZERO); D3 reject only Send/Stream/Subscribe in grace; D4 end subs at drain/deadline, normal if terminal else -32603; D5 keep last state; D6 cancel only internal child job; D7 no lifecycle hook now, document order; D8 deadline bounds C1), hooks pick, plan.md §4.10 (TCK SUT push, Phase 4). Smells scheduling proposed: S6→W1, S2→W2, S1+lock-order KDoc→A1, S3+S7+C1 TODO→small cleanup slice. R2 Q1 moot after W2.
- W5 ✅ `5abcd0704` (one non-suspending internal close(); isOpen private AtomicBoolean; isTaskEventSent private; no public API change; Turn.agentJob KDoc fixed; server 241, 3 skipped).
- W1(+A2,S6) ✅ `6dc005250` (internal `TurnOutcome` Completed(rejection)/Rejected/Failed in `Turn.outcome`; `TurnOutcomeRecorder` in TurnManager; endTurn(outcome) closes requester view before markFailed for Rejected; `TurnEventProcessor.rejection` removed; server 244, 3 skipped). Known narrow leftover: B2-path interrupted terminal fan-out before reaching the requester view → requester maps Rejected/Failed although task CANCELED (out of scope). Hooks report done: `.agents/research/server-hooks-inventory.md` (H1 failureMessage now, H2 op param/merge into C2 check now; H4/H5/H7/H9 later; side findings: blocking SendMessage result read context-free `A2AServer.kt:726-731`, RequestContext KDoc, TaskStorage.update wording). Consolidated open list presented; WAITING for the user's answers.
- B5 ✅ `bacf7aedd` (`TurnOutcome.RequestError`; `onPublished` flag; rejected/ignored events don't count as first event; server 255, 3 skipped). Follow-ups: JSON-RPC transport test for -32005 on follow-up (optional, T-slice).
- Proposed to the user: discuss open topics (hooks, C2, W1 leftover) in SEPARATE Claude Code sessions via briefs `.agents/briefs/<topic>.md` → decisions `.agents/decisions/<topic>.md`. Waiting for 'write the briefs'.
- W2(+S2,B6) ✅ `cd259aa25` (`startTurn(context, inlinePushConfig = null, block)`, save under gate via `RequestContext.callContext`; orphan cleanup in monitor teardown AFTER the gate, before release (requester may get response just before the delete — invisible via API; user may want it under the gate); agent job DEFAULT start; server 256, 3 skipped). R2 Q1 now MOOT. Follow-up: plan.md row 3.12 text outdated (fix in the plan.md update slice).
- W3 ✅ `4a622a54d` (`withTaskProcessor(taskId, getTask: suspend () -> Task, block = { task, processor, agentJob, publish -> })`, owner-scoped read under the gate; reads: normal 2, forced idle 3, forced running 4, TurnNotActive race 2; server 262, 3 skipped). Flag to user: getTask is a callback under the gate (A1 rejected callbacks); unknown/invisible id briefly holds a gate. Consider making it data (ctx+tenant) in A1.
- HOOKS DECIDED (2026-10-21): H1 `protected open fun failureMessage(task, error, messageId): Message?` NOW (default = generic message; throwing override → default). H2 `checkRequiredExtensions` gets the operation / merged into C2's accepting check (with C2). H6 with C2. H7 YES: `ServerCallContext` builder on the Ktor route (own slice after A1; public transport API change approved in principle). H4, H5, H9 SKIPPED. H8/validateFollowUp etc.: never (document decorator pattern).
- Side findings: (d) FIX blocking SendMessage result read without caller ctx (`A2AServer.kt:~726`) — do in A1 (reads). (f) `TaskStorage.update` "store as is" wording → add to the storage-API open question. (e) INVALID: verified `ContextTaskStorage.get/getAll` are NOT context-scoped (only getByContext/delete are), so referenced tasks ARE reachable; the researcher was wrong. Related note told to user: those reads also skip the owner check (agent reading a client-referenced task id of another owner) → add to the storage-API open question.
- §4.10 (TCK SUT push): decide when implementing Phase 4.
- C2 D1–D8: user will look later. Not yet decided.
- W4 ✅ `4ee28e7cf` (`sseSession` + own iteration, `session.cancel()` in finally; onCompletion hack removed; decode errors now unwrapped IAE/SerializationException; client-jsonrpc-http 19, core-jsonrpc 15; a2a-client 12/12 incl. 8 Docker ITs pass in a CLEAN worktree; in the main checkout the Docker image build fails, likely due to untracked `test-python-a2a-server/.venv` + no `.dockerignore`). Follow-ups: add `.dockerignore` (small slice); decide whether stream decode errors stay raw (left raw).
- DECIDED: W2 orphan cleanup stays AFTER the gate. W3: A1 replaces `withTaskProcessor`'s `getTask` callback with data (`lookup: GetTaskRequest, ctx`; TurnManager does the owner-scoped read + TaskNotFound). PLUS: document concisely the design rule "TurnManager APIs take data, not caller callbacks that run under its locks (hidden lock rules, deadlocks)". Orchestrator's placement choice: a regular block comment at the TOP OF THE CLASS BODY (maintainer-facing; a comment between KDoc and the declaration could detach the KDoc).
- A1 split: A1a = startTurn owns serialization + validation (slot, one owner-scoped read under the gate, checks, contextId mismatch, snapshot from same read; removes tasksMutex/join/check/pre-check) + fix (d) blocking result read with caller ctx. A1b = withTaskProcessor data variant + design comment + S1 + lock-order KDoc (turnMutex). A1c = S4 visibility (@InternalA2AApi on TurnManager/Turn, turnManager protected non-open).
- A1a ✅ `e3d75ad03` (`turnSlots` KeyedMutex held for the whole turn, unlocked in monitor teardown; `startTurn(request, ctx, taskId, defaultContextId, inlinePushConfig, block = { task, processor -> })`; one owner-scoped read under the gate; tasksMutex removed; (d) fixed; server 269, 3 skipped; stress IT 610/610 once). TO CONFIRM with user: (1) inline config validation (URL + validatePushNotificationConfig hook) now runs BEFORE task checks (error precedence + hook called for follow-ups that then fail task checks); (2) defaultContextId generated up front, so a follow-up without message.contextId calls IdGenerator.generateContextId needlessly.
- A1b ✅ `00d4de329` (`withTaskProcessor(lookup: GetTaskRequest, ctx, block)`; design block comment at top of TurnManager body; S1: turn ids removed → `Subscriber.isTurnView` + `check` against a 2nd open turn view in `register`; lock-order KDoc with turnMutex + KeyedMutex leaf; server 271, 3 skipped).
- A1c ✅ `6d0baf407` (class-level @InternalA2AApi on TurnManager/Turn; `turnManager` @InternalA2AApi protected val; server 271). A1 COMPLETE.
- A1a points elaborated to user; recommendations: (1) accept config validation before task checks + document in validatePushNotificationConfig KDoc; (2) TurnManager gets IdGenerator as constructor dependency and computes contextId lazily (`task?.contextId ?: message.contextId ?: generate`), dropping startTurn's defaultContextId — in the cleanup slice. WAITING.
- Docker ITs ✅ `7e56429fe` (Testcontainers 1.21.4 ignores .dockerignore; TestA2AServerContainer now copies only Dockerfile, pyproject.toml, uv.lock, src; a2a-client 12/12 in the main checkout).
- Cleanup ✅ `0dd73627f` (lazy contextId in TurnManager via existing idGenerator dependency; startTurn lost defaultContextId; validatePushNotificationConfig KDoc; S3 → checkNotNull 'Unreachable' in onSendMessage; S7 KDoc; C1 TODO; server 273, 3 skipped). Side effect: generateContextId now runs under the gate, after validation.
- H1 DECIDED: option (b), NON-nullable: `protected open suspend fun failureMessage(taskId: String, contextId: String, error: Throwable): Message` on A2AServer; default returns the generic agent message ("Task failed due to an internal error", messageId via idGenerator). Called by the monitor CANCELLABLY, BEFORE the NonCancellable teardown and before taking the gate (outside locks; throw or cancel incl. shutdown → generic default; teardown always runs), result passed into `endTurn` as data; no timeout; markFailed still decides whether FAILED is written (unused if task missing/terminal). Orchestrator defaults (tell user): an override that throws → log + generic default; server forces taskId/contextId/role=AGENT on the returned message.
- H7 DECIDED: `customizeCallContext: suspend ApplicationCall.(ServerCallContext) -> ServerCallContext = { it }` on `a2aJsonRpcTransportRoute` (and the standalone `HttpJSONRPCServerTransport`). `ServerCallContext.copy` ALREADY EXISTS (a2a-core; re-derives requestedVersion/Extensions when headers change) — orchestrator was wrong that it's missing. SUSPEND (user decided). Run after H1.
- Next after H1: S5 appendHistory, H7 Ktor ServerCallContext builder, plan.md update (incl. row 3.12), tests T1–T4 (+ -32005 transport test, A4 disconnect test). C2 waits for the user.
- S5 ✅ `80a614980` (`TaskStorage.appendHistory(taskId, message)` default get+update, InMemory override; internal `Task.withAppendedHistory`; update KDoc no longer 'server relies on store as is'; server 277, 3 skipped).
- Slice order: B4 → B3 → B1 → B2 → W5 → W1 → B5 → W2 → W3 → W4 → A1; then S5, C2 etc. once decided.
- **Open questions recorded (future):**
  - Storage API architecture: revisit all storage APIs. Keep both styles (RPC-request DTO + ServerCallContext methods vs. plain internal methods) or streamline them to one pattern?
  - D-5 back-pressure: switch to non-blocking subscriber streams with a bounded buffer and force-fail slow subscribers?
- Docker IS available now (OrbStack): run the a2a-client Docker integration tests where relevant (W4).
- No slices started yet.

### Brainstorm agenda
Item ids are the ones in `phase3-code-quality-review.md`. The report ends with a ranked top 10.

**A. Bugs with an obvious fix, no decision needed** (the orchestrator suggested doing these first):
- **B1:** an agent-thrown `CancellationException` (e.g. `withTimeout`) is treated as an outside cancellation. The task stays WORKING forever, subscribers never close, and the error is swallowed. Fix: inside the agent job, a CE thrown while the job is still active is a failure, so the task becomes FAILED.
- **B2:** a publisher cancelled mid-fan-out of a terminal event skips `closeAll()`, so the other subscribers stay open forever. Minimal fix: `finally { if terminal closeAll() }`. A stronger variant closes every subscriber the fan-out didn't reach; that needs a small decision.
- **B3:** with a non-supervisor `coroutineScope`, one agent crash cancels the whole server. Fix: an internal `SupervisorJob` in `TurnManager` and the dispatcher.
- **B4 (older bug):** `InMemoryMessageStorage.deleteByContext` changes its map under the read lock. Fix: use the write lock.

**B. Workarounds** (root fixes are sketched in the report):
- **W1:** the requester guesses a rejected event from the agent job's state. That produces -32006 instead of CANCELED in a reject-vs-cancel race. Root fix: a typed in-band `TurnOutcome`, which also moves `rejection` out of `TurnEventProcessor` (A2). No decision needed.
- **W2:** the inline push config is saved before `startTurn`'s terminal re-check. This forces three undo paths, an `isNew` race with a concurrent Create, and a shutdown leak (B6: the LAZY agent job never runs). Root fix: save it under the gate inside `startTurn` (storage I/O under the gate, which is a small decision), and clean up orphans in the monitor teardown.
- **W3:** CancelTask makes up to 6 storage reads and catches `TurnNotActiveException`. Root fix: the stream remembers "terminal", which revisits the skipped B4 "terminal is final" (needs a decision). A cheap partial fix is to merge the reads.
- **W4:** the client runs downstream collection inside Ktor's `sse {}`, so user exceptions come back wrapped in `SSEClientException`. There is also the `onCompletion` unwrap hack. Root fix: `sseSession` / iterate outside the block. No decision needed.
- **W5:** `TurnEventProcessor.close()` and `closeImmediately()`, plus test-only state (`isOpen`, `isTaskEventSent`). Direction: one non-suspending close.

**C. Needs a user decision:**
- **B5:** an A2A exception thrown by the agent on a follow-up marks the task FAILED for good, and the client never sees the error code. The `AgentExecutor` KDoc invites throwing these. Options: (a) keep and fix the KDoc; (b) don't mark FAILED for an `A2AException` thrown before the turn's first event, and rethrow it to the requester.
- **A1:** move turn serialization (task lock, join, pre-check) from `A2AServer` into `TurnManager.startTurn`. It would retire `tasksMutex`/TL, the duplicated terminal check and W2's undo paths. Big change: `startTurn` semantics, FIFO fairness, a new deadlock analysis. This is the previously deferred simplification-review B3. Discuss it together with W2, W3 and S4.
- **S4:** `TurnManager` and `Turn` are half public (public class and constructor, `@InternalA2AApi` methods, `protected open val turnManager`). Make them internal or `@InternalA2AApi` as a whole, and make `turnManager` non-open?
- **S5:** persisting a follow-up user message reads the full task and writes it back with `update(Task)`. Add a dedicated storage op (`appendHistory`), which is a storage API change?
- **C2:** after `A2AServer.cancel()`, RPCs return misleading results. Add a shutdown flag with an explicit error, and which error?
- **C1:** the non-cancellable FAILED fan-out in monitor teardown can block shutdown behind a stalled requester view. This is accepted under D-5. Act only if shutdown latency matters (e.g. cancel requester views in `shutdown()`).

**D. Smells, no decision needed:**
- **S1:** `nextTurnId` and `Subscriber.turnId` could be a boolean.
- **S2:** the agent job uses `LAZY` start; DEFAULT would do and would remove B6.
- **S3:** the unreachable "event stream is empty" ISE.
- **S6:** `emitTurnEvents` branching, which shrinks with W1.
- **S7:** `InMemoryTaskStorage` pagination-stability KDoc is wrong.
- **Lock-order KDoc:** add `turnMutex` (between the gate and [S]).
- **A4:** the transport's F1 guard relies on Ktor cancelling the call on disconnect, which is untested.

**E. Tests** (do last, once the code is stable):
- **T1:** fixtures copy-pasted across about 8 files (200–300 lines).
- **T2:** back-pressure, FAILED handling and push are tested at three layers.
- **T3:** missing tests for B1–B3, the W1 race, the W2 Create race, an SSE transport disconnect, and requests after shutdown.
- **T4:** tests assert `isOpen` and the leak counters.

**Older open questions, still open:**
- **R2 Q1:** a follow-up's newly saved inline config is deleted on ANY `startTurn` failure, not only terminal rejection. The user hasn't confirmed this. It becomes moot if W2's root fix is adopted.
- **plan.md §4.10 / Phase 4:** should the TCK SUT declare `pushNotifications`? If yes, it needs a `pushSender`. The SUT also doesn't compile against the current API.

**Known unrelated breakage:**
- `agents-features-a2a-*` don't compile (Phase 5).
- The TCK SUT doesn't compile (Phase 4).
- `jsBrowserTest` is broken everywhere.
- (fixed) Docker integration tests run in the main checkout now.

## History (older entries; superseded by RESUME HERE where they conflict)
- **2026-10-16: code-quality review done** → `.agents/research/phase3-code-quality-review.md` (bugs B1–B6, workarounds W1–W5, smells S1–S7, architecture A1–A4, tests T1–T4, top-10). Presented to the user for brainstorming; NO fixes started. Decisions needed: B5, W2 (storage I/O under gate), W3 stream-terminal variant, S4, S5, A1, C2.
- **2026-10-16: PHASE 3 COMPLETE.** Last commits: `f7c3019b3` (`TurnManager.shutdown` @InternalA2AApi), `65b063414` (plan.md updated: 3.1–3.20 ✅, 3.21 ⏭, §4 items 2/5/6/7/8 decided; §4.10 TCK SUT push still open). Server 229 tests (3 skipped). Nothing pushed. Waiting for the user's next goal. Pending confirmation: R2 Q1 deletes a follow-up's new inline config on ANY startTurn failure (not only terminal rejection).
- **2026-10-16: M8e-3 ✅ `17abd548a` (inline config; server 217 = 214 pass + 3 skipped). M8e complete.** User CONFIRMED (keep all) the programmer's choices: cleanup only if this turn saved an inline config; tenant overwritten with request.tenant even if null; cleanup also if startTurn throws after save; hook gets resolved config (ids filled); hook runs under TL. M8e-4 ✅ `edc575012` (blank `url` → InvalidParams on Create + inline; server 219 incl. 3 skipped). Final review ✅ `.agents/research/phase3-final-review.md` (3.1–3.20 done, 3.21 skipped; has plan.md status table). R1 ✅ `f6f12ada4` (F1 hardening — review's root cause was WRONG, no real leak in coroutines 1.10.2; guard added anyway), `991ddb372` (F2 cancel race → TaskNotCancelable; F6 persist+onPersisted under NonCancellable), `73543b896` (docs), `9c593cb48` (standalone cancel processor taskExists=true; dead SubscribeToTask branch removed). Server 224 incl. 3 skipped, core-jsonrpc 15, jsonrpc-http 25.
User decisions (FINAL) for R2: F3 → new-task turn with no events and no failure → -32006 for blocking AND streaming. F4 → KEEP transport's "Internal error: ${e.message}" as is. F5 → push requires storage AND sender; without sender, config RPCs → -32003 and inline ignored. Open pt 1 → if a follow-up is rejected by startTurn's terminal re-check, delete the inline config it just saved. Open pt 2 → `A2AServer.cancel()` (shutdown) closes all task streams so SubscribeToTask subscribers end. R2 ✅ `726c8fbd6` (F3), `f5f6a2764` (F5), `2f1163a87` (Q1: delete on ANY startTurn failure of a follow-up, only if config id didn't exist before; replaced config kept), `1e91856db` (Q2: lock-free `TurnManager.shutdown()` Job; subscriber flows complete normally). Server 229 incl. 3 skipped. Notes: TCK SUT `Main.kt` will need a `pushSender` (Phase 4); `TurnManager.shutdown()` is new public API → make `@InternalA2AApi` (B5 consistency) in the plan.md slice. Next: plan.md update slice (final step of Phase 3). Then: present review findings, fix slices, `plan.md` update. (Old text:) Next: M8e-3 (inline config per Q2/Q3/Q9/Q10 + orphan cleanup + capability-off ignore; see m8e-push-design.md §5 and slice M8e-3), then final review + `plan.md` update. Test counts at HEAD: server 205 (+3 skipped), jsonrpc 25, core 63, client 12 (8 Docker-skipped).
- All Phase 3 behavior slices are done except **M8e** (push timing + inline push config), which is on hold.
- We are in a **cleanup round** requested by the user (table below). C1 and C2 are done. User answered the C2 B-items (see "C3 decisions"). **Next step: C3 (programmer, opus) is running / to run.**
- Then C5 → C4 → C6, then M8e, then final review + `plan.md` update.

## Cleanup round (user request; current focus)
Order: C1 ∥ C2 (done) → C3a → C3b → M7fix → C5 → C4 → C6 → M8e.

| # | Item | Agent | Status |
|---|------|-------|--------|
| C1 | Fix cancellation-safety bugs in `agents/agents-utils` `RWLock`/`KeyedMutex` (no API/behavior change) | a2a-programmer (opus) | ✅ `09e3da335`. 4 real bugs fixed (NonCancellable release/rollback; RWLock counts reader only after writeMutex acquired). Tests fail-before/pass-after (`RWLockTest`, new `KeyedMutexTest`, new jvm `LockCancellationJvmTest` using reflection on private mutex fields). **Cancel-race hang gone**: old 6/30 hangs, fixed 0/50. |
| C2 | Simplification review → `.agents/research/simplification-review.md` | a2a-researcher (opus) | ✅ (see "C2 results" below) |
| C3 | Remove workarounds + safe simplifications per C2 | a2a-programmer (opus recommended) | **pending — needs user answers on B-items first** |
| C5 | Rename all `Session*` symbols/classes/files → `Turn*` (incl. comments/KDoc; e.g. Session→Turn, SessionManager→TurnManager?, SessionEventProcessor→Turn…). Exact new names: propose to user before running if non-obvious | a2a-programmer | pending |
| C4 | Add a concise-comments guideline to `a2a/AGENTS.md` (`a2a/CLAUDE.md` symlinks to it; checked: no such note exists today), then pass over code changed this session: shorten comments that are long, keep those explaining genuinely complex behavior | a2a-programmer | pending |
| C6 | Review tests added this session (list in `session-manager-concurrency.md` §7): shorten / merge / make less verbose where sensible; may change nothing | a2a-programmer | pending |

### C3 decisions (FINAL, user answered 2026-10-15)
- B1: YES — gate-based view-first `startTurn` as in the review (terminal snapshot → `A2AUnsupportedOperationException`).
- B2: REMOVE `cancelKey()` entirely (and its usage in `onCancelTask`). `tasksMutex` stays (still the TL for send).
- B3: defer (not asked; review recommendation). B4: skip (review recommendation (a)).
- B5: mark new public `SessionManager` methods (`startTurn`, `subscribe`, `withTaskProcessor`) `@InternalA2AApi`.
- B6/B7: YES, both in M8e (user, 2026-10-15): forced CANCELED published directly to stream (delete `sendServerTaskEvent` + `persistFollowUpMessage` plumbing); remove `Turn.completion`, `join()` waits for monitor job.
- M8e design report done: `.agents/research/m8e-push-design.md`. User answers (FINAL, 2026-10-15):
  - Q1: push server-originated events too (forced CANCELED, crash FAILED) — every persisted TaskEvent.
  - Q2: inline config WITHOUT id → id = taskId (Python upsert behavior; not via IdGenerator). Create RPC unchanged (D5). (If unclear whether IdGenerator should still be consulted, ask.)
  - Q3: overwrite client-set inline `taskId` with the turn's task id.
  - Q4: read configs at dispatch time. Q5: concurrent fan-out per event. Q6: unbounded per-task queue. Q7: no retries in dispatcher, keep 15s timeout, drop queued pushes on shutdown. Dispatcher internal.
  - Q9: orphan cleanup in A2AServer (non-cancellable finally around execute()).
  - Q8: option 1 — `withTaskProcessor` block gets a `publish: suspend (TaskEvent) -> Unit` bound to the pinned stream (may group processor/agentJob/publish into a small scope object); cancel policy stays in A2AServer.
  - Q10: `protected open suspend fun validatePushNotificationConfig(config, ctx)` on A2AServer, no-op default, called in Create and for inline config before save; overrides throw (e.g. InvalidParams).
- M8e slices: M8e-1 ✅ `3fa858d5a` (internal `notifications/PushNotificationDispatcher`; hook `TaskEventStream.persist` → `onPersisted` under [S]; server 199 green); M8e-2 ✅ `7d3317337` (B6: `withTaskProcessor` block is `{ processor, agentJob, publish -> }` (plain 3-param lambda), `publish` guarded by taskId `require` + only-inside-block `check`; `sendServerTaskEvent`/`persistFollowUpMessage` removed; forced CANCELED no longer closes running turn's processor (monitor closes it). B7: `Turn.completion` removed, private `monitor: Job`, `join()` = `monitor.join()`. Server 205 green +3 skipped, jsonrpc 25; cancel tests 15/15 runs); M8e-3 (inline config Q2/Q3/Q9/Q10 + orphan cleanup).
- M8e plan: researcher writes `.agents/research/m8e-push-design.md` (design against current code + decision questions) → ask user → programmer(s) (opus).
- New issue 1: FIX shutdown `join()` hang (monitorTurn body never runs if scope already cancelled).
- New issue 2: FIX — push-config RPCs check task existence via owner-scoped `get(GetTaskRequest, ctx)`.
- All A-items (A1–A10; A11 optional) go into C3.
- C3 split: **C3a** = B1+B2+B5 ✅ `5002472b3` (216 tests green; race test 3,500 attempts, 0 hangs; extra: startTurn `catch` unsubscribes view if cancelled during NonCancellable commit; rejected view closed via lock-free `unsubscribe`; A2AServer pre-check for terminal kept); **C3b** ✅ `351db7ba6` (A-items; A8 + A11 skipped: extra storage read under stream lock / C6 risk) + `efc227515` (shutdown hang via `CoroutineStart.ATOMIC`; push-config RPCs owner-scoped). 220 tests green. New: `SessionEventProcessor.closeImmediately()`, `tasks/TaskEvents.kt` (`Event.isTerminal`), `RequestContext.withTask` removed.
- C6 ✅ `4f2df25ad` (core+client), `3133a5988` (server), `a67b56aac` (jsonrpc). Counts now: core 63, server 188 (+3 skipped), client 12 (8 Docker-skipped), jsonrpc 25. New fixtures: `a2a-server/src/commonTest/.../server/A2AServerTestFixtures.kt`, `.../server/turn/TurnTestFixtures.kt`. **Cleanup round complete → next M8e.**
- C4 ✅ `9564f7d88` (guideline in a2a/AGENTS.md) + `b08dbcd4d` (comments only, −160 net lines). Pre-existing comments in untouched files left alone.
- C5 ✅ `b53ac9b7d` (package `ai.koog.a2a.server.turn`; `getSession`→`getTurn`, `activeSessions`→`activeTurns`, `sessionManager`→`turnManager`; server 221 / jsonrpc 27 green). Research reports under `.agents/research/` still use OLD names.
- C5 names (FINAL): straight swap — Session→Turn, SessionManager→TurnManager, SessionEventProcessor→TurnEventProcessor, SessionNotActiveException→TurnNotActiveException, package/dir `session/`→`turn/`, tests renamed to match.

### C2 results (`.agents/research/simplification-review.md`)
- **B1 (needs user decision, recommended):** `startTurn` takes the task's existing `processorGate` and registers the turn view FIRST under it; fails with UnsupportedOperation if the stored task is already terminal. Serializes follow-up start vs cancel per task. Removes: cancel `tryLock`+5ms polling, `hasTaskLock`, nullable `cancelWithProcessor`, `Turn.registered` + `findRegisteredSession` loop, `turnView`/`taskSnapshot` deferreds, most of startTurn undo path, effectively `cancelKey`. RPC-visible: nothing new (both race outcomes already possible). `SessionManager` API: `getSession`/`activeSessions` no longer report a turn still registering (2 SessionManagerTest tests change).
- §6 of concurrency report re-evaluated: view-first startTurn folded into B1; "terminal is final in publish" NOT recommended alone (spurious -32006 risk); turn queue in SessionManager → defer.
- **A-items (behavior-preserving, can go into C3 without asking):** single non-cancellable teardown in `monitorTurn` (removes `closeTurnView` + duplicate turn removal); read full task once in follow-up validation (drop `RequestContext.withTask` + second read); close the standalone cancel processor after use; one `isTerminal` helper instead of 3 copies; fix misplaced/garbled KDoc (`awaitAgent` KDoc on `failedWithRejectedEvent`; `includeTaskSnapshot` KDoc); merge small duplicates (`awaitAgent` branches, `sendTaskEvent`/`sendServerTaskEvent`, two `openTurnView` overloads, request-context building in send & cancel).
- **Other B-items to ask:** stop using `cancelKey` after B1 and deprecate it; mark new public `SessionManager` methods `@InternalA2AApi`; (M8e) send forced CANCELED without the processor (delete `sendServerTaskEvent`), replace `Session.completion` with the monitor job.
- **New issues found (ask/plan):** (1) shutdown: `monitorTurn` launched with default start — if server scope already cancelled, body never runs → `join()` hangs; (2) push-config RPCs check task existence via the context-free lookup, while subscribe/cancel/send use the owner-scoped `get(GetTaskRequest, ctx)` (inconsistent with D3 derivation).

## Milestones (Phase 3)
| # | Milestone | Status |
|---|-----------|--------|
| M1 | 3a: a2a-server main compiles on v1 core | ✅ `887b947e6` |
| M3.5 | a2a-server tests compile + pass (3.15 part 1) | ✅ `957c7cd6b` |
| M4a | TaskStorage DTO list/get + GetTask/ListTasks (3.8, 3.9) | ✅ `1043a4f27` (`Task.project` helper; internal get/getAll/getByContext default to full task; page token = base64url(ts\|id)) |
| M4b | Push config storage DTOs + sender `send(config, event)` + push CRUD (3.11) | ✅ `1081e7482` (+ `970580f74` ktlint whitespace in a2a-core). Sender Json explicitNulls=false; 15s timeout; X-A2A-Notification-Token kept |
| M4c | Push-config id via `IdGenerator.generatePushNotificationConfigId(config)` (default UUID body) | ✅ `f4250ca34` |
| M8a | Per-task `TaskEventStream` in SessionManager; v1 SubscribeToTask (3.5); FAILED on crash | ✅ `946cd5ff0` |
| M8a2 | Back-pressure (rendezvous channels) instead of eviction; generic FAILED message; `IdGenerator.generateMessageId` | ✅ `b6dc6eab7` |
| M8a3 | Registry lock never held while taking a stream lock (cross-task stall fix) | ✅ `c2f9845d8` |
| M8b | Send semantics 3.1/3.2/3.3/3.4 | ✅ `897095aa9` |
| M8c | History persistence (3.14), full RequestContext.task, ignore Task replacement | ✅ `43ef39c7c` |
| M8c2 | Lazy follow-up persistence (literal D-3), ids filled on stored user msg, invalid agent events → -32006 | ✅ `5364d68cb` |
| M8d | CancelTask 3.10 with server-forced CANCELED (no status message) | ✅ `f57327f6b` |
| M7 | 3.7 A2A-Version (-32009) + required extensions (-32008) | ✅ `9c69f8e59` (Docker client ITs not run) |
| M8f | Capability gating 3.6 (already implemented; tests + KDoc) | ✅ `63f18225e` |
| C1 | RWLock/KeyedMutex cancellation safety (agents-utils) | ✅ `09e3da335` |
| **M8e** | Push on every persisted TaskEvent via per-task FIFO dispatcher (3.13), inline `SendMessageConfiguration.taskPushNotificationConfig` (3.12), orphan inline-config cleanup | **on hold until cleanup round done** |
| Final | Code review pass; update `plan.md` (3.x rows, §4 decisions) | pending |

Test status at HEAD: `:a2a:a2a-server:jvmTest` 214 tests green; agents-utils 22 jvm tests green.

## Research reports (`.agents/research/`)
- `server-storage-api-proposal.md` — storage DTO design (decisions D1–D7).
- `server-session-redesign-proposal.md` — session redesign options; Option A chosen; D-1..D-6.
- `server-version-extensions-proposal.md` — 3.7.
- `session-manager-concurrency.md` — explainer of current session layer: what a turn is and why, every entity/lock with "why" + pre-v1 equivalent, lock order diagram, flows, rough spots, §7 table of ~100 tests added this session.
- `simplification-review.md` — C2.
- Older: `koog-*-status.md`, `proto-drift.md`, `migration-guide-and-changelog-audit.md`.

## Decisions recorded (FINAL — do not re-ask)
Storage:
- D1: `TaskStorage.list(ListTasksRequest, ctx): ListTasksResponse`.
- D2: storage applies historyLength/includeArtifacts (0 → history=null); public projection helper `Task.project`. A2AServer validates pageSize 1..100 and historyLength>=0 → InvalidParams; storage default pageSize 50.
- D3: `ServerCallContext` on every RPC-mirroring storage method (TaskStorage `get(GetTaskRequest, ctx)` + `list`; push save/get/list/delete). Internal methods stay context-free. Two `get`s on TaskStorage for now (user confirmed).
- D4: keep non-DTO TaskStorage methods; internal `get` defaults to full task.
- D5: server assigns push-config id when absent (via IdGenerator, user decision); storage requires non-null id.
- D6: push storage takes DTOs for all 4 RPCs + internal `getAll(taskId)`/`deleteAll(taskId)`; missing config → TaskNotFound; delete idempotent; task existence checked in A2AServer; terminal tasks not rejected.
- D7: `PushNotificationSender.send(config, event: Event)`; server fans out per config. Keep `X-A2A-Notification-Token` header (Python interop).
- A2AServer always fills contextId for tasks it creates.
Version/extensions (3.7):
- Missing A2A-Version → 0.3 → -32009; supported versions = transport constructor param, default {"1.0"}; check in `JSONRPCServerTransport.handleRequest`; helper in a2a-core.
- `ServerCallContext.requestedVersion` / `requestedExtensions` (case-insensitive headers).
- `A2AServer.checkRequiredExtensions(ctx)` hook on every op (-32008); unknown extensions ignored. No echo header.
Session layer:
- Option A: turn-scoped `Session` + internal per-task `TaskEventStream` in `SessionManager`.
- D-1: blocking SendMessage returns at first terminal/interrupted state (or end of turn); agent continues in background. `returnImmediately` → after first task event; Message reply always returned.
- D-2: SendStreamingMessage closes at end of turn or terminal; SubscribeToTask only at terminal.
- D-4: follow-up stream starts with Task snapshot.
- D-5 (REVISED): NO eviction. Back-pressure: fan-out under the per-task stream lock waits for every listener (requester view, blocking collector, subscribers). Rationale: spec Q9 only forbids *closing* one stream affecting others, not slowness; pre-v1 also back-pressured. Accepted cost: subscribe/cancel can wait behind a slow reader of the same task (never across tasks — M8a3).
- 3.13: push on every persisted TaskEvent as StreamResponse, per-task FIFO dispatcher; no push for Message replies. (M8e)
- 3.14 (as revised after M8c): server persists user messages into history, dedup by messageId. New task: appended to the agent's initial Task. Follow-up: persisted lazily right before the agent's first non-Task TaskEvent (not at turn start); if the agent emits none, not persisted. `RequestContext.task` = stored task at turn start (no current message). Stored user message gets taskId/contextId filled. Existing subscribers are not notified of it.
- Agent Task event for an existing task → ignored.
- Invalid agent events → requester gets -32006 `A2AInvalidAgentResponseException`.
- Executor crash → FAILED with generic agent message "Task failed due to an internal error" (no exception details).
- 3.10 cancel: unknown → TaskNotFound; terminal → TaskNotCancelable; executor cancel that doesn't reach terminal → server forces CANCELED (no status message); requester of the cancelled turn gets the CANCELED task.
- Orphan inline push config (turn answered with Message) → `deleteAll(pre-generated taskId)` at end of turn. (M8e)
- Owner-scoped lookups: subscribe/cancel/send use `taskStorage.get(GetTaskRequest, ctx)`.

## Open questions for the user (not yet asked)
1. (answered — see C3 decisions)
2. (answered — see C3 decisions)
3. (answered — C5 straight swap)
4–5: DONE in M7fix `2aa739056` (server 221 green, client 13 run/8 skipped Docker).
4. ANSWERED: required-extensions check must be SKIPPED for GetExtendedAgentCard (todo: small slice "M7fix", run after C3b).
5. ANSWERED: fix `A2AClient.prepareContext` A2A-Version header check to be case-insensitive (todo: same "M7fix" slice).
6. ANSWERED: no validation of `AgentInterface.protocolVersion` vs `supportedVersions`.
7. ANSWERED (M8e): inline push config when push capability is off → silently ignore (Python behavior).
8. Phase 4: does the TCK SUT declare pushNotifications (4.2)?

## Known broken / environment notes
- `jsBrowserTest` (karma/webpack toolchain) fails everywhere, pre-existing. Use `../gradlew :a2a:<module>:build -x jsBrowserTest -x wasmJsBrowserTest -x wasmJsNodeTest`.
- Pre-commit ktlint hook may restore stale cached results: use `--no-build-cache --rerun-tasks`.
- `agents-features-a2a-*` don't compile (Phase 5: `MessageConverters.kt` uses removed `FilePart`/`FileWithBytes`/`FileWithUri`, non-exhaustive Role `when`). TCK SUT `test-tck/a2a-test-server-tck` doesn't compile (Phase 4).
- a2a-client integration tests need Docker (not available in past runs).
- Subagents must NEVER stage/commit anything under `a2a/.agents/` (`.agents/prompt.md` and `state.md` are untracked/orchestrator files; `a2a-programmer.md` has an uncommitted user edit).
- `StressA2AServerJsonRpcIntegrationTest` (3 tests) is @Disabled, pre-existing.
