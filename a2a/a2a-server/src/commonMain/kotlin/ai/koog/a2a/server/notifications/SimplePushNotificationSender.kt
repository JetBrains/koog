package ai.koog.a2a.server.notifications

import ai.koog.a2a.consts.A2AHeaders
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskPushNotificationConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Simple implementation of a notification sender.
 * Doesn't perform any configuration validation.
 * Uses the authentication scheme provided in [TaskPushNotificationConfig.authentication]
 */
public class SimplePushNotificationSender(
    baseHttpClient: HttpClient,
    json: Json = Json,
) : PushNotificationSender, AutoCloseable {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    private val httpClient = baseHttpClient.config {
        install(ContentNegotiation) {
            json(json)
        }

        expectSuccess = true
    }

    override suspend fun send(config: TaskPushNotificationConfig, task: Task) {
        try {
            logger.debug { "Sending push notification configId='${config.id} for taskId='${task.id}'" }

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
