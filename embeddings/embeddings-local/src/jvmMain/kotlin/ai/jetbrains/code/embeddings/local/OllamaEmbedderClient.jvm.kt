package ai.jetbrains.embeddings.local

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO

actual fun engineFactoryProvider(): HttpClientEngineFactory<*> = CIO