# Server customization: current extension points, Python SDK parity, and a hook list for the turn layer

**Sources checked:**
- Koog `eugenethedev/a2a-1.0` @ `b29a7b6ae` plus the uncommitted working tree. `Turn.kt`, `TurnEventProcessor.kt` and `TurnManager.kt` have local W5 edits, so line numbers in those files can be off by a few lines.
- a2a-python @ `79a6f45` (`v1.2.3-5`, main). Koog's Python test server pins `a2a-sdk==1.1.0` (`a2a/test-python-a2a-server/pyproject.toml:7`). Pinned versions don't matter here: this is about API design, not interop.
- A2A spec repo @ `98279d6` (`v1.0.1-89`). The baseline is `v1.0.1:docs/specification.md`.
- TCK: not examined. Hooks are a design topic, and the TCK only checks the defaults.

**Confidence:** high for the inventory (Q1, Q3) and the Python facts (Q2). Medium for the proposal (Q4): it is a design opinion, and the spec says nothing about server extensibility.

Koog paths are relative to `a2a/a2a-server/src/commonMain/kotlin/ai/koog/a2a/server/` unless prefixed. Python paths are relative to `src/a2a/server/`.

## Answer

- **Usable today, without the turn layer:**
  - the composition points: `AgentExecutor`, the three storages, `IdGenerator`, `PushNotificationSender`;
  - `validatePushNotificationConfig` and `checkStreamingSupport`;
  - overriding the RPC handlers that don't touch the turn layer (GetTask, ListTasks, push config CRUD, GetExtendedAgentCard);
  - wrapping any RPC handler and calling `super` (auth, `ctx.state` enrichment);
  - on the transport, `JSONRPCServerTransport`'s protected open methods.
- **Traps:**
  - Fully overriding `onSendMessageCommon`, `onCancelTask` or `onSubscribeToTask` needs the `@InternalA2AApi` turn layer plus private helpers.
  - `tasksMutex` is `protected`, so a subclass can join the lock protocol.
  - `turnManager` is `protected open`.
  - `checkRequiredExtensions`'s KDoc promises "skip some operations", but the method gets no operation.
  - The Ktor route builds `ServerCallContext` from headers only, so there is no way to get the authenticated principal into `ctx.state` without overriding RPCs.
- **Python** has the same pattern. Its turn-layer equivalent (`ActiveTaskRegistry`/`ActiveTask`/`EventConsumer`/`TaskManager`/event queues) is built inside `DefaultRequestHandlerV2` and is not injectable. v2 even dropped the old `QueueManager` hook.
  - Python's customization lives at the edges: the handler's constructor parameters, `RequestContextBuilder`, `ServerCallContextBuilder`, stores with `OwnerResolver`, the push sender, `push_url_validator`, `extended_card_modifier`, the cluster `VersionedTaskStore`/`TaskEventStream`, and subclassing the handler's `on_*` methods.
  - Queue sizes, eviction, the follow-up queueing policy and the FAILED status are hard-coded.
- **Phase 5** (`agents-features-a2a-server`) uses only `RequestContext` (its fields plus `ContextTaskStorage`/`ContextMessageStorage`) and `TurnEventProcessor.sendMessage`/`sendTaskEvent`. It uses no `TurnManager`/`Turn`, no `A2AServer` subclassing, and no hooks. S4/A1 don't affect it.
- **Recommended now (with S4/A1):**
  1. a `failureMessage(task, error)` hook;
  2. an operation parameter for `checkRequiredExtensions`;
  3. pass-through only: `validatePushNotificationConfig` keeps running before the slot.

  Recommended later: follow-up queue limit or reject policy, a subscriber buffer policy (with D-5), shutdown parameters (with C2), a `ServerCallContext` builder on the Ktor route, and possibly an opt-in event transform.
- **Not worth a hook:** `validateFollowUp` (B5 already lets the agent reject a follow-up), a request-context builder, a user-message history policy, task-snapshot shaping. Storage decorators and an `AgentExecutor` decorator cover these.

## Requirements

The spec says almost nothing about server extensibility. Only these rules constrain the hooks:

| # | Requirement | Tier | Bindings | Citation |
|---|---|---|---|---|
| 1 | A message to a task in a terminal state fails with `UnsupportedOperationError` | MUST (listed error) | all | spec v1.0.1 §3.1.1, `docs/specification.md:175` (and §3.1.2 L200) |
| 2 | Follow-ups to a non-terminal (e.g. WORKING) task: no rule for queueing or rejecting | unspecified | all | §3.4.3 L635-639 (only "clients can send additional messages") |
| 3 | "The agent is responsible to determine which Messages are persisted in the Task History"; persisting all of them is MAY, and clients MUST NOT rely on it | MAY | all | §3.7 L760, L764 |
| 4 | Multiple concurrent streams per task | MAY | streaming | §3.5.2 L687 |
| 5 | Out-of-band auth: the agent SHOULD keep streams open in AUTH_REQUIRED and MAY continue without a follow-up | SHOULD/MAY | all | §7.6.1 L1931 |
| 6 | Audit trails for sensitive operations; logs MUST NOT contain sensitive data | SHOULD / MUST | all | §13.4 L3196-3197 |

## Details

### Q1. Current Koog extension points

**A2AServer** (`A2AServer.kt:338-1078`; `public open class`. Its `override` members are open because Kotlin doesn't make overrides final.)

| Member | Kind | Usable? | Notes |
|---|---|---|---|
| ctor: `agentExecutor`, `agentCard`, `agentCardExtended`, `taskStorage`, `messageStorage`, `pushConfigStorage`, `pushSender`, `idGenerator`, `coroutineScope` (`:338-347`) | composition, `protected val` | yes | These are the main hooks. |
| `tasksMutex` (`:365`) | `protected val` | **trap** | Exposes the lock protocol: a subclass locking it can deadlock with `onCancelTask`. A1 removes it. |
| `turnManager` (`:370-376`) | `protected open val` | **trap** | Overriding it builds a second manager, and the base one is still built. S4 makes it `protected val`. |
| `onGetExtendedAgentCard` (`:386`) | open override | yes | Safe to replace: no turn layer. Per-caller cards go here (Python has `extended_card_modifier`). |
| `onSendMessageCommon` (`:445`) | `protected open` | wrap only | Replacing it needs `turnManager.startTurn` (internal) and the private `emitTurnEvents`/`awaitAgent`/`failedWithRejectedEvent`/`saveInlinePushConfig`/`newRequestContext` (`:527-677`). Calling `super` with a modified request or ctx works. |
| `onSendMessage` / `onSendMessageStreaming` (`:695`, `:748`) | open override | wrap only | The KDoc example (`:137-150`) wraps `super` for auth. Replacing them means reimplementing `onSendMessageCommon`'s consumers. |
| `onGetTask` / `onListTasks` (`:763`, `:774`) | open override | yes | Safe to replace. Validation is private (`validateHistoryLength` `:792`). |
| `onCancelTask` (`:818`) | open override | wrap only | The body uses `turnManager.withTaskProcessor` (internal) and its `publish` lambda. |
| `onSubscribeToTask` (`:897`) | open override | wrap only | Delegates to `turnManager.subscribe` (internal). |
| push config CRUD (`:917-979`) | open override | yes | Safe to replace. `checkTaskExists`/`checkPushUrl` are private. |
| `validatePushNotificationConfig(config, ctx)` (`:991`) | `protected open suspend` | yes | A narrow hook: throw to reject (SSRF allow-list). Runs before the turn. Python equivalent: `push_url_validator`. |
| `checkRequiredExtensions(ctx)` (`:1021`) | `protected open` | partly | The KDoc says "e.g. to skip some operations" (`:1016-1017`), but the method gets no operation, so it can't do that. |
| `checkStreamingSupport()` (`:1040`) | `protected open` | yes | Has no parameters. |
| `storageIfPushNotificationSupported()` (`:1055`) | `protected open` | yes, odd | Mixes a capability check with an accessor. |
| `cancel(cause)` (`:1074`) | `public open` | yes | Overriders must call `super`. C2 will change it. |
| `newRequestContext` (`:639`) | **private** | no | There's no request-context hook. `ctx.state` (set in a wrapped RPC) is the only channel to the agent. |

**AgentExecutor** (`agent/AgentExecutor.kt:20-157`): `execute(context, eventProcessor)` (`:98`) and `cancel(context, eventProcessor, agentJob)` (`:150`, which by default throws `A2ATaskNotCancelableException`). This is the main extension point. A **decorating executor** (wrap `execute`, run checks or enrich state, delegate) works today and runs outside every server lock. It can't intercept events, because `TurnEventProcessor` is a final class with an `internal` constructor (`turn/TurnEventProcessor.kt:46`).

**Storages:**
- `TaskStorage` (`tasks/TaskStorage.kt:17-158`) has the RPC-scoped `get(GetTaskRequest, ctx)` and `list(...)` (owner/tenant scoping), plus the internal `get/getAll/getByContext/update/delete`.
  - `update` must "store the given task as is" (`:121-124`). That wording blocks redacting decorators, even though one that keeps `messageId` would be harmless.
- `MessageStorage` (`messages/MessageStorage.kt:12-46`).
- `PushNotificationConfigStorage` (`notifications/PushNotificationConfigStorage.kt:23-98`), with ctx on the RPC methods and none on the internal `getAll`/`deleteAll`.
- All three are user-replaceable and decoratable.
- `ContextTaskStorage`/`ContextMessageStorage` are final wrappers.

**IdGenerator** (`turn/IdGenerator.kt:15-63`): context, task, push config and server message ids. `UuidIdGenerator` requires the ids to be null (`:70-82`). It's usable and narrow.

**PushNotificationSender** (`notifications/PushNotificationSender.kt:13-25`): one `send(config, event)` per config.
- The internal dispatcher queues per task, logs failures and doesn't retry (`turn/TurnManager.kt:101-104`).
- A sender may retry internally: a slow sender only delays its own task's pushes.
- `SimplePushNotificationSender` is final (`notifications/SimplePushNotificationSender.kt:38`).

**Turn layer** (`turn/`): `TurnManager` is a public final class. `startTurn`, `subscribe`, `withTaskProcessor` and `shutdown` are `@InternalA2AApi` (`TurnManager.kt:185`, `:358`, `:413`, `:463`); `getTurn`/`activeTurns` are public (`:472`, `:477`). The other relevant internals:
- `Turn` (`Turn.kt:25`): internal constructor; `eventProcessor`/`taskSnapshot` internal.
- `TaskEventStream` (internal): RENDEZVOUS per subscriber (`:311`); FAILED status with a fixed text (`:92`, `:274-297`); user-message persistence (`:242-257`).

None of it is a usable extension point, which is consistent with S4.

**Transport:**
- `JSONRPCServerTransport` (`a2a-transport/a2a-transport-core-jsonrpc/.../JSONRPCServerTransport.kt:62`) is `public open`, with these protected open members:
  - `checkVersion` (`:133`);
  - `onRequest` (`:178`) and `onRequestStreaming` (`:226`): method dispatch, where ctx can be enriched;
  - `toRequest` (`:282`), `toJSONRPCSuccessResponse` (`:303`), `toJSONRPCErrorResponse` (`:322`), `toErrorData` (`:360`).
- `handleRequest` is public, so a user can write their own Ktor route.
- `HttpJSONRPCServerTransport` is final.
- `a2aJsonRpcTransportRoute` builds `ServerCallContext(headers = ...)` itself (`.../server/jsonrpc/http/Routes.kt:83-85`). A Ktor-authenticated principal can't reach `ctx.state` unless the user copies the route (including the SSE code) or re-parses headers in a wrapped RPC. This is the gap versus Python's `ServerCallContextBuilder`.

### Q2. Python SDK server-side customization (main @ `79a6f45`)

| Python point | Citation | What it customizes | Turn layer? | Koog equivalent |
|---|---|---|---|---|
| `RequestHandler` ABC: replace everything | `request_handlers/request_handler.py:34-245` | all RPCs | — | `RequestHandler` interface (a2a-core) |
| Subclass `DefaultRequestHandlerV2`, override `on_*` | `request_handlers/default_request_handler_v2.py:90`; `on_message_send` `:396`; setup is private `_setup_active_task` `:330` | per RPC | wrap only, same as Koog | open overrides |
| ctor `agent_executor` | v2 `:105` | agent logic | — | `AgentExecutor` |
| ctor `task_store: TaskStore \| VersionedTaskStore` | v2 `:106`, `:134-139`; `tasks/task_store.py:7-33`; `cluster/task_store.py:29-82` | persistence; optimistic versioning for multi-replica | storage below the turn layer | `TaskStorage` |
| ctor `queue_manager` | v2 `:108-132`: **ignored in v2, with a DeprecationWarning**; legacy `default_request_handler.py:102` | the old turn-layer hook (event queues) | **yes, removed** | none (that matches S4) |
| ctor `push_config_store`, `push_sender` | v2 `:110-111`; `tasks/push_notification_config_store.py:24-96`; `tasks/push_notification_sender.py:13-25` | push | — | same |
| `BasePushNotificationSender(push_url_validator=...)` | `tasks/base_push_notification_sender.py:28-34` | URL screening at send time | — | (validate at save time) |
| ctor `request_context_builder` | v2 `:112`, `:159-165`; `agent_execution/request_context_builder.py:8-20`; `simple_request_context_builder.py` (fills `related_tasks` from `reference_task_ids`, injects `IDGenerator`s) | the agent's `RequestContext` | **at the edge**: v2 calls it with `task=None` before the active task exists (v2 `:361-367`); the producer attaches the task later (`agent_execution/active_task.py:628`) | none (`newRequestContext` is private) |
| `IDGenerator` | `id_generator.py:14-28` (via the context builder) | task and context ids | — | `IdGenerator` |
| ctor `extended_agent_card`, `extended_card_modifier(card, ctx)` | v2 `:113-117`, `:691` | per-caller card | — | override `onGetExtendedAgentCard` |
| ctor `push_url_validator` | v2 `:118`, `:178-183`, used at `:376`, `:510` | inline and CRUD push URL | — | `validatePushNotificationConfig` |
| ctor `validate_input_modes` | v2 `:119`, `:337-338` | input-mode check against the card | — | none (do it in a wrapped RPC or the executor) |
| ctor `event_stream: TaskEventStream` | v2 `:120`; `cluster/event_stream.py:17-32` | cross-replica event delivery | **yes, the only turn-adjacent plug-in**, and only for clustering | none |
| `ServerCallContextBuilder` | `routes/common.py:91-131` (user, `state['auth']`, headers); passed to `create_jsonrpc_routes(..., context_builder=)` `routes/jsonrpc_routes.py:28-32` | call context from the HTTP request | — | **missing** (Routes.kt:83) |
| `OwnerResolver` | `owner_resolver.py:1-13`; used by stores, e.g. `tasks/inmemory_push_notification_config_store.py:25`, `tasks/database_task_store.py:84` | owner scoping | — | the storage's `ctx` parameter |
| `aclose()` | v2 `:185-191` | drain on shutdown | yes, but it's a method, not a hook | `cancel()` (C2) |
| `shutdown_grace_period` | REST only: `routes/rest_routes.py:35`, `routes/rest_dispatcher.py:82` | SSE grace | — | (C2 design) |
| Server interceptors/middleware | none in the SDK (only `client/interceptors.py:38`); Starlette middleware is used | — | — | Ktor plugins |

Hard-coded in the Python turn layer (not customizable):
- `ActiveTaskRegistry` is constructed inside the handler (v2 `:170-175`) and builds its own `TaskManager` (`agent_execution/active_task_registry.py:61`).
- Follow-ups are **queued FIFO** in an unbounded `_request_queue` (`active_task.py:496-498`), with one request at a time under `_request_lock` (`:590-628`). Nothing rejects a follow-up because a task is busy.
- Subscriber taps have a 1024-event buffer (`events/event_queue.py:31`) and are **evicted when full** (`active_task.py:743-746`; `events/event_queue_v2.py:101-110`, `:210-243`). Neither value is configurable from the handler.
- A crash produces FAILED **with no status message** (`active_task.py:162-176`, `:986-1004`, `:672-680`), with no hook.
- The terminal check runs at start (`active_task.py:520-566`) and again per request in cluster mode (`:616-626`).

### Q3. What Phase 5 (`agents/agents-features/agents-features-a2a-*`) needs

- `agents-features-a2a-server/.../A2AAgentServer.kt:43-46`, `:55`, `:61` hold `RequestContext<SendMessageRequest>` and `TurnEventProcessor` handed over from `AgentExecutor.execute`.
- `A2AAgentServerNodes.kt` uses:
  - `eventProcessor.sendMessage` (`:27`) and `sendTaskEvent` (`:49`);
  - `context.messageStorage.save/getAll/replaceAll` (`:29`, `:68`, `:87`, `:104`);
  - `context.taskStorage.get(taskId, historyLength, includeArtifacts)` (`:135-139`) and `getAll(...)` (`:170-174`).
- `agents-features-a2a-core` only converts messages. It fails to compile because of removed `FilePart`/`FileWithBytes`/`FileWithUri` and a non-exhaustive `Role` `when` (`a2a/.agents/state.md:219`), not because of server APIs.
- It doesn't use `TurnManager`, `Turn`, `A2AServer` subclassing, or any protected hook. The examples (`examples/simple-examples/.../a2a/*/`) and the TCK SUT (`a2a/test-tck/a2a-test-server-tck/.../Main.kt:95`, `TckAgentExecutor.kt:27`, `:82`) only implement `AgentExecutor` and construct `A2AServer`.
- What Phase 5 depends on: the public shapes of `RequestContext` and `ContextTaskStorage`/`ContextMessageStorage`, and the semantics of `sendMessage`/`sendTaskEvent`. Keep these stable through A1/S4/W5.
- Possible future need (hypothesis): a Koog-agent executor would benefit from the B5 "throw an `A2AException` before the first event = request error" rule and from a `failureMessage` hook. Neither needs new API from the feature module.

### Q4. Hook proposal

The lock names follow A1: **slot** = the per-task FIFO slot, **gate** = `processorGate`, **[S]** = the stream lock. The rule from S4/A1 is that the server never calls user code under the gate or [S], except for a non-suspending pure function where the table says so.

Packaging rule:
- **Logic hooks** are `protected open` methods on `A2AServer`, like `validatePushNotificationConfig`. They are passed into `TurnManager` as lambdas (`{ t, e -> failureMessage(t, e) }`), so the turn layer stays closed and the virtual call only happens at run time.
- **Numeric or enum policies** go in one data-only config object passed to the `A2AServer` constructor (e.g. `A2AServerOptions`), forwarded to `TurnManager`.
- No strategy interfaces.

| # | Hook | Signature sketch | Where / locks | Contract | Use case | Risk | Form | Verdict |
|---|---|---|---|---|---|---|---|---|
| H1 | Crash status message | `protected open fun failureMessage(task: Task, error: Throwable, messageId: String): Message?` | `TaskEventStream.markFailed` (`:274`), under gate + [S], NonCancellable, called only when the turn's failure marks FAILED (B5: not for an `A2AException` before the first event) | non-suspending, pure. The server keeps `state = FAILED` and the timestamp, and enforces `taskId`/`contextId`/`role = AGENT`. `null` means no message. If it throws, log it and use the default text. Pass the unwrapped cause (`AgentCancellationFailure.cause`). | localized or classified text ("model quota exceeded, retry later"), a correlation id in `metadata` for support, matching Python's no-message behavior | low. Leaking exception details is the user's choice; the KDoc should warn about it (§13.4). | protected open on A2AServer | **now** (with A1/S4; also reusable for C2-D5) |
| H2 | Operation-aware request check | `protected open fun checkRequiredExtensions(method: A2AMethod, ctx: ServerCallContext)` (or merge it into C2's `checkAccepting(method)`) | first statement of every RPC, no locks | throw an `A2AException` | skip the extension requirement for some RPCs (what the KDoc already promises), maintenance mode | none | change the existing protected open | **now** (cheap; it fixes a misleading KDoc) |
| H3 | `validateFollowUp` | `protected open fun validateFollowUp(task: Task, request: SendMessageRequest, ctx: ServerCallContext)` | under slot + gate, after not-found/context/terminal, **before** the inline config save | non-suspending, no I/O, throw an `A2AException` | accept follow-ups only in INPUT_REQUIRED/AUTH_REQUIRED, check owner metadata, enforce message rules against the stored state | medium: it's a user callback under the gate (which the user rejected for A1). B5 already covers it: an executor (or decorator) that throws an `A2AException` before its first event gives the same error with the task unchanged and the user message not persisted (lazy persistence). The only gain is rejecting before the inline push config is saved. Ownership belongs in `TaskStorage.get(request, ctx)`. | — | **later, only if** someone needs the push-config ordering. Otherwise document the B5 pattern instead. |
| H4 | Follow-up policy while a turn runs | config `maxQueuedFollowUps: Int? = null` (null = unbounded FIFO, as today and in Python; 0 = reject while busy) | slot admission, before waiting; no gate | data only. An over-limit follow-up fails immediately; which error to use is an open question (the spec has none: `-32603`, or `UnsupportedOperation`?) | stop pile-ups behind a long turn; out-of-band AUTH_REQUIRED turns that never return (§7.6.1) make queued follow-ups wait forever | low, if the A1 slot keeps a waiter count | config object | **later** (after A1; design the slot so a counter fits) |
| H5 | Subscriber buffer / back-pressure | config `streamBuffer: Int = 0` (rendezvous) and `onSlowSubscriber: SUSPEND \| EVICT` (evict ends the subscriber's flow with an error) | the fan-out under [S] reads only the data | data only, no callback under [S] | stop one slow SSE client from stalling the agent and the other clients (Python: 1024 + evict, hard-coded) | medium: eviction semantics for the requester's own view (the requester loses events and must get an error, not a silent end), and the interplay with C1/D8 | config object | **later**, once D-5 is decided. If adopted, consider making evict-with-buffer the default (parity with Python). |
| H6 | Shutdown / drain | method parameters: `suspend fun shutdown(gracePeriod: Duration)`; C2's `checkAccepting(method)` | see `c2-shutdown-design.md` | — | rolling deploys | — | method parameters, not hooks | **later** (C2). Reuse H1 if D5 persists FAILED at the deadline. |
| H7 | Call-context building (transport) | `a2aJsonRpcTransportRoute(path, transport, callContext: suspend ApplicationCall.() -> ServerCallContext = { default from headers })` | Ktor route, before dispatch, no server locks | return a ctx; may do I/O | put the Ktor `principal()` or tenant into `ctx.state` without overriding RPCs (Python `ServerCallContextBuilder`) | low; it's a public transport API change (needs the user's approval) | route parameter | **later, but high value** |
| H8 | Request-context builder | `protected open fun newRequestContext(...)` | A1 builds it from the gate read, so it would run under the gate | — | `related_tasks` like Python, extra state | the user rejected callbacks under the gate; a decorating `AgentExecutor` plus `ctx.state` already cover it outside all locks | — | **never** (document the decorator pattern). Optional cheap fix: let `RequestContext` expose an unscoped read for `referenceTaskIds`, since `ContextTaskStorage` can't reach other contexts. That's an open question. |
| H9 | Event transform / redaction | (a) `protected open fun transformAgentEvent(event: TaskEvent, ctx): TaskEvent`, or (b) make `TurnEventProcessor` an interface so an executor decorator can wrap it | (a) in `sendTaskEvent` before validation, under the processor's `turnMutex`; during CancelTask it also runs under the gate, so it **must be non-suspending** | ids, kind and state unchanged (the server re-validates after the transform); applied to agent events only, not to server FAILED/CANCELED | PII redaction, adding trace or agent-version metadata to every event; affects persistence, delivery and push the same way | medium: hidden mutation; (b) has a bigger API surface but no new server rules | (b) preferred if needed | **later / on demand**. A storage decorator covers persistence-only redaction, but not live delivery. |
| H10 | User-message history policy | config `persistUserMessages: LAZY \| NEVER` | [S] | data only | privacy (§3.7 lets the agent decide what's persisted) | NEVER leaves agents unable to add follow-up messages (Task events for existing tasks are ignored; `ContextTaskStorage` has no update; S5 `appendHistory` isn't exposed to agents) | — | **never for now**. Retention and redaction belong in a `TaskStorage` decorator: relax the "store as is" wording at `TaskStorage.kt:121-124` to "preserve `messageId`s and the replacement semantics". |
| H11 | Task-snapshot shaping | — | — | — | trimming artifacts or history in snapshots | three different read paths today: Turn snapshot (gate read, `historyLength`), Subscribe (`TaskStorage.get(GetTaskRequest, ctx)`, `TurnManager.kt:364`), and the blocking SendMessage result (internal `get(taskId, …)`, `A2AServer.kt:726-731`, **not ctx-scoped**) | — | **never as a hook**. Instead, route every client-facing read through the ctx-scoped `TaskStorage.get(request, ctx)` so storages shape and authorize them consistently. That's an open question for A1/storage. |
| H12 | Agent-failure observer | (covered by H1's `error` argument) | — | — | metrics and alerts | — | — | **never as a separate hook**. Logging already happens (`TurnManager.kt:308-311`). |
| H13 | Push retry or filter | — | — | — | retries, pushing only terminal events | the `PushNotificationSender` gets every event and may retry or filter itself | — | **never**. Document that the sender may retry and only delays its own task. |

## Koog status

- The extension points and traps are tabled in Q1.
- What to fix alongside S4/A1:
  - `tasksMutex` stops being `protected` (A1 removes it).
  - `turnManager` becomes non-open (S4).
  - `checkRequiredExtensions` gets an operation (H2).
  - Add H1.
- With S4, `onSendMessageCommon`/`onCancelTask`/`onSubscribeToTask` stay "wrap only". Their KDoc should say so, because overriding them without calling `super` requires `@InternalA2AApi`.

## Testability notes

- **H1:** a unit test in `A2AServer*Test` with a crashing executor and an overridden `failureMessage`. Assert the whole FAILED `TaskStatusUpdateEvent` on the stream and in storage. Also test that a throwing override falls back to the default.
- **H2:** extend `A2AServerRequiredExtensionsTest` with an override that skips one method.
- **H4/H5:** `TurnManagerTest`/`TaskEventStreamTest` with virtual time.
- **H7:** a Ktor `testApplication` route test that asserts `ctx.state` reaches the executor.
- No TCK requirement covers any of these. The TCK only checks the defaults (terminal follow-up → `UnsupportedOperation`; streams), so the defaults must stay unchanged.

## Discrepancies

- **Python, internal:** `AgentExecutor.execute`'s docstring says unhandled exceptions move the task to "`TaskState.TASK_STATE_ERROR`" (`agent_execution/agent_executor.py:29-31`). No such state exists; the code writes `TASK_STATE_FAILED` (`active_task.py:169`, `:678`, `:998`).
- **Koog, internal:** `checkRequiredExtensions`'s KDoc promises per-operation skipping that its signature can't support (`A2AServer.kt:1016-1021`).
- **Koog, internal:** `RequestContext` KDoc says "For referenced tasks, query [taskStorage] directly" (`turn/RequestContext.kt:22`), but `taskStorage` is the context-scoped `ContextTaskStorage`, so referenced tasks in other contexts can't be reached.
- Spec vs. Python vs. TCK: none relevant.

## Open questions

- The error code for a follow-up rejected by H4 (no spec error for "busy").
- Whether the blocking SendMessage result and the Turn snapshot should use the ctx-scoped `TaskStorage.get(request, ctx)` (H11; related to the storage API question).
- Whether `RequestContext` should give agents a ctx-scoped way to read `referenceTaskIds` tasks outside the context (Python `related_tasks`).
- Whether D-5 (buffer + evict) should become the default; this decides H5's shape.
- Whether `TurnEventProcessor` should become an interface (H9b). It affects the Phase 5 feature's `A2AAgentServer` type only nominally.
