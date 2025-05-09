package ai.jetbrains.code.prompt.executor.clients.openrouter

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.js.Js

actual fun engineFactoryProvider(): HttpClientEngineFactory<*> = Js