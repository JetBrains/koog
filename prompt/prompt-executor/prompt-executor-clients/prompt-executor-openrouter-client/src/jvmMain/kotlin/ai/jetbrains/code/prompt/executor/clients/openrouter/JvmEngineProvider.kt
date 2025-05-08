package ai.jetbrains.code.prompt.executor.clients.openrouter

import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*

internal actual fun engineFactoryProvider(): HttpClientEngineFactory<*> = CIO