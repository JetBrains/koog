# Where can the code changed in this session be simplified without changing required behavior or recorded decisions?

**Sources checked:** Koog `eugenethedev/a2a-1.0` @ `63f18225e` (HEAD, clean under `a2a/` except `.agents/`), range `887b947e6..HEAD`. Decisions: `a2a/.agents/state.md`. Prior analysis: `a2a/.agents/research/session-manager-concurrency.md` (written at `5364d68cb`; its line numbers are stale, the ones below are current). No upstream spec/SDK/TCK lookup was needed: this is a code-structure question, and every proposal keeps the RPC-level behavior.
**Confidence:** high for the inventory and the A items. Medium-high for B1 (lock reasoning done by hand, not implemented or run). The `RWLock`/`KeyedMutex` fixes are assumed to land, as instructed.

Paths are relative to `a2a/a2a-server/src/commonMain/kotlin/ai/koog/a2a/server/` unless stated. SM = `session/SessionManager.kt`, TES = `session/TaskEventStream.kt`, SEP = `session/SessionEventProcessor.kt`.

## Answer

The biggest simplification is one structural change, **B1**. It linearizes turn start and cancel on the existing per-task `processorGate`. `startTurn` registers the view first under the gate and fails on a terminal snapshot.

B1 removes every cancel/start workaround in one go:
- the `tryLock` + 5 ms polling loop;
- `hasTaskLock` and the nullable `cancelWithProcessor`;
- `Turn.registered` and the `findRegisteredSession` loop;
- the `turnView`/`taskSnapshot` deferreds;
- most of the `startTurn` undo path;
- in practice, the `cancelKey` lock.

At the RPC level it adds no new outcome: the cancel-vs-follow-up race can already end either way today. It does change one thing visible through `SessionManager`'s public (unreleased) API, and it is a lock-design change, so it needs a user decision.

How the §6 candidates fare:
- **§6(3) view-first:** correct, but only safe together with the gate. It is folded into B1.
- **§6(2) "terminal is final" in `publish`:** on its own it does **not** remove the polling cleanly. After B1 it is only defensive.
- **§6(1) turn queue in `SessionManager`:** loses its motivation once B1 lands. Recommend deferring it.
- **§6 "close the standalone processor":** correct and cheap (A3).

The remaining A items are local cleanups:
- teardown merge in `monitorTurn`;
- a single read for `RequestContext.task`;
- three copies of `isTerminal`;
- KDoc errors;
- small duplications.

## Requirements

Not a protocol question. Every item below must preserve the following, all of which apply to Koog's JSON-RPC binding:

| # | Constraint to preserve | Tier | Bindings | Citation |
|---|---|---|---|---|
| C1 | All streams of a task see the same events in the same order; subscribe snapshot is atomic with registration | MUST | all | session-manager-concurrency.md R2/R3 |
| C2 | Cancel on an idle task forces CANCELED; terminal → TaskNotCancelable; unknown → TaskNotFound | MUST + user decision 3.10 | all | state.md "3.10" |
| C3 | The requester of a cancelled running turn sees CANCELED (gate) | pre-v1 behavior, tested | all | `testCancelRunningTaskEndsRequesterWithCanceledTaskWhenExecutorCancels` |
| C4 | Registry lock never waits on a stream lock (no cross-task stall) | user-approved fix M8a3 | — | `c2f9845d8` |
| C5 | Back-pressure, no eviction | user decision D-5 (revised) | all | state.md |
| C6 | Literal D-3 lazy follow-up persistence; forced CANCELED doesn't trigger it | user decision | all | state.md; SEP:165-176 |
| C7 | Rejected agent event → -32006 even when the task is marked FAILED or the agent swallowed it | user decision | all | A2AServer.kt:509-548 |

## Details

### §6 candidates, verified against HEAD

| §6 item | Correct? | What it removes | Observable change | Verdict |
|---|---|---|---|---|
| (1) Turn queue in SM | Workable, but large. `startTurn` would suspend until the previous turn ends and run validation in a callback under a per-task slot. FIFO fairness of the follow-ups queued today on `KeyedMutex` (a fair `Mutex`) must be rebuilt, and the wait must not hold the gate (`monitorTurn` needs it). | `tasksMutex.withLock(taskId)` + `getSession()?.join()` in A2AServer.kt:452-454; `check(taskId !in turns)` SM:192 | `startTurn` semantics and the role of `getSession`/`join` change (public SM API); `protected tasksMutex` loses its meaning for subclasses | **Defer (B3).** Its main payoff, removing the cancel polling, is delivered more cheaply by B1. |
| (2) "Terminal is final" in `TaskEventStream.publish` | It enforces the invariant, but **alone it is not a clean fix for the polling**. Take a follow-up F whose view registered just before an idle cancel's CANCELED: F's agent still starts, does side effects after CANCELED, and its first send fails with `TaskOperationException`. That failure is logged as "Agent execution failed". If the agent job completes before F's requester consumes the CANCELED event, `failedWithRejectedEvent()` (A2AServer.kt:558-561) turns F's response into -32006. It also costs one extra storage read per published event under `[S]`. | The polling (only together with dropping the TL from cancel) | Yes, in that race: a spurious -32006 or agent side effects after CANCELED | **Don't adopt as the polling fix.** After B1, optional defense only (B4). |
| (3) View-first `startTurn` | Correct. On its own it only moves the "cancel waits for registration" wait. Combined with taking the gate (B1) it also makes check + register + insert atomic. | `Turn`, `registered`, `findRegisteredSession` loop, `turnView`/`taskSnapshot` deferreds, most of the undo path | `getSession`/`activeSessions` stop reporting a turn whose view is still being registered | **Fold into B1.** |
| "Close the standalone processor" (§5.6) | Correct. | A latent R3 break: a leaked processor could publish into a dropped stream instance | Only for user `AgentExecutor.cancel` code that leaks the processor: it now gets `SessionNotActiveException` | **Do it (A3).** |
| (4) `RWLock`/`KeyedMutex` | Out of scope (being fixed). | — | — | B1 also removes cancel's `tryLock`/`unlock(taskId)`, which reduces exposure to `KeyedMutex`. |

### Workarounds and their root fixes

| # | Workaround (location) | Exists to compensate for | Root fix |
|---|---|---|---|
| W1 | Cancel `tryLock` + `delay(5)` loop; `lockAcquired` re-try; `CANCEL_LOCK_POLL_MILLIS` (A2AServer.kt:345, 731-765) | Cancel's "idle → force CANCELED" decision isn't linearized with a follow-up that has validated but not yet registered its turn. Cancel can't wait on TL (deadlock via `join`). | B1: both decisions are made under the gate, and `startTurn` re-checks terminal under `[S]`. |
| W2 | `hasTaskLock` param, `null` return, `checkNotNull(canceled)` (A2AServer.kt:759, 772-785) | W1 | B1 |
| W3 | `Turn` + `Turn.registered`; `findRegisteredSession` loop (SM:117-126, 427-439) | The turn is published in `turns` before its view exists (two-step `startTurn`, itself forced by C4) | B1 (view-first under the gate) |
| W4 | `turnView`/`taskSnapshot` `CompletableDeferred`s; `events = flow { emitAll(turnView.await()) }` (SM:180-181, 213-215, 232-233, 244-245; Session.kt:28,35) | Same as W3: the `Session` is built before the view | B1: build the `Session` after `openTurnView`, with plain `events: Flow<Event>` and `taskSnapshot: Task?` |
| W5 | Undo path in `startTurn` (SM:235-251) | Reservation made before registration | B1: nothing is visible until registered; undo = `closeTurnView` + `release` |
| W6 | `cancelKey` lock in `onCancelTask` (A2AServer.kt:741) | Pre-v1 used it to serialize cancels and delay teardown. Teardown moved to the gate in `946cd5ff0`. | With B1 the gate already serializes cancels (both pin the same stream instance, refcount invariant SM:445-452). Remove the usage (B2). |
| W7 | `sendServerTaskEvent` + `persistFollowUpMessage` flag (SEP:165-178, 187) | The forced CANCELED travels through the agent-facing processor, whose first non-Task event triggers lazy D-3 persistence | After M8e (when `isTaskEventSent` no longer drives push): publish server events to the stream directly (B6). Until then it is the minimal solution. |
| W8 | Duplicate turn removal and `closeTurnView` in `monitorTurn`'s `finally` (SM:281-299; TES:264-269) | Teardown split between `try` (under the gate) and `finally` (for failure/cancellation) | A1: one NonCancellable teardown |
| W9 | `RequestContext.withTask` + re-read of the full task inside the agent job (A2AServer.kt:493-500; RequestContext.kt:34-45) | The validation lookup reads with `historyLength = 0` | A2: read the full task once at validation |
| — | Not workarounds (keep): `processorGate` (C3); `rejection` + `failedWithRejectedEvent` (C7); lock-free `unsubscribe` + `removeAll { isClosedForSend }` in `register` (C5); `check(taskId !in turns)` (caller-contract guard, atomic under B1); `release`'s `entry.stream === stream` (cheap defense); storage `pageSize < 1` check (InMemoryTaskStorage.kt:48; without it `page.last()` throws on an empty page) | | |

### (A) Safe, behavior-preserving simplifications, by value

| # | Location | Change | Removes | Behavior impact | Risk |
|---|---|---|---|---|---|
| A1 | SM:263-305, TES:264-269 | Teardown runs once in `finally { withContext(NonCancellable) { gate.withLock { processor.close(); try { stream.endTurn(turnId, contextId, failure) } finally { registry { turns -= taskId } } }; release(stream) } ; completion.complete() }`. `endTurn` already closes the view in its own `finally` (TES:259-261). Keep the turn removal **inside** the gate: if it ran after the gate is released, a cancel could get a closed processor. | `closeTurnView` (TES), the second guarded `turns -=` (SM:294-296), the `catch (e: Exception)` log branch (fold into the `finally`) | None. Teardown now also runs, waiting non-cancellably for the gate, if the server scope is cancelled mid-turn (shutdown only). | Low |
| A2 | A2AServer.kt:457-459, 493-500; RequestContext.kt:34-45 | Validation lookup without `historyLength = 0`: `GetTaskRequest(id, tenant)` returns the full history and always the artifacts (TaskStorage.kt:21-24). Pass `requestContext` (which then holds the full task) straight to `execute`. | Second storage read per follow-up, the `fullTask ?: task` fallback, `RequestContext.withTask` | None in practice. "Stored task at turn start" is read under TL right before `startTurn`. Only a concurrent idle cancel could change it in between, and under B1 that turn then fails anyway. Reads the full history once (it was read anyway). | Low |
| A3 | SM:381-404 | `finally { if (session == null) processor.close() }` for the standalone processor. Prefer a non-suspending internal close (`_isOpen.store(false)`): `close()` takes `sessionMutex` and could wait behind a leaked, back-pressured send while the gate is held. | Latent R3 violation (§5.6) | Only misbehaving user `cancel` code that keeps the processor sees `SessionNotActiveException` | Low |
| A4 | A2AServer.kt:649-653; TES:351-355; SEP:199-200 | One `internal fun Event.isTerminal()` (e.g. in `tasks/` next to `plusIfAbsent`), plus `isInterrupted` if wanted | Two duplicate helpers and an inline copy | None | Trivial |
| A5 | A2AServer.kt:551-561 | Misplaced KDoc: the "Waits for the agent job…" KDoc sits on `failedWithRejectedEvent`; move it to `awaitAgent` (:563). Fix the garbled `includeTaskSnapshot` KDoc (:431-434, which says "before the agent's first event" twice). Wrap SM:58 (overlong line). Rewrite SM "Lock order" (:55-60) after B1. | — | None | Trivial |
| A6 | A2AServer.kt:532-548 | `if (isRejected || !isResponseComplete) awaitAgent(session)`, then the `!isRejected` tail | One branch | None | Trivial |
| A7 | SEP:156-176 | `sendTaskEvent`/`sendServerTaskEvent` → one private `send(event, persistFollowUpMessage)` holding the open check | Duplicated open-check bodies | None | Trivial |
| A8 | TES:130-149; SM:226-230 | One `openTurnView(turnId, historyLength)` for both cases (the new-task snapshot is always `null`). Costs one storage read per new-task turn; skip if that matters. | One overload, the `if (taskExists)` branch | None | Trivial |
| A9 | TES:271-296 | `val ctxId = task.contextId ?: contextId` once | 3× repetition | None | Trivial |
| A10 | A2AServer.kt:482-490, 796-804 | Private `newRequestContext(ctx, params, contextId, taskId, task)` | Duplicated `ContextTaskStorage`/`ContextMessageStorage` wiring (partly pre-existing) | None | Trivial |
| A11 (optional) | SEP:47-57, 187-190; TES:180-211 | Single `pendingUserMessage` instead of `userMessage` + `followUpMessage` (new task: consumed by the applied initial `Task`; follow-up: by the first applied non-Task event) | One `publish` param, `takeUnless { taskExists }` | Differs only if a **new-task** turn's `Task` is ignored because a task with the generated id already exists (practically impossible). Then a later status update would persist the user message. | Low; low value |

### (B) Changes needing a user decision

**B1. Linearize turn start and cancel on `processorGate` (view-first `startTurn`). Recommended.**
- **Change:**
  1. `startTurn` pins the stream (registry, short) and then takes `stream.processorGate`.
  2. Under the gate: `check(taskId !in turns)` (registry, short), then `openTurnView` under `[S]`.
  3. For a follow-up, if the snapshot is terminal, close the view and throw `A2AUnsupportedOperationException` (the same error the A2AServer validation gives today, A2AServer.kt:468-472).
  4. Build the `Session` with plain values, insert it into `turns` (NonCancellable), release the gate, launch `monitorTurn`.
  5. `withTaskProcessor` looks up `turns[taskId]?.session` once.
  6. `onCancelTask` drops `tryLock`/`unlock(taskId)`, the loop, `hasTaskLock` and the nullable return. `cancelWithProcessor` becomes the body of `withTaskProcessor`.
- **Why it is deadlock-free.** New order: TL → gate → {registry | `[S]`}.
  - Cancel never takes TL.
  - `join()` under TL waits for `monitorTurn`, which needs the gate. The gate holder (cancel) never waits on TL or on a registering turn.
  - The registry lock still never waits on `[S]` (C4).
- **Removes:** W1–W5 (`CANCEL_LOCK_POLL_MILLIS`, the loop, `lockAcquired` handling, `hasTaskLock`, `checkNotNull`, `Turn`, `registered`, `findRegisteredSession`, the two deferreds, the lazy `events` wrapper, most of SM:235-251), and makes W6 redundant. Roughly 80–100 lines and two of the three intricate spots in session-manager-concurrency.md §5.
- **Observable impact:**
  - **RPC level: no new outcomes.** In the cancel-vs-follow-up race, F either gets `UnsupportedOperation` (cancel linearized first) or is cancelled as a running turn. Both happen today, depending on who wins TL. B1 shifts the distribution toward the first. `testCancelRacingWithFollowUpTurnEndsInOneConsistentTerminalState` accepts both (it only checks final-state terminal and "cancel succeeds iff CANCELED", A2AServerCancelTest.kt:305-337).
  - **`SessionManager` API (public, new this session):** `getSession`/`activeSessions` no longer report a turn that is blocked registering its view. Only callers that start turns without serializing them (contract violation, SM:48-50) could notice. Two tests assert the old visibility and need edits: `testStuckFanOutOfOneTaskDoesNotBlockOtherTasks` (SessionManagerTest.kt:686-690, the `1 // only A, which is still starting` at :711) and `testCancelledStartTurnReleasesReservation` (:743).
  - A follow-up now waits on the gate (while holding TL) behind a running idle-cancel handler, instead of the cancel polling behind the follow-up. That is equivalent from the client's point of view.
- **Risk:** medium (lock change). Covered by the existing cancel/race/stuck-stream tests after the two edits.
- **Options:**
  - (a) B1 as described (recommended);
  - (b) keep the current design and only do A-items;
  - (c) B1 without the terminal-snapshot check. **Not valid**: a follow-up could then start on a CANCELED task.

**B2. Drop the `cancelKey` lock from `onCancelTask` (after B1).**
- The gate serializes cancels per task.
- `cancelKey()` and `tasksMutex` are pre-existing `protected` API.
- Options:
  - (a) remove the usage, keep `cancelKey()` deprecated;
  - (b) remove both, since v1 already breaks the API;
  - (c) keep it (harmless redundancy).
- Recommend (a).

**B3. §6(1) turn queue in `SessionManager`.**
- Recommend **not now**.
- After B1 its remaining payoff is moving TL + `join` out of A2AServer. It costs a public `startTurn` semantics change, rebuilding FIFO fairness, and a fresh deadlock analysis.
- Revisit only if `tasksMutex` itself becomes a problem.

**B4. §6(2) "terminal is final" in `publish`.**
- After B1 there is no remaining in-SDK path that publishes after a terminal state:
  - turn processors close at terminal (SEP:202-204);
  - cancel cancels and joins the agent before forcing;
  - standalone processors are closed by A3.
- Options:
  - (a) skip (recommended);
  - (b) cheap guard: a per-instance flag set by `closeAll` at terminal (a stream instance recreated later reads storage anyway);
  - (c) a storage read per event under `[S]`, which costs throughput.
- If adopted, choose "ignore + log" (like `Task`-for-existing, TES:187-194), not a `TaskOperationException`. An exception would feed `rejection` and produce -32006 (C7).

**B5. Public surface added this session in `SessionManager`.**
- Affected: `startTurn`, `subscribe`, `withTaskProcessor`; `Session` stays public with an internal constructor.
- They are used only inside a2a-server (a repo-wide search for `SessionManager|withTaskProcessor|tasksMutex|cancelKey` finds no users outside it).
- They expose the gate contract ("must not `join` inside `withTaskProcessor`", SM:373-374) to users.
- Options:
  - (a) keep public;
  - (b) mark them `@InternalA2AApi` (subclasses overriding `onCancelTask` can opt in);
  - (c) make them `internal` and `sessionManager` non-`open`.
- Recommend (b).

**B6. Forced CANCELED bypassing the processor (W7). Decide in M8e.**
- Today `sendServerTaskEvent` sets `isTaskEventSent`. That makes the old after-turn push fire for a cancelled turn in which the agent sent nothing (SM:302-304).
- Once M8e pushes per persisted event, publish the forced CANCELED through an internal stream call and delete `sendServerTaskEvent` and the `persistFollowUpMessage` plumbing.

**B7. `Session.completion` → the monitor `Job`. After M8e.**
- Today push runs after `completion.complete()` (SM:299-304), so `join()` = monitor job would make follow-ups wait for webhooks. That is an observable latency change.
- Once push leaves `monitorTurn`, `completion` can go.

## Koog status

The tables above cite the current Kotlin code. No code was changed.

## Testability notes

- **B1:**
  - Run `A2AServerCancelTest` (incl. the race test under repetition), `SessionManagerTest` (`testWithTaskProcessorKeepsRunningTurnOpen`, `testStuckFanOutOfOneTaskDoesNotBlockOtherTasks`, `testCancelledStartTurnReleasesReservation`, `testStartTurnWhileTurnIsRunningFails`) and `A2AServerSubscribeTest`.
  - Add one deterministic test: an idle cancel holds the gate inside a gated `AgentExecutor.cancel`, a follow-up is started, the cancel is released → the follow-up fails with `A2AUnsupportedOperationException` and its agent never runs.
- **A1:** existing turn-end/failure tests (`testFailedTurnMarksTaskFailed`, `testEndTurnWith*`).
- **A3:** a unit test where `AgentExecutor.cancel` leaks the processor and sends after returning → `SessionNotActiveException`.
- No TCK requirement observes any of these internals.

## Discrepancies

None. No upstream sources were compared.

## Open questions

1. **Bug, not a simplification.** `monitorTurn` is launched with a default-start `coroutineScope.launch` (SM:253). If the server scope is already cancelled, the body never runs, so `completion` never completes and `join()` hangs (shutdown only). The fix is `CoroutineStart.ATOMIC` (a delicate API) or completing `completion` from `invokeOnCompletion`.
2. The push-config RPCs check task existence with the context-free `get(taskId)` (A2AServer.kt:921-924). Subscribe, cancel and send use the owner-scoped `get(GetTaskRequest, ctx)`. Should D3's owner scoping apply to push RPCs too? This is a correctness/decision question, not a simplification.
3. Python-SDK check (carried over from session-manager-concurrency.md OQ3) of whether events after a terminal state are rejected. It only matters if B4(b/c) is chosen.
