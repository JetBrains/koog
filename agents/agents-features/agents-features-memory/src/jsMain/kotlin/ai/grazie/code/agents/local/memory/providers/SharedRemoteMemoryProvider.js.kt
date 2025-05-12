package ai.grazie.code.agents.local.memory.providers

import io.ktor.client.engine.*
import io.ktor.client.engine.js.*

actual fun engineFactoryProvider(): HttpClientEngineFactory<*> = Js