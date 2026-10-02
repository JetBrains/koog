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

Legend: ✅ done · 🟡 partial / buggy · ❌ missing / not migrated · ❓ needs your decision

---

## 0. TL;DR

- **The vendored `a2a.proto` is current.** It is byte-identical to `v1.0.1`; the git blob `400cdba` matches. Upstream
  `main` changes only one comment, on `AgentInterface.url`, to allow `host:port` URLs for gRPC. No update is needed.
- **Upstream `docs/whats-new-v1.md` has known errors** (§1), some of them describing fields that don't exist in the
  proto. Don't use it as a reference. This plan is the source of truth for migration status.
- **`a2a-core`** is about 85% migrated. About 10 wire-format bugs remain. The biggest are `security` instead of
  `securityRequirements`, API key `in` instead of `location`, `HTTP+JSON/REST` instead of `HTTP+JSON`, and
  `DataPart.data` limited to an object. Some tests assert the wrong shapes.
- **Transports and `a2a-client`** have all 11 methods wired with v1 names and shapes. Missing:
    - server-side `A2A-Version` validation;
    - `ErrorInfo` in error data;
    - plain-JSON errors before an SSE stream starts;
    - tolerant client parsing of `error.data`;
    - interface selection and tenant propagation;
    - `A2A-Extensions` support.
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

| Area                              | Actual     | Notes                                                                                                                                                                                                                                                                                                                   |
|-----------------------------------|------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| TaskStatus                        | 🟡         | The state names are correct. `TASK_STATE_UNKNOWN` exists but is not in the proto, and `TASK_STATE_UNSPECIFIED` is missing. `timestamp` defaults to `Clock.System.now()` when decoding, so a missing value gets invented.                                                                                                |
| Message                           | 🟡         | The fields are correct. `ROLE_UNSPECIFIED` is missing.                                                                                                                                                                                                                                                                  |
| Part                              | 🟡         | Member-based oneof ✅. **`DataPart.data` is `JsonObject` but must be `google.protobuf.Value`** (any JSON). Base64 decoding is strict, so URL-safe and unpadded input are rejected. Unknown sibling keys break decoding.                                                                                                 |
| Artifact                          | ✅         |                                                                                                                                                                                                                                                                                                                         |
| AgentCard                         | 🟡         | `supportedInterfaces`, `protocolVersion` and `tenant` ✅. **`security` with the v0.3 shape should be `securityRequirements` + `StringList`** (also on AgentSkill). KDoc still mentions `url` and `preferredTransport`.                                                                                                  |
| AgentCapabilities                 | ✅         | `extendedAgentCard` and the presence of the optional bools are kept.                                                                                                                                                                                                                                                    |
| PushNotificationConfig            | 🟡         | The flattened `TaskPushNotificationConfig` ✅. **`taskId` is required but must be optional**, because inline configs in SendMessage have none and the TCK sends them that way. `ListTaskPushNotificationConfigsResponse.configs` has no default, so the `{}` the Python server sends for an empty list fails to decode. |
| Stream Event Objects              | ✅         | `statusUpdate` / `artifactUpdate`, no `final`.                                                                                                                                                                                                                                                                          |
| OAuth 2.0 Security                | 🟡         | The flows ✅, and implicit and password are kept as deprecated. **`APIKeySecurityScheme` uses `in` but should use `location`.** `TransportProtocol.HTTP_JSON_REST = "HTTP+JSON/REST"` should be `"HTTP+JSON"`. The deprecated flow fields are over-required.                                                            |
| Requests (all 11 ops + `tenant`)  | ✅         | `GetTask` has no `metadata`.                                                                                                                                                                                                                                                                                            |
| ListTasks request/response        | ✅ (model) | Server behaviour ❌ (see §3 Phase 3).                                                                                                                                                                                                                                                                                   |
| Error taxonomy                    | 🟡         | The codes -32001…-32007 and -32009 exist. **-32008 `ExtensionSupportRequired` is missing.** No mapping from an exception to an ErrorInfo `reason`. -32007 is still named "AuthenticatedExtendedCard".                                                                                                                   |
| Versioning constants / validation | 🟡         | `A2A-Version`, `A2A-Extensions` and `"1.0"` ✅. `isVersionCompatible` compares the major version only (the spec requires Major.Minor, with empty meaning 0.3), and nothing uses it.                                                                                                                                     |

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
| Error model (JSON-RPC)             | 🟡. `data` is an array ✅. The server always sends `data: []` with no ErrorInfo. The client throws `SerializationException` when `data` is missing or a string. The Python server sends both.                  |
| A2A-Version header                 | The client sends `1.0` ✅. The **server never validates it** ❌.                                                                                                                                               |
| A2A-Extensions header              | ❌. Only a constant exists.                                                                                                                                                                                    |
| Agent Card discovery               | `/.well-known/agent-card.json` ✅. Interface selection by `protocolBinding` + `protocolVersion` ❌.                                                                                                            |
| Agent Card signatures (JWS/JCS)    | ❌. Not implemented. It is optional, a MAY for the client.                                                                                                                                                     |

---

## 3. Work plan

Phases are ordered by dependency. Each item lists module, status, and TCK and requirement references. R-numbers refer to
`koog-server-and-tests-status.md`.

### Phase 1: Finish `a2a-core` (unblocks everything)

| #    | Item                                                                                                                                                                                                                     | Status | Notes                                                                    |
|------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------|--------------------------------------------------------------------------|
| 1.1  | `AgentCard.security` and `AgentSkill.security` → `securityRequirements: List<SecurityRequirement>`, add `StringList`                                                                                                     | ❌ ❓  | Public API change                                                        |
| 1.2  | `APIKeySecurityScheme` `in` → `location`                                                                                                                                                                                 | ❌     | Valid v1 cards currently fail to decode                                  |
| 1.3  | `TransportProtocol.HTTP_JSON_REST` value → `"HTTP+JSON"` (consider renaming the constant)                                                                                                                                | ❌     |                                                                          |
| 1.4  | `DataPart.data`: `JsonObject` → `JsonElement` (`google.protobuf.Value`)                                                                                                                                                  | ❌     |                                                                          |
| 1.5  | `TaskState`: remove `TASK_STATE_UNKNOWN`, add `TASK_STATE_UNSPECIFIED`; `Role`: add `ROLE_UNSPECIFIED`                                                                                                                   | ❌ ❓  | Decide how to handle UNSPECIFIED: reject it, or map unknown values to it |
| 1.6  | `TaskPushNotificationConfig.taskId` becomes optional                                                                                                                                                                     | ❌ ❓  | Needed for inline configs (R19) and the TCK PUSH-DELIVER-001..003 tests  |
| 1.7  | `ListTaskPushNotificationConfigsResponse.configs` defaults to `emptyList()`; review other `repeated` fields for missing defaults                                                                                         | ❌     | Interop with the Python server                                           |
| 1.8  | `TaskStatus.timestamp`: no `now()` default on decode (make it nullable, or set it only on construction)                                                                                                                  | ❌     |                                                                          |
| 1.9  | Loosen over-required fields: `Task.contextId`, `AgentExtension.uri`, the deprecated flow fields                                                                                                                          | ❌     |                                                                          |
| 1.10 | Add the -32008 `ExtensionSupportRequired` code and exception; rename -32007 to `ExtendedAgentCardNotConfigured`                                                                                                          | ❌     |                                                                          |
| 1.11 | Map each A2A exception to an ErrorInfo `reason` (`TASK_NOT_FOUND`, … `VERSION_NOT_SUPPORTED`) with domain `a2a-protocol.org`, so it is available to the transport layer                                                  | ❌     | Check the reason strings against the Python SDK                          |
| 1.12 | Version utilities: Major.Minor comparison, empty → `0.3`; decide supported versions                                                                                                                                      | 🟡 ❓  |                                                                          |
| 1.13 | ProtoJSON tolerance: unknown sibling keys in oneof wrappers, URL-safe and unpadded base64, optionally integer enums and snake_case                                                                                       | ❌     | SHOULD-level; lower priority                                             |
| 1.14 | Clean up v0.3 leftovers: KDoc links (`A2APaths.kt:10`, `ClientTransport.kt:126`, `ServerTransport.kt:137`), AgentCard KDoc                                                                                               | ❌     |                                                                          |
| 1.15 | Tests: fix `AgentCardSerializationTest` and `SerializersTest` (they assert `security`, `in`, `HTTP+JSON/REST`); add wire tests for Task, Message, all Part variants, StreamResponse, requests, enums, base64, timestamps | 🟡     |                                                                          |

### Phase 2: Transports and client

| #    | Item                                                                                                                                                                                                                 | Status | Notes                                                                                  |
|------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------|----------------------------------------------------------------------------------------|
| 2.1  | Server: validate `A2A-Version` (empty → 0.3; unsupported → -32009)                                                                                                                                                   | ❌ ❓  | TCK VER-SERVER-002 (MUST). Decide whether it lives in the transport or in `A2AServer`. |
| 2.2  | Server: emit ErrorInfo in `error.data`                                                                                                                                                                               | ❌     | TCK JSONRPC-ERR-003 (the TCK treats it as MUST)                                        |
| 2.3  | Server: errors raised before the first stream event go out as a plain JSON-RPC response (`application/json`), not over SSE                                                                                           | ❌ ❓  | TCK STREAM-SUB-004 (MUST). Interacts with how `A2AServer` streaming flows fail.        |
| 2.4  | Server: accept a missing `params` for no-arg methods such as `GetExtendedAgentCard`                                                                                                                                  | ❌     | `JSONRPCServerTransport.kt:212-221`, `:357`                                            |
| 2.5  | Serialize `"id": null` in error responses, as JSON-RPC 2.0 requires; fix `JsonRpcSerializationTest`                                                                                                                  | ❌     |                                                                                        |
| 2.6  | Client: tolerant `error.data` parsing (missing, string or object) → still an `A2AException`                                                                                                                          | ❌     | The Python server sends these shapes                                                   |
| 2.7  | Client: handle an `application/json` error response to a streaming request, instead of failing with `SSEClientException`                                                                                             | ❌     | Also fixes the subscribe-after-terminal race                                           |
| 2.8  | Client: check the streaming capability in `subscribeToTask`; throw A2A errors instead of `IllegalStateException`                                                                                                     | 🟡     |                                                                                        |
| 2.9  | Client: select an interface from `supportedInterfaces` by `protocolBinding` + `protocolVersion`, and propagate `tenant` automatically                                                                                | ❌ ❓  | API design (a ClientFactory-like API?)                                                 |
| 2.10 | `A2A-Extensions` header: client sends it; server parses it and exposes it in the call context; ExtensionSupportRequired check (R11)                                                                                  | ❌ ❓  |                                                                                        |
| 2.11 | Agent Card signature verification (JWS + JCS)                                                                                                                                                                        | ❌     | Optional, MAY. Defer?                                                                  |
| 2.12 | Rename leftover constants (`GetAuthenticatedExtendedAgentCard`, `ListTaskPushNotificationConfig`); fix KDoc (`A2AClient.kt:172`, `Routes.kt` example); fix test fixtures (`security`, patch versions like `"1.0.1"`) | ❌     |                                                                                        |
| 2.13 | SSE client tests: replace the `@Ignore`d MockEngine tests with Ktor `testApplication`                                                                                                                                | ❌     |                                                                                        |
| 2.14 | Optional: `Cache-Control`/`ETag` on the agent-card route                                                                                                                                                             | ❌     | TCK CARD-CACHE-001/002 (SHOULD, reported as xfail)                                     |

### Phase 3: `a2a-server` migration (largest phase)

**3a. Compile against the v1 core API.** These are mechanical changes:

- the `RequestHandler` signatures (no `Request`/`Response` wrappers; v1 request types);
- `onSubscribeToTask`, `onCreateTaskPushNotificationConfig` and `onGetExtendedAgentCard`;
- `capabilities.extendedAgentCard`;
- remove `final` from `SessionEventProcessor`;
- push layer on `TaskPushNotificationConfig` and `AuthenticationInfo.scheme`.

**3b. v1 behaviour.** Requirement numbers R1–R24 are from `koog-server-and-tests-status.md`.

| #    | Item                                                                                                                                                                                                                                            | Req      | TCK                                        | Status                         |
|------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------|--------------------------------------------|--------------------------------|
| 3.1  | Blocking by default; `returnImmediately`; return on terminal **or interrupted** states with full artifacts                                                                                                                                      | R1, R23  | CORE-SEND-*                                | ❌                             |
| 3.2  | `Message.taskId` must exist (else TaskNotFound); infer `contextId` from the task; reject a mismatched `contextId`                                                                                                                               | R2, R4   | CORE-MULTI-006                             | ❌                             |
| 3.3  | Send to a terminal task → UnsupportedOperation                                                                                                                                                                                                  | R3       | CORE-SEND-002                              | ❌                             |
| 3.4  | Stream rules: the first event is a `Task`; a message-only stream has one Message and then closes; close on terminal state                                                                                                                       | R5       | STREAM-*                                   | ❌                             |
| 3.5  | SubscribeToTask: Task snapshot first; terminal → -32004; unknown → TaskNotFound; works for input-required tasks without a live session; events broadcast to all subscribers in order                                                            | R6, R7   | STREAM-SUB-001..003, STREAM-ORDER-002..004 | ❌ ❓ (session redesign)       |
| 3.6  | Capability gating: streaming off → -32004; push off → -32003; extended card off → -32004, declared but missing → -32007                                                                                                                         | R8–R10   | CORE-CAP-003                               | ❌                             |
| 3.7  | `A2A-Version` and required-extension validation (if this lives in the server rather than the transport)                                                                                                                                         | R11, R12 | VER-SERVER-002                             | ❌                             |
| 3.8  | GetTask includes artifacts; `historyLength` semantics (unset, 0, N)                                                                                                                                                                             | R13, R14 | CORE-GET-001, CORE-HIST-*                  | ❌                             |
| 3.9  | ListTasks: filters (contextId, status, statusTimestampAfter), sorted by status timestamp descending, page tokens, default 50 / range 1..100, omit artifacts unless `includeArtifacts`, `nextPageToken` always present, `historyLength` per task | R15      | CORE-LIST-001..005                         | ❌ ❓ (`TaskStorage` list API) |
| 3.10 | CancelTask: terminal → TaskNotCancelable; unknown → TaskNotFound                                                                                                                                                                                | R16      | CORE-CANCEL-001/002                        | ❌                             |
| 3.11 | Push config CRUD: flattened storage; server-assigned id; missing config → TaskNotFound; Delete is idempotent                                                                                                                                    | R17, R18 | PUSH-*                                     | ❌ ❓ (storage flattening)     |
| 3.12 | Honour the inline `SendMessageConfiguration.taskPushNotificationConfig`                                                                                                                                                                         | R19      | PUSH-DELIVER-001..003                      | ❌                             |
| 3.13 | Push delivery: `StreamResponse` payload for each event; `Authorization: <scheme> <credentials>`; at least one attempt; 10–30 s timeout                                                                                                          | R20      | PUSH-DELIVER-*                             | ❌ ❓ (sender contract)        |
| 3.14 | Decide whether the server persists user follow-up messages into task history                                                                                                                                                                    | —        | CORE-HIST-*                                | ❓                             |
| 3.15 | Server tests: rewrite them (none compile); re-enable the `@Disabled` JSON-RPC integration test class                                                                                                                                            | —        | —                                          | ❌                             |

### Phase 4: TCK SUT and CI

| #   | Item                                                                                                                                                                                                              | Status |
|-----|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------|
| 4.1 | Rewrite `test-tck/a2a-test-server-tck` with the v1 agent card (`protocolVersion` currently says `0.3.0`) and the v1 TCK messageId-prefix scenarios (`tck-complete-task`, `tck-input-required`, `tck-stream-*`, …) | ❌     |
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

1. **Public model API breaks:**
    - 1.1 `securityRequirements`;
    - 1.5 the UNSPECIFIED enum values;
    - 1.6 optional `TaskPushNotificationConfig.taskId`;
    - 1.8 `TaskStatus.timestamp` nullability.
2. **Version policy (1.12, 2.1):**
    - accept exactly `1.0`, or any `1.x` like Python does;
    - reject a missing header (meaning 0.3) with -32009, or accept it;
    - where the check lives: the transport or `A2AServer`.
3. **Pre-stream errors (2.3):** how `A2AServer` streaming handlers signal failure before the first event, so the
   transport can answer with plain JSON.
4. **Client interface selection and tenant (2.9):** whether to add a factory-style API that picks the interface and
   injects the tenant.
5. **Extensions (2.10):** the scope of `A2A-Extensions` support and the ExtensionSupportRequired check now.
6. **Session redesign (3.5):** a per-task event stream that outlives a single execution, which SubscribeToTask on
   input-required tasks needs.
7. **Storage interfaces (3.9, 3.11, 3.13):**
    - the `TaskStorage` list/query API with pagination;
    - flattening `PushNotificationConfigStorage`;
    - the `PushNotificationSender` contract (`StreamResponse` payload).
8. **History persistence (3.14):** whether the server stores user follow-up messages into task history.
9. **Agent Card signatures (2.11):** implement now or defer. They are optional.
10. **TCK SUT push support (4.2).**

---

## 5. Known upstream discrepancies to keep in mind

- **ErrorInfo strength:** JSON-RPC ErrorInfo is SHOULD in spec v1.0.1, MUST in v1.0.0, and the TCK enforces MUST.
  **Implement it.**
- **Blocking wording:** spec §3.1.1 says SendMessage "MUST return immediately", but §3.2.2 and the proto make blocking
  the default. Follow §3.2.2 and the proto.
- **When a stream closes:** §3.1.2 closes on a terminal state; §11.7 (HTTP+JSON) closes on terminal or interrupted
  states.
- **Version comparison:** Python and Koog compare the major version only; the spec says Major.Minor.
- **`ExtensionSupportRequiredError`** is a spec MUST that Python never raises.
- **TCK CORE-SEND-003** checks for success rather than the error its title names.
- **Stale spec text:** the v1.0.1 push-notification sections still name the removed `PushNotificationConfig` and
  `CreateTaskPushNotificationConfigRequest` types. They are fixed on `main`; the proto is authoritative.
- **Sample card:** the sample card in §8.5 still uses the v0.3 `security` shape. §1.4 makes the proto authoritative.
