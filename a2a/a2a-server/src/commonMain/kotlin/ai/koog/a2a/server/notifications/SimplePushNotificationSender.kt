package ai.koog.a2a.server.notifications

import ai.koog.a2a.consts.A2AHeaders
import ai.koog.a2a.model.Event
import ai.koog.a2a.model.TaskPushNotificationConfig
import ai.koog.a2a.serialization.EventSerializer
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Simple implementation of a notification sender: performs an HTTP `POST` of the event to the config URL.
 *
 * - The body is the [Event] serialized as a `StreamResponse` (for example, `{"task": {...}}`) with
 *   the `application/json` content type.
 * - If [TaskPushNotificationConfig.authentication] has credentials, the `Authorization: <scheme> <credentials>`
 *   header is set.
 * - If [TaskPushNotificationConfig.token] is set, it is sent in the [A2AHeaders.X_A2A_NOTIFICATION_TOKEN] header.
 *   This is a convention of the Python A2A SDK, not a part of the specification.
 * - The request is bound by [timeout]. The specification recommends 10-30 seconds.
 * - Any failure (including a non-success HTTP status and a timeout) is logged and swallowed.
 *
 * Doesn't perform any configuration validation (for example, the safety of the URL).
 *
 * @param baseHttpClient The HTTP client to derive the sender's client from.
 * @param json The JSON configuration used to serialize the payload. By default, null and default values are omitted.
 * @param timeout The timeout of the whole request. Defaults to 15 seconds.
 */
public class SimplePushNotificationSender(
    baseHttpClient: HttpClient,
    private val json: Json = DEFAULT_JSON,
    timeout: Duration = DEFAULT_TIMEOUT,
) : PushNotificationSender, AutoCloseable {
    /**
     * Defaults of [SimplePushNotificationSender].
     */
    public companion object {
        private val logger = KotlinLogging.logger {}

        /**
         * The default JSON configuration: omits null and default values, as the other A2A transports do.
         */
        public val DEFAULT_JSON: Json = Json {
            explicitNulls = false
            encodeDefaults = false
            ignoreUnknownKeys = true
        }

        /**
         * The default timeout of a single notification request.
         */
        public val DEFAULT_TIMEOUT: Duration = 15.seconds
    }

    private val httpClient = baseHttpClient.config {
        install(HttpTimeout) {
            requestTimeoutMillis = timeout.inWholeMilliseconds
        }

        expectSuccess = true
    }

    override suspend fun send(config: TaskPushNotificationConfig, event: Event) {
        try {
            logger.debug { "Sending push notification configId='${config.id}' for taskId='${config.taskId}'" }

            val payload = json.encodeToString(EventSerializer, event)

            httpClient.post(config.url) {
                config.authentication?.let { auth ->
                    val credentials = auth.credentials

                    if (credentials != null) {
                        headers[HttpHeaders.Authorization] = "${auth.scheme} $credentials"
                    }
                }

                config.token?.let { token ->
                    headers[A2AHeaders.X_A2A_NOTIFICATION_TOKEN] = token
                }

                contentType(ContentType.Application.Json)
                setBody(payload)
            }

            logger.debug { "Sent push notification successfully configId='${config.id}' for taskId='${config.taskId}'" }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "Failed to send push notification configId='${config.id}' for taskId='${config.taskId}'" }
        }
    }

    override fun close() {
        httpClient.close()
    }
}
