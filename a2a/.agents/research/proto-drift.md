# Is the vendored `a2a/a2a.proto` up to date with the latest released A2A `v1.*` tag and with upstream `main`?

**Sources checked:** a2aproject/A2A (local clone `/Users/eugene/Documents/JetBrains/projects/a2a-protocol`),
`main` @ `65dadbd9b900fe2d970e05416eb432a91347cd2a` (2026-10-02, "docs: updating llms.txt and (#2278)"),
tags fetched; latest `v1.*` release tag `v1.0.1` -> commit `3303592588e388e62e0f69f701af531d2f4e3991`
(2026-05-28, "chore(main): release 1.0.1 (#1749)"). Also compared `v1.0.0` (`173695755607e884aa9acf8ce4feed90e32727a1`).
Koog vendored file: `a2a/a2a.proto` at Koog commit `44fa019c1` (branch `eugenethedev/a2a-1.0`).
**Confidence:** high. The comparison is by git blob hash, so it is exact. There is nothing to interpret.

Note: the first `git pull --ff-only` printed "no such ref was fetched" (looks transient). `ls-remote` showed that
`origin/main` = `65dadbd`, the same as local HEAD. A retry of `pull --ff-only` returned "Already up to date", and
`fetch --tags` succeeded.

## Answer

The vendored `a2a/a2a.proto` is **byte-identical to `specification/a2a.proto` at the latest release tag `v1.0.1`**.
Both have git blob `400cdbad934654e27d7abbae1e145923eb40ac52`, and `cmp` and `shasum` match
(`2e005333…`). That blob was introduced upstream by `cd87b93` (2026-05-26, "docs: add multi-tenancy guide and
clarify tenant field semantics (#1848)"), which is the last proto change before `v1.0.1`. So the vendored file
corresponds exactly to `cd87b93` / `v1.0.1`.

Compared with upstream `main` (unreleased), the vendored file is behind by **one commit that touches only a
comment**: `cfc9d34` (2026-07-21, "fix(proto): correct gRPC URL example in AgentInterface (#1997)"). It rewrites
the doc comment on `AgentInterface.url`. No message, field, field number, enum value, oneof, `optional`,
`REQUIRED` annotation, or HTTP binding changed. **Against the released tag the vendored file is not outdated.
Against `main` it is cosmetically outdated (one comment), and that change does not matter for JSON-RPC.**

## Requirements

| # | Requirement | Tier | Bindings | Citation |
|---|-------------|------|----------|----------|
| 1 | `AgentInterface.url` is REQUIRED (field_behavior). This is unchanged in v1.0.1 and main | MUST (proto REQUIRED) | all | A2A `v1.0.1:specification/a2a.proto:339`; `main:specification/a2a.proto:340` |
| 2 | (v1.0.1 comment) URL "Must be a valid absolute HTTPS URL in production" | stated in a proto comment (not RFC 2119-capitalised) | all, as worded | A2A `v1.0.1:specification/a2a.proto:337-338` |
| 3 | (main, unreleased) HTTPS-URL rule limited to HTTP-based transports. For gRPC the address "should be" `hostname:port` | comment-level guidance, not capitalised. Unreleased | JSON-RPC/HTTP+JSON: HTTPS URL. gRPC: `host:port` | A2A `main:specification/a2a.proto:337-340` |
| 4 | When `AgentInterface.tenant` is set, clients MUST put that value in the `tenant` field of every request message to that interface (added in v1.0.0..v1.0.1, already in the vendored file) | MUST (proto comment) | all | A2A `v1.0.1:specification/a2a.proto:344-350`; spec `docs/specification.md` (v1.0.1) line 2000 |

Koog implements only JSON-RPC over HTTP. Rows 1, 2, and 4 apply to it. Row 3 changes nothing for JSON-RPC, because
an HTTP URL is still required in practice.

## Details

### (a) Latest tag `v1.0.1` vs vendored `a2a/a2a.proto`

**No differences.** `git rev-parse v1.0.1:specification/a2a.proto` = `400cdba…` = `git hash-object a2a/a2a.proto`.
Both files are 811 lines and have the same `package lf.a2a.v1;` (line 3). The lists below are therefore all empty:

- messages added/removed/renamed: none
- fields / field numbers added/removed/renumbered: none
- enum values: none
- oneof members: none
- `optional` markers: none
- `(google.api.field_behavior) = REQUIRED`: none
- `google.api.http` paths / additional_bindings: none
- doc comments: none

Provenance. Upstream history of `specification/a2a.proto` (`git log -- specification/a2a.proto`):

| Commit | Date | Blob | In tag | Relation to vendored |
|--------|------|------|--------|----------------------|
| `601c408` (rename `blocking` -> `return_immediately`) | 2026-03-10 | `184a555` | v1.0.0 | older |
| `cd87b93` (tenant doc clarifications) | 2026-05-26 | `400cdba` | v1.0.1 | **identical** |
| `cfc9d34` (AgentInterface.url comment) | 2026-07-21 | `2814f0f` | none (main only) | newer |

For context, these are the changes between `v1.0.0` and `v1.0.1` that the vendored file already contains
(`git diff v1.0.0 v1.0.1 -- specification/a2a.proto`, all from `cd87b93`). They are **comment-only**. The
`tenant` fields keep the same numbers and types. The comments that changed are:
- `AgentInterface.tenant` (field 3): "Tenant ID to be used in the request…" becomes the opaque-routing text plus
  "When set, clients MUST include this value in the `tenant` field of all request messages sent to this interface…
  the protocol does not define its format or semantics." (vendored lines 344-350)
- `tenant` on `TaskPushNotificationConfig`(1), `SendMessageRequest`(1), `GetTaskRequest`(1), `ListTasksRequest`(1),
  `CancelTaskRequest`(1), `GetTaskPushNotificationConfigRequest`(1), `DeleteTaskPushNotificationConfigRequest`(1),
  `SubscribeToTaskRequest`(1), `ListTaskPushNotificationConfigsRequest`(**4**), `GetExtendedAgentCardRequest`(1):
  "Optional. Tenant ID, provided as a path parameter." becomes "Optional. Opaque routing identifier. Must match the
  `tenant` value from the selected `AgentInterface` in the Agent Card when that field is set." (vendored lines 472,
  651, 664, 678, 718, 729, 740, 751, 760, 775). The old text tied `tenant` to a REST path parameter. The new text
  makes it binding-neutral, so it applies to the JSON-RPC `params` body as well.

### (b) `main` vs `v1.0.1` (UNRELEASED)

`git diff v1.0.1 main -- specification/a2a.proto` gives one hunk, from commit `cfc9d34`:

```diff
 message AgentInterface {
-  // The URL where this interface is available. Must be a valid absolute HTTPS URL in production.
-  // Example: "https://api.example.com/a2a/v1", "https://grpc.example.com/a2a"
+  // The URL or address where this interface is available. For HTTP-based transports, must be a valid absolute
+  // HTTPS URL in production. For gRPC, the address should be in the format "hostname:port".
+  // Example: "https://api.example.com/a2a/v1", "grpc.example.com:443"
   string url = 1 [(google.api.field_behavior) = REQUIRED];
```

- messages / fields / numbers / enums / oneofs / `optional` / REQUIRED / HTTP bindings: **no change**
- doc-comment semantics: **yes, narrowly**. The "absolute HTTPS URL" constraint now applies only to HTTP-based
  transports (JSON-RPC, HTTP+JSON). gRPC interfaces use `hostname:port` with no scheme or path. This matters for
  validators that check `supportedInterfaces[].url`. For example, a strict client-side check that rejects every
  non-`https://` URL would wrongly reject gRPC entries on cards that follow main. For a JSON-RPC-only
  implementation, the rule for its own interface does not change.

### Proto-adjacent artifacts

- `specification/json/`: only `README.md` is committed. `a2a.json` is described as a **non-normative** build
  artifact that is not committed (A2A `specification/json/README.md`, "A2A JSON Artifact"). No JSON schema file
  can drift.
- `scripts/proto_to_json_schema.sh` changed on main (unreleased) in `2c3affc` (2026-08-17, "build: Json schema
  generation. Fixes #2073 (#2074)"). It now uses `protoc-gen-jsonschema` with `target=json-bundle` (previously
  `target=json`), merges each file's `$defs` (previously it keyed by `title`), and emits a top-level `"$defs"`
  (previously `"definitions"`). It also adds `scripts/clean_schema_names.py`, which removes the `lf.a2a.v1.` and
  `google.protobuf.` prefixes and the `.jsonschema.json` suffix from definition names and rewrites `$ref`s. This
  changes the **published, non-normative** schema at `docs/spec/a2a.json` (key layout and definition names), not
  the protocol. It matters only if Koog tooling or tests consume the published `a2a.json`.
- `docs/specification.md` v1.0.1..main (unreleased, editorial). The normative text in v1.0.1 still referred to a
  `PushNotificationConfig` object (§4.3.1, line 832) and to `CreateTaskPushNotificationConfigRequest` (§11/gRPC
  section, line 2623). Neither exists in the v1.0.1 proto: `d1ed0da` merged them into `TaskPushNotificationConfig`,
  and `CreateTaskPushNotificationConfig` takes `TaskPushNotificationConfig` directly (vendored line 90). Main
  (`f63dbb4`) renames these references to `TaskPushNotificationConfig`. Main also adds `contextId` to the
  streaming example (`84ba07f`), fixes the Agent Card sample to `securityRequirements` (`dfe216a`), changes the
  migration notes from `protocolVersions` to `supportedInterfaces[].protocolVersion`, and adds a new normative
  §7.6.4 "In-Task Authorization Scope" (`6550d34`: MUST NOT treat `TASK_STATE_AUTH_REQUIRED` as authorization by
  itself). None of these change the proto. The §7.6.4 MUSTs are unreleased.

## Koog status

`a2a/a2a.proto` (Koog) = upstream `v1.0.1` exactly. The Kotlin models were not examined, per scope.

## Testability notes

Drift can be detected mechanically. Compare `git hash-object a2a/a2a.proto` with
`git -C <spec> rev-parse <tag>:specification/a2a.proto` (expected `400cdbad934654e27d7abbae1e145923eb40ac52` for
v1.0.1). No TCK coverage was examined for this question.

## Discrepancies

- Spec text vs proto at v1.0.1. `docs/specification.md` §4.3.1 (line 832) and the push-config sections (lines 326,
  351, 2623-2635, 3130) name `PushNotificationConfig` / `CreateTaskPushNotificationConfigRequest`. The v1.0.1
  proto (and the vendored file) has only `TaskPushNotificationConfig` (line 469), which
  `CreateTaskPushNotificationConfig` accepts as its request (line 90). Main fixes the text (`f63dbb4`, unreleased).
  The proto is normative.
- Within the v1.0.1 proto, the `AgentInterface.url` comment requires "absolute HTTPS URL" for every binding,
  including gRPC. Main corrects this (`cfc9d34`, unreleased).

## Open questions

- The upstream remote has a `dev-1.1` branch (`db39eb5`, seen via `ls-remote`) that was not fetched or examined.
  If Koog wants early warning of 1.1 proto changes, someone should diff that branch separately.
- Whether any Koog tooling or tests consume the published `docs/spec/a2a.json`. If so, the `definitions` -> `$defs`
  and name-cleanup change in `2c3affc` matters. Not examined.
