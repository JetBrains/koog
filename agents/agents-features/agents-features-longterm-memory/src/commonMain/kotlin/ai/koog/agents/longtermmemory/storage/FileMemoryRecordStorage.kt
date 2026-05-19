package ai.koog.agents.longtermmemory.storage

import ai.koog.agents.longtermmemory.model.MemoryRecord
import ai.koog.embeddings.base.Embedder
import ai.koog.rag.base.TextDocument
import ai.koog.rag.base.files.DocumentProvider
import ai.koog.rag.base.files.FileSystemProvider
import ai.koog.rag.base.files.createDirectory
import ai.koog.rag.base.files.readText
import ai.koog.rag.base.files.writeText
import ai.koog.rag.base.storage.DeletionStorage
import ai.koog.rag.base.storage.LookupStorage
import ai.koog.rag.base.storage.SearchStorage
import ai.koog.rag.base.storage.WriteStorage
import ai.koog.rag.base.storage.search.KeywordSearchRequest
import ai.koog.rag.base.storage.search.Score
import ai.koog.rag.base.storage.search.ScoreMetric
import ai.koog.rag.base.storage.search.SearchRequest
import ai.koog.rag.base.storage.search.SearchResult
import ai.koog.rag.base.storage.search.SimilaritySearchRequest
import ai.koog.rag.vector.backend.FileVectorStorageBackend
import ai.koog.rag.vector.embedder.TextDocumentEmbedder
import ai.koog.rag.vector.storage.EmbeddingStorage
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * File-system based implementation of [SearchStorage], [WriteStorage], [LookupStorage],
 * and [DeletionStorage] for [TextDocument] records that can be used as the underlying
 * record storage for [ai.koog.agents.longtermmemory.feature.LongTermMemory].
 *
 * The implementation reuses [FileVectorStorageBackend] and [EmbeddingStorage] from
 * `rag-vector` to provide persistent, embedding-based storage of [MemoryRecord]s.
 *
 * Each namespace is mapped to a dedicated sub-directory under [root] (since
 * [EmbeddingStorage] itself does not support namespaces). Inside a namespace
 * sub-directory the on-disk layout is the standard one produced by
 * [FileVectorStorageBackend] (`documents/` and `vectors/` sub-directories).
 *
 * [MemoryRecord]s are serialized to JSON on disk so that their `id` and `metadata`
 * fields are preserved across restarts.
 *
 * Search support:
 * - [SimilaritySearchRequest] is delegated to the underlying [EmbeddingStorage] and
 *   uses the provided [Embedder] for vector similarity.
 * - [KeywordSearchRequest] is implemented as a simple case-insensitive substring match
 *   over all stored documents in the namespace.
 *
 * @param embedder The embedder used to compute vector representations of stored records.
 * @param fs The file system provider used to access the file system.
 * @param root The root directory under which all namespace sub-directories will be created.
 * @param defaultNamespace The default namespace to use when none is specified in method calls.
 */
public open class FileMemoryRecordStorage<Path>(
    private val embedder: Embedder,
    private val fs: FileSystemProvider.ReadWrite<Path>,
    private val root: Path,
    private val defaultNamespace: String = "default",
) : SearchStorage<TextDocument, SearchRequest>,
    WriteStorage<TextDocument>,
    LookupStorage<TextDocument>,
    DeletionStorage {

    private val registryMutex = Mutex()
    private val namespaceStorages = mutableMapOf<String, NamespaceEmbeddingStorage>()
    private val namespaceMutexes = mutableMapOf<String, Mutex>()

    /**
     * Maximum allowed length of a namespace name or document id used as a filesystem path segment.
     */
    private val maxSegmentLength: Int = 128

    /**
     * Allowed characters for a namespace name or document id when used as a filesystem path segment.
     * The whitelist is intentionally strict to prevent path traversal and platform-specific
     * filename pitfalls (separators, reserved characters, case-insensitive collisions, etc.).
     *
     * Notes:
     * - Lowercase only — uppercase letters are rejected so that `Foo` and `foo` cannot collide
     *   on case-insensitive filesystems (macOS default, Windows).
     * - Must not end with `.` (problematic on Windows, where trailing dots are stripped).
     */
    private val safeSegmentRegex: Regex = Regex("[a-z0-9_-][a-z0-9._-]*[a-z0-9_-]|[a-z0-9_-]")

    /**
     * Validates that [value] is safe to use as a single filesystem path segment.
     * Throws [IllegalArgumentException] otherwise.
     */
    private fun requireSafeSegment(value: String, label: String) {
        require(value.isNotEmpty()) { "$label must not be empty" }
        require(value.length <= maxSegmentLength) {
            "$label must be at most $maxSegmentLength characters long, but was ${value.length}"
        }
        require(value != "." && value != "..") { "$label must not be '.' or '..'" }
        require(!value.endsWith('.')) { "$label must not end with '.': '$value'" }
        require(safeSegmentRegex.matches(value)) {
            "$label must be lowercase and match [a-z0-9._-], but was: '$value'"
        }
    }

    /**
     * Internal [EmbeddingStorage] subclass that exposes the backing
     * [ai.koog.rag.vector.backend.VectorStorageBackend] so that records can be inserted
     * under a caller-supplied id (which the public [EmbeddingStorage.add] API does not allow).
     */
    private inner class NamespaceEmbeddingStorage(
        embedder: ai.koog.rag.vector.embedder.DocumentEmbedder<TextDocument>,
        private val provider: DocumentProvider<Path, TextDocument>,
        backend: FileVectorStorageBackend<TextDocument, Path>,
        private val nsRoot: Path,
    ) : EmbeddingStorage<TextDocument>(embedder, backend) {
        private val docEmbedder = embedder

        /**
         * Inserts (or overwrites) a record under a caller-supplied id.
         *
         * Writes the document JSON and its vector directly to disk, bypassing
         * [EmbeddingStorage.add] (which always generates a UUID) and the backend's
         * update path (which requires a pre-existing document file and would otherwise
         * force us to write an empty/invalid placeholder first).
         *
         * Each file is written by first writing its full content to a unique temp file
         * and then atomically renaming it into place, so a concurrent reader or a crash
         * cannot observe a partially-written or empty/invalid JSON file.
         */
        @OptIn(ExperimentalUuidApi::class)
        suspend fun putWithId(id: String, document: TextDocument) {
            val vector = docEmbedder.embed(document)

            val docsDir = fs.joinPath(nsRoot, "documents")
            if (!fs.exists(docsDir)) fs.createDirectory(docsDir)
            val vecsDir = fs.joinPath(nsRoot, "vectors")
            if (!fs.exists(vecsDir)) fs.createDirectory(vecsDir)

            val docPath = fs.joinPath(docsDir, id)
            val docText = provider.text(document).toString()
            writeAtomically(docsDir, docPath, docText)

            val vecPath = fs.joinPath(vecsDir, id)
            val vectorJson = vectorJson.encodeToString<ai.koog.embeddings.base.Vector>(vector)
            writeAtomically(vecsDir, vecPath, vectorJson)
        }

        @OptIn(ExperimentalUuidApi::class)
        private suspend fun writeAtomically(parentDir: Path, target: Path, content: String) {
            val tmp = fs.joinPath(parentDir, ".tmp-${Uuid.random()}")
            fs.writeText(tmp, content)
            try {
                if (fs.exists(target)) {
                    fs.delete(target)
                }
                fs.move(tmp, target)
            } catch (t: Throwable) {
                if (fs.exists(tmp)) {
                    runCatching { fs.delete(tmp) }
                }
                throw t
            }
        }
    }

    private val vectorJson = Json { prettyPrint = true }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * A [DocumentProvider] that uses JSON files on disk as the document representation.
     * Each file stores a single [MemoryRecord] serialized as JSON. The provider's
     * [text] returns the original [MemoryRecord.content] so that the underlying
     * [TextDocumentEmbedder] embeds only the textual content (not the JSON envelope).
     */
    private inner class MemoryRecordFileProvider : DocumentProvider<Path, TextDocument> {
        override suspend fun document(path: Path): TextDocument? {
            if (!fs.exists(path)) return null
            val raw = fs.readText(path)
            val serializable = json.decodeFromString(SerializableMemoryRecord.serializer(), raw)
            return serializable.toMemoryRecord()
        }

        override suspend fun text(document: TextDocument): CharSequence {
            // Persisted form is JSON; embedding form (computed via TextDocumentEmbedder) uses content directly.
            val serializable = SerializableMemoryRecord.fromTextDocument(document)
            return json.encodeToString(SerializableMemoryRecord.serializer(), serializable)
        }
    }

    /**
     * Resolves the effective namespace, validating it against the safe-segment rules.
     */
    private fun resolveNamespace(namespace: String?): String {
        val ns = namespace ?: defaultNamespace
        requireSafeSegment(ns, "namespace")
        return ns
    }

    /**
     * Returns the per-namespace [Mutex] used to serialize all I/O operations
     * (add/update/delete/get/search) for that namespace, so that concurrent callers
     * cannot observe partial or intermediate on-disk states.
     */
    private suspend fun namespaceMutex(namespace: String): Mutex = registryMutex.withLock {
        namespaceMutexes.getOrPut(namespace) { Mutex() }
    }

    private suspend fun storageFor(namespace: String): NamespaceEmbeddingStorage {
        return registryMutex.withLock {
            namespaceStorages.getOrPut(namespace) {
                val nsRoot = fs.joinPath(root, namespace)
                if (!fs.exists(nsRoot)) {
                    fs.createDirectory(nsRoot)
                }
                val provider = MemoryRecordFileProvider()
                NamespaceEmbeddingStorage(
                    embedder = object : TextDocumentEmbedder<TextDocument, Path>(provider, embedder) {
                        // Override embed(document) so we embed the textual content,
                        // not the JSON envelope returned by provider.text().
                        override suspend fun embed(document: TextDocument) = embedder.embed(document.content)
                    },
                    provider = provider,
                    backend = FileVectorStorageBackend(provider, fs, nsRoot),
                    nsRoot = nsRoot,
                )
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun add(documents: List<TextDocument>, namespace: String?): List<String> {
        val ns = resolveNamespace(namespace)
        // Validate all caller-provided ids up front so we don't write anything if any id is unsafe.
        documents.forEach { doc -> doc.id?.let { requireSafeSegment(it, "document id") } }
        // Reject batches that would silently overwrite themselves due to duplicate ids.
        val providedIds = documents.mapNotNull { it.id }
        require(providedIds.size == providedIds.toSet().size) {
            "document ids in the same add() batch must be unique, but got duplicates: " +
                providedIds.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        }
        val storage = storageFor(ns)
        return namespaceMutex(ns).withLock {
            documents.map { doc ->
                // For both caller-provided and generated ids we write the record exactly once,
                // with the final id already baked into the on-disk JSON envelope.
                val id = doc.id ?: Uuid.random().toString()
                storage.putWithId(id, doc.toMemoryRecord(id = id))
                id
            }
        }
    }

    override suspend fun update(documents: Map<String, TextDocument>, namespace: String?): List<String> {
        val ns = resolveNamespace(namespace)
        documents.keys.forEach { id -> requireSafeSegment(id, "document id") }
        val storage = storageFor(ns)
        val rebound = documents.mapValues { (id, doc) -> doc.toMemoryRecord(id = id) }
        return namespaceMutex(ns).withLock {
            storage.update(rebound)
        }
    }

    override suspend fun get(ids: List<String>, namespace: String?): List<TextDocument> {
        val ns = resolveNamespace(namespace)
        ids.forEach { requireSafeSegment(it, "document id") }
        val storage = storageFor(ns)
        return namespaceMutex(ns).withLock { storage.get(ids) }
    }

    override suspend fun delete(ids: List<String>, namespace: String?): List<String> {
        val ns = resolveNamespace(namespace)
        ids.forEach { requireSafeSegment(it, "document id") }
        val storage = storageFor(ns)
        return namespaceMutex(ns).withLock { storage.delete(ids) }
    }

    override suspend fun search(request: SearchRequest, namespace: String?): List<SearchResult<TextDocument>> {
        val ns = resolveNamespace(namespace)
        val storage = storageFor(ns)
        val lock = namespaceMutex(ns)
        return when (request) {
            is SimilaritySearchRequest -> lock.withLock { storage.search(request, namespace = null) }
            is KeywordSearchRequest -> lock.withLock {
                val queryLower = request.queryText.lowercase()
                val minScore = request.minScore ?: 0.0
                storage.allDocuments().toList()
                    .filter { it.content.lowercase().contains(queryLower) }
                    .map { SearchResult(it, Score(1.0, ScoreMetric.COSINE_SIMILARITY)) }
                    .filter { it.score.value >= minScore }
                    .take(request.limit)
            }

            else -> throw UnsupportedOperationException(
                "FileMemoryRecordStorage supports only KeywordSearchRequest and SimilaritySearchRequest, " +
                    "but got: ${request::class.simpleName}"
            )
        }
    }

    private fun TextDocument.toMemoryRecord(id: String? = this.id): MemoryRecord = MemoryRecord(
        content = content,
        id = id,
        metadata = metadata,
    )

    @Serializable
    private data class SerializableMemoryRecord(
        val content: String,
        val id: String? = null,
        val metadata: Map<String, String> = emptyMap(),
    ) {
        fun toMemoryRecord(): MemoryRecord = MemoryRecord(content, id, metadata)

        companion object {
            fun fromTextDocument(doc: TextDocument): SerializableMemoryRecord = SerializableMemoryRecord(
                content = doc.content,
                id = doc.id,
                // MemoryRecord.metadata is Map<String, Any>; persist values as their string representation.
                metadata = doc.metadata.mapValues { (_, value) -> value.toString() },
            )
        }
    }
}
