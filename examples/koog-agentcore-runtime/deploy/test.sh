#!/usr/bin/env bash
# Client-side smoke tests for the deployed agent.
#
# Two groups:
#   PRIMARY  — the documented vision-streaming chat agent (3 scenarios)
#   E2E      — coverage of every other plugin input/output shape against a real AWS round-trip
#
# The primary scenarios are what a community user would run. The e2e ones verify that the
# plugin's shape matrix (binary in, multipart, byte-stream out, etc.) survives the
# AgentCore Runtime proxy, complementing the in-process `testApplication` tests.
set -euo pipefail
cd "$(dirname "$0")/.."

REGION=${AWS_REGION:-us-east-1}
ARN=$(aws bedrock-agentcore-control list-agent-runtimes --region "$REGION" --no-paginate \
  --query "agentRuntimes[?agentRuntimeName=='koog_agentcore_example'].agentRuntimeArn | [0]" --output text)
echo "Runtime: $ARN"

OUT=deploy/test-outputs
mkdir -p "$OUT"
SESSION_BASE="koog-runtime-test-$(date +%s)-pad-pad-pad"

banner () { echo; echo "========== $1 =========="; }

invoke () {
  local outfile=$1; shift
  local session=$1; shift
  local content_type=$1; shift
  local accept=$1; shift
  local payload_b64=$1
  aws bedrock-agentcore invoke-agent-runtime --region "$REGION" \
    --agent-runtime-arn "$ARN" --runtime-session-id "$session" \
    --content-type "$content_type" --accept "$accept" \
    --payload "$payload_b64" "$outfile"
}

# Sample PNG for the vision-chat scenario. A legible 320x160 "HELLO KOOG" image ships
# alongside this script; override with SAMPLE_PNG=/path/to.png to use your own.
SAMPLE_PNG=${SAMPLE_PNG:-$(dirname "$0")/sample.png}

# Larger payload for stream-input test (must exceed 64 KB threshold configured on the agent).
LARGE_PAYLOAD=$OUT/large-payload.bin
if [[ ! -f "$LARGE_PAYLOAD" ]] || [[ $(wc -c < "$LARGE_PAYLOAD") -lt 80000 ]]; then
  head -c 100000 /dev/urandom > "$LARGE_PAYLOAD"
fi

#####################################################################
# PRIMARY  —  the documented community example
#####################################################################

banner "PRIMARY 1a. streaming chat (Accept: text/event-stream → SSE, converseStream)"
P='{"prompt":"In two short sentences, why are coroutines better than threads?"}'
invoke "$OUT/01-chat-stream.txt" "$SESSION_BASE-chat-strm" "application/json" "text/event-stream" \
  "$(echo -n "$P" | base64)" >/dev/null
echo "→ $OUT/01-chat-stream.txt: events=$(grep -c '^data:' "$OUT/01-chat-stream.txt" || true) (SSE)"
head -8 "$OUT/01-chat-stream.txt"

banner "PRIMARY 1b. non-streaming chat (Accept: text/plain → one-shot body, converse)"
invoke "$OUT/01-chat-oneshot.txt" "$SESSION_BASE-chat-once" "application/json" "text/plain" \
  "$(echo -n "$P" | base64)" >/dev/null
echo "→ $OUT/01-chat-oneshot.txt: events=$(grep -c '^data:' "$OUT/01-chat-oneshot.txt" || true) (expected 0)"
cat "$OUT/01-chat-oneshot.txt"; echo

if [[ -f "$SAMPLE_PNG" ]]; then
  banner "PRIMARY 2. vision chat (text + image → SSE)"
  B64=$(base64 < "$SAMPLE_PNG" | tr -d '\n')
  P=$(cat <<JSON
{"prompt":"Describe this image in one sentence.","imageBase64":"$B64","imageFormat":"png"}
JSON
  )
  invoke "$OUT/02-vision.txt" "$SESSION_BASE-vision" "application/json" "text/event-stream" \
    "$(echo -n "$P" | base64)" >/dev/null
  echo "→ $OUT/02-vision.txt: events=$(grep -c '^data:' "$OUT/02-vision.txt" || true)"
  head -8 "$OUT/02-vision.txt"
fi

banner "PRIMARY 3. bad JSON  (malformed body → structured 500)"
P='{this is not valid json'
RC=0
invoke "$OUT/03-error.txt" "$SESSION_BASE-error-pad" "application/json" "application/json" \
  "$(echo -n "$P" | base64)" 2>&1 | tail -2 || RC=$?
echo "(✓ exit=$RC, AgentCore returns RuntimeClientError for handler 5xx)"

#####################################################################
# E2E  —  every other plugin input/output shape
#####################################################################

banner "E2E 4. action=string-out (Text envelope → InvocationResult.Text plain)"
P='{"action":"string-out"}'
invoke "$OUT/04-string-out.txt" "$SESSION_BASE-string-out" "application/json" "text/plain" \
  "$(echo -n "$P" | base64)" >/dev/null
echo "→ $OUT/04-string-out.txt: $(cat "$OUT/04-string-out.txt")"

banner "E2E 5. action=image-out (Text envelope → InvocationResult.Binary one-shot)"
B64=$(base64 < "$SAMPLE_PNG" | tr -d '\n')
P=$(cat <<JSON
{"action":"image-out","imageBase64":"$B64","imageFormat":"png"}
JSON
)
invoke "$OUT/05-image-out.bin" "$SESSION_BASE-img-out-pad" "application/json" "application/octet-stream" \
  "$(echo -n "$P" | base64)" >/dev/null
SIZE=$(wc -c < "$OUT/05-image-out.bin" | tr -d ' ')
MAGIC=$(head -c 8 "$OUT/05-image-out.bin" | xxd -p)
echo "→ $OUT/05-image-out.bin: ${SIZE} bytes; magic=${MAGIC} (PNG: 89504e470d0a1a0a)"

banner "E2E 6. action=bytes-stream (Text envelope → InvocationResult.BinaryStream chunked)"
P='{"action":"bytes-stream","sizeBytes":1024,"chunkBytes":128}'
invoke "$OUT/06-bytes-stream.bin" "$SESSION_BASE-bs-pad-pad" "application/json" "application/octet-stream" \
  "$(echo -n "$P" | base64)" >/dev/null
SIZE=$(wc -c < "$OUT/06-bytes-stream.bin" | tr -d ' ')
echo "→ $OUT/06-bytes-stream.bin: ${SIZE} bytes  (expected: 1024)"
echo "  first 26 bytes: $(head -c 26 "$OUT/06-bytes-stream.bin")"

banner "E2E 7. binary-in (raw image/png → InvocationInput.Binary → JSON ack)"
B64_RAW=$(base64 < "$SAMPLE_PNG" | tr -d '\n')
invoke "$OUT/07-binary-in.json" "$SESSION_BASE-bin-in-pad" "image/png" "application/json" \
  "$B64_RAW" >/dev/null
echo "→ $OUT/07-binary-in.json: $(cat "$OUT/07-binary-in.json")"

banner "E2E 8. stream-in (large body, > 64 KB threshold → InvocationInput.Stream → JSON ack)"
LARGE_B64=$(base64 < "$LARGE_PAYLOAD" | tr -d '\n')
invoke "$OUT/08-stream-in.json" "$SESSION_BASE-stm-in-pad" "application/octet-stream" "application/json" \
  "$LARGE_B64" >/dev/null
echo "→ $OUT/08-stream-in.json: $(cat "$OUT/08-stream-in.json")"

banner "E2E 9. multipart-in (multipart/form-data → InvocationInput.Multipart → JSON ack)"
BOUNDARY="----test-boundary-1"
MULTIPART_BODY=$'------test-boundary-1\r\nContent-Disposition: form-data; name="prompt"\r\n\r\nhello\r\n------test-boundary-1\r\nContent-Disposition: form-data; name="image"; filename="x.png"\r\nContent-Type: image/png\r\n\r\n'"$(cat "$SAMPLE_PNG")"$'\r\n------test-boundary-1--\r\n'
MULTIPART_B64=$(printf '%s' "$MULTIPART_BODY" | base64 | tr -d '\n')
invoke "$OUT/09-multipart-in.json" "$SESSION_BASE-mpart-pad-pad" "multipart/form-data; boundary=$BOUNDARY" "application/json" \
  "$MULTIPART_B64" >/dev/null
echo "→ $OUT/09-multipart-in.json: $(cat "$OUT/09-multipart-in.json")"

banner "All tests done"
