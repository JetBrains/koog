package ai.koog.agents.core.system.mock

import ai.koog.agents.core.feature.message.FeatureMessage
import ai.koog.agents.core.feature.model.events.AgentClosingEvent
import ai.koog.agents.core.feature.model.events.AgentStartingEvent
import ai.koog.agents.core.feature.model.events.DefinedFeatureEvent
import ai.koog.agents.core.feature.remote.client.FeatureMessageRemoteClient
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch

internal class ClientEventsCollector(
    private val client: FeatureMessageRemoteClient
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
                if (event is AgentStartingEvent) {
                    _runId = event.runId
                }

                _collectedEvents.add(event as DefinedFeatureEvent)
                logger.info { "[${_collectedEvents.size}] Received event: $event" }

                if (event is AgentClosingEvent) {
                    cancel()
                }
            }
        }
    }
}
