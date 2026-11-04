# Snake_case JSON-RPC params: who deviates from the A2A spec, the TCK or Koog?

**Sources checked:** a2aproject/A2A @ `v1.0.1` (`3303592`), `v1.1.0-rc` (`78e1d6a`), `origin/main` (`4735f48`, 2026-10-30); a2aproject/a2a-tck @ `d4d6fae` (= Koog pin = remote `main` HEAD as of 2026-11-02); a2aproject/a2a-python @ `main` `6ca8593` (v1.3.0) and `v1.1.0` (Koog test-server pin); ProtoJSON guide (protocolbuffers.github.io `content/programming-guides/json.md` @ `d75b49a`); protobuf `python/google/protobuf/json_format.py` (main); a2a-js `src/types/protojson.ts` @ `ce10234`; a2a-go and a2a-java via GitHub code search; GitHub issues read only.
**Confidence:** high for the TCK side, which breaks an explicit MUST that the TCK itself codifies. Medium-high for the Koog side: whether a receiver must accept snake_case depends on whether ADR-001 is normative, and the spec text does not settle that.

## Answer
The TCK deviates. Spec §5.5 says "All JSON serializations of the A2A protocol data model **MUST** use camelCase". The TCK's JSON-RPC client puts `history_length`, `context_id`, `page_size`, `page_token`, `status_timestamp_after`, `include_artifacts` and `task_id` in `params`, so it breaks that MUST. The TCK's own DM-SERIAL-001 (MUST, §5.5) and its HTTP+JSON client both use camelCase. Payload objects (`message`, `configuration`) are camelCase.

No RFC 2119 requirement in the A2A spec text says a receiver must accept snake_case. That duty exists only in ProtoJSON ("parsers are required to accept both"). A2A brings ProtoJSON in only through a non-normative "Note" in §5.5 that points to ADR-001, and ADR-001 says "must" in lowercase in a rationale document. So Koog does not break any spec MUST, SHOULD or MAY. It is still not a strict ProtoJSON parser. Spec issue A2A#2087 (open, no maintainer reply yet) asks for exactly this accept-side MUST to be added. **Verdict: (i), with a caveat on Koog's side.**

## Requirements
| # | Requirement | Tier | Bindings | Citation |
|---|---|---|---|---|
| R1 | JSON serializations of the data model use camelCase field names | MUST (emitter) | JSON-RPC, HTTP+JSON | A2A `docs/specification.md:1204` §5.5 @ v1.0.1; same line on main; `:1395` @ v1.1.0-rc |
| R2 | Enums follow ProtoJSON (string names) | MUST | JSON-RPC, HTTP+JSON | `specification.md:1215` §5.5 @ v1.0.1 |
| R3 | "This follows the ProtoJSON specification as adopted in ADR-001" | Note (no RFC 2119 keyword) | JSON | `specification.md:1222` §5.5 @ v1.0.1 |
| R4 | Query parameter names are camelCase, "consistency with request bodies used in POST operations" | MUST | HTTP+JSON only (not relevant to Koog) | `specification.md:2857` §11.5 @ v1.0.1 |
| R5 | Ignore unrecognized fields | SHOULD (receiver) | all | `specification.md:1275` §5.7 @ v1.0.1 |
| R6 | Receiver accepts the proto (snake_case) name | **Not in spec text.** ProtoJSON "required"; ADR-001 lowercase "must" | JSON | ADR `adrs/adr-001-protojson-serialization.md:34,62`; ProtoJSON `json.md:257-261,749-754` |
| R7 | The spec's normative sources are the spec text and `a2a.proto` (ADRs are not listed) | — | — | `specification.md:105-107` §1.4 @ v1.0.1 |

## Details
**1. What the A2A spec says** (§5.5 is identical in v1.0.1, v1.1.0-rc and main)
- Senders: "All JSON serializations of the A2A protocol data model **MUST** use **camelCase** naming for field names, not the snake_case convention used in Protocol Buffer definitions." (`:1204`)
  - Its examples include `context_id → contextId`.
  - The `GetTask` example in §9.4.3 (`:2332-2348`) sends `"historyLength": 10` in `params`.
  - §1.4 counts "request/response messages" as part of the proto-defined model (`:107`), so `GetTaskRequest` and similar `params` objects are covered.
- Receivers: there is no accept-side statement. The only receiver-side rule is §5.7, "Implementations **SHOULD** ignore unrecognized fields" (`:1275`). A camelCase-only parser that follows §5.7 therefore drops snake_case keys silently. That is what Koog does.
- ProtoJSON link:
  - Spec text: "**Note:** This follows the ProtoJSON specification as adopted in ADR-001" (`:1222`). This is an informative note with no keyword.
  - ADR-001 (`:34`): "The TSC has decided to leverage the ProtoJSON specification as the normative approach to serializing JSON based on the proto definition".
  - ADR-001, Consequences/Neutral (`:62`): "Implementations must follow ProtoJSON specification strictly".
  - ADRs are design rationale, not listed as normative in §1.4. Read strictly, ProtoJSON would also *reject* unknown fields by default (`json.md:745-747`), which §5.7 contradicts. So A2A does not adopt ProtoJSON wholesale.
- JSON-RPC binding §9: has no naming rules of its own. Every example uses camelCase.

**2. ProtoJSON, verbatim** (`content/programming-guides/json.md`)
- Lines 257-261: "Parsers accept both the lowerCamelCase name (or the one specified by the `json_name` option) and the original proto field name. This allows for a serializer option to choose to print using the original field name (see JSON Options) and have the resulting output still be parsed back by all spec parsers."
- Lines 749-754: "By default the Protobuf JSON printer should convert the field name to lowerCamelCase and use that as the JSON name. An implementation may provide an option to use proto field name as the JSON name instead. Protobuf JSON parsers are required to accept both the converted lowerCamelCase name and the proto field name."
- ProtoJSON does not use RFC 2119. "Should" (printer) and "are required to" (parser) are plain English.

**3. Verdict reasoning**
- TCK breaks R1 (MUST). The offending call sites in `tck/transport/jsonrpc_client.py` @ `d4d6fae`:
  - `:212` `get_task` sends `history_length`.
  - `:227-235` `list_tasks` sends `page_size`, `page_token`, `history_length`, `status_timestamp_after`, `include_artifacts`, `context_id`.
  - `:252`, `:257`, `:272` push-config calls send `task_id`.
  - `:267` list push configs sends `task_id`, `page_size`, `page_token`.
  - `_build_params` passes kwargs through unchanged (`tck/transport/_helpers.py:17-19`).
- The rest of the TCK is camelCase:
  - The HTTP+JSON client uses camelCase (`http_json_client.py:265,284-292,343`).
  - The TCK's own DM-SERIAL-001 is a MUST on camelCase (`tck/requirements/data_model.py:122-133`).
  - Message payloads in tests are camelCase (`messageId`, `taskId`, `configuration={"historyLength": 0}`, e.g. `tests/compatibility/core_operations/test_task_history.py:146-155`).
- Koog breaks no RFC 2119 requirement in the spec. It does deviate from ProtoJSON's parser rule (R6), which A2A reaches only through the ADR and the Note. So: "not a spec violation; a ProtoJSON-strictness and interop-robustness gap". (ii) and (iii) are not supported by the spec text. (iv) applies only to the receiver half, and A2A#2087 tracks it.

**4. Upstream state**
- TCK `main` has no commits after `d4d6fae` (`git ls-remote` HEAD = `d4d6fae`).
- [a2a-tck#242](https://github.com/a2aproject/a2a-tck/issues/242), open, 2026-09-19: "JSON-RPC client sends history_length (snake_case) instead of historyLength". The reporter is an a2a-go user. A third party confirmed it statically. No maintainer response.
- [a2a-tck#243](https://github.com/a2aproject/a2a-tck/pull/243), open PR: fixes **only** `get_task`. No reviews. `list_tasks` and the push-config `task_id`/`page_*` keys are left as they are.
- [a2a-tck#109](https://github.com/a2aproject/a2a-tck/pull/109), merged: the same fix for the REST client. This is the closest thing to a maintainer stance: camelCase is correct.
- [a2a-tck#97](https://github.com/a2aproject/a2a-tck/issues/97), open: proposes camelCase for REST.
- [a2a-tck#145](https://github.com/a2aproject/a2a-tck/issues/145), open: push-config field structure (related, not the same issue).
- [A2A#2087](https://github.com/a2aproject/A2A/issues/2087), open, 2026-07-30: "§5.5 states only the emit side of ProtoJSON — ADR-001's strictness requirement (accept integer enums and snake_case field names) is unstated". It proposes "MUST accept the original proto field name". A contributor offered a PR. No maintainer reply, no linked PR. No relevant A2A discussions were found.

**How other SDKs parse snake_case input:**

| SDK | Accepts snake_case input? | Evidence |
|---|---|---|
| Python, pinned v1.1.0 | Yes, unknown fields rejected | `ParseDict(params, model_class())` (`src/a2a/server/routes/jsonrpc_dispatcher.py:291`) |
| Python, main/v1.3.0 | Yes, unknown fields ignored | `ParseDict(..., ignore_unknown_fields=True)` (`:308-309`). Both versions resolve names by json_name, then by proto name (protobuf `json_format.py:604-630`) |
| Java | Yes (inferred, not re-read) | `JsonFormat.parser()` (`spec-grpc/.../JSONRPCUtils.java` `parseJsonString`); protobuf-java JsonFormat accepts both names per ProtoJSON |
| JS | Yes | Explicit fallback `isSet(object.historyLength) ? … : isSet(object.history_length) ? …` (`src/types/protojson.ts:173-177,1647-1651`) |
| Go | **No, camelCase only** | Hand-written structs with `json:"historyLength"` tags (`a2a/core.go`); confirmed in practice by #242 |

The Python and Java SUTs hide the TCK bug, which is why it has survived.

## Koog status
- Not re-examined in depth. Verified: `ignoreUnknownKeys = true` (`a2a-transport/a2a-transport-core-jsonrpc/src/commonMain/kotlin/ai/koog/a2a/transport/jsonrpc/serialization/Serialization.kt:38`), and `GetTaskRequest(id, historyLength, tenant)` has no alternative names (`a2a-core/src/commonMain/kotlin/ai/koog/a2a/model/Requests.kt:58-62`).
- Result: `history_length`, `context_id` and the other snake keys are dropped silently. This follows §5.7.
- Emitting camelCase is correct per R1.
- See `phase4-snake-case-input.md` for the full list of affected fields.

## Testability notes
- The TCK checks that are affected include CORE-HIST-002 (GetTask history limit), ListTasks filtering by context and paging, and push-config CRUD over JSON-RPC.
- Koog unit test, if Koog decides to accept both names: decode `{"id":"t","history_length":1}` and assert `GetTaskRequest(id="t", historyLength=1)`; also assert that encoding still emits only camelCase.
- No TCK test checks that a server accepts snake_case on purpose. The current behavior is accidental.

## Discrepancies
- The TCK's JSON-RPC client contradicts spec §5.5 MUST, the TCK's own DM-SERIAL-001, and the TCK's own HTTP+JSON client.
- Spec §5.5 (emit-only) vs ADR-001 ("must follow ProtoJSON strictly") vs ProtoJSON (parsers required to accept both names, and reject unknown fields by default) vs spec §5.7 (SHOULD ignore unknown fields). The receiver duty is undefined in the spec text and is the subject of A2A#2087.
- The reference SDKs disagree: Python, Java and JS accept snake_case; Go does not.

## Open questions
- **Recommendation (needs the user's decision under the a2a CLAUDE.md architecture rule):**
  - (a) Optionally accept proto names on decode, e.g. with kotlinx `@JsonNames("history_length")` on the affected request fields. This changes decoding only; encoding stays camelCase. It matches ProtoJSON and Python/Java/JS and unblocks the TCK now. Not required by the spec text.
  - (b) In either case, push the TCK fix. Do not file a duplicate of #242. Instead comment on #242 / PR #243 that the fix is incomplete, or file a follow-up issue. Draft below.
- If A2A#2087 is accepted, (a) becomes a MUST. Integer-enum acceptance would then also be needed (Koog presumably rejects `"role": 1`; not examined).

**Draft TCK issue/comment (not filed):**
> **Title:** JSON-RPC client still sends snake_case params beyond `history_length` (ListTasks, push-config methods)
>
> Spec §5.5 (v1.0.1, `docs/specification.md:1204`): "All JSON serializations of the A2A protocol data model **MUST** use camelCase naming for field names". The TCK codifies this as DM-SERIAL-001, and `http_json_client.py` already complies. But `tck/transport/jsonrpc_client.py` @ `d4d6fae` emits Python kwarg names directly into `params`:
> - `:212` `history_length` (GetTask; PR #243 fixes only this one)
> - `:227-235` `page_size`, `page_token`, `history_length`, `status_timestamp_after`, `include_artifacts`, `context_id` (ListTasks)
> - `:252,:257,:272` `task_id`; `:267` `task_id`, `page_size`, `page_token` (push-config methods)
>
> Servers built on protobuf `ParseDict`/`JsonFormat` (Python, Java SUTs) accept both names, which masks the bug. Camel-only servers following §5.7 ("SHOULD ignore unrecognized fields") drop these keys silently, so history limits, context filters, paging and push-config task scoping are lost, and tests such as CORE-HIST-002 fail against spec-compliant servers (see #242 for a2a-go).
>
> Proposed fix: map every JSON-RPC param to its camelCase wire name (`historyLength`, `contextId`, `pageSize`, `pageToken`, `statusTimestampAfter`, `includeArtifacts`, `taskId`), and add a unit test asserting the emitted `params` dict, as #243 does for GetTask. Extends #242 / #243.
