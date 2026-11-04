# Should Koog A2A replace its hand-written kotlinx.serialization models with Protobuf-generated models and ProtoJSON?

**Sources checked (2026-11-04):**
- Koog branch `eugenethedev/a2a-1.0`: `a2a/a2a.proto`, which is byte-identical to A2A `v1.0.1` (see `proto-drift.md`).
- GitHub via `gh`: square/wire (`CHANGELOG.md`, `docs/wire_json.md`, `wire-runtime/build.gradle.kts`, issue #3604); streem/pbandk (README, `runtime/build.gradle.kts`, `JsonMessageDecoder.kt`, `buildSrc/Versions.kt`, issues #72 and #297); open-toast/protokt (README); Kotlin/kotlinx-rpc (releases, `docs/.../grpc-limitations.topic`); a2aproject/a2a-java @ `e5483dd` (2026-11-03); a2aproject/a2a-python @ `f666ba3` (2026-11-04).
- Maven Central metadata.
- kotlinlang.org `serialization-protobuf.html`.

**Confidence:**
- High on library capabilities and targets. These were read from the current sources and docs.
- Medium on effort figures. These are estimates.
- JS/wasm bundle-size impact was not measured. Only klib sizes are given.

## Answer (short)
- **No, do not replace the public models.** No KMP protobuf library offers ProtoJSON on all Koog targets with an active, released runtime:
  - **Wire** (healthy) has JSON only on the JVM (Moshi/Gson).
  - **kotlinx-rpc protobuf** has no JSON and no JS/wasm.
  - **protokt** has no JSON.
  - **kotlinx-serialization-protobuf** is binary-only and has no codegen.
  - **pbandk** is the only library with ProtoJSON on every target. Its last release was 0.16.0 on 2024-09-03, it has a single spare-time maintainer, it was built with Kotlin 2.0.20, and it has known ProtoJSON gaps.
- **The hybrid (generated internal wire models plus mappers to the public models) is what a2a-java does.** It uses hand-written `spec` records, protobuf-java `JsonFormat`, and about 43 MapStruct mapper files. That works on the JVM only. For Koog KMP the hybrid means pbandk on all targets, or a JVM-only Wire layer. Either way it is about 1–2 weeks of work to fix a TCK bug that is out of spec.
- **Build the decode-only normalizer** (0.5–1 day, from `phase4-snake-case-input.md`). Add a cheap proto-vs-descriptor drift test. Keep protobuf codegen in reserve for a future **gRPC transport module** (JVM/Android/native), where binary protobuf is the actual wire format.

## Koog targets (from `convention-plugin-ai/.../ai.kotlin.multiplatform.gradle.kts`)
- All a2a modules use `id("ai.kotlin.multiplatform")`, and none sets `koog.target.wasmJs=false`.
- They therefore build for: **JVM, Android (`androidTarget`), JS IR (browser; `a2a-core` re-declares `js(IR){browser()}`), wasmJs (browser and nodejs), iosArm64, iosSimulatorArm64, iosX64.** `a2a-core/build/classes/kotlin` confirms jvm, js, wasmJs and the three iOS targets.
- Koog uses Kotlin 2.3.10 and kotlinx.serialization 1.10.0 (`gradle/libs.versions.toml:17,21`).

## 1. KMP protobuf options

| Library (latest) | Targets vs Koog | ProtoJSON | Codegen in Gradle KMP | Maturity / licence | Runtime klib (js / wasm-js) |
|---|---|---|---|---|---|
| **Square Wire** 7.1.2 (2026-10-27) | Runtime covers all Koog targets: jvm, js, wasmJs (on Maven since issue #3604, 2026-05), ios×3, plus more | **JVM only.** `wire-moshi-adapter` is `kotlin("jvm")` and its reflection is in `jvmMain` (`createRuntimeMessageAdapter`). It accepts both names (`jsonAlternateNames`), and 7.1.0 fixed the repeated-null rejection. It ignores unknown fields. **No JSON on JS, wasm or iOS** | Own Gradle plugin, KMP-aware, emits Kotlin into commonMain. `Struct` maps to `Map<String,*>`, `Timestamp` to `com.squareup.wire.Instant` (an expect class), oneofs to nullable fields or `OneOf` boxes | Very active, Apache-2.0 | about 248 KB / 248 KB, plus okio |
| **pbandk** 0.16.0 (2024-09-03; last commit 2025-10-09) | jvm, android, js, wasmJs, ios×3, and more. Covers all Koog targets | **Yes, on every target**, built on kotlinx-serialization-json. The decoder accepts the json name and the proto name (`JsonMessageDecoder.kt:18-21`). Has Struct, Value, Timestamp, Any. Known gaps (#72, open): duplicate oneof values are last-wins instead of rejected, no base64url, no FieldMask. README calls JS/wasm "experimental" | `protoc` plus the `protoc-gen-pbandk` JVM jar through protobuf-gradle-plugin. No first-class KMP plugin. WKTs are generated as `pbandk.wkt.*` messages, not `JsonObject`/`Instant`. KDoc generation is still an open issue (#297) | **Single maintainer, spare time** (README "Status"). No release for 2 years. Built with Kotlin 2.0.20 and serialization 1.7.2. Whether its wasm klib links with Kotlin 2.3.10 is **unverified**. MIT | about 675 KB / 675 KB |
| **kotlinx-rpc protobuf/gRPC** 0.11.0-grpc-190 (2026-09-18) | JVM, Android, Apple, Linux. **No JS, no wasm** (`grpc-limitations.topic`) | **None.** "Protobuf messages can only be serialized in the binary wire format. JSON encoding … is not available" | buf/protoc Gradle plugin. Well-known types (WKTs) are plain messages. API still changing (nullability rework in grpc-189). The latest release requires **Kotlin 2.4.0**, and Koog is on 2.3.10 | Pre-1.0 and experimental, Apache-2.0 | n/a |
| **protokt** 0.12.3 (2026-09-15) / 1.0.0-beta.13 | JVM, plus JS and native in beta | **None** ("Not yet implemented: Protobuf JSON support") | Own Gradle plugin | Active, Apache-2.0 | n/a |
| **kotlinx-serialization-protobuf** (part of 1.12.0, 2026-10-19) | All targets | **None.** Binary only, proto2 semantics, `@ExperimentalSerializationApi` | **No codegen from .proto.** It only goes the other way, classes to schema (`ProtoBufSchemaGenerator`) | Experimental, Apache-2.0 | n/a |
| **protobuf-java / protobuf-kotlin** 4.37.0-RC1 (2026-10-15) | **JVM only.** On Android, `JsonFormat` needs full protobuf-java, not lite | Reference implementation, in `protobuf-java-util` | protobuf-gradle-plugin, JVM | Google, BSD-3 | n/a |

**Bottom line:** the only KMP ProtoJSON implementation is pbandk, and it is stale. Wire's JSON is tied to the JVM and reflection, so Koog would still need its own ProtoJSON layer for JS, wasm and iOS.

## 2. What Koog would lose or have to change

**Current shape (`a2a-core`):**
- 8 model files, about 1,078 LOC. Across `a2a-core` (models, exceptions, errors): 47 data classes, 9 sealed interfaces plus 2 sealed classes, 4 enums, 14 objects. All have KDoc and `explicitApi()`.
- `serialization/Serialization.kt` (282 LOC) holds 7 custom `KSerializer`s: Part, Event, ResponseEvent, TaskEvent, SecurityScheme, OAuthFlow, base64.

**Idioms that generated code would not reproduce:**
- Sealed `Part` subtypes (`TextPart`/`FileBytesPart`/`FileUrlPart`/`DataPart`), replacing the proto `oneof content`.
- Sealed `Event`, `ResponseEvent` and `TaskEvent`.
- Sealed `SecurityScheme` and `OAuthFlow`.
- `kotlin.time.Instant`, replacing `Timestamp`.
- `JsonObject` for metadata and `JsonElement` for `DataPart.data`, replacing `Struct`/`Value`.
- Typed `In` enum (cookie/header), where the proto has `string location`.
- `TaskState.terminal`.
- Nullable optionals with defaults.
- `ByteArray` equality.

**Reach of the public API:**
- 637 `import ai.koog.a2a.model` lines.
- 49 files in `a2a-server`, about 10 in client and transport, 5 in `agents-features-a2a-*` (Phase 5 integration), and 6 in `examples/simple-examples`.
- `AgentExecutor`, `A2AServer`, `A2AClient` and the Koog agent features all expose these types.
- Tests: about 14.4k LOC of test sources across a2a and the agents-a2a features.

**Option A, full replacement (generated types become the public API):**
- It rewrites every user-facing signature.
- It loses the sealed hierarchies (unless boxed oneofs), `Instant` and `JsonObject`.
- pbandk would also bring its own `pbandk.wkt.Struct` into user code.
- Estimate: **3–4+ weeks**, mostly in server, tests and docs.
- Pre-1.0 breaking changes are allowed, but this would be a large ergonomic regression.

**Option B, hybrid (generated types internal, mapped to the public models):**
- This is a2a-java's design: `spec/` has 84 hand-written classes, and `spec-grpc/.../mapper/` has 43 mapper files plus `ProtoUtils`/`JSONRPCUtils`.
- For Koog: about 44 messages and 2 enums means roughly 1–1.5k LOC of mappers plus round-trip tests, about **1–2 weeks**.
- What it gains is ProtoJSON input fidelity: both names, integer enums, duplicate handling, WKT parsing.
- Costs:
  - The JSON is converted twice (pbandk on top of kotlinx-json, then the mapper).
  - The mappers themselves become a place for new drift.
  - Koog takes a hard dependency on stale pbandk, because Wire offers no JSON outside the JVM.

## 3. Other benefits of protobuf codegen (and cheaper ways to get them)

| Benefit | Real? | Cheaper alternative |
|---|---|---|
| Future gRPC transport (spec binding) | Yes, but it is binary protobuf, not JSON. It is only possible on JVM/Android/native, since no KMP gRPC on JS/wasm exists. | A separate `a2a-transport-*-grpc` module with Wire- or kotlinx-rpc-generated **internal** models and mappers, added when needed. The JSON-RPC models stay unchanged. |
| Exact proto-drift avoidance | Partly. Codegen makes field names exact, but the mappers or the public API still need hand updates. | A test that parses `a2a.proto` and diffs field names against the `SerialDescriptor`s. The normalizer already walks these descriptors. |
| Integer enum input (ProtoJSON MUST) | Yes | The normalizer can map ints using an explicit number table. Note that the Koog `TaskState` ordinals are **not** the proto numbers: `INPUT_REQUIRED` is ordinal 3 but proto 6 (`Task.kt:106-146` vs `a2a.proto:187-196`). |
| Unknown fields (spec §5.7 SHOULD ignore) | Neutral. Koog already sets `ignoreUnknownKeys = true` (`JSONRPCJson`, `transport-core-jsonrpc/.../Serialization.kt:35-39`). ProtoJSON parsers reject unknown fields by default, so Python passes `ignore_unknown_fields=True`. | none needed |

## 4. Risks of switching
- **Ergonomics:**
  - Generated classes have no sealed Part/Event hierarchy and no `Instant`/`JsonObject`.
  - pbandk has no KDoc yet (#297).
  - Generated code does not fit `explicitApi` and ABI validation well, so it has to be internal or excluded.
- **The JSON-RPC envelope stays hand-made** (`JSONRPCMessage`, errors, `ErrorData`). So kotlinx-json stays in the stack either way.
- **Proto3 presence semantics:**
  - Printers omit default values for fields without presence: `""`, `0`, `*_UNSPECIFIED`, empty lists. Examples are `next_page_token` and `page_size`.
  - Matching other SDKs may need `alwaysPrintFieldsWithNoPresence`-like behaviour.
  - Koog's current output (`explicitNulls=false`, `encodeDefaults=false`) differs and is already tuned against the TCK.
- **Dependencies:**
  - pbandk adds about 675 KB of klib per JS/wasm target, a stale Kotlin 2.0.20 build with unverified 2.3.x wasm linking, and a protoc toolchain in the build.
  - Wire adds okio.
  - Codegen also needs the `google/api/*.proto` imports of `a2a.proto` to be available.
- **Supply and maintenance:** pbandk has a bus factor of 1. kotlinx-rpc protobuf is pre-1.0 and requires Kotlin 2.4.

## 5. Normalizer vs protobuf

| | Normalizer (decode-only, schema-aware) | Hybrid with pbandk | Full replacement |
|---|---|---|---|
| Effort | 0.5–1 day (about 100 LOC, 6 shape registrations, tests) | 1–2 weeks | 3–4+ weeks |
| Solves TCK snake_case | Yes, centrally, including mixed input | Yes | Yes |
| ProtoJSON input fidelity | Names yes. Integer enums and dup-key errors can be added. WKTs already handled by Koog serializers | Close to full (pbandk gaps in #72) | Same as hybrid |
| Public API impact | None | None | Large break |
| New deps / targets | None, all targets | pbandk plus protoc. wasm compatibility unverified | Same |
| gRPC readiness | No (not needed for JSON-RPC) | Partial: pbandk has binary but no KMP gRPC | Partial |
| Risk | Low. Reversible, internal (`@InternalA2AApi`) | Medium. Double mapping, stale dependency | High |

## Assessment

| Option | Pros | Cons |
|---|---|---|
| Keep models and add the normalizer | Cheapest. No API change. Works on every target. Fixes the TCK issue and keeps §5.5 camelCase output | Hand-maintained schema, which a drift test mitigates. Partial ProtoJSON (no int enums unless added) |
| 66 `@JsonNames` | No new code path | Noisy. Silent camel-first-wins on duplicates. Easy to forget for new fields |
| Hybrid: pbandk internal plus mappers | Real ProtoJSON on all targets. Public API kept | 1–2 weeks. About 1.5k LOC of mappers. Stale single-maintainer dependency. Double conversion. Bundle size |
| Hybrid: Wire internal (JVM JSON) | Healthy library. Same pattern as a2a-java | No JSON on JS, wasm or iOS, so it cannot be the common decode path |
| Full replacement (pbandk/Wire as the public API) | "Same as Python" | Loses sealed types, `Instant` and `JsonObject`. Breaks all users and Phase 5. No KMP library supports it well |
| kotlinx-rpc protobuf / protokt / kotlinx-serialization-protobuf | gRPC/binary paths for later | No ProtoJSON at all, so they do not apply to the JSON question |

## Recommendation
1. **Do not migrate to protobuf-generated models.** In KMP no library offers maintained ProtoJSON across JVM, Android, JS, wasmJs and iOS. The Python and Java SDKs rely on Google's runtimes, and those are not available for Kotlin non-JVM targets. Java also keeps hand-written public models, which is the same choice Koog made.
2. **Implement the decode-only normalizer** from `phase4-snake-case-input.md`. Optionally extend it with ProtoJSON integer enum input, using an explicit proto-number table because the ordinals differ, and with a duplicate camel/snake key error.
3. **Add a proto-drift test.** It parses `a2a/a2a.proto` message and field names, maps them to lowerCamel, and asserts that each name exists in the corresponding `SerialDescriptor`. This gets most of codegen's drift protection for about half a day.
4. **Revisit codegen only for a gRPC transport.** It would be a separate module with internal generated models (Wire or kotlinx-rpc once on stable Kotlin) and mappers, on JVM/Android/native, leaving the JSON-RPC models untouched. Re-check pbandk's maintenance before any ProtoJSON-on-KMP plan.
