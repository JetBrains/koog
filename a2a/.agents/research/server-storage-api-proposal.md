# How should the `a2a-server` storage interfaces take and return v1 DTOs, and where is that not applicable?

**Sources checked:**
- A2A spec repo `a2a-protocol` at tag `v1.0.1` (baseline). `main` @ `7c861a5` (v1.0.1-82) has no semantic change to the operations used here; it only renames `PushNotificationConfig` → `TaskPushNotificationConfig` in the spec text.
- `a2a-python` `main` @ `a4c4fc9` (v1.2.2-13). The pinned `a2a-sdk==1.1.0` (`test-python-a2a-server/pyproject.toml:7`) has the same `TaskStore`. Its push store differs only in that `set_info` returns `None` there.
- `a2a-tck` @ `263b9cf`.
- Koog working tree on branch `eugenethedev/a2a-1.0`, including the 3a edits that are still uncommitted. `A2AServer.kt` line numbers may shift.

**Confidence:** high for the spec, proto and Python facts. Medium for the recommendations, which are design judgement where several shapes are valid.

## Answer

- **Use DTOs where a storage method answers a protocol query one-to-one:**
  - `TaskStorage.list(ListTasksRequest): ListTasksResponse`.
  - The four push-config CRUD methods: `TaskPushNotificationConfig` in and out, plus `GetTaskPushNotificationConfigRequest`, `ListTaskPushNotificationConfigsRequest → ListTaskPushNotificationConfigsResponse` and `DeleteTaskPushNotificationConfigRequest`.
- **Do not use DTOs in these cases:**
  - Internal workhorse methods that many non-RPC callers use: `TaskStorage.get`, `update(TaskEvent)`, `delete*`, `getAll(taskIds)`, `getByContext`. `GetTaskRequest` has no `includeArtifacts`, and cancel, session, push and agent code would have to fabricate requests.
  - The whole of `MessageStorage`. No v1 RPC touches it.
  - The push dispatch path (`getAll(taskId)`), which runs without a caller request.
- **`A2AServer` keeps these jobs:**
  - capability gating;
  - parameter validation (`pageSize` 1..100, `historyLength ≥ 0`);
  - task-existence checks → `TaskNotFoundError`;
  - assigning push-config ids;
  - mapping `null` results to protocol errors.
- **Storage keeps these jobs:** filtering, ordering, pagination tokens, `totalSize`, and (recommended) the `historyLength`/`includeArtifacts` projection.
- **Python differs on one point.** Its `TaskStore.list` takes the DTO, but the request handler does the projection afterwards.

## Requirements

| # | Requirement | Tier | Bindings | Citation |
|---|---|---|---|---|
| S1 | ListTasks filters: `contextId`, `status`, `statusTimestampAfter` (status timestamp ≥ value) | MUST (it is the schema) | all; Koog: JSON-RPC | proto `ListTasksRequest` L675-700; spec §3.1.4 L232-240 |
| S2 | Sort by status timestamp, descending | MUST | all | spec §3.1.4 L254, L262 |
| S3 | Cursor pagination with `pageToken`/`nextPageToken` | MUST | all | spec §3.1.4 L254, L258 |
| S4 | `pageSize` unset → at most 50; valid range 1..100 | MUST (proto comment) | all | proto L684-688 |
| S5 | `nextPageToken` always present, `""` on the last page. `pageSize` and `totalSize` (count before pagination) are REQUIRED | MUST | all | spec §3.1.4 L246; proto `ListTasksResponse` L703-712 |
| S6 | `includeArtifacts` false/unset → `artifacts` omitted entirely (not `[]`, not `null`) | MUST | all | spec §3.1.4 L240 |
| S7 | `historyLength`: unset = server default; 0 = no history, field SHOULD be omitted; N = at most N most recent messages; MUST NOT exceed N | MUST / SHOULD | all | spec §3.2.4 L465-471; proto `GetTaskRequest` L661-672 |
| S8 | GetTask returns the current state **and artifacts**; unknown → TaskNotFound | MUST | all | spec §3.1.3 L214-230 |
| S9 | ListTasks and Get/Cancel/Subscribe/push ops are scoped to the caller's authorization | MUST | all | spec §13.1 L3073-3105; §3.1.4 L254 |
| S10 | Create push config returns the config "with assigned ID"; unknown task → TaskNotFound; it persists until task completion or explicit deletion | MUST; capability:pushNotifications | all | spec §3.1.7 L313-335; proto `TaskPushNotificationConfig` L469-484, rpc L90 |
| S11 | Get push config: missing config → TaskNotFound | MUST; capability:pushNotifications | all | spec §3.1.8 L354, L360 |
| S12 | List push configs returns all active configs; pagination is MAY | MUST / MAY; capability:pushNotifications | all | spec §3.1.9 L381; proto L757-768, L806-811 |
| S13 | Delete push config is idempotent; unknown task → TaskNotFound; response is `Empty` | MUST; capability:pushNotifications | all | spec §3.1.10 L398, L402; proto rpc L131 |
| S14 | Webhook payload is `StreamResponse`. Send `Authorization: <scheme> <credentials>`. At least one attempt per configured webhook. Timeout of 10-30 s | MUST / SHOULD; capability:pushNotifications | webhook (plain HTTP) | spec §4.3.3 L842-887 |
| S15 | `tenant` on every request: an opaque routing id. The server-side rules are not normative | MAY | all | proto (every `*Request.tenant`, e.g. L677) |

All of these apply to Koog's JSON-RPC binding. None of them are binding-specific.

## Details

### 1. Current signatures (Koog)

**`tasks/TaskStorage.kt`** (no 3a diff yet):

- `get(taskId, historyLength: Int? = 0, includeArtifacts = false): Task?` (`:23-27`)
- `getAll(taskIds, historyLength = 0, includeArtifacts = false): List<Task>` (`:38-42`)
- `getByContext(contextId, historyLength = 0, includeArtifacts = false)` (`:53-57`)
- `update(event: TaskEvent)` (`:70`)
- `delete(taskId)` (`:78`)
- `deleteAll(taskIds)` (`:86`)
- `ContextTaskStorage` mirrors these (`:96-161`), and agent code reaches it through `RequestContext.taskStorage` (`session/RequestContext.kt:28`).
- `InMemoryTaskStorage.get` projects with `history.takeLast(historyLength)` (`:38-49`). That gives `[]` for 0, and kotlinx serializes it as `"history":[]`, which breaks the S7 SHOULD.
- There is no list API, and `A2AServer.onListTasks` throws UnsupportedOperation (`A2AServer.kt:513-518`).
- `A2AServer.onGetTask` passes `includeArtifacts = false` (`A2AServer.kt:500-506`). That violates S8.

**`messages/MessageStorage.kt`:** `save(message)` (`:19`), `getByContext(contextId)` (`:26`), `deleteByContext` (`:34`), `replaceByContext` (`:42`), and the `ContextMessageStorage` wrapper (`:52-101`). It is used by agent code and the `agents-features-a2a-server` nodes (`A2AAgentServerNodes.kt:29,68,87,104`).

**`notifications/PushNotificationConfigStorage.kt`** (after 3a):

- `save(taskId, TaskPushNotificationConfig)` (`:19`)
- `get(taskId, configId: String?): TaskPushNotificationConfig?` (`:26`)
- `getAll(taskId)` (`:33`)
- `delete(taskId, configId: String? = null)` (`:42`)
- In-memory: `MutableMap<String, MutableMap<String?, TaskPushNotificationConfig>>` (`InMemoryPushNotificationConfigStorage.kt:15`). The key is a nullable id, and `save` copies `taskId` in.

The `A2AServer` push handlers (`:599-636`) have these gaps:

- create returns the request unchanged, with no id assignment and no task-existence check;
- get throws `NoSuchElementException` instead of `A2ATaskNotFoundException`;
- list and delete do not check that the task exists.

**`PushNotificationSender.send(config: TaskPushNotificationConfig, task: Task)`** (`PushNotificationSender.kt:18`). `SessionManager` calls it once, after the session finishes, with a task trimmed to `historyLength=0, includeArtifacts=false` (`session/SessionManager.kt:96-109`). `SimplePushNotificationSender` posts a bare `Task` (`:53`), not a `StreamResponse`.

### 2. v1 DTOs in `a2a-core` (`a2a-core/src/commonMain/kotlin/ai/koog/a2a/model/`)

| DTO | Fields (Kotlin) | File:line |
|---|---|---|
| `GetTaskRequest` | `id: String`, `historyLength: Int?`, `tenant: String?` | `Requests.kt:57-62` |
| `ListTasksRequest` | `contextId: String?`, `status: TaskState?`, `pageSize: Int?`, `pageToken: String?`, `historyLength: Int?`, `statusTimestampAfter: Instant?`, `includeArtifacts: Boolean?`, `tenant: String?` | `Requests.kt:77-87` |
| `ListTasksResponse` | `tasks: List<Task>`, `nextPageToken: String`, `pageSize: Int`, `totalSize: Int` (no defaults, so kotlinx always encodes them; `""` is emitted) | `Responses.kt:18-24` |
| `TaskPushNotificationConfig` (create request **and** stored entity; it implements `Request`) | `url`, `taskId: String?`, `id: String?`, `token: String?`, `authentication: AuthenticationInfo?`, `tenant: String?`. KDoc `:13` says the id is "set by the client" | `TaskPushNotificationConfig.kt:19-27` |
| `GetTaskPushNotificationConfigRequest` / `DeleteTaskPushNotificationConfigRequest` | `taskId: String`, `id: String`, `tenant: String?` | `Requests.kt:108-113`, `:121-126` |
| `ListTaskPushNotificationConfigsRequest` | `taskId: String`, `pageSize: Int?`, `pageToken: String?`, `tenant: String?` | `Requests.kt:146-152` |
| `ListTaskPushNotificationConfigsResponse` | `configs = emptyList()`, `nextPageToken = ""`. Defaults are omitted on the wire, which is fine because neither field is REQUIRED (proto L806-811) | `Responses.kt:32-36` |
| `Event` (the Kotlin form of `StreamResponse`; `EventSerializer` wraps it as `{"task"/"message"/"statusUpdate"/"artifactUpdate": …}`) | — | `Events.kt:11-21` |

`RequestHandler` already speaks these DTOs (`a2a-core/.../transport/ServerTransport.kt:81-154`).

Spec semantics the storage must implement for `list`:

- The filters are AND-ed.
- `status == null` or `TASK_STATE_UNSPECIFIED` means no filter. This is the proto3 enum default; Python checks `if params.status:` (`inmemory_task_store.py:117`).
- `statusTimestampAfter` keeps tasks with `status.timestamp >= value`. Tasks without a timestamp are excluded (`inmemory_task_store.py:121-134`).
- Sort by `status.timestamp` descending. Python breaks ties by id, descending, and puts tasks without a timestamp last (`inmemory_task_store.py:25-34, 137`). Because `TaskStatus.timestamp` is now nullable (`Task.kt:93`), the server should stamp every status (3a item) or ordering degrades.
- The page token is opaque and its format is defined by the storage. Python encodes a `(timestamp, id)` cursor, so the token survives inserts.
- An invalid token gives InvalidParams (`inmemory_task_store.py:174-181`).
- `pageSize` in the response is the effective size, e.g. 50 when unset (`:158, :171`).
- `totalSize` is the filtered count before pagination (`:142`).

### 3. Proposed Kotlin signatures

**TaskStorage**

| Method | DTO? | Why / contract |
|---|---|---|
| `list(request: ListTasksRequest, ctx: ServerCallContext): ListTasksResponse` (**new**) | **Yes** | The request is a pure query and the response is exactly what storage computes, including `nextPageToken` and `totalSize`, which only the storage can produce efficiently. See the field split below. |
| `get(taskId: String, historyLength: Int? = null, includeArtifacts: Boolean = true): Task?` | **No** | Called from 5+ internal paths (cancel, session, push, subscribe) and from agent code (`ContextTaskStorage`, `A2AAgentServerNodes.kt:129-140`, `examples/.../JokeWriterAgentExecutor.kt:129`). `GetTaskRequest` lacks `includeArtifacts`, so it cannot express the internal needs. **Change the defaults** to "full task" (`null`/`true`). The current `0`/`false` defaults are the cause of the S8 bug. Optional sugar: an extension `TaskStorage.get(request: GetTaskRequest) = get(request.id, request.historyLength, includeArtifacts = true)`. |
| `getAll(taskIds, …)`, `getByContext(contextId, …)` | No | There is no protocol counterpart. `getByContext` is unpaginated and agent-facing; keep it (see D4). |
| `update(event: TaskEvent)` | No | This is an event-sourcing write, and there is no request DTO for it. History persistence (3.14) changes its *semantics*, not its shape. |
| `delete(taskId)`, `deleteAll(taskIds)` | No | No protocol counterpart. |
| `ContextTaskStorage.list(request)` (optional) | Yes | It forces or validates `contextId == this.contextId`. |

`list`: which `ListTasksRequest` fields each layer handles.

| Field | `A2AServer` (before the call) | Storage |
|---|---|---|
| `pageSize` | Validate 1..100 when it is present (0 counts as present, and invalid) → `A2AInvalidParamsException` | Apply the default of 50 when `null`, and echo the effective value |
| `historyLength` | Validate `>= 0` → InvalidParams | Project: `null` → server default (all); `0` → `history = null` (**not** `[]`); `N` → `takeLast(N)` |
| `includeArtifacts` | — | `false`/`null` → `artifacts = null` (omitted because `explicitNulls=false`) |
| `contextId`, `status`, `statusTimestampAfter` | — (an invalid enum value already fails decoding) | Filter |
| `pageToken` | — | Decode the opaque cursor. Throw `A2AInvalidParamsException` when it is invalid |
| `tenant` | Not validated by the server in v1 | Passed through. The in-memory storage ignores it; a multi-tenant storage may partition on it |
| (`ctx`) | Passes the call context | Caller scoping under §13.1. The in-memory storage ignores it; custom storages resolve the owner from `ctx.state` |

`GetTask` in `A2AServer`:

1. Validate `historyLength >= 0`.
2. Call `taskStorage.get(id, historyLength, includeArtifacts = true)`.
3. Map `null` → `A2ATaskNotFoundException`.

The trimming stays in storage. That is the current Koog contract, and it lets DB-backed storages avoid loading history; Python does it in the handler instead (§4). The in-memory projection must return `history = null` for 0.

**MessageStorage: not applicable.** No v1 RPC reads or writes it. It is an agent-side conversation memory keyed by `contextId`, and the only v1 impact is `Message.contextId` being nullable (already handled at `InMemoryMessageStorage.kt:20-21`). Keep it unchanged. Persisting user follow-ups into task history (3.14) belongs to `TaskStorage.update`/`A2AServer`, not here.

**PushNotificationConfigStorage**

| Method | DTO? | Contract |
|---|---|---|
| `save(config: TaskPushNotificationConfig): TaskPushNotificationConfig` | **Yes** (it is both the RPC request and the entity) | Precondition, enforced by `A2AServer`: `taskId` and `id` are non-null and non-blank. Upsert by `(taskId, id)` and return what was stored. The storage persists the DTO as given, `tenant` included. Python does the same, so Get echoes it. |
| `get(request: GetTaskPushNotificationConfigRequest): TaskPushNotificationConfig?` | **Yes** | Returns `null` when missing. `A2AServer` maps `null` → `A2ATaskNotFoundException` (S11). |
| `list(request: ListTaskPushNotificationConfigsRequest): ListTaskPushNotificationConfigsResponse` | **Yes** | Pagination is MAY. The in-memory storage may ignore `pageSize`/`pageToken` and return everything with `nextPageToken = ""`, as Python does (`default_request_handler_v2.py:643-649`). |
| `delete(request: DeleteTaskPushNotificationConfigRequest)` | **Yes** | Idempotent: a missing config is a no-op, never an error (S13). |
| `getAll(taskId: String): List<TaskPushNotificationConfig>` | **No** | The internal dispatch path has no caller request. This is the analogue of Python `get_info_for_dispatch`. |
| `deleteAll(taskId: String)` | **No** | Internal cleanup, for example when a task is deleted. Replaces today's `delete(taskId, null)` overload. |

`A2AServer` handles these for every push op:

1. Capability check (exists, `storageIfPushNotificationSupported`).
2. `taskStorage.get(taskId) ?: throw A2ATaskNotFoundException` (S10, S12, S13; Python does the same, `default_request_handler_v2.py:506-508, 537-539, 639-641, 666-668`).
3. On create:
   - require `taskId` (exists, `:604-605`);
   - **assign `id`** when it is null or blank;
   - for the inline `SendMessageConfiguration.taskPushNotificationConfig` (3.12), fill in `taskId` from the created task (proto L147-149 says it is empty there).
4. Do **not** reject configs for terminal tasks. The TCK creates configs on completed tasks and then reads them back (`tests/compatibility/core_operations/test_push_notifications.py:110-173`).

**PushNotificationSender** (brief): `send(config: TaskPushNotificationConfig, event: Event)`.

- Not a DTO question. The payload is `StreamResponse`, which is `Event` in Koog, and it must be serialized through `EventSerializer` (S14).
- Keep the Koog split: the server or `SessionManager` loads `pushConfigStorage.getAll(taskId)` and calls `send` once per config. The sender only handles HTTP: auth header, a 10-30 s timeout, and errors that are logged and swallowed.
- Python fans out on every task event, not once at the end (`agent_execution/active_task.py:351-364`). Koog currently sends once, after the session, with a trimmed task (`SessionManager.kt:96-109`). The *when* is 3.13's decision; this signature supports both.

### 4. Python reference shapes (main @ a4c4fc9)

**`TaskStore`** (`src/a2a/server/tasks/task_store.py:7-33`):

- `save(task, context)`, `get(task_id, context) -> Task | None`, `list(params: ListTasksRequest, context) -> ListTasksResponse`, `delete(task_id, context)`.
- So **the DTO is used only for `list`**. `get` takes primitives and returns the **full** task.
- Every method takes `ServerCallContext`, and the store partitions by `OwnerResolver(context)` (default `context.user.user_name`, `server/owner_resolver.py:7-13`; `inmemory_task_store.py:51-69`).

**Where `ListTasksRequest` fields are honoured:**

- *Store:* `context_id`, `status`, `status_timestamp_after`, ordering, `page_token`, `page_size` (default `DEFAULT_LIST_TASKS_PAGE_SIZE = 50`, `utils/constants.py:8`), `total_size`, `next_page_token` (`inmemory_task_store.py:99-172`).
  - The store returns `next_page_token=None` on the last page; protobuf turns that into `""`.
- *Handler:* `validate_history_length` and `validate_page_size` (1..100, `MAX_LIST_TASKS_PAGE_SIZE = 100`) **before** the call. **After** the call it clears `artifacts` unless `include_artifacts` and applies `apply_history_length` (`request_handlers/default_request_handler_v2.py:209-228`; `utils/task.py:29-33, 35-72, 75-86`). `tenant` is never read by the store.
- `on_get_task`: `store.get` → `TaskNotFoundError` if `None` → `apply_history_length` (`default_request_handler_v2.py:194-207`).
  - For 0, Python *clears* `history`, so it is omitted (`utils/task.py:57-63`).
  - Artifacts are always returned.

**`PushNotificationConfigStore`** (`tasks/push_notification_config_store.py:24-96`). It does **not** use the request DTOs:

- `set_info(task_id, config, context) -> TaskPushNotificationConfig | None`
- `get_info(task_id, context) -> list` (owner-scoped)
- `get_info_for_dispatch(task_id) -> list` (all owners, used by the sender)
- `delete_info(task_id, context, config_id=None)` (`None` means all)

How it handles the operations:

- **Id assignment:** `normalize_push_notification_config` sets `task_id` and defaults an empty `id` **to the task id**, not a fresh UUID (`:12-21`). A second id-less config therefore overwrites the first. The handler applies this as a fallback too (`default_request_handler_v2.py:512-520`).
- **Get:** the handler loads the list and searches by id, raising `TaskNotFoundError` when it is not found (`:541-549`).
- **List:** ignores pagination (`:643-649`).
- **Delete:** the in-memory store is a no-op when the config is missing (`inmemory_push_notification_config_store.py:102-140`).

**`PushNotificationSender.send_notification(task_id, event)`** (`tasks/push_notification_sender.py:13-25`). The event is `Task | TaskStatusUpdateEvent | TaskArtifactUpdateEvent`. `BasePushNotificationSender` owns the config lookup through `get_info_for_dispatch` and fans out (`base_push_notification_sender.py:68-102`).

### 5. Decisions for the user

**D1. ListTasks storage shape**
- (a) `list(ListTasksRequest, …): ListTasksResponse`.
- (b) Koog-specific `TaskQuery`/`Page<Task>` types, with the server mapping them to the DTOs.
- (c) Individual parameters.
- **Recommend (a).** It is the user's stated goal and the Python shape, and the DTO carries exactly the query plus `tenant`.

**D2. Who applies `historyLength`/`includeArtifacts` (get and list)**
- (a) Storage (the current Koog `get` contract; efficient for DBs).
- (b) `A2AServer` after the storage call (Python; spec compliance is guaranteed in one place even with custom storages).
- (c) Both: storage may pre-trim, and the server always re-applies.
- **Recommend (a),** documented precisely: `0` → `null`, not `[]`. Also expose the projection as a reusable public helper so custom storages get it right. Choose (b) if you would rather not trust third-party storages with a MUST.

**D3. Caller scoping (§13.1 MUST) in the storage API**
- (a) Add `ctx: ServerCallContext` to `list` only. Scoping must happen inside the query, or pagination breaks. `get`-based checks can be done in an `A2AServer` override.
- (b) Add `ctx` to every RPC-facing storage method, the Python model with an owner resolver.
- (c) No scoping parameter; leave it entirely to users.
- **Recommend (a).** It is minimal, and (b) can come later without breaking (a).

**D4. Non-DTO TaskStorage methods**
- (a) Keep `get`/`getAll`/`getByContext`/`update`/`delete*`, switch the `get` defaults to the full task (`historyLength=null, includeArtifacts=true`), and add a `GetTaskRequest` extension overload.
- (b) Keep the current defaults (`0`/`false`).
- (c) Replace `getByContext`/`getAll` with `list`.
- **Recommend (a).** For (c): `list` is capped at 100 per page, and agent code (`ContextTaskStorage`, Koog nodes) wants an unpaginated read.

**D5. Push config id when the client omits it**
- (a) `A2AServer` assigns a UUID; the storage requires a non-null id.
- (b) The storage assigns the id and returns it (Python, with id = taskId).
- (c) Reject with InvalidParams.
- **Recommend (a).** The spec says "assigned ID" (§3.1.7 L326), and a per-config UUID avoids Python's overwrite-on-second-create behavior. `save` still returns the stored config, so a DB storage could normalize.

**D6. Push storage method shapes**
- (a) DTOs for all four RPC-mirroring methods, plus non-DTO `getAll(taskId)`/`deleteAll(taskId)` for internal use.
- (b) DTOs only for create and list (the only ones with more than `(taskId, id)`); primitives for get and delete.
- (c) All primitives (the Python shape).
- **Recommend (a)** for symmetry, and so `tenant` reaches multi-tenant storages. (b) is acceptable if you see DTO wrapping of two strings as noise.

**D7. Sender contract (overlaps 3.13)**
- (a) `send(config, event: Event)`; the server fans out per config.
- (b) The Python shape `send(taskId, event)`, where the sender owns the config lookup.
- **Recommend (a).** Separately, decide in 3.13 whether to send on every task event (Python) or only at the end of the session (current Koog).

## Koog status

Covered in Details §1. The concrete gaps that the reshaping must close:

- **TaskStorage:**
  - there is no list API, and `onListTasks` throws (`A2AServer.kt:513-518`);
  - `onGetTask` omits artifacts (`:500-506`);
  - `get` defaults `0`/`false` (`TaskStorage.kt:23-27`);
  - in-memory `historyLength=0` gives `[]`, not `null` (`InMemoryTaskStorage.kt:38-40`);
  - a negative `historyLength` throws `IllegalArgumentException` from `require` inside the storage (`:29-31`) instead of `A2AInvalidParamsException` from the server.
- **Push:**
  - nullable map key (`InMemoryPushNotificationConfigStorage.kt:15`);
  - no id assignment and no task-existence checks (`A2AServer.kt:599-636`);
  - missing config gives `NoSuchElementException` (`:618-619`), not TaskNotFound;
  - the payload is a bare `Task` (`SimplePushNotificationSender.kt:53`).
- **MessageStorage:** no gaps.

## Testability notes

- **Unit tests for `InMemoryTaskStorage.list`:**
  - each filter, alone and combined;
  - `UNSPECIFIED` status means no filter;
  - descending order with ties and missing timestamps;
  - default page size 50; pages joined by tokens cover everything with no duplicates;
  - the last page has `nextPageToken == ""`;
  - `totalSize` is the filtered count;
  - an invalid token gives `A2AInvalidParamsException`;
  - `includeArtifacts` false gives `artifacts == null`;
  - `historyLength` 0 gives `history == null`.
  - Assert on whole `ListTasksResponse` objects.
- **`A2AServer` tests:** `pageSize` 0 or 101 → InvalidParams; `historyLength` -1 → InvalidParams; GetTask includes artifacts; push get/list/delete on an unknown task → TaskNotFound; a missing config → TaskNotFound; delete twice succeeds; create without an id returns a non-blank id.
- **Wire test:** encode `ListTasksResponse(tasks = emptyList(), nextPageToken = "", …)` with `JSONRPCJson` and check that `"nextPageToken":""` is present.
- **TCK @263b9cf:**
  - CORE-LIST-001..005 (`tck/requirements/core_operations.py:241-330`) only send a sample request and validate the response schema (`tests/compatibility/core_operations/test_requirements.py:92-130`). They do **not** assert ordering, pagination or artifact omission, so Koog unit tests must cover those.
  - PUSH-CREATE-001/002, GET-002, DEL-002 (`test_push_notifications.py:110-173, 210-238, 319-358`) always send a client-chosen `id` (`:69-86`), so server-side id assignment is untested by the TCK.
  - GET-002 accepts any error, not specifically TaskNotFound.
  - The push tests only run if the SUT declares `pushNotifications` (plan 4.2).

## Discrepancies

- **Spec vs. spec:** §3.1.4 says "sorted by last update time" (L254) and also "sorted by their status timestamp" (L262). Python and the TCK use the status timestamp.
- **Python vs. spec:**
  - Python assigns id = taskId by default, not a unique id. A second create without an id replaces the first. The spec only says "assigned ID".
  - Python ignores push-list pagination, which the spec allows (MAY).
- **Koog KDoc vs. spec:** `TaskPushNotificationConfig.id` KDoc says "set by the client" (`TaskPushNotificationConfig.kt:13`), while the spec says the server assigns it. The KDoc should say "may be set by the client; assigned by the server if absent".
- **Spec v1.0.1 text:** §3.1.7/§3.1.8 outputs still name `PushNotificationConfig`. This is fixed on `main`; the proto (`TaskPushNotificationConfig`) is authoritative.

## Open questions

- History persistence of user follow-up messages (3.14) changes the semantics of `TaskStorage.update`, not this API shape.
- Whether configs should be auto-removed after a terminal state. Spec: "persist until task completion or explicit deletion". The TCK needs them to stay readable after completion.
- How a caller identity gets into `ServerCallContext.state`. This depends on D3, and there is no Koog convention for it yet.
- Push URL safety validation (Python `_reject_unsafe_push_url`, `default_request_handler_v2.py:510`). It is an SSRF SHOULD in §13.2 and has not been examined here.
