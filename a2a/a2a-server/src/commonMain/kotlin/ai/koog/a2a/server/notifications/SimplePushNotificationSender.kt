package ai.koog.a2a.server.notifications

import ai.koog.a2a.model.PushNotificationConfig
import ai.koog.a2a.model.Task
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.core.Closeable
import kotlinx.serialization.json.Json

/**
 * Simple implementation of a notification sender.
 * Doesn't perform any configuration validation.
 * Always takes the first authentication scheme provided in [PushNotificationConfig.authentication]
 */
public class SimplePushNotificationSender(
    baseHttpClient: HttpClient,
    json: Json = Json,
) : PushNotificationSender, Closeable {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    private val httpClient = baseHttpClient.config {
        install(ContentNegotiation) {
            json(json)
        }

        expectSuccess = true
    }

    override suspend fun send(config: PushNotificationConfig, task: Task) {
        try {
            logger.debug { "Sending push notification configId='${config.id} for taskId='${task.id}'" }

            httpClient.post(config.url) {
                config.authentication?.let { auth ->
                    // Simple sender always takes the first scheme from the list
                    val schema = auth.schemes.firstOrNull()
                    val credentials = auth.credentials

                    if (schema != null && credentials != null) {
                        headers[HttpHeaders.Authorization] = "$schema $credentials"
                    }
                }

                config.token?.let { token ->
                    headers[PushNotificationSender.A2A_NOTIFICATION_TOKEN_HEADER] = token
                }

                setBody(task)
            }

            logger.debug { "Sent push notification successfully configId='${config.id} for taskId='${task.id}'" }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to send push notification configId='${config.id} for taskId='${task.id}'" }
        }
    }

    override fun close() {
        httpClient.close()
    }
}
