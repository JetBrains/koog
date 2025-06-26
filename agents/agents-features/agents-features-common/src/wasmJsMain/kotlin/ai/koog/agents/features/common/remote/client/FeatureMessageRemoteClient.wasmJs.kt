package ai.koog.agents.features.common.remote.client

import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO

internal actual fun engineFactoryProvider(): HttpClientEngineFactory<HttpClientEngineConfig> = CIO