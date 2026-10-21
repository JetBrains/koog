# Session/event-stream redesign for `a2a-server` (plan 3.1, 3.4, 3.5, 3.13 timing, 3.14)

**Sources checked:**
- A2A spec repo @ `v1.0.1` (`3303592`), used as the baseline. Spec text extracted with `git show v1.0.1:docs/specification.md`, so the line numbers below refer to that file. `main` @ `7c861a5` (v1.0.1-82) was diffed for every cited section. The only changes are the `PushNotificationConfig` → `TaskPushNotificationConfig` renames, with no semantic change.
- a2a-python @ `v1.1.0` (`96c14b7`), the version pinned by `a2a/test-python-a2a-server/pyproject.toml:7`, and `main` @ `a4c4fc9` (v1.2.2-13). Citations are on `main` unless marked `@v1.1.0`.
- a2a-tck @ `263b9cf`. The research checkout is the same commit as the pinned clone `a2a/test-tck/a2a-tck`.
- Koog working tree on `eugenethedev/a2a-1.0` @ `887b947e6`.

**Confidence:** high for the requirements, Python behaviour, and TCK checks. Medium for the recommendation: it is a design judgement, and the spec text is internally inconsistent on when a stream closes.

## Answer

The core problem is that Koog's event stream lives only as long as one `AgentExecutor.execute` call ("turn"), but v1 needs one that lives as long as the task.

- **Today.** A `Session` is one execution. Its `MutableSharedFlow` closes and the session is dropped when `agentJob` ends (`SessionManager.kt:76-93`). As a result:
  - SubscribeToTask on an input-required task returns an empty stream (`A2AServer.kt:594`).
  - SubscribeToTask never sends a snapshot.
  - Cancelling an idle task emits into a processor nobody listens to (`A2AServer.kt:552-556`).
- **Needed.** A per-task event stream that lives for the task's whole non-terminal life, with "turns" layered on top:
  - SubscribeToTask attaches at any time, including when no turn is running. Taking the `Task` snapshot and registering the subscriber happen atomically. The stream closes only on a terminal state.
  - SendMessage and SendStreamingMessage see a turn-scoped view that ends when `execute` returns or the task becomes terminal.
  - Blocking SendMessage returns at the first terminal or interrupted state.
  - All streams of a task receive the same events in the same order.
  - The task stream is rebuilt lazily from `TaskStorage`, so idle input-required tasks hold no memory.
- **Python.** It does exactly this with a long-lived `ActiveTask` per task: a request queue, a producer running one turn at a time, a consumer that persists, broadcasts and pushes, and end-of-turn markers.
- **Recommendation: Option A.**
  - Keep `Session` as one turn.
  - Add an internal per-task `TaskEventStream`, owned by `SessionManager`. All persisting and fan-out go through it.
  - Keep the agent-facing API unchanged: `AgentExecutor`, `SessionEventProcessor.sendMessage`, `sendTaskEvent`.
- **Side decisions:**
  - **3.14:** the server persists incoming user messages into `Task.history`, deduplicated by `messageId`. This is Python's behaviour and is needed for the SHOULD-level TCK history tests.
  - **3.13:** push a `StreamResponse` for every `TaskEvent`, dispatched asynchronously and in order per task. This is Python's behaviour and matches spec §4.3.3 "when a task update occurs".

## Requirements

| # | Requirement | Tier | Bindings | Citation |
|---|---|---|---|---|
| Q1 | Blocking (`returnImmediately` false or unset, the default): wait until a terminal **or interrupted** (`INPUT_REQUIRED`, `AUTH_REQUIRED`) state, then return the latest task with all artifacts. | MUST | all (Koog: JSON-RPC) | spec §3.2.2 L446; proto `SendMessageConfiguration.return_immediately` L155-160 |
| Q2 | `returnImmediately: true`: return right after the task is created, while processing continues. | MUST | all | §3.2.2 L448 |
| Q3 | `returnImmediately` has no effect on a Message response, on streaming, or on push configs ("operates independently of execution mode"). | n/a (no RFC 2119 keyword) | all | §3.2.2 L450-454 |
| Q4 | Message-only stream: exactly one `Message`, then close. | MUST; capability:streaming | all | §3.1.2 L208 |
| Q5 | Task stream: begins with the `Task`, followed by status/artifact updates. It closes when the task reaches a terminal state. | MUST; capability:streaming | all | §3.1.2 L210. §11.7 L2973 (HTTP+JSON section) says "terminal or interrupted" and "MAY optionally resend a final Task" (see Discrepancies) |
| Q6 | SubscribeToTask: the first event is a `Task` with the current state at subscription time. | MUST; capability:streaming | all | §3.1.6 L311 |
| Q7 | SubscribeToTask can be used with **any non-terminal task**. The stream terminates at a terminal state. | MUST | all | §3.1.6 L309 |
| Q8 | SubscribeToTask errors: a terminal task gives `UnsupportedOperationError` (JSON-RPC -32004). An unknown task gives `TaskNotFoundError` (-32001). | MUST | all; JSON-RPC codes | §3.1.6 L304-305; §9.4.6 L2408; §5.4 L1182, L1185 |
| Q9 | Events are delivered in generation order. They are broadcast to all active streams of the task, and each stream gets the same events in the same order. Closing one stream doesn't affect the others. The task lifecycle is independent of stream lifecycles. | MUST | all | §3.5.2 L683, L691-694 |
| Q10 | Follow-up to an input-required task: the client sends a new message with the same `taskId`/`contextId`. An unknown `taskId` gives TaskNotFound (3.2). `contextId` is inferred from the task, and a mismatch is rejected (3.2). | MUST (errors, inference) | all | §3.4.3 L630-633, L627-628; §3.4.2 L613-614 |
| Q11 | Sending to a terminal task gives `UnsupportedOperationError` (3.3). | MUST | all | §3.1.1 L175 |
| Q12 | The agent decides which messages go into `Task.history`. Persisting all important messages is MAY, and clients must not rely on it. | MAY | all | §3.7 L760, L764 |
| Q13 | Push: "When a task update occurs" the server POSTs a `StreamResponse`. At least one delivery attempt per configured webhook. The spec does not define per-event vs. per-turn timing. | MUST (payload, ≥1 attempt); timing unspecified | webhooks (all) | §4.3.3 L844, L884 |
| Q14 | SendMessage "MUST return immediately with either task information or response message". | MUST, but contradicts Q1 (see Discrepancies) | all | §3.1.1 L180 |

All of these are relevant to Koog's JSON-RPC binding. Q4–Q9 apply only when `capabilities.streaming = true`. Q13 applies only when `capabilities.pushNotifications = true`.

## Details

### 1. Python reference structure (v2 handler; `DefaultRequestHandler = DefaultRequestHandlerV2` in both v1.1.0 and main, `request_handlers/__init__.py:46`)

**`ActiveTaskRegistry`** (`agent_execution/active_task_registry.py:47-109`)
- `taskId → ActiveTask`, created lazily by `get_or_create(create_task_if_missing=…)`.
- Removed when the task is finished **and** has no subscribers (`:111-121`, `active_task.py:963-984`).
- After a restart, an input-required task gets a fresh `ActiveTask` on subscribe or follow-up, because `start()` reads the task from the store (`active_task.py:520-588`).

**`ActiveTask`** (`active_task.py:418-1012`) has these parts:
- **Request queue + producer** (`:590-687`):
  - One producer loop per task. It takes requests one at a time under `_request_lock`.
  - For each request it brackets `execute()` with internal `_RequestStarted` and `_RequestCompleted(request_id)` markers.
  - The producer stays alive across turns. After an input-required turn, the `ActiveTask` keeps waiting for the next request.
- **Consumer `EventConsumer`** (`:111-415`): validates, persists through `TaskManager`, pushes, then enqueues `(event, updatedTask)` to the subscribers' queue (`:386-415`).
  - Message-vs-task mode is enforced (`:253-262`, `:271-274`).
  - A status update before any `Task` is rejected (`:304-312`).
  - A `Task` event for an existing task is **ignored** (logged; `:287-296`).
- **`subscribe(request?, include_initial_task, replace_status_update_with_task)`** (`:715-820`):
  - Each subscriber gets its own tap (`:745-747`) with `evict_on_full=True`, so a lagging subscriber is dropped rather than stalling the others.
  - With `request`, it enqueues that request and **returns at that request's `_RequestCompleted`** (`:791-801`). This gives the per-turn view.
  - Without `request`, it runs until the queue shuts down, which happens on terminal state (`:377-384`).
  - With `include_initial_task`, it yields `get_task()` first (`:751-757`). The tap is created before the snapshot read, so events can be duplicated but are never lost.

How the handlers use it (`request_handlers/default_request_handler_v2.py`):
- **`on_message_send`** (`:396-458`):
  - Subscribes with `request` and `replace_status_update_with_task=True`.
  - **Breaks on the first `Task` whose state is terminal or interrupted**, or on the first Task when `return_immediately` is set (`:420-436`). The executor keeps running in the background.
  - If the turn ended without such a state, it falls back to `get_task()` (`:446-448`).
  - The same logic is in v1.1.0 (`default_request_handler_v2.py:242-275`@v1.1.0).
- **`on_message_send_stream`** (`:465-490`): `include_initial_task=False`, and it closes at the end of its turn. **Follow-up streams therefore start with whatever the agent emits** (usually `statusUpdate`).
  - Python test `tests/integration/test_scenarios.py:1929` asserts that the last event of a follow-up stream is a `status_update`.
- **`on_subscribe_to_task`** (`:556-596`):
  - Store lookup: missing → TaskNotFound; terminal → UnsupportedOperation (`:563-573`).
  - Then `get_or_create(create_task_if_missing=False)` and `subscribe(include_initial_task=True)` (`:577-581`).
  - v1.1.0 raised `InvalidParamsError` for terminal tasks (`active_task.py:462-463, 600`@v1.1.0), which deviates from Q8. Fixed in `6cce91b` (in v1.2.x).

Python behaviour confirmed by its tests:
- An input-required task restored in a new handler can be subscribed to. A follow-up `send_message` completes it, and the subscriber's last event is COMPLETED (`tests/integration/test_scenarios.py:1846-1951`, assertion at `:1944`; also present @v1.1.0 `:1843`).
- Two concurrent subscribers receive the same WORKING snapshot, then the artifact, then COMPLETED (`test_scenarios.py:818-900`).

**Push timing** (`active_task.py:339-375`):
- Fires on **every** `Task`/`TaskStatusUpdateEvent`/`TaskArtifactUpdateEvent`. This is `PushNotificationEvent`, defined at `tasks/push_notification_sender.py:10`. Messages are never pushed.
- It is awaited inline in the consumer, so it is ordered but blocks the event pipeline.
- Failures are logged and swallowed (main `:361-375`; v1.1.0 `:286-297` had no guard).
- The sender posts `MessageToDict(to_stream_response(event))` to every config, concurrently (`tasks/base_push_notification_sender.py:68-102, 104-150`).
- An inline config from `SendMessageConfiguration` is stored under the (generated) task id **before** the ActiveTask starts (`default_request_handler_v2.py:373-383`).

**History** (the 3.14 question):
- `_RequestStarted` records the incoming user message (`active_task.py:216-220`).
- On the **first non-`Task` task event** of the turn, the consumer appends it to history if no history entry has the same `messageId` (`:319-334`; no dedup @v1.1.0 `:262-268`).
- `update_with_message` first moves the current `status.message` into history, then appends the user message, then clears `status.message` (`tasks/task_manager.py:392-410`).
- On a status update, the old `status.message` is moved into history (`task_manager.py:261-262`).
- For a *new* task:
  - If the agent's first event is a `Task`, it is stored as-is (`active_task.py:287-296`). The agent is expected to use `new_task_from_user_message` (`helpers/proto_helpers.py:277-291`), which puts the user message in `history`.
  - If the server has to create the task itself, it seeds `history=[initial_message]` (`task_manager.py:348-369`).
- The TCK's Python SUT relies on this server-side persistence: its agent never writes follow-up user messages (`tck: sut/a2a-python/sut_agent.py:51-120`).

**Failure** (`active_task.py:665-681`, `:144-160`): an executor exception writes FAILED (if the task isn't terminal yet) and propagates the error to all subscribers.

**Cancel** (`active_task.py:822-919`): calls `executor.cancel()`, then cancels the producer. If the task is still not terminal, the server writes CANCELED itself (`:905-918`). This includes idle input-required tasks.

**Cluster mode** (main only, released in v1.2.0, `942d621`):
- An optional versioned `TaskEventStream` (`server/cluster/event_stream.py:17-32`) lets subscribers on another replica tail events after a snapshot version (`default_request_handler_v2.py:584-596`).
- This shows where a pluggable event-stream abstraction leads. It is not required by the spec.

### 2. What the TCK actually checks (`263b9cf`)

| Req | Test | What it really asserts | Level |
|---|---|---|---|
| STREAM-SUB-001 | `tests/compatibility/jsonrpc/test_sse_streaming.py:211-258` | Creates an input-required task (`create_working_task`, blocking `tck-input-required`; `_task_helpers.py:55-64`). The first SSE result has key `task` and validates as `StreamResponse`. A subscribe error leads to **skip**. | MUST |
| STREAM-SUB-002 | `core_operations/test_task_lifecycle.py:360-417` | Subscribes to an **input-required task whose turn already ended**. 0.5 s later another client sends a blocking follow-up `tck-complete-task`. The subscribe stream must **close** (no 10 s timeout) and its last event must be terminal. This forces a stream that outlives the turn, receives a *later* turn's events, and stays open across input-required. | MUST |
| STREAM-SUB-003 | `test_task_lifecycle.py:419-462` | Subscribe to a completed task: either an immediate error validated as UnsupportedOperationError, or a stream that yields no event. | MUST |
| STREAM-SUB-004 | `test_sse_streaming.py:177-209` | Unknown id → TaskNotFoundError. | MUST |
| STREAM-ORDER-001 | `core_operations/test_stream_ordering.py:169-208` | Streaming `tck-stream-ordering-001`: states never regress (SUBMITTED < WORKING/INPUT_REQUIRED/AUTH_REQUIRED < terminal). Every event validates as `StreamResponse`. Despite the docstring, it does **not** check that the first event is a Task or that the last is terminal. | MUST |
| STREAM-ORDER-002/003/004 | `core_operations/test_multi_stream.py:131-241` | Two concurrent SubscribeToTask calls on an **idle input-required task**. Each stream must get ≥1 event, the two event lists must be identical (excluding the JSON-RPC id), and closing stream 0 after one event must not stop stream 1 getting ≥1 event. In practice only the snapshot is compared; the streams then sit until the 10 s timeout (`_test_helpers.py:90`). | MUST |
| CORE-SEND-001 | generic runner `test_requirements.py:90-128` | `tck-complete-task`: success + `SendMessageResponse` schema. | MUST |
| CORE-SEND-002 | `test_task_lifecycle.py:251-285` | Send to a completed task → UnsupportedOperationError. | MUST |
| CORE-EXECUTION-MODE-001/002 | generic runner (`tck/requirements/core_operations.py:385-436`) | **Success + schema only.** No timing check for blocking or `returnImmediately`. | MUST |
| CORE-STREAM-001/002/003 | generic runner (`validate_streaming_events`, `tck/validators/streaming.py:25-38`) | ≥1 event only. Neither "begins with Task" nor "closes at terminal" is checked. | MUST |
| CORE-MULTI-005/006 | `test_task_lifecycle.py:287-358` | contextId inference / mismatch rejection (3.2). | MUST |
| CORE-HIST-004 | `core_operations/test_task_history.py:179-211` | After input-required → follow-up → complete (`_task_helpers.py:107-137`), `GetTask(historyLength=10)` has non-empty history. On failure: xfail. | MAY |
| CORE-HIST-005/006 | `test_task_history.py:233-320` | After two **user** follow-ups "TCK history message 1/2" (`_task_helpers.py:140-187`), history contains both texts, in order. This passes only if user follow-up messages are persisted, by the server or by the SUT agent. On failure: xfail. | SHOULD |
| PUSH-DELIVER-001..003 | `core_operations/test_push_notifications.py:367-510` | Inline config on a blocking `tck-input-required` send, then a follow-up `tck-complete-task`. It waits up to 15 s for **any** webhook request, then checks the auth header and the `StreamResponse` body. Per-event and per-turn timing both pass. | MUST |

What the TCK does not check:
- the timing of blocking vs. returnImmediately;
- a follow-up stream starting with a Task;
- SendStreamingMessage closing at input-required;
- live-event ordering across several subscribers (only the snapshot);
- the policy for slow subscribers;
- push per event vs. per turn.

### 3. Event sequences the redesign must produce (JSON-RPC, `result` = StreamResponse)

- **New task, streaming:** `{task: WORKING}` → `{statusUpdate}`/`{artifactUpdate}`… → `{statusUpdate: INPUT_REQUIRED}` → *stream closes when `execute` returns* (Python; §11.7) → or … `{statusUpdate: COMPLETED}` → close.
- **Follow-up, streaming:** Python sends `{statusUpdate: WORKING}` … The spec wants `{task}` first (Q5); see decision D-4.
- **SubscribeToTask on an idle input-required task:** `{task: INPUT_REQUIRED}` (snapshot) → *stays open* → (another client's follow-up turn) `{statusUpdate: WORKING}` → … → `{statusUpdate: COMPLETED}` → close.
- **Blocking SendMessage:** returns `Task` (history trimmed to `historyLength`, all artifacts) at the first event whose resulting state is terminal or interrupted. If the turn ends without one, it returns at turn end with the stored state.

## Koog status

Current code (`a2a-server/src/commonMain/kotlin/ai/koog/a2a/server/…`), checked against Q1–Q14:

| Gap | Where | Affects |
|---|---|---|
| G1. A session is one `execute` call. When `agentJob` ends, `SessionManager` removes it and closes the processor. There is no per-task stream after an input-required turn. | `session/SessionManager.kt:76-93`; `session/Session.kt:65-68` | 3.5 (Q7, STREAM-SUB-001/002, STREAM-ORDER-002..004) |
| G2. `onSubscribeToTask` returns an **empty** flow when no session exists. There is no Task snapshot, no TaskNotFound and no terminal check. | `A2AServer.kt:588-597` | 3.5 (Q6–Q8) |
| G3. A late subscriber to a closed processor gets an immediate `Close` (`onSubscription`), and nothing combines a snapshot with live events. The race between reading state and subscribing is unhandled. | `session/SessionEventProcessor.kt:84-93` | 3.5 (Q6) |
| G4. Broadcast uses `MutableSharedFlow()` with no buffer, so one slow subscriber back-pressures the agent and every other stream. This doesn't violate Q9, but is the issue Python fixed in `0c2126f`. | `SessionEventProcessor.kt:84`, `:141` | 3.5 (quality) |
| G5. Blocking waits for the **end of the turn** (`lastOrNull`, then `agentJob.await()` and `eventCollectionFinished.join()`), not for the first terminal/interrupted state. It is correct only if the agent returns right after INPUT_REQUIRED; otherwise it hangs. Artifacts are included correctly. | `A2AServer.kt:458-460`, `:471-489` | 3.1 (Q1) |
| G6. `returnImmediately` takes the first agent event, which is fine for new tasks. On follow-ups it returns after the first event of any kind; the result is then re-read from storage (acceptable). | `A2AServer.kt:471-473`, `:481-488` | 3.1 (Q2) |
| G7. Stream start: a new task whose first event is not a `Task` fails inside storage (`TaskOperationException`), surfacing as a non-A2A error. A follow-up stream starts with whatever the agent sends. | `tasks/InMemoryTaskStorage.kt:95-96`; `A2AServer.kt:437-447` | 3.4 (Q5) |
| G8. Stream end: send streams end when the processor closes (terminal event, `SessionEventProcessor.kt:143-148`, or session teardown). This is consistent with "close at end of turn". A message-only turn closes after one Message (`:103-118`). | — | 3.4 (OK) |
| G9. A processor for an **existing** task starts in "no task event yet" mode, so an agent can answer a follow-up with a `Message`. | `SessionEventProcessor.kt:71`, `:103-108`; created at `A2AServer.kt:402-408` | 3.4 |
| G10. Cancelling an idle (e.g. input-required) task creates a fresh `SessionEventProcessor` that no subscriber is attached to. CANCELED is persisted but never streamed, so subscribers hang until timeout. | `A2AServer.kt:552-556` | 3.5, 3.10 |
| G11. If the executor throws, the exception goes only to the requester (`agentJob.await()`). The task stays non-terminal, so other subscribers never see a terminal state. | `A2AServer.kt:458` | 3.5 (Q7) |
| G12. Push is sent once per session, after the session ends. It carries a raw `Task` with `historyLength=0, includeArtifacts=false`, is skipped unless the first event was a `TaskEvent`, and errors are swallowed without logging. | `SessionManager.kt:95-110` | 3.13 |
| G13. User messages are never put into history by the server. Only `status.message` is moved into history on each status update, without dedup. `RequestContext.task` is shallow (`historyLength = 0`). | `InMemoryTaskStorage.kt:98-102`; `A2AServer.kt:395` | 3.14 |
| G14 (adjacent: 3.2/3.3). No terminal-task check and no contextId-mismatch check before a turn starts. | `A2AServer.kt:393-405` | 3.2, 3.3 |

What already works:
- Per-task turns are serialized: `tasksMutex.withLock(taskId)` plus `getSession(taskId)?.join()` (`A2AServer.kt:388-390`).
- The JSON-RPC transport turns an error thrown before the first emitted event into a plain JSON-RPC error (plan 3.17). Therefore `onSubscribeToTask` and the streaming handlers can simply throw TaskNotFound or UnsupportedOperation before they emit anything.

Callers outside the module use only `SessionEventProcessor.sendMessage` and `sendTaskEvent`:
- `agents/agents-features/agents-features-a2a-server/.../A2AAgentServerNodes.kt:27,49`
- examples
- `test-tck/.../TckAgentExecutor.kt`

`events`, `close`, `isOpen`, the constructor, `Session`, `LazySession` and `SessionManager` are used only inside `A2AServer` and `a2a-server` tests (`SessionManagerTest.kt`, `SessionEventProcessorTest.kt`, `TestAgentExecutor.kt`).

## Design options for the session redesign

Common rules for all options:
- "Turn" means one `execute()` call.
- The "task stream" is the per-task broadcast.
- Error types are the existing `A2A*Exception`s.

**Requirements every option must meet:**
- R-a. A per-task stream that outlives turns and is (re)created lazily from `TaskStorage`. It exists when a turn is running or there is at least one subscriber, and is dropped when neither holds.
- R-b. Atomic snapshot+subscribe, with the terminal/unknown checks done under the same lock that guards persist+broadcast.
- R-c. Persist and fan out under one per-task lock, so all subscribers see storage order.
- R-d. An in-band `TurnEnd(turnId)` marker published after the last event of a turn, so turn views can close without racing.
- R-e. On an executor exception, write FAILED if the task isn't terminal yet, then end the turn.
- R-f. Cancel goes through the task stream even when no turn is running.
- R-g. A push hook on every persisted `TaskEvent`.

### Option A (recommended): turn-scoped `Session` + per-task `TaskEventStream` inside `SessionManager`

Sketch (public changes ✱; everything else internal):

```kotlin
// internal: one per non-terminal task with a running turn or ≥1 subscriber
internal class TaskEventStream(taskId: String, taskStorage: TaskStorage, push: PushDispatcher?, bufferCapacity: Int) {
    private val mutex: Mutex                       // guards storage write + fan-out + subscriber set
    private val subscribers: MutableSet<Channel<StreamItem>>   // bounded; evict on overflow (D-5)
    suspend fun subscribeWithSnapshot(): Pair<Task, ReceiveChannel<StreamItem>>  // under mutex: get full task; null→TaskNotFound; terminal→UnsupportedOperation
    suspend fun subscribeTurnView(): ReceiveChannel<StreamItem>                  // no snapshot (registered before execute starts)
    suspend fun publish(event: Event)               // under mutex: storage.update / history merge (D-3), fan-out, push enqueue, Close on terminal
    suspend fun endTurn(turnId: Long, failure: Throwable?)  // writes FAILED if needed (R-e), fans out TurnEnd
}
internal sealed interface StreamItem { data class Data(val event: Event); data class TurnEnd(val turnId: Long, val failure: Throwable?); data object Terminal }

public class SessionEventProcessor ✱ internal constructor(...)  // keeps: contextId, taskId, sendMessage, sendTaskEvent
                                                                 // removes from public API: events, close, isOpen
                                                                 // starts in task mode when the task already exists (fixes G9)
public class Session ✱ (taskId, contextId, agentJob) {
    public val events: Flow<Event>   // this turn only: ends at its TurnEnd or Terminal
    public suspend fun join()
}
public class SessionManager ✱ (coroutineScope, taskStorage, pushConfigStorage?, pushSender?, subscriberBufferCapacity = 64) {
    public suspend fun startTurn(context: RequestContext<SendMessageRequest>, block: suspend (SessionEventProcessor) -> Unit): Session
    public fun subscribe(taskId: String, ctx: ServerCallContext): Flow<Event>   // snapshot + live, ends at Terminal
    public suspend fun <T> withTaskProcessor(taskId: String, block: suspend (SessionEventProcessor, Deferred<Unit>?) -> T): T  // cancel path (R-f)
    public suspend fun getSession(taskId: String): Session?
    public suspend fun activeSessions(): Int
}
```

How `A2AServer` uses it:
- **`onSendMessageStreaming`:** emits the turn view. For a follow-up whose first agent event is not a `Task`, it first emits the snapshot taken when the view was registered (D-4).
- **`onSendMessage`:** collects the turn view.
  - It stops at the first `Task` or `TaskStatusUpdateEvent` whose state is terminal or interrupted, or after the first `TaskEvent` when `returnImmediately` is set. It also stops at `TurnEnd`.
  - It then reads `taskStorage.get(taskId, historyLength, includeArtifacts = true)`.
  - The agent keeps running in the server scope.
- **`onSubscribeToTask`:** calls `sessionManager.subscribe(id, ctx)` after `checkStreamingSupport()`.

`AgentExecutor` contract changes (KDoc only, no signature change):
- `execute` returning ends the turn, and follow-ups on the same task queue behind it.
- The first event for a new task must be `Task` (or a single `Message`). For an existing task (`context.task != null`), send status/artifact updates; a `Message` is rejected.
- Exceptions mark the task FAILED.
- `cancel` always receives a processor bound to the task stream (`agentJob == null` when idle).

Trade-offs:
- \+ Smallest change. The agent-facing API, the existing turn serialization (`tasksMutex`) and the synchronous "persisted when `sendTaskEvent` returns" semantics all stay.
- \+ Maps one-to-one onto R-a..R-g.
- \+ Lazy re-creation means no leak for abandoned input-required tasks.
- − `SessionManager`'s public shape changes (`A2AServer.sessionManager` is `protected open`), and two locking layers (`tasksMutex` for turns, the stream mutex for events) have to be documented.
- − Single-process only, like Python's default mode.

### Option B: Python-style `ActiveTask` actor + `ActiveTaskRegistry` (replaces `Session`/`LazySession`/`SessionManager`)

```kotlin
public class ActiveTaskRegistry(scope, executor, taskStorage, pushConfigStorage?, pushSender?) {
    suspend fun getOrCreate(taskId: String, ctx: ServerCallContext, createIfMissing: Boolean): ActiveTask
}
public class ActiveTask {                     // one producer coroutine consuming Channel<TurnRequest>; per-task fan-out like Option A
    suspend fun submit(request: RequestContext<SendMessageRequest>): Flow<Event>  // turn view (ends at TurnEnd)
    fun subscribe(): Flow<Event>                                                 // snapshot + live until terminal
    suspend fun cancel(context: RequestContext<CancelTaskRequest>): Task
}
```

Trade-offs:
- \+ Closest to the reference; one owner for sequencing, persistence, push and cancel. `tasksMutex`/`cancelKey` double-locking goes away (`A2AServer.kt:341-346`, `:531-541`).
- \+ Easier to compare with Python when debugging interop.
- − Largest rewrite and a bigger public-API break.
- − If the consumer is decoupled the way Python does it, `sendTaskEvent` would return before persistence. Koog agents and `agents-features-a2a-server` currently get synchronous persistence and would need to keep it, which removes most of the producer/consumer benefit.

### Option C: storage-backed, versioned event log (Python cluster-mode direction)

- Add a versioned `TaskStorage` (compare-and-swap) and a pluggable `TaskEventStream` interface (publish, `subscribe(after = version)`), in the spirit of Python `cluster/event_stream.py:17-32`.
- Subscribers tail the log from their snapshot version.
- \+ Multi-replica support; exact snapshot/stream consistency through versions.
- − Reopens the storage interfaces that were just decided (state.md D1–D7). Not needed by the spec or the TCK. Much more work.
- Treat it as a later extension: Option A's internal `TaskEventStream` can become an interface later.

**Recommendation: Option A.** Take from Python:
- end-of-turn markers;
- FAILED on executor crash;
- the server forcing CANCELED on idle cancel (route to 3.10);
- dedup history by `messageId`;
- bounded per-subscriber buffers with eviction.

### Small decisions

**D-1 Blocking stop condition (3.1).**
- (a) Stop at the first terminal/interrupted state or at TurnEnd, as Python does.
- (b) Stop at end of turn only (current).

Recommend (a). Q1 says "wait until … reaches", and (a) also stops the call from hanging when an agent keeps running after INPUT_REQUIRED.

**D-2 SendStreamingMessage close point (3.4).**
- (a) At TurnEnd or terminal (Python, §11.7, topic doc).
- (b) Only at terminal (literal §3.1.2), which keeps the stream open while the task waits for input that this client has to send on another call.

Recommend (a). SubscribeToTask must still close **only** on terminal: STREAM-SUB-002 subscribes at input-required and expects the stream to stay open.

**D-3 History persistence (3.14).**
- (a) The server persists user messages, the Python way:
  - For a new task, if the agent's initial `Task` has no history entry with the user message's `messageId`, append it.
  - For a follow-up, before applying the turn's first non-`Task` `TaskEvent`, move `status.message` into history, append the user message, and clear `status.message`, unless a message with that `messageId` is already present.
  - Also dedup when the storage moves `status.message` into history.
- (b) The agent is responsible (current Koog; literal §3.7 L760).
- (c) (a) behind a server flag.

Recommend (a), or (c) with the flag on by default. It is spec-allowed (MAY), it is Python's behaviour, it makes CORE-HIST-005/006 (SHOULD) pass without SUT-specific code, and it lets agents stay simple.

Consequences of (a):
- Update the KDoc and example executors that put the user message in the initial `status.message` (`AgentExecutor.kt:38-49`, `A2AServer.kt:214-225`). Use `history = listOf(userMessage)`, otherwise dedup has to cover that path too.
- History logic now lives in the server (the stream's `publish`) instead of only in `TaskStorage.update`. With D2 already decided, the storage only needs to read and write the full task under the stream lock.

**D-4 First event of a follow-up stream (3.4).**
- (a) Emit the snapshot `Task` first if the agent's first event in the turn isn't a `Task`. This means peeking the first event of the turn view. It is spec-literal for Q5.
- (b) Pass events through unchanged, like Python.

Recommend (a). It costs little, and Koog's own client treats a leading `Task` as normal. The TCK doesn't test it.

**D-5 Slow subscribers.**
- (a) Bounded per-subscriber channel; on overflow, close that subscriber's flow with an error. This is Python main `0c2126f`.
- (b) Suspend (current back-pressure).

Recommend (a) with a configurable capacity. Q9 forbids one stream affecting others, and (a) honours that. §3.7 L762 allows missed updates on reconnect.

**D-6 Push timing (3.13).**
- (a) Every persisted `TaskEvent` (Task / status / artifact) as a `StreamResponse`. It goes to a per-task FIFO dispatcher coroutine, so webhook order matches event order and the agent is never blocked. Configs are looked up per event, so configs added mid-task are honoured. Failures are logged.
- (b) Only status changes, skipping artifacts.
- (c) One snapshot `{task}` at the end of each turn (close to the current behaviour, but wrapped).

Recommend (a). It matches §4.3.3 "when a task update occurs" and Python. All three pass PUSH-DELIVER-001..003.

No push for direct `Message` replies (Python behaviour). An inline config (3.12) must be saved under the pre-generated task id before the turn starts (Python `default_request_handler_v2.py:373-383`).

## Testability notes

Koog unit or integration tests at the `A2AServer` level, using a scripted `AgentExecutor` with `CompletableDeferred` gates (common/jvm tests, `runTest`):
- **SubscribeToTask**:
  - **After the turn ended:** subscribe after an input-required turn has returned. The first event equals the stored task. A follow-up turn from another coroutine reaches COMPLETED, and the subscriber receives its events and completes (STREAM-SUB-001/002).
  - **Fan-out:** two subscribers plus one send-stream get identical event lists (STREAM-ORDER-002/003). Cancelling one collector leaves the others running (STREAM-ORDER-004).
  - **Errors:** a terminal task gives `A2AUnsupportedOperationException` and an unknown task gives `A2ATaskNotFoundException`, both thrown before the first emission (STREAM-SUB-003/004; the JSON-RPC mapping is covered by `HttpJSONRPCServerTransportTest`).
  - **Snapshot race:** start a subscriber while the agent emits rapidly. Snapshot plus events must equal the final stored state, with no gap.
- **Blocking and `returnImmediately`**:
  - Blocking returns at INPUT_REQUIRED while the executor is still suspended (D-1).
  - `returnImmediately` returns while the executor is gated. A Message reply ignores the flag.
- **Streaming shape**: a follow-up stream's first event is a `Task` (D-4). A message-only stream has exactly one event.
- **Cancel and failure**:
  - Cancelling an idle input-required task gives subscribers CANCELED, after which they complete (G10).
  - An executor exception gives subscribers FAILED, after which they complete (G11).
- **Push**: a recording `PushNotificationSender` receives every TaskEvent in order, and a failing sender does not affect streams (D-6).
- **History**: GetTask history contains `[userMsg1, agentPrompt, userMsg2, …]` in order with no duplicate `messageId`s (D-3; CORE-HIST-004/005/006).
- **Slow subscribers**: a subscriber that never collects is evicted, and the others finish (D-5).

TCK coverage (Koog SUT, JSON-RPC):
- **Checked:** STREAM-SUB-001..004, STREAM-ORDER-001..004, CORE-SEND-001/002, CORE-HIST-001..006, CORE-MULTI-005/006, PUSH-DELIVER-001..003.
- **Not checked by the TCK:** blocking timing, follow-up Task-first, SendStreamingMessage close point, live-event equality across subscribers, slow-subscriber policy, push granularity. Koog tests must cover these.
- **SUT dependency:** the SUT needs the `tck-input-required` and `tck-complete-task` scenarios (Phase 4.1), and its executor must return after `INPUT_REQUIRED`.

## Discrepancies

1. **Blocking vs. "return immediately".** §3.1.1 L180 says SendMessage "MUST return immediately". §3.2.2 L446 and the proto (L155-160) make blocking the default. Python and the TCK follow §3.2.2 (already in plan §5).
2. **When a task stream closes.** §3.1.2 L210 and §3.1.6 L309 say terminal. §11.7 L2973 and `docs/topics/streaming-and-async.md:23` (explanatory) say terminal **or interrupted**. Python closes send-streams at end of turn and subscribe-streams only at terminal. The TCK (STREAM-SUB-002) requires subscribe-streams to survive input-required.
3. **Task-first for follow-up streams.** §3.1.2 L210 says "MUST begin with the Task". Python's follow-up streams start with `statusUpdate` (`default_request_handler_v2.py:476-479`, asserted by `test_scenarios.py:1929`). The TCK doesn't check it.
4. **Terminal-task error in Python v1.1.0** (pinned by Koog's client integration server) is `InvalidParamsError` rather than `UnsupportedOperationError` (`active_task.py:462-463, 600`@v1.1.0). Fixed on main (`6cce91b`, v1.2.x).
5. **History ownership.** Spec §3.7 L760 says "the agent is responsible to determine which Messages are persisted". Python's *SDK* persists user messages for the agent, and the TCK's SHOULD tests (CORE-HIST-005/006) only pass with that kind of persistence.
6. **Push timing.** The spec says "when a task update occurs" (§4.3.3 L844). The topic doc says "typically when a task reaches a significant state change" (`streaming-and-async.md:54`). Python pushes every task event.
7. **Snapshot/subscribe consistency.** Python taps before reading the snapshot, which can duplicate an event (`active_task.py:745-757`). The spec's stated goal (L311) is "prevents a potential loss of information"; duplicates are not addressed.

## Open questions

- **3.10 cancel semantics** (route to the 3.10 slice): should the server force CANCELED when the executor's `cancel` returns without a terminal state (Python `active_task.py:905-918`), instead of Koog's `TaskNotCancelable` (`A2AServer.kt:575-577`)? This matters for idle input-required tasks.
- **Task-replacement events on an existing task:** Python ignores them (`active_task.py:287-296`); Koog's storage replaces the whole task, history included (`InMemoryTaskStorage.kt:78-86`). This interacts with D-3. Decide to ignore, merge, or reject.
- **`RequestContext.task` shape:** it is currently shallow, with no history or artifacts (`A2AServer.kt:395`). Should agents get the full task, including the persisted user message?
- **Owner scoping:** SubscribeToTask and cancel should look the task up through the context-aware storage path (state.md D3), to avoid leaking the existence of other callers' tasks (Python #1159, `active_task_registry.py:79-103`).
- **Orphan inline push configs:** a turn that answers with a `Message` never creates the task, so an inline config saved under the pre-generated id is left behind. Python has the same issue. Delete it at TurnEnd?
- **Message-only turns and SubscribeToTask:** no task exists, so TaskNotFound is correct. Check that the turn stream is torn down immediately.
