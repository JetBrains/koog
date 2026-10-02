---
name: check-python-sdk
description: Check the current official A2A Python SDK (a2a-sdk) reference implementation, including runtime behavior, task lifecycle, streaming and resubscription, push notifications, error handling, transports, tests, and API patterns. Use when this Kotlin SDK must be compared with the latest Python reference behavior, when debugging interop with the Python test server used by the client integration tests, or to understand how to use the Python SDK. Do not use it as the source of truth for protocol schemas or specification text.
---

# Check the A2A Python SDK

Use the official [A2A Python SDK](https://github.com/a2aproject/a2a-python) as the reference implementation for A2A runtime behavior.

The Python SDK is implementation evidence, not the normative protocol specification. Use the `check-specification` skill for the specification text and `a2a.proto`. If the implementation and specification disagree, report the discrepancy instead of silently treating the Python behavior as the protocol contract.

## Locate the repository

The `.repo` file next to this `SKILL.md` is gitignored and contains one absolute path to a local clone of the Python SDK repository. Read and trim that path before doing any reference-implementation research.

If `.repo` does not exist, explicitly ask the user for permission to clone the repository and for their preferred clone location. Do not clone it until permission is granted. If the user grants permission without choosing a location, clone `https://github.com/a2aproject/a2a-python` as `a2a-python` beside the current Koog checkout. After cloning, write the clone's absolute path to `.repo`.

If `.repo` exists but its value does not identify a usable Git checkout, report the problem and ask the user whether to correct the path or create a clone. Do not silently replace an existing checkout.

## Refresh before research

Before exploring or searching the local Python SDK checkout, update it:

```bash
git -C "<absolute path read from .repo>" pull --ff-only
git -C "<absolute path read from .repo>" fetch --tags
```

Run this on every use of the skill, even if the checkout was used recently. If the pull fails, report the failure and do not describe the checkout as current. Do not discard local changes, reset branches, or otherwise repair the checkout without the user's authorization.

After the pull succeeds, read the checkout's `AGENTS.md` and the guidance it points to (such as `docs/ai/`) that is relevant to reading code, including more specific guidance in subdirectories you inspect. Its mandatory workflow for editing that repository (checks, mistake journaling) does not apply to read-only research from this project.

## Match the version in use

Koog's client integration tests run against a Python A2A server in `a2a/test-python-a2a-server/` (started as a container from `a2a/a2a-client/src/jvmTest/kotlin/ai/koog/a2a/client/TestA2AServerContainer.kt`), which pins an `a2a-sdk` version in its `pyproject.toml`/`uv.lock`. When the question concerns that server or a failing interop test, read the code at the matching release tag (for example `git -C <repo> show v<version>:<path>`) and note any relevant difference from `main`.


## Check the reference implementation

Trace the complete Python behavior relevant to the question rather than relying on similarly named types or isolated functions:

- Server side: start at the routes and request handlers (`src/a2a/server/routes/`, `src/a2a/server/request_handlers/`) and follow control flow through agent execution, event queues, and task management (`agent_execution/`, `events/`, `tasks/`).
- Client side: start at the client and factory (`src/a2a/client/`) and follow into the transport implementations (`client/transports/`).
- Read focused tests in `tests/` alongside the implementation to confirm observable behavior, failure handling, cancellation, cleanup, and event ordering.
- Check `samples/`, `tck/`, and `docs/migrations/` when the question concerns supported API usage, lifecycle wiring, or v0.3 to v1.0 changes; `src/a2a/compat/` holds v0.3 compatibility code, so do not mistake it for 1.0 behavior.
- Check model and serialization code (generated protobuf types in `src/a2a/types/`, ProtoJSON conversion helpers) when it directly affects how the reference implementation consumes or produces protocol data.

Compare externally observable semantics on the wire (JSON-RPC payloads, error codes, SSE event sequences, task state transitions) rather than translating Python structure mechanically into Kotlin. Account for asyncio task and queue behavior, cancellation, error propagation, and shutdown explicitly when they affect parity with Kotlin coroutines and flows. Koog currently implements only the JSON-RPC over HTTP binding, so focus on the JSON-RPC paths unless the user asks about other transports.

Report the upstream revision checked (commit and nearest tag) and cite concrete repository-relative files and lines. Distinguish behavior demonstrated by code or tests from interpretation, and note any relevant version constraints, optional extras, or untested paths.
