package ai.koog.agents.core.feature.mock

import ai.koog.agents.core.feature.message.FeatureMessage
import ai.koog.agents.core.feature.message.FeatureMessageProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TestFeatureMessageProcessor : FeatureMessageProcessor() {

    val processedMessages = mutableListOf<FeatureMessage>()

    private var _isOpen = MutableStateFlow(false)

    override val isOpen: StateFlow<Boolean>
        get() = _isOpen.asStateFlow()

    override suspend fun initialize() {
        super.initialize()
        _isOpen.value = true
    }

    override suspend fun processMessage(message: FeatureMessage) {
        processedMessages.add(message)
    }

    override suspend fun close() {
        _isOpen.value = false
    }
}
