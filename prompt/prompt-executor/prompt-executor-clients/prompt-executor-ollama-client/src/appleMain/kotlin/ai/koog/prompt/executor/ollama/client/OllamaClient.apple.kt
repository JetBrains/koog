package ai.koog.prompt.executor.ollama.client

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

internal actual fun engineFactoryProvider(): HttpClientEngineFactory<*> = Darwin
