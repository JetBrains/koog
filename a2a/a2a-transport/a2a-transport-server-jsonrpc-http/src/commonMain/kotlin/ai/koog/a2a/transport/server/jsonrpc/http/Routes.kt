package ai.koog.a2a.transport.server.jsonrpc.http

import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.transport.ServerCallContext
import ai.koog.a2a.transport.jsonrpc.JSONRPCServerTransport
import ai.koog.a2a.transport.jsonrpc.serialization.JSONRPCJson
import ai.koog.a2a.transport.withCaseInsensitiveNames
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.fromHttpToGmtDate
import io.ktor.http.toHttpDate
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.sse.SSE
import io.ktor.server.sse.SSEServerContent
import io.ktor.server.sse.heartbeat
import io.ktor.sse.ServerSentEvent
import io.ktor.util.date.GMTDate
import io.ktor.util.hex
import io.ktor.util.sha1
import io.ktor.util.toMap
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Route for handling JSON-RPC HTTP requests.
 * Allows mounting A2A requests handling into an existing Ktor server application.
 * This can also be used to mount multiple A2A server transports on the same server, to serve multiple A2A agents.
 *
 * You also need to serve an [ai.koog.a2a.model.AgentCard] manually to make your agent discoverable at the desired location.
 * [a2aAgentCardRoute] can be used for that.
 *
 * For a provided standalone Ktor server for a single agent for a quick prototyping, check [HttpJSONRPCServerTransport]
 *
 * Example usage:
 * ```kotlin
 * val firstAgentCard = AgentCard(...)
 * val firstTransport = JSONRPCServerTransport(A2AServer(...)) // first agent
 *
 * val secondAgentCard = AgentCard(...)
 * val secondTransport = JSONRPCServerTransport(A2AServer(...)) // second agent
 *
 * embeddedServer(Netty, port = 8080) {
 *     install(SSE)
 *
 *     // Other configurations...
 *
 *     routing {
 *         // Other routes...
 *
 *         // Transport agent routes
 *         route("/a2a") {
 *             a2aJsonRpcTransportRoute("/agent-1", firstTransport)
 *             a2aJsonRpcTransportRoute("/agent-2", secondTransport)
 *         }
 *
 *         // Agent card routes
 *         route("/agent-cards") {
 *             a2aAgentCardRoute("/agent-1", firstAgentCard)
 *             a2aAgentCardRoute("/agent-2", secondAgentCard)
 *         }
 *     }
 * }.startSuspend(wait = true)
 * ```
 *
 * The route does not install `ContentNegotiation`, so it can be mounted into an application that installs it.
 *
 * [customizeCallContext] can enrich the [ServerCallContext] built from the request headers, e.g. with the
 * authenticated principal:
 * ```kotlin
 * val UserKey = StateKey<String>("user")
 *
 * authenticate("auth-basic") {
 *     a2aJsonRpcTransportRoute("/agent", transport) { ctx ->
 *         ctx.copy(state = ctx.state + (UserKey to principal<UserIdPrincipal>()!!.name))
 *     }
 * }
 * ```
 *
 * @param path JSON-RPC endpoint path that will be mounted under the base route.
 * @param transport The JSON-RPC server transport to handle requests.
 * @param heartbeatPeriod Period of the SSE comment lines (`: heartbeat`) sent on streaming responses, `null` (the
 * default) sends none. A server only notices that a client has gone away when a write to it fails, so with a
 * heartbeat an idle stream (e.g. a subscription to a task waiting for input) is released after the client is gone.
 * This is best-effort and engine-dependent: detection takes a few periods, and with Ktor 3.3.3 on Netty a client
 * that closes with unread data (TCP reset) may not be detected, so its stream then ends with the task or on shutdown.
 * The heartbeat also keeps idle streams alive for clients that rely on a read or inactivity timeout, so without it
 * a stream that stays silent may time out on their side.
 * @param customizeCallContext Called for every parsed JSON-RPC request with the current [ApplicationCall] as the
 * receiver and the default context built from the request headers, its tenant set from the request; the returned
 * context is passed to the [transport] as is, so it may also set its own tenant. It runs before the requested version is checked. An [ai.koog.a2a.exceptions.A2AException] thrown
 * here is answered as a JSON-RPC error with its code, any other exception as an internal error.
 * Defaults to returning the default context unchanged.
 */
public fun Route.a2aJsonRpcTransportRoute(
    path: String,
    transport: JSONRPCServerTransport,
    heartbeatPeriod: Duration? = null,
    customizeCallContext: suspend ApplicationCall.(ServerCallContext) -> ServerCallContext = { it },
): Route = route(path) {
    // Check SSE plugin is installed
    plugin(SSE)

    // Handle incoming JSON-RPC requests, both regular and streaming
    post {
        // HTTP header names are case-insensitive (HTTP/2 lowercases them), but Ktor's toMap() keeps names as received
        val defaultCtx = ServerCallContext(
            headers = call.request.headers.toMap().withCaseInsensitiveNames()
        )

        transport.handleRequest(
            requestRaw = call.receiveText(),
            ctx = defaultCtx,
            respond = { response ->
                // Bypass content negotiation: streaming requests send `Accept: text/event-stream`,
                // but errors raised before the stream starts are still answered with plain JSON
                call.respondText(JSONRPCJson.encodeToString(response), ContentType.Application.Json)
            },
            respondStreaming = { responseFlow ->
                // Reply with SSE (implementation copied from SSE plugin code)
                call.response.apply {
                    header(HttpHeaders.ContentType, ContentType.Text.EventStream.toString())
                    header(HttpHeaders.CacheControl, "no-store")
                    header(HttpHeaders.Connection, "keep-alive")
                    header("X-Accel-Buffering", "no")
                }

                call.respond(
                    message = SSEServerContent(call) {
                        heartbeatPeriod?.let { period -> heartbeat { this.period = period } }
                        responseFlow.collect { response ->
                            send(ServerSentEvent(JSONRPCJson.encodeToString(response)))
                        }
                    }
                )
            },
            customizeCallContext = { call.customizeCallContext(it) },
        )
    }
}

/**
 * Route serving [agentCard] at [path] with HTTP caching headers.
 *
 * The card is serialized once, so the weak `ETag` always matches the body. The route installs no Ktor plugin, so it
 * can be mounted into an application that installs `ContentNegotiation`.
 *
 * Responses carry the `ETag`, `Cache-Control: max-age` and `Last-Modified`. `public` is not set, as the route may be
 * behind authentication. A matching `If-None-Match`, or without it an `If-Modified-Since` not earlier than
 * [lastModified], gets `304 Not Modified` with no body.
 *
 * @param path The path at which the card is served.
 * @param agentCard The card to serve.
 * @param cacheMaxAge `max-age` of `Cache-Control`; `null` omits the header.
 * @param lastModified `Last-Modified` of the card. Defaults to when the route is built; pass a fixed value to keep it
 * stable across restarts and replicas. `null` omits the header.
 * @throws IllegalArgumentException if [cacheMaxAge] is negative.
 */
public fun Route.a2aAgentCardRoute(
    path: String,
    agentCard: AgentCard,
    cacheMaxAge: Duration? = 1.hours,
    lastModified: Instant? = Clock.System.now(),
): Route {
    require(cacheMaxAge == null || !cacheMaxAge.isNegative()) { "cacheMaxAge must not be negative: $cacheMaxAge" }

    val body = JSONRPCJson.encodeToString(AgentCard.serializer(), agentCard)
    val opaqueTag = "\"${hex(sha1(body.encodeToByteArray()))}\""
    val etag = "W/$opaqueTag"
    val cacheControl = cacheMaxAge?.let { "max-age=${it.inWholeSeconds}" }
    val lastModifiedSeconds = lastModified?.epochSeconds
    val lastModifiedHeader = lastModifiedSeconds?.let { GMTDate(it * 1000).toHttpDate() }

    return route(path) {
        get {
            call.response.header(HttpHeaders.ETag, etag)
            cacheControl?.let { call.response.header(HttpHeaders.CacheControl, it) }
            lastModifiedHeader?.let { call.response.header(HttpHeaders.LastModified, it) }

            val notModified = call.request.headers[HttpHeaders.IfNoneMatch]
                ?.let { ifNoneMatchMatches(it, opaqueTag) }
                ?: ifModifiedSinceMatches(call.request.headers[HttpHeaders.IfModifiedSince], lastModifiedSeconds)

            if (notModified) {
                call.respond(HttpStatusCode.NotModified)
            } else {
                call.respondText(body, ContentType.Application.Json)
            }
        }
    }
}

/** Weak comparison of an `If-None-Match` header value (`*` or a comma-separated list of tags) with [opaqueTag]. */
private fun ifNoneMatchMatches(header: String, opaqueTag: String): Boolean =
    header.split(',').map { it.trim() }.any { it == "*" || it.removePrefix("W/") == opaqueTag }

/** Whether an `If-Modified-Since` header value is a valid date not earlier than [lastModifiedSeconds]. */
private fun ifModifiedSinceMatches(header: String?, lastModifiedSeconds: Long?): Boolean {
    if (header == null || lastModifiedSeconds == null) return false
    val since = runCatching { header.fromHttpToGmtDate() }.getOrNull() ?: return false
    return lastModifiedSeconds <= Instant.fromEpochMilliseconds(since.timestamp).epochSeconds
}
