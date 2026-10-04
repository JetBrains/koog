# A2A v1.x Migration Plan

Status snapshot: Koog `e45d756bc` (branch `eugenethedev/a2a-1.0`), 2026-10-02.

**Upstream baseline**

| Source     | Revision                                                                                             |
|------------|------------------------------------------------------------------------------------------------------|
| A2A spec   | `v1.0.1` (`3303592`), released 2026-05-28. `main` `65dadbd` has no normative changes relevant to us. |
| Python SDK | `v1.1.0` (pinned by `test-python-a2a-server`) and `main` (~`v1.2.1`)                                 |
| TCK        | `263b9cf` (pinned in `test-tck/setup_tck.sh`, which is the current TCK `main`; targets v1.0.x)       |

**Research notes:** everything below cites these reports in `.agents/research/`. They have full `file:line` citations.

| Report                                   | Contents                                                                                                         |
|------------------------------------------|------------------------------------------------------------------------------------------------------------------|
| `proto-drift.md`                         | The vendored proto compared with upstream                                                                        |
| `migration-guide-and-changelog-audit.md` | The official migration guide compared with upstream `whats-new-v1.md`, plus what that file is missing            |
| `koog-core-status.md`                    | `a2a-core` audit, one row per proto type                                                                         |
| `koog-transport-client-status.md`        | Audit of the JSON-RPC transports and `a2a-client`                                                                |
| `koog-server-and-tests-status.md`        | `a2a-server`, the test harnesses, the TCK, and dependents outside `a2a/`. This report holds requirements R1–R24. |

Legend: ✅ done · 🟡 partial / buggy · ❌ missing / not migrated · ❓ needs your decision · ⏭ skipped by decision

---

## 0. TL;DR

- **The vendored `a2a.proto` is current.** It is byte-identical to `v1.0.1`; the git blob `400cdba` matches. Upstream
  `main` changes only one comment, on `AgentInterface.url`, to allow `host:port` URLs for gRPC. No update is needed.
- **Upstream `docs/whats-new-v1.md` has known errors** (§1), some of them describing fields that don't exist in the
  proto. Don't use it as a reference. This plan is the source of truth for migration status.
- **`a2a-core`** Phase 1 is done (§3). The wire-format bugs are fixed: `securityRequirements`, API key `location`,
  `HTTP+JSON`, `DataPart.data` as any JSON value, optional `TaskPushNotificationConfig.taskId`, and the others.
  Skipped by decision: integer enums and snake_case names (1.13). The core tests assert the v1 shapes.
- **Transports and `a2a-client`** have all 11 methods wired with v1 names and shapes. Missing:
    - server-side `A2A-Version` validation;
    - `ErrorInfo` in error data;
    - plain-JSON errors before an SSE stream starts;
    - interface selection and tenant propagation;
    - server-side `A2A-Extensions` support (the client sends the header from `ClientCallContext.headers`).
- **`a2a-server` is not migrated and does not compile** (100+ errors). It also needs behavioural changes: blocking by
  default, subscribe semantics, and push via `StreamResponse`. Some of those changes reach into session and storage
  design and need your decisions (§4).
- **`a2a-test` and `test-python-a2a-server` are already migrated.** Not migrated: the server tests, the TCK SUT,
  `agents-features-a2a-{core,client,server}`, the examples, and the docs. The root `./gradlew build` is broken until
  they are migrated.

---

## 1. Known errors in upstream `whats-new-v1.md`

Don't use upstream `whats-new-v1.md` as a reference; use the proto and the spec. Source:
`migration-guide-and-changelog-audit.md`. Upstream fixed some of these errors on `main`
after `v1.0.1` (PRs #2056 and #2165); others are still wrong there.

| Upstream claim                                           | Reality (v1.0.1 proto/spec)                                                                                                                                                                                                                                                               |
|----------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Task.createdAt` / `Task.lastModified`                   | **Do not exist.** The only timestamps are `TaskStatus.timestamp` and `ListTasksRequest.statusTimestampAfter`.                                                                                                                                                                             |
| Push config `configId` and `createdAt`                   | **Do not exist.** The field is `id`, which already existed in v0.3. Requests use `{taskId, id}`.                                                                                                                                                                                          |
| `TaskArtifactUpdateEvent.index`                          | **Does not exist** (still wrong on upstream `main`).                                                                                                                                                                                                                                      |
| Stream members `taskStatusUpdate` / `taskArtifactUpdate` | The real members are `statusUpdate` / `artifactUpdate`. The v0.3 `kind` values were `status-update` / `artifact-update`.                                                                                                                                                                  |
| `extensions[]` is new on Message, Artifact and Task      | Message and Artifact already had it in v0.3. **Task has no `extensions`.**                                                                                                                                                                                                                |
| Implicit and password OAuth flows removed                | **Deprecated, not removed** (`[deprecated = true]`). The JSON keys are `implicit`, `password`, `authorizationCode`, `clientCredentials` and `deviceCode`, not `*Flow`. The device-code fields `verification_uri` / `user_code` are not in the spec.                                       |
| Push `authentication` "enhanced"                         | The change is **breaking**: `schemes[]` became a single required `scheme` plus optional `credentials`.                                                                                                                                                                                    |
| Cursor pagination `cursor` / `limit` / `nextCursor`      | The fields are `pageToken`, `pageSize` and `nextPageToken`, plus `totalSize`. ListTasks did not exist in v0.3.                                                                                                                                                                            |
| ErrorInfo MUST in JSON-RPC `error.data`                  | In v1.0.1 it is **SHOULD** for JSON-RPC (it was MUST in v1.0.0, and the TCK still enforces MUST). The `@type` on each `data` entry is MUST. The codes are unchanged. -32006…-32009 = InvalidAgentResponse, ExtendedAgentCardNotConfigured, ExtensionSupportRequired, VersionNotSupported. |
| Timestamps "with millisecond precision"                  | Millisecond precision is SHOULD. The fractional part may be omitted when parsing.                                                                                                                                                                                                         |

**Missing from `whats-new-v1.md` (relevant to JSON-RPC):**

- **Result shapes:**
    - `SendMessage` returns the wrappers `{"task":…}` or `{"message":…}`.
    - Each SSE `data:` line is a JSON-RPC response whose `result` is a `StreamResponse`.
    - `kind` is gone from Task and Message too.
- **Send semantics:**
    - `blocking` was replaced by `returnImmediately`, with the meaning inverted: blocking is the default, and a blocking
      call also returns on interrupted states.
    - `historyLength` semantics: unset means no client limit, 0 means no history, N means at most N.
    - `GetTask` lost `metadata`.
- **Streaming:**
    - The first event of a task stream is a `Task`.
    - A message-only stream has one Message and then closes.
    - `SubscribeToTask` on a terminal task, or sending to a terminal task, returns UnsupportedOperation (-32004).
- **Push notifications:** the webhook payload is a `StreamResponse`.
- **Service parameters:**
    - `A2A-Version` is MUST on every request.
    - A missing header means `0.3`; compare Major.Minor only; unsupported returns -32009.
    - `X-A2A-Extensions` was renamed to `A2A-Extensions`.
    - In JSON-RPC, service parameters are sent as HTTP headers.
- **Tenant:** `tenant` goes in JSON-RPC `params`. The client copies it from the chosen `AgentInterface`.
- **ListTasks rules:**
    - pageSize defaults to 50, valid range 1..100;
    - results sorted by status timestamp, newest first;
    - `artifacts` omitted unless `includeArtifacts`;
    - `nextPageToken` always present (`""` on the last page).
- **Enums:** new `TASK_STATE_UNSPECIFIED` and `ROLE_UNSPECIFIED`.
- **AgentCard reshape:**
    - `stateTransitionHistory` removed;
    - `transport` → `protocolBinding`;
    - `security` → `securityRequirements[{schemes:{x:{list:[…]}}}]`;
    - SecurityScheme is now a oneof wrapper (no `type` field);
    - API key `in` → `location`.
- **ProtoJSON rules:**
    - fields at default values are omitted unless the field has explicit presence;
    - int32 values are JSON numbers;
    - bytes are base64, and parsers should also accept URL-safe and unpadded base64;
    - unknown fields are tolerated.

Section 2 has the real status of each area.

---

## 2. Real status by area

### Core model objects (`a2a-core`)

| Area                              | Actual    | Notes                                                                                                                                                                                                                                                              |
|-----------------------------------|-----------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| TaskStatus                        | ✅         | `TASK_STATE_UNKNOWN` removed and `TASK_STATE_UNSPECIFIED` added. `timestamp` defaults to `null`, so a missing value is no longer invented. Callers that want the current time must set it.                                                                         |
| Message                           | ✅         | `ROLE_UNSPECIFIED` added.                                                                                                                                                                                                                                          |
| Part                              | ✅         | Member-based oneof ✅. `DataPart.data` is a `JsonElement`. Base64 decoding accepts URL-safe and unpadded input and throws `SerializationException`. Unknown sibling keys are ignored.                                                                               |
| Artifact                          | ✅         |                                                                                                                                                                                                                                                                    |
| AgentCard                         | ✅         | `securityRequirements: List<SecurityRequirement>` (with `StringList`) on AgentCard and AgentSkill. KDoc updated.                                                                                                                                                   |
| AgentCapabilities                 | ✅         | `extendedAgentCard` and the presence of the optional bools are kept.                                                                                                                                                                                               |
| PushNotificationConfig            | ✅         | The flattened `TaskPushNotificationConfig` ✅. `taskId` is `String?` (absent for inline configs in SendMessage; Create requires it, to be validated by server and client). `ListTaskPushNotificationConfigsResponse.configs` defaults to `emptyList()`.             |
| Stream Event Objects              | ✅         | `statusUpdate` / `artifactUpdate`, no `final`.                                                                                                                                                                                                                     |
| OAuth 2.0 Security                | ✅         | `APIKeySecurityScheme.location` (was `in`). `TransportProtocol.HTTP_JSON = "HTTP+JSON"` (renamed from `HTTP_JSON_REST`). The deprecated Implicit and Password flow fields have defaults.                                                                           |
| Requests (all 11 ops + `tenant`)  | ✅         | `GetTask` has no `metadata`.                                                                                                                                                                                                                                       |
| ListTasks request/response        | ✅ (model) | Server behaviour ❌ (see §3 Phase 3).                                                                                                                                                                                                                               |
| Error taxonomy                    | ✅         | -32008 `A2AExtensionSupportRequiredException` added. -32007 renamed to `A2AExtendedAgentCardNotConfiguredException`. `A2AException.reason` / `A2AErrorReasons` map each A2A error to its ErrorInfo reason. The server transport does not emit them yet (item 3.16). |
| Versioning constants / validation | ✅         | `isVersionCompatible` compares Major.Minor, and blank means `0.3`. Added `normalizeVersion` . Nothing calls them yet (item 3.7).                                                       |

### Operations and bindings (transports + client + server)

| Area                               | Actual                                                                                                                                                                                                         |
|------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| SendMessage / SendStreamingMessage | Client and transport ✅. **Server ❌**: v0.3 `blocking` logic, `final`; blocking by default and the stream rules are not implemented.                                                                          |
| GetTask                            | Client and transport ✅. Server ❌: it doesn't compile, and it strips artifacts.                                                                                                                               |
| ListTasks                          | Client and transport ✅. Server ❌: not implemented, and `TaskStorage` has no list API.                                                                                                                        |
| CancelTask                         | Client and transport ✅. Server ❌: doesn't compile.                                                                                                                                                           |
| GetExtendedAgentCard               | Client and transport ✅; the client replaces its cached card. Server ❌: still uses `supportsAuthenticatedExtendedCard`, and the UnsupportedOperation and -32007 split is missing.                             |
| SubscribeToTask                    | Client ✅, with no streaming-capability check. Server ❌: no Task snapshot first, no terminal → -32004, and no subscribe to an input-required task without a live session.                                     |
| Push config CRUD                   | Client and transport ✅. Server ❌: uses the old `PushNotificationConfig` and `schemes`; payload is not a `StreamResponse`; missing config gives InternalError instead of TaskNotFound; inline config ignored. |
| Multi-tenancy                      | Model ✅. Client 🟡: no automatic tenant from the `AgentInterface`. The server ignores it, which is allowed (MAY).                                                                                             |
| ID simplification                  | ✅ in model and transport.                                                                                                                                                                                     |
| HTTP+JSON `/v1` prefix             | N/A, because Koog has no REST binding.                                                                                                                                                                         |
| JSON-RPC method names              | ✅ (`A2AMethod.kt`). Constant names `GetAuthenticatedExtendedAgentCard` and `ListTaskPushNotificationConfig` are left over.                                                                                    |
| Error model (JSON-RPC)             | 🟡. `data` is an array ✅. The server always sends `data: []` with no ErrorInfo. The client parses `data` leniently (missing, string or object still give the typed `A2AException`, 2.1 ✅).                     |
| A2A-Version header                 | The client sends `1.0` ✅. The **server never validates it** ❌.                                                                                                                                               |
| A2A-Extensions header              | Client ✅ (explicit per call through `ClientCallContext.headers`, 2.5). Server ❌ (3.7).                                                                                                                       |
| Agent Card discovery               | `/.well-known/agent-card.json` ✅. Interface selection by `protocolBinding` + `protocolVersion` ❌.                                                                                                            |
| Agent Card signatures (JWS/JCS)    | ❌. Not implemented. It is optional, a MAY for the client.                                                                                                                                                     |

---

## 3. Work plan

Phases are ordered by dependency. Each item lists module, status, and TCK and requirement references. R-numbers refer to
`koog-server-and-tests-status.md`.

### Phase 1: Finish `a2a-core` ✅

| #    | Item                                                                                                                                                                                                                     | Status | Notes                                                                                                                                                                                                                     |
|------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1.1  | `AgentCard.security` and `AgentSkill.security` → `securityRequirements: List<SecurityRequirement>`, add `StringList`                                                                                                     | ✅      | `SecurityRequirement(schemes: Map<String, StringList>)`; `Security` typealias removed                                                                                                                                     |
| 1.2  | `APIKeySecurityScheme` `in` → `location`                                                                                                                                                                                 | ✅      | The `In` enum name is kept                                                                                                                                                                                                |
| 1.3  | `TransportProtocol.HTTP_JSON_REST` value → `"HTTP+JSON"` (consider renaming the constant)                                                                                                                                | ✅      | Constant renamed to `TransportProtocol.HTTP_JSON`                                                                                                                                                                         |
| 1.4  | `DataPart.data`: `JsonObject` → `JsonElement` (`google.protobuf.Value`)                                                                                                                                                  | ✅      |                                                                                                                                                                                                                           |
| 1.5  | `TaskState`: remove `TASK_STATE_UNKNOWN`, add `TASK_STATE_UNSPECIFIED`; `Role`: add `ROLE_UNSPECIFIED`                                                                                                                   | ✅      | Both are plain enum entries; no rejection on decode                                                                                                                                                                       |
| 1.6  | `TaskPushNotificationConfig.taskId` becomes optional                                                                                                                                                                     | ✅      | `taskId: String? = null`. Create validation of `taskId` moves to the server and client (Phases 2–3).                                                                                                                      |
| 1.7  | `ListTaskPushNotificationConfigsResponse.configs` defaults to `emptyList()`; review other `repeated` fields for missing defaults                                                                                         | ✅      | `configs` defaults to `emptyList()`. `ListTasksResponse` is unchanged: a default on `nextPageToken` would stop it being emitted (R15).                                                                                    |
| 1.8  | `TaskStatus.timestamp`: no `now()` default on decode (make it nullable, or set it only on construction)                                                                                                                  | ✅      | `timestamp` defaults to `null`. The server and the TCK SUT must set it explicitly (3a, 4.1).                                                                                                                              |
| 1.9  | Loosen over-required fields: `Task.contextId`, `AgentExtension.uri`, the deprecated flow fields                                                                                                                          | ✅      | `Task.contextId`, `AgentExtension.uri` and the deprecated flows' URL fields are nullable (`= null`); the deprecated flows' `scopes` default to `emptyMap()`. `TaskEvent` no longer narrows `contextId` to non-null (status and artifact events still require it).                                                                                                        |
| 1.10 | Add the -32008 `ExtensionSupportRequired` code and exception; rename -32007 to `ExtendedAgentCardNotConfigured`                                                                                                          | ✅      |                                                                                                                                                                                                                           |
| 1.11 | Map each A2A exception to an ErrorInfo `reason` (`TASK_NOT_FOUND`, … `VERSION_NOT_SUPPORTED`) with domain `a2a-protocol.org`, so it is available to the transport layer                                                  | ✅      | `A2AException.reason` / `A2AErrorReasons`, checked against the Python SDK. The server transport has to emit them (3.16).                                                                                                          |
| 1.12 | Version utilities: Major.Minor comparison, empty → `0.3`; decide supported versions                                                                                                                                      | ✅      | Major.Minor, empty → `0.3`, `SUPPORTED_VERSIONS = ["1.0"]`. Where the check lives is still open (§4).                                                                                                                     |
| 1.13 | ProtoJSON tolerance: unknown sibling keys in oneof wrappers, URL-safe and unpadded base64, optionally integer enums and snake_case                                                                                       | ⏭      | Done: unknown sibling keys in oneof wrappers, URL-safe and unpadded base64. Skipped by decision: integer enums and snake_case names. `null` for a non-null field needs `coerceInputValues` in the transport `Json` (2.x). |
| 1.14 | Clean up v0.3 leftovers: KDoc links (`A2APaths.kt:10`, `ClientTransport.kt:126`, `ServerTransport.kt:137`), AgentCard KDoc                                                                                               | ✅      |                                                                                                                                                                                                                           |
| 1.15 | Tests: fix `AgentCardSerializationTest` and `SerializersTest` (they assert `security`, `in`, `HTTP+JSON/REST`); add wire tests for Task, Message, all Part variants, StreamResponse, requests, enums, base64, timestamps | ✅      | Added tests for Task, TaskState/Role, Parts, StreamResponse/SendMessageResponse, requests, responses, exceptions and versions                                                                                             |

### Phase 2: Client transport and `a2a-client`

Server-side transport work is in Phase 3 (3c). The JSON-RPC transport test suites currently pass on JVM.

| #   | Item                                                                                                                                  | Status | Notes                                                                                                                                                                                                                                                         |
|-----|---------------------------------------------------------------------------------------------------------------------------------------|--------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 2.1 | Client: tolerant `error.data` parsing (missing, string or object) → still an `A2AException`                                           | ✅     | `JSONRPCClientTransport.toErrorDetails` (overridable): an array is decoded entry by entry and a bare object is treated as a one-element array. Absent or primitive `data` gives no details. Entries without a string `@type` are dropped. Tagged entries that fail typed decoding (e.g. `ErrorInfo` with non-string metadata) are kept as `GenericErrorData`. Tests: `JSONRPCClientTransportTest`. |
| 2.2 | Client: handle an `application/json` error response to a streaming request, instead of failing with `SSEClientException`             | ✅     | `HttpJSONRPCClientTransport.requestStreaming` catches `SSEClientException`. If the response it carries is `application/json` and decodes to a `JSONRPCResponse`, that response is emitted, so a JSON-RPC error becomes the typed `A2AException`. Anything else rethrows the original exception. Also fixes the subscribe-after-terminal race. Tests: `HttpJSONRPCClientTransportTest` (jvmTest). Server side: 3.17. |
| 2.3 | Client: check the streaming capability in `subscribeToTask`; throw A2A errors instead of `IllegalStateException`                      | ✅     | `subscribeToTask` now calls `checkStreamingSupported()`. The `check*Supported` helpers throw `A2AUnsupportedOperationException` (-32004) for streaming and the extended card, and `A2APushNotificationNotSupportedException` (-32003) for push, matching the server codes (3.6). Tests: `A2AClientTest` (commonTest). |
| 2.4 | Client: select an interface from `supportedInterfaces` by `protocolBinding` + `protocolVersion`, and propagate `tenant` automatically | ❌ ❓  | API design (a ClientFactory-like API?). `AgentCardResolver` only fetches the card.                                                                                                                                                                            |
| 2.5 | Client: send the `A2A-Extensions` header                                                                                              | ✅     | Decided: explicit per-call only. The caller passes `A2AHeaders.A2A_EXTENSIONS` in `ClientCallContext.headers`; `A2AClient.prepareContext` forwards it untouched and the transport sends it as an HTTP header. No client-level extension list and no check of `required` card extensions. Documented in the `prepareContext` KDoc; tested in `A2AClientTest`. Server side: 3.7. |
| 2.6 | Agent Card signature verification (JWS + JCS)                                                                                         | ❌     | Optional, MAY. Defer?                                                                                                                                                                                                                                         |
| 2.7 | Cleanup: rename leftover constants, fix the v0.3 KDoc link, fix the test fixture                                                      | ❌     | Constants `GetAuthenticatedExtendedAgentCard` and `ListTaskPushNotificationConfig` (`A2AMethod.kt:10`, `:19`; the server transport uses them too). KDoc link `A2AClient.kt:172`. Fixture `protocolVersion = "1.0.1"` at `HttpJSONRPCClientTransportTest.kt:187`. |
| 2.8 | SSE client tests: replace the `@Ignore`d MockEngine tests with Ktor `testApplication`                                                 | ✅     | `HttpJSONRPCClientTransportTest` moved to `jvmTest`. It runs the real transport against a fake JSON-RPC server in `testApplication` (the pattern of `HttpJSONRPCServerTransportTest`), so `testSendMessageStreaming` and `testSubscribeToTask` now run. `ktor-client-mock` removed. The class is JVM-only, since the common source set has no SSE-capable engine. |

### Phase 3: Server: JSON-RPC server transport and `a2a-server` (largest phase)

**3a. Compile against the v1 core API.** These are mechanical changes:

- the `RequestHandler` signatures (no `Request`/`Response` wrappers; v1 request types);
- `onSubscribeToTask`, `onCreateTaskPushNotificationConfig` and `onGetExtendedAgentCard`;
- `capabilities.extendedAgentCard`;
- remove `final` from `SessionEventProcessor`;
- push layer on `TaskPushNotificationConfig` and `AuthenticationInfo.scheme`.
- set `TaskStatus.timestamp` explicitly (it no longer defaults to now), and handle the nullable `TaskPushNotificationConfig.taskId`.

**3b. v1 behaviour.** Requirement numbers R1–R24 are from `koog-server-and-tests-status.md`.

| #    | Item                                                                                                                                                                                                                                            | Req      | TCK                                        | Status                         |
|------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------|--------------------------------------------|--------------------------------|
| 3.1  | Blocking by default; `returnImmediately`; return on terminal **or interrupted** states with full artifacts                                                                                                                                      | R1, R23  | CORE-SEND-*                                | ❌                             |
| 3.2  | `Message.taskId` must exist (else TaskNotFound); infer `contextId` from the task; reject a mismatched `contextId`                                                                                                                               | R2, R4   | CORE-MULTI-006                             | ❌                             |
| 3.3  | Send to a terminal task → UnsupportedOperation                                                                                                                                                                                                  | R3       | CORE-SEND-002                              | ❌                             |
| 3.4  | Stream rules: the first event is a `Task`; a message-only stream has one Message and then closes; close on terminal state                                                                                                                       | R5       | STREAM-*                                   | ❌                             |
| 3.5  | SubscribeToTask: Task snapshot first; terminal → -32004; unknown → TaskNotFound; works for input-required tasks without a live session; events broadcast to all subscribers in order                                                            | R6, R7   | STREAM-SUB-001..003, STREAM-ORDER-002..004 | ❌ ❓ (session redesign)       |
| 3.6  | Capability gating: streaming off → -32004; push off → -32003; extended card off → -32004, declared but missing → -32007                                                                                                                         | R8–R10   | CORE-CAP-003                               | ❌                             |
| 3.7  | `A2A-Version` validation (empty → 0.3; unsupported → -32009). `A2A-Extensions`: parse it and expose it in the call context; ExtensionSupportRequired check. Decide whether this lives in the server transport or in `A2AServer`. Today `ServerCallContext` carries only the raw headers (`Routes.kt:80-82`), and nothing calls `isVersionCompatible`. | R11, R12 | VER-SERVER-002 (MUST)                      | ❌ ❓                          |
| 3.8  | GetTask includes artifacts; `historyLength` semantics (unset, 0, N)                                                                                                                                                                             | R13, R14 | CORE-GET-001, CORE-HIST-*                  | ❌                             |
| 3.9  | ListTasks: filters (contextId, status, statusTimestampAfter), sorted by status timestamp descending, page tokens, default 50 / range 1..100, omit artifacts unless `includeArtifacts`, `nextPageToken` always present, `historyLength` per task | R15      | CORE-LIST-001..005                         | ❌ ❓ (`TaskStorage` list API) |
| 3.10 | CancelTask: terminal → TaskNotCancelable; unknown → TaskNotFound                                                                                                                                                                                | R16      | CORE-CANCEL-001/002                        | ❌                             |
| 3.11 | Push config CRUD: flattened storage; server-assigned id; missing config → TaskNotFound; Delete is idempotent                                                                                                                                    | R17, R18 | PUSH-*                                     | ❌ ❓ (storage flattening)     |
| 3.12 | Honour the inline `SendMessageConfiguration.taskPushNotificationConfig`                                                                                                                                                                         | R19      | PUSH-DELIVER-001..003                      | ❌                             |
| 3.13 | Push delivery: `StreamResponse` payload for each event; decide what to do with `A2AHeaders.X_A2A_NOTIFICATION_TOKEN` (not in the spec, a Python convention); `Authorization: <scheme> <credentials>`; at least one attempt; 10–30 s timeout                                                                                                          | R20      | PUSH-DELIVER-*                             | ❌ ❓ (sender contract)        |
| 3.14 | Decide whether the server persists user follow-up messages into task history                                                                                                                                                                    | —        | CORE-HIST-*                                | ❓                             |
| 3.15 | Server tests: rewrite them (none compile); re-enable the `@Disabled` JSON-RPC integration test class                                                                                                                                            | —        | —                                          | ❌                             |

**3c. JSON-RPC server transport** (`a2a-transport-core-jsonrpc`, `a2a-transport-server-jsonrpc-http`). These modules depend only on
`a2a-core`, so this work doesn't wait for 3a.

| #    | Item                                                                                                                       | TCK                                                 | Status | Notes                                                                                                                                                                                                                                   |
|------|----------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------|--------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 3.16 | Emit ErrorInfo in `error.data`                                                                                             | JSONRPC-ERR-003 (the TCK treats it as MUST)         | ❌     | `JSONRPCServerTransport.kt:265-276` encodes `A2AException.details`, which defaults to empty, so `data` is always `[]`. Build the ErrorInfo from `A2AException.reason` with domain `a2a-protocol.org`.                                  |
| 3.17 | Errors raised before the first stream event go out as a plain JSON-RPC response (`application/json`), not over SSE         | STREAM-SUB-004 (MUST)                               | ❌ ❓  | `JSONRPCServerTransport.kt:158-180` catches errors inside the flow and sends them as an SSE event. Only param-decoding errors go out as plain JSON, because `toRequest` runs eagerly. Interacts with how `A2AServer` streaming flows fail. Client side: 2.2. |
| 3.18 | Accept a missing `params` for no-arg methods such as `GetExtendedAgentCard`                                                | —                                                   | ❌     | A missing `params` becomes `JsonNull` (`JSONRPCServerTransport.kt:357`) and fails to decode into the request object (`:212-221`) → InvalidParams.                                                                                      |
| 3.19 | Serialize `"id": null` in error responses, as JSON-RPC 2.0 requires; fix `JsonRpcSerializationTest`                        | —                                                   | ❌     | `JSONRPCJson` sets `explicitNulls = false` (`Serialization.kt:29-33`), so the `id` is dropped. `JsonRpcSerializationTest.kt:135-151` asserts the omission.                                                                             |
| 3.20 | Fix the `Routes.kt` KDoc example and the test fixture                                                                      | —                                                   | ❌     | The example (`Routes.kt:33-62`) calls `a2aJsonRPCTransportRoute(transport, path)` and `a2aAgentCardRoute(card, path)`. The real names are `a2aJsonRpcTransportRoute` and `a2aAgentCardRoute`, and both take `path` first. Fixture `protocolVersion = "1.0.1"` at `HttpJSONRPCServerTransportTest.kt:72`. |
| 3.21 | Optional: `Cache-Control`/`ETag` on the agent-card route                                                                   | CARD-CACHE-001/002 (SHOULD, reported as xfail)      | ❌     | `Routes.kt:114-125` sets neither header.                                                                                                                                                                                                |

### Phase 4: TCK SUT and CI

| #   | Item                                                                                                                                                                                                              | Status |
|-----|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------|
| 4.1 | Rewrite `test-tck/a2a-test-server-tck` (also set `TaskStatus.timestamp` explicitly) with the v1 agent card (`protocolVersion` currently says `0.3.0`) and the v1 TCK messageId-prefix scenarios (`tck-complete-task`, `tck-input-required`, `tck-stream-*`, …) | ❌     |
| 4.2 | Decide whether the SUT declares `pushNotifications`. If it does, it needs 1.6 and 3.11–3.13.                                                                                                                      | ❓     |
| 4.3 | Re-enable CI: remove `if: false` at `.github/workflows/a2a-tck-test.yml:22`. Optionally pass `--transport jsonrpc`. The TCK pin is already v1, and the rest of the workflow still fits.                           | ❌     |
| 4.4 | Gate: all MUST tests green. SHOULD failures show up as xfail.                                                                                                                                                     | ❌     |

### Phase 5: Dependents outside `a2a/`

These modules block the root `./gradlew build`.

| #   | Item                                                                                                                                                                                                                                                                                                                                 | Status                  |
|-----|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------|
| 5.1 | `agents/agents-features/agents-features-a2a-core`. `MessageConverters.kt` and its test use `FilePart`/`FileWithBytes`/`FileWithUri`; change them to `FileBytesPart` / `FileUrlPart`. Consider `AttachmentContent.Binary.Bytes`.                                                                                                      | ❌                      |
| 5.2 | `agents-features-a2a-client/A2AAgentClientNodes.kt`, nodes for the new client API:<br>• drop `Request`/`*Params`;<br>• `ClientCallContext` is no longer `@Serializable`;<br>• `card`, `getExtendedAgentCard`, `subscribeToTask`, `createTaskPushNotificationConfig`, `listTaskPushNotificationConfigs`;<br>• add a `listTasks` node. | ❌                      |
| 5.3 | `agents-features-a2a-server`: follow the final server API (`RequestContext`, `SessionEventProcessor`, storages)                                                                                                                                                                                                                      | ❌ (blocked by Phase 3) |
| 5.4 | `examples/simple-examples/.../a2a/{simplejoke,advancedjoke}`:<br>• client: v1 API, no `event.kind` or `final`;<br>• server: v1 card;<br>• `JokeWriterAgentExecutor` must return after `INPUT_REQUIRED`, which used to rely on `final = true`;<br>• blocking is now the default, so results change.                                   | ❌                      |
| 5.5 | `docs/docs/a2a/*.md` (`a2a-client.md`, `a2a-server.md`, `a2a-koog-integration.md`, `index.md:34` "v0.3.0"). Rewrite the snippets; they are not Knit-compiled, so nothing catches regressions.                                                                                                                                        | ❌                      |

### Phase 6: Housekeeping

- Update the stale v0.3 references:
    - ✅ the spec link in `a2a/AGENTS.md` (`a2a/CLAUDE.md` is a symlink to it), now `v1.0.1`;
- Commit `.agents/research/`. `.agents/.gitignore` no longer ignores it, and this plan cites those reports.
- Optional: track the unreleased upstream comment change on `main` that relaxes the `AgentInterface.url` HTTPS rule for
  gRPC. Don't enforce HTTPS-only checks on non-HTTP bindings.
- Watch the Python SDK version: `1.1.0` returns InvalidParams for operations on terminal tasks, and `1.2.x` fixes this
  to UnsupportedOperation. Consider bumping `test-python-a2a-server`.

---

## 4. Decisions needed from you

Per `a2a/CLAUDE.md`, these are architecture and API decisions that need your confirmation.

1. ~~**Public model API breaks**~~ Decided and done: `securityRequirements`, the UNSPECIFIED enum entries, optional
   `TaskPushNotificationConfig.taskId` (`String?`), and nullable `TaskStatus.timestamp` (default `null`).
2. **Version policy (3.7):** `a2a-core` now compares Major.Minor with empty → `0.3` and supports `["1.0"]`. Still open:
    - reject a missing header (meaning 0.3) with -32009, or accept it;
    - where the check lives: the transport or `A2AServer`.
3. **Pre-stream errors (3.17):** how `A2AServer` streaming handlers signal failure before the first event, so the
   transport can answer with plain JSON.
4. **Client interface selection and tenant (2.4):** whether to add a factory-style API that picks the interface and
   injects the tenant.
5. **Extensions (3.7):** ~~client side (2.5)~~ decided: explicit per call. Still open: the server-side ExtensionSupportRequired check.
6. **Session redesign (3.5):** a per-task event stream that outlives a single execution, which SubscribeToTask on
   input-required tasks needs.
7. **Storage interfaces (3.9, 3.11, 3.13):**
    - the `TaskStorage` list/query API with pagination;
    - flattening `PushNotificationConfigStorage`;
    - the `PushNotificationSender` contract (`StreamResponse` payload).
8. **History persistence (3.14):** whether the server stores user follow-up messages into task history.
9. **Agent Card signatures (2.6):** implement now or defer. They are optional.
10. **TCK SUT push support (4.2).**

---

## 5. Known upstream discrepancies to keep in mind

- **ErrorInfo strength:** JSON-RPC ErrorInfo is SHOULD in spec v1.0.1, MUST in v1.0.0, and the TCK enforces MUST.
  **Implement it.**
- **Blocking wording:** spec §3.1.1 says SendMessage "MUST return immediately", but §3.2.2 and the proto make blocking
  the default. Follow §3.2.2 and the proto.
- **When a stream closes:** §3.1.2 closes on a terminal state; §11.7 (HTTP+JSON) closes on terminal or interrupted
  states.
- **Version comparison:** Python compares the major version only, and the spec says Major.Minor. `a2a-core` now follows the spec.
- **`ExtensionSupportRequiredError`** is a spec MUST that Python never raises.
- **TCK CORE-SEND-003** checks for success rather than the error its title names.
- **Stale spec text:** the v1.0.1 push-notification sections still name the removed `PushNotificationConfig` and
  `CreateTaskPushNotificationConfigRequest` types. They are fixed on `main`; the proto is authoritative.
- **Sample card:** the sample card in §8.5 still uses the v0.3 `security` shape. §1.4 makes the proto authoritative.
