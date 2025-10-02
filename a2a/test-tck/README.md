# A2A Testing Kit Integration

This directory contains tooling to validate the A2A Kotlin SDK against the
official [A2A protocol specification](https://a2a-protocol.org/latest/specification/) using the A2A Testing Kit (TCK).

## Contents

- **`a2a-test-server-tck/`**: Sample A2A server implementation built with Koog SDK for TCK validation
- **`a2a-tck/`**: Official A2A Testing Kit repository (gitignored, should be cloned by `setup_tck.sh`)
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
    ./run_tck.sh --sut-url http://localhost:9999/a2a --category all --report   
    ```

## More information

For more information, see the [A2A Testing Kit repo](https://a2a-protocol.org/latest/tck/).
