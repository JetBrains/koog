# M8e: how to implement per-event push (3.13), inline push config (3.12), B6 and B7 on the current a2a-server, and which decisions still need the user

**Sources checked:**
- A2A spec repo @ `v1.0.1` (baseline; `main` = `1205f45`, v1.0.1-88). Spec text read with `git show v1.0.1:docs/specification.md`, so line numbers refer to that file. v1.0.1→main for the push sections is only the `PushNotificationConfig`→`TaskPushNotificationConfig` renames.
- a2a-python @ `a4c4fc9` (v1.2.2-13, `main`). Also checked `v1.1.0`, which is pinned by `a2a/test-python-a2a-server/pyproject.toml:7`; its push path is the same apart from the try/except guard.
- a2a-tck @ `263b9cf`.
- Koog `eugenethedev/a2a-1.0` @ `a67b56aac`.

**Confidence:** high on the spec, Python and TCK facts and on the current-code analysis. Medium-high on the design: the lock reasoning was done by hand, and nothing was implemented or run.

Unless stated otherwise, Koog paths are relative to `a2a/a2a-server/src/commonMain/kotlin/ai/koog/a2a/server/`. Abbreviations: TM = `turn/TurnManager.kt`, TES = `turn/TaskEventStream.kt`, TEP = `turn/TurnEventProcessor.kt`, S = `A2AServer.kt`. Locks: TL = `tasksMutex`, gate = `processorGate`, `[S]` = the stream lock, registry = `registryMutex`.

## Answer

Make push a side effect of **persistence** inside `TaskEventStream`:
- Right after `taskStorage.update(event)` succeeds, and still under `[S]`, the stream hands the event to a new internal per-task FIFO **`PushNotificationDispatcher`** owned by `TurnManager`. The hand-off is non-blocking and needs only a leaf lock.
- A worker coroutine for each task with pending events drains them in order. For each event it reads `getAll(taskId)` and calls `pushSender.send(config, event)`, holding no other lock.
- The worker exits when its queue is empty. Nothing about it is tied to a turn, so `monitorTurn` loses its push code.

B6 and B7 then follow:
- **B6:** the forced CANCELED is published with `stream.publish(event, turnId = null)` from inside `withTaskProcessor`, which persists, delivers to the turn view, and pushes. This lets `sendServerTaskEvent`, the `persistFollowUpMessage` parameter and push's use of `isTaskEventSent` go.
- **B7:** `Turn.join()` becomes `monitorJob.join()`.

The inline config is resolved and saved by `A2AServer` under the task lock, after validation and before `startTurn`, so the agent's first event is pushed:
- it is ignored when the card's `pushNotifications` is off or no storage is configured;
- `taskId` is set to the turn's task;
- the id comes from `IdGenerator` when absent (D5).

For a new-task turn that ends without a stored task (a Message reply, no events, or a crash), `deleteAll(taskId)` runs at the end of the agent job.

Ten behaviour/API questions remain (§Decisions). The most important:
- whether server-originated events (forced CANCELED, crash FAILED) are pushed. Python does **not** push them; the spec wording and the recorded 3.13 decision say "every persisted event".
- how to avoid duplicate configs when a client re-sends the same id-less inline config on every follow-up, as the Python client does.

## Requirements

| # | Requirement | Tier | Bindings | Citation |
|---|---|---|---|---|
| P1 | Webhook payload is a `StreamResponse` (exactly one of task/message/statusUpdate/artifactUpdate), sent "when a task update occurs" | MUST (payload); timing unspecified | all (webhooks are plain HTTP using the HTTP+JSON payload, so relevant to Koog) | spec §4.3.3 L844, L864-869; §3.5.1 L677 |
| P2 | `Authorization: {scheme} {credentials}` from `authentication` | MUST | all | §4.3.3 L873; §13.2 L3109 |
| P3 | At least one delivery attempt per configured webhook | MUST | all | §4.3.3 L884 |
| P4 | Retry with exponential backoff | MAY (§4.3.3 L885) **vs** SHOULD (§13.2 L3111): conflict inside the spec | all | see Discrepancies |
| P5 | Webhook timeout 10-30 s | SHOULD | all | §4.3.3 L886; §13.2 L3110 |
| P6 | After Delete, "No further notifications will be sent to the configured webhook" | MUST (part of the Delete contract) | all | §3.1.10 L402 |
| P7 | A config persists until task completion or explicit deletion | MUST | all | §3.1.7 L335 |
| P8 | The inline `SendMessageConfiguration.task_push_notification_config`: "Task id should be empty when sending this configuration in a SendMessage request" | SHOULD (client); the server must accept it | all; capability:pushNotifications | proto L147-149; example §6.6 L1638-1650 |
| P9 | Push config **operations** with the capability off → `PushNotificationNotSupportedError`. Nothing is said about the inline config. | MUST (CRUD only) | all | §3.3.4 L573 |
| P10 | `return_immediately` has no effect on push configs | normative statement | all | §3.2.2 L454 |
| P11 | Validate webhook URLs (SSRF: private ranges, localhost) | SHOULD | all | §13.2 L3113-3116; topic doc `streaming-and-async.md:83-84` (explanatory) |
| P12 | Event order within a stream is preserved | MUST, but for **streams** (§3.5.2), not push. Push ordering is unspecified. | all | §3.5.2 L681-683 |

## Details

### 1. What counts as a "persisted TaskEvent" (push trigger)

| Event | Persisted where | Push? |
|---|---|---|
| Agent's initial `Task` for a new task (after the user message is added to its history) | TES:184-190 → `persistAndDeliver` | **Yes**: the event as persisted (`created`), i.e. with the user message. Python pushes the stored task here (`active_task.py:233`, `:350-375`), and the Python e2e test expects `task(SUBMITTED)` then `statusUpdate(COMPLETED)` (`tests/integration/push_notifications/test_default_push_notification_support.py:180-193`). |
| `TaskStatusUpdateEvent` / `TaskArtifactUpdateEvent` from the agent | TES:193-199 → `persistAndDeliver` | Yes |
| The lazy follow-up history write (`persistUserMessage`) | TES:217-227 (a storage write, not an event) | No |
| An agent `Task` for an existing task (ignored) | not persisted (TES:176-182) | No. Python still pushes the existing task, because `handled_event = updated_task` (`active_task.py:233`); this is a minor divergence. |
| Rejected event (`taskStorage.update` throws `TaskOperationException`) | not persisted | No: enqueue only **after** `update` returns |
| `Message` reply | never persisted (TES:201) | No (decision 3.13; Python `push_notification_sender.py:10`) |
| Crash FAILED (`markFailed`) | TES:249-275 | Yes per 3.13, but see **Q1** (Python does not push it) |
| Forced CANCELED (after B6, through `publish`) | TES `persistAndDeliver` | Yes per 3.13, but see **Q1** |
| Follow-up stream's leading Task snapshot; subscribe snapshot | not persisted | No |

There are only two call sites: `persistAndDeliver` (TES:207-214) and `markFailed` (TES:271-273). Add one private helper `persisted(event)` that does `taskStorage.update(event); onPersisted(event)` and use it in both. The enqueue goes **before** `fanOut`. Then an event whose publisher is cancelled mid-fan-out is still pushed (TES:49-50 documents that such an event is persisted but may miss some subscribers). The enqueue must also be non-cancellable: either a non-suspending structure, or `withContext(NonCancellable)` around a leaf-lock section with no suspension inside. Otherwise a cancellation between `update` and the enqueue drops a persisted event.

### 2. Dispatcher

```kotlin
// turn/ or notifications/, internal
internal class PushNotificationDispatcher(
    scope: CoroutineScope,               // TurnManager.coroutineScope
    configs: PushNotificationConfigStorage,
    sender: PushNotificationSender,
) {
    private val lock = Mutex()                                   // leaf lock
    private val queues = mutableMapOf<String, ArrayDeque<TaskEvent>>()  // a key exists iff a worker runs

    /** Called under [S]. Never waits for anything but [lock]; no suspension inside the critical section. */
    suspend fun enqueue(taskId: String, event: TaskEvent) = withContext(NonCancellable) {
        val start = lock.withLock {
            val q = queues[taskId]
            if (q != null) { q.addLast(event); false }
            else { queues[taskId] = ArrayDeque(listOf(event)); true }
        }
        if (start) scope.launch(start = CoroutineStart.ATOMIC) { drain(taskId) }
    }

    private suspend fun drain(taskId: String) {
        try {
            while (true) {
                val event = lock.withLock {
                    queues[taskId]!!.removeFirstOrNull() ?: run { queues -= taskId; null }
                } ?: return
                dispatch(taskId, event)          // getAll + send to each config; failures logged
            }
        } finally {
            // e.g. scope cancelled on shutdown: drop the queue so the map doesn't leak.
            withContext(NonCancellable) { lock.withLock { queues -= taskId } }
        }
    }
}
```

- **Keyed by task id, not by stream instance.** A `TaskEventStream` instance is dropped when its holder count reaches zero and is recreated later (TM:421-442). If the dispatcher lived in the stream, a recreated instance could start a second worker for the same task and break FIFO. A worker is created when the first event of an idle task arrives and exits when its queue is empty, so idle tasks hold nothing.
- **Exit/enqueue race:** the empty check and the map removal happen in one critical section. A concurrent `enqueue` either appends before the check, or finds no key and starts a fresh worker. Per-task FIFO holds because only one worker exists per key, and the enqueue order is the `[S]` (persistence) order.
- **Locks:** new edge `[S]` → dispatcher lock. The dispatcher lock is a leaf: it is never held while acquiring anything or suspending on I/O, and HTTP and `getAll` run without it. This fits TL → gate → {registry | `[S]`} and "registry never waits on `[S]`", since the registry is not involved at all. The worker never takes `[S]` or the gate, so a slow webhook never blocks the agent, streams, cancel or turn start, and never blocks other tasks. Add this edge to TM's "Lock order" KDoc (TM:59-64).
- **Configs are read per event at dispatch time** (`getAll(taskId)`, the context-free internal method, `notifications/PushNotificationConfigStorage.kt:89`). This is required for P6 (deleted ⇒ no further sends) and lets configs added mid-task take effect. Side effect: a config created while events are still queued also receives those older events (Q4). Python does the same: `get_info_for_dispatch` per event (`base_push_notification_sender.py:77`).
- **Fan-out per event:** send to all configs of the event (sequentially or concurrently, Q5), and finish every config of event N before starting event N+1. Per-config order then equals event order. Catch `Exception` per config (rethrow `CancellationException`) and log. `SimplePushNotificationSender` already swallows (`notifications/SimplePushNotificationSender.kt:90-94`), but custom senders may not, and Python guards the same way (`active_task.py:361-375`).
- **Back-pressure:** the queue is unbounded per task (Q6). Memory is bounded by the events of one task between webhook round-trips. Bounding it would push webhook latency into `[S]`, i.e. into the agent and every stream of the task. Python effectively does that: the consumer awaits the push before enqueueing to subscribers (`active_task.py:233-238`, `:350-375`).
- **Shutdown:** workers are children of `coroutineScope`. `A2AServer.cancel()` (S:903-905) cancels them, and queued pushes are dropped. `ATOMIC` + `finally` guarantee map cleanup even when the scope is already cancelled. This is the same lesson as the C3b `join()` hang. There is no drain API (Q7).
- **Construction:** `TurnManager` creates the dispatcher only if both `pushSender` and `pushConfigStorage` are non-null. It passes `onPersisted = { dispatcher.enqueue(taskId, it) }` (or `null`) into each `TaskEventStream` it creates (TM:423). TES keeps no reference to configs or the sender. Optional: `A2AServer` passes `pushSender.takeIf { agentCard.capabilities.pushNotifications == true }`, which saves a `getAll` per event when the capability is off. There can be no configs then anyway: CRUD fails with -32003 (S:886-896), and the inline config is ignored.
- **Remove** `TurnManager.sendPushNotifications` and the `isTaskEventSent` check (TM:290-310). Rewrite the KDoc "Push notifications" section (TM:88-91).

### 3. B6: forced CANCELED straight to the stream

- Today: `eventProcessor.sendServerTaskEvent(...)` (S:735-744) → TEP:138, `send(persistFollowUpMessage = false)` (TEP:140-170).
- Change: `withTaskProcessor` (TM:372-401) already holds the gate and the pinned `stream`. Expose a server-only publish to the block (shape: Q8) that calls `stream.publish(event, turnId = null)`. With `userMessage`/`followUpMessage` left `null`, `publish` takes the `is TaskEvent` branch (TES:193-199) with no follow-up persistence, then `persistAndDeliver`. That path:
  - persists the event;
  - pushes it (new);
  - delivers it with filter `{ true }`, so the **running turn's view gets CANCELED** and the requester's blocking or streaming call ends with it (the C3 guarantee, tested by `A2AServerCancelTest.kt:177-192`, `:195-208`);
  - then `closeAll()`.
- The turn's pending follow-up message stays unpersisted, because only TEP's `send` consumes `pendingFollowUpMessage`. The C6/D-3 invariant holds by construction.
- Delete `sendServerTaskEvent` (TEP:134-138). Fold `send(event, persistFollowUpMessage)` back into `sendTaskEvent`, with `followUpMessage = pendingFollowUpMessage.takeIf { event !is Task }` (TEP:155).
- `isTaskEventSent` stays: it still enforces "no Message after a TaskEvent" (TEP:103). Only its push use goes away.
- Safety:
  - the agent job was `cancelAndJoin`ed (S:731) before the publish;
  - the gate is held, so no turn starts or ends meanwhile;
  - the turn's processor stays open, but nothing calls it;
  - the turn ends normally later via `monitorTurn` → `endTurn`, and its view is already closed by `closeAll`.

### 4. B7: `Turn.join()` = monitor job

- Today `Turn.completion` (Turn.kt:27, :45-47) is completed in `monitorTurn`'s `finally` (TM:286-288), **before** the push (TM:290-292), so that follow-ups don't wait for webhooks. Once push leaves the monitor (§2), that reason disappears.
- Change: `monitorTurn` takes the pieces it uses (`agentJob`, `processor`, `taskId`, `contextId`, `stream`, `turnId`) instead of `Turn`. Launch it `ATOMIC` (as now, TM:228-231) **before** constructing `Turn`, and pass the `Job` into `Turn` (`internal val monitor: Job`; `join() = monitor.join()`).
- Launching before the `turns[taskId] = turn` insert is safe. The monitor can only end the turn under the gate, and `startTurn` holds the gate until after the insert (TM:177-235). An early agent event publishes to the already-registered view.
- Do **not** use `CoroutineStart.LAZY` to get the job first: a lazy child of a cancelled scope never runs its body, which brings back the shutdown hang.
- Remove `completion`, the `CompletableJob` import, and the `finally { completion.complete() }`. `A2AServer`'s `getTurn(taskId)?.join()` under TL (S:440) then waits for the full teardown (`endTurn` + `release`), and for the orphan cleanup if Q9(b) puts it in the monitor.

### 5. Inline config (3.12)

**Where and when.** In `onSendMessageCommon`, under TL, after validation (S:443-461), once `taskId` and `contextId` are resolved (S:463-465), and **before** `startTurn` (S:470):
```kotlin
val inline = request.configuration?.taskPushNotificationConfig
val storedInline = if (inline != null && agentCard.capabilities.pushNotifications == true && pushConfigStorage != null) {
    // validatePushNotificationUrl(inline.url)  -- only if Q10 adds a hook
    val withTask = inline.copy(taskId = taskId)            // Q3: override, Python does too
    pushConfigStorage.save(if (withTask.id.isNullOrBlank()) withTask.copy(id = idGenerator.generatePushNotificationConfigId(withTask)) else withTask, ctx)
} else null                                                // capability off / no storage: silently ignored (decision 7)
```
- **Before the agent starts** means the initial `Task` event is already pushed to it. Dispatch reads configs after persistence, so "saved before `startTurn`" is enough.
- It can't be atomic with task creation: the agent creates the task.
- Python saves at the same point, before `get_or_create` starts the producer (`default_request_handler_v2.py:373-383`). Python saves even before the terminal-task check, while Koog validates first, so a rejected request stores nothing.
- Use the request `ctx` in `save`, so owner-scoped storages see the caller (D3).
- Wrap the save in a `try` together with `startTurn`. If `startTurn` throws for a **new** task (practically only on cancellation), call `deleteAll(taskId)`. For a follow-up, leave it: the task exists and P7 lets configs outlive anything else.

**Follow-ups.** An inline config on a message to an existing task is upserted for that task. Python does this: `set_info(task_id, …)` with the resolved id (`default_request_handler_v2.py:361-383`). The Python client attaches its configured push config to **every** send (`src/a2a/client/base_client.py:105-111`). See Q2 for the duplicate-id problem.

**Orphan cleanup** (recorded decision). A **new-task** turn (`context.task == null`) that saved an inline config and ends with no stored task gets `pushConfigStorage.deleteAll(taskId)`. "Ends with no stored task" covers a Message reply, an agent that sent nothing, and a crash before the first Task.
- **Never** do this for follow-ups: it would delete the task's other configs.
- Test "task absent" with `taskStorage.get(taskId, 0, false) == null`, not with "a Message was sent". It covers all three cases, and no race exists: the id is fresh, the turn is over, and TL serializes the next turn.
- No pushes can be pending for that id, because nothing was persisted.
- Placement is Q9.

**Capability off → silently ignore** (decision 7). Also ignore it when the capability is on but `pushConfigStorage == null`. That matches how CRUD treats a missing storage as "not supported" (S:891-893), but on SendMessage it doesn't error. See Discrepancies for how this differs from Python.

### 6. Event sequence example (TCK PUSH-DELIVER flow, blocking)

`send(inline cfg, "input-required")`:
1. config saved;
2. agent `Task(SUBMITTED)` persisted → enqueue → webhook `{"task":…}`;
3. `statusUpdate(INPUT_REQUIRED)` → webhook `{"statusUpdate":…}`.

Then the follow-up `send("complete-task")`:
1. the user message is lazily persisted (no push);
2. `statusUpdate(COMPLETED)` → webhook.

The TCK accepts the first request it receives (`tests/compatibility/core_operations/test_push_notifications.py:370-406`, `:435`).

## Koog status

- **Push timing:** one push per turn, after teardown, with a raw `Task` read with `historyLength = 0, includeArtifacts = false`. It is sent only if `isTaskEventSent` (TM:290-310). Gaps:
  - no per-event pushes;
  - none for an idle-task cancel (`withTaskProcessor` never pushes);
  - none for a crash FAILED in a turn whose agent sent nothing;
  - no ordering guarantee relative to the next turn.
- **Payload, sender and storage are already v1:**
  - `PushNotificationSender.send(config, event: Event)` (`notifications/PushNotificationSender.kt:24`);
  - `SimplePushNotificationSender` serializes with `EventSerializer` as a StreamResponse, sets `Authorization: <scheme> <credentials>` and the Python `X-A2A-Notification-Token`, uses a 15 s timeout, and swallows and logs failures (`notifications/SimplePushNotificationSender.kt:66-95`);
  - storage has `getAll`/`deleteAll` (`notifications/PushNotificationConfigStorage.kt:89-97`).
- **Inline config:** ignored. `SendMessageConfiguration.taskPushNotificationConfig` exists in core (`a2a-core/.../model/Requests.kt:45`), and `TaskPushNotificationConfig.taskId` is nullable (core KDoc :9-14), but `onSendMessageCommon` never reads it.
- **B6 surface:** `sendServerTaskEvent` (TEP:134-138), used only at S:735. **B7 surface:** `Turn.completion` (Turn.kt:27, :45-47; TM:216, :286-288).
- **Tests to rewrite:** `TurnManagerTest.testTurnWithPushNotifications` asserts the old after-turn raw-Task push (`a2a-server/src/commonTest/.../turn/TurnManagerTest.kt:499-524`).

## Decisions (numbered; options, recommendation, evidence)

**Q1. Push server-originated events (forced CANCELED, crash FAILED)?**
- (a) Yes, every persisted TaskEvent. This is the literal recorded 3.13. It also matches spec §4.3.3 "when a task update occurs" (L844): a CANCELED or FAILED is a task update, and the webhook client otherwise never learns of the terminal state.
- (b) Agent events only (Python). Python writes the forced CANCELED (`active_task.py:905-918`) and the producer-crash FAILED (`:665-680`) straight to the task manager, bypassing the consumer, so neither is pushed. Only the FAILED from a failing `executor.cancel()` goes through the queue and is pushed (`:986-1003`).
- **Recommend (a)**: spec-aligned, and it falls out of §1 with no special cases.

**Q2. Duplicate inline configs on follow-ups.** A client that re-sends the same id-less inline config on every message (the Python client does, `base_client.py:105-111`) gets a new UUID per send under D5, so N follow-ups mean N webhook calls per event. Python avoids this because an id-less config defaults to `id = taskId` (`push_notification_config_store.py:12-21`), so it upserts.
- (a) Accept the duplicates (literal D5).
- (b) For **inline** configs only, skip the save if the task already has a config equal ignoring `id`/`tenant` (one `getAll` read). D5 stays as it is.
- (c) For inline configs, default the id to the task id (Python). This conflicts with D5's "via IdGenerator", although a user `IdGenerator` can already do it, since it receives the config with `taskId` filled.
- (d) Ignore inline configs on follow-ups altogether. The spec topic doc mentions the inline config only for "the initial" request (`streaming-and-async.md:50-52`, explanatory), but Python honours it on follow-ups.
- **Recommend (b).** Keep follow-up support (Python parity).

**Q3. Inline config with a client-set `taskId`** (the proto says it SHOULD be empty, L148).
- (a) Overwrite it with the turn's task id (Python, `push_notification_config_store.py:18`).
- (b) A mismatch → `InvalidParams`.
- **Recommend (a).**

**Q4. Configs created while events are queued.**
- (a) Read configs at dispatch time. Simple; satisfies P6. A brand-new config may also receive a few events persisted just before it was created.
- (b) Snapshot configs at enqueue time. That needs a storage read under `[S]` per event, which is slow and blocks streams.
- **Recommend (a).** The spec tells clients to be idempotent (L878).

**Q5. Fan-out across the configs of one event.**
- (a) Concurrently (Python `asyncio.gather`, `base_push_notification_sender.py:93-97`), then the next event.
- (b) Sequentially.
- **Recommend (a).** One slow webhook doesn't add its latency to the others for that event, and per-config order is unchanged.

**Q6. Queue bound.**
- (a) Unbounded per task (the agent is never blocked by webhooks; the memory grows only while webhooks lag).
- (b) Bounded, back-pressuring under `[S]`. This is Python-like and D-5-like, but webhook latency then stalls the task's agent and streams.
- (c) Bounded, dropping the oldest with a log. This violates P3 for the dropped events.
- **Recommend (a)**, documented.

**Q7. Retries, timeout, shutdown.**
- Retries: the spec says MAY (L885) or SHOULD (L3111); Python doesn't retry.
- **Recommend:** no retry in the dispatcher. Retries belong in the `PushNotificationSender` implementation, which is already pluggable. The dispatcher awaits it, so a retrying sender keeps per-task order. Keep the 15 s timeout (P5 ✓).
- Shutdown: drop queued pushes when `coroutineScope` is cancelled. Do not add a drain API now; Python's `aclose` also force-cancels (`default_request_handler_v2.py:186-193`).

**Q8. Shape of the server-only publish for B6** (all options are `@InternalA2AApi` or internal).
- (a) A third `withTaskProcessor` block parameter, `publish: suspend (TaskEvent) -> Unit`, bound to the pinned stream.
- (b) An internal `TurnEventProcessor.publishToStream(event)` that bypasses `send`. It is cheapest, but the processor stays the path, which B6 wanted to remove.
- (c) Move the whole "force CANCELED if not terminal" step into a `TurnManager` method. Cleanest, but larger, and it moves cancel policy out of `A2AServer`.
- **Recommend (a).**

**Q9. Where the orphan cleanup runs.**
- (a) In `A2AServer`'s agent block: `startTurn(rc) { p -> try { execute(rc, p) } finally { withContext(NonCancellable) { cleanupIfNoTask() } } }`. No `TurnManager` change; logged, never rethrown. It doesn't run if the scope was already cancelled before the agent started, which is shutdown only.
- (b) In `monitorTurn` after the gated teardown, which needs a flag passed to `startTurn`.
- **Recommend (a).** All inline-config logic stays in one place.

**Q10. Webhook URL validation hook** (P11 SHOULD; Python has an optional `push_url_validator` → `InvalidParams` on Create and on SendMessage, `default_request_handler_v2.py:178-183`, `:376-378`, `:510`).
- (a) Out of M8e scope.
- (b) A `protected open suspend fun validatePushNotificationConfig(config)` on `A2AServer`, a no-op by default, called for Create and for the inline config.
- A default-on SSRF filter would break the TCK, whose webhook host defaults to `localhost` (`tests/compatibility/conftest.py:72-73`).
- **Recommend (b)** if cheap, otherwise (a).

Also confirm, implied but not yet explicit: push is internal and not pluggable beyond `PushNotificationSender` and `PushNotificationConfigStorage`, i.e. no public dispatcher interface. **Recommend: internal.**

## Suggested slices

| Slice | Content | Tests |
|---|---|---|
| **M8e-1** Dispatcher + hook (3.13) | `PushNotificationDispatcher` (internal); `onPersisted` callback in TES (`persistAndDeliver`, `markFailed`; before `fanOut`; non-cancellable enqueue); wiring in TM; remove `sendPushNotifications` + `isTaskEventSent` push use; KDoc (lock order, "Push notifications") | New `PushNotificationDispatcherTest`: FIFO per task with a gated sender; a stuck task A doesn't delay task B; a throwing sender doesn't stop later events or other configs; the worker map is empty after drain (internal counter); a config deleted between events gets no later event; a scope cancelled with a queue leaves no worker. `TurnManagerTest` (replacing `testTurnWithPushNotifications`): a recording sender gets `[Task(with user msg), status, artifact, COMPLETED]` in order; a Message reply gets nothing; an ignored Task replacement gets nothing; a crash FAILED is pushed; a blocked sender doesn't block the agent, streams, or `join()`. |
| **M8e-2** B6 + B7 | Server publish in `withTaskProcessor` (Q8); delete `sendServerTaskEvent` / `persistFollowUpMessage`; `monitorTurn` takes its parts; launch the monitor before `Turn`; `Turn.join()` = monitor job; remove `completion` | Existing `A2AServerCancelTest` (esp. :177-208, :223, :279 race) and `TurnManagerTest` must stay green. New: forced CANCELED of a running **follow-up** turn whose agent sent nothing → the stored history doesn't contain the follow-up user message (no test covers this today); forced CANCELED (idle and running) is pushed; `join()` returns after teardown; the shutdown no-hang test still passes. |
| **M8e-3** Inline config (3.12) + orphan cleanup | §5 in `onSendMessageCommon`; Q2/Q3/Q9/Q10 per user answers; KDoc for `onSendMessage`/`onSendMessageStreaming` | `A2AServerPushNotificationsTest`: inline config saved with the generated id and `taskId`, and the first `Task` event pushed to it (blocking send ending at INPUT_REQUIRED); a follow-up inline config is stored for the existing task (plus the Q2 dedupe); capability off → no config, no error; storage `null` → ignored; Message reply → `getAll` empty; crash before a Task → empty; TaskNotFound / terminal-task validation failure → nothing stored; a follow-up Message-mode turn never deletes existing configs. Optionally one JSON-RPC integration test with the Ktor `MockEngine` sender. |

TCK PUSH-DELIVER-001..003 run only if the SUT declares `pushNotifications` (state.md open question 8 / plan 4.2). They need M8e-1 + M8e-3 and a real `SimplePushNotificationSender` in the SUT. Both per-event and per-turn timing pass them, so Koog's own tests must pin the timing.

## Testability notes

- **TCK** (`tck/requirements/push_notifications.py:152-191`; `tests/compatibility/core_operations/test_push_notifications.py:367-510`): it checks only that ≥1 webhook request arrives within 15 s, the exact `Authorization` header, and StreamResponse schema validity. It uses an inline config **with** an `id` on a blocking `input-required` send, then a follow-up without config. Not tested: per-event timing, ordering, the initial Task push, server-originated events, the capability-off inline path, orphan cleanup, delete-stops-push.
- **Unobservable:** the dispatcher is internal, so FIFO and worker cleanup need internal test access (`internal` visibility is enough in `commonTest`). Use gated fake senders (`CompletableDeferred`), not timing.

## Discrepancies

1. **Spec internal:** retries are MAY in §4.3.3 (L885) but SHOULD in §13.2 (L3111).
2. **Spec vs Python, server-originated events:** Python does not push the forced CANCELED or the producer-crash FAILED (`active_task.py:665-680`, `:905-918`). The spec says push "when a task update occurs". (Q1.)
3. **Python inline config vs "capability off → ignore":** Python saves the inline config whenever a `push_config_store` exists and dispatches whenever a `push_sender` exists, **without checking `capabilities.push_notifications`** (`default_request_handler_v2.py:373-383`; `active_task.py:350-355`). So decision 7 is not literally "Python behavior". It matches Python only when the deployment wires no store when the capability is off. Decision 7 stands; it is noted only so the KDoc doesn't claim Python parity.
4. **Content-Type:** the spec example uses `application/a2a+json` (L851). Koog's sender (`SimplePushNotificationSender.kt:85`) and Python (httpx `json=`, `base_push_notification_sender.py:143-147`) send `application/json`. The TCK doesn't check it.
5. **Python pushes the existing task when an agent's Task replacement is ignored** (`active_task.py:233`); Koog won't push anything for it.

## Open questions

- Should push configs be deleted after a terminal state? P7 allows it, Python doesn't, and D6 says terminal tasks aren't rejected by CRUD. Recommend not deleting, which is outside M8e anyway.
- Should the inline config's or Create's `url` be checked for blank (the proto marks `url` REQUIRED, L479)? Koog's Create doesn't check it today. This is a small validation item for 3.11/3.12.
- Content-Type `application/a2a+json` for webhooks (Discrepancy 4): a sender-contract question for 3.13, routed separately.
- Phase 4: does the SUT declare `pushNotifications` (state.md question 8)?
