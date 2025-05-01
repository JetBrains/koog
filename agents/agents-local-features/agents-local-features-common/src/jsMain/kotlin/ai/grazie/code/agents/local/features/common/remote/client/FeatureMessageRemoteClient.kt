package ai.grazie.code.agents.local.features.common.remote.client

import io.ktor.client.engine.*
import io.ktor.client.engine.js.*

actual fun engineFactoryProvider(): HttpClientEngineFactory<HttpClientEngineConfig> = Js
