package ai.koog.prompt.executor.clients.ollama

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.js.Js

internal actual fun engineFactoryProvider(): HttpClientEngineFactory<*> = Js
