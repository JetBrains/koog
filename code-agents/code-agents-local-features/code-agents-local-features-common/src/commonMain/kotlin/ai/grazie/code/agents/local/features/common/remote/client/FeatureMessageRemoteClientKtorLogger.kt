package ai.grazie.code.agents.local.features.common.remote.client

import ai.grazie.utils.mpp.LoggerFactory
import io.ktor.client.plugins.logging.Logger

class FeatureMessageRemoteClientKtorLogger : Logger {

    companion object {
        private val logger =
            LoggerFactory.create("ai.grazie.code.agents.local.features.common.remote.client.FeatureMessageRemoteClientKtorLogger")
    }

    val debugEnabled: Boolean
        get() = logger.debugEnabled

    override fun log(message: String) {
        logger.debug { message }
    }
}
