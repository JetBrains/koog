# What is left to migrate to A2A v1.x in `a2a-server`, `a2a-test`, `test-tck`, `test-python-a2a-server`, and the Koog modules outside `a2a/` that use A2A?

**Sources checked:**
- **A2A spec repo** (`check-specification`): `/Users/eugene/Documents/JetBrains/projects/a2a-protocol`.
  - Baseline is tag **`v1.0.1`** (`3303592588e3`). Line numbers below refer to `git show v1.0.1:docs/specification.md` and `git show v1.0.1:specification/a2a.proto`.
  - `main` is at `65dadbd9b900` (`v1.0.1-70`). `git pull --ff-only` printed "no such ref was fetched", but `git ls-remote origin main` = local `HEAD` = `65dadbd`, so the checkout is current.
  - `v1.0.1`→`main` is editorial only for the parts used here (PushNotificationConfig→TaskPushNotificationConfig wording, example fixes, new §7.6.4).
  - `v1.0.0` (`17369575`) was also checked for the ErrorInfo wording.
- **A2A Python SDK** (`check-python-sdk`): `/Users/eugene/Documents/JetBrains/projects/a2a-python`.
  - `main` is at `ddce6b87c4e5` (`v1.2.1-3`, behaviour identical to `v1.2.1`).
  - Tag **`v1.1.0`** (`96c14b79325c`) was checked too, because it is the version the Koog Python test server pins.
- **A2A TCK** (`check-a2a-tck`): `/Users/eugene/Documents/JetBrains/projects/a2a-tck` @ `263b9cfaf16a` (no tags). This is **identical** to the commit pinned in `a2a/test-tck/setup_tck.sh:9` and to the `HEAD` of the pinned clone `a2a/test-tck/a2a-tck`. The vendored spec is A2A `v1.0.0` @ `17369575` (`specification/version.json`).
- **Koog**: branch `eugenethedev/a2a-1.0` @ `e45d756bc`.
  - Compile breakages were confirmed with IntelliJ inspections (`lint_files`, errors only). No Gradle builds or tests were run.
  - The inventory outside `a2a/` comes from a delegated read-only sweep, and I spot-checked it (`JokeWriterAgentExecutor.kt:264`, `A2AAgentClientNodes.kt:28-33`, `MessageConverters.kt:110-134`).
- Related reports already in this folder: `koog-core-status.md` (core model gaps) and `koog-transport-client-status.md` (transport and client gaps). Where a server gap really belongs to core or transport, this report points to them rather than repeating them.

**Confidence:** high for compile breakages, the inventory, and the TCK/Python behaviour (all from code and IDE diagnostics). Medium for the "what to build" shape of the session/subscription redesign: the spec states outcomes, not architecture, and AGENTS.md requires user sign-off on session and storage changes.

## Answer

**`a2a-server` is not migrated at all and does not compile.**
- `A2AServer.kt` still implements the v0.3 `RequestHandler`. It uses the `Request<T>`/`Response<T>` wrappers, `TaskIdParams`/`TaskQueryParams`/`TaskPushNotificationConfigParams`, `onResubscribeTask`/`onSetTaskPushNotificationConfig`/`onGetAuthenticatedExtendedAgentCard`, `supportsAuthenticatedExtendedCard`, and `configuration.blocking`.
- The push-notification layer uses the removed `PushNotificationConfig` and `AuthenticationInfo.schemes`.
- `SessionEventProcessor` uses the removed `TaskStatusUpdateEvent.final`.
- `ListTasks` is missing.

**Beyond the mechanical port, v1 requires behaviour changes:**
- Blocking is now the default (`returnImmediately` flips the old polarity).
- `SubscribeToTask` must emit a `Task` snapshot first, reject terminal tasks with `UnsupportedOperationError` and unknown tasks with `TaskNotFoundError`, and keep working across executions. Today a subscription to an input-required task returns an empty stream.
- Sending to a terminal task → `UnsupportedOperationError`. A `contextId`/`taskId` mismatch must be rejected.
- `GetTask` must include artifacts.
- A missing push config → `TaskNotFoundError`.
- `capabilities.extendedAgentCard=false` → `UnsupportedOperationError`.
- `A2A-Version` must be validated (VersionNotSupportedError; an empty header means 0.3).
- Push notifications must deliver `StreamResponse` payloads per event, honour the inline `configuration.taskPushNotificationConfig`, and use the flattened config with a single `scheme`.

**The other test modules:**
- **`a2a-test` is already migrated** (it compiles against the new API).
- **`test-python-a2a-server` is already migrated.** It pins `a2a-sdk[http-server]==1.1.0`, which is a v1 SDK.
- **The server's own tests, the TCK SUT, the examples, `agents-features-a2a-*`, and `docs/docs/a2a/*.md` are not migrated.**
- The TCK SUT also needs a rewrite. The v1 TCK drives behaviour by **messageId prefix** (`tck-complete-task`, `tck-input-required`, `tck-stream-001`, …), not by message text.

**The TCK CI is disabled.** `.github/workflows/a2a-tck-test.yml:22` has `if: false # TODO re-enable after 1.0 is done`, and the pinned TCK (`263b9cf`) is a v1 TCK.
- Re-enabling it needs a compiling, v1-correct server and a rewritten SUT.
- It also needs three transport/core fixes that the TCK enforces as MUST:
  - pre-stream errors returned as plain JSON;
  - `A2A-Version` validation;
  - `ErrorInfo` in `error.data`.

## Requirements

| # | Requirement | Tier | Bindings | Citation |
|---|-------------|------|----------|----------|
| R1 | Blocking by default. `returnImmediately` false/unset → wait for a terminal or interrupted state and return the latest task with all artifacts. `true` → return right after the task is created. No effect for Message results or streaming. | MUST | all (Koog: JSON-RPC) | spec §3.2.2 L446-454; proto L155-160 |
| R2 | A `Message.taskId` must reference an existing task, else `TaskNotFoundError`. Client-supplied ids for new tasks are not supported. | MUST | all | spec §3.4.2 L613-615; §3.1.1 L176 |
| R3 | SendMessage/SendStreamingMessage to a terminal task → `UnsupportedOperationError`. | MUST | all | spec §3.1.1 L175; §3.1.2 L200 |
| R4 | Infer `contextId` from the task when only `taskId` is given. Reject a mismatching `contextId`+`taskId` (error type not specified; Python uses InvalidParams). | MUST | all | spec §3.4.3 L627-628 |
| R5 | A streaming task stream begins with the `Task` and closes at a terminal state. A message-only stream has exactly one Message, then closes. | MUST; capability:streaming | all | spec §3.1.2 L208-210 |
| R6 | SubscribeToTask: the first event is a `Task` snapshot. The stream terminates at a terminal state. A terminal task → `UnsupportedOperationError`. An unknown task → `TaskNotFoundError`. | MUST; capability:streaming | all | spec §3.1.6 L302-311; §9.4.6 L2408 |
| R7 | Events in generation order. Broadcast to all active streams of a task, each getting the same order. Closing one stream doesn't affect the others. | MUST | all | spec §3.5.2 L683-694 |
| R8 | Streaming disabled → SendStreamingMessage/SubscribeToTask return `UnsupportedOperationError`. | MUST; capability:streaming | all | spec §3.3.4 L574 |
| R9 | Push disabled → all four push-config methods return `PushNotificationNotSupportedError`. | MUST; capability:pushNotifications | all | spec §3.3.4 L573 |
| R10 | `extendedAgentCard` false/absent → GetExtendedAgentCard returns `UnsupportedOperationError`. Declared but not configured → `ExtendedAgentCardNotConfiguredError` (-32007). | MUST; capability:extendedAgentCard | all | spec §3.3.4 L575; §3.1.11 L418-419 |
| R11 | A required extension (`required: true`) not declared by the client → `ExtensionSupportRequiredError` (-32008). | MUST; capability:extensions | all | spec §3.3.4 L576; §5.4 L1189 |
| R12 | Validate `A2A-Version` (Major.Minor). Unsupported → `VersionNotSupportedError` (-32009). An empty value means 0.3. | MUST | all | spec §3.6.2 L737-739; §3.6 L708 |
| R13 | GetTask returns the current state **and artifacts**, optionally history. Unknown → `TaskNotFoundError`. | MUST | all | spec §3.1.3 L224-230 |
| R14 | `historyLength`: unset = server default, 0 = no history (field SHOULD be omitted), >0 = at most N most-recent messages. The server MUST NOT exceed it. | MUST (bound) / SHOULD (omit) | all | spec §3.2.4 L465-471; proto L150-154, L667-671 |
| R15 | ListTasks: caller-scoped; cursor pagination; sorted by status timestamp descending. `nextPageToken` is always present (`""` on the last page). Artifacts are omitted unless `includeArtifacts`. pageSize defaults to 50, valid range 1..100. Filters: `contextId`, `status`, `statusTimestampAfter`; `historyLength` applies per task. | MUST | all | spec §3.1.4 L232-262; proto L675-713 |
| R16 | CancelTask returns the updated task. Terminal → `TaskNotCancelableError`. Unknown → `TaskNotFoundError`. | MUST | all | spec §3.1.5 L264-283 |
| R17 | Create push config: flattened `TaskPushNotificationConfig`; returns the config with an assigned id; unknown task → `TaskNotFoundError`; the config persists until task completion or deletion. | MUST; capability:pushNotifications | all | spec §3.1.7 L313-335; proto L469-484 |
| R18 | Get push config: missing config → `TaskNotFoundError`. List: unknown task → `TaskNotFoundError` (pagination is MAY). Delete is idempotent; unknown task → `TaskNotFoundError`. | MUST; capability:pushNotifications | all | spec §3.1.8-3.1.10 L339-402 |
| R19 | The inline push config in `SendMessageConfiguration.taskPushNotificationConfig` (its task id is empty in SendMessage). | MUST (it is part of the request schema); capability:pushNotifications | all | proto L147-149 |
| R20 | Webhook payload = `StreamResponse` (`task`/`message`/`statusUpdate`/`artifactUpdate`). Include `Authorization: <scheme> <credentials>`. At least one delivery attempt per configured webhook. Timeout SHOULD be 10-30 s. | MUST (payload, auth, ≥1 attempt) / SHOULD (timeout) / MAY (retry) | all (webhooks are plain HTTP) | spec §4.3.3 L842-887; §3.1.7 L335 |
| R21 | JSON-RPC errors: the `data` array entries each carry `@type`. A2A errors carry `google.rpc.ErrorInfo{reason, domain:"a2a-protocol.org"}`. | `@type` MUST; ErrorInfo **SHOULD in v1.0.1** (it was MUST in v1.0.0; the TCK enforces MUST) | JSON-RPC | spec v1.0.1 §9.5 L2455; v1.0.0 L2431 |
| R22 | `tenant` on every request (an optional routing id). | MAY for the server (only client rules are normative) | all | proto (every `*Request.tenant`); spec §8.3.2 L2000 |
| R23 | SendMessage MUST return immediately with task or message (async processing). | MUST | all | spec §3.1.1 L180 (read together with R1) |
| R24 | ContentTypeNotSupportedError for unsupported part media types. | MUST if the agent rejects (detection is agent-defined) | all | spec §3.1.1 L174 |

## Details

### 1. `a2a/a2a-server` component inventory

All paths below are relative to `a2a/a2a-server/src/commonMain/kotlin/ai/koog/a2a/server/`.

#### `A2AServer.kt`: the RequestHandler implementation

**Compile breakages** (IDE: 100+ errors):
- Imports of the removed `TaskIdParams`, `TaskPushNotificationConfigParams`, `TaskQueryParams`, `ai.koog.a2a.transport.Request` and `ai.koog.a2a.transport.Response` (`:16-19`, `:36-38`).
- Every override has the v0.3 signature, so it `overrides nothing`. Eleven abstract members of the new `RequestHandler` are unimplemented (`a2a-core/.../transport/ServerTransport.kt:45-155`).
- The new signatures take plain request models and return plain models:
  - `onGetExtendedAgentCard(GetExtendedAgentCardRequest, ctx): AgentCard`
  - `onSendMessage(SendMessageRequest, ctx): ResponseEvent`
  - `onSendMessageStreaming(...): Flow<Event>`
  - `onGetTask(GetTaskRequest)`
  - `onListTasks(ListTasksRequest): ListTasksResponse`
  - `onCancelTask(CancelTaskRequest)`
  - `onSubscribeToTask(SubscribeToTaskRequest): Flow<Event>`
  - `onCreateTaskPushNotificationConfig(TaskPushNotificationConfig)`
  - `onGetTaskPushNotificationConfig(GetTaskPushNotificationConfigRequest)`
  - `onListTaskPushNotificationConfigs(ListTaskPushNotificationConfigsRequest): ListTaskPushNotificationConfigsResponse`
  - `onDeleteTaskPushNotificationConfig(DeleteTaskPushNotificationConfigRequest)`
- Response ids are gone. JSON-RPC ids now live in the transport, so all the `Response(data=…, id=request.id)` plumbing disappears (`:360-363`, `:440`, `:476`, `:484`, `:503-507`, `:564-573`, `:592`, `:605`, `:618-624`, `:634-639`, `:651`).

**Per-operation changes:**

- **GetExtendedAgentCard** (`:351-365`)
  - Today: `agentCard.supportsAuthenticatedExtendedCard` throws `A2AAuthenticatedExtendedCardNotConfiguredException` when the capability is off.
  - v1: use `agentCard.capabilities.extendedAgentCard`.
    - Capability off → `A2AUnsupportedOperationException` (R10).
    - Capability on but `agentCardExtended == null` → the -32007 exception.
  - Python: `default_request_handler_v2.py:660-674` (`@validate` defaults to `UnsupportedOperationError`, `request_handler.py:290-299`).
  - TCK: CORE-CAP-003 fails today, because the Koog SUT declares no extended card.

- **SendMessage common** (`onSendMessageCommon`, `:373-458`)
  - Add a terminal-task check: if `task.status.state.terminal`, throw `A2AUnsupportedOperationException` (R3). Python does this in `active_task.py:529-532`.
  - Add a context-mismatch check: if `message.contextId != null && message.contextId != task.contextId`, throw `A2AInvalidParamsException` (R4). Python: `default_request_handler_v2.py:339-346`.
  - Today Koog silently uses `task.contextId` (`:400-402`).
  - Store `configuration.taskPushNotificationConfig` (if push is enabled) under the resolved task id before execution (R19). Python: `default_request_handler_v2.py:361-371`.
    - Blocked by core: `TaskPushNotificationConfig.taskId` is non-null (`a2a-core/.../model/TaskPushNotificationConfig.kt:19`), so the TCK's inline config without `taskId` fails to decode (see `koog-core-status.md` item 6).
  - Validate `historyLength >= 0` and throw `A2AInvalidParamsException`. Python: `utils/task.py:29-32`.
    - Today `InMemoryTaskStorage.get` calls `require(it >= 0)` (`tasks/InMemoryTaskStorage.kt:29-31`), which throws `IllegalArgumentException`. The transport maps that to -32603.

- **SendMessage** (`:460-487`)
  - Flip the polarity:
    - `configuration?.returnImmediately == true` → first Task event (today's non-blocking path).
    - Otherwise → wait until the execution ends (today's `blocking == true` path, `:468-473`).
  - Koog's "last event when the agent returns" matches Python's fallback (`default_request_handler_v2.py:425-431`, where `active_task.get_task()` is used when no terminal/interrupted Task arrived).
  - Python returns early on the first terminal/interrupted Task (`:405-424`). Koog sessions end when `execute` returns, so the results are equivalent as long as agents return after entering interrupted states.
  - The response must include all artifacts (it already does: `includeArtifacts = true`, `:482`) and apply `historyLength`.

- **SendStreamingMessage** (`:489-495`)
  - The signature changes. The capability check (`checkStreamingSupport`, `:654-658`) stays.
  - First-event rule (R5): Koog relies on the agent sending a `Task` first. Otherwise `InMemoryTaskStorage.update` throws `TaskOperationException` (`tasks/InMemoryTaskStorage.kt:93-94`), which becomes an InternalError.
  - Python raises `InvalidAgentResponseError` (-32006) for the same case (`active_task.py:293-299`).
  - Consider mapping `InvalidEventException`/`TaskOperationException` from agent misbehaviour to `A2AInvalidAgentResponseException`.
  - Follow-up messages to an existing task do not start with a `Task` in Koog or in Python. See Discrepancies.

- **GetTask** (`:497-508`)
  - It passes `includeArtifacts = false` (`:504`), so artifacts are stripped. This violates R13. Use `includeArtifacts = true`. Python returns the full task with history applied (`default_request_handler_v2.py:183-196`).
  - Validate `historyLength` (as above).

- **ListTasks** (missing)
  - Implement it per R15 on top of a new `TaskStorage` list API (see `TaskStorage` below).
  - Python reference:
    - `on_list_tasks` (`default_request_handler_v2.py:198-217`): validates historyLength and pageSize 1..100, clears artifacts unless `include_artifacts`, applies history.
    - `InMemoryTaskStore.list` (`tasks/inmemory_task_store.py:99-172`): filters `context_id`, `status`, `status_timestamp_after` (>=); sorts descending by status timestamp with an id tiebreak (`:137`); opaque cursor token; `page_size` defaults to 50 (`constants.py:8`); `total_size` is computed before pagination; the response `page_size` is the requested or default size.
  - With `encodeDefaults=false`, Koog's `ListTasksResponse.nextPageToken: String` has no default (`a2a-core/.../model/Responses.kt:19-24`), so it is always encoded. That is correct.
  - Authorization scoping is a MUST but is implementation-defined. Koog has no auth, so document that subclasses scope via `ServerCallContext`.

- **CancelTask** (`:510-580`)
  - The params type becomes `CancelTaskRequest` (`.id`, `.metadata`).
  - Keep the existing semantics: terminal and no session → `TaskNotCancelable`; executor must leave the task in `CANCELED`.
  - New issue: when the task is in `INPUT_REQUIRED` there is no session. The ad-hoc `SessionEventProcessor` built at `:543-547` is not connected to any active `SubscribeToTask` stream, so subscribers never see `CANCELED` and never close (R6/R7). This is fixed by the subscription redesign below.
  - Python forces `CANCELED` if the executor's cancel didn't reach a terminal state (`active_task.py:864-876`). Koog throws `TaskNotCancelable` instead (`:567-569`). Both are permitted ("success is not guaranteed", spec §3.1.5 L266).

- **SubscribeToTask** (replaces `onResubscribeTask`, `:582-594`)
  - Today: no session → `return@flow` (empty stream). The method never checks existence or terminal state and never emits a snapshot.
  - Required (R6):
    1. Capability check.
    2. `taskStorage.get` returns null → `A2ATaskNotFoundException`.
    3. Terminal → `A2AUnsupportedOperationException`.
    4. Subscribe to the task's live event stream **before** reading the snapshot, then emit the `Task` snapshot first (Python taps the queue before `get_task()`, `active_task.py:706-719`).
    5. Forward events until a terminal state.
  - Because the subscriber must also survive an execution boundary (TCK STREAM-SUB-002 subscribes to an input-required task, then sends a follow-up that completes it, `tests/compatibility/core_operations/test_task_lifecycle.py:362-420`), the event stream has to be **per task, living until the task is terminal**. It cannot be per `Session`.
  - Python models this as `ActiveTask`, which stays alive across requests until terminal and has a request queue (`active_task.py:488-556`, `678-779`). This is a **session-management design change** and needs user sign-off (AGENTS.md "Changing session management behavior").

- **Create push config** (`:596-606`)
  - Store the flat `TaskPushNotificationConfig`.
  - Check the task exists → `TaskNotFoundError` (Python `:486-496`).
  - Assign an id if it is missing (Python uses the task id: `inmemory_push_notification_config_store.py:51-52`).
  - Return the stored config (R17).

- **Get push config** (`:608-625`)
  - The missing config currently throws `NoSuchElementException` (`:616`), which becomes InternalError. It must be `A2ATaskNotFoundException` (R18; Python `:514-535`; `a2a-test` asserts this at `BaseA2AProtocolTest.kt:396-398`).
  - Also check the task exists.

- **List push configs** (`:627-640`)
  - Return `ListTaskPushNotificationConfigsResponse(configs, nextPageToken = "")`.
  - Unknown task → `TaskNotFoundError` (Python `:617-633`).
  - `pageSize`/`pageToken` are MAY.

- **Delete push config** (`:642-652`)
  - Becomes Unit. Unknown task → `TaskNotFoundError`. A missing config stays idempotent (Python `:643-656`).

- **Version and extension validation** (missing everywhere)
  - Nothing on the server side reads `A2A-Version`. `ai.koog.a2a.validation.isVersionCompatible` (`a2a-core/.../validation/VersionValidation.kt:6-11`) is unused outside the client.
  - Python validates it in the JSON-RPC **dispatcher**, not the handler (`routes/jsonrpc_dispatcher.py:349`, `:500`; `utils/version_validator.py`). An empty value counts as `0.3`; only the major version is compared.
  - Decide whether Koog validates in `JSONRPCServerTransport`/`Routes.kt` (closest to Python, and it applies to any RequestHandler) or in `A2AServer`. This is a design decision.
  - Header lookup must be case-insensitive. `ServerCallContext.headers` is a plain `Map` built by `call.request.headers.toMap()` (`a2a-transport-server-jsonrpc-http/.../Routes.kt:80-82`). Whether Ktor preserves the original header-name case is an unverified hypothesis.
  - `ExtensionSupportRequiredError` (R11) cannot be thrown at all: there is no `-32008` code or exception in core (`a2a-core/.../exceptions/Exceptions.kt:7-24`).
  - Python never raises it automatically. It only exposes `requested_extensions` on the context (`server/routes/common.py:88`; `errors.py:103` is the only definition). So a server-side check is a spec MUST with no reference behaviour. Exposing parsed `A2A-Extensions` on `RequestContext` is the minimum.

- **KDoc**: `:60` links v0.3. The examples at `:119-151`, `:169-176`, `:203-244` use `Request<MessageSendParams>`, `TaskState.Working`, `Role.Agent`, `final = true` and `A2AConsts`. All of these need rewriting.

#### `agent/AgentExecutor.kt`

- **Compile:** `TaskIdParams` (`:9`, `:131`) becomes `RequestContext<CancelTaskRequest>`.
- **Semantics:**
  - `execute` stays the same.
  - Document that agents must return after entering `INPUT_REQUIRED`/`AUTH_REQUIRED`, because with `final` removed, returning is the only end-of-turn signal.
  - Document that the first task event must be a `Task`.
- **KDoc** `:37-72` and `:103-120` use `TaskState.Working`, `TaskState.Completed`, `TaskState.Canceled`, `Role.Agent` and `final = true`.

#### `session/RequestContext.kt` (`:25-33`)

- It compiles.
- Optional v1 additions:
  - `tenant` (from `params`: `Request.tenant`, R22, MAY);
  - requested extensions from `A2A-Extensions`;
  - the negotiated version.
- Python's `RequestContext` exposes `tenant` and `requested_extensions` (`agent_execution/context.py:159-166`). This is MAY.

#### `session/SessionEventProcessor.kt`

- **Compile:** `event.final` (`:144`). The final-event rule becomes `(event is TaskStatusUpdateEvent || event is Task) && state.terminal`.
- Update the class KDoc rule "Final event enforcement" (`:34`).
- **Semantics:**
  - Interrupted states no longer close the processor. That is correct: the session ends when `execute` returns.
  - Examples that used `final = true` on `INPUT_REQUIRED` keep working only if the agent returns (see `JokeWriterAgentExecutor.kt:264` below).
- **Broadcast:** `MutableSharedFlow<FlowEvent>()` has no buffer (`:85`), so a slow subscriber back-pressures the agent. R7 is still met (same order to all). Python instead evicts lagging subscribers (`active_task.py:701-704`). This is optional.

#### `session/Session.kt`, `session/SessionManager.kt`

- They compile apart from the push-sender types (`SessionManager.kt:101-103`).
- **Semantics to change:**
  1. **Push timing and payload.** Today one push is sent per session, after the session ends, with a raw `Task`, and only if the first event was a `TaskEvent` (`:95-110`). Errors are swallowed (`// TODO log error`, `:105`).
     - v1 (R20, Python `active_task.py:326-350` + `base_push_notification_sender.py:76-130`): push every `Task`/`TaskStatusUpdateEvent`/`TaskArtifactUpdateEvent` as a `StreamResponse`.
     - At minimum, wrap the payload as a `StreamResponse`. The TCK PUSH-DELIVER-003 validates the body against the `StreamResponse` schema.
     - Per-event vs. per-turn delivery is a design choice. Both satisfy "at least once per webhook". Python is per event.
  2. **Session lifetime vs. subscription lifetime.** Today sessions are removed when `agentJob` completes (`:76-93`). The task-lifetime event stream needed for SubscribeToTask (see A2AServer) has to live here or in a new component. This needs user sign-off.

#### `session/IdGenerator.kt`

- No change needed: ids are already simple UUIDs, which matches the v1 ID simplification.

#### `tasks/TaskStorage.kt` + `tasks/InMemoryTaskStorage.kt`

- They compile.
- **Required:** a list/query API for ListTasks: filters, sort by status timestamp descending, cursor, total count, historyLength, includeArtifacts. Example shape: `list(contextId, status, statusTimestampAfter, pageSize, pageToken, historyLength, includeArtifacts): Page`.
  - This is a **storage interface change**, so it needs user sign-off (AGENTS.md).
  - `ContextTaskStorage` (`TaskStorage.kt:96-161`) may want a context-scoped variant.
- **historyLength:** `InMemoryTaskStorage.get` returns `[]` for 0 (`:39-41`), which is encoded as `"history":[]`.
  - Spec SHOULD omit (R14). The TCK accepts empty (`test_task_history.py:66-80` checks falsiness). Returning `null` for 0 would satisfy the SHOULD.
  - The negative-value check should become `A2AInvalidParamsException`, or be validated in the server.
- **History population:** `TaskStatusUpdateEvent` moves the *previous* `status.message` into history (`:96-100`). Incoming user follow-up messages are **not** persisted unless the agent includes them.
  - Python persists the request message on the first modification event (`active_task.py:306-321`).
  - TCK CORE-HIST-005/006 (SHOULD) expect the two follow-up user texts in history in order (`test_task_history.py:236-330`). This is a server-vs-agent responsibility decision.

#### `messages/MessageStorage.kt`, `messages/InMemoryMessageStorage.kt`

- No v1 change. `Message` changed only in model shape. These compile, and so do their tests.

#### `notifications/PushNotificationConfigStorage.kt` + `InMemoryPushNotificationConfigStorage.kt`

- **Compile:** they use the removed `PushNotificationConfig` (`:3`, `:19`, `:26`, `:33`; InMemory `:4`, `:15-49`).
- Change them to store the flattened `TaskPushNotificationConfig`. Example: `save(config)`, `get(taskId, id: String)`, `getAll(taskId)` (with optional pagination), `delete(taskId, id?)`.
- The `String?` config-id key (`InMemory…:15`) should go away once ids are assigned on create.
- This is a storage interface change, so it needs user sign-off.

#### `notifications/PushNotificationSender.kt`

- **Compile:** `PushNotificationConfig` (`:3`, `:18`).
- Change the signature to `send(config: TaskPushNotificationConfig, event: Event /* StreamResponse */)`.
- The KDoc links v0.3 §9.5 (`:9`). It should be §4.3.3.

#### `notifications/SimplePushNotificationSender.kt`

- **Compile:** `PushNotificationConfig` (`:4`, `:36`) and `auth.schemes.firstOrNull()` (`:43`). Use `auth.scheme`.
- **Payload:** `setBody(task)` (`:55`) with a reified `Task` type would use `Task.serializer()`, which is unwrapped.
  - It must serialize through the `Event` type, so that core's `EventSerializer` (`a2a-core/.../serialization/Serialization.kt:171-179`) emits `{"task":…}` / `{"statusUpdate":…}` / `{"artifactUpdate":…}`.
  - Call `setBody<Event>(event)` or encode explicitly.
- **JSON configuration:** the default `json: Json = Json` (`:22`) has `explicitNulls = true`, so null optionals would be emitted as `null`.
  - Use the A2A JSON settings (`explicitNulls=false`, `encodeDefaults=false`, as in `JSONRPCJson`).
  - Whether the TCK schema rejects nulls is unverified. ProtoJSON omission is the safe choice.
- **Headers:**
  - `Authorization: <scheme> <credentials>` is correct (R20).
  - `X-A2A-Notification-Token` is not in the v1 spec, but Python sends it too (`base_push_notification_sender.py:101-102`). It is harmless.
  - Add a request timeout (SHOULD be 10-30 s).

#### `exceptions/Exceptions.kt` (server)

- No change. Consider mapping `InvalidEventException` to `A2AInvalidAgentResponseException` at the server boundary (Python behaviour).

#### Server-side dependencies in other already-migrated modules (not in scope, but blocking TCK MUSTs)

- **Pre-stream errors.** `JSONRPCServerTransport.handleRequest` calls `respondStreaming(...)` before the flow runs (`a2a-transport-core-jsonrpc/.../JSONRPCServerTransport.kt:89-93`). As a result, errors raised inside `onSubscribeToTask`/`onSendMessageStreaming` (TaskNotFound, UnsupportedOperation, capability) are sent **inside an SSE stream**.
  - The TCK client treats an SSE response as success (`tck/transport/jsonrpc_client.py:149-167`), so STREAM-SUB-004 fails (`tests/compatibility/jsonrpc/test_sse_streaming.py:176-205`).
  - Python peeks the first event before choosing SSE (`routes/jsonrpc_dispatcher.py:381-385`).
  - Fix this in the transport (see `koog-transport-client-status.md`), or make the server's checks eager.
- **ErrorInfo.** Every server-thrown exception has `details = emptyList()`, so `error.data` is `[]`. This fails TCK JSONRPC-ERR-003 (`tests/compatibility/jsonrpc/test_error_info.py`). Python always emits a leading `ErrorInfo{reason, domain, metadata}` (`utils/error_handlers.py:50-63`). The fix belongs in core or transport (`koog-core-status.md` item 10).
- **Dead branch.** `A2AMethod.SubscribeToTask` has `streaming = true`, so the non-streaming branch at `JSONRPCServerTransport.kt:128-129` is dead. Harmless.

### 2. Tests and harnesses

#### `a2a/a2a-server` tests (none compile)

**`src/jvmTest/.../jsonrpc/BaseA2AServerJsonRpcTest.kt`**
- `A2AClient(transport, agentCardResolver=…)` (`:82-89`) → `A2AClient(card = UrlAgentCardResolver(...).resolve(), transport)` (see `a2a-client/.../A2AClient.kt:35-38`).
- `client.connect()` (`:93`) → remove.
- Agent cards (`:101-183`) use `protocolVersion`, `url`, `preferredTransport`, `supportedInterfaces = null`, `stateTransitionHistory` and `supportsAuthenticatedExtendedCard`. Rebuild them so that the extended card **exactly equals** `BaseA2AProtocolTest.kt:69-116`:
  - `supportedInterfaces = [AgentInterface("http://localhost:9999/", JSONRPC, "1.0")]`
  - `capabilities(streaming=true, pushNotifications=true, extendedAgentCard=true)`
  - version `"1.0.1"`, plus the two skills.

**`.../jsonrpc/A2AServerJsonRpcIntegrationTest.kt`**
- The **whole class is `@Disabled("Flaky test")`** (`:47`), so server integration coverage is off today.
- It overrides the removed `` `test get agent card` `` (`:66-68`; removed from the base in `b31650bf6`).
- `Request`/`TaskIdParams`/`.data` (`:112-131`, `:179-186`) and `resubscribeTask` (`:136`) → `subscribeToTask`.
- `SendMessageConfiguration(blocking=…)` (`:224`) → `returnImmediately = !blocking`.

**`.../TestAgentExecutor.kt`**
- `TaskIdParams` (`:9`, `:194`) → `CancelTaskRequest`.
- `final =` (`:72`, `:92`, `:149`, `:215`) → remove.
- `doLongRunningTask` (`:114-152`) never sends `COMPLETED`, unlike the Python counterpart (`test-python-a2a-server/src/agent_executor.py:128-141`). Under v1 blocking defaults, align it so the shared base tests behave the same against both servers.

**`.../StressA2AServerJsonRpcIntegrationTest.kt`**
- It compiles. It inherits from the broken base.

**`src/commonTest`**
- `InMemoryPushNotificationConfigStorageTest.kt` (`:3`, `:20-84`) and `SessionManagerTest.kt` (`:4`, `:42-45`, `:247`) use `PushNotificationConfig`.
- `final =` appears in:
  - `SessionEventProcessorTest.kt:160`, `:191`, `:264`, `:286`, `:367`;
  - `SessionManagerTest.kt:152`, `:196`, `:215`, `:265`;
  - `InMemoryTaskStorageTest.kt:108`, `:133`, `:272`, `:293`, `:314`.
- `task_testSendEventAfterFinalEventFails` (`SessionEventProcessorTest.kt:184-210`) uses `COMPLETED` (terminal), so removing `final` keeps its intent.
- `testSessionWithPushNotifications` (`SessionManagerTest.kt:242-281`) asserts the old per-session raw-Task push and must be rewritten for the new push contract.

#### `a2a/a2a-test`: already migrated

- `BaseA2AProtocolTest.kt` compiles against the v1 API (no IDE errors). It was migrated in `ccd5328db`, `54b60f35e`, `b31650bf6`, `73f71807d` and `24e5aebd5`.
- It now encodes v1 expectations the Koog server must meet:
  - `test send message streaming`: exactly 3 events — Task(SUBMITTED, history=[user]), WORKING, COMPLETED (`:154-198`).
  - `test subscribe to task`: uses `returnImmediately = true` and expects events from a running task (`:294-342`).
  - The push test expects `A2ATaskNotFoundException` after delete (`:396-398`).
  - The extended-card shape (`:69-116`).
- It has no tests for ListTasks pagination or ordering, terminal-task rejections, the subscribe first event, or version errors. Those would need new tests.

#### `a2a/test-python-a2a-server`: already migrated

- Pins `a2a-sdk[http-server]==1.1.0` (`pyproject.toml:7`; `uv.lock:10-11`). This **is a v1 SDK**:
  - `DefaultRequestHandler = DefaultRequestHandlerV2` at v1.1.0 (`src/a2a/server/request_handlers/__init__.py:46`);
  - version validation (`jsonrpc_dispatcher.py:344`, `:497` at v1.1.0);
  - first-event peek (`:377`);
  - subscribe with `include_initial_task=True` (`default_request_handler_v2.py:394` at v1.1.0).
- `src/main.py:48-103` uses the v1 card (`supported_interfaces`, `extended_agent_card`) and route factories. `src/agent_executor.py` uses v1 `TaskState` names.
- Latest is `v1.2.1`. One relevant 1.1.0→1.2.1 difference matters:
  - At 1.1.0, sending to a terminal task, or subscribing to one, raises **`InvalidParamsError` (-32602)**. This happens in `ActiveTask.start` (`git show v1.1.0:src/a2a/server/agent_execution/active_task.py:460-465`).
  - At 1.2.1 it is `UnsupportedOperationError` (-32004), as the spec requires (`active_task.py:529-532`; `default_request_handler_v2.py:555-556`).
  - Bumping is only needed if the shared `a2a-test` base starts asserting those errors against the Python server. Otherwise it is optional.
- `src/__pycache__/` exists locally but is git-ignored (`.gitignore`), not tracked.

#### `a2a/test-tck/a2a-test-server-tck` (SUT): not migrated

**`Main.kt`**
- `OAuthFlows` (`:11`, `:31`) → core's `OAuth2SecurityScheme(flows = AuthorizationCodeOAuthFlow(...))`.
- `protocolVersion`/`url`/`preferredTransport` (`:51-56`) → `AgentInterface(url="http://localhost:9999/a2a", protocolBinding=JSONRPC, protocolVersion="1.0")`. The `AgentInterface` at `:58-61` lacks the required `protocolVersion`.
- `supportsAuthenticatedExtendedCard` (`:82`) → `capabilities.extendedAgentCard`.
- **Capabilities:** only `streaming = true` (`:63-65`), although `pushConfigStorage` is wired (`:99`) and no `pushSender` is.
  - With `pushNotifications` absent, CORE-CAP-001 runs, which is fine.
  - If push is declared, PUSH-CREATE/GET/LIST/DEL/DELIVER-001..003 run and need R17-R20, the inline config (R19), a `SimplePushNotificationSender`, and the core `taskId`-optional fix.
- **Extended card:** not declared → CORE-CAP-003 expects `UnsupportedOperationError` (server gap). If declared and configured, CARD-EXT-001 passes and CARD-EXT-002 is skipped.

**`TckAgentExecutor.kt`**
- `TaskIdParams` (`:9`, `:83`) and `final =` (`:76`, `:99`, `:134`, `:175`, `:195`, `:216`).
- **Behaviour mismatch with the v1 TCK.** The executor routes on lower-cased text (`:31-34`, `:221-228`). The TCK drives the SUT by **`messageId` prefix** (`docs/SUT_REQUIREMENTS.md` "SUT Behavior: Gherkin Scenarios"; `scenarios/core_operations.feature`, `scenarios/streaming.feature`).
- **Required scenarios.** For a new task, first send `Task(SUBMITTED, history=[userMessage])`, as Python does (`sut/a2a-python/sut_agent.py:71-72`). Then:

  | messageId prefix | Behaviour (Python reference `sut_agent.py`) | TCK users |
  |---|---|---|
  | `tck-complete-task` | `COMPLETED` with agent message "Hello from TCK" (`:120-122`). This is also the **default** for unknown prefixes (`:158-163`). | CORE-SEND-001/002, EXECUTION-MODE, MULTI-*, HIST-*, CANCEL-002, SUB-002/003 setup |
  | `tck-input-required` | `INPUT_REQUIRED`, then return (`:116-118`). Must work as a follow-up on an existing task too (`tests/compatibility/_task_helpers.py:143-188`). | CANCEL-001, MULTI-005, SUB-001/002, ORDER-002..004, PUSH-DELIVER setup |
  | `tck-message-response` | Return a direct `Message` "Direct message response" (`:112-114`). | DM-MSG-001 |
  | `tck-artifact-text` / `-file` / `-file-url` / `-data` | Add an artifact (text / `raw=b"tck"` + `text/plain` + `output.txt` / url + filename + mediaType / data `{"key":"value","count":42}`), then `COMPLETED` (`:107-137`). Match `tck-artifact-file-url` before `tck-artifact-file`. | DM-ART-001, DM-PART-001 |
  | `tck-reject-task` | Raise an error (`:139-140`). | none found in tests |
  | `tck-stream-001` / `-003` / `-ordering-001` / `-artifact-text` / `-artifact-file` | `WORKING` → artifact → `COMPLETED` (`:89-105`, `:142-156`). | CORE-STREAM-001/003, STREAM-ORDER-001, JSONRPC-SSE-001 |
  | `tck-stream-002` | `COMPLETED` only (`:148-150`). | CORE-STREAM-002 |
  | `tck-stream-artifact-chunked` | `WORKING`, then the artifact chunk "chunk-1 " (append), then "chunk-2" (append, `lastChunk`), then `COMPLETED` (`:76-81`). | streaming artifacts |
  | `test-resubscribe-message-id` | `WORKING`, then ≥ 2×`TCK_STREAMING_TIMEOUT` (default 4 s), then `COMPLETED` (`:83-87`; `docs/SUT_REQUIREMENTS.md`). | resubscribe |

- **Cancel:** emit `CANCELED` (it already does, `:82-102`).

**Build:** `build.gradle.kts` is fine.

### 3. Koog modules outside `a2a/` that depend on A2A

All of the following are broken. Either they reference removed core/client symbols, or they depend on the non-compiling `a2a-server`. The aggregate builds `koog-agents/build.gradle.kts:25-36`, `koog-agents-additions/build.gradle.kts:26-37` and `docs/build.gradle.kts:41-46` pull them in, so `./gradlew build` fails until they are fixed.

**`agents/agents-features/agents-features-a2a-core`** (independent of the server; broken by the core Part redesign)
- `MessageConverters.kt:4-6`, `:110-134`, `:146-158`: `FilePart`/`FileWithBytes`/`FileWithUri`/`mimeType`/`name`/`bytes` (base64) → `FileBytesPart(raw: ByteArray, filename, mediaType)` / `FileUrlPart(url, filename, mediaType)`.
- The A2A→Koog mapping should probably produce `AttachmentContent.Binary.Bytes`.
- `MessageConvertersTest.kt:4-6`, `:45-46`, `:64`, `:182-190` need the same changes. The expected `Binary.Base64` at `:64` changes.
- `MessageA2AMetadata.kt` is fine.

**`agents-features-a2a-client`**: `A2AAgentClientNodes.kt`
- `TaskIdParams`/`TaskQueryParams`/`TaskPushNotificationConfigParams` and `transport.Request`/`Response` (`:8-14`).
- `@Serializable A2AClientRequest(callContext: ClientCallContext, …)` (`:28-33`): `ClientCallContext` is no longer serializable (`a2a-core/.../transport/ClientTransport.kt:192`).
- `cachedAgentCard()` (`:61`, `:95`) → `card`.
- `getAgentCard()` (`:79`) has no equivalent.
- `getAuthenticatedExtendedAgentCard` (`:102-111`) → `getExtendedAgentCard`.
- `Request(data=…)` / `.data` (`:112`–`:289`).
- `resubscribeTask` (`:200-209`) → `subscribeToTask`.
- `setTaskPushNotificationConfig` (`:219-228`) → `createTaskPushNotificationConfig`.
- `listTaskPushNotificationConfig` (`:259-268`) → `listTaskPushNotificationConfigs` returning `ListTaskPushNotificationConfigsResponse`.
- There is no `listTasks` node.
- `A2AAgentClient.kt` is clean.

**`agents-features-a2a-server`**
- `A2AAgentServer.kt:4-5`, `:44`, `:55` and `A2AAgentServerNodes.kt:27-174` use `RequestContext`, `SessionEventProcessor`, `context.messageStorage`/`taskStorage`.
- They have no v0.3 model symbols but must follow whatever server API results. `RequestContext<SendMessageRequest>` is already used.

**Examples** (`examples/simple-examples/src/main/kotlin/ai/koog/agents/example/a2a/`)
- `simplejoke/Client.kt:11`, `:35-48`, `:74-77` and `advancedjoke/Client.kt:16`, `:38-41`, `:73-75`, `:113`, `:120`: `Request`, `agentCardResolver`, `connect()`, `cachedAgentCard()`, `.data`, `event.kind`, `event.final`.
- `simplejoke/Server.kt:23-55` and `advancedjoke/Server.kt:23-55`: v0.3 card fields, missing `AgentInterface.protocolVersion`, `stateTransitionHistory`, `supportsAuthenticatedExtendedCard`.
- `advancedjoke/JokeWriterAgentExecutor.kt:162`, `:264`, `:297`: `final =`.
  - `:264` sets `final = true` on `INPUT_REQUIRED`, which is not terminal, so the session ended only because of `final`. After the migration the agent must return after that event.
  - The client side (`Client.kt:113`, `:120`) must use `status.state.terminal`, or interrupted states.
- Semantic shift: `simplejoke/Client.kt:73-77` sends without configuration. Under v1 that is blocking by default, so the result changes.

**Docs** (`docs/docs/a2a/*.md`)
- `a2a-client.md`, `a2a-server.md` and `a2a-koog-integration.md` are full of v0.3 API: `MessageSendParams`, `TaskIdParams`, `TaskQueryParams`, `Request(data=…)`, `Role.User`/`Agent`, `TaskState.Working`, `final`, `protocolVersion`/`url`/`preferredTransport`/`additionalInterfaces`, `stateTransitionHistory`, `supportsAuthenticatedExtendedCard`, `connect()`, `cachedAgentCard()`, `CommunicationEvent`, `nodeA2AClientGetAgentCard`.
- `index.md:34` says "v0.3.0".
- These snippets are **not Knit-compiled**: there are no `<!--- KNIT` markers in `docs/docs/a2a/*.md`. `docs/build.gradle.kts` still depends on all `:a2a:*` modules (`:41-46`), including `a2a-server`.
- Per the delegated sweep, `docs/src/main/kotlin` is git-ignored and has no A2A files.

**Other matches**
- `convention-plugin-ai/.../ai.kotlin.{jvm,multiplatform}.gradle.kts` reference `InternalA2AApi`, which still exists. They are fine.

### 4. TCK CI status and what re-enabling needs

**Status**
- Disabled by commit `3e7cd2d88` ("temp disable a2a-tck ci"): `.github/workflows/a2a-tck-test.yml:22` has `if: false # TODO re-enable after 1.0 is done`.
- The pin is already v1: `setup_tck.sh:9` = `263b9cf…` ("A2A version 1.0.x"), changed in `44fa019c1`/`c5c1597c3`. It is the current TCK `main`.
- The workflow steps still fit the TCK:
  - Python 3.12 is OK (the TCK needs `>=3.11`, `pyproject.toml:10`).
  - `uv sync --all-packages --all-extras` is fine (no workspace).
  - `run_tck.py --sut-host` is current. Reports go to `reports/*.html` (`run_tck.py:24`, `:62-66`), which matches the artifact path.
  - The "Responding at http://0.0.0.0:9999" wait is unchanged Ktor behaviour.

**To make the job green against MUST-level tests:**
1. `a2a-server` compiles and implements R1-R18 (at least the ones exercised).
   - Key TCK checks: CORE-SEND-002 (UnsupportedOperation for terminal), CORE-MULTI-006, CORE-CAP-003, STREAM-SUB-001/002/003, STREAM-ORDER-002..004 (subscribe to an input-required task).
   - CORE-LIST-001..005 are auto-dispatched with sample inputs; only success and schema are validated (`tck/requirements` dump, `tests/compatibility/core_operations/test_requirements.py`).
   - CORE-GET-001 and CORE-CANCEL-001/002.
2. SUT rewritten with the v1 agent card and the messageId-prefix scenarios.
3. Transport/core fixes:
   - plain-JSON pre-stream errors (STREAM-SUB-004, MUST);
   - `A2A-Version` validation (VER-SERVER-002, MUST; `test_error_handling.py:550-577`; also `test_error_codes.py` version cases);
   - `ErrorInfo` with reason (JSONRPC-ERR-003, MUST in the TCK; `test_error_info.py`).
4. If the SUT declares `pushNotifications`: R17-R20 plus the core `TaskPushNotificationConfig.taskId` optional fix. Otherwise leave it undeclared.
5. Remove `if: false`. Optionally pass `--transport jsonrpc`, though transports not declared on the card are skipped anyway (`README.md:94`).
6. SHOULD failures are reported as xfail and MAY tests are skipped (README "Requirement levels"), so they don't fail CI. Examples: CORE-HIST-001/003/005/006 and CARD-CACHE-001/002 (Koog's agent-card route sets no `Cache-Control`/`ETag`, `Routes.kt:114-125`).

## Koog status

Summarised from §1-§4:
- **Compiles:**
  - `a2a-test` (`BaseA2AProtocolTest.kt`);
  - `a2a-server`'s `RequestContext`, `IdGenerator`, `Session`, `TaskStorage`, `InMemoryTaskStorage`, `MessageStorage` and its tests;
  - `MessageA2AMetadata`, `A2AAgentClient.kt`.
- **Does not compile:**
  - `A2AServer.kt`, `AgentExecutor.kt`, `SessionEventProcessor.kt`, `SessionManager.kt`, all four `notifications/*.kt`;
  - all `a2a-server` jvmTest files except Stress (which inherits the broken base), plus the commonTest files listed in §2;
  - the SUT (`Main.kt`, `TckAgentExecutor.kt`);
  - `agents-features-a2a-core` (MessageConverters + test), `agents-features-a2a-client` (Nodes), `agents-features-a2a-server` (transitively);
  - the examples.
- **Exact behavioural gaps** (beyond compiling):
  - blocking-default inversion;
  - ListTasks missing;
  - SubscribeToTask snapshot/terminal/not-found/cross-execution;
  - terminal-task send;
  - context mismatch;
  - GetTask artifacts;
  - push config not-found and task-existence errors;
  - inline push config;
  - push payload format and timing;
  - extended-card capability error;
  - version and extension validation;
  - historyLength validation error type;
  - InvalidAgentResponse mapping;
  - user-message history persistence (SHOULD-level, TCK HIST-005/006).

**kotlinx.serialization notes for implementers:**
- Return `ResponseEvent`/`Event`-typed values from the handler so the transport's `serializer<ResponseEvent>()`/`serializer<Event>()` emit the `{"task":…}` / `{"message":…}` wrappers. This is already how `JSONRPCServerTransport.onRequest` reifies types (`JSONRPCServerTransport.kt:182-202`).
- The same applies to push bodies: use the `Event` static type.
- `ListTasksResponse.nextPageToken` must stay without a default so it is always encoded.
- `ListTaskPushNotificationConfigsResponse.nextPageToken = ""` is omitted when empty (`encodeDefaults=false`). That is fine, because the proto field is not REQUIRED (`proto L806-811`).

## Testability notes

**Unit tests (commonTest, in-memory storages, no transport):**
- `onSendMessage` with `returnImmediately` true/false/unset.
- Send to a terminal task → `A2AUnsupportedOperationException`.
- Mismatched contextId → `A2AInvalidParamsException`.
- `onGetTask` includes artifacts and honours historyLength 0/N/negative.
- `onListTasks`: filters, order, page boundaries, `nextPageToken == ""` on the last page, `totalSize`, artifacts omitted by default, pageSize 0/101 → InvalidParams.
- `onSubscribeToTask`:
  - first element is `Task`;
  - terminal → UnsupportedOperation;
  - unknown → TaskNotFound;
  - a subscriber on an INPUT_REQUIRED task receives the follow-up execution's events and completes at COMPLETED;
  - two subscribers see identical sequences;
  - cancelling one doesn't affect the other.
- Push config CRUD error cases.
- Extended card with capability off/on/missing.

**Push sender:** use a Ktor `MockEngine` to assert the body is a `StreamResponse` (`{"statusUpdate":{…}}`), the `Authorization` header, and that no `null` fields are emitted.

**Integration (`a2a-test` base, shared by the Python and Koog servers):** add base tests for ListTasks pagination, subscribe-first-event, terminal rejections, and the VersionNotSupported header. Re-enable `A2AServerJsonRpcIntegrationTest` once the flakiness is understood (its class-level `@Disabled` is at `:47`).

**TCK IDs per gap:**

| Gap | TCK IDs |
|---|---|
| Blocking / non-blocking | CORE-EXECUTION-MODE-001/002 |
| Terminal send | CORE-SEND-002 |
| Context | CORE-MULTI-005/006 |
| Get | CORE-GET-001/002 |
| List | CORE-LIST-001..005 |
| Cancel | CORE-CANCEL-001..003 |
| Subscribe | STREAM-SUB-001..004 |
| Multi-stream | STREAM-ORDER-001..004 |
| Capabilities | CORE-CAP-001..004, CARD-EXT-001/002 |
| Version | VER-SERVER-002/003 |
| Errors | JSONRPC-ERR-001..003, JSONRPC-SSE-001/002 |
| Push | PUSH-CREATE/GET/LIST/DEL-*, PUSH-DELIVER-001..003 (needs the `--webhook-host` reachable from the SUT; default `localhost`, `tests/compatibility/conftest.py:70-78`) |
| History | CORE-HIST-001..006 |
| Artifacts / data model | DM-ART-001, DM-PART-001, DM-MSG-001, DM-SERIAL-* |

**Unobservable or untested:**
- ListTasks authorization scoping (CORE-LIST-001 only checks success).
- AUTH-* and CARD-SIGN-* (no tests reference them).
- CORE-SEND-003: the TCK expects success, not the error (see Discrepancies).
- ExtensionSupportRequired: skipped unless the card declares `urn:a2a:tck:required-extension` as required (`test_error_handling.py:300-340`).

## Discrepancies

1. **ErrorInfo level.** Spec v1.0.1 §9.5 says SHOULD (L2455). v1.0.0 said MUST (L2431). The TCK vendors v1.0.0 and enforces MUST (JSONRPC-ERR-003). Python always emits ErrorInfo.
2. **ExtensionSupportRequiredError.** It is a spec MUST (§3.3.4 L576), but Python never raises it. It only parses `A2A-Extensions` into the context (`server/routes/common.py:88`; the class at `utils/errors.py:103` is never raised in `src/`).
3. **Version comparison.** The spec says to match `Major.Minor` (L737). Python compares the major version only (`utils/version_validator.py`, `_is_version_compatible`). Koog's `isVersionCompatible` is also major-only (`VersionValidation.kt:6-11`).
4. **Stream first event for follow-ups.** The spec says a task stream MUST begin with the Task (L210). Python's `on_message_send_stream` uses `include_initial_task=False` (`default_request_handler_v2.py:466`). Its SUT emits a Task only when `current_task is None` (`sut_agent.py:71-72`). So Python follow-up streams start with `statusUpdate`, and Koog's do too.
5. **TCK CORE-SEND-003.** The title and description say ContentTypeNotSupportedError, but no `expected_error` is set, so the generic runner checks for success (`tck/requirements/core_operations.py:97-124`). Separately, `test_error_codes.py:198-249` maps a wrong HTTP `Content-Type` to ContentTypeNotSupportedError. The spec defines that error for message-part media types (L174).
6. **Webhook Content-Type.** The spec example uses `application/a2a+json` (L851-856). Python posts with httpx `json=`, i.e. `application/json` (`base_push_notification_sender.py:118-121`).
7. **`createdAt`/`lastModified` fields.** `a2a/a2a_1.0.0_changelog.md:86` and `:158` (copied from upstream "what's new") claim `Task.createdAt`/`lastModified` and `PushNotificationConfig.createdAt`. The v1.0.1 proto has neither (`a2a.proto` L167-185, L469-484).
8. **PushNotificationConfig name.** Spec v1.0.1 text names `PushNotificationConfig` (L326, L873), but the proto has only `TaskPushNotificationConfig`. This is fixed on `main` (unreleased).
9. **Python 1.1.0 (pinned by the Koog test server) vs. spec.** For a terminal task on send/subscribe it returns `InvalidParamsError`, where the spec says `UnsupportedOperationError` (§3.1.1 L175, §3.1.6 L305). Fixed in 1.2.x.
10. **Cancel semantics.** Python forces CANCELED after the executor's cancel (`active_task.py:864-876`). Koog requires the executor to set CANCELED, otherwise it returns TaskNotCancelable. Both are allowed by §3.1.5.

## Open questions

- **Core or transport fixes the server depends on** (route to the core/transport owners; see `koog-core-status.md` and `koog-transport-client-status.md`):
  - `TaskPushNotificationConfig.taskId` optional;
  - the `-32008` code and exception;
  - default `ErrorInfo` details;
  - `TASK_STATE_UNSPECIFIED`, which is relevant to the ListTasks `status` filter decode;
  - `securityRequirements` shape, which is relevant to the SUT card;
  - pre-stream JSON errors;
  - where `A2A-Version` validation lives.
- **Design decisions needing user approval** (AGENTS.md):
  - the task-lifetime event stream for SubscribeToTask (session management);
  - the `TaskStorage` list API and the `PushNotificationConfigStorage` flattening (storage interfaces);
  - the push sender contract (per-event vs. per-turn);
  - whether the server persists incoming user messages into history (Python does);
  - whether the server synthesizes a leading `Task` for follow-up streams.
- Whether the Python test server should move from `1.1.0` to `1.2.1`. Not required.
- Whether the TCK SUT should declare `pushNotifications` and `extendedAgentCard` (each choice selects a different TCK test set).
