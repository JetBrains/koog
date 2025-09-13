package ai.koog.prompt.executor.ollama.client

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO

internal actual fun engineFactoryProvider(): HttpClientEngineFactory<*> = CIO
