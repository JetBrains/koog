package ai.grazie.code.agents.local.features.common.writer

import ai.grazie.code.agents.local.features.common.MutexCheck.withLockCheck
import ai.grazie.code.agents.local.features.message.FeatureMessage
import ai.grazie.code.agents.local.features.message.FeatureMessageProcessor
import ai.grazie.code.files.model.FileSystemProvider
import ai.grazie.code.files.model.isFile
import ai.grazie.utils.mpp.LoggerFactory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.io.Sink
import kotlin.concurrent.Volatile
import kotlin.properties.Delegates

/**
 * A feature message processor responsible for writing feature messages to a file.
 * This abstract class provides functionality to convert and write feature messages into a target file using a specified file system provider.
 *
 * @param Path The type representing paths supported by the file system provider.
 * @param fs The file system provider used to interact with the file system for reading and writing.
 * @param root The directory root or file path where messages will be written. If root is a directory, a new file will
 *             be created at initialization.
 * @param append Whether to append to an existing file or overwrite it. Defaults to `false`.
 */
abstract class FeatureMessageFileWriter<Path>(
    private val fs: FileSystemProvider.ReadWrite<Path>,
    private val root: Path,
    private val append: Boolean = false,
) : FeatureMessageProcessor() {

    companion object {
        private val logger =
            LoggerFactory.create("ai.grazie.code.agents.local.features.common.provider.FeatureMessageFileWriter")
    }


    private var _sink: Sink by Delegates.notNull()

    private var _targetPath: Path? = null

    @Volatile
    private var _isOpen: Boolean = false

    private val writerMutex = Mutex()


    val targetPath: Path
        get() = _targetPath ?: error("Target path is not initialized. Please make sure you call method 'initialize()' before.")

    val isOpen: Boolean
        get() = _isOpen


    abstract fun FeatureMessage.toFileString(): String

    override suspend fun processMessage(message: FeatureMessage) {
        check(isOpen) { "Writer is not initialized. Please make sure you call method 'initialize()' before." }
        writeMessage(message.toFileString())
    }

    override suspend fun initialize() {
        withLockEnsureClosed {
            logger.debug { "Writer initialization is started." }

            val path = resolvePath(fs, root)
            val sink = fs.sink(path, append)

            _sink = sink
            _targetPath = path

            _isOpen = true
            super.initialize()

            logger.debug { "Writer initialization is finished." }
        }
    }

    override suspend fun close() {
        withLockEnsureOpen {
            _isOpen = false
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

    private suspend fun resolvePath(fs: FileSystemProvider.ReadWrite<Path>, root: Path): Path {
        // Root is an existing file
        if (fs.exists(root) && fs.isFile(root)) {
            return root
        }

        val targetFileName = "agent-trace-${Clock.System.now().toEpochMilliseconds()}.log"
        return fs.fromRelativeString(root, targetFileName)
    }

    private suspend fun withLockEnsureClosed(action: suspend () -> Unit) =
        writerMutex.withLockCheck(
            check = { isOpen },
            message = { "Writer is already opened" },
            action = action
        )

    private suspend fun withLockEnsureOpen(action: suspend () -> Unit) =
        writerMutex.withLockCheck(
            check = { !isOpen },
            message = { "Writer is already closed" },
            action = action
        )

    //endregion Private Methods
}
