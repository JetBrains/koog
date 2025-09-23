package ai.koog.agents.features.debugger.mock

import ai.koog.agents.core.feature.message.FeatureMessage
import ai.koog.agents.core.feature.model.events.AIAgentStartedEvent
import ai.koog.agents.core.feature.model.events.DefinedFeatureEvent
import ai.koog.agents.core.feature.remote.client.FeatureMessageRemoteClient
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch

internal class ClientEventsCollector(
    private val client: FeatureMessageRemoteClient,
    private val expectedEventsCount: Int,
) {

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    private var _runId: String? = null

    internal val runId: String
        get() = _runId ?: error("runId is undefined")

    private val _collectedEvents = mutableListOf<FeatureMessage>()

    internal val collectedEvents: List<FeatureMessage>
        get() = _collectedEvents

    internal fun startCollectEvents(coroutineScope: CoroutineScope): Job {
        return coroutineScope.launch {
            client.receivedMessages.consumeAsFlow().collect { event ->
                if (event is AIAgentStartedEvent) {
                    _runId = event.runId
                }

                _collectedEvents.add(event as DefinedFeatureEvent)
                logger.info { "[${_collectedEvents.size}/$expectedEventsCount] Received event: $event" }

                if (_collectedEvents.size >= expectedEventsCount) {
                    cancel()
                }
            }
        }
    }
}
