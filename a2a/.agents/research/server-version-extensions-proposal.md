# Server-side `A2A-Version` validation and `A2A-Extensions` handling (plan item 3.7)

**Sources checked:** A2A spec repo `a2a-protocol` @ `v1.0.1` (baseline; `main` = `7c861a5`, v1.0.1-82, no relevant change in §3.2.6/§3.3.4/§3.6/§5.4/§9.2); a2a-python @ `a4c4fc9` (v1.2.2-13) plus `v1.1.0` (the version pinned in `a2a/test-python-a2a-server/pyproject.toml:7`); a2a-tck @ `263b9cf` (same commit as `a2a/test-tck/setup_tck.sh:9`); Koog working tree on `eugenethedev/a2a-1.0` @ `887b947e6`; Ktor 3.3.3 `ktor-utils` sources.
**Confidence:** high on the spec, Python and TCK facts; medium on the recommendations. They are design choices, and the spec leaves the extension check's scope and the response echo open.

Spec line numbers below are for `git show v1.0.1:docs/specification.md`.

## Answer

- **Missing or empty `A2A-Version`.** The spec says it MUST be read as `0.3` (§3.6.2). Koog supports only `1.0`, so a missing header means an unsupported version, which MUST produce `VersionNotSupportedError`. On JSON-RPC that is code `-32009`. The Python SDK does the same when v0.3 compat is off.
- **The TCK accepts either behavior.** VER-SERVER-003 passes for any well-formed result or error. Every other TCK request sends `A2A-Version: 1.0`. So rejecting a missing header is spec-correct and passes the TCK.
- **Where the version check goes.** Put it in `JSONRPCServerTransport`, not `A2AServer`. Version is a per-interface concern ("not supported by the interface", §3.6.2). The transport is also the only place that knows the JSON-RPC `id` before dispatch.
- **Where the extensions check goes.** Parse `A2A-Extensions` once into `ServerCallContext`. The ExtensionSupportRequired check (`-32008`, a MUST in §3.3.4) needs the AgentCard, so it belongs in `A2AServer`.
  - Python declares the error but never raises it.
  - The TCK tests it (CORE-CAP-004) only if the card declares `urn:a2a:tck:required-extension` as required. Otherwise the test is skipped.
- **Echoing activated extensions.** Do not echo them in a response header for now. The normative spec does not require it. Only the explanatory topic doc says SHOULD, and Python deliberately removed the "activation" echo.

## Requirements

| # | Requirement | Tier | Bindings | Citation |
|---|---|---|---|---|
| V1 | Server processes the request with the semantics of the requested `A2A-Version`, matching `Major.Minor`. An unsupported version returns `VersionNotSupportedError`. | MUST | all | spec §3.6.2 L737 |
| V2 | An empty value is interpreted as `0.3`. | MUST | all | spec §3.6.2 L739 |
| V3 | The patch component is ignored when negotiating. | MUST | all | spec §3.6 L708 |
| V4 | `VersionNotSupportedError` maps to JSON-RPC `-32009`, gRPC `FAILED_PRECONDITION`, HTTP 400. | MUST | all | spec §5.4 L1190 |
| V5 | A client MAY send `A2A-Version` as a request parameter instead of a header. | MAY (client) | spec example is REST `GET ?A2A-Version=` | spec §3.6.1 L724-731 |
| V6 | JSON-RPC service parameters MUST be sent as HTTP headers, and their names are case-insensitive. | MUST | JSON-RPC | spec §9.2 L2242-2248 |
| E1 | `A2A-Extensions` is a comma-separated list of URIs. Multiple values SHOULD be comma-joined into one header. | definition; SHOULD | all | spec §3.2.6 L485; §9.2 L2248 |
| E2 | If the card marks an extension `required: true` and the client did not declare it, the server returns `ExtensionSupportRequiredError`. | MUST, but only if the card declares a required extension | all | spec §3.3.4 L576; §3.3.2 L562 |
| E3 | `ExtensionSupportRequiredError` maps to JSON-RPC `-32008`. | MUST | all | spec §5.4 L1189 |
| E4 | Clients opt in through binding-specific mechanisms: HTTP headers, gRPC metadata, or JSON-RPC request params. | descriptive | all | spec §4.6.1 L1051 |
| E5 | Requested extensions the agent does not support can be ignored. A version mismatch SHOULD be ignored, not rejected. | explanatory (topic doc) | all | `docs/topics/extensions.md` @v1.0.1 L166-167, L234 |
| E6 | The response SHOULD include an `A2A-Extensions` header listing the activated extensions. | explanatory only, not in the normative spec | HTTP | `docs/topics/extensions.md` @v1.0.1 L169 |
| E7 | Error `data` SHOULD carry a `google.rpc.ErrorInfo`. | SHOULD | JSON-RPC | spec §9.5 L2455 |

All rows apply to Koog, since Koog's only binding is JSON-RPC over HTTP. V5 conflicts with V6 for JSON-RPC; see Discrepancies.

## Details

### Python SDK (reference behavior, also in v1.1.0)

**Parsing.**
- `DefaultServerCallContextBuilder.build` copies all headers into `state['headers']`.
- It sets `requested_extensions = get_requested_extensions(request.headers.getlist('A2A-Extensions'))` (`src/a2a/server/routes/common.py:113-122`).
- The parser takes every header value, splits on `,`, trims, drops empty entries and returns a `set` (`src/a2a/extensions/common.py:7-18`).
- The set lives on `ServerCallContext.requested_extensions: set[str]` (`src/a2a/server/context.py:25`). It is surfaced to executors as `RequestContext.requested_extensions` (`src/a2a/server/agent_execution/context.py:164-166`).

**Version check.**
- The `@validate_version('1.0')` decorator sits on the dispatcher's `_process_streaming_request` and `_process_non_streaming_request` (`src/a2a/server/routes/jsonrpc_dispatcher.py:353,504`). It is a transport/dispatcher-layer check, not a handler-layer one.
- A missing or empty header becomes `'0.3'` (`src/a2a/utils/version_validator.py:68-78`).
- Compatibility compares **major only** (`version_validator.py:80-90`). So `1.1` and `1.9` are accepted and `0.3` and `2.0` are rejected.
- The header is read only, never a query parameter.
- Order of errors: JSON parse, then the envelope `-32600`, then v0.3 compat routing by method name (only if `enable_v0_3_compat`, `jsonrpc_dispatcher.py:277-287`), then `-32601`, then params `-32602`, then the version check `-32009`.
- Covered by the unit test for a missing header (`tests/utils/test_version_validation.py:61-72`) and the integration test sending `2.0.0` (`tests/integration/test_client_server_integration.py:1186-1197`).

**Extensions.**
- `ExtensionSupportRequiredError` is declared and mapped to `-32008` (`src/a2a/utils/errors.py:103,145`). Nothing in `src/` raises it, so there is no required-extension check.
- No response header echo. Commit `b8df210` ("remove 'activation' concept", #984, first released in v1.0.0) states that the 1.0 spec has no activation concept and that used extensions go in `message.extensions` / `artifact.extensions`.

**Client side.** The Python client always sets `A2A-Version: 1.0` (`src/a2a/client/client_factory.py:87-90`).

### TCK

- **VER-SERVER-002 (MUST).** Sends `SendMessage` with `A2A-Version: 99.0` and expects `-32009` (`tests/compatibility/core_operations/test_error_handling.py:556-578`; also `tests/compatibility/jsonrpc/test_error_codes.py:257-290`, requirement JSONRPC-SSE-002).
- **VER-SERVER-003 (MUST).** Sends `A2A-Version: ""` and passes if the body has `result` **or** `error` (`test_error_handling.py:607-629`). Rejecting the request is accepted.
- **All other requests send `A2A-Version: 1.0`** (`tck/transport/_helpers.py:13-14`, `tck/transport/jsonrpc_client.py:84`, `test_error_handling.py:106`). Rejecting a missing header therefore breaks no TCK test.
- **JSONRPC-SVC-002.** Sends unknown `A2A-Extensions: https://example.com/ext/v1,https://example.com/ext/v2` with `SendMessage`. It fails only on HTTP status ≥ 500 (`tests/compatibility/core_operations/test_transport_behavior.py:163-197`). Unknown requested extensions must be tolerated.
- **CORE-CAP-004 (MUST).** Skipped unless the card has `capabilities.extensions[{uri: "urn:a2a:tck:required-extension", required: true}]`. If present, the test sends `SendMessage` with no `A2A-Extensions` and expects `-32008` (`test_error_handling.py:302-337`; requirement `tck/requirements/core_operations.py:508-521`). No scenario or SUT in the TCK declares this extension, so in practice it is always skipped.
- The JSON-RPC validator checks only `code`; it does not check `data` or `ErrorInfo` (`tck/validators/jsonrpc/error_validator.py:41+`).

### Wire shape (JSON-RPC)

Koog's `toErrorData` adds `ErrorInfo` automatically from `A2AException.reason` (`JSONRPCServerTransport.kt:338-346`).

```json
{"jsonrpc":"2.0","id":1,"error":{"code":-32009,"message":"A2A version '0.3' is not supported; supported: 1.0",
 "data":[{"@type":"type.googleapis.com/google.rpc.ErrorInfo","reason":"VERSION_NOT_SUPPORTED","domain":"a2a-protocol.org"}]}}
```

`-32008` has the same shape with reason `EXTENSION_SUPPORT_REQUIRED`. Optionally, put the missing URIs in `ErrorInfo.metadata`.

## Koog status

**Version utilities.**
- `normalizeVersion` maps blank to `"0.3"` and keeps `Major.Minor`. `isVersionCompatible` compares `Major.Minor` (`a2a-core/.../validation/VersionValidation.kt:9-21`).
- `A2AVersions` has `VERSION_0_3`, `VERSION_1_0` and `CURRENT_VERSION` (`a2a-core/.../consts/A2AVersions.kt:6-18`).
- **There is no `SUPPORTED_VERSIONS` constant** anywhere in the code, although `plan.md:177` says there is.
- Nothing calls `isVersionCompatible` or `normalizeVersion` outside tests.

**Exceptions.** `A2AExtensionSupportRequiredException` (`-32008`) and `A2AVersionNotSupportedException` (`-32009`) exist (`a2a-core/.../exceptions/Exceptions.kt:223-235`). Status file R11's "no -32008 exception" is out of date.

**`ServerCallContext`** carries only `headers: Map<String, List<String>>` and `state` (`a2a-core/.../transport/ServerTransport.kt:197-234`). There are no typed version or extension fields.

**Case-sensitivity gap.**
- `Routes.kt:80-83` builds `headers = call.request.headers.toMap()`.
- Ktor's `StringValues.toMap()` returns a plain `LinkedHashMap` keyed by the names as received (`ktor-utils 3.3.3 StringValues.kt:373-374`; `CaseInsensitiveMap.entries` yields `key.content`).
- So `ctx.headers["A2A-Version"]` misses `a2a-version`, which is always lowercase over HTTP/2. This violates V6 for any lookup done on `ctx.headers`.

**`JSONRPCServerTransport`.**
- `handleRequest` goes JSON parse, then envelope and method (`parseJSONRPCRequest`), then dispatch. Params are deserialized inside dispatch (`JSONRPCServerTransport.kt:73-110, 228-248`).
- The transport has no AgentCard; its only constructor parameter is `requestHandler` (`:52-54`).
- There is no version or extension handling.

**`A2AServer`.**
- Holds `agentCard` (`A2AServer.kt:323-333`).
- Already does the capability checks: extended card (`:361`), streaming (`:640-644`), push (`:646-656`).
- Has no extension check.
- Executors can reach `ctx` through `RequestContext.callContext` (`A2AServer.kt:412`; `session/RequestContext.kt:26`).

**Client.** It sends `A2A-Version: 1.0` unless the caller set it (`a2a-client/.../A2AClient.kt:228-237`). That check uses `!in ctx.headers` and is case-sensitive: a caller-provided `a2a-version` would produce a duplicate header. This is minor and outside 3.7.

**TCK SUT.** `test-tck/a2a-test-server-tck/.../Main.kt:50-65` still has `protocolVersion = "0.3.0"` at card level, and its `AgentInterface` has no `protocolVersion` (now required, `AgentCard.kt:120-125`). It declares no extensions. This is Phase 4, out of scope here.

**Existing tests affected by rejecting a missing header.**
- `HttpJSONRPCServerTransportTest` posts raw requests without `A2A-Version` (helpers at `:241`, `:285`), so they would need the header added.
- `A2AServerJsonRpcIntegrationTest` and `BaseA2AServerJsonRpcTest` use the Koog client, which sends the header, so they are unaffected.

## Decisions

### (a) Missing or empty header: reject or accept?

1. **Reject (recommended).** Treat it as `0.3`, which is not in the supported set, and return `-32009`.
   - Literal spec reading (V2 together with V1). Same as the Python default. Passes VER-SERVER-003.
   - Accepting buys almost nothing. Real 0.3 clients use different method names (`message/send`), so without a 0.3 compat layer they get an error anyway, just a less helpful `-32601`.
2. **Accept as `1.0` (lenient).** Friendlier to curl or ad-hoc callers, but breaks V2: a request "interpreted as 0.3" would be served with 1.0 semantics.
3. **Configurable.** Reject by default and let the server owner add `"0.3"` to the supported set.
   - This falls out of option 1 at no cost if the supported set is a constructor parameter.
   - It does **not** make 0.3 requests work, because Koog has no 0.3 method or body handling. Document that.

**Recommendation: 1, with a configurable supported set (as in 3).** For query parameters: read only the header for JSON-RPC (V6). Optionally fall back to `?A2A-Version=` in `Routes.kt` if the header is absent; this costs nothing and nothing tests it. Python does not do this. Your call.

### (b) Where the check lives

1. **Transport (recommended).**
   - Add `supportedVersions: Set<String> = setOf(A2AVersions.VERSION_1_0)` to `JSONRPCServerTransport` and check in `handleRequest` right after `parseJSONRPCRequest`, before dispatch.
   - Resulting error order: `-32700`, `-32600`, `-32601`, `-32009`, then `-32602`. That is Python's order except that `-32009` comes before `-32602`; no test depends on the difference.
   - The `id` is known, so the error is well-formed. Streaming requests get a plain JSON error because the stream has not started yet, consistent with decision 3.17.
   - It applies to every `RequestHandler`, not only `A2AServer`. It matches the spec's "per interface" wording, and a future REST transport would reuse the same core helper.
2. **`A2AServer`.** Add a `checkVersion(ctx)` to each of the 11 handler methods.
   - Custom `RequestHandler`s would skip it, and the error would come after params deserialization.
   - One `A2AServer` behind two interfaces with different versions (allowed by §3.6.2 L741) could not tell them apart.
3. **Both, split by concern.** HTTP-specific parsing (headers, case-insensitivity, optional query fallback) goes in `Routes.kt`. Version validation goes in `JSONRPCServerTransport`. Card-dependent checks (extensions) go in `A2AServer`.

**Recommendation: 3, which is option 1 for the version check plus the extension check in `A2AServer`.**
- Put a binding-agnostic helper in a2a-core next to `isVersionCompatible`, e.g. `fun requireSupportedVersion(requested: String?, supported: Set<String>)` that throws `A2AVersionNotSupportedException`.
- Keep the transport hook `protected open` so users can override it.

### (c) Shape of parsed version and extensions in `ServerCallContext`

1. **Typed properties with defaults derived from headers (recommended).**
   - `requestedVersion: String` holds the normalized `Major.Minor`, `"0.3"` when absent.
   - `requestedExtensions: Set<String>` holds trimmed, non-empty URIs from all `A2A-Extensions` values split on commas.
   - Both are constructor parameters whose defaults are computed from `headers` with a **case-insensitive** lookup, and both are added to `copy(...)`.
   - Source-compatible (new trailing parameters with defaults). Mirrors Python's `requested_extensions: set[str]`. A query-param fallback can be passed explicitly by `Routes.kt`.
2. **Well-known `StateKey`s** (e.g. `A2AStateKeys.REQUESTED_EXTENSIONS`). Needs no API change, but the values are untyped-ish, easy to forget to populate, and not discoverable.
3. **Extension functions over raw headers** (`ctx.requestedExtensions()`). No state to keep in sync, but the values are re-parsed on every call and there is no hook for a query fallback.

**Recommendation: 1.** Also normalize `headers` in `Routes.kt`, either by lowercasing keys or by using a case-insensitive map, so user code that reads `ctx.headers` gets V6-correct behavior.

Optionally add `RequestContext.requestedExtensions` as a convenience delegating to `callContext`.

### (d) ExtensionSupportRequired check

1. **Automatic check in `A2AServer` on every operation (recommended).**
   - Add `protected open fun checkRequiredExtensions(ctx)`.
   - It computes `required = agentCard.capabilities.extensions.orEmpty().filter { it.required == true }.mapNotNull { it.uri }` and `missing = required - ctx.requestedExtensions`.
   - If `missing` is non-empty, it throws `A2AExtensionSupportRequiredException` naming the missing URIs, optionally also in `ErrorInfo.metadata`.
   - Call it first in every `RequestHandler` method, before the capability checks. For streaming, throw it inside the flow before the first emit so the transport replies with plain JSON.
   - The spec does not limit it to particular operations (§3.3.4). It is a no-op unless the card declares a required extension. It passes CORE-CAP-004 if the SUT opts in.
2. **Message operations only** (`SendMessage`, `SendStreamingMessage`). Matches the only TCK case and the "impacts how requests are structured" rationale in the topic doc, but the spec text does not limit the check this way.
3. **No automatic check (Python parity).** Expose `requestedExtensions` and leave enforcement to the `AgentExecutor`. This is non-conformant with a MUST whenever a card declares `required: true`.

**Recommendation: 1.** "Declared" means present in `A2A-Extensions` (E4/V6) only. `Message.extensions` is not counted, because it describes message content, not client support.

Unknown or unsupported requested URIs are ignored and never cause an error (E5, JSONRPC-SVC-002).

Validate against the public `agentCard`; see the open question on the extended card.

### (e) Echo activated extensions in a response `A2A-Extensions` header

1. **Do not echo (recommended for now).** The normative spec is silent. Python removed the echo on purpose. No TCK test checks it.
2. **Echo the requested extensions that the card also declares,** set in `Routes.kt` before responding.
   - Cheap, and covers the topic doc's SHOULD.
   - But it claims "activated" without the executor's say. It also needs the card or a `supportedExtensions` set in the route or transport.
3. **Executor-controlled activation set** in the context, written as a response header.
   - Does not work for SSE: headers are committed before the executor decides, because the transport sends them on the first event.
   - Would need a new response-metadata channel through `RequestHandler`. That is too much API for an explanatory SHOULD.

**Recommendation: 1.** Revisit as option 2 if the normative spec adopts the echo.

## Testability notes

**Unit tests (`a2a-core`).**
- Header parsing: multiple values, commas, whitespace, empty entries, case-insensitive name.
- `requireSupportedVersion`: `""`/null → 0.3 → rejected; `1.0` and `1.0.3` accepted; `1.1`, `2.0`, `99.0` rejected; `0.3` accepted when configured.

**Transport tests (`HttpJSONRPCServerTransportTest`).**
- Header `99.0` → `-32009` with the request `id` and an `ErrorInfo` with reason `VERSION_NOT_SUPPORTED`.
- Missing header → `-32009`.
- Lowercase `a2a-version: 1.0` → success.
- Streaming request with a bad version → plain JSON error, not SSE.
- Existing raw-post helpers (`:241`, `:285`) must add `A2A-Version: 1.0`.

**Server tests (`A2AServerJsonRpcIntegrationTest`).**
- With a card declaring `required: true` for `urn:x`: calls without `A2A-Extensions` → `-32008`; calls with `urn:x` → success.
- Unknown requested extensions → success.
- Assert that `RequestContext.callContext.requestedExtensions` reaches the executor.

**TCK.**
- VER-SERVER-002 (core and `jsonrpc/test_error_codes.py`) and VER-SERVER-003 both pass under recommendation (a)1.
- CORE-CAP-004 runs only if the Koog SUT card declares `urn:a2a:tck:required-extension` with `required: true`. Doing so would make every TCK request without that header fail with `-32008`, and the TCK's own client never sends it. **Do not add it to the SUT.** Cover `-32008` with Koog's own tests instead; this is a TCK design limitation.

**Not covered by the TCK.** The query-param fallback, header case-insensitivity, and response echo are not tested.

## Discrepancies

1. **Version compatibility granularity.** The spec matches `Major.Minor` (V1). Python matches **major only** (`version_validator.py:80-90`) and accepts `1.1`. Koog's `isVersionCompatible` follows the spec. Koog will reject `1.1` clients that Python accepts.
2. **Query parameter vs. JSON-RPC binding.** §3.6.1 L724 lets clients send `A2A-Version` as a request parameter. §9.2 L2242-2246 says JSON-RPC service parameters MUST be HTTP headers. Python reads only the header and the TCK does not test the parameter.
3. **Echo of activated extensions.** The topic doc (`docs/topics/extensions.md` @v1.0.1 L169) says SHOULD. The normative spec does not mention it. Python removed it (commit `b8df210`, #984), saying 1.0 has no activation concept.
4. **ExtensionSupportRequired.** The spec says MUST (§3.3.4 L576). Python never raises it. The TCK's CORE-CAP-004 is effectively unreachable for a general-purpose SUT.
5. **Off-topic for Koog.** The TCK maps `VersionNotSupportedError` to gRPC `UNIMPLEMENTED` (`tck/requirements/base.py:325-331`, `binding_grpc.py:113`). The spec says `FAILED_PRECONDITION` (§5.4 L1190). This is gRPC only.
6. **Plan out of date.** `plan.md:177` claims `SUPPORTED_VERSIONS = ["1.0"]` exists in a2a-core; it does not.

## Open questions

- Should required extensions also come from `agentCardExtended`, for authenticated callers? The spec only says "the Agent Card".
- Should `GetExtendedAgentCard` be exempt from the required-extension check, so clients can discover requirements first? The spec is silent; the recommendation is no exemption.
- The client's `prepareContext` header check is case-sensitive (`A2AClient.kt:231`). This is a client-side follow-up.
- The TCK SUT card is not v1-shaped (`Main.kt:50-62`). It belongs to Phase 4.
- Should `AgentInterface.protocolVersion` on the served card be derived from or validated against the transport's `supportedVersions`, to keep them consistent?
