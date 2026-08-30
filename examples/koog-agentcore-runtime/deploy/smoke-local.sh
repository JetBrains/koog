#!/usr/bin/env bash
# Local smoke test — start the app on port 8090, hit /ping, send a streaming and a
# non-streaming chat invocation (selected via the Accept header), kill it.
set -euo pipefail
cd "$(dirname "$0")/.."
LOG=$(mktemp)
echo "Starting app on :8090 (logs: $LOG)..."
PORT=8090 java -jar build/libs/app.jar > "$LOG" 2>&1 &
PID=$!
trap 'kill $PID 2>/dev/null; wait $PID 2>/dev/null; echo "--- app log ---"; tail -40 "$LOG"' EXIT

# Wait for /ping
for i in $(seq 1 40); do
  if curl -fs http://localhost:8090/ping >/dev/null; then break; fi
  sleep 0.5
done

echo "=== /ping ==="
curl -s http://localhost:8090/ping; echo

echo "=== /invocations streaming chat (Accept: text/event-stream) ==="
curl -s -X POST http://localhost:8090/invocations \
  -H 'Content-Type: application/json' -H 'Accept: text/event-stream' \
  -d '{"prompt":"Say hello in 5 words"}'; echo

echo "=== /invocations non-streaming chat (Accept: text/plain) ==="
curl -s -X POST http://localhost:8090/invocations \
  -H 'Content-Type: application/json' -H 'Accept: text/plain' \
  -d '{"prompt":"Say hello in 5 words"}'; echo

echo "OK"
