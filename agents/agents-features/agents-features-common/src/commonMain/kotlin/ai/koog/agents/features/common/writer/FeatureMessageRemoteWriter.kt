package ai.koog.agents.features.common.writer

import ai.koog.agents.features.common.MutexCheck.withLockCheck
import ai.koog.agents.features.common.message.FeatureMessage
import ai.koog.agents.features.common.message.FeatureMessageProcessor
import ai.koog.agents.features.common.remote.server.FeatureMessageRemoteServer
import ai.koog.agents.features.common.remote.server.config.DefaultServerConnectionConfig
import ai.koog.agents.features.common.remote.server.config.ServerConnectionConfig
import kotlinx.coroutines.sync.Mutex

/**
 * An abstract class that facilitates writing feature messages to a remote server.
 *
 * @param connectionConfig Configuration for the server connection. If not provided,
 * a default configuration using port 8080 will be used.
 */
public abstract class FeatureMessageRemoteWriter(
    connectionConfig: ServerConnectionConfig? = null
) : FeatureMessageProcessor() {

    private val writerMutex = Mutex()

    /**
     * Indicates the internal state of the writer, specifically whether the connection to the remote server
     * is currently open (`true`) or closed (`false`).
     *
     * This variable is used internally for managing the lifecycle of the server connection.
     * It is updated during server initialization and closure processes, and its state determines
     * whether certain operations can be performed, such as message processing.
     *
     * The value of this property should not be accessed or modified directly outside the class.
     * Use the public `isOpen` getter for read-only access.
     */
    private var _isOpen: Boolean = false

    /**
     * Indicates whether the writer is currently open and initialized.
     *
     * A value of `true` means the writer is open and ready to process messages,
     * while `false` indicates the writer is either not initialized or has been closed.
     *
     * This property reflects the internal `_isOpen` state and ensures thread-safe access.
     */
    public val isOpen: Boolean
        get() = _isOpen

    internal val server: FeatureMessageRemoteServer =
        FeatureMessageRemoteServer(connectionConfig = connectionConfig ?: DefaultServerConnectionConfig())

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
