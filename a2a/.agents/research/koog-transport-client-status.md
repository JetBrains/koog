# How completely and correctly do Koog's JSON-RPC transport modules and `a2a-client` implement the A2A v1.x JSON-RPC-over-HTTP binding and client-side requirements?

**Sources checked:**
- A2A spec repo (`a2a-protocol`) at tag **v1.0.1** (`3303592588e3`). `main` is at `65dadbd9b900` (v1.0.1-70). `git diff v1.0.1 main` has no normative changes to sections 3.6, 5.4, 8.3, or 9; the only proto change is a comment on `AgentInterface.url`.
- A2A Python SDK (`a2a-python`): `main` at `ddce6b87c4e5` (v1.2.1-3), plus tag **v1.1.0** (`96c14b79325c`). v1.1.0 is the version pinned by `a2a/test-python-a2a-server/pyproject.toml` (`a2a-sdk[http-server]==1.1.0`) and its `uv.lock`.
- A2A TCK (`a2a-tck`) at `263b9cfaf16a`. This is the same commit pinned in `a2a/test-tck/setup_tck.sh:9`.
- Koog at `e45d756bc` (branch `eugenethedev/a2a-1.0`). Ktor client sources 3.3.3 (`gradle/libs.versions.toml:25`) were checked for SSE behavior.

**Confidence:** high for the static wire-shape and code-path findings. I traced them in source on both sides. Medium for the reason behind the "subscribe doesn't work" note: I reconstructed it by reading code and did not run anything.

## Answer

Overall: **partial**.

**Done:**
- All 11 JSON-RPC method names are correct. Both the client and the server transport wire every operation.
- Request and response envelopes follow JSON-RPC 2.0. `params` are the v1 proto request messages.
- `StreamResponse` and `SendMessageResponse` use the member-based (`task` / `message` / `statusUpdate` / `artifactUpdate`) encoding.
- There is no `final` flag. Streams end when the SSE connection closes.
- Errors are serialized with `error.data` as an array, and the error-code table covers -32700..-32603 and -32001..-32007, -32009.
- The client sends `A2A-Version: 1.0`. The well-known card path is correct.

**Partial / incorrect:**
- **Client error parsing is brittle.** `error.data` must be a JSON array, or decoding fails with `SerializationException` instead of `A2AException`. The pinned Python server omits `data` for `-32601` and `-32700`, and sends it as a *string* for `-32600` and `-32602`.
- **Streaming errors returned as plain JSON break the client.** A JSON-RPC error sent as a plain JSON body to a streaming request (what Python does) surfaces as Ktor `SSEClientException`, not `A2AException`.
- **The server never emits `ErrorInfo`.** It always sends `"data": []`. The TCK treats this as MUST (JSONRPC-ERR-003); the spec says SHOULD for JSON-RPC.
- **The server puts pre-stream errors inside the SSE stream.** Python and the TCK expect a plain JSON error body. TCK STREAM-SUB-004 would fail.
- **The server rejects a request with no `params`.** That includes the spec's own `GetExtendedAgentCard` example.
- **Error responses with an unknown id omit `id` entirely**, instead of sending `"id": null`.

**Missing:**
- Server-side `A2A-Version` validation: no default to 0.3 when the header is absent, and no `VersionNotSupportedError` for an unsupported version.
- `ExtensionSupportRequiredError` (-32008).
- First-class `A2A-Extensions` support.
- `supportedInterfaces` selection by `protocolBinding` + `protocolVersion`.
- Automatic `tenant` propagation.
- Agent Card signature verification.
- Client-side streaming capability check for `SubscribeToTask`.
- SSE client tests.

The "test subscribe doesn't work" note (commit `73f71807d`) appears to be fixed by `24e5aebd5`. That commit fixed a test assumption that v1 breaks, and a Python test-agent stream that never reached a terminal state. The transport was not the problem.

## Requirements

| # | Requirement | Tier | Bindings | Citation (spec @ v1.0.1 unless noted) |
|---|-------------|------|----------|----------|
| R1 | JSON-RPC 2.0 over HTTP; `application/json` for requests and responses; SSE `text/event-stream` for streaming | MUST | JSON-RPC | `docs/specification.md:2233-2238` (§9.1), `:2268-2279` (§9.3), `:2322` (§9.4.2) |
| R2 | PascalCase method names: `SendMessage`, `SendStreamingMessage`, `GetTask`, `ListTasks`, `CancelTask`, `SubscribeToTask`, `Create/Get/List(s)/DeleteTaskPushNotificationConfig(s)`, `GetExtendedAgentCard` | MUST | JSON-RPC | §5.3 `docs/specification.md:1160-1174`; §9.4 `:2281-2429` |
| R3 | `SendMessage` result is `SendMessageResponse` (oneof `task`/`message`); stream events are `StreamResponse` wrapped in a JSON-RPC success envelope | MUST | JSON-RPC | `:2302-2312`, `:2322-2328`; proto `specification/a2a.proto:779-800` |
| R4 | Service parameters are sent as HTTP headers; keys are case-insensitive; multiple `A2A-Extensions` values SHOULD be comma-joined in one header | MUST / SHOULD | JSON-RPC, REST | §9.2 `:2240-2248`; §3.2.6 `:477-488` |
| R5 | Client sends `A2A-Version` (Major.Minor) on each request | MUST | all | §3.6.1 `:712`; §3.6 `:708` |
| R6 | Server processes the requested `Major.Minor`; an unsupported version yields `VersionNotSupportedError` (-32009); an empty or missing header is interpreted as 0.3 | MUST | all | §3.6.2 `:737-739`; §5.4 `:1176-1190` |
| R7 | SDKs provide mechanisms for transport/version negotiation | MUST (tooling) | all | §3.6.3 `:745` |
| R8 | `error.data` is an array of `@type`-tagged objects; each object MUST have `@type`; implementations SHOULD use `google.rpc.ErrorInfo` / `BadRequest` | MUST (array, `@type`) / SHOULD (ErrorInfo) | JSON-RPC | §9.5 `:2437`, `:2455`; §3.3.2 `:541-549` |
| R9 | A2A error → JSON-RPC code mapping, including -32008 `ExtensionSupportRequiredError` and -32009 `VersionNotSupportedError` | MUST | JSON-RPC | §5.4 `:1176-1190`; §3.3.2 table `:551-563` |
| R10 | `SubscribeToTask`: first event MUST be a `Task`; stream terminates at a terminal state; subscribing to a terminal task → `UnsupportedOperationError` | MUST, capability:streaming | all | §3.1.6 `:285-311`; §9.4.6 `:2408` |
| R11 | Stream patterns: a Message-only stream closes after one `Message`; a Task stream closes at terminal state (there is no `final` field in v1) | MUST, capability:streaming | all | §3.1.2 `:203-208`; proto `a2a.proto:296-305` |
| R12 | The agent MUST reject streaming / push / extended-card operations when the capability is not declared | MUST (server) | all | §3.3.4 `:569-576` |
| R13 | Clients SHOULD validate capability support from the Agent Card before calling | SHOULD (client) | all | §3.3.4 `:578` |
| R14 | Client selects the first supported entry in `supportedInterfaces`, uses its URL, and sets `tenant` in every request to the selected interface's `tenant` (omitted if unset) | MUST | all | §8.3.2 `:1993-2000`; proto `a2a.proto:344-350` |
| R15 | Well-known card URI `https://{server_domain}/.well-known/agent-card.json` | discovery mechanism (not a MUST for clients) | all | §8.2 `:1974-1980` |
| R16 | Signature verification steps (when a client verifies) | MUST if verifying; SHOULD verify at least one signature | all | §8.4.3 `:2117-2134` |
| R17 | `GetExtendedAgentCard`: client SHOULD replace the cached card | SHOULD, capability:extendedAgentCard | all | §3.1.11 `:425` |
| R18 | `ListTasks`: `nextPageToken` MUST always be present (`""` on the last page) | MUST | all | §3.1.4 `:246`; proto `a2a.proto:703-712` |
| R19 | `return_immediately` / `history_length` live in `SendMessageConfiguration`; `historyLength` is also on GetTask/ListTasks | normative shape | all | §3.2.2 `:438-456`; §3.2.4 `:465-471`; proto `a2a.proto:146-161` |
| R20 | Unrecognized fields SHOULD be ignored | SHOULD | all | §5.7 `:1257-1275` |
| R21 | Agent Card caching (Cache-Control/ETag on the server; RFC 9111 on the client) | SHOULD | HTTP | §8.6 `:2213-2227` |

## Details

### Wire shapes the Koog code must produce and accept (ProtoJSON, camelCase)

- **Request:** `{"jsonrpc":"2.0","id":<string|number>,"method":"<PascalCase>","params":{...proto request...}}`.
  - JSON-RPC 2.0 allows `params` to be omitted. The spec's `GetExtendedAgentCard` example omits it (`docs/specification.md:2421-2428`).
- **Success:** `{"jsonrpc":"2.0","id":...,"result":{...}}`.
  - Delete returns `google.protobuf.Empty` (`a2a.proto:131`). Python sends `"result": null` (`src/a2a/server/routes/jsonrpc_dispatcher.py:537-541` @main; `JSONRPC20Response(result=None)`).
- **Error:**

  ```json
  {"jsonrpc":"2.0","id":<id|null>,"error":{"code":-32001,"message":"...","data":[{"@type":"type.googleapis.com/google.rpc.ErrorInfo","reason":"TASK_NOT_FOUND","domain":"a2a-protocol.org","metadata":{...}}]}}
  ```

  (`docs/specification.md:2484-2502`). The JSON-RPC 2.0 spec (external, jsonrpc.org §5) requires the `id` member, set to `null` when the id could not be determined.
- **SSE:** each event is `data: {"jsonrpc":"2.0","id":...,"result":{"task"|"message"|"statusUpdate"|"artifactUpdate": {...}}}`.
  - Python additionally sets `event: error` on error events (`jsonrpc_dispatcher.py:571-601` @main, `:579` @v1.1.0). The spec does not require an event name.

### Python reference behavior where the spec is implicit

- **Pre-stream errors come back as plain JSON, not SSE.**
  - Python validates the version, then eagerly pulls the first stream event before opening SSE (`jsonrpc_dispatcher.py:349-384` @main; `:344`, `:377` @v1.1.0).
  - Errors raised before the first event are returned as a plain `JSONResponse` with HTTP 200 (`:164-204`, `:341-342`).
  - Errors after streaming has started are yielded inside the stream (`:402-403`).
  - The Python client handles a non-`text/event-stream` response to a streaming request by reading it as JSON (`src/a2a/client/transports/http_helpers.py:107` @v1.1.0).
  - The TCK client does the same (`tck/transport/jsonrpc_client.py:149-162`).
- **Version validation.**
  - `validate_version("1.0")` wraps both the streaming and non-streaming dispatch (`src/a2a/utils/version_validator.py:23-130`).
  - A missing or empty header is treated as `0.3` and therefore rejected with -32009 (`:75-76`).
  - A same-major version is accepted (`:80-90`): `1.5` and `1.0.1` both pass.
  - Header lookup is case-insensitive: Starlette lowercases names, and the validator also tries the lowercase key (`src/a2a/server/routes/common.py:65` @v1.1.0, `version_validator.py:71-73`).
- **Error `data`.**
  - For `A2AError` subclasses, `build_error_details` always emits a leading ErrorInfo with `reason` (e.g. `TASK_NOT_FOUND`), `domain` `a2a-protocol.org`, and `metadata` (`src/a2a/utils/error_handlers.py:36-81`; reasons in `src/a2a/utils/errors.py:155-194`).
  - Dispatcher-level errors built from `a2a.server.jsonrpc_models` (`MethodNotFoundError`, `JSONParseError`, `InternalError`) carry **no** `data` (`jsonrpc_models.py:16-56`; `jsonrpc_dispatcher.py:284-288`, `:329-333`).
  - In the **pinned v1.1.0**, `InvalidRequestError` and `InvalidParamsError` also come from `jsonrpc_models`, with `data=str(e)`, i.e. a **string** (`jsonrpc_dispatcher.py:16-19`, `:258`, `:296` @v1.1.0).
  - On `main` they come from `a2a.utils.errors` with `data={'parseError': ...}`, which turns into an ErrorInfo array.
- **Client error parsing.** The Python client only looks for ErrorInfo if `data` is a list, and otherwise ignores `data` (`src/a2a/client/transports/jsonrpc.py:318-337` @v1.1.0).
- **Interface selection and tenant (Python client).**
  - `ClientFactory._find_best_interface` filters by `protocol_binding`, prefers `protocol_version == "1.0"`, then >1.0, then ≥0.3 (`src/a2a/client/client_factory.py:213-256` @v1.1.0).
  - It wraps the transport in `TenantTransportDecorator` when `interface.tenant` is set (`:354-357`; `transports/tenant_decorator.py:24-35`).
  - It sets the `A2A-Version` default header on the shared httpx client, so the card fetch also carries it (`client_factory.py:81-84`).
  - It accepts a pluggable `signature_verifier` (`card_resolver.py:146-195`).
  - It checks the streaming capability before `subscribe` (`base_client.py:322`).
- **Pinned v1.1.0 subscribe semantics.**
  - `DefaultRequestHandler` is `DefaultRequestHandlerV2` (`src/a2a/server/request_handlers/__init__.py:46` @v1.1.0).
  - Subscribe yields the current Task first (`agent_execution/active_task.py:614-620` @v1.1.0).
  - The stream closes only when the consumer reaches a terminal state (`:281-306`) or the producer ends. In V2 the producer loops forever on `_request_queue.get()` for further turns (`:493-516`).
  - Subscribing to a finished task raises **`InvalidParamsError`** in v1.1.0 (`:599-602`, `:462-465`). `main` raises `UnsupportedOperationError` (`active_task.py:500-503`, `:529-531`).

## Koog status

### 1. Method names, envelopes, params, id (a2a-transport-core-jsonrpc)

- **Method names are correct for all 11 operations** (`a2a/a2a-transport/a2a-transport-core-jsonrpc/src/commonMain/kotlin/ai/koog/a2a/transport/jsonrpc/A2AMethod.kt:10-20`). Two enum constant names are leftovers, though their values are right:
  - `GetAuthenticatedExtendedAgentCard` (value `GetExtendedAgentCard`)
  - `ListTaskPushNotificationConfig` (singular; value `ListTaskPushNotificationConfigs`)
- **Envelope models** (`.../jsonrpc/model/Messages.kt:24-58`) are serialized with `JSONRPCJson { explicitNulls=false; encodeDefaults=false; ignoreUnknownKeys=true }` (`.../serialization/Serialization.kt:29-33`).
  - `jsonrpc` has no default, so it is always encoded.
  - Because of `explicitNulls=false`, `JSONRPCErrorResponse(id = null)` serializes **without an `id` member**. A unit test locks this in (`JsonRpcSerializationTest.kt:135-151`). This violates JSON-RPC 2.0 §5, which requires `"id": null`.
- **Request ids.** The client generates UUID string ids (`JSONRPCClientTransport.kt:68-80`). `RequestIdSerializer` accepts strings and integral numbers only (`Serialization.kt:61-85`). An explicit `"id": null` or a fractional number is rejected as `-32600`.
- **Params.** `params` is the kotlinx-encoded v1 request type (`JSONRPCClientTransport.kt:77`).
  - The Kotlin request models carry `tenant` (`a2a/a2a-core/src/commonMain/kotlin/ai/koog/a2a/model/Requests.kt:15-160`), plus `returnImmediately` and `historyLength` in `SendMessageConfiguration` (`:42-47`).
  - `historyLength` is also on GetTask and ListTasks (`:60`, `:83`).
  - Delete decodes `Unit?`, which accepts `null` or `{}` (`JSONRPCClientTransport.kt:190-195`).
- **Payload encoding.** `SendMessageResponse` and `StreamResponse` use `PropertyWrappingPolymorphicSerializer` with the keys `task`, `message`, `statusUpdate`, `artifactUpdate` (`a2a/a2a-core/.../serialization/Serialization.kt:171-196`; KIND constants in `model/Task.kt:30,51,76`, `model/Message.kt:40`). There is no `final` field (`model/Task.kt:44-49`).

### 2. Error mapping

**Codes** (`a2a/a2a-core/.../exceptions/Exceptions.kt:7-24`, `:44-58`):
- Covers -32700..-32603, -32001..-32007, and -32009.
- **Missing -32008 `ExtensionSupportRequiredError`.** It maps to `A2AUnknownException`.
- -32007 is named `AUTHENTICATED_EXTENDED_CARD_NOT_CONFIGURED` / `A2AAuthenticatedExtendedCardNotConfiguredException`. That is the v0.3 name; v1 calls it `ExtendedAgentCardNotConfiguredError`.

**Server serialization** (`JSONRPCServerTransport.kt:250-277`):
- Always writes `data` as an array of `a2aException.details`.
- Every exception defaults to `details = emptyList()` (`Exceptions.kt:67-193`), and no server code adds ErrorInfo. A repo search finds no production use of `ErrorInfo(` outside the models.
- So every Koog server error carries `"data": []`, with no ErrorInfo, reason, or domain.
- The ErrorInfo model itself is correct: `@type`, `reason`, `domain` defaulting to `a2a-protocol.org` via `@EncodeDefault`, and `metadata: Map<String,String>` (`a2a/a2a-core/.../exceptions/ErrorData.kt:34-49`).

**Client parsing** (`JSONRPCClientTransport.kt:98-101`):
- It calls `decodeFromJsonElement(ListSerializer(ErrorData.serializer()), it.data)`. `data` defaults to `JsonNull` (`Messages.kt:50`).
  - If `data` is **absent**, the decode throws (kotlinx "expected JsonArray, got JsonNull").
  - If `data` is a **string or object**, it also throws.
  - If any ErrorInfo `metadata` value is not a string, it also throws.
- The user then gets a `SerializationException` instead of an `A2AException`.
- Against the pinned Python v1.1.0 server this affects:
  - `-32601` MethodNotFound (no data)
  - `-32700` (no data)
  - `-32600` / `-32602` (string data)
- Only `A2AError`-subclass errors (TaskNotFound, UnsupportedOperation, VersionNotSupported, and similar) parse correctly.

**Edge cases in server request validation** (`JSONRPCServerTransport.kt:300-362`):
- **Missing `params`** becomes `JsonNull` (`:357`). `toRequest` then fails to decode any object type from `JsonNull` and throws `-32602` (`:212-221`). So `{"method":"GetExtendedAgentCard"}` without params, which is the spec's own example, is rejected. Python defaults to `{}` (`jsonrpc_dispatcher.py:293`).
- **Non-primitive `jsonrpc` value:** `jsonPrimitive` throws `IllegalArgumentException` (`:349-352`), which is mapped to `-32603` instead of `-32600`.
- **Check order:** `jsonrpc` is checked *after* the method lookup, so a wrong version plus an unknown method returns `-32601`.
- **Batches:** a batch array fails to decode as an object and returns `-32700`. Python returns `-32600` "Batch requests are not supported" (`jsonrpc_dispatcher.py:240-247`).
- **Notifications** (no `id`) are answered with `-32600`.
- **Dead code:** the `SubscribeToTask` branch in non-streaming `onRequest` (`:128-129`) is unreachable, and would fail at runtime because there is no serializer for `Flow`.

### 3. Service parameters (A2A-Version, A2A-Extensions, content types)

- **Client.**
  - `A2AClient.prepareContext` adds `A2A-Version: 1.0` when that exact key is absent (`a2a/a2a-client/src/commonMain/kotlin/ai/koog/a2a/client/A2AClient.kt:206-217`; `a2a/a2a-core/.../consts/A2AVersions.kt:6-13`).
  - The key check is case-sensitive, so a user-supplied `a2a-version` produces a duplicate.
  - `HttpJSONRPCClientTransport` used directly, without `A2AClient`, sends no version header.
  - `UrlAgentCardResolver` sends no `A2A-Version` (`AgentCardResolver.kt:38-62`). Python does send it on the card fetch.
  - **A2A-Extensions:** only a constant exists (`A2AHeaders.kt:17`). Nothing in the client sets it, and there is no comma-joining helper (Python: `client/service_parameters.py:49-62`). `ctx.headers` values are appended with `appendAll` (`HttpJSONRPCClientTransport.kt:58-62`). Whether Ktor emits a list as one comma-joined line is unverified (hypothesis).
  - **Content type:** `application/json` on requests (`HttpJSONRPCClientTransport.kt:39-42`). Ktor's SSE plugin adds `Accept: text/event-stream` (Ktor `SSEClientContent.kt`).
- **Server.**
  - Headers are copied into `ServerCallContext` (`Routes.kt:80-82`), and **nothing reads `A2A-Version`**. The only version helper, `isVersionCompatible` (`a2a/a2a-core/.../validation/VersionValidation.kt:6-11`), has no callers.
  - So there is no default to 0.3 and no -32009 (R6 not met).
  - **A2A-Extensions:** not parsed, and -32008 does not exist.
  - Responses go through `ContentNegotiation(json)` → `application/json` (`Routes.kt:74-76`, `:87-89`).

### 4. Streaming

- **Server** (`Routes.kt:90-106`):
  - Sets `text/event-stream` and emits one `ServerSentEvent(data = <JSON-RPC envelope>)` per event, with no `event:` name.
  - Errors from param decoding are thrown before the flow is built, so they go out as plain JSON (`JSONRPCServerTransport.kt:89-99`).
  - **Errors raised inside the handler flow** are emitted *as an SSE event*, after HTTP 200 + `text/event-stream` has already been committed (`:158-180`). That covers TaskNotFound, streaming not supported, and terminal task.
  - Python returns these as plain JSON. The TCK client marks any `text/event-stream` response as `success=True` (`tck/transport/jsonrpc_client.py:149-170`), so STREAM-SUB-004 `test_subscribe_nonexistent_task_returns_error` (`tests/compatibility/jsonrpc/test_sse_streaming.py:177-209`) would report "operation succeeded" for a Koog server that throws lazily.
- **Client** (`HttpJSONRPCClientTransport.kt:70-94`):
  - Uses Ktor `sse {}` and decodes each `data` as `JSONRPCResponse`. An error envelope becomes `A2AException` via `toResponse`, which is correct for in-stream errors.
  - **If the server answers a streaming call with `application/json`**, Ktor throws `SSEClientException("Expected Content-Type text/event-stream ...")` (Ktor 3.3.3 `plugins/sse/SSE.kt:192-210`). Pinned Python does this for any pre-stream error: TaskNotFound, terminal-task subscribe, version error.
  - The `onCompletion` rethrow only unwraps when `cause is A2AException` (`JSONRPCClientTransport.kt:132-137`), so the user sees the Ktor exception.
  - With `expectSuccess = true`, non-2xx responses surface as Ktor `ResponseException`.
  - Stream termination is simply the end of the connection, which is correct for v1.
- **SubscribeToTask** is wired as a streaming method (`A2AMethod.kt:16`, `JSONRPCServerTransport.kt:166-167`, `JSONRPCClientTransport.kt:170-173`).

### 5. Tenant

`tenant` exists on every request model, and on `AgentInterface` (`a2a/a2a-core/.../model/AgentCard.kt:123-128`). Nothing copies `AgentInterface.tenant` into requests. Callers must set it manually on each request (R14 not met by the SDK). The server transport passes it through inside `params` and does not use it for routing.

### 6. Client (a2a-client)

- **AgentCardResolver.**
  - `UrlAgentCardResolver(baseUrl, path = "/.well-known/agent-card.json")` (`AgentCardResolver.kt:38-62`; `A2APaths.kt:12`).
  - The path starts with `/`, so Ktor's `DefaultRequest` replaces any path in `baseUrl` (Ktor `DefaultRequest.kt:144-157`). The card is always fetched from the origin root. That matches §8.2; Python instead appends to the base path (`card_resolver.py:140-181` @v1.1.0).
  - There is no caching and no ETag handling (R21 SHOULD).
  - `ExplicitAgentCardResolver` exists.
- **Interface selection: missing.**
  - `A2AClient(card, transport)` takes a pre-built transport (`A2AClient.kt:35-38`). Nothing reads `supportedInterfaces`, `protocolBinding`, or `protocolVersion`.
  - The integration test hard-codes the container URL (`A2AClientJsonRpcIntegrationTest.kt:38-50`), because the Python card advertises `http://localhost:9999/` (`a2a/test-python-a2a-server/src/main.py:53-59`).
  - This leaves R7 and R14 unmet.
  - `TransportProtocol.HTTP_JSON_REST = "HTTP+JSON/REST"` (`a2a/a2a-core/.../model/AgentCard.kt:94`) is wrong for v1. The proto says `HTTP+JSON` (`a2a.proto:340-342`), so it would mis-select if selection is added.
- **Capability checks** (`A2AClient.kt:219-235`):
  - streaming is checked for `sendMessageStreaming`;
  - pushNotifications for all four push-config methods;
  - extendedAgentCard for `getExtendedAgentCard`.
  - **`subscribeToTask` is not checked** (`:136-141`). R13 SHOULD; Python checks it.
  - Failures throw `IllegalStateException` via `check`, not an A2A error.
- **GetExtendedAgentCard** replaces the cached card (`A2AClient.kt:58-67`, R17 met).
- **ListTasks:** wired. `ListTasksResponse` requires all four fields (`a2a/a2a-core/.../model/Responses.kt:18-24`), which is consistent with R18. Python uses `always_print_fields_with_no_presence` for ListTasks (`routes/common.py`, `serialize_list_tasks_response` @main).
- **Push-config CRUD:** wired. `ListTaskPushNotificationConfigsResponse.configs` has no default (`Responses.kt:32-36`). In proto it is a plain `repeated` field (`a2a.proto:806-811`), and Python serializes with `MessageToDict` without printing defaults (`jsonrpc_dispatcher.py:467-479`). An empty list therefore arrives as `{}` and fails to decode on the Koog client.
- **`return_immediately` / `history_length`:** passed through `SendMessageConfiguration.returnImmediately` / `historyLength`, `GetTaskRequest.historyLength`, and `ListTasksRequest.historyLength` (`Requests.kt:42-87`). The integration test uses `returnImmediately = true` (`a2a/a2a-test/src/jvmMain/kotlin/ai/koog/a2a/test/BaseA2AProtocolTest.kt:302-304`).
- **Signatures:** the model has `signatures` (`AgentCard.kt:73`), but **no verification** exists, and there is no verifier hook (Python: `signature_verifier`).

### 7. Server transport

- **Routes.** `Route.a2aJsonRpcTransportRoute(path, transport)` is a single POST endpoint, requires the SSE plugin, and serves both plain and streaming responses (`Routes.kt:67-109`). `a2aAgentCardRoute` serves GET at a path, with no caching headers (`:114-125`). The standalone `HttpJSONRPCServerTransport.start` defaults the card path to the well-known path and installs CORS `anyHost` (`HttpJSONRPCServerTransport.kt:67-97`).
- **Dispatch.** All 11 operations reach `RequestHandler` (`JSONRPCServerTransport.kt:112-141`, `:162-167`). An unknown method returns `-32601` with the request id (`:325-331`). Unparseable params return `-32602` (`:212-221`, `:333-347`). Empty param names return `-32602`, as a TCK accommodation (`:339-343`).
- **Out of scope, but it blocks end-to-end use:** `A2AServer` still implements the old handler signatures (`Request<TaskQueryParams>`, `Response<...>`, `a2a/a2a-server/src/commonMain/kotlin/ai/koog/a2a/server/A2AServer.kt:489-508`), and `TaskQueryParams` no longer exists in a2a-core. The server transport can currently only be exercised with mock handlers.

### 8. "test subscribe doesn't work for now" (commit `73f71807d`)

That commit only renamed `blocking` → `returnImmediately`, updated `AuthenticationInfo.scheme` and `nextPageToken`, and left the subscribe test as it was. The next commit, `24e5aebd5` ("fix client integration test"), made two changes. Reading the code, both match the failure:

1. **The old assertion contradicted v1.** It required every subscribe event to be a `TaskStatusUpdateEvent`. In v1 the first event MUST be a `Task` (§3.1.6 `:311`), and Python v1.1.0 yields it (`active_task.py:614-620`). The fix relaxed the assertion to "all events share the taskId/contextId; some status update is WORKING" (`BaseA2AProtocolTest.kt:315-341`).
2. **The stream never ended.** The Python test agent's `do long-running task` finished with the task still WORKING. In `DefaultRequestHandlerV2` the producer then waits for further requests (`active_task.py:493-516`), so the tapped subscriber queue never shuts down. `toList()` on the Koog side would hang until the 10 s `testTimeout` (`A2AClientJsonRpcIntegrationTest.kt:30`). The fix enqueues a COMPLETED status update (`a2a/test-python-a2a-server/src/agent_executor.py:128-141`).

One residual race remains. If the subscribe call arrives after the agent has completed (about 0.8 s, `agent_executor.py:109-110`), Python v1.1.0 returns a JSON `-32602` error rather than SSE. The Koog client would then fail with `SSEClientException` (§4 above). Nothing was run, so this explanation is inferred from code.

### Leftover v0.3 artifacts

In scope:
- `A2AMethod.GetAuthenticatedExtendedAgentCard` and `A2AMethod.ListTaskPushNotificationConfig` constant names (`A2AMethod.kt:10`, `:19`).
- The error message "authenticated extended agent card" (`A2AClient.kt:221`).
- A KDoc link to `v0.3.0/#77-taskspushnotificationconfiglist` (`A2AClient.kt:172`).
- KDoc in `Routes.kt:51-58` uses a non-existent name and the wrong argument order (`a2aJsonRPCTransportRoute(firstTransport, "/agent-1")`).
- Test fixtures use the v0.3-style `AgentCard.security` (`HttpJSONRPCServerTransportTest.kt:77-80`) and patch versions `protocolVersion = "1.0.1"` (`HttpJSONRPCServerTransportTest.kt:70`, `HttpJSONRPCClientTransportTest.kt:110`); §3.6 `:708` says SHOULD NOT.

Adjacent, in a2a-core and other modules:
- The same v0.3 KDoc links in `ClientTransport.kt:126`, `ServerTransport.kt:137`, and `A2APaths.kt:10`.
- The -32007 naming (`Exceptions.kt:22`, `:173-176`).
- `TransportProtocol.HTTP_JSON_REST = "HTTP+JSON/REST"` (`AgentCard.kt:94`).
- `AgentCard.security: List<SecurityRequirement>` (`AgentCard.kt:72`, `:189`) where v1 has `securityRequirements` (`a2a.proto:383`).
- The TCK SUT advertises `protocolVersion = "0.3.0"` (`a2a/test-tck/a2a-test-server-tck/src/main/kotlin/ai/koog/a2a/test/tck/Main.kt:51`); so does `a2a/a2a-server/src/jvmTest/.../BaseA2AServerJsonRpcTest.kt:102,139`.
- `A2AServer` still uses the v0.3 handler API.

## Testability notes

**Existing coverage:**
- `JsonRpcSerializationTest` (8 tests): RequestId string/number, error object, request, notification (with/without params), success, error, and error-without-id. The last one asserts that `id` is omitted, which is the JSON-RPC 2.0 violation.
- `HttpJSONRPCClientTransportTest` (MockEngine):
  - Non-streaming methods are checked for method name, params round-trip, and result decoding.
  - One error test uses a well-formed ErrorInfo array (`:368-421`).
  - Streaming and subscribe are `@Ignore`d: "MockEngine doesn't support SSE" (`:163-167`, `:278-282`).
  - No headers are asserted, and nothing covers absent or string `data`.
- `HttpJSONRPCServerTransportTest` (Ktor `testApplication`):
  - All non-streaming ops, plus streaming SendStreamingMessage and SubscribeToTask through SSE.
  - Method-not-found and invalid JSON.
  - Nothing covers errors inside streams, version headers, missing params, `data` contents, or `id: null`.
- `A2AClientJsonRpcIntegrationTest` + `TestA2AServerContainer`: runs `BaseA2AProtocolTest` against the Python v1.1.0 container. Only happy paths and TaskNotFound (ErrorInfo-bearing) errors are exercised.

**Suggested Koog tests (no Docker):**
- Client: `toResponse` with `data` absent, a string, an object, and ErrorInfo with non-string metadata. Each should still yield the typed `A2AException`.
- Client streaming against a Ktor `testApplication` server rather than MockEngine. This sidesteps the SSE limitation.
  - One case returns a plain `application/json` error to a streaming call; expect `A2ATaskNotFoundException` / `A2AUnsupportedOperationException`.
  - One case sends an in-stream error event.
- `A2AClient` sends `A2A-Version: 1.0` (MockEngine header assertion), does not duplicate a lowercase key, and checks streaming capability for `subscribeToTask`.
- Server:
  - a missing `A2A-Version` → -32009 (or whatever the chosen policy is);
  - `A2A-Version: 99.0` → -32009;
  - `1.0` and `1.1` accepted per policy;
  - missing `params` accepted for `GetExtendedAgentCard`;
  - an unknown id → `"id": null` present;
  - an A2A error → `data[0]` is ErrorInfo with reason and domain;
  - a handler flow that throws before the first emit → plain JSON response, not SSE.
- Interface selection and tenant: unit tests over an `AgentCard` with multiple `supportedInterfaces`, once a selection API exists.

**TCK coverage** (pinned `263b9cf` = research checkout):

| Area | TCK requirement / test | Expected Koog result |
|------|------------------------|----------------------|
| Version | VER-SERVER-002 `test_unsupported_version_returns_error_jsonrpc` (`tests/compatibility/core_operations/test_error_handling.py:550-574`) and `test_version_not_supported_error` (`tests/compatibility/jsonrpc/test_error_codes.py:252-310`) | Fail: no validation |
| Version | VER-SERVER-003 (`test_error_handling.py:603-630`) only requires "result or error" | Passes trivially |
| ErrorInfo | JSONRPC-ERR-003 (`tck/requirements/binding_jsonrpc.py:110-123`, tests `tests/compatibility/jsonrpc/test_error_info.py:65-180`) | `test_error_data_is_array` passes; `test_data_contains_error_info`, `test_error_info_valid`, `test_error_info_reason_matches_condition` fail |
| Streaming errors | STREAM-SUB-004 (`test_sse_streaming.py:177-209`) | Fails if TaskNotFound is raised lazily inside the flow |
| Subscribe first event | `test_subscribe_first_event_is_task` (`:211`) | Depends on A2AServer (not migrated) |
| Headers | JSONRPC-SVC-002, JSONRPC-SSE-001 (`test_transport_behavior.py:157`, `:205`) | Not examined further |

**Untested by the TCK:**
- Missing `params`: the TCK always sends `{}` (`tck/transport/jsonrpc_client.py:274-276`).
- `id: null`.
- All client-side behavior: client version header, interface selection, tenant, signatures. The TCK tests servers only.

TCK CI is currently disabled (commit `3e7cd2d88`).

## Discrepancies

1. **ErrorInfo tier.**
   - Spec §9.5 (`:2455`) makes ErrorInfo in JSON-RPC `data` a **SHOULD**. Only gRPC and REST say MUST (`:2689`, `:2913`).
   - TCK JSONRPC-ERR-003 is **MUST** (`binding_jsonrpc.py:110-123`). This is a TCK discrepancy.
   - Python always emits ErrorInfo for `A2AError`s, but not for its own `jsonrpc_models` errors (-32601, -32700, and in v1.1.0 also -32600/-32602 with string `data`). That arguably violates the spec's MUST that `data` be an array of `@type` objects (`:2437`, `:2455`).
2. **Version leniency.**
   - Spec §3.6.2 (`:737`) says process the "matching `Major.Minor`".
   - Python accepts any same-major version (`version_validator.py:80-90`).
   - The TCK only probes `99.0` and empty.
3. **Subscribe to a terminal task.**
   - Spec §9.4.6 (`:2408`): `UnsupportedOperationError` (-32004).
   - Pinned Python v1.1.0: `InvalidParamsError` (-32602) (`active_task.py:599-602`, `:462-465`).
   - Python `main`: fixed (`active_task.py:500-503`).
4. **Pre-stream error format.** The spec does not say whether errors on streaming methods arrive as plain JSON or inside SSE. Python (eager first event) and the TCK client (`text/event-stream` ⇒ success) both effectively require plain JSON for upfront errors. This is an implicit requirement Koog does not meet on the server or handle on the client.
5. **Agent card path joining.** Spec §8.2 gives the origin-root form. Python appends the path to `base_url` including its path; Koog/Ktor resolves to the origin root. These differ for base URLs that have a path.
6. **Version-error example.** Spec §6.4 (`:1442-1477`) shows a REST `application/problem+json` version error with `supportedVersions`. That conflicts with the REST error format in §11.6 and is not applicable to JSON-RPC. It is noted only so implementers don't copy it.

## Open questions

- **Server version policy** (architecture decision for the user): strict `1.0` only, or same-major like Python? Should a missing header be rejected (spec: interpreted as 0.3 → unsupported unless Koog adds 0.3 compat), like Python?
- **Client-side interface selection / ClientFactory and automatic tenant injection.** This needs an API design decision. The JSON-RPC-only scope means "select first `JSONRPC` interface with `protocolVersion` 1.x".
- **`A2AServer` migration** to the new `RequestHandler` signatures. It does not compile against a2a-core as read, and it blocks server-side end-to-end and TCK verification. Whether its streaming handlers should fail eagerly before the first emit is tied to discrepancy 4.
- **a2a-core model issues seen in passing:**
  - `ListTaskPushNotificationConfigsResponse.configs` needs a default so `{}` decodes.
  - `AgentCard.security` → `securityRequirements`.
  - `TransportProtocol.HTTP_JSON_REST` value.
  - -32007 naming.
  - Adding -32008.
- Whether Ktor emits multi-value `ctx.headers` entries as one comma-joined header (R4 SHOULD). Unverified.
- Agent Card signature verification: whether Koog should offer a verifier hook like Python's `signature_verifier`, and which JWS/JCS libraries are acceptable for KMP.
