# Storage API shape, part 2: method shape, batch get and delete, MessageStorage (decision 12)

**Sources checked:**
- Koog @ `808e95f7a` (no product change since `storage-api-shape.md`)
- a2a-python `main` @ `05aaff6`
- A2A spec repository @ tag `v0.3.0` (only for the old protocol type names)

**Confidence:** high on facts, medium on style recommendations (taste, within the user's rules).

The user's decisions in the brief are taken as fixed: one `TaskStorage`, no task storage for agents, `loadReferencedTasks`, a context on every server read and write, deletes kept, `MessageStorage` with a context, and the context as a required last parameter.

## Answer

- **Q1. Only one non-RPC method has optional arguments: `TaskStorage.getAll(taskIds, historyLength = null, includeArtifacts = true)`** (`TaskStorage.kt:101-105`). Every other non-RPC method already has only required arguments: `update`, `appendHistory`, `delete*`, all of `MessageStorage`, and push `getAll`/`deleteAll`.
  - Recommendation (d): non-RPC storage methods take no optional arguments and return full data; callers shape it with `Task.project`. With `getAll` gone (Q2), (a) and (d) coincide: plain arguments, then the context last.
  - If a method ever needs options, use (c) with a nested, suffix-free type (e.g. `TaskStorage.Selection`). Never `*Request`, `*Response`, `*Params` or `*Query`: v0.3 protocol types used `*Params` (`TaskQueryParams`, `TaskIdParams`, `MessageSendParams`, … in `specification/json/a2a.json` @ v0.3.0).
- **Q2. Drop `getAll`.** Referenced tasks load as N scoped `get(GetTaskRequest(id, tenant = request.tenant), callContext)` calls, which is what Python does (`simple_request_context_builder.py:69-75`).
- **Keep `delete(taskId, callContext)` with a context, and drop `deleteAll`.** The server never calls either. The context lets an owner-scoping storage delete within the caller's scope, as Python's `TaskStore.delete(task_id, context)` does (`task_store.py:32`, `inmemory_task_store.py:183-197`).
  - "Admin" deletes are the storage's concern. `InMemoryTaskStorage` ignores the context, so `ServerCallContext()` works out of the box.
- **Q3.** Signatures for `MessageStorage`:
  - `save(message, callContext)`
  - `getByContext(contextId, callContext)`
  - `deleteByContext(contextId, callContext)`
  - `replaceByContext(contextId, messages, callContext)`

  The context is for owner partitioning of conversation memory. Only agents call these methods, through `ContextMessageStorage`; the server only builds the wrapper (`A2AServer.kt:602`).
- **Q4. Keep `ContextMessageStorage`** as `ContextMessageStorage(contextId, callContext, messageStorage)`. It keeps the contextId checks and a narrow surface. The Phase 5 message nodes and the examples stay unchanged.
- **Naming.** Call the parameter `callContext`, not `context`, in all storages. In `MessageStorage`, `context` next to `contextId` reads as the A2A context. `RequestContext.callContext` (`RequestContext.kt:27`) already uses that name.

## Q1. Shape of the non-RPC methods

These RPC-facing methods are fixed and identical under every option:
```kotlin
// TaskStorage
get(request: GetTaskRequest, callContext: ServerCallContext): Task?
list(request: ListTasksRequest, callContext: ServerCallContext): ListTasksResponse
// PushNotificationConfigStorage
save(config: TaskPushNotificationConfig, callContext: ServerCallContext): TaskPushNotificationConfig
get(request: GetTaskPushNotificationConfigRequest, callContext: ServerCallContext): TaskPushNotificationConfig?
list(request: ListTaskPushNotificationConfigsRequest, callContext: ServerCallContext): ListTaskPushNotificationConfigsResponse
delete(request: DeleteTaskPushNotificationConfigRequest, callContext: ServerCallContext)
```
The rule they set: protocol data first (one DTO), the call context last. The non-RPC methods follow the same pattern with plain values or Koog types in place of the DTO.

### (a) Plain arguments, the context last, no defaults before it
```kotlin
interface TaskStorage {
    /* RPC get, list */
    suspend fun getAll(taskIds: List<String>, historyLength: Int?, includeArtifacts: Boolean, callContext: ServerCallContext): List<Task>
    suspend fun update(event: TaskEvent, callContext: ServerCallContext)
    suspend fun appendHistory(taskId: String, message: Message, callContext: ServerCallContext)
    suspend fun delete(taskId: String, callContext: ServerCallContext)
    suspend fun deleteAll(taskIds: List<String>, callContext: ServerCallContext)
}
interface MessageStorage {
    suspend fun save(message: Message, callContext: ServerCallContext)
    suspend fun getByContext(contextId: String, callContext: ServerCallContext): List<Message>
    suspend fun deleteByContext(contextId: String, callContext: ServerCallContext)
    suspend fun replaceByContext(contextId: String, messages: List<Message>, callContext: ServerCallContext)
}
interface PushNotificationConfigStorage {
    /* RPC save, get, list, delete */
    suspend fun getAll(taskId: String): List<TaskPushNotificationConfig>          // dispatch, the one callerless read
    suspend fun deleteAll(taskId: String, callContext: ServerCallContext)          // TurnManager.kt:623, the turn's context
}
```
- Pros: uniform, and Java-friendly: no hidden defaults (the a2a modules use no `@JvmOverloads`; grep finds none).
- Cons: only `getAll` gets worse, with two positional arguments at every call.
- Kotlin note: defaults before a required last parameter would compile, but callers would then have to write `callContext = ctx` by name, which is why (a) forbids them.

### (b) Required arguments, the context, then defaults (Python's `delete_info(task_id, context, config_id=None)`, `push_notification_config_store.py:90-95`)
```kotlin
suspend fun getAll(taskIds: List<String>, callContext: ServerCallContext, historyLength: Int? = null, includeArtifacts: Boolean = true): List<Task>
```
All other signatures are as in (a). This breaks "context last" for exactly one method. In Kotlin, defaults in an interface are inherited, and overrides can't redeclare them. Java callers see no defaults. Not recommended.

### (c) A server-side options type, then the context last
```kotlin
package ai.koog.a2a.server.tasks
public interface TaskStorage {
    /** Which tasks [getAll] returns, and how they are shaped. Not a protocol type. */
    public data class Selection(
        val taskIds: List<String>,
        val historyLength: Int? = null,
        val includeArtifacts: Boolean = true,
    )
    suspend fun getAll(selection: Selection, callContext: ServerCallContext): List<Task>
    // delete(taskId, callContext): one argument, no class needed
}
```
- Naming: a nested `TaskStorage.Selection` (or `TaskStorage.Shape` for the projection alone) can't be confused with an a2a-core DTO. All a2a-core request types end in `Request` (`Requests.kt`: `GetTaskRequest`, `ListTasksRequest`, …), and v0.3 ones ended in `Params`.
- A class per single-argument operation (e.g. `TasksDeletion`) adds types and nothing else.
- Fine as the pattern for any future options. It is overkill for the one method if `getAll` goes.

### (d) Recommended: no optional parameters on non-RPC storage methods
Non-RPC reads return full data, and callers trim with `Task.project` (`TaskProjection.kt:26`). After Q2, this is (a) with no `getAll`; see the final signatures in "Resulting interfaces". For any future method with options, use (c) with a nested, suffix-free type.

## Q2. Batch get and deletes

**`getAll`.**
- The only candidate caller is referenced-task loading. Its tasks need the request's tenant, and that comes through `GetTaskRequest.tenant` (`Requests.kt:61`). A batch method would need a separate tenant argument or a list of `GetTaskRequest`s.
- Reference lists are short, and Python loads them with N gets (`simple_request_context_builder.py:69-75`).
- Drop it. If batching is ever needed, add `getAll(requests: List<GetTaskRequest>, callContext)` with a default implementation that loops `get`. Each element is a protocol GetTask, so no types are mixed.

**`delete`.** No server caller (grep of `a2a-server/src/commonMain`), and Python has none either: the hits are `inmemory_task_store.py:256`, `copying_task_store.py:57`, `cluster/task_store.py:128` and `cluster/database_task_store.py:357`, all forwarding adapters.

| Choice | Owner-scoping storage | Admin or servicing code | User "delete my task" endpoint |
|---|---|---|---|
| **`delete(taskId, callContext)` (recommended)** | deletes only within the caller's scope, like Python (`inmemory_task_store.py:183-197`) | passes a context the storage treats as administrative (a storage-defined marker in `state`), or uses the concrete storage's own API. In-memory ignores the context: `ServerCallContext()` works | passes the user's context: scoped by the storage |
| `delete(taskId)` without a context | must delete across owners, so it needs an id index | simplest | must authorize first with the scoped `get`, then delete (two calls, a small race) |

The context version keeps the rule "every `TaskStorage` method takes the call context", and matches Python.

**Make `delete` idempotent.** The KDoc says it throws if the task doesn't exist (`TaskStorage.kt:165`), but `InMemoryTaskStorage` ignores a missing task (`InMemoryTaskStorage.kt:177-181`). Push `delete` is already a no-op for a missing config (`PushNotificationConfigStorage.kt:74`), and Python only logs (`inmemory_task_store.py:194-197`). Returning `Boolean` (deleted or not) is an alternative, so admin code can tell when its context was wrong.

**`deleteAll`: drop it.** It has no caller, it is a loop over `delete`, and its partial-failure contract ("throws if some tasks cannot be deleted", `TaskStorage.kt:173`) doesn't match `InMemoryTaskStorage.kt:183-189`, which ignores missing ids.

**Push `deleteAll(taskId, callContext)`.** Keep it, with the turn's context. Its one caller (`TurnManager.kt:622-623`) deletes configs that the same turn saved with that context (`TurnManager.kt:413`), so an owner-scoped push storage deletes exactly what it saved. Python's equivalent is `delete_info(task_id, context, config_id=None)` ("all configurations for the task for the owner", `inmemory_push_notification_config_store.py:110-125`).

## Q3. MessageStorage with a context

```kotlin
public interface MessageStorage {
    public suspend fun save(message: Message, callContext: ServerCallContext)
    public suspend fun getByContext(contextId: String, callContext: ServerCallContext): List<Message>
    public suspend fun deleteByContext(contextId: String, callContext: ServerCallContext)
    public suspend fun replaceByContext(contextId: String, messages: List<Message>, callContext: ServerCallContext)
}
```
- **What the context is for:**
  - Owner partitioning of conversation memory. A client can send any `contextId` (`TurnManager.kt:395`), so without it, owner B naming A's context would read A's memory.
  - `InMemoryMessageStorage` ignores it (`InMemoryMessageStorage.kt:15-47`).
  - KDoc: "the call the agent acts for; an owner-aware storage partitions by it".
- **Who calls it:** the server never does. It only builds `ContextMessageStorage` (`A2AServer.kt:602`). Agents call it through `RequestContext.messageStorage`, and the wrapper supplies `contextId` and `callContext`.
- **Python:** no message store exists.

## Q4. Is `ContextMessageStorage` worth it?

What it does today:
- `save` and `replaceAll` check `contextId` (`MessageStorage.kt:62-66`, `:94-100`).
- `getAll` and `deleteAll` bind `contextId` (`:75-86`).

| Option | Checks | Phase 5 nodes (`A2AAgentServerNodes.kt:29,68,87,104`) and examples (`SimpleJokeAgentExecutor.kt:44,47,68`, `JokeWriterAgentExecutor.kt:110,167,191`) | Testability | Style |
|---|---|---|---|---|
| **Keep the class (recommended)**: `ContextMessageStorage(contextId, callContext, messageStorage)` | kept | unchanged (`context.messageStorage.save/getAll/replaceAll`) | build it over `InMemoryMessageStorage()` | one discoverable property; the agent never handles `contextId` or the call context |
| Extensions on `RequestContext` (`context.saveMessage(m)`, `context.contextMessages()`) | can be kept in the extensions | every call site changes | same | needs a public raw `MessageStorage` on `RequestContext`, so agents get two surfaces and the raw one bypasses the checks; extensions on generic `RequestContext<T>` are less discoverable than a property |
| Raw `MessageStorage` plus `callContext` | lost: only `InMemoryMessageStorage.replaceByContext` checks (`InMemoryMessageStorage.kt:36-42`); `save` doesn't | every call passes `contextId` and the call context | same | most verbose; easy to pass a wrong `contextId` |

Keeping the class costs one constructor parameter.

## Resulting interfaces (recommended)

```kotlin
public interface TaskStorage {
    public suspend fun get(request: GetTaskRequest, callContext: ServerCallContext): Task?
    public suspend fun list(request: ListTasksRequest, callContext: ServerCallContext): ListTasksResponse
    public suspend fun update(event: TaskEvent, callContext: ServerCallContext)
    public suspend fun appendHistory(taskId: String, message: Message, callContext: ServerCallContext) {
        val task = get(GetTaskRequest(id = taskId), callContext) ?: return   // tenant: see Open questions
        task.withAppendedHistory(message)?.let { update(it, callContext) }
    }
    public suspend fun delete(taskId: String, callContext: ServerCallContext)
}
public interface MessageStorage { /* as in Q3 */ }
public interface PushNotificationConfigStorage {
    /* RPC save, get, list, delete (callContext last) */
    public suspend fun getAll(taskId: String): List<TaskPushNotificationConfig>  // the one callerless read (dispatch)
    public suspend fun deleteAll(taskId: String, callContext: ServerCallContext)
}
public class RequestContext<T>(
    public val callContext: ServerCallContext,
    public val params: T,
    public val messageStorage: ContextMessageStorage,
    public val contextId: String,
    public val taskId: String,
    public val task: Task?,
    public val referencedTasks: List<Task>?,
)
```

## Decisions for the user

| # | Decision | Options | Recommendation |
|---|---|---|---|
| 12e | Shape of non-RPC methods | (a) / (b) / (c) / **(d)** | (d): no optional parameters; for future options, (c) with a nested, suffix-free type |
| 12f | Batch get | keep `getAll` / **drop** / later `getAll(List<GetTaskRequest>, callContext)` with a default | Drop; load referenced tasks with N scoped gets |
| 12g | Task delete | **`delete(taskId, callContext)`** / `delete(taskId)` | With the context; idempotent (`Unit`, or `Boolean`) |
| 12h | `deleteAll` (tasks) | keep / **drop** | Drop |
| 12i | Push `deleteAll` | **`deleteAll(taskId, callContext)`** / callerless | With the turn's context |
| 12j | Parameter name | `context` / **`callContext`** | `callContext` in all storages |
| 12k | `ContextMessageStorage` | **keep + `callContext`** / extensions / raw | Keep |
| 12l | Tenant on non-RPC writes (see Open questions) | ignore, as Python / tenant parameter / `ServerCallContext.tenant` | Decide separately; document it in this slice |

## Slice outline

1. **`tasks/TaskStorage.kt`:**
   - the interface above, with `callContext` last everywhere;
   - KDoc rules: a task stays visible to the context that created or updated it for the rest of that turn or cancel; the 12c `update` wording from `storage-api-shape.md`;
   - remove the internal `get(taskId, …)`, `getAll`, `getByContext` and `deleteAll`, and the `ContextTaskStorage` class.
2. **`tasks/InMemoryTaskStorage.kt`:** new signatures; the context is ignored; `delete` is idempotent.
3. **`messages/MessageStorage.kt`, `messages/InMemoryMessageStorage.kt`:** add `callContext`; `ContextMessageStorage(contextId, callContext, messageStorage)`.
4. **`notifications/PushNotificationConfigStorage.kt`, `InMemoryPushNotificationConfigStorage.kt`:** `deleteAll(taskId, callContext)`; KDoc for the callerless `getAll`; rename the parameter.
5. **`turn/RequestContext.kt`:** drop `taskStorage`, add `referencedTasks`, update the KDoc (`:20-24`).
6. **`A2AServer.kt`:**
   - the `loadReferencedTasks` constructor parameter and KDoc;
   - loading in `onSendMessageCommon` before `startTurn`: `message.referenceTaskIds.distinct()`, a scoped get with `request.tenant` and `ctx` for each, nulls dropped;
   - `newRequestContext(…, referencedTasks)`; `null` for cancel;
   - cancel re-reads scoped (`:783,795,813`);
   - fix the KDoc example (`:150-165`).
7. **`turn/TurnManager.kt`, `turn/TaskEventStream.kt`, `turn/TurnEventProcessor.kt`:** carry the turn's or cancel's context into `publish`, `persist`, `appendHistory`, `endTurn`/`markFailed` and `deletePushConfigsIfTaskAbsent`. The unscoped reads become scoped `get(GetTaskRequest(taskId, historyLength = 0, tenant), callContext)`; this needs the turn's tenant.
8. **Phase 5 (`agents/agents-features/agents-features-a2a-server`):** remove `nodeA2ATaskGet`, `nodeA2ATaskGetAll`, `A2ATaskGetParams` and `A2ATaskGetAllParams` (`A2AAgentServerNodes.kt:108-176`). The message nodes are unchanged.
9. **Examples:** `JokeWriterAgentExecutor.kt:129` reads `context.task`, which already has the full history (`RequestContext.kt:20-23`), instead of `taskStorage.get`.
10. **Tests (`a2a-server/src/commonTest`):**
    - New:
      - referenced tasks: flag off gives `null`; on gives reference order, deduplicated, invisible tasks dropped, full tasks; `null` for a request that isn't SendMessage;
      - a shared owner-scoped `TaskStorage`, `MessageStorage` and push fixture, replacing the ad hoc ones (`A2AServerPushNotificationsTest.kt:177-184`, `TurnManagerTest.kt:504-513`);
      - every storage call receives the expected context: the turn's for agent events, history and FAILED; the cancel caller's for the forced CANCELED; the turn's for push `deleteAll`.
    - Update: the recorders and decorators (`A2AServerSendMessageTest.kt:113-131`, `A2AServerCancelTest.kt:60-80`, `A2AServerShutdownTest.kt:612`, `TaskEventStreamTest.kt:468`) plus `InMemoryTaskStorageTest`, `InMemoryMessageStorageTest` and the push storage tests.
11. **TCK SUT:** no storage subclass exists (grep `TaskStorage` in `test-tck/a2a-test-server-tck` finds none). Rerun the MUST gate as a regression check.

## Discrepancies

`TaskStorage.delete`/`deleteAll` KDoc (`TaskStorage.kt:165,173`) says it throws for missing tasks. `InMemoryTaskStorage.kt:177-189` ignores them. Resolved by 12g and 12h.

## Open questions

- **Tenant on writes.** The scoped reads carry the tenant (`GetTaskRequest.tenant`, `ListTasksRequest.tenant`, `Requests.kt:61,85`), but `update`, `appendHistory`, `delete` and `MessageStorage` don't.
  - For a tenant-partitioning storage, a task written without a tenant can't be found by a read with one. The blocking SendMessage result read (`A2AServer.kt:660`) would then fail.
  - The default `appendHistory` above has no tenant to pass either.
  - Python passes no tenant to any store (no `tenant` in `src/a2a/server/tasks/*.py`).
  - Options: tenant-aware storages derive it from `callContext.state`, set by an `A2AServer` override (document only); a `tenant: String?` parameter before the context on writes; or a `tenant` on `ServerCallContext` (touches a2a-core and the transport). Needs its own decision (12l).
