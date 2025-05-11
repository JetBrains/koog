package ai.grazie.code.agents.local.features.common.writer

import ai.grazie.code.agents.local.features.common.MutexCheck.withLockCheck
import ai.grazie.code.agents.local.features.common.message.FeatureMessage
import ai.grazie.code.agents.local.features.common.message.FeatureMessageProcessor
import ai.grazie.code.agents.local.features.common.remote.server.FeatureMessageRemoteServer
import ai.grazie.code.agents.local.features.common.remote.server.ServerConnectionConfig
import kotlinx.coroutines.sync.Mutex

/**
 * An abstract class that facilitates writing feature messages to a remote server.
 *
 * @param connectionConfig Configuration for the server connection. If not provided,
 * a default configuration using port 8080 will be used.
 */
abstract class FeatureMessageRemoteWriter(
    connectionConfig: ServerConnectionConfig? = null
) : FeatureMessageProcessor() {

    private val writerMutex = Mutex()

    private var _isOpen: Boolean = false

    val isOpen: Boolean
        get() = _isOpen

    internal val server: FeatureMessageRemoteServer =
        FeatureMessageRemoteServer(connectionConfig = connectionConfig ?: ServerConnectionConfig())

    override suspend fun initialize() {
        withLockEnsureClosed {
            server.start()
            super.initialize()

            _isOpen = true
        }
    }

    override suspend fun processMessage(message: FeatureMessage) {
        check(isOpen) { "Writer is not initialized. Please make sure you call method 'initialize()' before." }
        server.sendMessage(message)
    }

    override suspend fun close() {
        withLockEnsureOpen {
            server.close()

            _isOpen = false
        }
    }

    //region Private Methods

    private suspend fun withLockEnsureClosed(action: suspend () -> Unit) =
        writerMutex.withLockCheck(
            check = { isOpen },
            message = { "Server is already started" },
            action = action
        )

    private suspend fun withLockEnsureOpen(action: suspend () -> Unit) =
        writerMutex.withLockCheck(
            check = { !isOpen },
            message = { "Server is already stopped" },
            action = action
        )

    //endregion Private Methods
}
