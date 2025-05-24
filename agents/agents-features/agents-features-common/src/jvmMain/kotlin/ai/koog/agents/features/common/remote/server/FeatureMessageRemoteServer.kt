package ai.koog.agents.features.common.remote.server

import io.ktor.server.cio.*
import io.ktor.server.engine.*

@Suppress("UNCHECKED_CAST")
internal actual fun engineFactoryProvider(): ApplicationEngineFactory<ApplicationEngine, ApplicationEngine.Configuration> {
    return CIO as ApplicationEngineFactory<ApplicationEngine, ApplicationEngine.Configuration>
}
