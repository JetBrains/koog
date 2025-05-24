package ai.koog.agents.features.common.remote.client

import io.ktor.client.engine.*
import io.ktor.client.engine.js.*

internal actual fun engineFactoryProvider(): HttpClientEngineFactory<HttpClientEngineConfig> = Js
