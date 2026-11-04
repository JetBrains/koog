# Can a Ktor 3.3.3 server detect an SSE client disconnect cleanly and engine-agnostically?

**Sources checked:** Ktor 3.3.3 sources (`ktor-server-sse`, `-core-jvm`, `-netty-jvm`, `-cio-jvm` from the Gradle cache); YouTrack KTOR-8685; a2a-python @ 79a6f45 (nearest tag v1.2.3) with the venv's sse-starlette 3.4.4; a scratch experiment in `/tmp/sse-disconnect` (macOS, loopback, JDK 17).
**Confidence:** high for "no passive API" and "heartbeat works on both engines". Medium on the exact number of writes it takes, because I measured on loopback only.

## Answer
Yes, with heartbeats. Ktor's public `ServerSSESession.heartbeat {}` works on any engine, and on both Netty and CIO it reliably cancels the SSE handler after a normal FIN close or an RST close. Detection is not instant. It is bounded by a few heartbeat intervals and depends on the engine. Ktor has no passive "connection closed" API for the server. A Ktor maintainer confirmed this in KTOR-8685 ("only possible to detect the connection closure by trying sending a packet ... sending ping/pong messages is a viable solution"; state Answered). This is the same approach the Python SDK uses through sse-starlette's ping.

## Details
- **Ktor API.** `ServerSSESession.heartbeat(config)` (`ktor-server-sse/commonMain/io/ktor/server/sse/ServerSSESession.kt:165-174`) starts a coroutine under `Job(call.coroutineContext[Job])`. That coroutine sends `Heartbeat.event` (default: comment `heartbeat`, `period` 30s, `:188-190`) in a loop. When a send fails, the failure cancels the call Job, so the handler that is collecting the flow gets cancelled. `SSEServerContent.writeTo` cancels the heartbeat job in `finally` (`SSEServerContent.kt:57-62`). Sends are serialized by a mutex and flushed each time (`DefaultServerSSESession.kt:18-35`), so a heartbeat cannot interleave with a data event.
- **Koog fit.** Koog already responds with `SSEServerContent(call) { responseFlow.collect { send(...) } }` (`a2a-transport/a2a-transport-server-jsonrpc-http/.../Routes.kt:123-129`). Calling `heartbeat { period = X }` at the top of that lambda is enough. It is about one line plus a configurable period, and needs no engine-specific code.
- **Experiment.** A raw-socket client reads the first event and then closes. The server sends a comment every P ms. Results:

| Engine | Close | Writes after close that still succeed | Handler cancelled after |
|---|---|---|---|
| Netty | FIN (`close()`) | 1 | 2nd write (P=50: 106ms, P=1000: 2020ms) |
| Netty | RST (linger 0) | 0 | 1st write (~1 P) |
| CIO | FIN | 4 | 5th write (P=50: 274ms, P=1000: 5027ms) |
| CIO | RST | – | ~1-2ms, without any write (CIO's reader sees the reset) |

  The built-in `heartbeat {}` combined with `awaitCancellation()` gave the same results: the handler ended with `JobCancellationException: Parent job is Cancelling`. The latency is set by the number of writes, not by time. On FIN the peer's kernel answers the first post-FIN segment with RST, and the following write fails. CIO needs about 4 extra writes because its write pipeline buffers.
- **Why the A4 draft saw "writes succeed silently" after FIN.** Hypothesis: that test made only 1-2 writes after the close, which is fewer than CIO needs. On Netty the second write would be the terminal event, which ends the stream anyway.
- **Python SDK.** `create_event_source_response` returns an sse-starlette `EventSourceResponse` (`src/a2a/server/routes/common.py:47-72`, a2a-python). sse-starlette detects a disconnect in two ways: `_listen_for_disconnect` reacts to ASGI `http.disconnect` (`sse_starlette/sse.py:378-390`), and `_ping` sends a ping every 15s by default (`:259`, `:435`). Either one cancels the task group (`:483`).

## Caveats (why this is "clean but not instant")
- **Latency.** Detection takes about 2 periods on Netty and about 5 on CIO after a FIN, and about 1 period after an RST. The period is the trade-off between latency and idle traffic. The Python default is 15s.
- **Dead peers.** If the peer disappears without sending FIN or RST (network partition, NAT drop), writes keep succeeding until the kernel send buffer fills or TCP retransmission gives up, which can take minutes. That is a TCP limit, not a Ktor one.
- **Proxies.** Behind a reverse proxy, the server only sees the proxy closing its upstream connection. Heartbeats still flow through when buffering is off (Koog already sets `X-Accel-Buffering: no`).
- **Comment lines.** Heartbeats are SSE comment lines (`: heartbeat`), which compliant SSE clients ignore. I did not check whether Koog's client, the Python client, or the TCK SSE parser tolerate comment lines. See Open questions.

## Koog status
The server never calls `heartbeat`, so an idle SubscribeToTask stream is never released (A4 premise). The fix point is the `SSEServerContent` lambda in `Routes.kt:124`.

## Testability notes
This can be tested engine-agnostically. Use a short period (50ms) and a raw socket that closes after the first event. Then assert that the server flow completes or is cancelled within about 10 periods. Run it once with Netty and once with CIO. Use FIN, not RST: FIN is the harder case, and the RST case on CIO passes even without heartbeats. A matching TCK requirement was not checked.

## Discrepancies
None found.

## Open questions
- Do Koog's SSE client (`a2a-transport-client-jsonrpc-http`), the Python client, and the TCK ignore SSE comment lines? Ktor's client hides comments by default (`showCommentEvents`), but this needs verification.
- What should the default heartbeat period be (Python uses 15s), and should it be configurable on `HttpJSONRPCServerTransport`? This is an API decision for the user.
