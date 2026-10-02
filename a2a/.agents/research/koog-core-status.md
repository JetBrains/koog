# How completely and correctly do the Koog `a2a-core` data models and serialization implement the A2A v1.x data model (ProtoJSON over JSON-RPC)?

**Sources checked:**
- A2A spec repo (`check-specification`) at `/Users/eugene/Documents/JetBrains/projects/a2a-protocol`: baseline tag **`v1.0.1`** (commit `3303592588e388e62e0f69f701af531d2f4e3991`, 2026-05-28). `main` is at `65dadbd9b900fe2d970e05416eb432a91347cd2a` (2026-10-02). From `v1.0.1` to `main`, `specification/a2a.proto` changes only a comment (`AgentInterface.url`), and `docs/specification.md` has editorial changes. There are **no proto shape changes between `v1.0.0` and `v1.0.1`**: the diff touches comments only. Koog's copy `a2a/a2a.proto` is byte-identical to `v1.0.1:specification/a2a.proto`.
- A2A Python SDK (`check-python-sdk`) at `/Users/eugene/Documents/JetBrains/projects/a2a-python`. `HEAD` is `ddce6b87c4` (`v1.2.1-3`). I read code at **`v1.1.0`** (`96c14b79`), the version pinned by `a2a/test-python-a2a-server/pyproject.toml:7` (`a2a-sdk[http-server]==1.1.0`).
- A2A TCK (`check-a2a-tck`) at `/Users/eugene/Documents/JetBrains/projects/a2a-tck` @ `263b9cfaf16a554bdfb166a7ba5b67716e946349`. This is the same commit pinned in `a2a/test-tck/setup_tck.sh:9`. The TCK's vendored spec is A2A `v1.0.0` @ `17369575` (`specification/version.json`).
- Koog repo branch `eugenethedev/a2a-1.0`. The last commit touching `a2a-core` is `73f71807d` (2026-10-02).

**Confidence:** high. Every finding comes from reading the proto, spec text and Kotlin source directly. The kotlinx.serialization behavior was checked against library sources in the Gradle cache (kotlinx-serialization 1.10.0, Kotlin stdlib 2.3.10). A few runtime claims are marked as hypotheses.

## Answer

The overall shape of the v1 migration is correct:
- Member-based oneofs replace `kind`, for `Part`, `SendMessageResponse`, `StreamResponse`, `SecurityScheme` and `OAuthFlows`.
- Enums use SCREAMING_SNAKE_CASE.
- `supportedInterfaces`/`AgentInterface`, `capabilities.extendedAgentCard`, `tenant` on every request, `ListTasks` and the v1 push-config request types all exist.
- `ClientTransport`/`RequestHandler` cover all 11 v1 operations.

Several data-model points are still **wrong on the wire**. Each of these either rejects valid ProtoJSON or emits non-conformant JSON:
1. The AgentCard/AgentSkill security requirement still uses the v0.3 shape and name (`security: [{"x": [...]}]`). It should be `securityRequirements: [{"schemes": {"x": {"list": [...]}}}]`.
2. `APIKeySecurityScheme` uses `in` instead of `location`.
3. `TaskState` has a non-proto `TASK_STATE_UNKNOWN` and no `TASK_STATE_UNSPECIFIED`. `Role` has no `ROLE_UNSPECIFIED`.
4. `TransportProtocol.HTTP_JSON_REST` is `"HTTP+JSON/REST"` instead of `"HTTP+JSON"`.
5. `DataPart.data` is a `JsonObject`, but the proto type is `google.protobuf.Value`, which can be any JSON value.
6. `TaskPushNotificationConfig.taskId` is required, so the inline config in `SendMessageConfiguration` cannot be decoded without a task id. The TCK sends exactly that.
7. `ListTaskPushNotificationConfigsResponse.configs` is required, so the `{}` the Python server sends for an empty list fails to decode.
8. `TaskStatus.timestamp` defaults to `Clock.System.now()` when decoding, which invents a timestamp the sender never sent.
9. `ExtensionSupportRequiredError` (`-32008`) is missing.
10. Nothing maps A2A errors to `ErrorInfo.reason`.

Some leftovers are smaller: v0.3 names (`A2AAuthenticatedExtendedCardNotConfiguredException`), v0.3 doc links, `KIND` constant names, and a header (`X-A2A-Notification-Token`) that is not in the spec. Tests in `commonTest` assert several of the wrong shapes (`security`, `in`, `HTTP+JSON/REST`). There are no core tests for Task, Message, the Part variants other than text, StreamResponse, the request types, enums, base64 or timestamps.

## Requirements

| # | Requirement | Tier | Bindings | Citation |
|---|---|---|---|---|
| R1 | The proto is the single authoritative definition of data objects. SDK bindings MUST be derived from it. | MUST | all | spec `docs/specification.md:107` §1.4 (v1.0.1) |
| R2 | JSON serializations MUST use camelCase field names (ProtoJSON lowerCamel of the proto field name). | MUST | JSON-RPC, HTTP+JSON | spec `:1204` §5.5; ADR-001 |
| R3 | Enum values MUST be ProtoJSON enum names as defined in the proto, e.g. `TASK_STATE_INPUT_REQUIRED`, `ROLE_USER`. | MUST | JSON-RPC, HTTP+JSON | spec `:1215` §5.5 |
| R4 | Timestamps MUST be ISO 8601 UTC strings with `Z` only. Millisecond precision SHOULD be used. | MUST / SHOULD | JSON-RPC, HTTP+JSON | spec `:1230`, `:1236`, `:1255` §5.6.1 |
| R5 | REQUIRED fields MUST be present in valid messages. Implementations SHOULD validate them. Required arrays MUST be non-empty. | MUST (validity) / SHOULD (validate) | all | spec `:1263` §5.7 |
| R6 | Implementations SHOULD ignore unrecognized fields. | SHOULD | all | spec `:1275` §5.7 |
| R7 | `optional` fields must keep explicit-vs-absent presence. This matters for defaults and Agent Card canonicalization. | MUST (for signing) | all | spec §5.7 (`:1265-1271`) |
| R8 | The error taxonomy uses JSON-RPC codes -32001 to -32009, including **-32008 ExtensionSupportRequiredError**. | MUST | JSON-RPC | spec §5.4 `:1176-1190`, §3.3.2 table |
| R9 | JSON-RPC `error.data` is an array of objects, each with `@type`. | MUST | JSON-RPC | spec `:2455` §9.5 (v1.0.1) |
| R10 | JSON-RPC A2A errors include `google.rpc.ErrorInfo` with `reason` in UPPER_SNAKE (without `Error`) and `domain`=`a2a-protocol.org`. | **SHOULD in v1.0.1**; **MUST in v1.0.0** (and the TCK enforces MUST) | JSON-RPC | spec v1.0.1 `:2455`; v1.0.0 `docs/specification.md:2431`; TCK `tck/requirements/binding_jsonrpc.py:111-122` (JSONRPC-ERR-003) |
| R11 | gRPC and HTTP+JSON errors MUST include ErrorInfo with reason and domain. | MUST | gRPC, HTTP+JSON (not relevant to Koog today) | spec §10.6, §11.6 |
| R12 | Service parameters `A2A-Version` and `A2A-Extensions` are sent as HTTP headers in JSON-RPC. | MUST | JSON-RPC | spec §3.2.6 `:485-486`, §9.2 |
| R13 | The server MUST process using the requested `Major.Minor` and MUST return VersionNotSupportedError otherwise. An empty version means 0.3. | MUST | all | spec `:737`, `:739` §3.6.2 |
| R14 | Core protocol binding identifiers are `JSONRPC`, `GRPC`, `HTTP+JSON`. | MUST be accurate (§8.3.1) | AgentCard | proto `a2a.proto:340-343`; spec `:2143-2145` §8.5 |
| R15 | `ListTasksResponse.nextPageToken` MUST always be present (`""` on the last page). | MUST | all | spec `:246` §3.1.4 |
| R16 | When `includeArtifacts` is false, `artifacts` MUST be omitted from each Task. | MUST | all | spec `:240` §3.1.4 |
| R17 | Push-config inline in SendMessage: "Task id should be empty when sending this configuration in a `SendMessage` request". | SHOULD (proto comment) | all; capability:pushNotifications | proto `a2a.proto:147-149` |
| R18 | JSON-RPC method names are PascalCase v1 names (`SendMessage`, ..., `GetExtendedAgentCard`). | MUST | JSON-RPC | spec §5.3, §9.1 |

## Details

### ProtoJSON rules that apply here (ADR-001 adopts ProtoJSON normatively)
- **Field names:** lowerCamelCase of the proto name. ProtoJSON parsers also accept the original snake_case name.
- **Enums:** emitted as the enum name string. Parsers accept the name or the integer value.
- **oneof:** exactly one member key. No discriminator.
- **`bytes`:** standard base64 with padding on output. Parsers accept standard or URL-safe, with or without padding.
- **`google.protobuf.Struct`:** a JSON object. **`google.protobuf.Value`:** any JSON value.
- **`google.protobuf.Timestamp`:** RFC 3339 `Z` string with 0, 3, 6 or 9 fractional digits.
- **Default omission:** implicit-presence scalars are omitted when they hold the default (`""`, `0`, `false`, empty repeated/map). `optional` and message fields are emitted when set.
- **`null`:** accepted for any field and treated as the default.
- These ProtoJSON rules are restated from the ProtoJSON guide that ADR-001 references. They are not quoted from the A2A repo.

### Koog serialization machinery (`a2a-core/src/commonMain/kotlin/ai/koog/a2a/serialization/Serialization.kt`)
- **`PropertyPresencePolymorphicSerializer`** (`:58-81`) extends `JsonContentPolymorphicSerializer`.
  - Encoding delegates to the subclass serializer, so no discriminator is added. `SerializersTest.kt:28` asserts exactly `{"text":"Hello"}`.
  - Decoding selects the variant by which known key is present. It errors when zero or more than one known key is present. Unknown sibling keys are tolerated.
  - It is used for `Part`.
- **`PropertyWrappingPolymorphicSerializer`** (`:92-138`) emits `{"<member>": <value>}`.
  - Decoding requires the object to have **exactly one key**: `jsonElement.keys.singleOrNull()` at `:129-130`.
  - So any extra or unknown sibling key makes decoding fail, even when `ignoreUnknownKeys = true`. This is a gap against R6 (SHOULD).
  - It is used for `Event` (StreamResponse), `ResponseEvent` (SendMessageResponse), `TaskEvent`, `SecurityScheme` and `OAuthFlow` (OAuthFlows).
- **`ByteArrayAsBase64Serializer`** (`:140-159`) uses `kotlin.io.encoding.Base64.Default`.
  - Kotlin stdlib 2.3.10 `Base64.Default` uses `PaddingOption.PRESENT`, which means padding is required on decode. It also throws on characters outside the standard alphabet (stdlib `kotlin/io/encoding/Base64.kt:79`, `:554-555`, `:707`).
  - So **URL-safe or unpadded base64 is rejected**, which is a ProtoJSON parser-tolerance gap.
  - The exception is `IllegalArgumentException`, not `SerializationException`. Hypothesis: transports may map it to -32603 rather than -32602/-32700.
  - Encoding (standard, padded) is correct.
- **Timestamps:** `kotlin.time.Instant` uses kotlinx-serialization 1.10's built-in `InstantSerializer`, which is a string via `Instant.toString()` and `Instant.parse()` (kotlinx-serialization-core sources: `Instant.Companion.serializer()` and `InstantSerializer`, `PrimitiveKind.STRING`).
  - Output uses `Z` (R4 MUST met).
  - Parsing also accepts non-`Z` offsets, which is a lenient superset and acceptable.
  - Hypothesis: `Clock.System.now()` on the JVM yields micro- or nanosecond precision, so output has 6 or 9 fractional digits rather than the SHOULD millisecond precision.
- **Struct:** `JsonObject` / `Map<String, JsonElement>`. Correct.
- **Json configuration:** `a2a-core` has none. The transport uses `JSONRPCJson = Json { explicitNulls = false; encodeDefaults = false; ignoreUnknownKeys = true }` (`a2a-transport/a2a-transport-core-jsonrpc/.../serialization/Serialization.kt:29-33`).
  - Null optionals are omitted, which matches ProtoJSON default omission.
  - Non-null fields are always emitted. That is allowed by ProtoJSON: emitting defaults is permitted and parsers accept it.
  - `AgentCardResolver` uses a different `Json { ignoreUnknownKeys = true }` (`a2a-client/.../AgentCardResolver.kt:50-54`).
- **Decoding tolerance gaps beyond the above (lower priority):**
  - Integer enum values are not accepted.
  - snake_case field names are not accepted (no `@JsonNames`).
  - `null` for a non-null field fails, because `coerceInputValues` is off.

### Per-proto-type audit (proto line numbers refer to `v1.0.1:specification/a2a.proto`)

Kotlin paths are relative to `a2a/a2a-core/src/commonMain/kotlin/ai/koog/a2a/`.

| Proto type | Kotlin type (file:line) | Status | Gaps |
|---|---|---|---|
| `SendMessageConfiguration` (143-161) | `SendMessageConfiguration` `model/Requests.kt:41-47` | **done** | `acceptedOutputModes`, `taskPushNotificationConfig`, `historyLength: Int?` (optional), `returnImmediately: Boolean?`. `blocking` was removed in `73f71807d`. The nested push config is affected by the `taskId` issue (see TaskPushNotificationConfig). |
| `Task` (167-184) | `Task` `model/Task.kt:18-32` | **partial** | `contextId: String` is non-null with no default (`:21`), but proto `context_id` is not REQUIRED (`:173`). A ProtoJSON Task with an empty or omitted contextId fails to decode. Everything else is fine. `history`, `artifacts` and `metadata` are nullable and omitted when null, which suits R16 (omit artifacts). |
| `TaskState` (187-208) | `TaskState` `model/Task.kt:99-145` | **wrong** | 8 real states are correct and their wire names match. **`TASK_STATE_UNKNOWN` (`:144`) is not a proto value** (v0.3 `unknown` leftover). **`TASK_STATE_UNSPECIFIED` is missing**, so decoding `"TASK_STATE_UNSPECIFIED"` or `0` fails. Encoding `TASK_STATE_UNKNOWN` produces an invalid enum. The `terminal` flag is correct. There is no "interrupted" flag for INPUT_REQUIRED/AUTH_REQUIRED, which matters for `returnImmediately` semantics; that is only a convenience. |
| `TaskStatus` (211-219) | `TaskStatus` `model/Task.kt:87-92` | **partial** | `timestamp: Instant? = Clock.System.now()` (`:91`). On decode, an absent `timestamp` becomes "now" instead of null, which invents data the sender never sent. Hypothesis: on encode with `encodeDefaults=false`, the plugin re-evaluates the default to compare, so it is in practice always emitted. |
| `Part` (224-242) | sealed `Part` + `TextPart`/`FileBytesPart`/`FileUrlPart`/`DataPart` `model/Parts.kt:11-116`, `PartSerializer` `Serialization.kt:161-169` | **partial** | The member-based oneof is correct (`text`/`raw`/`url`/`data`). `filename`, `mediaType` and `metadata` are on every variant, which is correct. `raw` is base64 (see the tolerance gap). **`DataPart.data: JsonObject` (`Parts.kt:108`) is wrong.** Proto `google.protobuf.Value` (`:233`) allows arrays, strings, numbers, booleans and null, so `{"data":[1,2]}` or `{"data":"x"}` fails to decode and cannot be produced. |
| `Role` (245-252) | `Role` `model/Message.kt:9-13` | **partial** | `ROLE_USER` and `ROLE_AGENT` are correct. `ROLE_UNSPECIFIED` is missing, so decoding fails. |
| `Message` (260-277) | `Message` `model/Message.kt:28-42` | **done** | All 8 fields have correct names and optionality. Non-empty `parts` (R5) is not validated. `Message.KIND="message"` is only a oneof key name. |
| `Artifact` (280-293) | `Artifact` `model/Task.kt:157-165` | **done** | Correct. Non-empty `parts` is not validated. |
| `TaskStatusUpdateEvent` (296-305) | `model/Task.kt:43-53` | **done** | No `kind` and no `final`. Correct. |
| `TaskArtifactUpdateEvent` (308-322) | `model/Task.kt:66-78` | **done** | `append` and `lastChunk` are `Boolean?`. Correct. |
| `AuthenticationInfo` (325-332) | `model/TaskPushNotificationConfig.kt:32-36` | **done** | Renamed `schemes`→`scheme: String` in `73f71807d`. Correct. |
| `AgentInterface` (336-355) | `model/AgentCard.kt:122-128` | **done** | `url`, `protocolBinding`, `protocolVersion` required; `tenant` optional. Correct. `protocolBinding` is a `TransportProtocol` value class serialized as a string, which is correct; the bad constant is listed separately below. |
| (binding identifiers) | `TransportProtocol` `model/AgentCard.kt:79-101` | **wrong** | `HTTP_JSON_REST = "HTTP+JSON/REST"` (`:94`). Proto (`:340-342`), spec §8.5 and Python (`src/a2a/utils/constants.py:19` `HTTP_JSON = 'HTTP+JSON'`) all use **`HTTP+JSON`**. The TCK treats `HTTP+JSON/REST` as an unknown binding (`tests/compatibility/agent_card/test_agent_card.py:53`, `:196-201`, skip). |
| `AgentCard` (361-398) | `AgentCard` `model/AgentCard.kt:58-74` | **wrong (one field)** | All fields are present with correct optionality (`documentationUrl`/`iconUrl` are nullable; `optional` presence is kept as null vs set). **`security: Security?` (`:72`) → JSON key `security`. It must be `securityRequirements`** (proto `security_requirements = 9`, `:383`), with the shape in the SecurityRequirement row. The KDoc still mentions v0.3 `'url'` and `'preferredTransport'` (`:22`). |
| `AgentProvider` (401-408) | `model/AgentCard.kt:136-140` | **done** | Correct. |
| `AgentCapabilities` (411-420) | `model/AgentCard.kt:150-156` | **done** | `streaming`, `pushNotifications` and `extendedAgentCard` are `Boolean?`, which keeps presence. `extensions` is correct. |
| `AgentExtension` (423-432) | `model/AgentCard.kt:166-172` | **partial (minor)** | `uri: String` is required, but proto `uri` is not REQUIRED (`:425`). Over-strict on decode. `params` as a Struct is correct. |
| `AgentSkill` (435-452) | `model/AgentCard.kt:448-458` | **wrong (one field)** | `security` (`:457`) must be `securityRequirements` (proto `:451`) with the SecurityRequirement shape. |
| `AgentCardSignature` (456-466) | `model/AgentCard.kt:468-474` | **done** | `protected`, `signature`, `header` (Struct). Correct. |
| `TaskPushNotificationConfig` (469-484) | `model/TaskPushNotificationConfig.kt:16-24` | **partial** | `tenant`, `id`, `url`, `token` and `authentication` are correct. **`taskId: String` is non-null (`:19`), but proto `task_id` is not REQUIRED (`:477`) and SHOULD be empty when inline in SendMessage (`:148`).** Effects: (a) a Koog client cannot omit it; (b) a Koog server fails to decode a `SendMessageRequest` whose `configuration.taskPushNotificationConfig` has no `taskId`, which is exactly what TCK `_setup_push_and_trigger` sends (`tests/compatibility/core_operations/test_push_notifications.py:77-86`, `:389`). The type is also used as the Create request (`ClientTransport.kt:110-113`). For CreateTaskPushNotificationConfig the task id is semantically required, but that is a server-validation question, not a schema one. |
| `StringList` (488-491) | — | **missing** | Needed for the SecurityRequirement shape. |
| `SecurityRequirement` (495-498) | `typealias SecurityRequirement = Map<String, List<String>>` `model/AgentCard.kt:199` (and `Security` `:189`) | **wrong** | The v0.3/OpenAPI shape `{"oauth":["read"]}` is used. ProtoJSON v1 is `{"schemes":{"oauth":{"list":["read"]}}}`. Asserted by `AgentCardSerializationTest.kt:120-122, 268-276, 188-191 (skill), 534-545`. |
| `SecurityScheme` oneof (503-516) | sealed `SecurityScheme` + `SecuritySchemeSerializer` `Serialization.kt:198-207` | **done** | The member keys `apiKeySecurityScheme`, `httpAuthSecurityScheme`, `oauth2SecurityScheme`, `openIdConnectSecurityScheme` and `mtlsSecurityScheme` are the correct ProtoJSON names. Subject to the single-key strictness. |
| `APIKeySecurityScheme` (519-526) | `model/AgentCard.kt:217-227`, `In` enum `:232-242` | **wrong** | **JSON key `in` (`@SerialName("in")`, `:219`) must be `location`** (proto `:523`). The proto field is a free `string`, while Kotlin restricts it to a 3-value enum. Lowercase values are fine, but unknown values fail. `name` and `description` are correct. |
| `HTTPAuthSecurityScheme` (529-539) | `model/AgentCard.kt:253-262` | **done** | Correct. |
| `OAuth2SecurityScheme` (542-550) | `model/AgentCard.kt:272-281` | **done** | `flows` (OAuthFlows oneof), `oauth2MetadataUrl` and `description` are correct. |
| `OpenIdConnectSecurityScheme` (553-558) | `model/AgentCard.kt:397-405` | **done** | Correct. |
| `MutualTlsSecurityScheme` (561-564) | `MutualTLSSecurityScheme` `model/AgentCard.kt:412-419` | **done** | Correct. The Kotlin class name differs in case only. |
| `OAuthFlows` oneof (567-580) | sealed `OAuthFlow` + `OAuthFlowSerializer` `Serialization.kt:209-219` | **done** | Keys `authorizationCode`, `clientCredentials`, `implicit`, `password` and `deviceCode` are correct. Implicit and password are `@Deprecated`, matching proto `deprecated = true`. |
| `AuthorizationCodeOAuthFlow` (583-595) | `model/AgentCard.kt:306-317` | **done** | `pkceRequired: Boolean?` is correct. |
| `ClientCredentialsOAuthFlow` (598-605) | `model/AgentCard.kt:324-333` | **done** | Correct. |
| `ImplicitOAuthFlow` (608-618) | `model/AgentCard.kt:340-350` | **partial (minor)** | `authorizationUrl` and `scopes` are required in Kotlin but not REQUIRED in the proto. Over-strict. |
| `PasswordOAuthFlow` (621-631) | `model/AgentCard.kt:357-367` | **partial (minor)** | `tokenUrl` and `scopes` are required in Kotlin but not REQUIRED in the proto. |
| `DeviceCodeOAuthFlow` (636-645) | `model/AgentCard.kt:379-389` | **done** | Correct. The KDoc for `refreshUrl` is a copy-paste of the scopes text (`:377`). |
| `SendMessageRequest` (648-658) | `model/Requests.kt:25-31` | **done** | `tenant`, `message`, `configuration`, `metadata`. Correct. |
| `GetTaskRequest` (661-672) | `model/Requests.kt:57-62` | **done** | `historyLength: Int?` keeps presence. Correct. |
| `ListTasksRequest` (675-700) | `model/Requests.kt:77-87` | **done** | All 8 fields. `status: TaskState?` inherits the UNSPECIFIED/UNKNOWN problem. `statusTimestampAfter: Instant?` is correct. |
| `ListTasksResponse` (703-712) | `model/Responses.kt:18-24` | **done** | All 4 are REQUIRED and non-null, so all are always emitted, which satisfies R15. Python v1.1.0 also emits all fields (`always_print_fields_with_no_presence=True`, `src/a2a/server/routes/jsonrpc_dispatcher.py:428-438`), so client decoding against it works. A non-Python server that omits `""`/`0`/`[]` would fail to decode. Spec R15 makes that server non-conformant for `nextPageToken`, but `totalSize: 0` or `tasks: []` omission is legal ProtoJSON. |
| `CancelTaskRequest` (715-723) | `model/Requests.kt:95-100` | **done** | Correct. |
| `GetTaskPushNotificationConfigRequest` (726-734) | `model/Requests.kt:108-113` | **done** | Correct. |
| `DeleteTaskPushNotificationConfigRequest` (737-745) | `model/Requests.kt:121-126` | **done** | Correct. |
| `SubscribeToTaskRequest` (748-754) | `model/Requests.kt:133-137` | **done** | Correct. |
| `ListTaskPushNotificationConfigsRequest` (757-769) | `model/Requests.kt:146-152` | **done** | Correct. |
| `GetExtendedAgentCardRequest` (772-776) | `model/Requests.kt:157-160` | **done** | Correct. |
| `SendMessageResponse` oneof (779-787) | `ResponseEvent` + `ResponseEventSerializer` `model/Events.kt:27-28`, `Serialization.kt:181-187` | **done** | `{"task":…}` or `{"message":…}`. Correct. Single-key strictness applies. |
| `StreamResponse` oneof (790-802) | `Event` + `EventSerializer` `model/Events.kt:11-22`, `Serialization.kt:171-179` | **done** | Keys `task`, `message`, `statusUpdate` and `artifactUpdate` are correct. It is also usable as the push payload (spec §4.3.3). `TaskEvent` (`Serialization.kt:189-196`) is a Koog-only subset. |
| `ListTaskPushNotificationConfigsResponse` (806-811) | `model/Responses.kt:32-36` | **partial** | `configs: List<…>` is required with no default (`:34`). The proto field is not REQUIRED, and ProtoJSON omits an empty repeated field. **Python v1.1.0 emits `MessageToDict(configs_response)` without always-print** (`jsonrpc_dispatcher.py:466-476`), so an empty list arrives as `{}` and Koog decoding fails with MissingFieldException. `nextPageToken` now defaults to `""` (`73f71807d`), which is correct. |
| `google.protobuf.Empty` (Delete result) | `Unit` return in `ClientTransport.kt:140-143` / `ServerTransport.kt:151-154` | **done** | The wire form is the transport's concern. |
| `google.protobuf.Struct` | `JsonObject` / `Map<String, JsonElement>` | **done** | — |
| `google.protobuf.Value` | (should be `JsonElement`) | **wrong** | Only used by `Part.data`; see the Part row. |
| `google.protobuf.Timestamp` | `kotlin.time.Instant` | **done** | See the timestamp notes above. |

### The 11 RPCs: request types, tenant, transport interfaces

Every request type has `override val tenant: String? = null` through the sealed `Request` (`model/Requests.kt:10-16`). The JSON name `tenant` is correct, and it is omitted when null.

`ClientTransport` (`transport/ClientTransport.kt:34-144`) and `RequestHandler` (`transport/ServerTransport.kt:45-155`) both cover all 11 operations:
- getExtendedAgentCard
- sendMessage
- sendMessageStreaming (v1 name is `SendStreamingMessage`; the Kotlin method name differs, which is cosmetic)
- getTask
- listTasks
- cancelTask
- subscribeToTask
- create/get/list/deleteTaskPushNotificationConfig

Return types match the proto: AgentCard, SendMessageResponse→`ResponseEvent`, stream→`Flow<Event>`, Task, ListTasksResponse, TaskPushNotificationConfig, list response, Unit.

Stale v0.3 KDoc links remain at `ClientTransport.kt:126` and `ServerTransport.kt:137` (`v0.3.0/specification/#77-taskspushnotificationconfiglist`). There are no v0.3 method-name strings in a2a-core. The JSON-RPC method constants live in a2a-transport and were not audited here.

### Errors vs the v1 taxonomy (`exceptions/Exceptions.kt`, `exceptions/ErrorData.kt`)

| A2A error (spec §3.3.2 / §5.4) | JSON-RPC code | Koog | ErrorInfo reason (spec §10.6 rule; Python `src/a2a/utils/errors.py` `A2A_ERROR_MAPPING`; TCK `tck/requirements/base.py:246-330`) |
|---|---|---|---|
| TaskNotFoundError | -32001 | `A2ATaskNotFoundException` | `TASK_NOT_FOUND` — not mapped |
| TaskNotCancelableError | -32002 | `A2ATaskNotCancelableException` | `TASK_NOT_CANCELABLE` — not mapped |
| PushNotificationNotSupportedError | -32003 | `A2APushNotificationNotSupportedException` | `PUSH_NOTIFICATION_NOT_SUPPORTED` — not mapped |
| UnsupportedOperationError | -32004 | `A2AUnsupportedOperationException` | `UNSUPPORTED_OPERATION` — not mapped |
| ContentTypeNotSupportedError | -32005 | `A2AContentTypeNotSupportedException` | `CONTENT_TYPE_NOT_SUPPORTED` — not mapped |
| InvalidAgentResponseError | -32006 | `A2AInvalidAgentResponseException` | `INVALID_AGENT_RESPONSE` — not mapped |
| ExtendedAgentCardNotConfiguredError | -32007 | **v0.3 name** `AUTHENTICATED_EXTENDED_CARD_NOT_CONFIGURED` / `A2AAuthenticatedExtendedCardNotConfiguredException` (`:22`, `:173-176`). The code is correct. | `EXTENDED_AGENT_CARD_NOT_CONFIGURED` — not mapped |
| **ExtensionSupportRequiredError** | **-32008** | **missing**: no constant and no exception. `A2AException.create` maps -32008 to `A2AUnknownException`. | `EXTENSION_SUPPORT_REQUIRED` |
| VersionNotSupportedError | -32009 | `A2AVersionNotSupportedException` (added `bc6bb9e32`) | `VERSION_NOT_SUPPORTED` — not mapped |
| JSON-RPC standard -32700/-32600/-32601/-32602/-32603 | — | present (`:9-13`) | Python also emits reasons `INVALID_PARAMS`, `INVALID_REQUEST`, `METHOD_NOT_FOUND`, `INTERNAL_ERROR` (not spec-defined) |

- `ErrorData`/`ErrorInfo`/`BadRequest`/`GenericErrorData` (`ErrorData.kt:17-84`) emit and parse `@type` correctly. `ErrorInfo` always emits `domain="a2a-protocol.org"` and `@type`. On decode, `ErrorDataSerializer` (`Serialization.kt:256-269`) dispatches on `@type` and preserves unknown types as raw JSON. This is correct per R9.
- **Gap:** exceptions default to `details = emptyList()`, and no table maps an exception to its `ErrorInfo.reason`. Unless the server transport adds them, A2A errors go out without ErrorInfo. That is SHOULD-level under v1.0.1 and fails TCK JSONRPC-ERR-003 (MUST). Server-transport behavior is out of scope here; see Open questions.
- `ErrorInfo.metadata: Map<String,String>` matches `google.rpc.ErrorInfo`. Hypothesis: Python's `_error_info` passes `error.data` (an arbitrary dict) as metadata (`src/a2a/utils/error_handlers.py:40-62`, v1.1.0). If Python ever puts non-string values there, Koog fails to decode `ErrorInfo`.
- `BadRequest.FieldViolation` lacks `localizedMessage`. It is ignored on decode, so this is minor.
- Default messages differ from the spec §9.5 "Standard Message" column (`-32600` "Request payload validation error", `-32602` "Invalid parameters", `-32603` "Internal error"). The spec column is informative.

### Constants and validation
- `A2AHeaders.A2A_VERSION = "A2A-Version"` and `A2A_EXTENSIONS = "A2A-Extensions"` (`consts/A2AHeaders.kt:11,17`). Correct (R12).
- `X_A2A_NOTIFICATION_TOKEN = "X-A2A-Notification-Token"` (`:22`) is **not in the v1 spec**: spec §4.3.3 and §13.2 send credentials through `Authorization` built from `AuthenticationInfo`. It is a Python SDK convention (`src/a2a/server/tasks/base_push_notification_sender.py:87`, v1.1.0).
- `A2APaths.AGENT_CARD_WELL_KNOWN_PATH = "/.well-known/agent-card.json"` is correct (spec §8.2, §14.3). Its KDoc links to v0.3 (`consts/A2APaths.kt:10`).
- `A2AVersions.VERSION_1_0 = "1.0"` (Major.Minor, correct per §3.6).
- `validation/VersionValidation.kt:6-11` `isVersionCompatible` compares the **major version only**. Spec §3.6.2 requires matching `Major.Minor` and treating an empty value as 0.3. The function is **unused** anywhere in `a2a/` (rg found no callers).

### Leftover v0.3 artifacts in a2a-core
- `TASK_STATE_UNKNOWN` (`model/Task.kt:144`).
- AgentCard and AgentSkill `security` with the OpenAPI shape (`model/AgentCard.kt:72,457,189,199`).
- APIKey `in` (`:219`).
- `HTTP+JSON/REST` (`:94`).
- `AUTHENTICATED_EXTENDED_CARD_NOT_CONFIGURED` / `A2AAuthenticatedExtendedCard…` (`exceptions/Exceptions.kt:22,56,171-176`).
- v0.3 doc links (`A2APaths.kt:10`, `ClientTransport.kt:126`, `ServerTransport.kt:137`).
- KDoc mentioning `preferredTransport`/`url` (`AgentCard.kt:22`) and "OpenAPI 3.0" (proto now says 3.2).
- The `KIND` constant naming on models. It is only used as the oneof member key, so the wire is correct, but the name is misleading.
- Not found: a `kind` field, a `final` field, compound `tasks/{id}` IDs, or v0.3 method names (`message/send`, `tasks/get`, …).

## Koog status

The migration commits correlate as follows (`git log -- a2a/a2a-core`):

| Commit | Change |
|---|---|
| `d0027323d` | task state / role serial names, removed `kind` from events |
| `9a5ee87b3` | message / part |
| `04d78a682` | agent card |
| `44fa019c1` | push config, headers, paths |
| `52d377fa6` | models / serialization finalized |
| `a1e8c62c8` | requests/responses and the new ClientTransport |
| `47adecfcd` | ServerTransport |
| `25e3bbeca`, `cee58c49c` | ErrorInfo / ErrorData |
| `bc6bb9e32` | VersionNotSupported |
| `54b60f35e` | versions |
| `73f71807d` | removed `blocking`; `AuthenticationInfo.scheme`; `nextPageToken` default |

The AgentCard commit migrated the structure but not the security requirement or APIKey field names.

Gaps, ordered by interop impact for JSON-RPC:
1. **SecurityRequirement shape and name** (AgentCard and AgentSkill). Every Koog agent card with security is non-conformant, and v1 cards from Python/ProtoJSON servers with `securityRequirements` lose that data. It is ignored as an unknown key, not rejected.
2. **`APIKeySecurityScheme.in` → `location`**. Decoding a v1 card with an API-key scheme **fails** because `in` is a missing required field. Encoding produces an invalid card.
3. **`TaskPushNotificationConfig.taskId` required**. The server rejects TCK and Python inline push configs. TCK PUSH-DELIVER-001..003 then skip at "send_message failed".
4. **`ListTaskPushNotificationConfigsResponse.configs` required**. The Koog client fails against the Python server's empty-list `{}`.
5. **`DataPart.data` as `JsonObject`** rejects non-object `Value`s.
6. **`TaskState.TASK_STATE_UNKNOWN` / missing `UNSPECIFIED`; `Role` missing `ROLE_UNSPECIFIED`.**
7. **`TransportProtocol.HTTP_JSON_REST` value** (latent; Koog does not implement REST).
8. **`TaskStatus.timestamp` default `now()` on decode.**
9. **Error taxonomy:** missing -32008, v0.3 name for -32007, and no reason mapping for ErrorInfo.
10. **Tolerance:** single-key oneof wrappers, strict base64, no integer enums or snake_case names, `Task.contextId` / `AgentExtension.uri` / Implicit- and Password-flow fields over-strict.
11. `X-A2A-Notification-Token` is non-spec. `isVersionCompatible` is major-only and unused.

How kotlinx.serialization serializes the fixes (implementation hints, not decisions):
- **SecurityRequirement:** `@Serializable data class SecurityRequirement(val schemes: Map<String, StringList>)` with `@Serializable data class StringList(val list: List<String> = emptyList())`, and the property renamed with `@SerialName("securityRequirements")`. These are API-breaking changes, so the maintainer should confirm first.
- **APIKey:** `@SerialName("location")`, and consider `String` instead of the enum.
- **Part data:** `DataPart.data: JsonElement`. Note that `JsonNull` must be distinguishable from an absent value for presence detection. `PartSerializer` already keys on presence, so `"data": null` selects DataPart, but deserializing `JsonElement` from `null` gives `JsonNull`, which is fine.
- **Optional fields:** `taskId: String? = null`, `configs: List<…> = emptyList()`, `timestamp: Instant? = null`, `contextId` with default `""` or nullable.

## Testability notes
- Everything here can be unit-tested in `a2a-core/commonTest` with JSON round-trip tests on whole objects, in the same pattern as `AgentCardSerializationTest`. Use ProtoJSON fixtures taken from the proto shapes, ideally produced by Python `MessageToDict`, for:
  - a Task with each state, plus `TASK_STATE_UNSPECIFIED`
  - a Message with all four Part kinds, including `data` as an array and as a string, and `raw` as padded, unpadded and URL-safe base64
  - all four StreamResponse members, and a wrapper with an unknown sibling key (to cover R6)
  - an AgentCard with `securityRequirements` and an `apiKeySecurityScheme` with `location`
  - a SendMessageRequest with `configuration.taskPushNotificationConfig` and no `taskId`
  - `{}` decoded as ListTaskPushNotificationConfigsResponse
  - a TaskStatus with no timestamp decoding to null
  - each exception mapped to its ErrorInfo reason
- The existing tests that assert wrong shapes must be updated: `AgentCardSerializationTest.kt:99,120-122,186,188-191,268-276,304,310,319-330`, the `In` enum test, and `SerializersTest.kt:35-48`.
- TCK coverage:
  - CARD-STRUCT-001 validates the card against the JSON Schema with `allow_additional=True` (`tests/compatibility/agent_card/test_agent_card.py:107-123`). Because extra properties are allowed and proto3 schemas have no `required`, the **v0.3 `security` and `in` keys pass unnoticed**. The TCK does not catch gaps 1 and 2.
  - BIND-FIELD-001 skips on unknown bindings (`:196-201`), but Koog's SUT only declares `JSONRPC`.
  - JSONRPC-ERR-003 (`tck/requirements/binding_jsonrpc.py:111-122`, `tck/validators/error_info.py`) checks for ErrorInfo with reason and domain, so it catches gap 9 at the server.
  - PUSH-DELIVER-001..003 exercise the inline push config (gap 3) but **skip** rather than fail on a send error (`test_push_notifications.py:390-391`).
  - The TCK schema allows `TASK_STATE_UNSPECIFIED` and integer enums (`specification/a2a.json` "Task State" anyOf).
  - I found no TCK test that sends a non-object `data` part or unpadded base64.
- Python interop (client integration tests against `a2a-sdk==1.1.0`) would show gap 4 on an empty list-configs response, and gap 2 if the test server card declared an API-key scheme.

## Discrepancies
- **Spec sample vs proto, SecurityRequirement.** Spec §8.5 sample card (`docs/specification.md:2166`, v1.0.1) shows `"security": [{ "google": ["openid","profile","email"] }]`, the v0.3 shape. The proto (`a2a.proto:383`, `:495-498`) defines `securityRequirements` → `{"schemes":{…:{"list":[…]}}}`. Spec §1.4 (`:107`) makes the proto authoritative for data objects, so the sample is wrong. The TCK schema (`specification/a2a.json` "Security Requirement" / "String List") follows the proto. Koog follows the sample.
- **ErrorInfo on JSON-RPC, MUST vs SHOULD.** v1.0.0 §9.5 says MUST (`v1.0.0:docs/specification.md:2431`). v1.0.1 §9.5 relaxed it to SHOULD (`:2455`, commit `757f0ec`). The TCK (tracking v1.0.0) enforces MUST as JSONRPC-ERR-003. Python always emits it.
- **TCK error HTTP/gRPC mappings vs spec §5.4.** For example, TaskNotCancelable 409 vs 400, and UnsupportedOperation gRPC UNIMPLEMENTED vs FAILED_PRECONDITION (`tck/requirements/base.py:254-330`). This does not affect the JSON-RPC codes, which match, and is irrelevant to Koog today.
- **whats-new doc vs proto.** `docs/whats-new-v1.md:86,158,433` (v1.0.1) claim `Task.createdAt`/`lastModified` and `PushNotificationConfig.createdAt` were added, but they are not in the proto. That document also says implicit and password flows were "removed", while the proto keeps them as `deprecated`. Koog follows the proto on both. Koog's `a2a_1.0.0_changelog.md` is a copy of this doc with `[DONE]` markers and inherits the errors.
- **Spec text references `PushNotificationConfig`** (§4.3.1 `proto_to_table("PushNotificationConfig")`, §3.1.7/3.1.8 outputs), but that message does not exist in the v1.0.1 proto; only `TaskPushNotificationConfig` does. This is an editorial spec defect.
- **Python list-configs omission vs ListTasks always-print.** Python emits ListTasksResponse with always-print but ListTaskPushNotificationConfigsResponse without it (`jsonrpc_dispatcher.py:428-438` vs `:466-476`). Both are legal ProtoJSON; the inconsistency is just worth noting.
- **`X-A2A-Notification-Token`** is used by Python but not specified.

## Open questions
- Does the JSON-RPC server transport (`a2a-transport-server-jsonrpc-http`) attach ErrorInfo reasons, and with which strings? This decides whether JSONRPC-ERR-003 passes. It is outside a2a-core.
- Does any server component validate `A2A-Version` (§3.6.2: Major.Minor, empty = 0.3)? `isVersionCompatible` is unused.
- Does the server omit `artifacts` for ListTasks when `includeArtifacts` is false (R16), and does it always emit `nextPageToken`? That is server logic.
- Should the push-config model be split into a Create request (`taskId` required) and an inline config (`taskId` absent)? This is an API design decision for the maintainer, per `a2a/CLAUDE.md` ("never make architecture decisions independently").
- Does Agent Card signing/canonicalization exist anywhere? It depends on R7 presence semantics, which the current nullable modelling supports.
- Do the a2a-transport JSON-RPC method-name constants use the v1 names? They were not audited because they are outside a2a-core.
