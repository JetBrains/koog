package ai.koog.agents.core.feature.writer

import ai.koog.agents.core.feature.message.FeatureMessage
import ai.koog.agents.core.feature.message.FeatureMessageProcessor
import ai.koog.agents.core.feature.remote.server.FeatureMessageRemoteServer
import ai.koog.agents.core.feature.remote.server.config.DefaultServerConnectionConfig
import ai.koog.agents.core.feature.remote.server.config.ServerConnectionConfig
import ai.koog.agents.core.utils.MutexCheck.withLockCheck
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlin.jvm.JvmOverloads

/**
 * An abstract class that facilitates writing feature messages to a remote server.
 *
 * @param connectionConfig Configuration for the server connection. If not provided,
 * a default configuration using port 50881 will be used.
 */
public abstract class FeatureMessageRemoteWriter @JvmOverloads constructor(
    connectionConfig: ServerConnectionConfig? = null
) : FeatureMessageProcessor() {

    private val writerMutex = Mutex()

    /**
     * Indicates whether the writer is currently open and initialized.
     *
     * A value of `true` means the writer is open and ready to process messages,
     * while `false` indicates the writer is either not initialized or has been closed.
     *
     * This property reflects the internal `_isOpen` state and ensures thread-safe access.
     */
    override val isOpen: StateFlow<Boolean>
        get() = server.isStarted

    internal val server: FeatureMessageRemoteServer =
        FeatureMessageRemoteServer(connectionConfig = connectionConfig ?: DefaultServerConnectionConfig())

    override suspend fun initialize() {
        withLockEnsureClosed {
            super.initialize()
            server.start()
        }
    }

    override suspend fun processMessage(message: FeatureMessage) {
        check(isOpen.value) { "Writer is not initialized. Please make sure you call method 'initialize()' before." }
        server.sendMessage(message)
    }

    override suspend fun close() {
        withLockEnsureOpen {
            server.close()
        }
    }

    //region Private Methods

    private suspend fun withLockEnsureClosed(action: suspend () -> Unit) =
        writerMutex.withLockCheck(
            check = { isOpen.value },
            message = { "Server is already started" },
            action = action
        )

    private suspend fun withLockEnsureOpen(action: suspend () -> Unit) =
        writerMutex.withLockCheck(
            check = { !isOpen.value },
            message = { "Server is already stopped" },
            action = action
        )

    //endregion Private Methods
}
