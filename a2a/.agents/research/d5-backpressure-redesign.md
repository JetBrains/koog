# D-5 revisited: back-pressure vs. bounded buffers with eviction in the Koog A2A server

**Sources checked:** Koog `eugenethedev/a2a-1.0` @ `9c7a104fb`; A2A spec repo @ `98279d6` (main, `v1.0.1-89`), baseline tag `v1.0.1` (`docs/specification.md`, no relevant diff to main); a2a-python @ `261c074` (main, `v1.2.3-6`), eviction commit `0c2126f` (first released in `v1.1.4`; Koog's Python test server pins `a2a-sdk==1.1.0`, which predates it); a2a-tck @ `d4d6fae` (research checkout) and the Koog pin `263b9cf` (same multi-stream test and requirements).
**Confidence:** high for the Koog mechanics, the Python behavior and the spec text; medium for the design trade-offs (interpretation) and for how much kernel/Ktor buffering sits in front of a stalled SSE client (not measured).

## Answer
- **Today.** Every listener of a task gets its own rendezvous channel. The fan-out runs under the per-task stream lock [S] and suspends on each channel in turn. A remote reader that is still connected but stalled freezes everything on that task: the agent, every other stream, CancelTask, follow-up turns, new SubscribeToTask calls, turn teardown (C1) and the graceful drain. Only a forced shutdown (D8) or the client's connection dying ends the freeze. The A4 heartbeat detects closed or reset connections. It does not detect a stalled one.
- **Push** has a per-task queue with no bound. A slow webhook never holds back the agent or the streams. It can only grow memory.
- **Spec.** The spec says nothing about slow consumers. Closing a lagging stream with an error is compatible with §3.5.2 and §3.7, and over JSON-RPC the natural signal is a final SSE error event with `-32603`.
- **Python main** evicts subscriber sinks instantly once 1024 events are queued (hard-coded). The requester's streaming response and the blocking collector count as subscriber sinks and can be evicted too. The evicted stream **ends silently, with no error**. The agent is never blocked by streams, but a slow push webhook does block it, because push is sent inline. The TCK does not test any of this.
- **Recommendation.** Use a bounded per-listener buffer. When it is full, wait a bounded time, then evict with `-32603`. Apply this to SubscribeToTask streams and to the streaming requester view. Keep the blocking/non-streaming SendMessage collector suspending (exempt). Make capacity and timeout configurable (H5 shape).

## Requirements
| # | Requirement | Tier | Bindings | Citation |
|---|---|---|---|---|
| R1 | Deliver events in the order they were generated; no reordering | MUST | all | spec v1.0.1 §3.5.2, `docs/specification.md:683` |
| R2 | Broadcast events to all *active* streams of a task | MUST, capability:streaming | all | §3.5.2 `:691` |
| R3 | Every stream receives the same events in the same order | MUST, capability:streaming | all | §3.5.2 `:692` |
| R4 | Closing one stream must not affect the other active streams (Q9) | MUST, capability:streaming | all | §3.5.2 `:693` |
| R5 | The task lifecycle is independent of any single stream's lifecycle | stated behavior (no RFC keyword) | all | §3.5.2 `:694` |
| R6 | Streaming clients MAY miss status updates across a disconnect and reconnect; messages are not reliable delivery | MAY (client expectation) | all | §3.7 `:762` |
| R7 | SubscribeToTask's first event is the current Task, "to prevent loss of information" | MUST, capability:streaming | all | §3.1.6 `:311` |
| R8 | Streams close at a terminal state | MUST | all | §3.1.2 `:210`, §3.1.6 `:309` |
| R9 | System errors map to JSON-RPC `-32603` | example mapping (SHOULD use appropriate codes) | JSON-RPC | §3.3.2 `:531-536`, §9.5 `:2447` |
| R10 | Webhook requests SHOULD time out (10–30 s); at least one delivery attempt per config | MUST (attempt) / SHOULD (timeout) | push (plain HTTP) | §4.3.3 `:884-886`, §13.2 `:3110` |

None of these requires or forbids buffering, dropping or evicting. All of them apply to Koog's JSON-RPC binding.

## Details

### 1. Current mechanics (Koog HEAD)

**Where fan-out happens.**
- `TaskEventStream.publish` takes [S] (`TaskEventStream.kt:166-170`).
- `persistAndDeliver` (`:204-215`) first calls `persist`, which runs `taskStorage.update` and then `onPersisted` (push enqueue), NonCancellable (`:222-225`).
- It then calls `fanOut` (`:294-306`), which loops over the subscribers in sequence and calls `deliver` = `channel.send` (`:314-324`).
- A closed or cancelled channel returns `false` and is removed (`:301-304`). A terminal event runs `closeAll()` in a `finally` (`:210-213`).
- Each channel is `Channel(RENDEZVOUS)` (`:289`).
- `endTurn`/`markFailed` (`:240-274`) also fan out under [S].

**Locks held while a delivery is suspended.**

| Path | Locks held during `send` |
|---|---|
| Agent `sendTaskEvent` / `sendMessage` | processor `turnMutex` (`TurnEventProcessor.kt:99,135`) → [S] |
| CancelTask through the processor | gate (`TurnManager.kt:709`) → `turnMutex` → [S] |
| CancelTask forced CANCELED (`publish`) | gate → [S] (`TurnManager.kt:722-726`, `A2AServer.kt:788`) |
| Turn teardown (`monitorTurn`) | gate, NonCancellable (`TurnManager.kt:552-566`), → [S] in `endTurn` → `markFailed` (C1). The turn slot stays held until `:580`. |

The registry lock is never held around [S] (`TurnManager.kt:82-88`, `:838-862`). The push queue lock is a leaf lock (`PushNotificationDispatcher.kt:45-71`).

**Listener kinds.**

| Listener | Created | Channel | Consumer |
|---|---|---|---|
| Requester turn view, streaming | `openTurnView` (`TaskEventStream.kt:136`, `TurnManager.kt:384`) | rendezvous | `Turn.events` (`Turn.kt:65`) → `emitTurnEvents` (`A2AServer.kt:506-553`) → JSON-RPC `produceIn(RENDEZVOUS)` (`JSONRPCServerTransport.kt:155`) → Ktor `SSEServerContent.send` (`Routes.kt:133-139`) → socket |
| Requester turn view, blocking/`returnImmediately` SendMessage | same view | rendezvous | in-process `firstOrNull` (`A2AServer.kt:626-635`). The view is cancelled right after the response event (`A2AServer.kt:486`, `Turn.kt:68-71`). |
| SubscribeToTask subscriber | `stream.subscribe` (`TaskEventStream.kt:120-130`, `TurnManager.kt:638`) | rendezvous | `for (event in channel) emit` (`TurnManager.kt:647-652`) → transport → SSE |
| Push | `onPersisted` → `dispatcher.enqueue` (`TurnManager.kt:840-842`) | unbounded `ArrayDeque` per task | per-task worker, no lock held (`PushNotificationDispatcher.kt:91-145`) |

The blocking collector is not a separate listener. It is the turn view consumed in-process, so it is never a stall source in practice.

**What one stalled listener S blocks today.**

| Party | Blocked? | Why |
|---|---|---|
| Agent `sendTaskEvent`/`sendMessage` | yes | suspended in `send` to S, under `turnMutex` and [S] |
| Other listeners of the task | yes | those after S in the list miss the current event until S takes it; all of them miss later events (sequential `fanOut`, `:297-305`) |
| Push of the stalled event | no | enqueued before the fan-out (`:224`) |
| Push of later events | delayed | the agent can't produce them |
| CancelTask | yes | `AgentExecutor.cancel` sending through the processor waits for `turnMutex`. A `cancel` that runs `agentJob.cancelAndJoin()` first unblocks the agent, because `send` is cancellable (`:317-321`). The forced CANCELED `publish` then waits for S under the gate (`A2AServer.kt:780-797`). The CancelTask HTTP call hangs. |
| Follow-up `startTurn` | yes | waits for the turn slot (`TurnManager.kt:362`), which is released only after teardown (`:580`); `openTurnView` also needs [S] |
| New SubscribeToTask | yes | `stream.subscribe` needs [S]; the client gets no snapshot |
| Monitor teardown / `markFailed` (C1) | yes, cannot be interrupted | NonCancellable under the gate (`TurnManager.kt:552-566`) |
| `Turn.join()` | yes | waits for the monitor |
| Graceful drain | yes, until the grace deadline | `admittedTurns` doesn't reach 0 (`TurnManager.kt:783-787`); the deadline then forces `shutdown()` (`:777-779`) |
| Forced shutdown | no | cancels every view and subscriber channel lock-free before the jobs (`TurnManager.kt:806-818`); the pending `send` fails (D8, test `A2AServerShutdownTest.kt:286`) |
| Other tasks, GetTask, ListTasks, `getTurn` | no | `TurnManagerTest.kt:1042` |

**What is already mitigated.**
- **Client that closed (FIN/RST).** The A4 heartbeat (`Routes.kt:84-89,135`, default 15 s) makes a write fail. Ktor cancels the call, the flow is cancelled, then `unsubscribe` (`TurnManager.kt:656`) or `cancelEvents` (`A2AServer.kt:486`) runs and the fan-out continues. Measured latency is about 1–5 heartbeat periods (A4 report).
- **Shutdown.** The forced phase bounds every stall (C2/D8).

**What remains.**
- **Stalled but alive client** (full TCP window, a client that stops reading, a proxy that buffers). The heartbeat write suspends behind the same session mutex (A4 report, Ktor `DefaultServerSSESession`), so nothing is detected. The stall lasts as long as the client does.
  - *Hypothesis, not measured:* the fan-out only stalls after the kernel send/receive buffers and Ktor's write channel are full, plus one element in `produceIn`. That is probably hundreds of small events.
- **Silent peer loss** until TCP gives up, and the Netty RST-with-unread-data case (`Routes.kt:86-88`).
- **Slow in-process consumer** of `onSendMessageStreaming`/`onSubscribeToTask` (a custom transport or subclass). The only in-repo consumer is the JSON-RPC transport.
- **C1.** A stalled requester holds the gate NonCancellable in teardown until it goes away or the server shuts down.

### 2. Push dispatcher
- **No bound.** The queue is unbounded per task (`PushNotificationDispatcher.kt:23,49,58-78`).
- **Never blocks the agent.** `enqueue` takes only the leaf lock and is NonCancellable. Sending runs with no lock held (`TurnManager.kt:150-153`, test `TurnManagerTest.kt:956` `testBlockedPushSenderDoesNotBlockAgentStreamsOrTurnEnd`).
- **Per-request timeout.** `SimplePushNotificationSender` uses a 15 s timeout (`SimplePushNotificationSender.kt:29-61`), which is within the spec's 10–30 s recommendation (§4.3.3).
- **Cost.** If a webhook is slower than the event rate, the queue grows without limit. The graceful drain waits for it until the deadline.
- **Different from Python,** which blocks the agent on push (see §4).

### 3. Spec position
- **Slow consumers.** No text in v1.0.1 (or main) covers slow or lagging consumers, buffering, dropping or eviction. A grep for slow/lag/buffer/drop/evict/back-pressure found none.
- **Q9 (R4)** covers *closing* a stream. Neither back-pressure nor eviction literally violates it.
- **Back-pressure vs. R5 and "independently subscribe" (`:687`).** Under back-pressure a stalled stream does affect the other streams and the task's progress. *Interpretation:* that goes against R5 and against `:687`, though no MUST is broken.
- **Eviction-with-error is compliant** (interpretation, no explicit text):
  - an evicted stream is no longer "active" (R2);
  - it received an in-order prefix, so R1 and R3 hold;
  - the other streams are unaffected (R4);
  - §3.7 `:762` expects missed updates across reconnects;
  - §3.1.6 `:311` (snapshot first) is the recovery path.
- **Message-only turns.** For a requester stream, an early close mid-task does not violate R8: R8 is about closing at the terminal state, and Koog already ends streams early at the turn end or interrupted states and on shutdown. Message-only streams (§3.1.2 `:208`) carry exactly one event, so they can't overflow.
- **What the client sees over JSON-RPC SSE.** Close the channel with a non-cancellation `A2AException`. The transport's `.catch` then emits a final `data: {"jsonrpc":"2.0","id":<req>,"error":{"code":-32603,"message":"…"}}` and the stream ends (`JSONRPCServerTransport.kt:249-255,330-349`). This is the same shape as the C2 shutdown error.
  - It must not be a `CancellationException`. Those are rethrown and the SSE just stops (`:251`, `:337`).
  - For a truly stalled socket, the error event is queued behind the stalled write. The client sees it only if it resumes reading. Otherwise the connection lingers until TCP or engine stop, but it no longer blocks anyone.
- **Error code.** No A2A-specific error fits. `-32603` falls under the "System Errors / temporary unavailability" category (§3.3.2 `:531-536`).

### 4. Python SDK (main `261c074`)

**Pipeline.**
- The agent enqueues into `_event_queue_agent`, an `EventQueueSource` (`active_task.py:461`). Its default sink is blocking, with a cap of 1024 (`event_queue_v2.py:49-53`; `DEFAULT_MAX_QUEUE_SIZE = 1024`, `event_queue.py:31`).
- The in-process `EventConsumer` reads it (`active_task.py:119-139`). It persists, sends push **inline and awaited** (`:352-375`), and only then calls `_enqueue_to_subscribers` (`:235-239`, `:386-404`).
- `_enqueue_to_subscribers` feeds `_event_queue_subscribers`, which has no default sink (`:462-464`). Its dispatcher fans out with `gather` to the tapped sinks (`event_queue_v2.py:114-137`).

**Every streaming consumer is an evict-on-full tap.**
- `ActiveTask.subscribe` taps with `evict_on_full=True` (`active_task.py:743-747`).
- `on_message_send` (blocking and `return_immediately`, `default_request_handler_v2.py:408-412`), `on_message_send_stream` (`:476-479`) and `on_subscribe_to_task` (`:580`, `:588`) all go through it.
- `DefaultRequestHandler = DefaultRequestHandlerV2` (`request_handlers/__init__.py:46`).

**Overflow.**
- In `_deliver_to_sink` (`event_queue_v2.py:101-112`), a sink that is full at delivery time gets `close(immediate=True)`. That drops its buffered events (`:110`, `:440`).
- The docstring admits that slow-but-alive consumers are evicted too (`:85-87`).
- The size is not configurable: neither `ActiveTask` nor the handler passes `max_queue_size`. A grep of `src/` outside `events/` finds no use of it.

**What each consumer sees on eviction.**

| Consumer | What happens |
|---|---|
| (a) Subscriber | `dequeue_event` raises `QueueShutDown`. It is caught and the loop `break`s (`active_task.py:812-813`), so the generator **ends normally**. The SSE stream closes with **no error event** and no terminal event. |
| (b) Requester streaming | same as (a): a silent early end |
| (c) Blocking collector | same eviction path. The loop ends, `result is None`, and the handler returns `active_task.get_task()` (`default_request_handler_v2.py:446-448`): possibly a **non-terminal task from a blocking call**. Interpretation from the code; no test covers it. In practice this is unlikely, because the consumer is an in-process generator and `gather` yields per event. |
| (d) Push | never evicted. It is inline in the consumer, so a slow webhook back-pressures the agent once 1024 events are queued, and delays every subscriber. |

**Does the agent ever block?** Not because of streams. It blocks when the consumer falls 1024 behind because of a slow store or slow push.

**Python tests.**
- `tests/server/events/test_event_queue_v2.py:893` (evicted, dispatch continues), `:937` (producer unblocked), `:958` (default tap keeps back-pressure), `:976` (a graceful close is not upgraded).
- `tests/server/agent_execution/test_active_task.py:1290` (subscriber taps are evict-on-full).
- No end-to-end test of what an evicted client receives.

**TCK.**
- STREAM-ORDER-002/003/004 (`tck/requirements/streaming.py:43-80`, §3.5.2).
- `test_multi_stream.py:216-241` opens two subscriptions and abandons the first after one event. The iterator is left open, not closed (`_test_helpers.py:105-117`). The test only requires the second stream to receive one or more events, and Koog emits the snapshot before any fan-out, so it can't distinguish back-pressure from eviction.
- No TCK test for slow consumers, buffers or eviction errors, at either revision.

### 5. Design options

Shared building block for B, C and D: `Channel(capacity)`. On full, either evict at once (Python) or `withTimeoutOrNull(T) { send }`, then evict. To evict, call `channel.close(SlowConsumerException)` (an `A2AInternalErrorException`), not `cancel`. Then the consumer drains its in-order prefix before it sees the error.

| | A. Back-pressure (+ optional per-delivery timeout T) | B. Buffer+evict, subscribers only | C. Buffer+evict, subscribers **and** requester views | D. Configurable (H5) |
|---|---|---|---|---|
| Stalled remote reader | blocks everything; with T, at most T once, then evicted | a stalled subscriber costs nothing; **a stalled requester still blocks everything** | costs nothing (or ≤T once) | per config |
| Requester never misses its final event | kept | kept | **lost** for an evicted streaming requester: it gets `-32603`, the turn continues (R5), recovery via SubscribeToTask/GetTask | per config |
| In-band end-of-turn marker (close of the view) | unchanged | unchanged (close after buffered elements is still ordered) | unchanged for non-evicted views | — |
| Ordering / same events per stream | unchanged | evicted stream gets a prefix plus an error; others unchanged | same | — |
| Terminal close | unchanged | if the buffer is full at the terminal event, the client gets an eviction error instead (unless the terminal event may always wait ≤T) | same | — |
| `sendTaskEvent` returns | after every listener took the event | after persisting and enqueuing to subscribers; still waits for the requester | after persisting and enqueuing | — |
| [S] hold time | unbounded (T: ≤T per stalled listener) | unbounded while a requester stalls | storage I/O + enqueue (≤T) | — |
| C1 | remains (T bounds it) | remains for requester views | **gone** | — |
| Shutdown forced phase (D8) | still essential to unblock | still needed for requester views | only needed to end streams with `-32603` (D4); no longer needed to unblock. *Change:* `cancel` drops a buffered, not yet emitted terminal event, so such a stream gets the shutdown error instead | — |
| Graceful drain | can wait for a stalled reader until the deadline | same if the requester stalls | waits only for agents and push | — |
| Memory per listener | 0 | capacity × event size (counted in events, not bytes; large artifacts count as 1) | same | — |
| Code | smallest; `deliver` gains a timeout | `register` gets a capacity flag per kind, `deliver` gets an evict branch | same, without the kind split; KDoc simplifies (`TaskEventStream.kt:44-53`, `TurnManager.kt:121-130`, `Turn.kt:62-63`) | adds a config object passed as data (fits the "data, not callbacks" rule, `TurnManager.kt:179-185`) |
| Tests to rewrite | `TaskEventStreamTest.kt:317,335` only if T is added | `TaskEventStreamTest.kt:317,335,376`, `A2AServerSubscribeTest.kt:279`; new eviction tests | B + `A2AServerShutdownTest.kt:286` | union, per mode |

**Burst hazard for instant eviction (Python style) in Koog.**
- `trySend` never suspends, so an agent that sends in a tight loop on a single-threaded dispatcher never lets any consumer run. Every listener overflows and is evicted, including the in-process blocking collector.
- `A2AServerSubscribeTest.kt:316` (`testAgentEmittingManyEventsWithoutSuspendingCompletesBlockingSend`, 500 events) pins exactly this case.
- Python avoids it because `gather` yields per event.
- Hence the recommendation: **on full, suspend up to T, then evict.** That yields to consumers and bounds the stall.

**Blocking SendMessage collector.**
- It is in-process. It consumes each event at once, and its view is cancelled right after the response event (`A2AServer.kt:486,626`).
- Evicting it would turn a healthy, progressing task into a `-32603` for a non-streaming call, which is worse than Python's silent fallback.
- **Keep it suspending (exempt).** It can't stall unless the dispatcher is starved.
- Unbounded is not needed and would give up the memory bound for a CPU-bound agent.
- This needs `startTurn` to receive the view's policy as data (e.g. `viewDelivery = SUSPEND` for `onSendMessage`).

## Koog status
- Back-pressure only. Rendezvous channels everywhere (`TaskEventStream.kt:289`), sequential fan-out under [S] (`:294-306`), documented as a deliberate choice (`TurnManager.kt:121-130`).
- Disconnects are handled by cancellation (heartbeat A4), shutdown by D8.
- Push is unbounded and decoupled.
- The gaps are listed in §1 "What remains". There is no buffer, eviction or timeout anywhere.

## Testability notes
- **Unit (virtual time).**
  - `TaskEventStreamTest`: a stalled subscriber with capacity N. Publish N+1 events and assert eviction, the error type, and the other subscriber receiving everything. With T, advance time.
  - The terminal event while full.
  - The view stays exempt in SUSPEND mode.
  - `A2AServerSubscribeTest.kt:316` must stay green, which proves no burst eviction.
- **A2AServer.**
  - A streaming requester that stalls (under C) ends with `A2AInternalErrorException`, while the turn completes and the task reaches its terminal state.
  - A blocking collector is never evicted.
- **Transport.** An SSE subscriber that stops reading: assert the final `-32603` event once the client resumes reading. Use a raw-socket client that pauses, like the A4 experiment.
- **TCK.** STREAM-ORDER-002/003/004 pass under every option. Nothing in the TCK observes eviction. Whether a client that stalls but stays alive ever sees the error event is untestable in general (it depends on the TCP stack).

## Discrepancies
- **Python vs. spec intent:** the evicted stream ends silently, with no terminal event and no error. A client can't tell that apart from a server-side close. Not a MUST violation, but it gives up R2/R3-style guarantees without telling anyone. Python's blocking path may return a non-terminal Task after eviction (from code, untested).
- **Python push vs. spec R5:** push is sent inline, so a slow webhook back-pressures the task. That is legal but the opposite of Koog.
- **TCK:** STREAM-ORDER-004 abandons the stream instead of closing it, and needs only one event, so it doesn't test R4 in a way that tells back-pressure from eviction. No TCK coverage of slow consumers.
- Spec main vs. v1.0.1: no relevant difference.

## Open questions
| # | Decision | Recommendation |
|---|---|---|
| Q1 | Evict at all, or keep D-5 (A)? | Evict (D with eviction as the default). A stalled-but-alive client is the remaining unbounded stall, and R5 favors it. |
| Q2 | Instant evict (Python) or wait ≤T when full? | Wait ≤T, then evict. It prevents burst eviction on single-threaded dispatchers and bounds [S], C1 and the drain. |
| Q3 | Default capacity / T | Capacity **64** (memory per listener; bursts are absorbed by the wait) and **T = 30 s** (more than 2× the heartbeat period; matches the spec's webhook upper bound). Alternative: 1024 for Python parity. The user should decide. |
| Q4 | Error for an evicted stream | `A2AInternalErrorException` `-32603`, message along the lines of "Stream closed: the client fell behind; resubscribe with SubscribeToTask or read the task with GetTask". Never a `CancellationException`. |
| Q5 | Does eviction apply to the streaming requester view? | **Yes** (C semantics). Otherwise a stalled requester still freezes the task and C1 stays. The turn continues, and the requester resubscribes. |
| Q6 | Is the blocking/`returnImmediately` collector exempt? | **Yes**: SUSPEND (rendezvous or bounded-suspend), passed to `startTurn` as data. |
| Q7 | Configurability | `A2AServer` constructor config (e.g. `StreamDeliveryConfig(capacity, slowConsumerTimeout, policy = EVICT \| SUSPEND)`), H5 shape. `SUSPEND` keeps today's behavior as an opt-out. |
| Q8 | Terminal event when the buffer is full | Let it wait ≤T like any other event (no special case). Document that a client evicted at that point should call GetTask. |
| Q9 | Push queue bound (unbounded today) | Out of scope here. Track separately: cap per task with drop-oldest plus a warning, or leave it. |
| Q10 | Shutdown: `cancel` drops a buffered terminal event | Accept and document, or switch the shutdown path to `close(ServerShutdownException-like A2A error)` so buffered events drain first. |
| Q11 | How do Koog's own client and the Python client react to a final `-32603` SSE event mid-task vs. a silent end? | Not examined; route to a client-side researcher. |

## Recommendation
Adopt option **D**, defaulting to eviction:
- Per-listener bounded channel (default 64).
- When full, suspend up to `slowConsumerTimeout` (default 30 s), then `close(A2AInternalErrorException)`. The client sees its in-order prefix and then a final `-32603` SSE error.
- Applies to SubscribeToTask streams and the streaming requester view. The turn keeps running.
- The blocking/`returnImmediately` collector stays suspending.
- `policy = SUSPEND` reproduces today's D-5 for users who want "never drop".

This removes the stall for stalled-but-alive clients and C1, lets the graceful drain wait only for agents, keeps ordering and the snapshot-on-subscribe recovery path, and stays compliant with §3.5.2/§3.7. Unlike Python, the eviction is reported to the client rather than silent, and a single-threaded burst can't trigger it.
