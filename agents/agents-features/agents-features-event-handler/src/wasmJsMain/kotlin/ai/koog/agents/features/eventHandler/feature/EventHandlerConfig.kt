@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.agents.features.eventHandler.feature

import ai.koog.agents.core.feature.config.FeatureConfig

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public actual open class EventHandlerConfig actual constructor() : FeatureConfig(),
    EventHandlerConfigAPI by EventHandlerConfigImpl()
