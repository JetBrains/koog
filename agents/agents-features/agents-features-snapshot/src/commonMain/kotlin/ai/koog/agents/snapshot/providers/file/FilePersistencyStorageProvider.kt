package ai.koog.agents.snapshot.providers.file

import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.providers.PersistencyStorageProvider
import ai.koog.rag.base.files.FileMetadata
import ai.koog.rag.base.files.FileSystemProvider
import kotlinx.serialization.json.Json

/**
 * A file-based implementation of [PersistencyStorageProvider] that stores agent checkpoints in a file system.
 *
 * This implementation organizes checkpoints by agent ID and uses JSON serialization for storing and retrieving
 * checkpoint data. It relies on [FileSystemProvider.ReadWrite] for file system operations.
 *
 * @param Path Type representing the file path in the storage system.
 * @param fs A file system provider enabling read and write operations for file storage.
 * @param root Root file path where the checkpoint storage will organize data.
 */
public open class FilePersistencyStorageProvider<Path>(
    private val fs: FileSystemProvider.ReadWrite<Path>,
    private val root: Path,
) : PersistencyStorageProvider {
    private val json = Json { prettyPrint = true }

    /**
     * Directory where agent checkpoints are stored
     */
    private suspend fun checkpointsDir(): Path {
        val dir = fs.fromRelativeString(root, "checkpoints")
        if (!fs.exists(dir)) {
            fs.create(root, "checkpoints", FileMetadata.FileType.Directory)
        }
        return dir
    }

    /**
     * Directory for a specific agent's checkpoints
     */
    private suspend fun agentCheckpointsDir(agentId: String): Path {
        val checkpointsDir = checkpointsDir()
        val agentDir = fs.fromRelativeString(checkpointsDir, agentId)
        if (!fs.exists(agentDir)) {
            fs.create(checkpointsDir, agentId, FileMetadata.FileType.Directory)
        }
        return agentDir
    }

    /**
     * Get the path to a specific checkpoint file
     */
    private suspend fun checkpointPath(agentId: String, checkpointId: String): Path {
        val agentDir = agentCheckpointsDir(agentId)
        return fs.fromRelativeString(agentDir, checkpointId)
    }

    override suspend fun getCheckpoints(agentId: String): List<AgentCheckpointData> {
        val agentDir = agentCheckpointsDir(agentId)
        
        if (!fs.exists(agentDir)) {
            return emptyList()
        }
        
        return fs.list(agentDir).mapNotNull { path ->
            try {
                val content = fs.read(path).decodeToString()
                json.decodeFromString<AgentCheckpointData>(content)
            } catch (_: Exception) {
                null
            }
        }
    }

    override suspend fun saveCheckpoint(agentCheckpointData: AgentCheckpointData) {
        val checkpointPath = checkpointPath(agentCheckpointData.agentId, agentCheckpointData.checkpointId)
        val serialized = json.encodeToString(AgentCheckpointData.serializer(), agentCheckpointData)
        fs.write(checkpointPath, serialized.encodeToByteArray())
    }

    override suspend fun getLatestCheckpoint(agentId: String): AgentCheckpointData? {
        return getCheckpoints(agentId)
            .maxByOrNull { it.createdAt }
    }
}