# What does the official A2A v0.3 → v1.x migration guide say, and how accurate is `a2a/a2a_1.0.0_changelog.md` against it and the latest v1.* spec?

**Sources checked:**
- `a2aproject/A2A` (local clone `/Users/eugene/Documents/JetBrains/projects/a2a-protocol`), refreshed 2026-10-02 (`git pull --ff-only` + `git fetch --tags`, both clean).
  - **Baseline: tag `v1.0.1`** (commit `3303592588e388e62e0f69f701af531d2f4e3991`, 2026-05-28): `docs/specification.md`, `specification/a2a.proto`, `docs/whats-new-v1.md`, `CHANGELOG.md`, `adrs/adr-001-protojson-serialization.md`, `docs/topics/multi-tenancy.md`, `docs/topics/extensions.md`, `docs/announcing-1.0.md`.
  - `main` @ `65dadbd9b900fe2d970e05416eb432a91347cd2a` (`v1.0.1-70-g65dadbd`) for **unreleased** corrections.
  - `v1.0.0` for the 1.0.0 → 1.0.1 delta; `v0.3.0` (`docs/specification.md`, `specification/json/a2a.json`, `specification/grpc/a2a.proto`) for "what v0.3 actually looked like".
- Python SDK and TCK: **not examined** (not in scope of this question).
- Koog Kotlin code: **not examined** (per instructions).

**Confidence:** high — the user's changelog is a byte-for-byte copy of an upstream doc, so every claim can be checked line-by-line against the proto and spec text at the same tag; the few medium-confidence points (ProtoJSON rules, exact ErrorInfo reason strings) are labeled.

## Answer

1. **The official migration guide is `docs/whats-new-v1.md`.** It is explanatory, not normative. The spec says the proto and the spec text win when they disagree (spec §1.4 L105-107; skill guidance). Supporting material: `CHANGELOG.md` (release notes), spec Appendix A "Migration & Legacy Compatibility" (§A, L3355-3596), ADR-001 (ProtoJSON), and `docs/topics/multi-tenancy.md` / `extensions.md`.
2. **`a2a/a2a_1.0.0_changelog.md` is a verbatim copy of `docs/whats-new-v1.md` as released at `v1.0.0`/`v1.0.1`**, with only `[DONE]` added to 9 headings. `diff` against `v1.0.1:docs/whats-new-v1.md` shows only those heading changes, and `v1.0.0` and `v1.0.1` copies of the guide are identical. Its line numbers therefore match upstream line numbers exactly.
3. **That released guide has known errors.** Upstream fixed some of them on `main` *after* v1.0.1 (unreleased): #2056 `08b42eb` "align v1 migration guide with proto" and #2165 `aa042ec` "fix pagination field names". The commit message of `08b42eb` says the guide "described stream wrappers and timestamp fields that do not exist in the normative `a2a.proto`". Every suspicious item in the task is confirmed wrong against the v1.0.1 proto: `Task.createdAt/lastModified`, push config `createdAt`/`configId`, `taskStatusUpdate`/`taskArtifactUpdate`, `index`, cursor/limit/nextCursor, `config_id`. Several more errors are **still present on `main`**: `index`, "removed" implicit/password flows, `extensions[]` described as new, OAuth JSON keys, device-code fields, and v0.3 `fileWithUri` examples.
4. **The guide leaves out many things that matter for a JSON-RPC server and client.** These include the `SendMessageResponse`/`StreamResponse` result wrappers, the `blocking`→`returnImmediately` polarity flip, the exact params shapes of every method, the error `data` array, the A2A-Version default of `0.3`, the SecurityScheme/securityRequirements reshaping, `AuthenticationInfo.schemes[]`→`scheme`, push payload = `StreamResponse`, ListTasks defaults and limits, `TASK_STATE_UNSPECIFIED`, `stateTransitionHistory` removal, and the ProtoJSON omission rules. All of these are listed in "Missing from the user's changelog" below.

## Requirements

Normative behavior a JSON-RPC-over-HTTP server/client must implement that the changelog omits or gets wrong. All are relevant to Koog unless marked otherwise. Spec = `docs/specification.md@v1.0.1`, proto = `specification/a2a.proto@v1.0.1`.

| # | Requirement | Tier | Bindings | Citation |
|---|---|---|---|---|
| R1 | JSON-RPC method names are exactly `SendMessage`, `SendStreamingMessage`, `GetTask`, `ListTasks`, `CancelTask`, `SubscribeToTask`, `CreateTaskPushNotificationConfig`, `GetTaskPushNotificationConfig`, `ListTaskPushNotificationConfigs`, `DeleteTaskPushNotificationConfig`, `GetExtendedAgentCard` | MUST | JSON-RPC | spec §5.3 L1162-1174; §9.1 L2237; §9.4 L2281-2429 |
| R2 | JSON-RPC `Content-Type: application/json` for requests and responses (`application/a2a+json` is the HTTP+JSON SHOULD only); streaming uses `text/event-stream`, each SSE `data:` holds a full JSON-RPC response whose `result` is a `StreamResponse` | MUST (as stated in protocol requirements) | JSON-RPC | spec §9.1 L2235-2238; §9.4.2 L2322-2328; §11.1 L2750 |
| R3 | `SendMessage` result is a `SendMessageResponse` wrapper: `{"task":{…}}` or `{"message":{…}}` (no `kind`) | MUST | all | proto L779-787; spec §9.4.1 L2302-2312 |
| R4 | Stream items are a `StreamResponse` wrapper with exactly one of `task`, `message`, `statusUpdate`, `artifactUpdate` | MUST | all | proto L790-802; spec §3.2.3, §4.3.3 L853-869 |
| R5 | Streaming send: either exactly one `Message` and then close, or `Task` first, then status/artifact events; close at a terminal state | MUST; capability:streaming | all | spec §3.1.2 L206-210 |
| R6 | `SubscribeToTask`: first event MUST be a `Task` snapshot; stream terminates at a terminal state; on a task already terminal → `UnsupportedOperationError` | MUST; capability:streaming | all | spec §3.1.6 L303-311; §9.4.6 L2408; proto L74-75 |
| R7 | Events delivered in generation order; with multiple streams per task, broadcast the same events in the same order to all; closing one stream doesn't affect others | MUST (multi-stream itself MAY) | all | spec §3.5.2 L683-694 |
| R8 | `returnImmediately` (bool, default false = blocking): blocking MUST wait for a terminal **or interrupted** (`INPUT_REQUIRED`/`AUTH_REQUIRED`) state and return the latest task. Non-blocking MUST return right after task creation. No effect for streaming, for direct-Message replies, or on push configs | MUST | all | proto L155-160; spec §3.2.2 L442-454 |
| R9 | `historyLength` (`optional int32`): unset = no client limit; `0` = no history (`history` SHOULD be omitted); `>0` = at most N most recent. Server MUST NOT return more than N, MAY return fewer. Applies to SendMessage config, GetTask, ListTasks | MUST / SHOULD | all | proto L150-154, L667-671, L693; spec §3.2.4 L465-471 |
| R10 | ListTasks: `pageSize` default 50, min 1, max 100; cursor pagination via `pageToken`/`nextPageToken`; sorted by status timestamp descending; only caller-visible tasks; `includeArtifacts` default false, and in that case `artifacts` MUST be omitted (not `[]`/null); `nextPageToken` MUST always be present (`""` on last page); response also has REQUIRED `pageSize`, `totalSize` | MUST | all | proto L675-712; spec §3.1.4 L240-262 |
| R11 | Error `data` in JSON-RPC errors is an **array** of objects, each with a `@type` (ProtoJSON `Any`). A2A errors SHOULD carry `google.rpc.ErrorInfo` with `reason` (UPPER_SNAKE_CASE of the error name without the `Error` suffix) and `domain: "a2a-protocol.org"` | MUST (`@type` on each entry); **SHOULD** (ErrorInfo) for JSON-RPC at v1.0.1. It was MUST at v1.0.0 | JSON-RPC | spec §9.5 L2435-2437, L2453-2455, example L2481-2503; v1.0.0→v1.0.1 diff (see Discrepancies) |
| R12 | A2A error codes: TaskNotFound −32001, TaskNotCancelable −32002, PushNotificationNotSupported −32003, UnsupportedOperation −32004, ContentTypeNotSupported −32005, InvalidAgentResponse −32006, ExtendedAgentCardNotConfigured −32007, ExtensionSupportRequired −32008, VersionNotSupported −32009 | MUST | JSON-RPC | spec §5.4 L1180-1190; §9.5 L2449-2451 |
| R13 | Client MUST send `A2A-Version` (as `Major.Minor`) on every request. Server MUST treat an absent/empty value as `0.3`, MUST process with the semantics of the requested version, and MUST return `VersionNotSupportedError` if that interface doesn't support it. Patch versions SHOULD NOT be sent and MUST NOT be considered | MUST | all (HTTP header for JSON-RPC) | spec §3.6 L708, §3.6.1 L712, §3.6.2 L737-739; §3.2.6 L486; §14.2.1 L3297 |
| R14 | Service params go in HTTP headers for JSON-RPC (`A2A-Version`, `A2A-Extensions`, case-insensitive; multiple extension URIs comma-separated in one header) | MUST / SHOULD (comma-joining) | JSON-RPC | spec §9.2 L2242-2248 |
| R15 | Agent MUST return `ExtensionSupportRequiredError` when an extension marked `required: true` isn't declared by the client. Unsupported non-required extensions SHOULD be ignored. Response SHOULD echo activated extensions in an `A2A-Extensions` response header (topic doc, explanatory) | MUST / SHOULD | all | spec §3.3.4 L576; §4.6.3 L1141; `docs/topics/extensions.md` L163-170 |
| R16 | Client MUST put the selected `AgentInterface.tenant` in the `tenant` field of **every** request message, and omit it if unset. For JSON-RPC this is the `tenant` member inside `params` | MUST | all | spec §8.3.2 L2000; proto L344-350 and every `*Request.tenant`; `docs/topics/multi-tenancy.md` L98-103 |
| R17 | Capability validation: push ops without `capabilities.pushNotifications` → `PushNotificationNotSupportedError`; streaming ops without `capabilities.streaming` → `UnsupportedOperationError`; GetExtendedAgentCard without `capabilities.extendedAgentCard` → `UnsupportedOperationError`, declared but not configured → `ExtendedAgentCardNotConfiguredError` | MUST; capability:* | all | spec §3.3.4 L571-576; §13.3 L3164-3165 |
| R18 | SendMessage/SendStreamingMessage to a terminal task → `UnsupportedOperationError`; unknown `taskId` → `TaskNotFoundError`; mismatched `contextId`+`taskId` MUST be rejected (error type not specified); `contextId` inferred from `taskId` | MUST | all | spec §3.1.1 L175-176; §3.1.2 L200-202; §3.4.2 L613-615; §3.4.3 L627-628 |
| R19 | CancelTask on a non-cancelable task → `TaskNotCancelableError`; cancel is idempotent; MAY return TaskNotFound if purged | MUST / MAY | all | spec §3.1.5 L278; §3.3.1 L496 |
| R20 | Push config CRUD shapes: Create params = flattened `TaskPushNotificationConfig` `{tenant,id,taskId,url,token,authentication}` → returns the same with assigned `id`; Get/Delete params `{tenant,taskId,id}`; List params `{tenant,taskId,pageSize,pageToken}` → `{configs,nextPageToken}`; Delete → `google.protobuf.Empty` (`{}`) and MUST be idempotent; config not found → `TaskNotFoundError` | MUST; capability:pushNotifications | all | proto L89-139, L469-484, L726-769, L806-811; spec §3.1.7-3.1.10 L313-402 |
| R21 | Push webhook payload is a `StreamResponse` (one of task/message/statusUpdate/artifactUpdate) over plain HTTP, with `Authorization: {scheme} {credentials}` from `AuthenticationInfo`; at-least-once delivery | MUST | webhook (binding-independent) | spec §4.3.3 L844-887; §3.5.1 L677 |
| R22 | JSON field names camelCase; enums as proto names (`TASK_STATE_*`, `ROLE_*`), per ProtoJSON (ADR-001) | MUST | JSON-RPC, HTTP+JSON | spec §5.5 L1204-1222; ADR-001 L32-34 |
| R23 | Timestamps: ISO 8601 UTC with `Z`, MUST NOT have other offsets; millisecond precision SHOULD; fractional part MAY be omitted | MUST / SHOULD / MAY | JSON bindings | spec §5.6.1 L1230-1255 |
| R24 | REQUIRED fields MUST be present; required arrays MUST have ≥1 element; implementations SHOULD reject missing required fields; SHOULD ignore unrecognized fields | MUST / SHOULD | all | spec §5.7 L1263, L1275 |
| R25 | Agent card at `https://{server_domain}/.well-known/agent-card.json` (path unchanged from v0.3) | MUST (server makes card available) | discovery | spec §8.1 L1970, §8.2 L1978; §14.3 L3328-3335 |
| R26 | ListTasks / GetTask / Cancel / Subscribe / push ops MUST be scoped to the caller's authorization | MUST | all | spec §13.1 L3075-3098 |

## Details

### A. What the official migration material consists of

| Doc | Status | Notes |
|---|---|---|
| `docs/whats-new-v1.md` | explanatory | The "official" migration guide; the user's changelog is a copy of it. Fixed on `main` (unreleased) by `08b42eb` (#2056), `aa042ec` (#2165), `1be9b56` (#1910). |
| `CHANGELOG.md` (`## [1.0.0]`, `## [1.0.1]`) | release notes | Lists breaking changes by PR: #1500, #1487, #1486, #1474, #1389, #1283, #1384, #1308, #1307, #1358, #1303, #1302, #1301, #1222. |
| spec Appendix A (§A L3355-3596) | normative text but partly stale | Has the legacy-name table and "kind discriminator removed" / "extendedAgentCard relocated" sections. Contains its own errors (see Discrepancies). |
| `adrs/adr-001-protojson-serialization.md` | rationale | Adopts ProtoJSON as normative for JSON serialization (L32-34). |
| `docs/topics/multi-tenancy.md`, `extensions.md` | explanatory | tenant echo rule; `A2A-Extensions` request/response header flow. |

### B. Section-by-section audit of `a2a/a2a_1.0.0_changelog.md`

Line numbers = changelog line = `whats-new-v1.md@v1.0.1` line. Verdicts: **correct**, **inaccurate** (with correction), **nuance** (missing nuance), **not in spec**.

#### Overview of major themes (L5-42)

| L | Claim | Verdict | Correct statement / evidence |
|---|---|---|---|
| 11-15 | proto normative; RFC 8785/7515; google.rpc.Status; versioning; error taxonomy | correct | spec §1.4 L107; §8.4; §3.3.2; §5.4 |
| 19-23 | `kind` removed; enums SCREAMING_SNAKE; camelCase; ISO 8601 ms | correct, with nuance | Millisecond precision is only **SHOULD**, and the fractional part MAY be omitted (spec §5.6.1 L1236, L1254). |
| 30 | A2A-Version / A2A-Extensions headers | correct | v0.3 used `X-A2A-Extensions` (`v0.3.0:docs/topics/extensions.md` L110, L120). v1 uses `A2A-Extensions` (spec §3.2.6 L485). The rename isn't called out. |
| 31 | "Removed complex compound IDs (e.g. `tasks/{id}`) in favor of simple UUIDs" | nuance | IDs are opaque strings ("e.g. UUID"), not required to be UUIDs (proto L168-169, L474-475). |
| 33 | "Native tenant scoping in gRPC requests" | nuance | `tenant` is in **every** request message for every binding. In JSON-RPC it is a `params` member (proto L651, L664, L678, L718, L729, L740, L751, L760, L775, L472; spec §8.3.2 L2000). |
| 40 | "removed deprecated implicit/password flows" | **inaccurate** | The proto **keeps** them as `ImplicitOAuthFlow implicit = 3 [deprecated = true]` and `PasswordOAuthFlow password = 4 [deprecated = true]` in `OAuthFlows.flow` (proto L573-576, messages L607-631). The spec text has no table sections for them (§4.5.7-4.5.10 L976-996). Treat them as **deprecated, still on the wire**; parsers should accept them. CHANGELOG #1303 also says "remove", so the proto contradicts it. |
| 41 | `pkce_required` | correct | proto L592-594 (`pkceRequired` in JSON). |
| 42 | cursor-based pagination | correct | spec §3.1.4 L254-258. |

#### Behavioral changes for core operations (L46-205)

| L | Claim | Verdict | Correct statement / evidence |
|---|---|---|---|
| 48-58 | `message/send` → `SendMessage`; clarified Task vs Message | correct, with nuance | Missing: the JSON-RPC `result` is now a wrapper `{"task":…}` / `{"message":…}` (proto L779-787; spec §9.4.1 L2302-2312). In v0.3 it was a bare Task/Message with `kind` (`v0.3.0:specification/json/a2a.json` `SendMessageSuccessResponse`). Also missing: a new error for sending to a terminal task, `UnsupportedOperationError` (spec §3.1.1 L175). |
| 60-73 | `message/stream` → `SendStreamingMessage`; no `kind`; `final` removed; concurrent streams | correct, with nuance | `final` is removed: proto L296-305 has no such field, CHANGELOG #1308. "Multiple concurrent streams allowed" is a **MAY** for the agent, but once multiple streams exist, broadcasting the same ordered events is a **MUST** (spec §3.5.2 L687-694). Missing: stream items are `StreamResponse` wrappers, and the stream-pattern MUSTs (R5). |
| 85 | `createdAt` and `lastModified` added to Task | **inaccurate** | `Task` has only `id, contextId, status, artifacts, history, metadata` (proto L167-184). Removed from the guide on `main` by `08b42eb`. |
| 87 | "historyLength clarified" | correct | spec §3.2.4 L465-471 (details in R9). |
| 88 | "Task object now includes `extensions[]` array in messages and artifacts" | **inaccurate** (misleading) | `Task` has no `extensions` field. `Message.extensions` and `Artifact.extensions` **already existed in v0.3** (`v0.3.0:specification/json/a2a.json` L491 Artifact, L1613 Message). Nothing changed here. |
| 89 | servers MUST only return tasks visible to caller | correct | spec §13.1 L3092. |
| 95 | ListTasks unavailable in v0.3 | correct, with nuance | Unavailable in **JSON-RPC**. v0.3 had gRPC `ListTask`/REST `GET /v1/tasks` without parameters or pagination (`v0.3.0:docs/specification.md` L218, L846-870). |
| 99-100 | new ListTasks with filtering; visibility scoped | correct, with nuance | Missing the normative defaults and limits (R10). |
| 111-113 | `tasks/cancel` → `CancelTask` | correct | Errors: `TaskNotCancelableError` (−32002), `TaskNotFoundError` (spec §3.1.5 L278-279). |
| 119 | v0.3 discovery via `/.well-known/agent-card.json` | correct | Unchanged in v1 (spec §8.2 L1978; §14.3). |
| 125-130 | rename to `GetExtendedAgentCard`; `capabilities.extendedAgentCard`; `protocolVersion` → AgentInterface; `supportedInterfaces[]` with `url, protocolBinding, protocolVersion` | correct, with nuance | AgentInterface also has `tenant` (proto L336-355). v0.3 `AgentInterface.transport` was renamed to `protocolBinding` (`v0.3.0` json L345). Missing: new error `ExtendedAgentCardNotConfiguredError` (−32007), and `UnsupportedOperationError` when the capability is absent (spec §3.1.11 L418-419). |
| 141-144 | `tasks/resubscribe` → `SubscribeToTask`; lifecycle; closure; multiple subscriptions | correct, with nuance | Missing the concrete MUSTs: first event is a `Task` snapshot, and `UnsupportedOperationError` if the task is already terminal (spec §3.1.6 L305, L311). |
| 157 | renamed push ops | correct | proto L89-139. |
| 158 | `createdAt` added to PushNotificationConfig | **inaccurate** | No such field (proto L469-484). Removed from the guide on `main` by `08b42eb`. |
| 159 | push payloads "CLARIFIED" to StreamResponse | nuance | This is a **breaking** wire change, not a clarification. v0.3 webhooks POSTed a Task. v1 POSTs a `StreamResponse` wrapper, `Content-Type: application/a2a+json` (spec §4.3.3 L844-869). |
| 160 | TaskPushNotificationConfig flattened | correct | `{tenant,id,taskId,url,token,authentication}` (proto L469-484), compared with v0.3 `{taskId, pushNotificationConfig:{id,url,token,authentication}}` (`v0.3.0` json `TaskPushNotificationConfig`). CHANGELOG #1500, #1487. |
| 171-174 | `tenant` on all requests and on AgentInterface; "inherited" | correct, with nuance | Precise rule: the client MUST copy `AgentInterface.tenant` into every request, and MUST omit it if unset (spec §8.3.2 L2000). The format is opaque (proto L344-349). |
| 187-189 | IDs simple; example → `task_id` and `config_id` | **inaccurate** (example) | The field is `id`, not `config_id` (proto L726-745; JSON: `{"taskId":…, "id":…}`). Fixed on `main` (`08b42eb`). v0.3 JSON-RPC used `{id: <taskId>, pushNotificationConfigId}` (`v0.3.0` json L731, L980). |
| 196-204 | `/v1` prefix removed from HTTP+JSON | correct (REST only) | proto `google.api.http` paths L22-138. Not relevant to Koog JSON-RPC. |

#### Structural changes (L208-549)

| L | Section / claim | Verdict | Correct statement / evidence |
|---|---|---|---|
| 210-241 | **[DONE] TaskStatus**: TASK_STATE_* enum; timestamp ms; no removed fields | correct, with nuance | Missing: `TASK_STATE_UNSPECIFIED = 0` (proto L189) replaces v0.3 `"unknown"` (`v0.3.0` json L2451). ms is SHOULD. The `"…10:15:00.000Z"` example is fine. |
| 243-273 | **[DONE] Message**: "Added `extensions[]`" | **inaccurate** | It already existed in v0.3 (`v0.3.0` json L1613). |
| 251-253 | role → `ROLE_USER`/`ROLE_AGENT` | correct | proto L245-252 (plus `ROLE_UNSPECIFIED`). |
| 264-268 | v1 example | nuance | Omits REQUIRED `messageId` (proto L262). Missing: Message `kind` removed (v0.3 required it, json L1620/L1662). |
| 275-364 | **[DONE] Part** redesign | mostly correct | Matches proto L224-242 (`text`/`raw`/`url`/`data` oneof, `metadata`, `filename`, `mediaType`). **Inaccurate v0.3 example**: v0.3 JSON was `file: {uri, mimeType, name}` / `file: {bytes, …}` (`v0.3.0` json `FileWithUri` L880, `FileWithBytes`). `fileWithUri` is the v0.3 *gRPC* field name. Missing: v0.3 `file.name` → `filename`. `data` is now any JSON value (`google.protobuf.Value`, proto L232-233), where v0.3 allowed only an object (json `DataPart.data`). |
| 366-374 | **[DONE] Artifact**: "Added `extensions[]`" | **inaccurate** | It existed in v0.3 (`v0.3.0` json L491). `parts` REQUIRED (≥1; spec §5.7 L1263). |
| 376-420 | **[DONE] AgentCard** | correct but incomplete | Missing, all breaking: `security` → `securityRequirements` with shape `[{"schemes":{"name":{"list":[…]}}}]` (proto L383, L488-498; the spec's own sample was fixed on main, `dfe216a`). Same rename for `AgentSkill.security` → `securityRequirements` (proto L451). `SecurityScheme` loses the `type` discriminator and becomes a oneof wrapper (`apiKeySecurityScheme`, `httpAuthSecurityScheme`, `oauth2SecurityScheme`, `openIdConnectSecurityScheme`, `mtlsSecurityScheme`; proto L503-516). `APIKeySecurityScheme.in` → `location` (proto L522-523 vs `v0.3.0` json L87). REQUIRED set: `name, description, supportedInterfaces, version, capabilities, defaultInputModes, defaultOutputModes, skills` (proto L364-393). |
| 422-426 | **[DONE] AgentCapabilities**: `extendedAgentCard` | correct, with nuance | Missing: `stateTransitionHistory` removed (CHANGELOG #1396; `v0.3.0` json L127; proto L411-420). `streaming`/`pushNotifications`/`extendedAgentCard` are `optional bool`; absent means false (spec §3.3.4 L573-575). |
| 428-437 | **[DONE] PushNotificationConfig**: added `configId`, `createdAt`; "enhanced" authentication | **inaccurate** | No `configId` or `createdAt`. The ID field is `id`, and it **already existed in v0.3** (`v0.3.0` json L1906). There is no standalone `PushNotificationConfig` message in v1; it was merged into `TaskPushNotificationConfig` (proto L469-484, CHANGELOG #1500). Authentication changed **breakingly** from `{schemes: string[] (required), credentials}` (`v0.3.0` json L1886-1895) to `AuthenticationInfo {scheme: string (REQUIRED), credentials}` (proto L325-332). Fixed on `main` (`08b42eb`), except the `authentication` wording. |
| 441-472 | **[DONE] TaskStatusUpdateEvent**: v0.3 `kind:"taskStatusUpdate"`; v1 member `taskStatusUpdate`; `final` removed | **inaccurate** (names), correct (`final`) | v0.3 kind was `"status-update"` (`v0.3.0` json L2491). The v1 StreamResponse member is **`statusUpdate`** (proto L798; spec §4.3.3 L857; §A.2.1 L3498). `final` removed: correct (proto L296-305). `contextId` REQUIRED (proto L300). Fixed on `main` (`08b42eb`). |
| 474-504 | **[DONE] TaskArtifactUpdateEvent**: kind `taskArtifactUpdate`; member `taskArtifactUpdate`; new `index` | **inaccurate** | v0.3 kind was `"artifact-update"` (`v0.3.0` json L2277). The v1 member is **`artifactUpdate`** (proto L800). **There is no `index` field.** Fields are `taskId, contextId, artifact, append, lastChunk, metadata` (proto L308-322), and `append`/`lastChunk` already existed in v0.3. The `index` claim is **still present on `main`** (`docs/whats-new-v1.md` L488, L497). |
| 506-549 | **[DONE] OAuth 2.0**: implicit/password removed; DeviceCode; pkce; JSON example | **inaccurate** in parts | (a) Implicit/password are **deprecated, not removed** (proto L573-576). (b) DeviceCodeOAuthFlow fields are `deviceAuthorizationUrl, tokenUrl, refreshUrl, scopes` (proto L636-645); "`verification_uri`, `user_code`" (L519) **is not in the spec**. (c) The JSON example keys `"implicitFlow"`/`"authorizationCodeFlow"` are wrong. The OAuthFlows oneof members are `implicit`, `authorizationCode`, `clientCredentials`, `password`, `deviceCode` (proto L568-579), nested under `{"oauth2SecurityScheme":{"flows":{…}}}`. (d) `pkceRequired` correct. |

#### New dependencies (L553-607)

| L | Claim | Verdict | Correct statement |
|---|---|---|---|
| 559-563 | google.rpc.Status/ErrorInfo for HTTP+JSON **and JSON-RPC**; "Enforces structured ErrorInfo" | nuance / partly inaccurate for JSON-RPC | JSON-RPC uses the JSON-RPC 2.0 error object; only `data` takes the `Any`-array form. At v1.0.1, ErrorInfo is **SHOULD** for JSON-RPC (spec §9.5 L2455) and **MUST** for HTTP+JSON (§11.6 L2910) and gRPC (§10.6 L2687). |
| 565-583 | RFC 8785, RFC 7515, Google API guidelines | correct | spec §8.4. |
| 585-589 | ISO 8601 for "createdAt, lastModified, timestamp"; ms required | **inaccurate** | The only `google.protobuf.Timestamp` fields are `TaskStatus.timestamp` (proto L218) and `ListTasksRequest.statusTimestampAfter` (proto L696). ms is SHOULD. Fixed on `main` (`08b42eb`). |
| 593-599 | retained deps | correct | — |

#### Impact on developers (L611-981)

| L | Claim | Verdict | Correct statement |
|---|---|---|---|
| 615-649 | Part unification | correct, with the same v0.3-example caveat as above (`fileWithUri`) | — |
| 651-673 | stream discriminators `taskStatusUpdate`/`taskArtifactUpdate` (v0.3 kinds and v1 members) | **inaccurate** | v0.3: `status-update`/`artifact-update`. v1: `statusUpdate`/`artifactUpdate`. Fixed on `main`. |
| 675-694 | agent card access | correct | — |
| 696-715 | pagination: v0.3 `page/perPage` → `cursor, limit, nextCursor` | **inaccurate** | v0.3 had no paginated ListTasks at all. The v1 names are `pageToken`, `pageSize`, `nextPageToken` (proto L687-691, L707). Fixed on `main` (`aa042ec`). |
| 717-754 | enum mapping | correct | Add `TASK_STATE_UNSPECIFIED`/`ROLE_UNSPECIFIED`; v0.3 `"unknown"` has no named equivalent. |
| 756-759 | `mimeType` → `mediaType`; "Operation names (aliases provided during transition)" | correct / **not in spec** | The spec defines no JSON-RPC method aliases. Backward compatibility is done by exposing separate `AgentInterface`s per `protocolVersion` (spec §3.6.2 L741; proto L351-354). Appendix A's aliases are SDK/schema *type* names (L3359-3366), not wire method names. |
| 761-828 | error model; JSON-RPC example; HTTP example | mostly correct | JSON-RPC example (L780-794) matches spec §9.5 L2481-2503. "MUST include ErrorInfo" (L769) is accurate for HTTP+JSON/gRPC only. For JSON-RPC it was MUST in v1.0.0 and is **SHOULD** in v1.0.1. HTTP `Content-Type: application/json` (L767) was superseded in v1.0.1 by `application/a2a+json` SHOULD (§11.1 L2750; CHANGELOG 1.0.1 #1753); REST only. |
| 832-840 | `returnImmediately` default false | correct, with nuance | Missing: it **replaces v0.3 `blocking`** with inverted polarity (`v0.3.0` json L1679). The default is now blocking. Blocking returns on terminal **or interrupted** states (spec §3.2.2 L446). |
| 842-864 | signatures; extension requirements | correct | Client-side check is advisory. Normatively the **server** MUST return `ExtensionSupportRequiredError` (spec §3.3.4 L576). |
| 866-871 | `task.createdAt` / `task.lastModified` | **inaccurate** | No such fields. Removed on `main`. |
| 873-883 | A2A-Version header; VersionNotSupportedError | correct, with nuance | Missing: empty/absent header MUST be treated as `0.3`; only `Major.Minor` is compared; JSON-RPC code −32009 (spec §3.6 L708, §3.6.2 L737-739; §5.4 L1190). |
| 885-945 | migration phases; per-interface protocolVersion | correct (advisory) | "page-based pagination" phases refer to something that never existed in v0.3. |
| 955-980 | priority list | advisory | "createdAt/lastModified" item refers to nonexistent fields (removed on `main`). |

### C. Missing from the user's changelog (JSON-RPC-over-HTTP relevance)

**C1. Wire envelopes (high impact, Koog server and client)**
- `SendMessage` result = `SendMessageResponse` oneof wrapper `{"task":…}|{"message":…}` (proto L779-787; spec §9.4.1).
- `SendStreamingMessage` / `SubscribeToTask` SSE: each `data:` is `{"jsonrpc":"2.0","id":…,"result":<StreamResponse>}`, where StreamResponse has one of `task|message|statusUpdate|artifactUpdate` (spec §9.4.2 L2324-2328; proto L790-802).
- `kind` is gone from **Task and Message** too, not only Part/events. In v0.3 `Task.kind` and `Message.kind` were required (`v0.3.0` json `Task.required`, L1662).
- Error `data` is now an **array** of `@type`-tagged objects (spec §9.5 L2437). In v0.3 it was a free-form value (the guide's own v0.3 example L775-779).

**C2. Exact JSON-RPC params per method (all ProtoJSON camelCase)**

| Method | v1 params (proto) | v0.3 params | Result |
|---|---|---|---|
| `SendMessage`, `SendStreamingMessage` | `{tenant?, message (REQ), configuration?{acceptedOutputModes[], taskPushNotificationConfig?, historyLength?, returnImmediately}, metadata?}` L648-658, L143-161 | `MessageSendParams{message, configuration{acceptedOutputModes, blocking, historyLength, pushNotificationConfig}, metadata}` | `SendMessageResponse` / stream of `StreamResponse` |
| `GetTask` | `{tenant?, id (REQ), historyLength?}` L661-672. **No `metadata`** | `TaskQueryParams{id, historyLength, metadata}` | `Task` |
| `ListTasks` | `{tenant?, contextId?, status?, pageSize?, pageToken?, historyLength?, statusTimestampAfter?, includeArtifacts?}` L675-700 | n/a (JSON-RPC) | `{tasks, nextPageToken, pageSize, totalSize}` L703-712 |
| `CancelTask` | `{tenant?, id (REQ), metadata?}` L715-723 | `TaskIdParams{id, metadata}` | `Task` |
| `SubscribeToTask` | `{tenant?, id (REQ)}` L748-754 | `TaskIdParams` | stream |
| `CreateTaskPushNotificationConfig` | `TaskPushNotificationConfig{tenant?, id?, taskId, url (REQ), token?, authentication?{scheme (REQ), credentials?}}` L469-484 | `{taskId, pushNotificationConfig{…}}` | `TaskPushNotificationConfig` |
| `GetTaskPushNotificationConfig` | `{tenant?, taskId (REQ), id (REQ)}` L726-734 | `{id, pushNotificationConfigId?}` | `TaskPushNotificationConfig` |
| `ListTaskPushNotificationConfigs` | `{tenant?, taskId (REQ), pageSize?, pageToken?}` L757-769 | `{id}` | `{configs, nextPageToken}` L806-811 (v0.3: bare array) |
| `DeleteTaskPushNotificationConfig` | `{tenant?, taskId (REQ), id (REQ)}` L737-745 | `{id, pushNotificationConfigId}` | `google.protobuf.Empty` → `{}` |
| `GetExtendedAgentCard` | `{tenant?}` L772-776. The spec example omits `params` (§9.4.8 L2423-2428) | `agent/getAuthenticatedExtendedCard` | `AgentCard` |

Note the method name pluralisation: `ListTaskPushNotificationConfigs` (CHANGELOG #1486). SendMessageConfiguration's push field is `taskPushNotificationConfig` and its `taskId` "should be empty" (proto L147-149).

**C3. Behavior changes**
- **`blocking` → `returnImmediately` with inverted polarity; default is now blocking**, returning on terminal **or interrupted** states (R8). It has no effect on streaming. The spec text conflicts internally on this (see Discrepancies).
- **historyLength semantics** (R9). `historyLength: 0` must be emitted on the wire when explicitly set, because the field is `optional` (field presence).
- **SubscribeToTask on a terminal task → `UnsupportedOperationError` (−32004)**; first event MUST be `Task` (R6).
- **First event of a task stream MUST be `Task`**; a message-only stream has exactly one `Message` and then closes (R5).
- **Send to a terminal task → `UnsupportedOperationError`** (R18).
- **Push payload = `StreamResponse`**, `Authorization: <scheme> <credentials>`, at-least-once, client MUST reply 2xx (R21). `AuthenticationInfo.scheme` is a single REQUIRED string, where v0.3 had a `schemes[]` array.
- **Tenant handling in JSON-RPC**: `tenant` is a `params` field. The client MUST echo it from the selected AgentInterface and omit it otherwise (R16). The server routes on it (format opaque).
- **ListTasks**: default `pageSize` 50, range 1–100 (the spec's validation example rejects 150 and negative `historyLength`: §6.5 L1589-1617; for JSON-RPC that maps to −32602). Sorted by status timestamp descending. `includeArtifacts` default false, which MUST omit `artifacts`. `nextPageToken` always present. `statusTimestampAfter` is inclusive ("greater than or equal", proto L694-696). Results scoped to the caller (R10, R26).
- **Versioning**: header `A2A-Version`, value `Major.Minor`. Absent/empty means `0.3`. Unsupported gives `VersionNotSupportedError` −32009 (R13). For JSON-RPC, service params MUST be HTTP headers (§9.2). The §3.6.1 "MAY send as request parameter" example is a REST query string.
- **Extensions**: header renamed from `X-A2A-Extensions` (v0.3) to `A2A-Extensions`. Server MUST error with −32008 for a missing required extension, and SHOULD echo activated extensions in the response header (R15).
- **Capability validation errors** (R17), including the new `ExtendedAgentCardNotConfiguredError` (−32007).
- **New error types and codes**: `InvalidAgentResponseError` −32006, `ExtendedAgentCardNotConfiguredError` −32007, `ExtensionSupportRequiredError` −32008, `VersionNotSupportedError` −32009 (spec §5.4 L1187-1190).
- **contextId rules**: if the agent can't accept a client-provided `contextId` it MUST reject and MUST NOT generate a new one; a client-provided `taskId` for a new task is not supported (spec §3.4.1 L593; §3.4.2 L613-615).

**C4. Data-model deltas not mentioned**
- `TASK_STATE_UNSPECIFIED`/`ROLE_UNSPECIFIED` (proto L189, L247). v0.3 `"unknown"` state dropped.
- `AgentCapabilities.stateTransitionHistory` removed.
- SecurityScheme oneof wrapper; `securityRequirements` shape; `APIKeySecurityScheme.location`; `AgentSkill.securityRequirements` (see the AgentCard row above).
- `AgentInterface.transport` → `protocolBinding`, plus `protocolVersion` (REQUIRED) and `tenant`.
- `Part.data` is any JSON value; `file.name` → `filename`.
- `TaskStatusUpdateEvent.contextId` and `TaskArtifactUpdateEvent.contextId` are REQUIRED (proto L300, L312).
- `GetTaskRequest.metadata` removed (CHANGELOG lists "add metadata to CancelTaskRequest" #1485, but v0.3 `TaskIdParams` already had it).
- Package is now `lf.a2a.v1` (CHANGELOG #1474; proto L3). This affects ErrorInfo/Any `@type` URLs only if A2A types are packed.

**C5. ProtoJSON rules** (ADR-001 makes ProtoJSON normative. The rules below come from the external ProtoJSON spec at https://protobuf.dev/programming-guides/json/, not from the A2A repo. Medium confidence for exact wording.)
- Output uses lowerCamelCase JSON names. Parsers must accept both the camelCase and the original snake_case names.
- Singular fields **without** presence (plain `string`, `bool`, `int32`, enum) that hold their default (`""`, `false`, `0`, enum 0) are **omitted** on output, as are empty repeated fields and maps. Fields **with** presence (`optional` scalars such as `historyLength`, `pageSize`, `includeArtifacts`, `AgentCapabilities.streaming`; message fields; oneof members) are emitted whenever set, even to a default value.
- `null` on input means "unset" (except for `google.protobuf.Value`, where it is JSON null).
- Enums: output the name string; parsers also accept the integer.
- `int32`: JSON number. Parsers also accept numeric strings. A2A has **no int64 fields** (proto: only `int32` at L154, L671, L687, L693, L709, L711, L765), so no string-encoded integers appear on output.
- `bytes` (`Part.raw`): standard base64 with padding on output; parsers accept standard or URL-safe, with or without padding.
- `Timestamp`: RFC 3339, `Z`-normalized, 0/3/6/9 fractional digits on output. A2A additionally forbids non-`Z` offsets (spec §5.6.1 L1255).
- `Struct` → JSON object, `Value` → any JSON, `Empty` → `{}`. `Any` → object with `@type` plus fields.
- ProtoJSON parsers reject unknown fields by default, but A2A says implementations SHOULD ignore them (spec §5.7 L1275).

## Koog status

Not examined (per instructions). The `[DONE]` markers show which guide sections the user believes are implemented. Five of the nine `[DONE]` sections contain upstream errors: Message (`extensions` "added"), Artifact (`extensions` "added"), PushNotificationConfig (`configId`/`createdAt`/"enhanced" auth), Stream Event Objects (`taskStatusUpdate`/`taskArtifactUpdate` naming, `index`), and OAuth (implicit/password "removed", wrong JSON keys). If the implementation followed those sections literally, these are the places to re-check against the proto: no `index`, `createdAt`, or `configId`; members `statusUpdate`/`artifactUpdate`; keep deprecated `implicit`/`password` parseable; `AuthenticationInfo.scheme` as a single string.

## Testability notes

TCK and Python SDK were not consulted in this pass. Most items in Requirements are wire-observable and suitable for JSON round-trip unit tests against hand-written ProtoJSON fixtures taken from the proto: wrappers, member names, params shapes, the error `data` array, omission rules, and enum names. Header-driven behavior (A2A-Version default `0.3`, VersionNotSupported −32009, `A2A-Extensions` echo, −32008) fits server transport tests. Behavioral items (blocking vs `returnImmediately`, first-event-is-Task, subscribe on terminal → −32004, ListTasks ordering/defaults/omission) fit server integration tests. The TCK requirement IDs covering these should be looked up with `check-a2a-tck` (see Open questions).

## Discrepancies

1. **Guide vs proto (released v1.0.1)**: `Task.createdAt/lastModified`, push `createdAt`/`configId`, `taskStatusUpdate`/`taskArtifactUpdate`, `index`, `cursor/limit/nextCursor`, `config_id`, ISO-8601 "createdAt/lastModified". All are absent from the proto. All except `index` were fixed on **`main` only (unreleased)** by `08b42eb`/`aa042ec`. `index`, "removed" implicit/password, `extensions[]` "new", the `implicitFlow`/`authorizationCodeFlow` keys, device-code `verification_uri`/`user_code`, and v0.3 `fileWithUri` remain wrong on `main`.
2. **CHANGELOG/guide vs proto on OAuth**: "remove implicit/password" (CHANGELOG 1.0.0 #1303; guide L40, L510-513) vs the proto keeping both as `[deprecated = true]` oneof members (proto L573-576).
3. **JSON-RPC ErrorInfo strength changed in a patch release**: at `v1.0.0`, JSON-RPC §9.5 said "implementations **MUST** include a `google.rpc.ErrorInfo` … with `reason` … `domain`". At `v1.0.1` it says "**SHOULD** use well-known types such as `google.rpc.ErrorInfo`" (spec@v1.0.1 L2455; `git diff v1.0.0 v1.0.1 -- docs/specification.md`). JSON-RPC numeric codes did not change. The same patch changed gRPC/HTTP mappings: TaskNotCancelable 409→400; PushNotificationNotSupported, UnsupportedOperation and VersionNotSupported gRPC `UNIMPLEMENTED`→`FAILED_PRECONDITION`; ContentTypeNotSupported 415→400; InvalidAgentResponse 502→500.
4. **Blocking default vs "return immediately"**: §3.1.1 L180 says "The operation MUST return immediately with either task information or response message", and §3.3.3 L567 says "Operations return immediately". Both contradict §3.2.2 L444-446 and proto L157-160 (blocking is the default and MUST wait for a terminal/interrupted state). The proto and §3.2.2 are the specific rule.
5. **When a task stream closes**: §3.1.2 L210 and §3.1.6 L309 say close at a **terminal** state. §11.7 L2973 (HTTP+JSON) says close at a "terminal **or interrupted** state" and MAY resend a final Task snapshot. JSON-RPC §9 doesn't restate the rule. Python SDK behavior should settle what clients see in practice.
6. **`nextPageToken` MUST always be present (§3.1.4 L246) vs ProtoJSON omitting empty non-optional strings.** The same applies to REQUIRED `int32` `pageSize`/`totalSize` when 0 (proto L707-711). A strict ProtoJSON serializer would drop `"nextPageToken": ""`. Implementations need an explicit override, and clients must treat absent as `""`.
7. **Spec text references nonexistent names**: `PushNotificationConfig` message (§3.1.7 L326, §3.1.8 L351, §4.3.1 L832-834, §13.2 L3130). Fixed on `main` to `TaskPushNotificationConfig` (`f63dbb4`, unreleased). Others: `AgentCard.security` (§3.1.11 L423, §13.3 L3143; proto is `securityRequirements`); `createdAt`/`lastModified` in the §5.6.1 example (L1244-1245); `protocolVersions` in §A.2.1 L3509/L3517 (fixed on `main`); §A.2.2 L3537/L3558 calls the legacy field `supportsExtendedAgentCard` and the new field number 5, but v0.3 was `supportsAuthenticatedExtendedCard` and the proto field number is 4 (proto L419); §9.3 L2276 example `"method": "category/action"` (stale).
8. **REST-only inconsistencies (not relevant to Koog)**: SubscribeToTask is `GET` in the proto (L77-82) but `POST` in spec §5.3 L1169 and §11.3.2 L2795. §6.4 L1466-1477 and §6.5 L1596-1617 still use RFC 9457 `application/problem+json`, contradicting §11.6.
9. **Timestamp precision**: the guide says ms is "explicit"/required. The spec makes ms only SHOULD and allows omitting fractions (§5.6.1 L1236, L1254). The proto comment example has no fraction (L217).
10. **Announcement vs data model**: `docs/announcing-1.0.md` says "AgentCard … has evolved in a backward-compatible way and now allows agents to advertise support for both existing v0.3 protocol behavior and v1.0 simultaneously". The v1 proto removes v0.3 card fields (`url`, `preferredTransport`, `protocolVersion`, …), so a strict v1 card is not readable by a v0.3 client. The mechanism is presumably multiple `supportedInterfaces` entries and/or SDK-emitted hybrid cards. This is a hypothesis to verify in the Python SDK.

## Open questions

- **Exact ErrorInfo `reason` strings**: the spec gives only the rule (UPPER_SNAKE_CASE, no `Error` suffix; examples `TASK_NOT_FOUND`, `TASK_NOT_CANCELABLE`, §11.6 L2913). The derived set is `TASK_NOT_FOUND`, `TASK_NOT_CANCELABLE`, `PUSH_NOTIFICATION_NOT_SUPPORTED`, `UNSUPPORTED_OPERATION`, `CONTENT_TYPE_NOT_SUPPORTED`, `INVALID_AGENT_RESPONSE`, `EXTENDED_AGENT_CARD_NOT_CONFIGURED`, `EXTENSION_SUPPORT_REQUIRED`, `VERSION_NOT_SUPPORTED`. Verify against the Python SDK and TCK (route to `check-python-sdk` / `check-a2a-tck`).
- Does the Python SDK (pinned version used by Koog's client integration tests) emit `nextPageToken: ""` and `pageSize`/`totalSize: 0`, close JSON-RPC streams on interrupted states, attach ErrorInfo in JSON-RPC `error.data`, and emit hybrid v0.3+v1 agent cards?
- Which TCK requirement IDs cover A2A-Version defaulting/rejection, SubscribeToTask-on-terminal, first-event-is-Task, ListTasks defaults/omission, and the error `data` array? Is the TCK strict about ErrorInfo for JSON-RPC (MUST in 1.0.0 vs SHOULD in 1.0.1)?
- Does Koog's local `a2a/a2a.proto` match upstream `v1.0.1` (a prior report exists at `a2a/.agents/research/proto-drift.md`; not re-checked here)?
- Unreleased `main` adds spec §7.6.4 "In-Task Authorization Scope" (`6550d34`): `AUTH_REQUIRED` transitions MUST NOT be treated as authorization by themselves. It is not in any v1.* tag yet.
