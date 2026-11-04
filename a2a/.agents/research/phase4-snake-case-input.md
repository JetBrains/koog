# Can Koog decode both camelCase and snake_case field names (encode camelCase only) from one central place?

**Sources checked:** A2A spec `a2aproject/A2A` @ `v1.0.1` (3303592; `main` 4735f48 not needed), a2a-python @ 6ca8593 (`v1.3.0-11`) plus its venv `protobuf` 6.33.6 `json_format.py`, a2a-tck research checkout @ d4d6fae (pinned SUT clone 263b9cf), protobuf.dev ProtoJSON guide (fetched 2026-11-02), kotlinx.serialization **1.10.0** sources (`gradle/libs.versions.toml:21`, extracted from the Gradle cache), Koog branch `eugenethedev/a2a-1.0`.
**Confidence:** high on the kotlinx API facts (read from the 1.10.0 sources) and on the field inventory. Medium on the effort estimate.

## Answer
- **No `Json { }` flag can do this.** `JsonNamingStrategy` maps each property to exactly one JSON name, applies to both encode and decode, and makes the decoder ignore the original camelCase name. `@JsonNames` has to be written on each property at compile time. There is no meta-annotation or global alias hook.
- **The closest thing to "one central place" is a schema-aware, decode-only key normalizer.** It walks the input `JsonElement` alongside the target `SerialDescriptor` and renames a key to `lowerCamel(key)` only when that camel name is a known element of the class being decoded. It does not descend into `JsonObject`, `JsonElement` or map keys, so free-form JSON is never touched. It runs at the two places where JSON-RPC payloads are decoded: `params` on the server, `result` on the client.
- **Encoding is unchanged** (still camelCase), because only the decode path is affected.
- **Recommendation:** build this normalizer in `a2a-core` (about 100 LOC plus about 6 shape registrations and tests, roughly 0.5–1 day). Do not add 66 `@JsonNames` annotations.

## Requirements
| # | Requirement | Tier | Bindings | Citation |
|---|---|---|---|---|
| 1 | JSON serializations MUST use camelCase field names | MUST (applies to emitting JSON) | JSON-RPC, HTTP+JSON | A2A `docs/specification.md:1202-1204` §5.5 @v1.0.1 |
| 2 | JSON follows ProtoJSON | normative via §5.5 note; ADR is rationale | JSON-RPC, HTTP+JSON | spec `:1220` §5.5; `adrs/adr-001-protojson-serialization.md:26-30` |
| 3 | ProtoJSON parsers "are required to accept both the converted lowerCamelCase name and the proto field name" | MUST (external ProtoJSON spec, adopted by A2A) | JSON-RPC, HTTP+JSON (Koog: JSON-RPC) | protobuf.dev/programming-guides/json (JSON options section) |
| 4 | Parsers accept enum names **and integer values** | MUST (ProtoJSON) | same | protobuf.dev JSON guide, enum row |
| 5 | Duplicate alternate spellings (camel and snake both present): parse failure preferred, "last wins" where practical; never first-wins | SHOULD (ProtoJSON) | same | protobuf.dev JSON guide, duplicates section |
| 6 | Struct/Value and map keys round-trip unchanged | MUST (ProtoJSON) | same | protobuf.dev JSON guide; proto Struct fields: `specification/a2a.proto:183,233,236,272,290,304,321,431,465,657,722`; maps `:381,497,591,604,617,630,644` |

The A2A proto has no `json_name` overrides (`a2a.proto` @v1.0.1: 0 occurrences), so the accepted names are exactly `lowerCamel(proto_name)` and `proto_name`.

## Details

### kotlinx.serialization 1.10.0 facts (from sources)
- **`JsonNamingStrategy`** (`json/.../JsonNamingStrategy.kt:8-19,47-64`, `@ExperimentalSerializationApi`):
  - "Transformed serial names are used for both serialization and deserialization." "Original serial names are never used after transformation, so they are ignored in a Json input."
  - The decode map holds the strategy name plus any `@JsonNames` entries, not the original name (`internal/JsonNamesMap.kt:25-41,44-46`). It is built once per descriptor, so `serialNameForJson` returns one name per element.
  - The strategy also applies only to `StructureKind.CLASS` (`JsonNamesMap.kt:76-77`).
  - Consequences: `namingStrategy = SnakeCase` would encode snake_case, which breaks §5.5. Using it only on a decode `Json` would reject camelCase, and the TCK sends **mixed** input: snake at the top level, camel inside `message` (e.g. `"messageId"` in `tests/compatibility/core_operations/test_data_model.py`). No single-name strategy can handle mixed input.
- **`@JsonNames`** (`JsonAnnotations.kt:40-43`):
  - It is `@SerialInfo @Target(PROPERTY)` and read with `getElementAnnotations(i).filterIsInstance<JsonNames>()` (`JsonNamesMap.kt:30`). That requires an exact `JsonNames` instance on the property, so a meta-annotation will not work.
  - It is honoured only when `useAlternativeNames = true`. That is the default (`JsonConfiguration.kt:31`), and `JSONRPCJson` does not override it.
  - It never affects encoding (`JsonNamesMap.kt:70`).
  - The tree decoder takes the primary name first when both spellings are present (`internal/TreeJsonDecoder.kt:238-251`). So camel silently wins, which is first-wins by name and not what ProtoJSON asks for (row 5).
- **`decodeEnumsCaseInsensitive`** only lowercases enum names (`JsonNamesMap.kt:27,82-83`). It does not help with field names or integer enums, and it is not needed.
- **`JsonTransformingSerializer` / custom `SerializersModule`:** these apply per serializer, while the generated serializers of nested types are called directly. Wrapping only the root serializer means the transform function would itself have to be the schema walker, so this is option 2 with a different hook.
- **Wrapping the decoder:** `JsonDecoder` is a public interface (`JsonDecoder.kt:55`). But key-to-index resolution happens inside the internal `JsonTreeDecoder`/`StreamingJsonDecoder`, so a wrapper never sees key names. You would have to reimplement the tree decoder (coercion, explicitNulls, polymorphism), which is far too much.
- **`@KeepGeneratedSerializer`, KSP or a compiler plugin:** none of these add aliases cheaply. Rejected.

### Free-form JSON in Koog models that must not be renamed
- `JsonObject` `metadata` on Part×4, Message, Task, Artifact, both update events, `SendMessageRequest` and `CancelTaskRequest` (`model/Parts.kt:40,58,95,112`, `Requests.kt:29,97`, `Task.kt:26,49,74,166`, `Message.kt:49`).
- `DataPart.data: JsonElement` (`Parts.kt:109`).
- `AgentExtension.params: Map<String, JsonElement>` (`AgentCard.kt:168`).
- `AgentCardSignature.header` (`AgentCard.kt:468`).
- The map keys of `securitySchemes`, `SecurityRequirement.schemes` and `scopes` (`AgentCard.kt:175,196,288…380`).
- `ErrorInfo.metadata: Map<String,String>` (`exceptions/ErrorData.kt:37`) and `GenericErrorData.raw` (`:82`).
- `TaskPushNotificationConfig.authentication` is a typed class (`AuthenticationInfo(scheme, credentials)`, `TaskPushNotificationConfig.kt:26,37-39`), not free-form. The walker handles it like any other class.

kotlinx descriptor shapes the walker relies on (1.10.0):
- `JsonObject`: kind `MAP`, serialName `kotlinx.serialization.json.JsonObject` (`JsonElementSerializers.kt:150-152`).
- `JsonElement`: `PolymorphicKind.SEALED` (`:30`).
- The walker treats every `kotlinx.serialization.json.*` descriptor as opaque.

### Koog custom serializers with element-less descriptors
These need a "shape" entry, otherwise the walker would stop and leave nested snake keys alone. That is safe but incomplete.
- `PartSerializer`: `JsonContentPolymorphicSerializer`, SEALED, no elements (`json/.../JsonContentPolymorphicSerializer.kt:76-77`; `core Serialization.kt:174-182`).
- `EventSerializer`, `ResponseEventSerializer`, `TaskEventSerializer`, `SecuritySchemeSerializer`, `OAuthFlowSerializer`: `buildClassSerialDescriptor` with no elements (`Serialization.kt:96-97,184-232`).
- Some wrapper keys are multi-word, so their proto names are snake: `statusUpdate`/`status_update`, `artifactUpdate`, `apiKeySecurityScheme`, `authorizationCode`, `deviceCode`, and others (`Task.kt:52,77`, `AgentCard.kt:222-414`). These only matter when the Koog client decodes, and in the agent card.
- `ErrorDataSerializer`: leave it out. Error details are decoded leniently, and the only multi-word field there is `BadRequest.fieldViolations`.

### Sketch (decode-only, in `a2a-core/.../serialization/`)
```kotlin
@InternalA2AApi
public object ProtoJsonFieldNames {
    // Union/oneof shapes for Koog serializers whose descriptors have no elements.
    private val shapes: Map<String, SerialDescriptor> by lazy {
        fun oneof(name: String, v: Map<String, KSerializer<*>>) = buildClassSerialDescriptor(name) {
            v.forEach { (k, s) -> element(k, s.descriptor, isOptional = true) }
        }
        fun union(name: String, vararg ds: SerialDescriptor) = buildClassSerialDescriptor(name) {
            val seen = mutableSetOf<String>()
            ds.forEach { d -> for (i in 0 until d.elementsCount)
                if (seen.add(d.getElementName(i))) element(d.getElementName(i), d.getElementDescriptor(i), isOptional = true) }
        }
        mapOf(
            PartSerializer.descriptor.serialName to union("PartShape", TextPart.serializer().descriptor,
                FileBytesPart.serializer().descriptor, FileUrlPart.serializer().descriptor, DataPart.serializer().descriptor),
            EventSerializer.descriptor.serialName to oneof("EventShape", /* Task.KIND to Task.serializer(), ... */),
            // ResponseEventSerializer, TaskEventSerializer, SecuritySchemeSerializer, OAuthFlowSerializer likewise
        )
    }

    /** Renames proto (snake_case) keys to their camelCase JSON names where they denote a known field of [descriptor]. */
    public fun normalize(element: JsonElement, descriptor: SerialDescriptor): JsonElement {
        val name = descriptor.serialName.removeSuffix("?")
        if (name.startsWith("kotlinx.serialization.json.")) return element      // free-form: never touch
        val d = shapes[name] ?: descriptor
        return when {
            element is JsonArray && d.kind == StructureKind.LIST ->
                JsonArray(element.map { normalize(it, d.getElementDescriptor(0)) })
            element is JsonObject && d.kind == StructureKind.MAP ->              // keys are data; walk values only
                JsonObject(element.mapValues { (_, v) -> normalize(v, d.getElementDescriptor(1)) })
            element is JsonObject && d.kind == StructureKind.CLASS && d.elementsCount > 0 -> {
                val out = LinkedHashMap<String, JsonElement>()
                for ((key, value) in element) {
                    val camel = if (d.getElementIndex(key) != UNKNOWN_NAME) key else key.protoToJsonName()
                    val idx = d.getElementIndex(camel)
                    if (idx == UNKNOWN_NAME) { out[key] = value; continue }      // unknown: keep, ignoreUnknownKeys drops it
                    if (camel in out) throw SerializationException("Duplicate field '$camel' (camelCase and proto name both present)")
                    out[camel] = normalize(value, d.getElementDescriptor(idx))
                }
                JsonObject(out)
            }
            else -> element                                                      // primitives, enums, SEALED/OPEN, CONTEXTUAL
        }
    }

    // protobuf ToJsonName: drop '_' and uppercase the following char ("oauth2_security_scheme" -> "oauth2SecurityScheme").
    private fun String.protoToJsonName(): String = buildString {
        var up = false
        for (c in this@protoToJsonName) { if (c == '_') up = true else { append(if (up) c.uppercaseChar() else c); up = false } }
    }
}
```
- The sketch converts input-to-camel and never camel-to-snake, because camel-to-snake is ambiguous for names with digits such as `oauth2…`.
- Value class `TransportProtocol` and `Instant` hold primitives, so the `else` branch covers them.

**Hooks (one per direction):**
- Server: `JSONRPCServerTransport.toRequest`, `a2a-transport-core-jsonrpc/.../JSONRPCServerTransport.kt:290-301`. Change it to `JSONRPCJson.decodeFromJsonElement(serializer, ProtoJsonFieldNames.normalize(params, serializer.descriptor))`. The thrown `SerializationException` already maps to `-32602`.
- Client: `JSONRPCClientTransport.toResponse`, `JSONRPCClientTransport.kt:95-106`, which decodes `result` for both unary and streaming. The SSE path decodes the envelope only (`HttpJSONRPCClientTransport.kt:100,119`).
- Optional third hook: `HttpAgentCardResolver`, which uses its own `Json { ignoreUnknownKeys }` and `body<AgentCard>()` (`a2a-client/.../AgentCardResolver.kt:50-60`). It would need to decode via `JsonElement` and then normalize.
- A future REST transport would call the same `normalize` on request bodies. REST query parameters are a separate issue (§11 requires camelCase names, `specification.md:3071`).

**Encoding:** unchanged in every option. None of `@JsonNames`, the normalizer, or `JSONRPCJson` (`Serialization.kt` jsonrpc `:35-39`) affects encoding. Only `namingStrategy` would change encoded names, and that option is rejected.

### Fallback: `@JsonNames` inventory
There are **66** serialized multi-word properties across **30** classes in `a2a-core` (`model/*.kt`, `exceptions/ErrorData.kt`), with 38 distinct names. Interface members and computed getters are excluded.

**Request path (server decode), 25 annotations:**
| Class | Count |
|---|---|
| `SendMessageConfiguration` | 4 |
| `GetTaskRequest` | 1 |
| `ListTasksRequest` | 6 |
| `GetTaskPushNotificationConfigRequest` | 1 (`taskId`) |
| `DeleteTaskPushNotificationConfigRequest` | 1 (`taskId`) |
| `ListTaskPushNotificationConfigsRequest` | 3 |
| `TaskPushNotificationConfig` | 1 (`taskId`) |
| `Message` | 4 (`messageId`, `taskId`, `referenceTaskIds`, `contextId`) |
| `*Part.mediaType` | 4 |

**Client side (responses, events, agent card), 41 more:**
| Classes | Count |
|---|---|
| `AgentCard` | 7 |
| `AgentSkill` | 3 |
| `AgentInterface` | 2 |
| `AgentCapabilities` | 2 |
| OAuth flows | 13 |
| `HTTPAuthSecurityScheme` / `OpenIdConnectSecurityScheme` | 2 |
| `Task` | 1 |
| `Artifact` | 1 |
| Update events | 5 |
| List responses | 4 |
| `BadRequest` | 1 |

The wrapper keys would also need aliases added by hand in `PropertyWrappingPolymorphicSerializer`.

Costs of this fallback:
- `@JsonNames` is `@ExperimentalSerializationApi`, and the a2a modules have no opt-in today.
- It silently lets camel win when both spellings are present.
- New fields are easily forgotten, and a guard test for that would need the same descriptor walker anyway.

### Enums (brief)
- ProtoJSON parsers accept enum names or integers (row 4). Python does both: `json_format.py:954-958`.
- Koog enums such as `TaskState` and `Role` decode by name only, so an integer `status` fails with `-32602`.
- The TCK sends names only (`CORE-LIST-*` samples use strings; there are no numeric enum literals in the `tests/` or `tck/` Python sources).
- Not needed for the TCK. The normalizer could later map JSON numbers to names in its `ENUM` branch, but only if each Koog enum's ordinal equals its proto number. That is unverified; see open questions.

## Koog status
- `JSONRPCJson` sets only `explicitNulls=false`, `encodeDefaults=false`, `ignoreUnknownKeys=true` (`a2a-transport-core-jsonrpc/.../serialization/Serialization.kt:35-39`). There are no `@JsonNames` anywhere in a2a-core.
- Effect: snake keys are dropped silently. For optional fields this gives wrong semantics, e.g. `ListTasksRequest.contextId` (`Requests.kt:78`) is dropped, so the filter is ignored. For required fields it fails: `GetTaskPushNotificationConfigRequest.taskId` (`Requests.kt:109`) gets a missing-field error, i.e. `-32602`.
- The TCK sends snake `task_id`, `context_id`, `history_length`, `page_size`, `page_token`, `status_timestamp_after`, `include_artifacts` at the top level (`tck/transport/jsonrpc_client.py:205-272`, `_helpers.py:17-19`). It sends camelCase inside messages.

## Testability notes
- **a2a-core unit tests for `normalize`:**
  - snake keys at the top level and nested (`message.message_id`, `parts[].media_type`, `configuration.history_length`) are renamed;
  - `metadata` / `data` / extension `params` / `scopes` keys stay byte-identical;
  - unknown snake keys are left as-is;
  - a duplicate spelling throws;
  - an already-camel input is returned unchanged;
  - mixed input works.
- **Round-trip test:** `encode(decode(normalize(snakeJson)))` emits camelCase only.
- **Server transport test:** one JSON-RPC call per request type with snake params (e.g. `ListTasks` with `context_id` filters; `GetTaskPushNotificationConfig` with `task_id` succeeds). Plus one for duplicates giving `-32602`.
- **Client:** a response with `status_update` / `artifact_update` wrapper keys decodes.
- **TCK:** `CORE-LIST-001…005` (`tck/requirements/core_operations.py:256-328`) and the push-config CRUD tests send snake keys (`dispatch.py:72-105`). The Python test server (`a2a-sdk==1.1.0`, `test-python-a2a-server/pyproject.toml:7`) emits camelCase (`MessageToDict`, `preserving_proto_field_name=False`, `server/routes/common.py:36-38`), so client-side snake handling cannot be observed against it. Only unit tests can cover it.

## Discrepancies
- **Python duplicate handling.** Python `ParseDict` accepts both names (`json_format.py:590-592`; the dispatcher uses `ParseDict(..., ignore_unknown_fields=True)`, `server/routes/jsonrpc_dispatcher.py:308-310`). But its duplicate check compares raw JSON keys (`json_format.py:625-632`), so `contextId` plus `context_id` is **not** rejected. The ProtoJSON guide prefers failure. The sketch throws; Python, by my reading, lets the later key win. Either choice is defensible. Pick one and test it.
- **TCK vs spec.** The TCK sends proto-name keys. That is valid input under ProtoJSON parse rules, so it is not a spec violation, but it makes the SUT depend on the dual-name parsing that is spec-required anyway.

## Open questions
- Do Koog enum ordinals match proto numbers (`TaskState`, `Role`)? This only matters if integer-enum input is ever supported. Route to proto-drift.
- Should `HttpAgentCardResolver` share `JSONRPCJson` and the normalizer instead of its own `Json`? This is an architecture choice.
- Do any Koog property names differ from `lowerCamel(proto_name)`? If so, the normalizer will not map them. See `proto-drift.md`.
