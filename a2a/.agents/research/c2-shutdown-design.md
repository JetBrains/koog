# C2: shutdown state and graceful shutdown for `A2AServer`

**Sources checked:** Koog `eugenethedev/a2a-1.0` @ `65b063414`. A2A spec repo (`a2a-protocol`) @ `98279d6` (`main`), baseline tag `v1.0.1`; the `v1.0.1..main` diff touches nothing relevant (only link fixes in §3.3.2/§3.1.x). a2a-python @ `79a6f45` (`v1.2.3-5`). Koog's Python test server pins `a2a-sdk==1.1.0`, which predates `aclose()` (added in v1.1.2) and `shutdown_grace_period` (v1.2.2); neither matters for the client ITs. a2a-tck @ `5daf014`. Ktor 3.3.3 sources (`ktor-server-core-jvm`, `ktor-server-netty-jvm`).
**Confidence:** high for today's behavior (Q1) and the upstream facts. Medium for the design (Q3/Q4): it is a proposal, not upstream behavior. The spec does not define shutdown at all.

Paths are relative to `a2a/a2a-server/src/commonMain/kotlin/ai/koog/a2a/server/` unless prefixed.

## Answer

- **Today.** `A2AServer.cancel()` is entirely non-graceful. It completes the subscribe shutdown signal and cancels `coroutineScope`.
  - Running agents are cancelled.
  - Their tasks stay in the last state the agent persisted (e.g. WORKING), with no terminal event.
  - Requester streams end silently.
  - Queued pushes are dropped.
  - Later RPCs return the misleading results C2 lists. Read-only RPCs and CancelTask work normally.
- **Transport.** The HTTP transport's `start`/`stop` is unrelated to `A2AServer.cancel()`. Neither calls the other. The transport owns the Ktor server only when you use its standalone `start`. With `a2aJsonRpcTransportRoute`, the user's app owns it.
- **Error code.** The spec has no shutdown error. Its "System Errors: … temporary unavailability" category gives JSON-RPC `-32603 Internal error` as the example code. The Python SDK also returns `-32603` after `aclose()`, but only by accident: an unhandled `RuntimeError('ActiveTaskRegistry is closed')`.
  - **Recommendation:** `A2AInternalErrorException("Server is shutting down")`, i.e. `-32603`.
  - Not `-32004`: it means "not supported by this implementation", maps to 400/`FAILED_PRECONDITION` on the other bindings, and clients read it as permanent.
- **Graceful shutdown.**
  - Enforce "stop accepting" in `A2AServer`/`TurnManager`, under the registry lock in `startTurn`. Ktor's own stop only closes the listening socket.
  - "In-flight" should mean running turns plus the push queue. It should not mean SubscribeToTask streams: they never end on their own.
  - Keep subscriptions open during the grace period, so they see the drained turns finish. End them when the drain completes or at the deadline.
  - At the deadline, cancel every turn view and subscriber channel **before** cancelling the agents. This also makes the C1 hang bounded.
  - Python has no graceful agent drain: `aclose()` always forces.

## Requirements

| # | Requirement | Tier | Bindings | Citation |
|---|---|---|---|---|
| R1 | Servers MUST return appropriate errors and SHOULD give actionable information | MUST / SHOULD | all | spec v1.0.1 §3.3.2, `docs/specification.md:500` |
| R2 | System errors ("temporary unavailability"): servers SHOULD use different codes for temporary vs. permanent failures, MAY include retry guidance (e.g. `Retry-After`), SHOULD log them. Examples: HTTP 500/503, gRPC `INTERNAL`/`UNAVAILABLE`, JSON-RPC `-32603` | SHOULD / MAY | all | §3.3.2, `docs/specification.md:531-536` |
| R3 | An error MUST convey a code and a message; details are optional, each with an `@type` | MUST | all | §3.3.2, `:539-548`; §9.5, `:2431-2437` |
| R4 | `UnsupportedOperationError` = "operation … not supported by this server agent implementation" → `-32004` / `FAILED_PRECONDITION` / 400 | MUST (mapping) | all | §3.3.2 `:558`; §5.4 `:1185` |
| R5 | A2A-specific JSON-RPC codes are in `-32001..-32099` (so a home-made code would collide with future A2A codes) | MUST | JSON-RPC | §9.5, `:2451-2453` |
| R6 | A streaming SendMessage stream / a SubscribeToTask stream MUST close when the task reaches a terminal state. Closing it earlier (e.g. on shutdown) is not addressed | MUST (terminal); silent otherwise | JSON-RPC, gRPC, REST (streaming) | §3.1.2 `:210`; §3.1.6 `:309` |
| R7 | Closing one stream MUST NOT affect other streams; the task lifecycle is independent of any stream; clients may reconnect with a new stream after an interruption | MUST | streaming | §3.5.2, `:689-700` |
| R8 | After a Task is returned, processing continues in the background | descriptive | all | §3.3.3, `:567` |

The spec contains no shutdown, draining, or graceful-stop requirement (searched `docs/`, `adrs/`, `specification/` for shut/graceful/unavailable). The TCK has no shutdown test. The only `-32603` reference is the error binding table in `tck/requirements/base.py:355-357`. Everything below is SDK design.

## Details

### Q1. What `cancel()` does today

`A2AServer.kt:1073-1076` calls `turnManager.shutdown()`, then `coroutineScope.cancel(cause)`. Nothing waits, and nothing is graceful.

| Thing | What happens | Evidence |
|---|---|---|
| SubscribeToTask streams | `shutdownSignal.complete()`. Each subscribe loop's `select` sees `onJoin` first and ends **normally**. A subscribe flow stuck in `emit` (stalled SSE write) is **not** woken. Its channel stays registered, so a fan-out to it can still block. | `turn/TurnManager.kt:148, 346-351, 441-443` |
| Running agent jobs | Cancelled, since they are `async` children of `coroutineScope`. The monitor's `await` gets a CE, and the teardown (NonCancellable) runs `endTurn(failure = CE)`, which does **not** mark the task failed. The task keeps its last persisted state (WORKING, etc.) forever, and no terminal event reaches anyone. | `turn/TurnManager.kt:229, 278-311`; `turn/TaskEventStream.kt:259-267` |
| Requester streams / blocking SendMessage | The view closes. `emitTurnEvents` ends without a terminal event, and `awaitAgent` swallows the agent's CE because the request coroutine (Ktor's) is still active. Streaming: the SSE ends after the last event, no error. Blocking: returns the stored, non-terminal task. New task with no event: `-32006`. | `A2AServer.kt:555-572, 666-670` |
| Push queue | Workers are cancelled (`ensureActive` at the top of each loop) and queued events are dropped, as decided in Q7. Events persisted later (e.g. by CancelTask) start an ATOMIC worker that exits right away. | `notifications/PushNotificationDispatcher.kt:27, 52-98` |
| SendMessage after cancel | Validation runs and the inline push config is saved. `startTurn` creates a LAZY `async` in the cancelled scope, which never runs. The ATOMIC monitor ends the turn. Result: `-32006` for a new task (and the inline config is orphaned, B6); for a follow-up, the snapshot or the stored task, with no agent run. | `A2AServer.kt:491-505`; `turn/TurnManager.kt:229-240` |
| SubscribeToTask after cancel | Snapshot, then a normal end (the signal is already complete). | `turn/TurnManager.kt:344-351` |
| CancelTask after cancel | **Works.** `executor.cancel` and the forced CANCELED run in the request coroutine and are persisted; the push is dropped. | `A2AServer.kt:835-885` |
| GetTask, ListTasks, push config CRUD, GetExtendedAgentCard | Work normally (storage only). | `A2AServer.kt:385-396, 762-789, 916-978` |
| User-supplied `coroutineScope` | `cancel()` cancels **the caller's own scope**, not an internal child. If that scope is the app scope, A2AServer cancels the whole app. | `A2AServer.kt:346, 1075` |

C1 interplay. The only non-cancellable fan-out is the `markFailed` FAILED event in the monitor teardown, and it only runs for a non-CE failure. Agent publishes are cancellable: `deliver` rethrows the publisher's CE (`TaskEventStream.kt:333-340`). So `cancel()` hangs only if an agent failed just before or during shutdown and a turn view or subscriber is stalled in its downstream `emit`. Cancelling the channel frees it: `deliver` then returns `false` (`:336-340`). Note: the code review's claim that `shutdown()` frees subscribers is true only for subscribers parked in `select`, not for stalled ones.

**Transport.** `HttpJSONRPCServerTransport` (`a2a/a2a-transport/a2a-transport-server-jsonrpc-http/.../HttpJSONRPCServerTransport.kt:52-116`) owns an `EmbeddedServer` only if `start()` was used. `stop(gracePeriodMillis = 1000, timeoutMillis = 2000)` → `stopSuspend`.
- It doesn't know `A2AServer`: it takes a `RequestHandler`, which has no lifecycle (`a2a-core/.../RequestHandler`). The module doesn't depend on `a2a-server`.
- With `a2aJsonRpcTransportRoute` (`Routes.kt:69`), the user's app owns Ktor.
- `JSONRPCServerTransport` (`a2a-transport-core-jsonrpc/.../JSONRPCServerTransport.kt:62-`) has no lifecycle at all.
- JSON-RPC errors are sent as HTTP 200 (`Routes.kt:90-93`).

**Ktor 3.3.3 stop sequence.** `EmbeddedServerJvm.kt:343-355` calls `engine.stop(grace, timeout)`, then `destroyApplication()`. That raises `ApplicationStopping`, runs `Application.dispose()` (which does `applicationJob.cancel()`, `Application.kt:148-150`), then raises `ApplicationStopped`. `NettyApplicationEngine.kt:289-314` raises `ApplicationStopPreparing`, closes the server channels (stops accepting), then runs `shutdownGracefully(grace, timeout)` on the Netty event loop groups.
- Netty's first argument is a **quiet period**: shut down once no task was submitted for that long, at most `timeout`. That is my reading of Netty's API, not re-read from Netty sources. It is not "wait for in-flight HTTP requests": a request suspended on an agent submits no Netty tasks.
- So Ktor's grace can't drain A2A turns. An idle SubscribeToTask SSE is just cut when the event loops stop or when the application job is cancelled.
- Hypothesis (not verified): call coroutines are children of the application job, so `dispose()` cancels them. That would run the requester `finally { turn.cancelEvents() }` and the subscriber `unsubscribe`.

### Q2. Which error code

| Option | Code | Assessment |
|---|---|---|
| **`A2AInternalErrorException("Server is shutting down")`** | `-32603` | **Recommended.** It is the spec's own JSON-RPC example for system errors, including temporary unavailability (R2), and matches what Python actually returns. It already exists in Koog (`a2a-core/.../Exceptions.kt:140-146`), so there is no core change. The client gets `A2AInternalErrorException`. |
| `A2AUnsupportedOperationException` | `-32004` | Not recommended: it means the operation is not implemented (R4). It maps to 400/`FAILED_PRECONDITION`, and Koog already uses it for terminal-task follow-ups and missing streaming. Clients would treat it as permanent. |
| Custom code (e.g. `-32000`) via `A2AUnknownException` | impl-defined | `-32001..-32099` belongs to A2A (R5). `-32000` is free in JSON-RPC 2.0, but no client understands it. `A2AException` is sealed, so a typed subclass needs a core change. Not recommended. |
| `ErrorInfo` detail with e.g. `reason = "SERVER_SHUTTING_DOWN"` | — | Optional. Koog's `ErrorInfo` hard-codes `domain = "a2a-protocol.org"` (`ErrorData.kt:41-42`), so a Koog reason under the A2A domain would invent protocol vocabulary. Skip, or decide on a Koog domain (needs a core change). |

Python, for reference:
- `DefaultRequestHandler` = `DefaultRequestHandlerV2` (`src/a2a/server/request_handlers/__init__.py:46`).
- `aclose()` (`default_request_handler_v2.py:187-194`) → `ActiveTaskRegistry.aclose()` sets `_closed` and force-closes every `ActiveTask` (`agent_execution/active_task_registry.py:128-160`; `active_task.py:921-961`: "Always forces", queues closed `immediate=True`, the producer (agent) is cancelled).
- After that, `get_or_create` raises `RuntimeError('ActiveTaskRegistry is closed')` (`active_task_registry.py:57-58`). The dispatcher maps any non-A2A exception to `InternalError(message=str(e))` (`routes/jsonrpc_dispatcher.py:184-186, 347-351`).
- So SendMessage, SendStreamingMessage, SubscribeToTask (single-process, `:577`) and CancelTask of a locally held task (`:249-262`) get `-32603`. GetTask, ListTasks and push config CRUD keep working.
- A cancelled agent writes no terminal state: `active_task.py:659-660` only logs the `CancelledError`. Only non-CE failures write FAILED (`:665-680`).
- At the transport level, `JsonRpcDispatcher(shutdown_grace_period=…)` gives active SSE streams that many seconds before force-cancelling them (`routes/jsonrpc_dispatcher.py:133-166, 606-609`; `routes/common.py:47-70`, via sse-starlette).

### Q3. Graceful design

**What "in-flight" should mean.**

| Kind | Bounded on its own? | Proposal |
|---|---|---|
| Running turns (agent job + monitor) | Yes. A turn ends when `execute()` returns, including at INPUT_REQUIRED. | **Wait** for every turn's `join()` (monitor fully ended). That includes turns between the registry check and registration (`TurnManager.kt:198-254`), so use a counter incremented at the check and decremented in the monitor teardown, or join the internal supervisor job of B3. |
| Streaming SendMessage | Yes, it ends with its turn. | Covered by waiting for turns. |
| Follow-ups queued on TL (`A2AServer.kt:459-461`) | No, and they can chain. | **Reject** with the shutdown error when they reach `startTurn` (the existing catch cleans up the inline config, `:506-514`). |
| SubscribeToTask | **No**: ends only at a terminal state; idle tasks never get one. | **Don't wait.** Keep them open during the grace period, so subscribers see the drained turns finish. End them when the drain completes or at the deadline. Ending at grace start would hide those final events. |
| Push queue | Yes: per-event sender timeout, no retries. | **Wait** until idle after the turns (`PushNotificationDispatcher.awaitIdle()`, i.e. `queues.isEmpty()`). Drop what's left at the deadline. This narrows the Q7 decision from "drop on shutdown" to "drop at deadline". |
| Unary RPCs, CancelTask blocks | Yes, bounded by storage/executor. | Not tracked by A2AServer; Ktor's quiet period covers them. |

**Where to enforce "stop accepting".** In `A2AServer`, as the authoritative check. The transport alone can't do it: Netty keeps already-open keep-alive connections working during its quiet period, and with `a2aJsonRpcTransportRoute` Koog doesn't own the server.
- Add a cheap check at each RPC entry, right after `checkRequiredExtensions`, with the C2 error.
- Add the decisive check under `registryMutex` in `startTurn`, next to `check(taskId !in turns)`. The drain flag set under that lock closes the race between the entry check and registration: after it is set, no turn can start, and the counter/registry is final.

**Ordering with Ktor.** A2A first, Ktor second: `a2aServer.shutdown(grace)`, then `transport.stop(…)` (or the app's `server.stop`). The reverse order cuts clients' streams first, while their agents keep running unobserved.
- Ktor's own grace only needs to flush the last responses, so the transport's 1 s/2 s defaults are fine.
- The transport can't call `A2AServer.shutdown` itself without a lifecycle on `RequestHandler` (a core API change, see decisions).
- Embedded apps can call it before `server.stop`. Ktor's `ApplicationStopPreparing` hook (`NettyApplicationEngine.kt:291`) is synchronous, so using it needs `runBlocking`.

**The deadline and C1.** The forced path must not depend on Ktor having cancelled the calls. Order:
1. Set SHUT_DOWN.
2. Complete `shutdownSignal`.
3. **Cancel every turn view and subscriber channel** (lock-free `channel.cancel()`). This frees every pending fan-out, including the NonCancellable `markFailed` one, through `deliver` → `false`.
4. Cancel the internal supervisor job (agents, monitors, push workers).
5. Optionally `join` it.

What remains unbounded: NonCancellable storage I/O (`persist`, `release`) against a hung storage. That can't be fixed in-library; document it.

Implementation note: `subscribers` is guarded by the stream lock [S]. To cancel channels without [S], keep a lock-free registry of channels (e.g. a copy-on-write set per stream, or one global set in `TurnManager`, removed in each subscriber's `finally`). Cancel with a dedicated CE subclass, so `A2AServer` can tell a shutdown from a client disconnect when the requester's `consumeAsFlow` throws.

### Q4. API sketch

```kotlin
public open class A2AServer(/* unchanged */) : RequestHandler {
    /**
     * Graceful shutdown: from now on SendMessage/SendStreamingMessage/SubscribeToTask fail with
     * A2AInternalErrorException ("Server is shutting down"); running turns and queued pushes are awaited for up to
     * [gracePeriod], then everything left is forced as in [cancel]. Returns once shut down. Idempotent; concurrent
     * callers wait for the same shutdown.
     */
    public open suspend fun shutdown(gracePeriod: Duration = Duration.ZERO)

    /** Immediate (non-graceful) shutdown, non-suspending: = the forced phase of [shutdown]. */
    public open fun cancel(cause: CancellationException? = null)

    /** Throws the shutdown error if the server no longer accepts [operation]. Called first by every RPC. */
    protected open fun checkAccepting(operation: A2AMethod)   // name/shape open
}
// TurnManager (@InternalA2AApi): beginDrain(), awaitTurns(), shutdown() (now also cancels views/subscribers);
// PushNotificationDispatcher (internal): awaitIdle().
```

`shutdown(grace)` = `beginDrain()`, then `withTimeoutOrNull(grace) { turnManager.awaitTurns(); dispatcher.awaitIdle() }`, then `forceShutdown()`. `cancel()` = `forceShutdown()`. Every step of `forceShutdown()` is non-suspending: flags, `Job.complete`/`cancel`, `channel.cancel`.

Behavior per RPC:

| RPC | RUNNING | DRAINING (grace) | SHUT_DOWN |
|---|---|---|---|
| SendMessage / SendStreamingMessage (new task or follow-up) | normal | `-32603` "shutting down", also for follow-ups queued on TL (rejected in `startTurn`) | `-32603` |
| Turns already running, and their requesters | normal | run to completion; requesters get their normal response | agent cancelled; the requester gets `-32603` (as the last SSE event if streaming) instead of today's silent end / WORKING task / `-32006` |
| SubscribeToTask (new) | normal | `-32603` (decision D3) | `-32603` |
| SubscribeToTask (open) | normal | stays open, receives events | ended when the drain completes or at the deadline: normally (current R2-Q2) or with `-32603` as the last event (D4) |
| CancelTask | normal | allowed (decision D3) | `-32603` |
| GetTask, ListTasks, push config CRUD, GetExtendedAgentCard | normal | allowed (D3) | `-32603` (per the user's "every RPC fails") |
| Push notifications | normal | delivered | queue dropped |

### Decisions for the user

- **D1 error:** `-32603` `A2AInternalErrorException` with messages "Server is shutting down" / "Server is shut down". (Recommended.)
- **D2 API:** add `suspend fun shutdown(gracePeriod)` and keep `cancel()` as the immediate path. Or replace `cancel()` (a breaking rename)?
- **D3 accepted during grace:** only reads, CancelTask and push config CRUD (recommended, useful to observe and cancel the drain), or nothing?
- **D4 how open subscriptions end:** normally (current R2-Q2) or with the shutdown error as the last SSE event (more explicit, consistent with C2)? And when: at drain end or deadline (recommended), or at grace start?
- **D5 tasks cut at the deadline:** leave them in their last state (today, like Python), or persist FAILED for them (no fan-out: the streams are already ended) so persistent storages don't keep WORKING zombies forever? The latter adds storage writes to the forced path.
- **D6 scope ownership:** shutdown cancels only an internal `SupervisorJob` child (pairs with B3), not the user-supplied `coroutineScope`. Recommended; changes today's `coroutineScope.cancel()`.
- **D7 transport coupling:** leave the ordering to the user (document "`a2aServer.shutdown()` before `transport.stop()`"), or add a lifecycle hook to `RequestHandler`/`ServerTransport` so `HttpJSONRPCServerTransport.stop()` drives it (a core API change)?
- **D8 C1:** cancelling all views and subscriber channels in the forced phase makes C1 bounded. Do it now (needs a lock-free channel registry), or keep C1 as TODO-only (recorded decision) and accept that a forced shutdown can still hang behind a stalled client after an agent failure?

## Koog status

- No shutdown state. `cancel()` behaves as tabled in Q1. There is no graceful path.
- `TurnManager.shutdown()` only ends subscribe loops parked in `select`.
- Transport `stop` and `A2AServer.cancel()` are independent; nothing documents their ordering.
- The TCK SUT (`a2a/test-tck/a2a-test-server-tck/src/main/kotlin/ai/koog/a2a/test/tck/Main.kt:95-110`) and the examples start with `wait = true` and never stop either.

## Testability notes

All of this can be tested in `commonTest` at the `A2AServer` level, using the existing fixtures (`A2AServerTestFixtures.kt`, scripted executors).
- **After `cancel()`:** each RPC returns `A2AInternalErrorException`.
- **Grace:**
  - a gated agent finishes inside the grace period; its requester gets the normal response, and its subscriber sees the terminal event and then ends;
  - a new SendMessage during the grace period fails;
  - a follow-up queued on TL is rejected and its inline config deleted;
  - GetTask works during the grace period.
- **Deadline:** a never-finishing agent is cancelled at the deadline, and its streaming requester gets the error as the last event.
- **C1:** a failing agent plus a stalled (uncollected) requester view; `cancel()` must return and `join()` must complete, with a `runTest` timeout.
- **Push:** a queued push is delivered within the grace period (slow fake sender), and is dropped at the deadline.
- **Ktor ordering:** observable only in a `ktor-server-test-host` or Netty IT. Optional.

The TCK doesn't cover shutdown, so nothing changes there.

## Discrepancies

- **Python vs. spec:** the spec suggests telling temporary from permanent failures (R2). Python's shutdown `-32603` is incidental (an unhandled `RuntimeError` with message "ActiveTaskRegistry is closed"), not a designed error. It does land on the code the spec's example names.
- **Python vs. this proposal:** Python's handler `aclose()` always forces. Only its SSE transport has a grace period, so it has no agent drain.
- **Koog review text:** "Subscribers are freed by `shutdown()`" (phase3 review C1) is true only for subscribers not stalled in `emit`.

## Open questions

- Should SHUT_DOWN be inferred too when `coroutineScope` gets cancelled from outside? It is the same brick as B3. Cheap: `!scopeJob.isActive` in the check.
- B6/W2: an inline config saved by a request rejected during the grace period must still be cleaned up. W2's "save under the gate" makes this moot.
- Should the HTTP transport add `Retry-After`/503 during shutdown? The JSON-RPC binding sends 200 for every error. The spec MAY allows retry guidance (R2), but nothing defines it for JSON-RPC.
