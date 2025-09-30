package ai.koog.agents.snapshot.providers.file

import ai.koog.agents.snapshot.providers.PersistencyUtils
import ai.koog.rag.base.files.JVMFileSystemProvider
import kotlinx.serialization.json.Json
import java.nio.file.Path

/**
 * A JVM-specific implementation of [FilePersistencyStorageProvider] for managing agent checkpoints
 * in a file system.
 *
 * This class utilizes JVM's [Path] for file system operations and [JVMFileSystemProvider.ReadWrite]
 * for file system access. It organizes checkpoints by agent ID in a structured directory format
 * under the specified root directory.
 *
 * Use this class to persistently store and retrieve agent checkpoints to and from a file-based system
 * in JVM environments.
 *
 * @constructor Initializes the [JVMFilePersistencyStorageProvider] with a specified root directory [root].
 * @param root The root directory where all agent checkpoints will be stored.
 */
public class JVMFilePersistencyStorageProvider(
    root: Path,
    persistenceId: String,
    json: Json = PersistencyUtils.defaultCheckpointJson
) : FilePersistencyStorageProvider<Path>(
    fs = JVMFileSystemProvider.ReadWrite,
    root = root,
    persistenceId = persistenceId,
    json = json
)
