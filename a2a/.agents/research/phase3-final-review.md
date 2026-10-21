# Phase 3 final review: does the a2a-server + JSON-RPC server transport implement plan §3 (3.1–3.21) and the recorded decisions, and what defects remain?

**Sources checked:** Koog `eugenethedev/a2a-1.0` @ `edc575012` (scope `git diff 887b947e6..HEAD -- a2a/`, plus `agents/agents-utils` `KeyedMutex`/`RWLock`); A2A spec repo @ `1205f45` (`v1.0.1-88`), baseline tag `v1.0.1` (`docs/specification.md`, `specification/a2a.proto`); kotlinx-coroutines-core 1.10.2 sources (the version in `gradle/libs.versions.toml:18`). I did not consult the Python SDK or the TCK: every behavior where they might matter is already settled by a recorded decision.
**Confidence:** high for the findings below. Each comes from reading the code paths and, for the coroutine semantics, the library source. I ran no tests (read-only role).

## Answer
Phase 3 is essentially complete. All of 3.1–3.21 and every recorded decision are implemented, including the C3, M8e Q1–Q10, M8e-3 and M8e-4 items: `cancelKey`, `sendServerTaskEvent`, `Turn.completion` and the `Session*` names are gone, and the lock order TL → gate → {registry | [S]} holds everywhere. Two real bugs remain:
- **F1:** a requester cancelled in a small window right after `startTurn` leaves its turn view registered and never drained. The task's agent then blocks forever while holding the stream lock, and later follow-ups and forced cancellation of that task hang too.
- **F2:** a race in CancelTask can surface as `-32603` instead of `TaskNotCancelable`.

The rest is minor: one unspecified-behavior inconsistency, some hygiene, and a few test gaps.

## Requirements
Per plan item; tiers and spec citations are summarized here, details are in the plan and earlier reports. All rows are JSON-RPC-relevant.

| #    | Item | Status | Evidence |
|------|------|--------|----------|
| 3a   | Compile on v1 core | ✅ | builds; explicit `TaskStatus.timestamp` in server-made events `TaskEventStream.kt:280`, `A2AServer.kt:824` |
| 3.1  | Blocking default, `returnImmediately`, terminal/interrupted, full artifacts | ✅ | `A2AServer.kt:653-691` (MUST, spec §3.2.2 `spec.md:442-447`) |
| 3.2  | `taskId` must exist; contextId inferred; mismatch rejected | ✅ | `A2AServer.kt:458-480` |
| 3.3  | Terminal task → UnsupportedOperation | ✅ | `A2AServer.kt:469-473`, re-checked atomically `TurnManager.kt:209-213` |
| 3.4  | Stream rules | ✅ per D-2/D-4 | `A2AServer.kt:508-551, 706-713`; see F3 for the empty-turn corner |
| 3.5  | SubscribeToTask | ✅ | `TurnManager.kt:331-352`, `TaskEventStream.kt:117-127` |
| 3.6  | Capability gating | ✅ | `A2AServer.kt:382-393, 992-1016` |
| 3.7  | A2A-Version / extensions | ✅ | `JSONRPCServerTransport.kt` `checkVersion`; `A2AServer.kt:973-984`; skipped for GetExtendedAgentCard (`:374-393`) |
| 3.8  | GetTask artifacts + historyLength | ✅ | `InMemoryTaskStorage.kt:38-40`, `TaskProjection.kt:26-41` |
| 3.9  | ListTasks | ✅ | `InMemoryTaskStorage.kt:42-75`, `A2AServer.kt:732-748` |
| 3.10 | CancelTask | ✅ (F2 race) | `A2AServer.kt:776-839` |
| 3.11 | Push CRUD | ✅ | `A2AServer.kt:869-931` (+ blank url `:950-955`, `edc575012`) |
| 3.12 | Inline push config | ✅ | `A2AServer.kt:484-505, 558-595` |
| 3.13 | Push delivery | ✅ | `PushNotificationDispatcher.kt`, `SimplePushNotificationSender.kt:66-95` (StreamResponse body, `Authorization`, token header, 15 s, one attempt) |
| 3.14 | Persist user messages | ✅ per decision | `TaskEventStream.kt:181-240` |
| 3.15 | Server tests rewritten; JSON-RPC IT re-enabled | ✅ | `A2AServerJsonRpcIntegrationTest` enabled; only `StressA2AServerJsonRpcIntegrationTest.kt:18` `@Disabled` (pre-existing, intentional) |
| 3.16–3.20 | Transport items | ✅ (unchanged since 3c) | — |
| 3.21 | Agent-card caching | ⏭ skipped by decision | — |

`plan.md` §4 can mark items 2, 5, 6, 7 and 8 as decided (see `state.md` "Decisions recorded"). Item 10 (4.2) stays open.

## Details: findings

### F1. bug: a turn view leaks if the requester is cancelled right after `startTurn` (task-wide hang)
- **Where:** `A2AServer.kt:453-514` together with `TurnManager.kt:215-258`. The root cause is in kotlinx `flow/Channels.kt:28-41` (1.10.2): `emitAllImpl` calls `ensureActive()` *before* its `try`, so `consumeAsFlow()` (used for `Turn.events`, `TurnManager.kt:245`) does **not** cancel the channel when the collector is already cancelled at collection start.
- **Mechanism:**
  - Once `openTurnView` registers the view, the only suspension points until `emitAll(turn.events…)` are non-cancellable: the `withContext(NonCancellable)` commit, `registryMutex`, and `KeyedMutex.unlock`, which also runs in `NonCancellable`.
  - Per the `withContext` contract (`Builders.common.kt:136-138`), `withContext(NonCancellable)` neither throws on entry nor on exit. So a cancellation that arrives anywhere in that window is only observed at `emitAllImpl`'s `ensureActive()`.
  - The `catch` at `TurnManager.kt:255-258` ("or it did but the caller is gone") therefore never fires after the turn has started.
- **Failure scenario:**
  1. A client disconnects or times out while its SendMessage / SendStreamingMessage is committing the turn. Ktor cancels the call, and the transport's `produceIn` producer is cancelled with it.
  2. The turn starts, but nobody ever receives from or cancels its RENDEZVOUS view.
  3. The agent's first `sendTaskEvent` persists the event, then suspends forever in `fanOut` while holding the stream lock. Push for that event is already queued.
  4. The turn never ends, so every follow-up for the task blocks forever in `tasksMutex` → `join()`, and `SubscribeToTask` blocks on the stream lock.
  5. CancelTask: an executor that cancels `agentJob` unblocks the agent, but the forced CANCELED `publish` then blocks on the same view while holding the gate and the stream lock. Subscribers never get the terminal event.
- **Likelihood:** low (a window of microseconds to milliseconds, longer when `registryMutex` or `KeyedMutex.mapMutex` are contended). Impact: permanent for that task.
- **Fix:** guarantee the view is cancelled whenever the flow body exits after `startTurn` returned. For example, add `internal fun Turn.cancelEvents()` (channel `cancel()`) and wrap everything from `startTurn`'s return through the end of `onSendMessageCommon` in `try { … } finally { turn.cancelEvents() }`. Alternatively, build `Turn.events` as `flow { try { for (e in channel) emit(e) } finally { channel.cancel() } }` (a `flow {}` builder runs no `ensureActive` before its body). Then remove or reword the misleading comment at `TurnManager.kt:256`.
- **Needs user decision:** no.

### F2. bug (minor): CancelTask racing a turn that just reached a terminal state can return `-32603`
- **Where:** `A2AServer.kt:794-805`, `TurnEventProcessor.kt:132-135`.
- **Scenario:**
  1. The block re-reads the task as non-terminal (`:796-801`). Concurrently the running agent sends `COMPLETED`, which closes the turn's processor. The gate doesn't stop the agent.
  2. The executor's `cancel` then calls `eventProcessor.sendTaskEvent(CANCELED)`, which is exactly the documented example at `AgentExecutor.kt:120-140`.
  3. That throws `TurnNotActiveException`, which is not an `A2AException`. The transport maps it to `-32603 "Internal error: Turn event processor is closed…"` (`JSONRPCServerTransport.kt:334-337`).
  4. Spec §3.1.5 (`spec.md:278-281`) lists `TaskNotCancelableError` for an already completed task, and the decided 3.10 semantics say the same.
- **Fix:** catch `TurnNotActiveException` around `agentExecutor.cancel(...)`, re-read the task, and throw `A2ATaskNotCancelableException` if it is terminal and not CANCELED (return it if CANCELED); otherwise rethrow.
- **Needs user decision:** no.

### F3. divergence (unspecified corner, inconsistent): a new-task turn that ends with no events and no failure
- **Blocking:** `IllegalStateException("…event stream is empty")` → `-32603 "Internal error: …"` (`A2AServer.kt:680-682`).
- **Streaming:** an empty SSE stream (`JSONRPCServerTransport.kt:156`). This violates spec §3.1.2's MUST that a stream contains one Message or begins with a Task (`spec.md:202-206`); the cause is an agent bug.
- **Fix:** in `onSendMessageCommon`, throw `A2AInvalidAgentResponseException` (-32006) when the turn of a new task ends with no event, so both paths agree. This matches the "invalid agent events → -32006" decision.
- **Needs user decision:** yes, small (which code to use).

### F4. hygiene (security-adjacent): agent exception text reaches the client
An agent crash before any event is rethrown by `awaitAgent` (`A2AServer.kt:625-635`). For non-A2A exceptions the transport builds `"Internal error: ${e.message}"` (`JSONRPCServerTransport.kt:336`). The transport line predates Phase 3, but it is inconsistent with the "generic FAILED message, no exception details" decision.
- **Fix:** use a generic message for non-A2A exceptions in `toJSONRPCErrorResponse`, keeping the detail in the log.
- **Needs user decision:** yes (scope: the transport is shared).

### F5. hygiene: push configs accepted but never delivered when `pushSender == null`
`storageIfPushNotificationSupported` (`A2AServer.kt:1006-1016`) and `saveInlinePushConfig` (`:564-568`) check only the storage. The dispatcher, however, exists only when both storage and sender are set (`TurnManager.kt:120-125`). CRUD and inline configs then succeed, but no notification is ever sent. This predates Phase 3 in spirit.
- **Fix:** treat a missing sender as "not configured" (-32003 / inline ignored), or document it.
- **Needs user decision:** yes, small.

### F6. minor: an event can be persisted without being pushed
`TaskEventStream.persist` (`TaskEventStream.kt:224-227`) calls `onPersisted` after `taskStorage.update`. Suppose a custom storage switches dispatcher inside `update` (prompt-cancellation `withContext`), and the publisher (agent job) is cancelled, e.g. by CancelTask. Then the update can commit but throw on return, so the push is skipped. This is a narrow corner for custom storages, and the stream KDoc already accepts the analogous fan-out loss.
- **Fix:** document it, or wrap `update` + `onPersisted` so that a committed write is always reported.
- **Needs user decision:** no.

### Hygiene: stale docs, dead code, API
- `A2AServer.kt:697-699`: "…unless the agent starts the turn with a [Task]" is stale. A Task event for an existing task is ignored, so the snapshot is always emitted first.
- `RequestContext.kt:20`: says `task` has the full history, but CancelTask contexts get a task read with `historyLength = 0` (`A2AServer.kt:796, 803`). Fix the KDoc or read the full task.
- `TurnManager.kt:395-400`: the standalone cancel processor is built with `taskExists = false`, so an executor's `sendMessage` there is "accepted" and delivered to nobody. Pass `taskExists = true` to reject it like any existing-task turn.
- `TurnManager.kt:255-256`: misleading comment; see F1.
- `a2a-server/Module.md:3-4`: garbled text ("storage abstractions with for tasks, turns, and push subscriptions"); there is no turn storage.
- `a2a/AGENTS.md:236`: the example still uses `RequestContext<MessageSendParams>`; it should be `SendMessageRequest`.
- `JSONRPCServerTransport.kt:199-200` (pre-Phase 3, `eb778ef7f`): the non-streaming `onRequest` switch has an unreachable `SubscribeToTask` branch that would try to serialize a `Flow`. Delete it.
- No leftover references to `completion`, `sendServerTaskEvent`, `cancelKey` or Session names in main code; no TODOs except the Stress test. All new public declarations have KDoc. `startTurn`, `subscribe` and `withTaskProcessor` are `@InternalA2AApi` (B5).

## Koog status
Verified against the recorded decisions:
- **Lock order:**
  - `startTurn` (`TurnManager.kt:193-254`) takes gate → registry, releases it, then takes [S] (`openTurnView`), then registry.
  - `monitorTurn` (`:295-303`) takes gate → [S] (`endTurn`), then registry after [S] is released.
  - `withTaskProcessor` (`:392-417`) takes gate → registry, then [S] via `publish`.
  - The registry lock is never held while waiting on [S], and the TL is never taken under the gate: `onCancelTask` doesn't take it.
- **Cancellation safety:** `acquire`/`release` are balanced; `release` runs in `NonCancellable`. The monitor uses ATOMIC (shutdown fix, `efc227515`), and the dispatcher worker uses ATOMIC with a `finally` that drops the queue. `KeyedMutex`/`RWLock` were fixed in C1. The only gap found is F1.
- **M8e:**
  - Push of every persisted event via `onPersisted` under [S], with a per-task FIFO and a concurrent fan-out: Q1, Q4–Q7.
  - Inline config: id = taskId, taskId and tenant overwritten, hook before save under TL, blank url → InvalidParams before the hook, ignored when push is off (Q2, Q3, Q10, M8e-3/4).
  - Orphan cleanup in a non-cancellable `finally` and on a `startTurn` throw, new tasks only (Q9).
  - `publish` lambda bound to the stream (Q8). Owner-scoped lookups for push RPCs (`checkTaskExists`), send, cancel and subscribe.

## Testability notes
- **F1:**
  - Unit level: a deterministic test needs a hook between `startTurn` returning and collection starting. Easiest after the fix: test that the `finally` cancels the view by cancelling the `onSendMessageCommon` collector from inside a gated `IdGenerator`/storage call that runs *after* registration. Alternatively, start the turn through `TurnManager` and assert the agent isn't blocked once the owning job is cancelled without collecting.
  - Integration: hammer SendStreamingMessage with client cancellation (the Stress IT is disabled).
- **F2:** gate the agent so it sends COMPLETED while `cancel` is about to send CANCELED; assert `A2ATaskNotCancelableException`.
- **F3:** an agent that returns without events for a new task; assert the same error for blocking and streaming.
- **Existing gaps worth one test each:**
  - SubscribeToTask streams after `A2AServer.cancel()`: idle-task subscribers stay open until the transport cancels them. Decide whether that's intended.
  - Inline config + concurrent cancel rejecting a follow-up (see Open questions).
- **TCK:** none of F1–F3 is directly exercised by the TCK (CORE-CANCEL-*, STREAM-* use happy paths).

## Discrepancies
- **Spec vs. D-2:** spec §3.1.2 says the stream "MUST close when the task reaches a terminal state" (`spec.md:206`); Koog also closes at end of turn (e.g. INPUT_REQUIRED). This is covered by recorded decision D-2, so it is not a defect.
- **Spec vs. F3:** spec §3.1.2 requires a stream to contain a Message or begin with a Task; F3's empty stream violates that, but only for a misbehaving agent.

## Open questions
1. A follow-up's inline config is saved before `startTurn`'s atomic terminal re-check (`A2AServer.kt:485`, `TurnManager.kt:209`). If a concurrent cancel makes the task terminal, the request fails with UnsupportedOperation but the config stays on the task. This is consistent with "never clean up for a follow-up"; flagging it only so the user knows.
2. `A2AServer.cancel()` doesn't end open SubscribeToTask streams of idle tasks. Is that acceptable, or should shutdown close task streams?
