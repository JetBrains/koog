package ai.jetbrains.embeddings.local

import io.ktor.client.engine.*
import io.ktor.client.engine.js.*

actual fun engineFactoryProvider(): HttpClientEngineFactory<*> = Js