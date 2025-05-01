package ai.grazie.code.agents.local.memory.providers

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO

actual fun engineFactoryProvider(): HttpClientEngineFactory<*> = CIO