package ai.koog.prompt.executor.ollama.client

import io.ktor.client.engine.cio.CIO

internal actual fun engineFactoryProvider(): io.ktor.client.engine.HttpClientEngineFactory<*> = CIO
