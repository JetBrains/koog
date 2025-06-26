package ai.koog.agents.features.common.remote.server

import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.ApplicationEngineFactory

@Suppress("UNCHECKED_CAST")
internal actual fun engineFactoryProvider(): ApplicationEngineFactory<ApplicationEngine, ApplicationEngine.Configuration> {
    return CIO as ApplicationEngineFactory<ApplicationEngine, ApplicationEngine.Configuration>
}
