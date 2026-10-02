# A2A Testing Kit Integration

This directory contains tooling to validate the A2A Kotlin SDK against the
official [A2A protocol specification](https://a2a-protocol.org/latest/specification/) using the A2A Testing Kit (TCK).

## Contents

- **`a2a-test-server-tck/`**: Sample A2A server implementation built with Koog SDK for TCK validation
- **`a2a-tck/`**: Official A2A Testing Kit repository (gitignored, cloned at a pinned commit by `setup_tck.sh`)
- **`setup_tck.sh`**: Clone and setup the A2A Testing Kit
- **`run_sut.sh`**: Run the Kotlin test server (System Under Test)
- **`run_tck.sh`**: Execute TCK tests against the running server

## Quick start

1. **Setup the Testing Kit:**
   ```bash
   ./setup_tck.sh
   ```

2. **Run the test server:**
   ```bash
   ./run_sut.sh
   ```

3. **In another terminal, run the TCK tests:**
   ```bash
   ./run_tck.sh --sut-host http://localhost:9999
   ```

The TCK fetches the agent card from `{sut-host}/.well-known/agent-card.json` and tests every transport declared in its
`supportedInterfaces`. The Koog test server declares only JSON-RPC, served at `http://localhost:9999/a2a`.

Useful options (all arguments are passed through to the TCK's `run_tck.py`):

```bash
# Run only MUST-level requirements (SHOULD failures are reported as xfail, MAY tests are skipped if not declared)
./run_tck.sh --sut-host http://localhost:9999 --level must

# Run only the JSON-RPC transport with verbose output
./run_tck.sh --sut-host http://localhost:9999 --transport jsonrpc -v

# Pass extra pytest arguments after -- (here: stop on first failure)
./run_tck.sh --sut-host http://localhost:9999 -- -x
```

Reports (compatibility JSON/HTML, pytest HTML, JUnit XML) are written to `a2a-tck/reports/`.

## Updating the TCK version

The TCK revision is pinned by `COMMIT_HASH` in `setup_tck.sh`. After changing it, re-run `./setup_tck.sh`, which
switches an existing `a2a-tck/` clone to the new commit.

## More information

For more information, see the [A2A Testing Kit repo](https://github.com/a2aproject/a2a-tck).
