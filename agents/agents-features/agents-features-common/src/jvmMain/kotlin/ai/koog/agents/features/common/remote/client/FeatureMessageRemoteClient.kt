package ai.koog.agents.features.common.remote.client

import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*

internal actual fun engineFactoryProvider(): HttpClientEngineFactory<HttpClientEngineConfig> = CIO
