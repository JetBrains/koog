package ai.koog.prompt.executor.ollama.client

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.winhttp.WinHttp

internal actual fun engineFactoryProvider(): HttpClientEngineFactory<*> = WinHttp
