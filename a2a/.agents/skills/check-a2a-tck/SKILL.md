---
name: check-a2a-tck
description: Check the current official A2A TCK (Technology Compatibility Kit), the conformance test suite this Kotlin SDK is validated against. Use to find which requirements the TCK checks and how (requirement IDs, RFC 2119 levels, per-transport tests, validators, SUT scenarios), to understand or diagnose a TCK failure against the Koog SUT, or to see what a SUT must do to pass. Do not use it as the source of truth for protocol behavior.
---

# Check the A2A TCK

Use the official [A2A TCK](https://github.com/a2aproject/a2a-tck) to understand how A2A conformance is tested. This Kotlin SDK is validated against it through the Koog system under test (SUT) in `a2a/test-tck/`.

The TCK is test evidence, not the normative protocol specification. Use the `check-specification` skill for what the protocol requires and `check-python-sdk` for reference implementation behavior. If a TCK test and the specification disagree, report the discrepancy instead of treating the test as the protocol contract, and do not bend the SDK to pass a test that contradicts the spec without flagging it to the user.

## Locate the repository

The `.repo` file next to this `SKILL.md` is gitignored and contains one absolute path to a local clone of the A2A TCK repository. Read and trim that path before doing any research.

If `.repo` does not exist, explicitly ask the user for permission to clone the repository and for their preferred clone location. Do not clone it until permission is granted. If the user grants permission without choosing a location, clone `https://github.com/a2aproject/a2a-tck` as `a2a-tck` beside the current Koog checkout. After cloning, write the clone's absolute path to `.repo`.

If `.repo` exists but its value does not identify a usable Git checkout, report the problem and ask the user whether to correct the path or create a clone. Do not silently replace an existing checkout.

This research checkout is separate from `a2a/test-tck/a2a-tck/`, which `a2a/test-tck/setup_tck.sh` clones at a pinned commit for local and CI runs. Do not modify that pinned clone as part of research. When the question is about a specific TCK run, check which revision that run used (the commit in `setup_tck.sh` or the clone's `HEAD`) and say whether it matches the research checkout.

## Refresh before research

Before exploring or searching the local TCK checkout, update it:

```bash
git -C "<absolute path read from .repo>" pull --ff-only
```

Run this on every use of the skill, even if the checkout was used recently. If the pull fails, report the failure and do not describe the checkout as current. Do not discard local changes, reset branches, or otherwise repair the checkout without the user's authorization.

After the pull succeeds, read the checkout's `AGENTS.md` and other applicable agent-guidance files before researching it, including more specific guidance in subdirectories you inspect. The TCK ships its own agent skills in `.agents/skills/` (for example `run-tck`, `diagnose-failure`, `learn-requirement`); read the relevant one when the question matches it, adapting any SDK-specific steps (they target the Python and Java SUTs) to the Koog SUT.

## Check what the TCK tests

Trace a requirement end to end rather than relying on a test name alone:

- `tck/requirements/` defines requirement IDs, RFC 2119 levels, spec references, and centrally maintained bindings (methods, error codes, task states) in `base.py`.
- `tests/compatibility/` holds the conformance tests: cross-transport tests in `core_operations/` and transport-specific tests in `jsonrpc/`, `http_json/`, and `grpc/`. `conftest.py` and `markers.py` show fixtures, agent card discovery, and level/transport markers.
- `tck/validators/` and `tck/transport/` show how responses are validated and how each transport client builds requests.
- `scenarios/*.feature` describes the behaviors a SUT agent must exhibit (triggered by message content) so the tests can drive it; `sut/` and `codegen/` show how the Python and Java SUTs implement them.
- `specification/` holds the TCK's vendored spec copy; `specification/version.json` records which upstream spec revision it tracks.

Relate findings to the Koog side: the SUT in `a2a/test-tck/a2a-test-server-tck/` must implement the same scenario behaviors, and Koog currently exposes only the JSON-RPC binding, so `http_json` and `grpc` tests are expected to be skipped or not applicable when the agent card declares only JSON-RPC. Distinguish real SDK conformance gaps from SUT scenario gaps, TCK revision mismatches, and transports Koog does not implement.

## Running the TCK

Only run the TCK when the user asks for it. Use the scripts in `a2a/test-tck/` (`setup_tck.sh`, `run_sut.sh`, `run_tck.sh`) and check the current `run_tck.py` CLI in the TCK checkout first: its flags change between TCK versions, and `a2a/test-tck/README.md` may describe an older one.

Report the TCK revision checked, cite concrete repository-relative files and lines (requirement IDs, test functions, validators), and state the spec section each relevant requirement references. Distinguish what the TCK demonstrably checks from your interpretation of why a test fails.
