# Storage API shape (open decision 12, plan.md §4)

**Sources checked:**
- A2A spec `a2aproject/A2A` @ tag `v1.0.1` (baseline; `main` is `9fff3e0`, v1.0.1-93; the §3.4 and §13.1 text is unchanged on `main`)
- a2a-python `main` @ `05aaff6` (v1.3.0-15), plus the pinned `a2a-sdk==1.1.0` (`test-python-a2a-server/pyproject.toml:7`) @ tag `v1.1.0` (`96c14b7`)
- a2a-tck @ `d4d6fae` (same as the pin in `test-tck/setup_tck.sh:9`)
- Koog @ `808e95f7a` (branch `eugenethedev/a2a-1.0`)

**Confidence:** high for the facts (spec, Python, TCK, Koog call sites). Medium for the recommendations: they are design choices, and the spec leaves the authorization model and the handling of referenced tasks to the agent.

## Answer

- **12a.** Keep two tiers, but make each one exact (option A′, a stricter A). The rule:
  - Any read whose result reaches a caller, or that is made on a caller's behalf (agents included), goes through the caller-scoped methods `get(GetTaskRequest, ctx)` and `list(ListTasksRequest, ctx)`.
  - Unscoped methods are only for the server's own bookkeeping, on a task it has already authorized or is creating. Their results never reach a caller.
  - `update` and `appendHistory` gain the `ServerCallContext` the write is made for. Today a scoping storage has no way to learn who created a task, short of agent-written metadata. Python passes the context to `TaskStore.save`.
  - Remove `getAll`, `getByContext`, `delete` and `deleteAll` from `TaskStorage`. Only `ContextTaskStorage` calls them.

  B (context on every method, as in Python) needs a "system" context for dispatch. Python shipped a bug from exactly that (#1016). C (plain methods plus server-side checks) still needs the context in `list`, and puts the authorization model in two places.
- **12b, referenced tasks.** The spec requires scoping for every operation that accesses tasks (§13.1). Neither the spec nor the TCK tests this for `referenceTaskIds`. Python reads related tasks with the caller's context and silently drops the ones it can't see. Recommendation: `ContextTaskStorage` reads become owner-scoped through the turn's `callContext` and tenant, routed through the scoped `get`/`list`.
- **12c, update contract.** A storage may trim or redact what it keeps. It must keep the id, `contextId` and status state as last written, keep `messageId` on every message it retains, and keep a task visible to the context that created it. Proposed wording is below.
- **Q6, do we need the wrappers.** Keep one small agent-facing type per storage, bound to the request (option c, implemented as a fixed (a)). Make `ContextTaskStorage` read-only and caller-scoped, and pass the context through `ContextMessageStorage`. Exposing `TaskStorage` itself (b) would let agents write around the turn's event stream and read unscoped.

## Requirements

| # | Requirement | Tier | Bindings | Citation |
|---|---|---|---|---|
| 1 | Scope results to the authenticated caller's boundaries for all operations that access or list tasks and other resources | MUST | all (Koog: JSON-RPC) | spec v1.0.1 §13.1, `docs/specification.md:3075-3081` |
| 2 | GetTask verifies access; Cancel, Subscribe and push config operations verify access rights | MUST | all | §13.1, `:3091-3093` |
| 3 | Authorization checks happen before queries or operations that could leak the existence of out-of-scope resources | MUST | all | §13.1, `:3098` |
| 4 | Don't reveal the existence of unauthorized resources; return not found for inaccessible ones, not distinguished from nonexistent | MUST / SHOULD NOT distinguish | all | §3.3.2, `:514`, `:526-527` |
| 5 | Clients SHOULD use `referenceTaskIds`; agents SHOULD use referenced tasks. Nothing is said about inaccessible references | SHOULD (silent on access) | all | §3.4.3, `:638-639`; proto `a2a.proto:275-276` |
| 6 | An agent that can't accept a client-provided `contextId` MUST reject the request | MUST (conditional) | all | §3.4.1, `:593` |
| 7 | TCK: AUTH-SCOPE-001/002/003 are defined but no test references them | not tested | n/a | `tck/requirements/auth.py:168,181,194`; no hit in `tests/` |

## Details

### Koog storage call sites (HEAD 808e95f7a)

| Site | Method | Scoped? | Caller context available? |
|---|---|---|---|
| `A2AServer.kt:601` `newRequestContext` | builds `ContextTaskStorage(contextId, taskStorage)` | n/a | yes, `ctx` param |
| `A2AServer.kt:660` blocking SendMessage result | `get(GetTaskRequest, ctx)` | yes | yes |
| `A2AServer.kt:702` GetTask, `:721` ListTasks | get/list with ctx | yes | yes |
| `A2AServer.kt:765-767`, then `TurnManager.kt:725` CancelTask lookup | `get(lookup, ctx)` | yes | yes |
| `A2AServer.kt:783,795,813` cancel re-reads (`:813`'s task is **returned to the caller**) | `get(taskId, 0, true)` | **no** | yes (cancel caller's `ctx`) |
| `A2AServer.kt:880,892,907,919` push config RPCs; `:979` `checkTaskExists` | scoped | yes | yes |
| `TurnManager.kt:413` inline push config save; `:496` follow-up read; `:653` Subscribe snapshot | scoped | yes | yes |
| `TurnManager.kt:622-623` `deletePushConfigsIfTaskAbsent` (in `monitorTurn`) | `get(taskId)`, push `deleteAll` | no | not passed; `monitorTurn` is launched inside `startTurn` (`:440-445`), so it can be threaded |
| `TaskEventStream.kt:253` `publish(Task)` existence check; `:272` `appendHistory`; `:303` `update` | unscoped read and writes | no | no: the stream is per task (`TurnManager.kt:861`). It can be threaded through `TurnEventProcessor` (built with a context at `TurnManager.kt:416` and `:728`, both internal) and the `publish` lambda (`:740`) |
| `TaskEventStream.kt:336` `markFailed` | `get(taskId)` | no | through `endTurn` (`TurnManager.kt:579`, inside `monitorTurn`) |
| `PushNotificationDispatcher.kt:124` | push `getAll(taskId)` | no | **no**: one queue per task carries events from every turn and caller |
| `TaskStorage.kt:194-249` `ContextTaskStorage` | `get`, `getAll`, `getByContext`, `delete*` | **no** | `RequestContext.callContext` exists (`RequestContext.kt:27`) but isn't passed |

The server never calls `getAll`, `getByContext`, `delete` or `deleteAll` itself; only `ContextTaskStorage` does (grep `taskStorage\.` in `a2a-server/src/commonMain`). Server tests mention them only in pass-through recorders such as `RequesterReadRecorder` (`A2AServerSendMessageTest.kt:113-131`).

**The write gap:** `update(event)` (`TaskStorage.kt:138`) gets no context. So a storage can't record an owner at creation, and the only recipe in Koog is agent-written `metadata["owner_id"]` (`A2AServer.kt:157-161`). That leaks to clients, since GetTask returns metadata, and that KDoc example also answers access denial with `UnsupportedOperation`, which is against requirement 4.

### 12a options

**(A) Keep both styles, fix the docs.** No signature change. Doesn't close the write gap or the agent leak. Implementers keep 9 methods. Phase 5 has no change. Rejected alone.

**(A′, recommended) Two exact tiers.**
```kotlin
public interface TaskStorage {
    // Caller-scoped: RPCs, and every read made for a caller (results may reach the caller).
    public suspend fun get(request: GetTaskRequest, context: ServerCallContext): Task?
    public suspend fun list(request: ListTasksRequest, context: ServerCallContext): ListTasksResponse

    // Server-internal: tasks the server has already authorized or is creating; results never reach a caller.
    public suspend fun get(taskId: String, historyLength: Int? = null, includeArtifacts: Boolean = true): Task?
    public suspend fun update(event: TaskEvent, context: ServerCallContext)
    public suspend fun appendHistory(taskId: String, message: Message, context: ServerCallContext) {
        val task = get(taskId, historyLength = null, includeArtifacts = true) ?: return
        task.withAppendedHistory(message)?.let { update(it, context) }
    }
}
```

The context `update` and `appendHistory` receive:
- the turn's requester for agent events, the lazy user message and the FAILED update;
- the CancelTask caller for the forced CANCELED;
- the running turn's context for events a cancel sends through that turn's processor. Same owner after authorization; Python has the same overlap and marks it TODO at `active_task.py:901`.

Cost per site:
- `A2AServer.kt:783,795,813` switch to `get(GetTaskRequest(taskId, historyLength = 0, tenant), ctx)`. The context is available, and GetTask always includes artifacts, so the shape is the same.
- Thread the context through internal `TurnEventProcessor`/`TaskEventStream.publish`/`endTurn`, and pass it to `monitorTurn` for `:622`. No public API break.
- The dispatcher and the existence checks stay unscoped.

Implementers get 5 methods instead of 9. `PushNotificationConfigStorage` is already in this shape: only the docs change, saying `getAll` returns every caller's configs, like Python's `get_info_for_dispatch`.

**(B) Python-style: context on every method, plus explicit unscoped dispatch reads.** Signatures: `get(taskId, ctx, …)`, `update(event, ctx)`, `appendHistory(…, ctx)`, push `getAllForDispatch(taskId)`, plus a system context for reads that have no caller.

Cost per site:
- All the threading A′ needs.
- `ServerCallContext` has no principal (`ServerTransport.kt:208-212`, headers and state only), so a "system" context is a marker convention that every storage must recognize.
- The dispatcher can't have a caller, so it needs an unscoped method anyway. Python's default `get_info_for_dispatch` falls back to `get_info(ServerCallContext())`. That returns nothing for multi-owner stores, which silently dropped notifications: issue #1016, fixed by `c24ae05`, documented at `push_notification_config_store.py:55-87`.

Implementers must scope every method and reproduce scoping on internal re-reads. Phase 5 has to pass the context everywhere.

**(C) Plain methods plus server visibility checks.** `get(taskId)` is plain, and `A2AServer` gains `protected open suspend fun isVisible(task: Task, ctx): Boolean`. `list` can't be post-filtered without breaking `pageSize`, `nextPageToken` and `totalSize` (`TaskStorage.kt:58-63`), so it keeps `ctx`.

Ownership then has to live on the `Task` (metadata, which leaks unless stripped), or the storage still needs the context at creation. The authorization model sits in two places, the hook and `list`, so GetTask and ListTasks can disagree. The post-read check is internal and returns the same not-found, so it doesn't break requirement 3 as written (interpretation). It is still the weaker match for "before any database queries".

**Recommendation: A′.**

### Q2. How Python does it

The interfaces are the same in `v1.1.0` and `main`, apart from the `set_info` return type and cluster mode on `main`.
- **Stores:** `TaskStore.save/get/list/delete` all take `context` (`task_store.py:14-33`). In-memory and DB stores partition by `owner_resolver(context)`, which defaults to `context.user.user_name` (`owner_resolver.py:7-13`, `inmemory_task_store.py:64-110`, `database_task_store.py:199-245`). Push `set_info/get_info/delete_info` take `context`, and `get_info_for_dispatch(task_id)` returns every owner's configs (`push_notification_config_store.py:27-96`).
- **Active task / consumer:**
  - `ActiveTaskRegistry.get_or_create` builds the `TaskManager` with the request's `call_context` (`active_task_registry.py:72-79`).
  - Every save goes through `context=self._call_context` (`task_manager.py:147`, `:401-407`).
  - `ActiveTask` overwrites that field with the latest requester's context on start, on each queued request and on cancel (`active_task.py:621`, `:677`, `:902`; the TODO at `:901` notes the conflict).
- **Push dispatch:** caller-free, through `get_info_for_dispatch` (`base_push_notification_sender.py:86`, called from `active_task.py:410`). Same in v1.1.0 (`base_push_notification_sender.py:62` @ v1.1.0).
- **Cancel and resubscribe (main):** an owner-scoped `get(task_id, context)` comes first (`default_request_handler_v2.py:287`, `:639`). The registry cache-hit path also re-checks with the caller's context (`active_task_registry.py:107`, CWE-639 comment).
  - **Pinned v1.1.0 difference:** v1.1.0's `on_cancel_task` and `on_subscribe_to_task` go straight to `get_or_create`, whose cache hit returns a live task with no owner check (`git show v1.1.0:…/active_task_registry.py`, `get_or_create`). Fixed in `ddbf853` (#1159/#1172, first in v1.1.4).
- **Referenced tasks:** `SimpleRequestContextBuilder` fetches `reference_task_ids` with `task_store.get(task_id, context)`, the caller's context, so they are owner-scoped. It drops the ones it can't see without an error (`simple_request_context_builder.py:63-75`). The default handler turns this off (`should_populate_referred_tasks=False`, `default_request_handler_v2.py:194-196`; v1.1.0 also passes False). Python's `RequestContext` exposes `related_tasks`, `current_task` and `call_context`, but no store handle (`agent_execution/context.py:102-149`).

### 12b. Referenced tasks

The spec says nothing about referenced-task access beyond the §13.1 MUST and §3.4.3's SHOULDs. Reading referenced tasks for a SendMessage is task access done on the caller's behalf, and its content can reach the caller through the reply, so §13.1 applies (interpretation). The TCK has no test (requirement 7).

| Option | Shape | Trade-offs |
|---|---|---|
| **(1) Owner-scoped through the turn's context (recommended)** | `ContextTaskStorage(contextId, tenant, callContext, taskStorage)`. `get` calls `taskStorage.get(GetTaskRequest(taskId, historyLength, tenant), callContext)?.project(null, includeArtifacts)`. `getAll` runs N scoped gets. `getByContext` pages through `list(ListTasksRequest(contextId, historyLength, includeArtifacts, tenant), callContext)` | Matches Python and A′. Invisible tasks are `null` or omitted, as in Python. No new storage method. The Phase 5 node signatures stay |
| (2) Separate scoped method for references | e.g. `getReferenced(ids)` plus the current unscoped `get` | Two read paths for agents, and the unscoped one stays the easy default (`nodeA2ATaskGet` takes LLM-provided ids, `A2AAgentServerNodes.kt:130-141`) |
| (3) Document it as the agent's responsibility | today's behavior (`RequestContext.kt:23-24`) | The agent can't scope without reimplementing the storage's model. Fails §13.1 for any multi-owner deployment |

### 12c. What the server actually relies on

- **R1.** A task created through `update(Task, ctx)` is found by `get(taskId)` afterwards (`TaskEventStream.kt:253`, `TurnManager.kt:622`) and by `get(GetTaskRequest, ctx)` with the creating context. The blocking SendMessage result (`A2AServer.kt:660-661`) fails with "not found after the agent execution" otherwise.
- **R2.** Status state as written. Cancel decides with it (`A2AServer.kt:783-821`), and so do `markFailed` (`TaskEventStream.kt:338`) and the follow-up terminal check (`TurnManager.kt:505`).
- **R3.** `contextId` as written (`TurnManager.kt:499`, `TaskEventStream.kt:339`, `TurnManager.kt:729`).
- **R4.** History dedupe by `messageId` (`TaskHistory.kt:12-26`). The default `appendHistory` writes back with `update(Task)`, which relies on Task replacement (`TaskStorage.kt:156-159`).
- **Not relied on:** artifacts, metadata, message parts, history length, timestamps (ordering is storage-defined, `TaskStorage.kt:49-50`).

Proposed KDoc:
```kotlin
/**
 * Applies [event] to the stored task, or creates the task from a [Task] event.
 *
 * - A [Task] creates the task, or replaces the stored one (status, history, artifacts, metadata).
 * - A [TaskStatusUpdateEvent] replaces the status. The replaced status message, if any, is appended to
 *   [Task.history] unless a message with its [Message.messageId] is there. Metadata is merged.
 * - A [TaskArtifactUpdateEvent] adds, replaces, or appends to an artifact.
 *
 * A storage may apply a retention or redaction policy (e.g. trim history, redact parts, drop artifacts or
 * metadata), provided later reads still return the task's id, contextId and status state as last written,
 * and every message kept has its original messageId. The server uses these for follow-ups,
 * cancellation, failure handling and history deduplication. All reads return the task as kept.
 *
 * [context] is the call the event is persisted for: the request of the turn that produced it, or the
 * CancelTask that forced the cancellation. The server has authorized it. An authorization-aware storage may
 * record the owner from it when the task is created, and the task must then be visible to that caller in
 * [get] and [list].
 *
 * @throws TaskOperationException if the event can't be applied, e.g. a non-[Task] event for a missing task
 */
```
For `appendHistory`, keep the current text (`TaskStorage.kt:141-151`), replacing "Nothing else changes" with: "Nothing else changes, apart from the storage's own retention policy (see [update]). [context] is the request of the turn whose message it is."

### Q6. Do we need `ContextTaskStorage` / `ContextMessageStorage`?

**What they provide today:**
- `ContextTaskStorage` restricts only `getByContext` and `delete*` to the context. `get` and `getAll` are unrestricted and unscoped (`TaskStorage.kt:194-209`).
- Its surface is narrower than `TaskStorage`: no `update`, `appendHistory` or `list`.
- `ContextMessageStorage` checks `contextId` on save and replace (`MessageStorage.kt:62-100`). `MessageStorage` has no context at all.

**Users:**
- `RequestContext` (`RequestContext.kt:29-30`, built at `A2AServer.kt:601-602`).
- Phase 5 nodes: `nodeA2ATaskGet` and `nodeA2ATaskGetAll` (`A2AAgentServerNodes.kt:135`, `:170`), and the message nodes (`:29`, `:68`, `:87`, `:104`).
- Examples: `JokeWriterAgentExecutor.kt:110,129,167,191` and `SimpleJokeAgentExecutor.kt:44,47,68`.
- Nobody calls `ContextTaskStorage.delete*`, and no test covers `ContextTaskStorage`.

**Options:**
- **(a) Keep the wrapper and fix its scoping.** Same as (c) in effect, if the delete methods go and the context is bound.
- **(b) Expose `TaskStorage` (or a read-only interface) plus `callContext` on `RequestContext`.**
  - With `TaskStorage` itself, agents could call `update` or `appendHistory` outside `TurnEventProcessor`. That skips turn sequencing (`processorGate`/`turnSlots`), subscriber fan-out, push dispatch, terminal close, validation and history dedupe.
  - The unscoped `get` would stay one call away.
  - A read-only interface over the raw storage still makes every agent pass `callContext` and tenant itself.
- **(c, recommended) Small request-bound agent-facing type.** Keep `ContextTaskStorage` as a final class with a public constructor `(contextId, tenant, callContext, taskStorage)`, read-only: `get`, `getAll`, `getByContext`, scoped as in 12b. Drop `delete`/`deleteAll`; there is no DeleteTask in A2A, Python's `RequestContext` has no store access, and deleting a task mid-turn breaks the turn's later updates (`TaskOperationException`, `TaskStorage.kt:133-136`).
  - Testability: agents build it over `InMemoryTaskStorage()` in tests, as today.
  - For `ContextMessageStorage`, keep the type and pass `callContext` into `MessageStorage` methods (`save(message, context)`, …), so a storage can partition conversation memory by owner. In-memory ignores it.

**Phase 5 impact:** node signatures and `A2ATaskGetParams` are unchanged. Only the `@see` KDoc links and the behavior change: invisible tasks are null or omitted. Only `A2AServer.newRequestContext` builds the wrappers; examples and nodes don't.

## Decisions for the user

| # | Decision | Options | Recommendation |
|---|---|---|---|
| 12a | Method style | A / **A′** / B / C | **A′**: two exact tiers, rule as in the Answer |
| 12a-i | How the creating caller reaches the storage | **`update(event, ctx)` + `appendHistory(…, ctx)`** / separate `create(task, ctx)` with a context-free `update` | `update(event, ctx)`: one write path, like Python `save(task, ctx)` |
| 12a-ii | Agent-only methods on `TaskStorage` (`getAll`, `getByContext`, `delete`, `deleteAll`) | **remove** / keep with a context | Remove: no server caller, and the wrapper uses `get`/`list` instead |
| 12a-iii | Name of the internal `get(taskId, …)` | keep the overload / **rename** (e.g. `getForServer`) | Rename: `taskStorage.get(id)` in a subclass override is the easy mistake (the KDoc example at `A2AServer.kt:158` makes it) |
| 12b | Referenced-task reads | **(1) owner-scoped through `callContext`** / (2) separate method / (3) agent's job | (1) |
| 12c | `update` contract | as proposed / stricter "store as is" | As proposed |
| 12d | Agent-facing wrappers | (a) / (b) / **(c)** | (c): read-only `ContextTaskStorage(contextId, tenant, callContext, storage)` without delete; `ContextMessageStorage` passes the context to `MessageStorage` |

## Implementation slice outline (one slice, a2a-server only)

1. **`tasks/TaskStorage.kt`:**
   - two tiers in the interface KDoc;
   - `update(event, context)` with the 12c KDoc;
   - `appendHistory(taskId, message, context)`;
   - remove `getAll`, `getByContext`, `delete`, `deleteAll`;
   - optional rename (12a-iii);
   - rewrite `ContextTaskStorage` (12b and Q6).
2. **`tasks/InMemoryTaskStorage.kt`:** new signatures, removed methods, KDoc saying the context is ignored.
3. **`turn/TurnEventProcessor.kt`, `turn/TaskEventStream.kt`, `turn/TurnManager.kt`:** carry the turn's context (internal constructor and params) into `publish`, `endTurn`, `persist`, `appendHistory` and `monitorTurn`. In `withTaskProcessor`, the standalone processor and the `publish` lambda use the `ctx` param.
4. **`A2AServer.kt`:**
   - `:783,795,813` use the scoped get;
   - `newRequestContext` passes `request.tenant` and `ctx`;
   - fix the KDoc example at `:150-165` (scoped read; not found rather than `UnsupportedOperation`).
5. **`messages/MessageStorage.kt`, `messages/InMemoryMessageStorage.kt`:** add a context to the methods; `ContextMessageStorage` takes `callContext`.
6. **`notifications/PushNotificationConfigStorage.kt`:** docs only. `getAll` returns every caller's configs; authorization happened at `save`.
7. **`turn/RequestContext.kt`:** KDoc says referenced tasks are read through `taskStorage`, scoped to `callContext`.
8. **Tests (`a2a-server/src/commonTest`):**
   - A shared `OwnerScopedTaskStorage` fixture: it records the owner from `update(Task, ctx)` and filters `get`/`list`. Today it is ad hoc in `A2AServerPushNotificationsTest.kt:177-184` and `TurnManagerTest.kt:504-513`.
   - New:
     - an agent can't read another owner's task by id, through `getAll`, or through `getByContext`;
     - `update` gets the turn's context for agent events and the FAILED update, and the cancel caller's for a forced CANCELED;
     - a redacting/trimming decorator storage keeps follow-up, cancel and failure flows working;
     - `ContextTaskStorage` projection, for `includeArtifacts` and `historyLength`.
   - Update: `RequesterReadRecorder` (`A2AServerSendMessageTest.kt:113-131`), `CancelReadCounter` (`A2AServerCancelTest.kt:60-80`; the cancel reads are now scoped), `HangingTaskStorage` (`A2AServerShutdownTest.kt:612`), the decorator at `TaskEventStreamTest.kt:468`, `InMemoryTaskStorageTest`, `InMemoryMessageStorageTest`.
9. **Phase 5 (`agents-features-a2a-server`) and examples:** no source change is expected, beyond the KDoc `@see` links in `A2AAgentServerNodes.kt` that name `TaskStorage.get`. Phase 7 docs: none until then.

## Koog status

Covered in Details. The gaps:
- the context-free `update` (owner can't be recorded);
- the unscoped `ContextTaskStorage.get`/`getAll`/`getByContext`;
- the cancel result read unscoped and then returned (`A2AServer.kt:813-821`);
- the misleading KDoc example at `A2AServer.kt:150-165`.

## Testability notes

The TCK has no coverage: AUTH-SCOPE-001..003 are requirement definitions only, and nothing exercises `referenceTaskIds`. The SUT uses in-memory storages, which ignore the context, so the TCK result should not change; rerun the MUST gate as a regression check. Every new behavior needs Koog unit tests with the owner-scoped fixture (item 8). Which context a forced cancel writes with is observable only through a recording storage.

## Discrepancies

- Python v1.1.0 (the pinned interop server) lacks the owner check on the cancel/subscribe cache-hit path. Fixed in v1.1.4 (`ddbf853`). This is a Python bug, not a spec conflict; Koog's interop tests are single-owner, so they're unaffected.
- Python writes with whichever requester's context reached the `ActiveTask` last (`active_task.py:621,677,902`). Koog's per-turn context would be stricter. No spec text covers it.
- The spec doesn't say whether an inaccessible referenced task is an error or is ignored. Python ignores it. Either way, requirement 4 forbids revealing that it exists.

## Open questions

- **Client-supplied foreign `contextId`.** Koog accepts any client `contextId` (`TurnManager.kt:395`). Even with A′, which tasks a context contains depends on the storage's scoping, and messages on `MessageStorage`'s. §3.4.1 lets the server reject an unacceptable `contextId` (MUST reject if it can't accept). Should `A2AServer` have a context-acceptance hook? Route this separately.
- **Owner resolver.** Should Koog offer something like Python's `OwnerResolver`, so that the in-memory storages can be owner-partitioned out of the box?
