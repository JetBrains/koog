# A2A Testing Kit Integration

This directory contains tooling to validate the A2A Kotlin SDK against the
official [A2A protocol specification](https://a2a-protocol.org/latest/specification/) using the A2A Testing Kit (TCK).

## Contents

- **`a2a-test-server-tck/`**: The system under test (SUT): a Koog A2A server for TCK validation
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

   The SUT listens on port 9999 once Ktor logs `Responding at http://0.0.0.0:9999`.

3. **In another terminal, run the TCK tests:**
   ```bash
   ./run_tck.sh --sut-host http://localhost:9999 --transport jsonrpc
   ```

The TCK fetches the agent card from `{sut-host}/.well-known/agent-card.json` and tests every transport declared in its
`supportedInterfaces`. The Koog test server declares only JSON-RPC, served at the root `http://localhost:9999` (the TCK
posts to the base URL with a trailing slash, so the route is mounted at `/`). It declares streaming and an extended agent
card, and no push notifications. Heartbeats are off, so every stream must end by itself.

The TCK drives the agent through the prefix of the `messageId` (`tck-<scenario>-<session>`); the prefixes are handled in
`TckAgentExecutor`. An unknown prefix completes the task.

| Prefix                                                                                | Behavior                                                         |
|---------------------------------------------------------------------------------------|------------------------------------------------------------------|
| `tck-complete-task`                                                                   | `COMPLETED` with the message "Hello from TCK"                    |
| `tck-input-required`                                                                  | `INPUT_REQUIRED`                                                 |
| `tck-artifact-text`, `-file`, `-file-url`, `-data`                                    | the artifact with a text, file, file URL or data part, `COMPLETED` |
| `tck-message-response`                                                                | replies with a `Message` and no task                             |
| `tck-stream-001`, `-003`, `-ordering-001`, `-artifact-text`, `-artifact-file`         | `WORKING`, artifact, `COMPLETED`                                 |
| `tck-stream-002`                                                                      | `COMPLETED`                                                      |
| `tck-stream-artifact-chunked`                                                         | `WORKING`, two artifact chunks, `COMPLETED`                      |
| `test-resubscribe-message-id`                                                         | `WORKING`, 4 seconds, `COMPLETED`                                |
| `tck-reject-task`                                                                     | `REJECTED`                                                       |
| anything else                                                                         | `COMPLETED`                                                      |

CancelTask moves the task to `CANCELED`.

Useful options (all arguments are passed through to the TCK's `run_tck.py`):

```bash
# Run only MUST-level requirements
./run_tck.sh --sut-host http://localhost:9999 --transport jsonrpc --level must

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
