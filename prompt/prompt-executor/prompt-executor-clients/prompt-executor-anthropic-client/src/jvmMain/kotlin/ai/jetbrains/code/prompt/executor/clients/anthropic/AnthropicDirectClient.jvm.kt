package ai.jetbrains.code.prompt.executor.clients.anthropic

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO

actual fun engineFactoryProvider(): HttpClientEngineFactory<*> = CIO