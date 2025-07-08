package ai.koog.prompt.cache.files

import ai.koog.prompt.cache.model.PromptCache
import ai.koog.prompt.message.Message
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.*

internal val defaultJson = Json {
    ignoreUnknownKeys = true
    allowStructuredMapKeys = true
}

internal val prettyJson = Json {
    ignoreUnknownKeys = true
    allowStructuredMapKeys = true
    prettyPrint = true
    prettyPrintIndent = "  "
}


/**
 * File-based implementation of [PromptCache].
 * This implementation stores cache entries in files on the file system.
 *
 * @param storage The directory where cache files will be stored
 * @param maxFiles The maximum number of files to store in the cache (default: 3000).
 *                When this limit is reached, the least recently accessed files will be removed.
 */
public class FilePromptCache(
    storage: Path,
    private val maxFiles: Int? = 3000
) : PromptCache {
    /**
     * Factory implementation for creating `FilePromptCache` instances based on the provided configuration.
     *
     * This class inherits from `PromptCache.Factory.Named` and is used to build a file-based cache
     * for storing prompt execution results. The cache location and optional configurations are
     * derived from the input configuration string.
     *
     * @constructor Initializes the factory with a default `Path` to be used when specific configurations are not provided.
     * @param default The default `Path` for the file cache if none is specified in the configuration.
     */
    public class Factory(private val default: Path) : PromptCache.Factory.Named("file") {
        override fun create(config: String): PromptCache {
            val parts = elements(config)
            val path = when {
                parts.size < 2 -> default
                parts[1].isEmpty() -> default
                else -> Path(parts[1])
            }

            val maxFiles = when {
                parts.size < 3 || parts[2].isEmpty() -> null
                else -> parts[2].toIntOrNull() ?: throw IllegalArgumentException(
                    "Invalid file cache size limit: ${parts[2]}. Expected a number."
                )
            }

            return FilePromptCache(path, maxFiles)
        }
    }

    private val requestsDir = storage.resolve("requests").apply { createDirectories() }

    private val access = ConcurrentHashMap<String, Instant>()
    private val lock = Mutex()

    @Serializable
    private data class CachedElement(val response: List<Message.Response>, val request: PromptCache.Request)

    override suspend fun get(request: PromptCache.Request): List<Message.Response>? {
        val response = getOrNull(request)

        if (response != null) {
            access[request.asCacheKey] = Instant.now()
        }

        return response
    }

    override suspend fun put(request: PromptCache.Request, response: List<Message.Response>): Unit = lock.withLock {
        // Check if we need to remove old files before adding a new one
        enforceFileLimit()

        // Create the directory if it doesn't exist
        requestsDir.createDirectories()

        // Write the file
        file(request).writeText(prettyJson.encodeToString(CachedElement(response, request)))

        // Update timestamps
        val now = Instant.now()
        access[request.asCacheKey] = now
    }

    private fun file(request: PromptCache.Request): Path = requestsDir / request.asCacheKey

    private fun getOrNull(request: PromptCache.Request): List<Message.Response>? {
        val file = file(request)
        if (!file.exists()) return null

        return defaultJson.decodeFromString<CachedElement>(file.readText()).response
    }

    /**
     * Enforces the maximum file limit by removing the least recently accessed files
     * when the number of files exceeds the maximum limit.
     */
    private fun enforceFileLimit() {
        if (maxFiles == null) return
        if (access.size < maxFiles) return

        // Find the least recently accessed files
        val toRemove = access.entries
            .sortedBy { it.value }
            .take(maxOf((maxFiles * 0.1).toInt(), access.size - maxFiles + 1))
            .map { it.key }

        // Remove the files
        for (id in toRemove) {
            val filePath = requestsDir / id
            if (filePath.exists()) {
                filePath.deleteIfExists()
            }

            // Remove from timestamp maps
            access.remove(id)
        }
    }

    /**
     * Initializes the timestamp maps by scanning the existing cache directory.
     * This is called when the cache is created to ensure we track all existing files.
     */
    init {
        if (requestsDir.exists()) {
            val now = Instant.now()
            requestsDir.listDirectoryEntries().forEach { file ->
                val fileId = file.name
                access[fileId] = now
            }
        }
    }
}
