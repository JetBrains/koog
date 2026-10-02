---
name: check-specification
description: Check the current A2A (Agent2Agent) protocol source of truth, including the specification text, the normative a2a.proto, ADRs, and topic docs. Use when A2A protocol behavior, data models, wire format, error mappings, or transport bindings (JSON-RPC, HTTP+JSON/REST, gRPC) must be verified against upstream rather than inferred from this Kotlin SDK. Do not use it as a reference implementation.
---

# Check the A2A specification

Use the official [A2A protocol repository](https://github.com/a2aproject/A2A) as the source of truth for A2A protocol semantics, data models, wire representation, and transport bindings.

This repository contains no runtime implementation. Use the `check-python-sdk` skill when reference implementation behavior is the question, and `check-a2a-tck` when the question is how conformance is tested.

## Locate the repository

The `.repo` file next to this `SKILL.md` is gitignored and contains one absolute path to a local clone of the specification repository. Read and trim that path before doing any upstream research.

If `.repo` does not exist, explicitly ask the user for permission to clone the repository and for their preferred clone location. Do not clone it until permission is granted. If the user grants permission without choosing a location, clone `https://github.com/a2aproject/A2A` as `a2a-protocol` beside the current Koog checkout. After cloning, write the clone's absolute path to `.repo`.

If `.repo` exists but its value does not identify a usable Git checkout, report the problem and ask the user whether to correct the path or create a clone. Do not silently replace an existing checkout.

## Refresh before research

Before exploring or searching the local specification checkout, update it:

```bash
git -C "<absolute path read from .repo>" pull --ff-only
git -C "<absolute path read from .repo>" fetch --tags
```

Run this on every use of the skill, even if the checkout was used recently. If the pull fails, report the failure and do not describe the checkout as current. Do not discard local changes, reset branches, or otherwise repair the checkout without the user's authorization.

After the pull succeeds, read the checkout's applicable `AGENTS.md`, `CONTRIBUTING.md`, or other agent-guidance files before researching it, including more specific guidance in subdirectories you inspect.

## Pick the right revision

The checkout's default branch (`main`) may contain unreleased changes ahead of the latest release tag (`v1.0.0`, `v1.0.1`, ...). This Kotlin SDK targets the A2A 1.0 line (see `a2a/a2a_1.0.0_changelog.md`).

- Use the latest `v1.*` release tag as the baseline for "what the SDK must implement" unless the user asks about `main`. Read tagged content with `git -C <repo> show <tag>:<path>` or `git -C <repo> diff <tag> main -- <path>` rather than checking out a different branch.
- When `main` differs from the release tag in a way relevant to the question, report both and label which is released and which is unreleased.

## Check the source of truth

Choose the authoritative upstream evidence that matches the question:

- `specification/a2a.proto` is the normative definition of data objects, request/response messages, enums, field presence, and gRPC service/HTTP annotations. Use it for wire names, shapes, required fields, optionality, oneofs, and REST paths (`google.api.http`).
- `docs/specification.md` is the normative specification text: operations and their semantics, task lifecycle and states, streaming and push notification delivery, versioning, error code mappings per binding, JSON field naming, and data type conventions. Use its section numbers when citing.
- `adrs/` captures design decisions, such as ProtoJSON serialization rules for the JSON-based bindings.
- `docs/topics/` and `docs/whats-new-v1.md` explain concepts and changes from v0.3. They are explanatory; prefer the specification text and proto when they disagree.
- `specification/json/` documents how the JSON schema is derived; do not treat generated artifacts as more authoritative than the proto.

Trace related evidence across these sources when the question spans semantics and wire representation (for example, a proto field's JSON name under ProtoJSON plus the spec's rules on its presence). Respect RFC 2119 keywords: distinguish MUST/SHOULD/MAY requirements and say which applies.

## Compare with this SDK

This Kotlin SDK keeps its own copy of the proto at `a2a/a2a.proto` and hand-written models in `a2a/a2a-core`, with transport bindings in `a2a/a2a-transport` (currently JSON-RPC over HTTP only; HTTP+JSON/REST and gRPC are not implemented), client in `a2a/a2a-client`, and server in `a2a/a2a-server`. When the question concerns SDK conformance:

- Diff `a2a/a2a.proto` against upstream `specification/a2a.proto` at the relevant tag when proto drift is plausible.
- Compare the Kotlin models' serialized JSON (kotlinx.serialization names, defaults, nullability, polymorphic discriminators) with the ProtoJSON representation the spec requires, not with Kotlin naming conventions.
- Do not treat existing SDK behavior as evidence of what the spec says.

Report the upstream revision checked (commit and nearest tag), cite concrete repository-relative files and lines, and distinguish normative requirements from explanatory docs, ADR rationale, or unreleased `main` changes. If authoritative sources disagree, surface the discrepancy instead of choosing one without explanation.
