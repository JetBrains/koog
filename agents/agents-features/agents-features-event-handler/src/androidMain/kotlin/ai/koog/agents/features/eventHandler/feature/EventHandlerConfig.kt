@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.agents.features.eventHandler.feature

import ai.koog.agents.core.feature.config.FeatureConfig

public actual open class EventHandlerConfig actual constructor() :
    FeatureConfig(),
    EventHandlerConfigAPI by EventHandlerConfigImpl()
