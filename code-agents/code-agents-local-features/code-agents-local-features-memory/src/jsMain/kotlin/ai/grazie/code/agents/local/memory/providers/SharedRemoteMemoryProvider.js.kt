package ai.grazie.code.agents.local.memory.providers

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.js.Js

actual fun engineFactoryProvider(): HttpClientEngineFactory<*> = Js