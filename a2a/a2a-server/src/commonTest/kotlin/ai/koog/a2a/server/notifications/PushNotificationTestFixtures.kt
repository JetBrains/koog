package ai.koog.a2a.server.notifications

import ai.koog.a2a.model.Event
import ai.koog.a2a.model.TaskPushNotificationConfig
import kotlinx.coroutines.CompletableDeferred

/**
 * Records push notifications by config id. Sends to a [block]ed config wait until [release] lets the oldest waiting
 * one through, or [unblock] lets all through.
 */
internal class GatedPushNotificationSender : PushNotificationSender {
    /** Notifications whose send has started, in call order. */
    val started = mutableListOf<Pair<String, Event>>()

    /** Notifications whose send has returned normally, in completion order. */
    val sent = mutableListOf<Pair<String, Event>>()

    /** Returns the failure to throw for a notification, or `null` to send it. */
    var failure: (configId: String, event: Event) -> Exception? = { _, _ -> null }

    private val gates = mutableMapOf<String, ArrayDeque<CompletableDeferred<Unit>>>()

    fun block(configId: String) {
        gates.getOrPut(configId) { ArrayDeque() }
    }

    fun release(configId: String) {
        gates.getValue(configId).removeFirst().complete(Unit)
    }

    fun unblock(configId: String) {
        gates.remove(configId)?.forEach { it.complete(Unit) }
    }

    override suspend fun send(config: TaskPushNotificationConfig, event: Event) {
        val configId = checkNotNull(config.id)
        started += configId to event

        gates[configId]?.let { waiting -> CompletableDeferred<Unit>().also { waiting.addLast(it) }.await() }
        failure(configId, event)?.let { throw it }

        sent += configId to event
    }
}

internal fun pushConfig(taskId: String, id: String) =
    TaskPushNotificationConfig(taskId = taskId, id = id, url = "https://example.com/$id")

/** The notifications of [events] to the config [configId], as recorded by [GatedPushNotificationSender]. */
internal fun pushes(configId: String, events: List<Event>): List<Pair<String, Event>> = events.map { configId to it }
