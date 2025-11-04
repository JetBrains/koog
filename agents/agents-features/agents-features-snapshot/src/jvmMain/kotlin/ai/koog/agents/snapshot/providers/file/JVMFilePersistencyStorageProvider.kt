package ai.koog.agents.snapshot.providers.file

import ai.koog.agents.snapshot.providers.PersistenceUtils
import ai.koog.rag.base.files.JVMFileSystemProvider
import kotlinx.serialization.json.Json
import java.nio.file.Path

@Deprecated(
    "`JVMFilePersistencyStorageProvider` has been renamed to `JVMFilePersistenceStorageProvider`",
    replaceWith = ReplaceWith(
        expression = "JVMFilePersistenceStorageProvider",
        "ai.koog.agents.snapshot.providers.file.JVMFilePersistenceStorageProvider"
    )
)
public typealias JVMFilePersistencyStorageProvider = JVMFilePersistenceStorageProvider

/**
 * A JVM-specific implementation of [FilePersistenceStorageProvider] for managing agent checkpoints
 * in a file system.
 *
 * This class utilizes JVM's [Path] for file system operations and [JVMFileSystemProvider.ReadWrite]
 * for file system access. It organizes checkpoints by agent ID in a structured directory format
 * under the specified root directory.
 *
 * Use this class to persistently store and retrieve agent checkpoints to and from a file-based system
 * in JVM environments.
 *
 * @constructor Initializes the [JVMFilePersistenceStorageProvider] with a specified root directory [root].
 * @param root The root directory where all agent checkpoints will be stored.
 */
public class JVMFilePersistenceStorageProvider @JvmOverloads constructor(
    root: Path,
    json: Json = PersistenceUtils.defaultCheckpointJson
) : FilePersistenceStorageProvider<Path>(
    fs = JVMFileSystemProvider.ReadWrite,
    root = root,
    json = json
)
