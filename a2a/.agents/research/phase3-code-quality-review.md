# Phase 3 code-quality and architecture review: a2a-server, a2a-client, JSON-RPC transports

**Sources checked:** Koog `eugenethedev/a2a-1.0` @ `65b063414` (code as it stands; `887b947e6..HEAD` used to find what changed); kotlinx-coroutines-core 1.10.2 sources (`withContext`, `Builders.common.kt`); Ktor client 3.3.3 sources (`plugins/sse/builders.kt`). Decisions: `a2a/.agents/state.md`. Earlier reports: `phase3-final-review.md`, `simplification-review.md`, `session-manager-concurrency.md`, `m8e-push-design.md`.
**Confidence:** high for B1–B4, B6 and W1 (I traced each path in the code and checked library behavior where it mattered). Medium for the architecture and test opinions.

**Method:** I ran the `code-review` skill at `high` on `887b947e6..HEAD`, report only. Its 10 findings are input only: I verified each one, merged the confirmed ones below, and dropped those that restate recorded decisions (unbounded push queue Q6, configs read per event Q4, replaced inline config kept R2-Q1) or are style. I did not run `simplify`, because it applies changes. I did the simplification pass by hand. No product file was touched.

Paths are relative to `a2a/a2a-server/src/commonMain/kotlin/ai/koog/a2a/server/` unless stated.

## Summary

The lock design holds up. It never blocks across tasks: the registry lock is never held while waiting on [S], and no lock is taken in the wrong order. Cancellation safety is mostly right too: NonCancellable/ATOMIC are used where they need to be, and the F1 guard (`turn.cancelEvents()` in `finally`) closes the requester-view leak.

The main problems:

- **Real bugs.** Cancellation semantics are inferred from exception types:
  - an agent's own `CancellationException` (e.g. from `withTimeout`) leaves the task stuck (B1);
  - a publisher cancelled in the middle of a fan-out leaves subscribers of a terminal task open forever (B2);
  - a non-supervisor `coroutineScope` lets one agent crash take the whole server down (B3).
- **Workarounds.**
  - "Why did the turn end?" is reconstructed from `agentJob` state on the requester side (W1).
  - The inline push config is saved outside the critical section, so it needs three undo paths (W2).
  - CancelTask re-reads storage up to 6 times and catches `TurnNotActiveException` (W3).
  - The `SSEClientException` unwrapping in the client (W4).
- **Architecture.** Most of the remaining complexity comes from one deferred decision: turn serialization lives in `A2AServer` (B3 of the simplification review, deferred).
- **Tests.** Behavior is well covered, but fixtures are copied across about 8 files, and the back-pressure, push and failure scenarios are tested three times, once per layer. B1–B3 have no tests.

## Workarounds

### W1. workaround (+ narrow bug): the requester infers "rejected event" by peeking at the agent job's state
- **Where:** `A2AServer.kt:534` (`event.isTerminal() && turn.failedWithRejectedEvent()`), `:656-659`, `:560-571` (`turn.eventProcessor.rejection`), `TurnEventProcessor.kt:70`.
- **Compensates for:** the turn view carries no reason for its end. The server's FAILED (from `markFailed`) looks the same as any other terminal event, so `A2AServer` guesses: "terminal event + agent job already failed with `InvalidEventException`/`TaskOperationException` ⇒ the FAILED came from a rejection".
- **Concrete failure** (code-review finding, verified):
  1. The agent's event is rejected and the job fails.
  2. Before `monitorTurn` gets the gate (`TurnManager.kt:299`), a CancelTask holds it. `AgentExecutor.cancel` publishes CANCELED through the turn processor, which is still open: a rejection doesn't close it. Or the server publishes the forced CANCELED.
  3. The requester's view receives CANCELED, `failedWithRejectedEvent()` is true, and the requester gets `-32006` although the task is CANCELED.
- **Root fix:** make the end of the turn typed and in band. `TurnManager` records a `TurnOutcome` (Completed / Failed(e) / Rejected(e) / Cancelled) before `endTurn`. The server's FAILED is delivered to the turn view tagged as server-made, e.g. an internal wrapper element, or `Turn.outcome` completed before `markFailed` publishes. `A2AServer` then maps that tag instead of `agentJob` state. This also removes `rejection` from the agent-facing `TurnEventProcessor`.
- **Needs user decision:** no (internal).

### W2. workaround: inline push config is saved before the turn's atomic check, so it needs undo logic
- **Where:** `A2AServer.kt:491-512` (save, then three cleanup branches), `:587-612` (`saveInlinePushConfig`, `isNew` via `getAll` before `save`), `:497-505` (orphan cleanup inside the agent block).
- **Compensates for:** the follow-up is validated (including the terminal check) outside the gate, and re-checked atomically in `startTurn` (`TurnManager.kt:213`). Anything saved in between must be undone when the re-check fails. The orphan cleanup for new tasks lives inside the agent block because `TurnManager` has no "turn ended" hook.
- **Problems it causes:**
  - **TOCTOU in `isNew`** (`:608`). CreateTaskPushNotificationConfig doesn't take `tasksMutex`, so a Create with the same id can land between `getAll` and `save`. The follow-up is then rejected by `startTurn`, and `deleteInlinePushConfig` deletes the config the Create call just reported as created. Narrow window.
  - **Shutdown leak** (code-review finding, verified). `agentJob` is `async(start = LAZY)` (`TurnManager.kt:229`). If `coroutineScope` is already cancelled, the block never runs, so its `finally` (`A2AServer.kt:500-503`) never deletes a new task's inline config. With a persistent push storage the config is orphaned forever.
  - **Cannot undo a replacement.** The recorded R2-Q1 decision ("replaced config kept") is the visible symptom: an upsert can't be undone after the fact.
- **Root fix:**
  - Validate the config (URL, `validatePushNotificationConfig`) before `startTurn` as today, but **save** it inside `startTurn`, under the gate, after the terminal snapshot check. That could be a `beforeStart: suspend () -> Unit` parameter, or a turn-start hook. Then a rejected follow-up has nothing to undo, `isNew`/`SavedInlinePushConfig`/`deleteInlinePushConfig` go away, and a replacement only happens when the turn really starts.
  - Run the new-task orphan cleanup from the monitor's NonCancellable teardown (an `onTurnEnd` hook) instead of the agent block, which also fixes the shutdown leak.
- **Needs user decision:** small, because the save moves under the gate (storage I/O under the gate).

### W3. workaround: CancelTask polls storage around the executor
- **Where:** `A2AServer.kt:826-885`: up to 6 reads (initial, re-read, after `TurnNotActiveException`, `afterCancel`, `current`, final), plus the `catch (e: TurnNotActiveException)` at `:848-852` (the F2 fix).
- **Compensates for:**
  - The running agent is not gated: it can reach a terminal state, and close the processor handed to `executor.cancel`, while the cancel runs.
  - `TaskEventStream` keeps no notion of "this task is terminal".

  So every decision re-reads storage, and a closed processor surfaces as an exception the server has to translate.
- **Root fix (direction):** `TaskEventStream.publish`/`persistAndDeliver` returns, or the stream instance remembers while it lives, whether the task has reached a terminal state. Cancel then asks the stream rather than storage, and a send after terminal returns a typed "already terminal" result instead of `TurnNotActiveException`. This is close to B4(b) "terminal is final" from the simplification review, which was skipped. Cheaper partial fix: merge the `TurnNotActive` read with `afterCancel`, and skip the `current` re-read when `agentJob == null`.
- **Needs user decision:** yes for the stream-state variant (it revisits B4); no for merging reads.

### W4. workaround (client): unwrapping Ktor's `SSEClientException` on completion
- **Where:** `a2a-transport/a2a-transport-core-jsonrpc/.../JSONRPCClientTransport.kt:169-174` and `a2a-transport/a2a-transport-client-jsonrpc-http/.../HttpJSONRPCClientTransport.kt:77-101`.
- **Mechanism, checked in Ktor 3.3.3** `plugins/sse/builders.kt:189-197` and `:1209-1226`: `serverSentEvents` wraps **any** non-cancellation exception thrown in its block into `SSEClientException(response, cause)`. Because `incoming…collect(this@flow)` runs the whole downstream inside that block, every downstream exception is wrapped:
  - `toResponse`'s `A2AException`;
  - the user's own exceptions thrown in `collect {}`.

  The `onCompletion` unwraps only `A2AException` causes.
- **Concrete failure:** `client.sendMessageStreaming(req).collect { throw MyStop() }`. The caller gets `SSEClientException(cause = MyStop)` instead of `MyStop`, which breaks Flow exception transparency.
- **Root fix:** don't run downstream code inside Ktor's block. Open the session with `sseSession(...)`/`prepare`, iterate `session.incoming` in the outer `flow {}`, and `cancel()` it in `finally`. Map pre-stream JSON errors where the session is opened. The `onCompletion` hack then goes away.
- **Needs user decision:** no.

### W5. workaround: two `close` variants and test-only state on the processor
- **Where:** `TurnEventProcessor.kt:169-177`, `:80`, `:83`.
- **`close()` vs `closeImmediately()`:** both exist only for misbehaving code that sends through a leaked processor from outside the agent job or the cancel block. Within the SDK's own call paths, no send can be in flight when either is called: the monitor runs after the agent job completed and under the gate, and the standalone processor is closed when the block returns.
- **Test-only state:** `isOpen` is read only by tests. `isTaskEventSent` is internal state that could be private.
- **Direction:** one non-suspending close. Document that sends from outside the turn are unsupported. Make `isTaskEventSent` private.
- **Needs user decision:** no.

## Concurrency

Verified lock order. The documented order is TL → gate → {registry | [S]}, with the push queue lock and storage locks as leaves. Two additions:
- The processor's `turnMutex` sits between the gate and [S]: the monitor takes gate → `processor.close()` → [S], and a cancel block takes gate → `sendTaskEvent` → [S]. The agent takes `turnMutex` → [S] without the gate. Nothing takes the gate while holding `turnMutex`, so this is consistent. The KDoc at `TurnManager.kt:61-67` just omits it; add it.
- `KeyedMutex.mapMutex` is a global leaf.

I found no inversion. The registry lock is never held while acquiring [S]: `startTurn` `:198-204/254`, `monitorTurn` `:305` (after `endTurn` returned), `withTaskProcessor` `:403`, `acquire`/`release`.

`withContext(NonCancellable)` does not throw on exit when the caller was cancelled: fast path #2, `UndispatchedCoroutine` (Builders.common.kt, 1.10.2). So nothing between `startTurn` returning and the `try { emitTurnEvents } finally { turn.cancelEvents() }` (`A2AServer.kt:517-522`) can skip the guard. F1 stays fixed.

### B1. bug: a `CancellationException` thrown by the agent itself is treated as a cancellation, so the task stays stuck and the error is swallowed
- **Where:** `TurnManager.kt:285`, `TaskEventStream.kt:261`, `A2AServer.kt:669-670`. Found by code-review; I verified it.
- **Scenario:**
  1. The agent sends `Task(WORKING)`, then a `withTimeout` around an LLM call expires and `TimeoutCancellationException` escapes `execute()`.
  2. The `async` job completes as cancelled. `monitorTurn` doesn't log it and `endTurn` doesn't call `markFailed`, so the task stays WORKING forever, and SubscribeToTask streams never get a terminal event and never close.
  3. `awaitAgent` swallows the CE because the requester is still active, so a blocking SendMessage returns the WORKING task with no error.
  4. For a new task with no events, the client gets the misleading "Agent ended the turn without sending any event" (-32006).

  This is a common pattern in Koog agents.
- **Fix:** convert inside the agent job: `async { try { block(processor) } catch (e: CancellationException) { currentCoroutineContext().ensureActive(); throw AgentFailureException(e) } }` (any non-CE wrapper). A CE that escapes while the job itself is still active is a failure, not a cancellation. `CancelTask`'s `cancelAndJoin` and shutdown still produce a real cancellation.
- **Needs user decision:** no (it matches the "executor crash → FAILED" decision).

### B2. bug: a publisher cancelled mid-fan-out of a terminal event leaves the other subscribers open forever
- **Where:** `TaskEventStream.kt:217-224` (`persist`; `fanOut`; `if (terminal) closeAll()`), `:333-343` (`deliver` rethrows the publisher's CE).
- **Scenario A** (CancelTask client timeout, plausible exactly when a subscriber is slow):
  1. Idle task with subscribers S1 (slow) and S2.
  2. CancelTask's forced `publish(CANCELED)` (`A2AServer.kt:865`) persists, then blocks in `fanOut` on S1.
  3. The CancelTask HTTP client times out, and the request coroutine is cancelled. `deliver` rethrows, so `closeAll()` is skipped.
  4. S2 never receives CANCELED and is never closed. The task is terminal, so nothing will ever publish to that stream again. S2 stays open until its client disconnects or the server shuts down, and the stream instance stays pinned.
- **Scenario B:** the executor's `agentJob.cancelAndJoin()` hits the agent while it is fanning out its own COMPLETED. Same result.
- **Also:** for non-terminal events, the subscribers that weren't reached silently miss an event. That breaks "all streams of a task see the same events". The KDoc accepts the loss (`TaskEventStream.kt:51-52`), but it doesn't mention that terminal closing is lost too.
- **Fix:** `try { fanOut(event) } finally { if (event.isTerminal()) closeAll() }`.
- **Stronger fix:** on a publisher cancellation mid-fan-out, close (not just skip) every subscriber not yet reached. A subscriber then either sees every event or ends, and can resubscribe for a fresh snapshot.
- **Needs user decision:** the minimal fix, no. The stronger fix, small (it closes slow subscribers in that case).

### B3. bug (configuration-dependent): an agent crash cancels the whole server unless `coroutineScope` is a supervisor
- **Where:** `TurnManager.kt:229` (`coroutineScope.async { block(processor) }`), `A2AServer.kt:346`. The KDoc doesn't require a supervisor.
- **Scenario:**
  1. A user passes `coroutineScope = CoroutineScope(Dispatchers.IO)`, which gets a plain `Job`.
  2. One agent throws. `async` propagates the failure to the parent even though it is awaited, so the scope is cancelled: every running agent, monitor and push worker is cancelled.
  3. Every later turn starts in a cancelled scope (ATOMIC monitor, the LAZY agent never runs). New tasks get -32006 "Agent ended the turn without sending any event"; follow-ups get a snapshot or stored task with no agent run. Pushes are silently dropped.

  The server is a silent brick. All tests pass a supervisor `agentScope()` (`A2AServerTestFixtures.kt:62-63`, `TurnManagerTest.kt:151-153`), so this is untested.
- **Fix:** in `TurnManager` (and the dispatcher), derive `CoroutineScope(coroutineScope.coroutineContext + SupervisorJob(coroutineScope.coroutineContext[Job]))`.
- **Needs user decision:** no.

### C1. hang risk (accepted by D-5, but worth knowing): non-cancellable fan-out of the server's FAILED
- **Where:** `monitorTurn`'s teardown (`TurnManager.kt:292-311`) runs `endTurn` → `markFailed` → `fanOut` inside `NonCancellable`, holding the gate.
- **Effect:** with a stalled requester view (e.g. an SSE client whose TCP window is full), the monitor can't be interrupted, not even by `A2AServer.cancel()`, until the transport cancels that request.
  - Meanwhile CancelTask, `startTurn` and `join()` for that task wait.
  - Subscribers are freed by `shutdown()`; requester views are not.
- **Direction:** acceptable under D-5. If it matters, end the requester views on shutdown too, e.g. cancel the views in `shutdown()`.
- **Needs user decision:** only if shutdown latency matters.

### C2. smell: the server has no "shut down" state
- **Behavior after `A2AServer.cancel()` (`A2AServer.kt:1073-1076`):**
  - SendMessage for a new task → -32006 "Agent ended the turn without sending any event";
  - a follow-up → snapshot only, or the stored task, with no agent run;
  - SubscribeToTask → snapshot, then normal completion.
- **Problem:** all of these are misleading.
- **Direction:** check a shutdown flag at the RPC entry points (e.g. `A2AUnsupportedOperationException`/-32603 "server is shutting down").
- **Needs user decision:** yes (which error).

### Design complexity (concurrency)
Per task there are TL (`tasksMutex`), the gate, `turnMutex` and [S], plus the global registry.
- **[S]:** essential: ordering plus atomic snapshot.
- **`turnMutex`:** essential: processor state together with publish.
- **The gate:** essential: cancel vs start/end.
- **TL:** exists only because turn serialization is the caller's job (`TurnManager.kt:50-52`). It is the one lock that could go (see A1).

The rest is about as simple as back-pressure without eviction allows.

## Bugs

- **B1–B3:** see Concurrency.
- **B4. bug (pre-existing, `f93408d05`):** `messages/InMemoryMessageStorage.kt:30` mutates the map under `withReadLock`.
  - Scenario: two agents in the same context call `ContextMessageStorage.deleteAll()` concurrently, or one deletes while another calls `getAll()`, on a multi-threaded dispatcher. Concurrent `HashMap` mutation and read on the JVM, which can corrupt the map or throw CME.
  - Fix: `withWriteLock`. No decision.
- **B5. design question (code-review finding, verified):** an A2A exception thrown by the agent on a **follow-up** marks the task FAILED.
  - This happens for any non-CE throw (`TaskEventStream.kt:261-262`), so the requester gets the FAILED task instead of the error code.
  - The `AgentExecutor` KDoc (`agent/AgentExecutor.kt:31-33, 94-96`) invites throwing e.g. `A2AContentTypeNotSupportedException` for invalid input. On a follow-up, that permanently kills an INPUT_REQUIRED conversation. On a new task with no events, the same exception reaches the client as -32005.
  - It is consistent with "executor crash → FAILED", but probably not what agent authors expect.
  - Options: (a) keep, and change the KDoc to say "on follow-ups, validate and reply with a status update instead of throwing"; (b) don't mark FAILED for `A2AException`s thrown before the agent's first event of the turn, and rethrow them to the requester.
  - **Needs user decision:** yes.
- **B6. bug (low, shutdown only):** the inline-config orphan leak when the LAZY agent job never starts. See W2.

## Smells / unnecessary code

| # | Where | What | Direction | Decision? |
|---|---|---|---|---|
| S1 | `turn/TurnManager.kt:145, 203`; `TaskEventStream.kt:96-97, 211, 297-301` | `nextTurnId` (counter under the registry lock) plus `Subscriber.turnId`. Turns of a task are serialized and the previous view is closed in `endTurn` before the next turn can start, so a stream has at most one turn view at a time. A boolean `isTurnView` would do. | Drop the counter, or keep it as cheap defense and say so. | no |
| S2 | `turn/TurnManager.kt:229` | The agent job is `LAZY` and started by the monitor's `await`. With the monitor ATOMIC and everything in `NonCancellable` under the gate, DEFAULT start behaves the same. LAZY is what makes B6 possible. | Use DEFAULT, or document why LAZY. | no |
| S3 | `A2AServer.kt:721-723` | `IllegalStateException("…event stream is empty")` is unreachable since F3: a new task with no events throws -32006 in `emitTurnEvents`. | `checkNotNull` with an "unreachable" message, or remove. | no |
| S4 | `turn/TurnManager.kt:110, 449-456`; `turn/Turn.kt:23-27`; `A2AServer.kt:364, 369` | Half-public surface. `TurnManager` is a public class with a public constructor (it takes push storage and sender), but its useful methods are `@InternalA2AApi`. `getTurn`/`activeTurns`/`Turn.events`/`Turn.agentJob` stay public, but without `startTurn` they are only useful for introspection. `protected open val turnManager` lets a subclass build a `TurnManager` with a different storage or scope (only a KDoc sentence prevents it). The base initializer still builds the unused default. | Make `TurnManager`/`Turn` `@InternalA2AApi` as a whole, or internal, and make `turnManager` non-open. B5 chose per-method annotation, so this revisits it. | yes |
| S5 | `TaskEventStream.kt:237-247`; `tasks/TaskStorage.kt:124-127` | Persisting the follow-up user message reads the full task (all history and artifacts) and writes it back with `update(Task)`. This imposes a hidden "replace entirely" contract on every custom storage, and costs O(task size) I/O per follow-up under [S]. (Code-review finding, verified.) | Dedicated storage op, e.g. `appendHistory(taskId, messages, clearStatusMessage)`, with a default implementation via get + update. | yes (storage API, cf. D4) |
| S6 | `A2AServer.kt:533-572` | `emitTurnEvents` tracks `isFirstEvent`/`isResponseComplete`/`isRejected` and branches four ways after the flow ends. Most of the branching is W1's inference. | Shrinks once W1 is done. | — |
| S7 | `tasks/InMemoryTaskStorage.kt:28-30` | The KDoc claims pages "stay stable when tasks are added or removed". A status update moves a task across the cursor, so it can be skipped or repeated. This is inherent to sorting by status timestamp. | Fix the KDoc only. | no |

## Architecture

**A1. The turn serialization split is the root of several workarounds.**
- **Today:**
  - `A2AServer` holds `tasksMutex` (TL), joins the previous turn, validates, and calls `startTurn`.
  - `TurnManager` asserts `check(taskId !in turns)` and re-checks the terminal state.
  - The lock order is documented across two classes (`TurnManager.kt:50-67`, `A2AServer.kt:360-364`).
- **Consequences:**
  - the duplicated terminal check (`A2AServer.kt:475-479` and `TurnManager.kt:213`);
  - W2's undo logic;
  - TL as a separate lock;
  - `protected val tasksMutex` as API whose contract subclasses can't really honour.

  This is B3 of `simplification-review.md`, deferred at the time because B1 was cheaper.
- **Recommendation:** revisit now that the design is stable. `TurnManager.startTurn(context, beforeStart, block)` waits for the previous turn itself (a per-task FIFO slot, not held while waiting for the monitor), runs validation and `beforeStart` under the gate, and returns the turn.
- **Removes:** TL, `getTurn().join()` in `A2AServer`, the `check`, the pre-check, and W2's undo paths.
- **Needs user decision:** yes (public `startTurn` semantics, FIFO fairness, a new deadlock analysis).

**A2. Response shaping depends on `TurnManager` internals.**
- `A2AServer` reads `turn.agentJob` completion exception types (`InvalidEventException`, `TaskOperationException`) and `turn.eventProcessor.rejection`.
- The agent-facing `TurnEventProcessor` carries turn-outcome state (`rejection`, `isTaskEventSent`, `pendingFollowUpMessage`) that belongs to `Turn`.
- **Fix:** W1's `TurnOutcome` fixes both.

**A3. The rest of the split is sound:**
- `TaskEventStream`: persist, broadcast, history rules;
- `TurnManager`: lifecycles, registry, gate;
- `PushNotificationDispatcher`: queueing and delivery, a leaf behind `onPersisted`;
- storage behind interfaces.

Nothing else is over-engineered beyond S1. `A2AServer` is long (1077 lines), but about 330 of them are KDoc examples. Its logic is cohesive and doesn't need splitting once A1/W1/W2 shrink it.

**A4. Transport:** `respondStreamingOrError` (`JSONRPCServerTransport.kt:142-171`) peeks the first event through a RENDEZVOUS `produceIn` channel that deliberately outlives `respondStreaming` ("e.g. by Ktor's test engine"). That is acceptable. It does mean the server flow is cancelled only when the call coroutine is cancelled, and F1's guard depends on Ktor doing that on disconnect, which no test checks (see T3).

## Tests

**T1. Copy-pasted fixtures** (about 200–300 lines removable):
- `createMessage`/`createTask`/`statusUpdate` are re-declared in `TurnManagerTest.kt:76-129`, `TaskEventStreamTest.kt:58-81` and `TurnEventProcessorTest.kt:47-74`.
- `userMessage`/`request`/`task`/`status`/`artifactChunk`/`createServer`/`ctx` are re-declared in each of `A2AServerSendMessageTest`, `A2AServerSubscribeTest`, `A2AServerCancelTest` and `A2AServerPushNotificationsTest` (see the private helpers at `:49-160` of each), with slightly different signatures.
- `agentScope()` exists twice: `A2AServerTestFixtures.kt:62` and `TurnManagerTest.kt:151`. `TEST_TIMEOUT` too: `TaskEventStreamTest.kt:43`.

Direction: one `TestEvents` builder in `commonTest` (task, status, message, artifact, with defaults for TASK_ID/CONTEXT_ID/TIMESTAMP), used by both test packages.

**T2. The same behavior is tested at three layers:**

| Behavior | `TaskEventStreamTest` | `TurnManagerTest` | `A2AServerSubscribeTest` |
|---|---|---|---|
| Back-pressure | `:312-441`, 5 tests | `:707-853`, 4 tests | `:328-383`, 2 tests |
| Failure → FAILED | `:274`, `:295` | `:259`, `:281` | `:284` |

Pushes are tested in `TurnManagerTest:576-705` (6 tests) and again in `PushNotificationDispatcherTest`. The per-layer variants are near-identical: a slow subscriber, a stuck subscriber cancelled, every event received in order.

Direction: keep the mechanism tests at the stream and dispatcher level, plus one end-to-end test per behavior at the `A2AServer` level. Drop the `TurnManager` duplicates, except those about turn/registry/gate interplay (`testStuckFanOutOfOneTaskDoesNotBlockOtherTasks`, `testCancellingRequesterViewUnblocksAgent`).

**T3. Gaps in the complex logic:**
- **B1:** an agent throwing `TimeoutCancellationException` after `Task(WORKING)` should give FAILED, and subscribers should close.
- **B2:** a cancelled publisher mid-fan-out of a terminal event should still close the other subscribers. `testCancelledPublisherReleasesStreamLock` covers only the lock.
- **B3:** a non-supervisor scope plus a crashing agent should not affect the next request.
- **W1 race:** an agent rejection followed by a cancel's CANCELED should give the requester CANCELED.
- **W2:** concurrent Create plus a rejected follow-up with the same config id.
- **Transport:** an SSE client disconnecting mid-stream should cancel the server flow and unblock the agent. This is the end-to-end precondition of the F1 guard; the only stress IT is `@Disabled`.
- **Shutdown:** requests after `A2AServer.cancel()` (C2).

**T4. Tests that pin down implementation details:**
- `TurnEventProcessorTest` asserts `processor.isOpen` (`:130, 181, 200, 249, 345, 368`). The behavioral assertion ("the next send fails with `TurnNotActiveException`") is already in the same tests.
- `activeStreams()`/`activeWorkers()` counters are asserted in many `TurnManagerTest` tests. That's fine as leak checks, but one dedicated leak test per path would do.

## Top 10, ranked

1. **B1:** treat an agent-thrown `CancellationException` (e.g. `withTimeout`) as a failure. Today the task is stuck non-terminal and the error is swallowed.
2. **B2:** close subscribers in a `finally` when a terminal fan-out is interrupted. Today SubscribeToTask streams of a terminal task can stay open forever.
3. **B3:** run agent jobs, monitors and push workers under an internal `SupervisorJob`. Today one crash can brick the server.
4. **W1/A2:** replace the requester-side `agentJob` peek with an in-band typed turn outcome. Fixes the -32006-instead-of-CANCELED race and moves `rejection` out of `TurnEventProcessor`.
5. **W2:** save the inline push config under the gate after the terminal re-check, and do the orphan cleanup in the monitor teardown. Removes the three undo paths, the `isNew` TOCTOU and the shutdown leak.
6. **B5 (decision):** should an A2A exception thrown on a follow-up kill the task (FAILED) or reach the client as its error code?
7. **W4:** stop running downstream collection inside Ktor's `sse {}` block. Fixes the wrapped user exceptions and the `onCompletion` unwrap hack in the client.
8. **B4:** `InMemoryMessageStorage.deleteByContext` must take the write lock.
9. **A1 (decision):** move turn serialization (TL + join + pre-check) into `TurnManager`. That retires TL, the duplicated terminal check and `protected tasksMutex`. Decide S4 (whether `TurnManager`/`turnManager` stay public/open) at the same time.
10. **T1–T3:** consolidate the test fixtures and the three-layer duplicates, and add the missing tests for B1–B3, W1 and transport disconnect.
