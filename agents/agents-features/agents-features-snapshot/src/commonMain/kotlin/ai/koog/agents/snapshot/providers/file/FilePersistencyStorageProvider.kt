package ai.koog.agents.snapshot.providers.file

import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.providers.PersistenceStorageProvider
import ai.koog.agents.snapshot.providers.PersistenceUtils
import ai.koog.agents.snapshot.providers.filters.AgentCheckpointPredicateFilter
import ai.koog.rag.base.files.FileSystemProvider
import ai.koog.rag.base.files.createDirectory
import ai.koog.rag.base.files.readText
import ai.koog.rag.base.files.writeText
import kotlinx.serialization.json.Json

@Deprecated(
    "`FilePersistencyStorageProvider` has been renamed to `FilePersistenceStorageProvider`",
    replaceWith = ReplaceWith(
        expression = "FilePersistenceStorageProvider",
        "ai.koog.agents.snapshot.providers.file.FilePersistenceStorageProvider"
    )
)
public typealias FilePersistencyStorageProvider<Path> = FilePersistenceStorageProvider<Path>

/**
 * A file-based implementation of [PersistenceStorageProvider] that stores agent checkpoints in a file system.
 *
 * This implementation organizes checkpoints by agent ID and uses JSON serialization for storing and retrieving
 * checkpoint data. It relies on [FileSystemProvider.ReadWrite] for file system operations.
 *
 * @param Path Type representing the file path in the storage system.
 * @param fs A file system provider enabling read and write operations for file storage.
 * @param root Root file path where the checkpoint storage will organize data.
 */
public open class FilePersistenceStorageProvider<Path>(
    private val fs: FileSystemProvider.ReadWrite<Path>,
    private val root: Path,
    private val json: Json = PersistenceUtils.defaultCheckpointJson
) : PersistenceStorageProvider<AgentCheckpointPredicateFilter> {

    /**
     * Directory where agent checkpoints are stored
     */
    private suspend fun checkpointsDir(): Path {
        val dir = fs.joinPath(root, "checkpoints")
        if (!fs.exists(dir)) {
            fs.createDirectory(dir)
        }
        return dir
    }

    /**
     * Directory for a specific agent's checkpoints
     */
    private suspend fun agentCheckpointsDir(agentId: String): Path {
        val checkpointsDir = checkpointsDir()
        val agentDir = fs.joinPath(checkpointsDir, agentId)
        if (!fs.exists(agentDir)) {
            fs.createDirectory(agentDir)
        }
        return agentDir
    }

    /**
     * Get the path to a specific checkpoint file
     */
    private suspend fun checkpointPath(agentId: String, checkpointId: String): Path {
        val agentDir = agentCheckpointsDir(agentId)
        return fs.joinPath(agentDir, checkpointId)
    }

    override suspend fun getCheckpoints(agentId: String, filter: AgentCheckpointPredicateFilter?): List<AgentCheckpointData> {
        val agentDir = agentCheckpointsDir(agentId)

        if (!fs.exists(agentDir)) {
            return emptyList()
        }

        val checkpoints = fs.list(agentDir).mapNotNull { path ->
            try {
                val content = fs.readText(path)
                json.decodeFromString<AgentCheckpointData>(content)
            } catch (_: Exception) {
                null
            }
        }

        if (filter != null) {
            return checkpoints.filter { filter.check(it) }
        }

        return checkpoints
    }

    override suspend fun saveCheckpoint(agentId: String, agentCheckpointData: AgentCheckpointData) {
        val checkpointPath = checkpointPath(agentId, agentCheckpointData.checkpointId)
        val serialized = json.encodeToString(AgentCheckpointData.serializer(), agentCheckpointData)
        fs.writeText(checkpointPath, serialized)
    }

    override suspend fun getLatestCheckpoint(agentId: String, filter: AgentCheckpointPredicateFilter?): AgentCheckpointData? =
        getCheckpoints(agentId, filter)
            .maxByOrNull { it.createdAt }
}
