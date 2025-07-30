package ai.koog.agents.core.feature.writer

import ai.koog.agents.core.feature.message.FeatureMessage
import ai.koog.agents.core.feature.message.FeatureMessageProcessor
import ai.koog.agents.core.utils.MutexCheck.withLockCheck
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.Sink
import kotlin.concurrent.Volatile
import kotlin.properties.Delegates

/**
 * A feature message processor responsible for writing feature messages to a file.
 * This abstract class provides functionality to convert and write feature messages into a target file using a specified file system provider.
 *
 * @param Path The type representing paths supported by the file system provider.
 * @param targetPath The file where feature messages will be written.
 * @param sinkOpener Returns a [Sink] for writing to the file, this class manages its lifecycle.
 */
public abstract class FeatureMessageFileWriter<Path>(
    public val targetPath: Path,
    private val sinkOpener: (Path) -> Sink,
) : FeatureMessageProcessor() {

    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    private var _sink: Sink by Delegates.notNull()

    @Volatile
    private var _isOpen: MutableStateFlow<Boolean> = MutableStateFlow(false)

    private val writerMutex = Mutex()

    /**
     * Indicates whether the writer is currently open and ready for operation.
     *
     * This property reflects the state of the writer, which transitions between open and closed
     * during its lifecycle. For instance, `isOpen` is set to `true` after the writer is successfully
     * initialized using the `initialize()` method and set to `false` upon closure via the `close()` method.
     *
     * The value of this property is used to enforce correct usage of the writer, ensuring that
     * operations, such as writing or processing messages, are only permitted when the writer is open.
     *
     * Accessing this property allows for thread-safe checking of the writer's state, particularly in
     * scenarios that involve concurrent operations.
     */
    override val isOpen: StateFlow<Boolean>
        get() = _isOpen.asStateFlow()

    /**
     * Converts the `FeatureMessage` instance to its corresponding string representation
     * suitable for writing to a file.
     *
     * This method should handle the serialization or formatting of the feature message,
     * ensuring that all the necessary attributes are represented in the output string consistently.
     *
     * @return A string representation of the `FeatureMessage` formatted for file output.
     */
    public abstract fun FeatureMessage.toFileString(): String

    override suspend fun processMessage(message: FeatureMessage) {
        check(isOpen.value) { "Writer is not initialized. Please make sure you call method 'initialize()' before." }
        writeMessage(message.toFileString())
    }

    override suspend fun initialize() {
        withLockEnsureClosed {
            logger.debug { "Writer initialization is started." }

            super.initialize()
            _sink = sinkOpener(targetPath)
            _isOpen.value = true

            logger.debug { "Writer initialization is finished." }
        }
    }

    override suspend fun close() {
        withLockEnsureOpen {
            _isOpen.value = false
            _sink.close()
        }
    }

    //region Private Methods

    private suspend fun writeMessage(message: String) {
        writerMutex.withLock {
            _sink.write(message.encodeToByteArray())

            // TODO: Add support for system line separator when kotlin multiplatform fixes the issue:
            //  https://github.com/Kotlin/kotlinx-io/issues/448
            _sink.write("\n".encodeToByteArray())
            _sink.flush()
        }
    }

    private suspend fun withLockEnsureClosed(action: suspend () -> Unit) =
        writerMutex.withLockCheck(
            check = { isOpen.value },
            message = { "Writer is already opened" },
            action = action
        )

    private suspend fun withLockEnsureOpen(action: suspend () -> Unit) =
        writerMutex.withLockCheck(
            check = { !isOpen.value },
            message = { "Writer is already closed" },
            action = action
        )

    //endregion Private Methods
}
