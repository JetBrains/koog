# What must/should an A2A server do about HTTP caching headers on the agent card endpoint, and what does the TCK check?

**Sources checked:**
- A2A spec repo `a2a-protocol` @ `v1.0.1` (3303592) and `main` @ 4735f48 (v1.0.1-92, 2026-10-30). The caching text is the same in both; only the line numbers differ.
- a2a-tck @ d4d6fae. The research checkout `main` and the Koog pin (`a2a/test-tck/setup_tck.sh:9`, `a2a/test-tck/a2a-tck` HEAD) are the same commit.
- a2a-tck PRs #235 and #237 and issues #234 and #236, read with `gh` on 2026-11-04.
- a2a-python @ f666ba3 (v1.3.0-14), compared against tags v1.1.0, v1.2.0 and v1.3.0.
- Ktor 3.3.3 source jars (`ktor-server-conditional-headers`, `ktor-server-caching-headers`, `ktor-server-core`, `ktor-http`, `ktor-utils`) from Maven Central.

**Confidence:** high. The spec text is short and clear, the TCK test is trivial, and I read the Python and Ktor behavior directly in their source.

## Answer
- **Spec:** none of the agent card caching headers is a MUST.
  - `Cache-Control` with `max-age`: SHOULD.
  - `ETag` derived from `version` or a content hash: SHOULD.
  - `Last-Modified`: MAY.
  - The spec never says the server must answer `If-None-Match` or `If-Modified-Since` with 304. It only says clients SHOULD send those headers, and that both sides SHOULD use standard HTTP caching (RFC 9111).
  - For the extended card there is only a vague "Agents SHOULD implement appropriate caching headers".
- **TCK at the pin:** the tests only check that the headers are present.
  - `Cache-Control` exists, and its value contains the substring `max-age`.
  - `ETag` exists.
  - `Last-Modified` exists.
  - There is no format, value or 304 check. All four fail as plain pytest failures; no xfail or skip is implemented, even for the MAY test.
  - PR #237 would turn a missing `Last-Modified` into a SKIP. It is still OPEN and unmerged, so at the pin Koog has to send `Last-Modified` to get a green run.
- **Python SDK** (since v1.2.0):
  - Always sends a weak ETag: `W/"sha256(card JSON with keys sorted and signatures removed)"`.
  - Sends `Cache-Control` only when `cache_control: str` is passed. There is no default.
  - Answers a matching `If-None-Match` (or `*`) with a 304.
  - Never sends `Last-Modified` and ignores `If-Modified-Since`.
- **Ktor:** set the headers by hand in the route. Do not install `ConditionalHeaders` or `CachingHeaders` from inside the route: that breaks users who installed the plugin at application level, and it leaks state across routes.

## Requirements
| # | Requirement | Tier | Bindings | Citation |
|---|---|---|---|---|
| R1 | "Servers and clients **SHOULD** use standard HTTP caching mechanisms" | SHOULD | all (card is plain HTTP GET) | a2a-protocol `docs/specification.md` §8.6, v1.0.1:2215 / main:2246 |
| R2 | "Agent Card HTTP endpoints **SHOULD** include a `Cache-Control` response header with a `max-age` directive appropriate for the agent's expected update frequency" | SHOULD | all | §8.6.1, v1.0.1:2219 / main:2250 |
| R3 | "…**SHOULD** include an `ETag` response header derived from the Agent Card's `version` field or a hash of the card content" | SHOULD | all | §8.6.1, v1.0.1:2220 / main:2251 |
| R4 | "…**MAY** include a `Last-Modified` response header" | MAY | all | §8.6.1, v1.0.1:2221 / main:2252 |
| R5 | Clients SHOULD honor RFC 9111. When the cache has expired, clients SHOULD send `If-None-Match` (with the stored ETag) or `If-Modified-Since`. If the server sent no caching headers, clients MAY use their own default lifetime. | SHOULD/MAY (client) | all | §8.6.2, v1.0.1:2224-2227 / main:2256-2258 |
| R6 | No explicit server rule for answering conditional requests with 304. This follows only from R1 plus RFC 9110 §13 by inference. | (implied by R1) | all | absent from §8.6; `rg 304` finds nothing relevant |
| R7 | Extended card: "Agents **SHOULD** implement appropriate caching headers to control client-side caching of extended cards" | SHOULD, capability:`extendedAgentCard` | JSON-RPC (POST), HTTP+JSON `GET /extendedAgentCard`, gRPC | §13.3, v1.0.1:3145 / main:3174 |
| R8 | Clients SHOULD swap their cached public card for the extended card for the length of the session or until `version` changes. Agents SHOULD version extended cards and honor cache invalidation. | SHOULD | all | §3.1.11 main:425; §13.3 v1.0.1:3158-3159 / main:3187-3188 |

`docs/topics/agent-discovery.md:96-106` restates R2, R3 and R5 in lowercase "should". It is explanatory only.

## Details

### TCK (`tests/compatibility/agent_card/test_agent_card_caching.py` @ d4d6fae)
- **Fetch:** `GET {sut_host}/.well-known/agent-card.json` via httpx, with no request headers and `raise_for_status()` (:59-65). The path is hardcoded, so the card must live at the well-known path on the root of `--sut-host`.
- **Checks:**

  | Test | Line | Assertion |
  |---|---|---|
  | `test_cache_control_present` | :78-98 | `cache-control` header is not None |
  | `test_cache_control_has_max_age` | :100-121 | `"max-age" in cache_control.lower()` (substring only, value not parsed) |
  | `test_etag_present` | :134-154 | `etag` header is not None (any format, weak or strong) |
  | `test_last_modified_present` | :167-187 | `last-modified` header is not None (date format not validated) |

  There is no `If-None-Match`, `If-Modified-Since` or 304 test anywhere in the TCK: `rg -i "etag|if-none-match|last-modified"` finds only this file and the requirement definitions.
- **Requirement levels:** `tck/requirements/agent_card.py:186-226`.
  - CARD-CACHE-001: SHOULD, §8.6.1, "Cache-Control header with max-age present".
  - CARD-CACHE-002: SHOULD.
  - CARD-CACHE-003: MAY.
- **Level markers have no effect on the outcome:**
  - `@should` and `@may` are bare `pytest.mark` aliases (`tests/compatibility/markers.py:18-19`).
  - The `run_tck.py:97-100` help says SHOULD means xfail and MAY means skip, but nothing implements that. The only hook is `pytest_runtest_makereport` (`tests/compatibility/conftest.py:289+`), and it only records results.
  - `LEVEL_FILTERS` (`run_tck.py:26-31`) is just a `-m` filter. So all four are hard failures, which matches the Koog run.
- **PR #237** (OPEN, head fb8b2b4, last updated 2026-10-16, no review decision; fixes #236):
  - Changes only `test_last_modified_present`.
  - If the header is absent, it records SKIPPED and calls `pytest.skip()`.
  - CARD-CACHE-001 and -002 are unchanged.
- **PR #235** (OPEN, head 4c553fb, 2026-09-04, fixes #234; note that #234's current title is about xfail and report mismatches):
  - Patches only the Python SUT: `codegen/a2a-python/sut_agent.py.j2` and `sut/a2a-python/sut_agent.py`.
  - Wraps `create_agent_card_routes` and adds `Cache-Control: public, max-age=3600` plus a strong ETag (`"` + 16 hex chars of sha256(json.dumps(card, sort_keys=True)) + `"`).
  - Omits `Last-Modified` on purpose (#236).
  - Effect: even the Python reference SUT at the pin fails CARD-CACHE-001, because `sut/a2a-python/sut_agent.py:247` calls `create_agent_card_routes(agent_card=agent_card)` without `cache_control`. It also fails CARD-CACHE-003.

### Python SDK (`src/a2a/server/routes/agent_card_routes.py` @ f666ba3; same since v1.2.0, added in #1272 / 83d7f5d)
- **Signature** (:76-81): `create_agent_card_routes(agent_card, card_modifier=None, card_url=…, cache_control: str | None = None)`.
- **No default `Cache-Control`.** The docstring (:88-92) explains why: a `max-age` "has to suit the agent's expected update frequency, which only the deployment knows, so there is no default. The `ETag` is sent either way".
- **ETag:**
  - Computed per request from the card after `card_modifier` runs (:106-111).
  - Formula (:35-60): `W/"<sha256 hex of json.dumps(card minus 'signatures', sort_keys=True, separators=(',', ':'))>"`.
  - It is weak so that a card re-signed on each request (ES256 signatures are randomized) still revalidates.
  - It is a content hash rather than `version`, because `card_modifier` can change the content without changing `version`.
- **Conditional requests** (:115-119, :63-73):
  - A comma-split `If-None-Match` is compared with weak comparison, and `*` also matches.
  - On a match the route returns `Response(304)` with an empty body and the same `ETag` and `Cache-Control` headers.
  - `If-Modified-Since` and `If-Match` are not handled.
  - No `Last-Modified` header is sent.
- **Tests:** `tests/server/routes/test_agent_card_routes.py:94-239` cover the weak tag, Cache-Control only when configured, the ETag changing with the card or the modifier, 304 for exact, unprefixed, `*` and list forms, 200 for a stale tag, and a stable tag across re-signing.
- **Extended card:** no caching headers on either the JSON-RPC or REST path. `rg -i cache src/a2a/server` finds only the card route.
- **Version notes:**
  - v1.1.0 (`a2a/test-python-a2a-server/pyproject.toml:7` pins `a2a-sdk==1.1.0`) sends no caching headers at all, so Koog client integration tests will not see an ETag.
  - The Python client streaming helper sends `Cache-Control: no-store` on SSE requests (`src/a2a/client/transports/http_helpers.py:186-187`). That does not affect the card route.

### Ktor 3.3.3: plugins vs manual
- **Both plugins are route-scoped:**
  - `ConditionalHeaders` and `CachingHeaders` are `RouteScopedPlugin`s (`ConditionalHeaders.kt:85`, `CachingHeaders.kt:59`).
- **Problem 1: installing a plugin on a route throws if the user already has it on the `Application`.**
  - `ApplicationPlugin.kt:155-159` throws `DuplicatePluginException("Installing RouteScopedPlugin to application and route is not supported…")`.
  - So if the Koog route installs a plugin, any user who has `install(ConditionalHeaders)` or `install(CachingHeaders)` at application level crashes at startup.
  - `:149-153` also throws when the plugin is installed twice on the same route node. That bites if two cards are mounted on one route node; in practice `route(path)` gives each card its own child node, so this is minor.
- **Problem 2: `ConditionalHeaders` writes app-global state even when installed on a route.**
  - It does `application.attributes.put(VersionProvidersKey, …)` (:91), and `versionsFor` reads that application attribute (:54-57).
  - So a route-scoped install replaces the version providers of an app-wide install, and the last route to install wins.
- **Problem 3: `ConditionalHeaders` can return a wrong 304 when both conditional headers are sent.**
  - It checks every version in turn, and the first non-OK result wins (:93-101).
  - With both `If-None-Match` and `If-Modified-Since`, a non-matching ETag still falls through to the date check, which can return 304. RFC 9110 §13.1.3 says `If-Modified-Since` must be ignored when `If-None-Match` is present.
- **Ktor's `EntityTagVersion` has two quirks of its own** (`ktor-http Versions.kt`):
  - `If-None-Match: *` returns OK rather than 304 (:236).
  - `parse()` uses `check()` and throws `IllegalStateException` on entity tags carrying quality or other parameters (:283-284).
  - It does use weak comparison for `If-None-Match` (:238).
- **`CachingHeaders` appends rather than sets `Cache-Control`** (:84-86). If a user has it app-wide with a JSON rule, the response gets duplicate values. That cannot be avoided from inside the route; document it.
- **Manual helpers are available in common code:**
  - `call.response.etag(String)` and `call.response.cacheControl(CacheControl)` (`ktor-server-core …/response/ApplicationResponseProperties.kt:37,44`).
  - `CacheControl.MaxAge(maxAgeSeconds, …, visibility)` (`ktor-http CacheControl.kt:90`).
  - `GMTDate.toHttpDate()` and `String.fromHttpToGmtDate()` (`ktor-http`).
  - `io.ktor.util.sha1(ByteArray)` and `hex()` are `expect`/`actual` on every platform (`ktor-utils Crypto.kt:24,78`). Koog has no SHA-256 library (`gradle/libs.versions.toml`). SHA-1 is fine for an opaque validator the server itself compares.
- **Strong vs weak ETag:**
  - A strong tag over the exact response bytes is technically correct only if nothing re-encodes the body. Compression middleware (Ktor `Compression`, reverse proxies) can do that. *Hypothesis, not verified:* Ktor `Compression` does not rewrite the `ETag`.
  - A weak tag (`W/"…"`) stays correct under re-encoding, matches Python, and is all `If-None-Match` needs, since that header uses weak comparison (RFC 9110 §13.1.2).
  - Recommendation: weak.
- **`Last-Modified` source:**
  - The card has no timestamp, so the value has to be either the time the route was built (roughly process start) or a value the user configures.
  - Using the build time is safe because the ETag takes precedence: clients that send `If-None-Match` never rely on the date.
  - Across restarts or replicas, the worst case is an extra 200. The one real inaccuracy: a replica whose build time is older than a client's `If-Modified-Since` returns a 304 for different content. That only affects clients that send `If-Modified-Since` without `If-None-Match`, and only during a rolling deploy.

### Proposed API (needs user approval: public API change)
```kotlin
public fun Route.a2aAgentCardRoute(
    path: String,
    agentCard: AgentCard,
    cacheMaxAge: Duration? = 1.hours,          // null = no Cache-Control
    lastModified: Instant? = <route build time>, // null = no Last-Modified
): Route
```
- **On route build** (the card is static today, `Routes.kt:150-161`):
  - `body = JSONRPCJson.encodeToString(AgentCard.serializer(), agentCard)`.
  - `etag = "W/\"" + hex(sha1(body.encodeToByteArray())) + "\""`.
  - Truncate `lastModified` to whole seconds.
  - Serving `body` with `respondText(body, ContentType.Application.Json)` replaces `call.respond(agentCard)` and the route-level `install(ContentNegotiation)` (:154-156). That guarantees the ETag matches the exact bytes sent, and it also removes the existing route-scoped `ContentNegotiation` install. That install has the same `DuplicatePluginException` problem described under Ktor above (see Open questions).
- **Per GET:**
  1. Always set `ETag`.
  2. If `cacheMaxAge != null`, set `Cache-Control: max-age=<seconds>`. Do not add `public`: under RFC 9111 §3.5, `public` lets shared caches store responses to requests carrying `Authorization`, and users may mount the route under `authenticate {}`.
  3. If `lastModified != null`, set `Last-Modified`.
  4. If `If-None-Match` is present: return 304 with no body if any comma-separated entry, after stripping `W/`, equals the opaque tag, or the header is `*`. Otherwise send 200. Ignore `If-Modified-Since` in this case.
  5. If `If-None-Match` is absent and `If-Modified-Since` parses and is at or after `lastModified`: return 304. Ignore dates that do not parse (RFC 9110 §13.1.3).
  6. A 304 carries the same `ETag`, `Cache-Control` and `Last-Modified` headers (RFC 9110 §15.4.5).
- **Dynamic cards:** if a card-provider overload is added later (for example `agentCard: suspend ApplicationCall.() -> AgentCard`), serialize and hash on every request, and set `Last-Modified` only when the user supplies a value. A build-time date would be wrong for a card that changes. Today Koog takes only a static `AgentCard`, so this is not needed now.
- **`HttpJSONRPCServerTransport.start`** (`HttpJSONRPCServerTransport.kt:78-108`) mounts the card via `a2aAgentCardRoute(agentCardPath, agentCard)` (:105-107). Add the same two parameters and pass them through.
  - The Koog SUT uses `start(…, agentCard = agentCard, agentCardPath = A2APaths.AGENT_CARD_WELL_KNOWN_PATH)` (`test-tck/a2a-test-server-tck/src/main/kotlin/ai/koog/a2a/test/tck/Main.kt:57-67`). With non-null defaults it passes all four TCK tests without changes. With null defaults it must pass explicit values.
- **Extended card (R7):**
  - Koog serves it inside the shared JSON-RPC `POST` handler (`Routes.kt:110-144`). POST responses are rarely reusable by HTTP caches (RFC 9111 §3 / RFC 9110 §9.3.3; the per-response explicit-freshness exception aside).
  - Python sets nothing on this path either. Recommend no change.

## Koog status
- `a2a-transport/a2a-transport-server-jsonrpc-http/src/commonMain/kotlin/ai/koog/a2a/transport/server/jsonrpc/http/Routes.kt:150-161` installs `ContentNegotiation` on the route and calls `call.respond(agentCard)`. It sends no `Cache-Control`, `ETag` or `Last-Modified`, and has no conditional-request handling.
- This is the only place the card is served. `HttpJSONRPCServerTransport.start` delegates to it (:105-107), and nothing else in the repo calls `a2aAgentCardRoute`.
- The gap is SHOULD/MAY level only. It explains all four TCK failures.

## Testability notes
- **Unit tests:** use `testApplication` in `a2a-transport-server-jsonrpc-http/src/jvmTest` (the same pattern as `HttpJSONRPCServerTransportTest.kt:151+`). Cover:
  1. The 200 response has a weak `ETag`, `Cache-Control: max-age=3600`, and a `Last-Modified` in HTTP-date format.
  2. `cacheMaxAge = null` means no `Cache-Control`, and `lastModified = null` means no `Last-Modified`.
  3. A different card gets a different ETag.
  4. `If-None-Match` matching (exact, without the `W/` prefix, `*`, as part of a list) returns 304 with no body and the headers present.
  5. A stale `If-None-Match` returns 200.
  6. `If-Modified-Since` at or after `lastModified` returns 304. An earlier date returns 200. An unparsable date returns 200.
  7. A non-matching `If-None-Match` together with a fresh `If-Modified-Since` returns 200, because `If-None-Match` takes precedence.
  8. The route works when the user has `ConditionalHeaders` and `ContentNegotiation` installed at application level (regression test for `DuplicatePluginException`).
- **TCK:** CARD-CACHE-001 (two tests), -002 and -003 in `tests/compatibility/agent_card/test_agent_card_caching.py`. Presence-only, so they pass with any values. The TCK never exercises 304 handling.

## Discrepancies
- **TCK vs spec, CARD-CACHE-003:** the requirement is MAY, but the test hard-fails when the header is absent (issue #236). PR #237 would fix it but is unmerged.
- **TCK `run_tck.py` help vs behavior:** the help says SHOULD means xfail and MAY means skip, but that is not implemented. SHOULD and MAY tests fail like MUST tests.
- **Python SDK vs spec R2:** the SDK satisfies the SHOULD only when the deployer passes `cache_control`. The TCK's own Python SUT does not pass it, so the reference SUT fails CARD-CACHE-001 and -003 at the pin. PR #235 fixes 001 and is open.
- **Python and TCK PR #235 differ on ETag style:** the Python SDK uses a weak sha256 tag; the SUT PR uses a strong tag truncated to 16 hex characters. Both are valid under the spec ("hash of the card content").

## Open questions
1. **Default `cacheMaxAge`.**
   - Python ships no default, on purpose.
   - The only concrete upstream value is `max-age=3600`, used in the Python docstring example and in TCK PR #235.
   - Recommendation: default to `1.hours`, so the SHOULD is met out of the box and the SUT needs no changes. Revalidation through the ETag keeps refetch cost low. The alternative is Python parity (`null`) with the SUT passing a value explicitly.
2. **Default `Last-Modified`.**
   - Recommendation: default to route build time, so the TCK at the pin goes fully green, with `null` to opt out.
   - The alternative is to default to `null`, set it only in the SUT, and wait for #237. That is more honest with respect to the MAY, but standalone users running the TCK would see a FAIL.
3. **Public API shape needs user confirmation** (architecture rule in `a2a/CLAUDE.md`).
   - `Duration?` vs Ktor `CacheControl?` vs a raw `String?` (Python style). Recommendation: `Duration?`; a raw string invites `public`/`no-store` mistakes.
   - Whether to add a dynamic card-provider overload now. Recommendation: not now.
4. **Adjacent, route elsewhere:** the existing route-scoped `install(ContentNegotiation)` in both Koog routes (`Routes.kt:105-107, 154-156`) throws `DuplicatePluginException` for users who installed `ContentNegotiation` on the `Application`. Fixing the card route as proposed removes it there. Whether to fix the JSON-RPC route the same way is a separate question.
5. **Adjacent, route elsewhere:** the Koog client side of R5 (does `a2a-client`'s card resolver honor `Cache-Control` and send `If-None-Match`?). Not examined.
6. **Minor:** `ETag` is not a CORS-safelisted response header. Browser JS that wants to read it needs `Access-Control-Expose-Headers: ETag` in the standalone server's `CORS` config (`HttpJSONRPCServerTransport.kt:98-101`). Browsers' built-in HTTP cache revalidates without it.
