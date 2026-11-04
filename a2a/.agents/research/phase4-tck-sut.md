# Phase 4: what the v1 TCK needs from the Koog SUT, which gaps block MUST, and how to slice the work

**Sources checked:**
- **A2A TCK** research checkout `/Users/eugene/Documents/JetBrains/projects/a2a-tck`:
  - `main` @ `d4d6fae` (2026-10-22), after `git pull --ff-only`;
  - the commit pinned by `a2a/test-tck/setup_tck.sh:9`, `263b9cf` (2026-09-01). That is also the `HEAD` of `a2a/test-tck/a2a-tck`. It is 3 commits behind `main`, so plan.md's "pin = current main" is stale.
  - `263b9cf..d4d6fae` changes only `tests/compatibility/_task_helpers.py` (#250), `pyproject.toml` (`requires-python >=3.11,<3.15`) and the TCK's own CI.
  - The TCK vendors spec `v1.0.0` @ `1736957` (`specification/version.json`).
- **A2A spec** `/Users/eugene/Documents/JetBrains/projects/a2a-protocol`: tag `v1.0.1` = `3303592`. `main` is now `4735f48` (there is also a `v1.1.0-rc` tag); it was not examined.
- **A2A Python SDK** `/Users/eugene/Documents/JetBrains/projects/a2a-python` @ `v1.3.0-11-g6ca8593`: one check only, how JSON-RPC params are parsed.
- **Koog** `eugenethedev/a2a-1.0` @ `44c45cb95`. The SUT inventory came from a delegated read-only sweep, and I spot-checked it.
- **Ktor** `ktor-server-core-jvm-3.3.3-sources.jar`: routing and trailing-slash handling.
- Nothing was built or run, neither the TCK, the SUT nor Gradle. I ran one httpx URL-merge check in the TCK venv (shown below).

**Confidence:** high for how the TCK drives the SUT and for the gap list; everything was traced in code. Medium on the final pass/fail tally, because nothing was run.

## Answer

The v1 TCK finds its transports from `/.well-known/agent-card.json` `supportedInterfaces`. With a JSONRPC-only card, the gRPC and HTTP+JSON tests are recorded as skipped. It drives the agent only through the `messageId` prefix, `tck-<name>-<session>`.

The compatibility tests really use only a few prefixes. Most of them only need the default behavior: **complete the task**.
- `complete-task`
- `input-required`
- `artifact-text|file|file-url|data`
- `message-response`
- `stream-001|002|003`
- `stream-ordering-001`

The rest of the scenarios in `scenarios/*.feature` are not used by any compatibility test.

Things the SUT can fix itself:
- **Endpoint path.** The TCK client always POSTs to `base_url + "/"`. With the current `/a2a` mount, every client call goes to `/a2a/`, and Ktor answers 404 there. Every JSON-RPC test would crash. Mount JSON-RPC at `/`.
- **Executor.** It must route by prefix and override `cancel`.
- **Streams must end.** Every streaming path must reach a terminal state. Koog's SSE heartbeats defeat the TCK's 30 s read timeout, so a stream that never ends hangs the run.

The one real **server/core gap**: the TCK's JSON-RPC client sends **snake_case** param names (`history_length`, `context_id`, `page_size`, `task_id`). Koog decodes only camelCase and ignores unknown keys.
- This fails **CORE-HIST-002 (MUST)**.
- It would fail **PUSH-CREATE-001 (MUST)** if the SUT declares push. This is the decisive input for 4.2.

Push, if it is not declared:
- the 10 PUSH-* MUST tests are skipped;
- CORE-CAP-001 runs and passes, because Koog answers -32003 before it validates the task id.

`SimplePushNotificationSender` exists. It sends `Authorization: <scheme> <credentials>`, which is exactly what PUSH-DELIVER-001 checks.

## Requirements

Scope: what the TCK checks on JSON-RPC, at the pinned commit (same on main unless noted). "Koog" means the expected result with a correct SUT and no server changes. It is a hypothesis until the suite is run.

| # | Requirement / test | Tier | Bindings | Citation (TCK unless noted) | Koog |
|---|---|---|---|---|---|
| 1 | Card at `{host}/.well-known/agent-card.json`; non-empty `supportedInterfaces`; first interface per binding wins | MUST (CARD-DISC-001, CARD-PROTO-001/002) | all | `tests/compatibility/conftest.py:92-161`, `agent_card/test_agent_card.py:82-180` | SUT |
| 2 | Card has `name, description, version, capabilities, skills, supportedInterfaces, defaultInputModes, defaultOutputModes`; schema-valid (`allow_additional=True`) | MUST (CARD-STRUCT-001) | all | `agent_card/test_agent_card.py:41-50,109-138` | SUT. `encodeDefaults=false` drops empty lists, so skills must be non-empty. |
| 3 | SendMessage returns schema-valid `SendMessageResponse`; contextId present; unique taskId; blocking/`returnImmediately` | MUST (CORE-SEND-001, EXECUTION-MODE-001/002, MULTI-001a/003) | all | `tck/requirements/core_operations.py:55-80,386-437,547-637`; runner `core_operations/test_requirements.py:149-190` | pass |
| 4 | Responses validate against `specification/a2a.json` with `additionalProperties:false` (no extra fields) | MUST (DM-*, STREAM-ORDER-001, JSONRPC-SSE-001) | JSON | `tck/validators/json_schema.py:255-307`; `core_operations/test_data_model.py:130-196` | pass if the models are ProtoJSON-clean |
| 5 | Artifacts: text "Generated text content"; file `raw` + `output.txt` + `text/plain`; url `https://example.com/output.txt`; data `{"key":"value","count":42}`; direct Message "Direct message response" | MUST (DM-ART-001, DM-MSG-001) | all | `core_operations/test_artifacts.py:79-280` | SUT |
| 6 | Timestamps ISO-8601 with `Z` (skipped if none are present) | MUST (DM-SERIAL-003) | JSON | `test_data_model.py:56-58,250-290` | SUT must set `TaskStatus.timestamp` |
| 7 | GetTask / CancelTask / Subscribe on an unknown id → -32001; Cancel on terminal → -32002; Send to terminal → error; taskId plus wrong contextId → error; unknown `message.taskId` → -32001 | MUST (CORE-GET-002, CANCEL-002/003, SEND-002, MULTI-004/006, STREAM-SUB-004) | all | `test_task_lifecycle.py:124-320`; `jsonrpc/test_sse_streaming.py:176-205` | pass (`TurnManager.kt:495-508`) |
| 8 | CancelTask on an input-required task returns that task | MUST (CORE-CANCEL-001) | all | `test_task_lifecycle.py:150-176` | SUT must override `cancel` (the default throws -32002, `AgentExecutor.kt:154-161`) |
| 9 | Streaming: SSE `text/event-stream`, every event a JSON-RPC envelope with `result` ∈ {task, message, statusUpdate, artifactUpdate}; state never regresses; the stream closes | MUST (CORE-STREAM-001..003, STREAM-ORDER-001, JSONRPC-SSE-001) | JSON-RPC | `jsonrpc/test_sse_streaming.py:43-150`; `test_stream_ordering.py:57-208` | SUT; comment heartbeats are ignored (`tck/transport/_helpers.py:22-33`) |
| 10 | Subscribe: first event is `task`; it ends at terminal (subscribe to input-required, then complete it with a follow-up); terminal → -32004; two concurrent subscribers get identical events; closing one doesn't affect the other | MUST (STREAM-SUB-001..003, STREAM-ORDER-002..004) | all | `test_task_lifecycle.py:362-462`; `test_multi_stream.py:85-180`; `jsonrpc/test_sse_streaming.py:207-258` | pass |
| 11 | Pre-stream errors as plain JSON (not SSE) | implied by STREAM-SUB-004 / CORE-CAP-002 | JSON-RPC | `tck/transport/jsonrpc_client.py:131-168` | pass (`JSONRPCServerTransport.kt:146-155`) |
| 12 | JSON-RPC errors: `{code,message,data?}`; code in -32001..-32099 or -32600..-32700; TaskNotFound = -32001; `data` is an array containing `google.rpc.ErrorInfo{domain:"a2a-protocol.org", reason:"TASK_NOT_FOUND"}` | MUST (JSONRPC-ERR-001..003, JSONRPC-SSE-002, CORE-ERR-001/002) | JSON-RPC | `core_operations/test_error_handling.py:648-755`; `jsonrpc/test_error_info.py`; `jsonrpc/test_error_codes.py:58-368` | pass (`JSONRPCServerTransport.kt:340-375`). The plan's earlier "ErrorInfo missing" is stale. |
| 13 | `A2A-Version: 99.0` → -32009; empty header → any well-formed result or error | MUST (VER-SERVER-002/003) | JSON-RPC | `test_error_handling.py:546-645`; `test_error_codes.py:208-248` | pass |
| 14 | **GetTask `historyLength` limit is honoured** | **MUST (CORE-HIST-002)** | all | `test_task_history.py:91-123`; client `jsonrpc_client.py:212` sends `history_length` | **FAIL (server/core)** |
| 15 | historyLength=0 omits history (GetTask / SendMessage); history in order; history content matches | SHOULD (CORE-HIST-001/003/005/006, xfail) | all | `test_task_history.py:56-88,128-170,236-320` | HIST-001 xfail (snake_case); HIST-005/006 xfail at the pin (see Discrepancies) |
| 16 | ListTasks: schema-valid `ListTasksResponse` | MUST (CORE-LIST-001..005) | all | `core_operations.py:241-330` (sample_input uses `context_id`, `page_size`, `include_artifacts`) | pass, by accident: the filters are dropped, and only the schema is checked |
| 17 | Push unsupported → -32003 for CreateTaskPushNotificationConfig | capability: pushNotifications=false, MUST (CORE-CAP-001) | all | `test_error_handling.py:219-264`; `test_error_codes.py:105-127` | pass (`A2AServer.kt:858-865,1047-1052`) |
| 18 | Push CRUD and delivery | capability: pushNotifications, MUST (PUSH-*) | all | `core_operations/test_push_notifications.py` | see Details §Push |
| 19 | Extended card: not declared → -32004 (CORE-CAP-003); declared and configured → card returned (CARD-EXT-001); declared and not configured → -32007 (CARD-EXT-002) | capability: extendedAgentCard, MUST | all | `test_error_handling.py:368-545`; `tck/validators/extended_card.py` | pass on either path (`A2AServer.kt:391-401`) |
| 20 | Required extension `urn:a2a:tck:required-extension` missing → -32008 | capability: declared required extension, MUST (CORE-CAP-004) | all | `test_error_handling.py:302-366` | **Don't declare it.** The TCK never sends `A2A-Extensions` on other calls, so every other request would get -32008. |
| 21 | Card `Cache-Control: max-age` and `ETag` | SHOULD (CARD-CACHE-001/002, xfail); `Last-Modified` MAY | HTTP | `agent_card/test_agent_card_caching.py:73-187` | xfail: `a2aAgentCardRoute` sets no headers (`Routes.kt:150-161`) |
| 22 | Ignore unknown fields | SHOULD (DM-SERIAL-005) | JSON | `test_data_model.py:293-379` | pass (`ignoreUnknownKeys`) |
| 23 | AUTH-*, BIND-EQUIV-*, CARD-SIGN-*, VER-CLIENT-*: no runnable test (`operation=None` / `not-automatable`) | n/a | n/a | `tck/transport/dispatch.py:136-148`; `test_requirements.py:79-86` | not tested |
| 24 | gRPC- and HTTP+JSON-specific tests | n/a for Koog | gRPC, HTTP+JSON | `_test_helpers.py:74-87`; `test_requirements.py:168-177` | skipped as "Transport not configured" |

How each level behaves:
- **MUST** tests fail hard.
- **SHOULD** tests call `pytest.xfail` on failure (`test_requirements.py:197-239`, plus inline in the history and data-model tests).
- **MAY** tests are skipped when the capability is undeclared.
- Any capability-tagged test is skipped and recorded as skipped when the card lacks the flag (`test_requirements.py:26-60`).
- The pytest exit code is non-zero only for MUST failures and crashes.

## Details

### How the TCK talks to the SUT (JSON-RPC)
- **Client.** `JsonRpcClient(url)` is an `httpx.Client(base_url=url, timeout=(5s, read 30s), headers={"A2A-Version":"1.0"})`. Every call is `POST "/"` relative to `base_url` (`tck/transport/jsonrpc_client.py:80-121,131-168`).
  - httpx appends `/` to `base_url` (checked in the TCK venv): `http://localhost:9999/a2a` → `http://localhost:9999/a2a/`, while `http://localhost:9999` → `http://localhost:9999/`.
  - Raw calls in the error/version tests `httpx.post(base_url)` exactly, with no slash (`test_error_handling.py:89-102`).
  - A non-JSON response, such as a 404 body, raises `JSONDecodeError`. That is not caught (`jsonrpc_client.py:104-121` catches only `httpx.HTTPError`), so the test crashes and is auto-recorded as a FAIL (`conftest.py:289-349`).
- **Ktor 3.3.3 behavior.** A request path with a trailing slash gets an extra `""` segment unless `IgnoreTrailingSlash` is installed (`RoutingResolveContext.kt:80-82`). `route("/a2a")` has no trailing-slash selector (`RoutingBuilder.kt:366-381`), so `POST /a2a/` → **404**. `route("/")` gets `TrailingSlashRouteSelector`, which matches `POST /` (`RouteSelector.kt:397-407`).
  - **SUT fix:** mount JSON-RPC at `/` and declare `url = "http://localhost:9999"`. This matches the Python SUT, `sut/a2a-python/sut_agent.py:197-201,237-240`.
  - Alternative: keep `/a2a` with `install(IgnoreTrailingSlash)` in a custom `embeddedServer`.
- **Params are snake_case** (`jsonrpc_client.py:212,226-235,252,257,267,272`; `tck/transport/dispatch.py:65-106`):
  - `GetTask{id, history_length}`;
  - `ListTasks{context_id, page_size, page_token, history_length, status_timestamp_after, include_artifacts}`;
  - push `{task_id, id, url, …}`.
  - `message` and `configuration` objects are camelCase (`returnImmediately`, `historyLength`, `taskPushNotificationConfig`).
- **Message ids.** `tck_id(name)` = `tck-{name}-{8 hex session}`, the same value for every call in a session (`tck/requirements/base.py:21-31`). The parametrized runner appends `-jsonrpc` (`dispatch.py:159-167`). Prefix matching must therefore use `startsWith`, and must check `tck-artifact-file-url` before `tck-artifact-file`.
- **Stream collection.** It mostly uses a 10 s wall clock (`_test_helpers.py:90-125`). But `test_sse_streaming.py:43-60` and `test_stream_ordering.py:57-69` use plain `list(response.events)`, which only ends when the stream closes or reads go idle for 30 s. Koog's `: heartbeat` comments every 15 s (`Routes.kt:84-89,135`) reset that read timeout, so **a stream that never closes hangs the run**.

### SUT behavior required, keyed by messageId prefix
Source: `scenarios/*.feature`, and the Python reference `sut/a2a-python/sut_agent.py:54-174`. Koog rules (`TurnEventProcessor.kt:20-30`, `AgentExecutor.kt:24-45`):
- a new task must start with a `Task` event;
- for an existing task, send only status/artifact updates;
- don't put the user message into history, because the server persists it, deduplicated by messageId;
- set `TaskStatus.timestamp`.

| Prefix | Events (new task: `Task(SUBMITTED)` first) | Used by compatibility tests |
|---|---|---|
| `tck-complete-task` | `COMPLETED` with status message (agent) "Hello from TCK". Also used as a follow-up on an input-required task. | SEND-001/002, EXEC-MODE, MULTI-*, GET-001, CANCEL-002, HIST-*, SUB-002/003, push CRUD |
| `tck-input-required` | `INPUT_REQUIRED`, then return. Also a follow-up (`-1`, `-2` suffixes on main). | CANCEL-001, MULTI-005, SUB-001/002, ORDER-002..004, PUSH-DELIVER |
| `tck-artifact-text` / `-file` / `-file-url` / `-data` | artifact (text / `FileBytesPart(raw="tck", filename="output.txt", mediaType="text/plain")` / `FileUrlPart(url="https://example.com/output.txt", same)` / `DataPart({"key":"value","count":42})`), then `COMPLETED` | DM-ART-001 |
| `tck-message-response` | `sendMessage(Message("Direct message response"))`, with no task | DM-MSG-001 |
| `tck-stream-001` / `-003` / `-ordering-001` / `-artifact-text` / `-artifact-file` | `WORKING` → artifact → `COMPLETED` | CORE-STREAM-001/003, STREAM-ORDER-001 |
| `tck-stream-002` | `COMPLETED` only. The scenario streams a task, despite the "Message-only" title. | CORE-STREAM-002 |
| `tck-stream-artifact-chunked` | `WORKING` → chunk "chunk-1 " (append) → "chunk-2" (append, lastChunk) → `COMPLETED` | none |
| `test-resubscribe-message-id` | `WORKING`, wait 4 s (2 × `TCK_STREAMING_TIMEOUT` 2 s), `COMPLETED` (`docs/SUT_REQUIREMENTS.md:5-19`) | none |
| `tck-reject-task` | Python raises `A2AError` (`sut_agent.py:139-140`); the Java emitter sets REJECTED (`codegen/java_emitter.py:195`) | none |
| **default** (e.g. `send-003`, `terminal`, `infer-ctx`, `mismatch`, `dm-test-001`, `sse-streaming-001`, `transport-test-001`, `ver-003`, `jsonrpc-svc-002-ext`) | `COMPLETED` with "Unhandled messageId prefix: …". **It must terminate**, including on SendStreamingMessage. | many |
| `cancel()` | `TaskStatusUpdateEvent(CANCELED)`, or return and let the server force it | CORE-CANCEL-001 |

### Agent card (v1) for the SUT
- `name`, `description`, `version = "1.0.0"`.
- `supportedInterfaces = [AgentInterface(url="http://localhost:9999", protocolBinding=JSONRPC, protocolVersion="1.0")]`.
- `capabilities = AgentCapabilities(streaming=true, pushNotifications=<4.2>, extendedAgentCard=<Q4>)`. No required extensions.
- `defaultInputModes = defaultOutputModes = ["text"]`.
- One skill (`id="tck"`, tags `["tck"]`).
- Remove the 0.3 `securitySchemes` / `security`. They are not enforced, and the TCK doesn't test them.
- Reference: `sut_agent.py:188-227` (Python declares `push_notifications=False` and no extended card).

### Push (decision 4.2)
- **Undeclared (Python reference choice):**
  - PUSH-CREATE-001/002, GET-001/002, LIST-001, DEL-001/002 and DELIVER-001/002/003 are all **skipped** (`test_push_notifications.py`, every test starts with a `pushNotifications` check).
  - CORE-CAP-001 and `test_push_notification_not_supported_error` run. Koog passes both: `storageIfPushNotificationSupported()` throws -32003 before the missing `task_id` is checked (`A2AServer.kt:858-865,1047-1052`).
  - Inline configs are ignored (`A2AServer.kt:571-579`).
- **Declared.** It needs `pushConfigStorage = InMemoryPushNotificationConfigStorage()` and `pushSender = SimplePushNotificationSender(HttpClient(CIO))` (`notifications/SimplePushNotificationSender.kt:38-104`). Without the sender, the RPCs return -32003 and the run fails.
- **What the delivery tests check:**
  - The TCK runs its own webhook receiver, `http.server` on `0.0.0.0:<random>`. It advertises `http://{--webhook-host, default localhost}:{port}/webhook` (`tck/webhook/server.py:37-51`; `conftest.py:70-78,181-193`).
  - Setup: a `tck-input-required` SendMessage with inline `configuration.taskPushNotificationConfig{id, url, authentication{scheme:"Bearer", credentials:"tck-test-token-…"}}`, then a `tck-complete-task` follow-up (`test_push_notifications.py:370-400`).
  - The test waits 15 s for the **first** POST (`:367`) and checks:
    - `Authorization == "Bearer <credentials>"` (DELIVER-001);
    - that the POST arrived (DELIVER-002);
    - that the body validates as `StreamResponse` (DELIVER-003).
  - `X-A2A-Notification-Token` is not checked.
  - Koog's sender matches all three. The URL is `http://localhost` and Koog only checks that it is not blank (`A2AServer.kt:971-975`).
- **CRUD tests** send `{"task_id": …}` in snake_case. Koog drops it: `TaskPushNotificationConfig.taskId` is optional (`model/TaskPushNotificationConfig.kt:21-39`), so Create returns **-32602 "Task id is required"** (`A2AServer.kt:866-867`). Results:
  - PUSH-CREATE-001 **FAILS (MUST)**;
  - CREATE-002, GET-001, LIST-001, DEL-001 and DEL-002 are skipped ("Create failed");
  - GET-002 passes by accident (any error).
- **After the snake_case fix (slice S3)**, all PUSH tests are expected to pass. Delete is idempotent (`InMemoryPushNotificationConfigStorage.kt:51-58`).

### TCK pin vs main
- The only behavior difference is #250: the follow-up history messages get unique ids (`-1`, `-2`), `tests/compatibility/_task_helpers.py:158-163`.
- At the pin, both follow-ups reuse the initial `tck-input-required-<S>` id. Koog deduplicates history by messageId (`a2a-server/.../tasks/TaskHistory.kt:14,22`), so both messages are dropped, and CORE-HIST-005/006 (SHOULD) xfail.
- Bump the pin to `d4d6fae`.

## Koog status

**SUT inventory** (`a2a/test-tck/a2a-test-server-tck`):

| File | State |
|---|---|
| `build.gradle.kts:1-24` | Keep. The `a2a-client` and client-transport deps are unused. |
| `src/main/resources/logback.xml` | Keep at INFO; the CI waits for Ktor's "Responding at" line. |
| `Module.md` | The blurb is wrong ("is a TCK"). Cosmetic. |
| `Main.kt` (114 lines) | Bootstrap salvageable (`:92-113`, `HttpJSONRPCServerTransport.start(Netty, 9999, "/a2a", wait=true, agentCard, A2APaths.AGENT_CARD_WELL_KNOWN_PATH)`). Card (`:25-89`) and path must change. |
| `TckAgentExecutor.kt` (229 lines) | Rewrite. It routes on lower-cased text, has 600 ms delays, sends INPUT_REQUIRED instead of COMPLETED, and turns every exception into FAILED. |

- Gradle include: `settings.gradle.kts:90`.
- There is no Dockerfile.

**Compile-error categories vs v1:**

`Main.kt`:
1. Removed `AgentCard` params: `protocolVersion` (`:51`), `url` (`:55`), `preferredTransport` (`:56`), `security` (`:78`) and `supportsAuthenticatedExtendedCard` (`:82`). The v1 constructor is at `a2a-core/.../model/AgentCard.kt:56-71`.
2. `AgentInterface` lacks the required `protocolVersion` (`Main.kt:58-61` vs `AgentCard.kt:120-125`).
3. `OAuthFlows` is gone (`:11,:31`); v1 has `OAuth2SecurityScheme(flows: OAuthFlow)` (`AgentCard.kt:270-304`).
4. `security` becomes `securityRequirements: List<SecurityRequirement>` (`AgentCard.kt:69,183-197`).

`TckAgentExecutor.kt`:
5. `TaskIdParams` was removed (`:9,:83`). The v1 signature is `cancel(RequestContext<CancelTaskRequest>, TurnEventProcessor, Deferred<Unit>?)` (`AgentExecutor.kt:154-158`).
6. `TaskStatusUpdateEvent(final=…)` was removed (`:76,:99,:134,:175,:195,:216` vs `model/Task.kt:45-50`).
7. Hypothesis: the `io.ktor.utils.io.CancellationException` import (`:17`) may not resolve. Use the kotlin one.

The rest compiles as is: Message/Part/Task/Artifact/TaskStatus constructors, `RequestContext` and `TurnEventProcessor` usage, and the `A2AServer` and `HttpJSONRPCServerTransport` calls.

**v1 API the SUT targets (final):**
- `A2AServer(agentExecutor, agentCard, agentCardExtended?, taskStorage, messageStorage, pushConfigStorage?, pushSender?, idGenerator, coroutineScope, streamDelivery = StreamDeliveryConfig.Evict())` (`A2AServer.kt:342-353`).
- `HttpJSONRPCServerTransport(requestHandler, supportedVersions = {"1.0"})` with `.start(engineFactory, port, path, wait, agentCard?, agentCardPath, heartbeatPeriod = 15.seconds, customizeCallContext)` (`HttpJSONRPCServerTransport.kt:48-111`).
- Or, for a custom `embeddedServer`: `Route.a2aJsonRpcTransportRoute(path, transport, heartbeatPeriod, customizeCallContext)` (needs the SSE plugin) plus `Route.a2aAgentCardRoute(path, card)` (`Routes.kt:96-161`).

**Gaps that would fail MUST tests** (re-verified at HEAD):

| Gap | Kind | Fails | Evidence |
|---|---|---|---|
| G1. JSON-RPC mounted at `/a2a`, but the TCK posts `/a2a/` → 404 → every client-based test crashes | **SUT** | essentially everything | `Main.kt:55-62,92-113`; `RoutingResolveContext.kt:80-82`; `jsonrpc_client.py:105-106` |
| G2. Executor routes on text, not the prefix; no COMPLETED path; `cancel` not overridden; streams must terminate | **SUT** | most CORE/DM/STREAM tests | `TckAgentExecutor.kt` (see above) |
| G3. **snake_case request params are silently dropped.** `JSONRPCJson { ignoreUnknownKeys = true }` (`a2a-transport-core-jsonrpc/.../serialization/Serialization.kt:35-39`); the DTOs have camelCase names only and no `@JsonNames` anywhere in `a2a/` (`model/Requests.kt:58-61,77-85,108-150`; `model/TaskPushNotificationConfig.kt:21-39`) | **SERVER/CORE CHANGE** (or a TCK fix upstream) | CORE-HIST-002 (MUST) always. PUSH-CREATE-001 (MUST) if push is declared. CORE-HIST-001 (SHOULD) xfail. | `jsonrpc_client.py:212,235,252-272`; `test_task_history.py:91-123` |
| G4. Card has no Cache-Control/ETag | server (optional) | SHOULD only (xfail) | `Routes.kt:150-161` |

Stale items from earlier reports (now fixed at HEAD):
- `koog-server-and-tests-status.md:288-291`: SSE pre-stream errors are now plain JSON (`JSONRPCServerTransport.kt:146-155`), and ErrorInfo is now emitted (`:360-375`).
- `:120`: `TaskPushNotificationConfig.taskId` is optional now.
- `:113`: CORE-CAP-003 now passes; the server returns -32004 when the extended card isn't declared (`A2AServer.kt:397-399`).

Side note, not a TCK issue: `A2AServer.kt:700-701` calls `validateHistoryLength(request.historyLength)` twice.

## Testability notes
- **The TCK is the test for the SUT.**
  - Verify G1 before the suite with `curl -s -X POST localhost:9999/ -H 'Content-Type: application/json' -H 'A2A-Version: 1.0' -d '{"jsonrpc":"2.0","id":1,"method":"GetTask","params":{"id":"x"}}'`. Expect -32001 with `data[0]."@type"` ErrorInfo.
  - Also POST once to `/` with a trailing-slash URL, to confirm the routing.
- **G3** can be unit-tested in `a2a-core` `RequestsSerializationTest` (decode `{"id":"t","history_length":1}` and `{"task_id":…}`) and in `HttpJSONRPCServerTransportTest`. TCK coverage: CORE-HIST-002, CORE-HIST-001, PUSH-*.
- **Untestable or untested by the TCK on our side:**
  - gRPC and HTTP+JSON;
  - the AUTH-* and BIND-EQUIV-* requirements;
  - the `tck-reject-task`, chunked, file-stream and resubscribe scenarios;
  - the CORE-LIST filters (schema only);
  - CORE-SEND-003 (it expects success);
  - CORE-CAP-004.
- **Running locally.** Docker is not needed; the SUT is a JVM process and the TCK runs under uv. Python 3.11–3.14 works.
  ```bash
  cd a2a/test-tck
  ./setup_tck.sh                                   # clone/checkout the pin into a2a-tck/, uv sync
  ./run_sut.sh > /tmp/sut.log 2>&1 &               # ../../gradlew :a2a:test-tck:a2a-test-server-tck:run, port 9999
  until grep -q "Responding at" /tmp/sut.log; do sleep 1; done
  curl -s localhost:9999/.well-known/agent-card.json | jq .
  ./run_tck.sh --sut-host http://localhost:9999 --transport jsonrpc --level must -v
  ./run_tck.sh --sut-host http://localhost:9999 --transport jsonrpc -v      # full; SHOULD = xfail
  ./run_tck.sh --sut-host http://localhost:9999 --verbose-log -- -k test_get_task_history_does_not_exceed_limit --tb=long
  # reports: a2a/test-tck/a2a-tck/reports/{compatibility.json,compatibility.html,tck_report.html,junitreport.xml}
  ```
  - `run_tck.sh` is a plain pass-through to `uv run ./run_tck.py` (CLI: `--sut-host`, `--transport`, `--level`, `-v`, `--verbose-log`, `-- <pytest args>`; `run_tck.py:104-137`). It is current. `docs/SUT_REQUIREMENTS.md:46-56` in the TCK shows stale flags (`--sut-url`, `--category`); ignore them.
  - Optional baseline: run the Python reference SUT from the research checkout on another port: `cd /Users/eugene/Documents/JetBrains/projects/a2a-tck/sut/a2a-python && SUT_HOST=localhost:9990 GRPC_PORT=9991 uv run python sut_agent.py`. It resolves `a2a-sdk` from `../../../a2a-python`, which exists there. Then run the same TCK command with `--transport jsonrpc` to compare.
- **CI** (`.github/workflows/a2a-tck-test.yml`):
  - `if: false` is at `:22`, and `timeout-minutes: 10` at `:24`;
  - the steps are setup_tck, then assemble, then `run_sut.sh` in the background with a 60 s wait for "Responding at http://0.0.0.0:9999" (`:63-77`), then `run_tck.sh --sut-host http://localhost:9999` (`:79-81`);
  - only `reports/*.html` is uploaded (`:91-97`).

## Discrepancies
1. **snake_case vs camelCase.**
   - Spec v1.0.1 §5.5 (`docs/specification.md:1202-1222`): all JSON serializations **MUST** use camelCase. The TCK JSON-RPC client violates this for request params.
   - ADR-001 (`adrs/adr-001-protojson-serialization.md:31-36`, rationale) makes ProtoJSON normative. ProtoJSON parsers accept both the lowerCamelCase name and the original proto field name. That rule comes from the external protobuf.dev JSON guide; I didn't cite it from the A2A repo.
   - The Python SDK parses with `ParseDict(params, …, ignore_unknown_fields=True)` (`src/a2a/server/routes/jsonrpc_dispatcher.py:303-310`), which accepts both, so the reference SUT passes.
   - Koog is strict, and silently ignores unknown keys.
   - So this is both a TCK bug (worth reporting upstream) and a Koog ProtoJSON-leniency gap.
2. **CORE-SEND-003 (MUST "returns ContentTypeNotSupportedError")** declares no `expected_error`. The generic runner then requires a **successful** response (`core_operations.py:99-124`; `test_requirements.py:99-114`), so a server that correctly returns -32005 fails. Koog doesn't validate media types, so it passes.
3. **CORE-MULTI-002a (MUST "rejects unacceptable contextId")** also has no `expected_error`, so it requires success (`core_operations.py:592-614`). Koog accepts client contextIds, so it passes.
4. **CORE-STREAM-002** is titled "Message-only stream", but the scenario streams a task COMPLETED (`streaming.feature:17-19`).
5. **`tck-reject-task`**: Python raises an error, while the Java emitter rejects the task (REJECTED state). No test uses it.
6. **CORE-CAP-004** can't pass without breaking every other test, because the TCK never sends the required extension header.
7. **Pinned history helper reuses one messageId.** This collides with server-side deduplication (Koog plan decision 8), and is fixed on TCK main (#250).
8. **ErrorInfo** is SHOULD in spec v1.0.1 and MUST in the TCK (already in plan §5). Koog emits it, so this has no effect.

## Open questions (for the user), each with a recommendation
1. **4.2 Push.** Declare `pushNotifications` only once G3 is fixed. Until then, keep it undeclared, like the Python reference: PUSH-* are skipped and CORE-CAP-001 is exercised. If it is declared, wire `InMemoryPushNotificationConfigStorage` plus `SimplePushNotificationSender(HttpClient(CIO))`.
2. **G3 (server/core change, needs sign-off per AGENTS.md).** Accept original proto field names when decoding: `@JsonNames("history_length")` etc. on the multi-word fields of request DTOs, or of all models for full ProtoJSON parser parity. Keep camelCase on output. Also file a TCK issue citing §5.5. Fallback if declined: deselect `test_get_task_history_does_not_exceed_limit` in CI, with a tracking link.
3. **Mount path.** Mount the SUT at `/` with `url="http://localhost:9999"`. Separately decide whether `a2aJsonRpcTransportRoute` should tolerate a trailing slash for every user (a small server change; no TCK need once the SUT mounts at `/`).
4. **Extended card.** Declare `extendedAgentCard=true` with a configured `agentCardExtended`: it exercises the GetExtendedAgentCard success path (CARD-EXT-001), and CORE-CAP-003 is skipped. Not declaring it is also green.
5. **Bump the TCK pin** to `d4d6fae` (main). Yes: it fixes the HIST-005/006 interaction with deduplication, and the other changes are CI-only.
6. **`tck-reject-task`.** Emit `Task` and then REJECTED, as the Java emitter does. It is untested, so the stakes are low.
7. **SUT `heartbeatPeriod = null`.** Recommended. A non-terminating stream then fails after the TCK's 30 s read timeout instead of hanging CI, where it would be killed without reports. Heartbeats aren't tested.
8. **CI shape.** Full run with `--transport jsonrpc`.
   - Raise `timeout-minutes` to 20.
   - Upload `reports/*` (including `compatibility.json` and `junitreport.xml`) and `server.log`.
   - The gate is the pytest exit code: SHOULD tests xfail, so they don't fail it.
9. **Card caching headers (SHOULD).** Leave them as xfail. Adding them needs either a custom `embeddedServer` (ConditionalHeaders/CachingHeaders) or a server route change.

## Proposed slices (sequential)
| # | Slice | Kind | Verify |
|---|---|---|---|
| S1 | Bump `setup_tck.sh:9` to `d4d6fae…`; update `test-tck/README.md` (path `/`, prefixes, commands) | scripts | `./setup_tck.sh` |
| S2 | `Main.kt`: v1 card (as above, decisions Q1/Q4), drop securitySchemes; `start(Netty, 9999, "/", …, heartbeatPeriod = null)`; `agentCardExtended` if Q4; drop unused client deps; fix `Module.md` | SUT | `../gradlew :a2a:test-tck:a2a-test-server-tck:assemble`; run SUT; `curl` the card and a GetTask on `/` (expect -32001 plus ErrorInfo) |
| S3 | `TckAgentExecutor.kt` rewrite: prefix table above (longest prefix first, terminal default, Task-first for new tasks, status-only for existing ones, timestamps, `cancel` → CANCELED) | SUT | assemble; `./run_tck.sh --sut-host http://localhost:9999 --transport jsonrpc --level must -v`. Expected: all MUST green except CORE-HIST-002 (hypothesis). |
| S4 | *(pending Q2)* ProtoJSON input leniency: `@JsonNames` with the snake_case names on request DTOs and nested models; unit tests in `a2a-core` (`RequestsSerializationTest`) and a transport test decoding snake_case GetTask/ListTasks/push params | **core change** | `../gradlew :a2a:a2a-core:jvmTest :a2a:a2a-transport:a2a-transport-server-jsonrpc-http:jvmTest`; the TCK MUST run turns CORE-HIST-002 green |
| S5 | *(pending Q1, after S4)* push in the SUT: `pushNotifications=true`, `InMemoryPushNotificationConfigStorage`, `SimplePushNotificationSender(HttpClient(CIO))` | SUT | TCK `-- -k push -v`: PUSH-* pass |
| S6 | CI: remove `if: false` (`a2a-tck-test.yml:22`), add `--transport jsonrpc`, timeout 20, upload `reports/**` and `server.log` | CI | local equivalent of the workflow steps; then a `workflow_dispatch` run on the branch (needs the user to push) |
| S7 | Gate 4.4: full run, triage `reports/compatibility.json` (FAIL rows), record MUST/xfail results in plan.md | verification | `./run_tck.sh --sut-host http://localhost:9999 --transport jsonrpc -v` exits 0 |
