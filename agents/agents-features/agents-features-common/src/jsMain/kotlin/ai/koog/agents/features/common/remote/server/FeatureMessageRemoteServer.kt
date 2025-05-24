package ai.koog.agents.features.common.remote.server

import io.ktor.server.engine.*

internal actual fun engineFactoryProvider(): ApplicationEngineFactory<ApplicationEngine, ApplicationEngine.Configuration> {
    throw NotImplementedError("Feature Server Engine factory is not supported on JS")
}
