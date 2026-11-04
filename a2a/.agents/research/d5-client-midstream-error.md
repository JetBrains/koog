# How do A2A clients react to a final `-32603` SSE error event after some task events (non-terminal task)?

**Sources checked:** Koog `eugenethedev/a2a-1.0` @ 9c7a104fb (Ktor 3.3.3 client sources from the Gradle cache); a2a-python `main` @ 56ec855 (v1.3.0-7); A2A spec `v1.0.1` @ 3303592 (main @ d924e07 and `v1.1.0-rc` glanced at only); TCK not checked (not asked).
**Confidence:** high for client behavior, which code and existing tests show. Medium for the recommendation, which is design judgment where the spec is silent.

## Answer
- Both clients deliver every earlier event in order, then fail with the typed internal-error exception. The code and message are preserved. Neither client swallows the error, and neither hangs: both throw as soon as they parse the error frame, without waiting for the server to close.
- Koog throws `A2AInternalErrorException` with the server's `message` and `details = []`. Python raises `a2a.utils.errors.InternalError(message, data=None)`.
- The spec says nothing about an error frame in the middle of a stream. JSON-RPC SSE is only shown with `result` frames. The stream-close MUSTs only cover terminal states.
- `-32603` plus the message is conformant (spec §3.3.2 lists `-32603` as the JSON-RPC example for system errors).
- Callers can't tell this error from other internal errors without parsing the message. The cheapest fix is a machine-readable `google.rpc.ErrorInfo` detail. A new code isn't needed.

## Requirements
| # | Requirement | Tier | Bindings | Citation |
|---|---|---|---|---|
| 1 | A task stream MUST close when the task reaches a terminal state (SendStreamingMessage, SubscribeToTask) | MUST | all | spec v1.0.1 `docs/specification.md:210` (§3.1.2), `:309` (§3.1.6) |
| 2 | Nothing forbids ending a stream early on a non-terminal task. Clients "MAY not receive all status update messages" across a disconnect and reconnect | MAY (informative) | all | `:762` (§3.7); reconnecting by opening a new stream `:699` (§3.5.2) |
| 3 | Closing one stream MUST NOT affect other streams of the same task | MUST | all | `:693` (§3.5.2) |
| 4 | Servers SHOULD use different error codes for temporary and permanent failures, and MAY include retry guidance. `-32603` is the JSON-RPC example | SHOULD/MAY | all | `:531-536` (§3.3.2 System Errors) |
| 5 | Error details go in `error.data` as an array of `@type` objects. Well-known types such as `google.rpc.ErrorInfo` SHOULD be used | SHOULD | JSON-RPC | `:2431-2457` (§9.5), `:540-545` (§3.3.2) |
| 6 | JSON-RPC SSE frames are only shown with `result`. Mid-stream error frames and the `event:` field are not specified | not specified | JSON-RPC | `:2316-2330` (§9.4.2) |

## Details
**Koog client (`a2a/`)**
- `HttpJSONRPCClientTransport.kt:97-104` collects `session.incoming` outside Ktor's `sse {}` block. Each `data:` frame is decoded into `JSONRPCResponse`, and the SSE `event:` name is ignored.
- `JSONRPCClientTransport.kt:159-168` maps each frame with `.map { toResponse(...) }`. An error frame throws `A2AException.create(message, code, toErrorDetails(data))` (`:103-105`).
  - `-32603` becomes `A2AInternalErrorException` (`a2a-core/.../Exceptions.kt:89`), not wrapped.
  - The server sends `data: []` for `-32603`, because `forCode` has no reason for it (`Exceptions.kt:46-57`, `JSONRPCServerTransport.kt:368-376`). So `details` is empty.
- The `finally { session.cancel() }` at `:102-104` releases the connection right after the throw, so the client does not depend on the server closing.
- Ktor passes the downstream exception through unchanged: the `.catch` in `DefaultClientSSESession._incoming` only catches upstream exceptions. `maxReconnectionAttempts` defaults to 0 (`SSEConfig.kt:33`), so there is no auto-reconnect and no hang. Heartbeat comments are dropped (`showCommentEvents = false`).
- `A2AClient.sendMessageStreaming` and `subscribeToTask` return the transport flow unchanged (`a2a-client/.../A2AClient.kt:95-101, 146-152`).
- **Silent-end case:** Ktor's `tryParseEvent` treats `ClosedByteChannelException` as a normal end, unless the root cause is `SocketTimeoutException` (`DefaultClientSSESession.kt:152-163`). If the server drops the connection without writing the error frame, the Flow completes normally.
  - The caller then can't tell this from a normal end, except by checking that the last state was terminal or interrupted.
  - The Koog client does not check this. Neither does Python.
- **Existing test:** `HttpJSONRPCClientTransportTest.kt:661-695` (`testStreamingErrorEventThrowsA2AException`) sends one `result` frame and then an error frame (`TASK_NOT_FOUND`). It asserts the event was received, the exception type, the message and the details.
  - The code path doesn't depend on the error code, so `-32603` is covered in effect.
  - There is no end-to-end test of the shutdown error over HTTP. `A2AServerShutdownTest.kt:221-238` calls `A2AServer` in-process.

**Python SDK client (`a2a-python` main)**
- `src/a2a/client/transports/http_helpers.py:152-157` passes `data:` frames through. Frames with `event: error` go to `_handle_sse_error`.
- `jsonrpc.py:361-387`: each frame goes through `_check_jsonrpc_response`, which raises `_create_jsonrpc_error` for any frame with `error` (`:347-354`). Both routes end in `_create_jsonrpc_error` (`:298-317`, `:389-399`).
- `-32603` becomes `InternalError` (`src/a2a/utils/errors.py:69-72, 158`), with the message preserved.
- `data` is set only to the **`metadata`** of the first `ErrorInfo` entry (`jsonrpc.py:304-309`). `reason` and `domain` are dropped. With Koog's `data: []`, it is `None`.
- Earlier events have already been yielded. `base_client.py:113-126, 493-539` re-raises unchanged.
- No hang: the `_SSEEventSource` closes the response on exit (`http_helpers.py`, `__aexit__`).
- If the server drops the connection without an error frame, the stream ends silently (same as Koog). Also, `parse_sse_stream` drops a final event that has no trailing blank line (`http_helpers.py:68-106`). This doesn't affect Koog, which writes `\n\n`.
- Tested in `tests/integration/test_client_server_integration.py:1306-1340` (one event, then an A2A error, for both `send_message` streaming and `subscribe`). `InternalError` is not one of the parametrized errors (`:1270-1281`).
- Python *server* sets `event: error` on mid-stream error frames (`src/a2a/server/routes/jsonrpc_dispatcher.py:585-611`). Koog writes a plain `data:` frame. Both clients accept both forms.

## Koog status
- No client bug for this case. The prefix is delivered, the exception is typed, the message is kept, and there is no hang or swallowing.
- Gap: the `-32603` code can't separate eviction from the shutdown error ("Server is shut down", `TurnManager.kt:874`) or from agent failures. Any non-A2A exception becomes `-32603 "Internal error: ..."` (`JSONRPCServerTransport.kt:330-349`). Only the message text differs.
- Limitation for a fix: Koog's `ErrorInfo` fixes `domain = "a2a-protocol.org"` (`a2a-core/.../ErrorData.kt:35-44`). A Koog-specific reason would be attributed to the A2A domain unless `domain` becomes a constructor parameter. That is a public-API change and needs the user's confirmation.

## Recommendation
- Keep `-32603`. It is conformant (§3.3.2). A custom code would sit in the range A2A reserves for its own errors (`-32001..-32099`, §9.5) and could collide with future A2A codes. Older clients would also see it as an unknown error.
- Add an `ErrorInfo` detail to the eviction error, and ideally to the shutdown error too, so each is tagged with a reason:
  - eviction: e.g. `reason = "SLOW_CONSUMER"`
  - shutdown: e.g. `"SERVER_SHUTTING_DOWN"`
  - domain: a Koog domain
  - put `taskId` and the reason in `metadata` as well, because Python clients only see `metadata`.
- On the Koog client side, a no-API option is to document "on any mid-stream `A2AException` for a non-terminal task: call `GetTask`, then `SubscribeToTask` again". An optional helper could check the `ErrorInfo` reason. Neither needs a new exception type.
- Raise the silent-end case with the user as a separate design decision. One option is a client-side check that the stream ended with a terminal or interrupted state.

## Testability notes
- Cheap test, unit level: copy `HttpJSONRPCClientTransportTest.kt:661-695` with `-32603`, the eviction message and `data: []`. Assert `A2AInternalErrorException`, the message, `details == []`, and the received prefix. About 20 lines.
- Cheap test, end to end: in `a2a-server/src/jvmTest/.../A2AServerJsonRpcTransportIntegrationTest.kt` (already has a real client, `:128`), start a streaming turn, call `server.cancel()`, then assert the prefix and the "Server is shut down" `A2AInternalErrorException` through `HttpJSONRPCClientTransport`.
- The silent-end path (connection dropped before the error frame) could be tested with a raw `respondTextWriter` that closes without an error frame. Today the expected result would be a normal completion.
- TCK coverage of mid-stream errors was not checked.

## Discrepancies
- Python server marks error frames with `event: error`, while Koog's server writes a plain `data:` frame. The spec defines neither. Both clients accept both forms.
- The Python client exposes only `ErrorInfo.metadata` and drops `reason`, so a reason-only tag would be invisible to Python callers.

## Open questions
- Should the Koog client detect a stream that ends without a terminal or interrupted state (silent drop)? This is a design decision.
- Should `ErrorInfo.domain` become a constructor parameter (public API in `a2a-core`)?
- Does the TCK check mid-stream error frames or the SSE `event:` field? Route to a TCK researcher if it matters.
- `v1.1.0-rc` / `main` adds text on `StreamError` for SendLiveMessage, saying only an unusable channel ends the stream (`main docs/specification.md:481`). This is unreleased and not applicable to Koog today.
