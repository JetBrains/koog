package ai.jetbrains.code.prompt.executor.clients.openai

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO

actual fun engineFactoryProvider(): HttpClientEngineFactory<*> = CIO