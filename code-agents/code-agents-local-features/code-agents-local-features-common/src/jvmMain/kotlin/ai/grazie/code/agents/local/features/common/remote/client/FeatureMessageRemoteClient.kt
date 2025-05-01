package ai.grazie.code.agents.local.features.common.remote.client

import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO

actual fun engineFactoryProvider(): HttpClientEngineFactory<HttpClientEngineConfig> = CIO
