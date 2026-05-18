package ai.koog.agents.example

package org.example.agents.coding.utils

import ai.koog.embeddings.base.Embedder
import ai.koog.rag.base.TextDocument
import ai.koog.rag.base.storage.search.HasFilterExpression
import ai.koog.rag.base.storage.search.HybridSearchRequest
import ai.koog.rag.base.storage.search.KeywordSearchRequest
import ai.koog.rag.base.storage.search.Score
import ai.koog.rag.base.storage.search.ScoreMetric
import ai.koog.rag.base.storage.search.SearchRequest
import ai.koog.rag.base.storage.search.SearchResult
import ai.koog.rag.base.storage.search.SimilaritySearchRequest
import ai.koog.rag.vector.storage.VectorStorage
import com.pgvector.PGvector
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.security.MessageDigest
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.sql.DataSource

private val logger = KotlinLogging.logger {}

/**
 * Tuning parameters for the pgvector HNSW index created by [PGVectorStorage.migrate].
 *
 * Only cosine distance (`vector_cosine_ops`) is supported by the query layer, so [distance]
 * is accepted for backward compatibility but ignored with a warning if not [HnswDistance.COSINE].
 */
public data class HnswParams(
    val m: Int = 16,
    val efConstruction: Int = 64,
    val distance: HnswDistance = HnswDistance.COSINE,
)

/** Distance functions nominally supported for the pgvector HNSW index. */
public enum class HnswDistance(internal val opClass: String) {
    COSINE("vector_cosine_ops"),
    L2("vector_l2_ops"),
    INNER_PRODUCT("vector_ip_ops"),
}

/**
 * Chunking configuration for long documents. Sizes are in characters (approximate token count
 * assuming ~4 chars/token for English prose). Injectable via constructor.
 *
 * @property chunkSizeChars target chunk size in characters. Defaults to ~2000 chars (~500 tokens).
 * @property overlapChars overlap between consecutive chunks. Defaults to ~300 chars (~15%).
 * @property minChunkChars minimum size for a trailing chunk not to be discarded/merged.
 */
public data class ChunkingParams(
    val chunkSizeChars: Int = 2000,
    val overlapChars: Int = 300,
    val minChunkChars: Int = 200,
) {
    init {
        require(chunkSizeChars > 0) { "chunkSizeChars must be > 0" }
        require(overlapChars in 0 until chunkSizeChars) { "overlapChars must be in [0, chunkSizeChars)" }
        require(minChunkChars in 1..chunkSizeChars) { "minChunkChars must be in [1, chunkSizeChars]" }
    }
}

/**
 * Result of a batched write operation: per-document success/failure reporting.
 */
public data class BatchResult(
    val successes: List<String>,
    val failures: Map<String, String> = emptyMap(),
) {
    public val total: Int get() = successes.size + failures.size
}

/**
 * Reserved metadata keys injected by [PGVectorStorage] into search results. These keys live
 * under a reserved `_koog.*` prefix so callers can distinguish system-populated fields from
 * their own metadata.
 */
public object KoogSystemMetadataKeys {
    public const val DOCUMENT_ID: String = "_koog.documentId"
    public const val CHUNK_ID: String = "_koog.chunkId"
    public const val CHUNK_INDEX: String = "_koog.chunkIndex"
    public const val CHUNK_COUNT: String = "_koog.chunkCount"
    public const val NAMESPACE: String = "_koog.namespace"
}

/**
 * Production-oriented PostgreSQL + pgvector implementation of [VectorStorage] over [TextDocument].
 *
 * ### Schema (v2)
 * A single shared schema with two tables and a `namespace` column:
 * - `koog_documents(id, namespace, content, content_hash, metadata, embedding_model, embedding_dim, created_at, updated_at)`
 * - `koog_document_chunks(chunk_id, document_id, namespace, chunk_index, chunk_count, content, embedding, tsv, token_count)`
 *
 * Ranking operates on chunks. Search results expose the matched chunk as
 * [SearchResult.document.content], with `documentId`, `chunkId`, `chunkIndex` and `chunkCount`
 * surfaced under the reserved [KoogSystemMetadataKeys] prefix. `SearchResult.id` is set to the
 * parent document id so callers can round-trip it through [get] and [delete].
 *
 * ### Hybrid search
 * True fusion via Reciprocal Rank Fusion (RRF) over independent vector top-K and FTS top-K
 * result lists over chunks. Trigram fuzzy search is only used as an explicit keyword fallback
 * when FTS returns nothing, not during hybrid.
 *
 * ### Scoring
 * - Similarity search → [ScoreMetric.COSINE_SIMILARITY], clamped to `[0, 1]`. `minScore` applies.
 * - Keyword (FTS) → [ScoreMetric.BM25] (ts_rank). `minScore` applies on `ts_rank` values.
 * - Keyword (trigram fallback) → [ScoreMetric.CUSTOM]. `minScore` applies on trigram similarity.
 * - Hybrid → [ScoreMetric.HYBRID]. Scores are RRF-fused ranks; `minScore` is REJECTED because
 *   RRF values are not comparable to raw similarity thresholds.
 *
 * ### Filtering
 * `filterExpression` on search requests is not silently ignored. If set, it is rejected with
 * [IllegalArgumentException] until a typed metadata filter API ships.
 *
 * ### Distance
 * Only cosine distance is honored by query execution. [HnswParams.distance] is logged if
 * configured to anything else and treated as cosine.
 *
 * ### Migration
 * Calling [migrate] creates the v2 schema and extensions. [migrate] on a legacy per-namespace
 * table name (default `memory_records`) will back-migrate existing rows into `koog_documents`
 * with a single chunk per document, preserving ids. The legacy table is left untouched.
 *
 * ### Namespaces
 * The [tableName] constructor argument is kept for source compatibility and is used as the
 * default namespace label on the shared schema; operations accepting `namespace: String?`
 * override it per call. There is no longer one table per namespace.
 */
public class PGVectorStorage private constructor(
    private val dataSource: DataSource,
    private val ownsDataSource: Boolean,
    private val tableName: String = DEFAULT_NAMESPACE,
    private val vectorDimension: Int = 3072,
    private val embedder: Embedder? = null,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val hnsw: HnswParams = HnswParams(),
    private val chunking: ChunkingParams = ChunkingParams(),
    private val embeddingModelId: String = embedder?.javaClass?.name ?: "unknown",
) : VectorStorage<TextDocument, SearchRequest>, AutoCloseable {

    public constructor(
        dataSource: DataSource,
        tableName: String = DEFAULT_NAMESPACE,
        vectorDimension: Int = 3072,
        embedder: Embedder? = null,
        json: Json = Json { ignoreUnknownKeys = true },
        hnsw: HnswParams = HnswParams(),
        chunking: ChunkingParams = ChunkingParams(),
    ) : this(
        dataSource = dataSource,
        ownsDataSource = false,
        tableName = tableName,
        vectorDimension = vectorDimension,
        embedder = embedder,
        json = json,
        hnsw = hnsw,
        chunking = chunking,
    )

    private val embedderDimValidated = java.util.concurrent.atomic.AtomicBoolean(false)
    private val migrated = java.util.concurrent.atomic.AtomicBoolean(false)
    private val knownNamespaces = ConcurrentHashMap.newKeySet<String>()

    init {
        if (hnsw.distance != HnswDistance.COSINE) {
            logger.warn {
                "HnswDistance ${hnsw.distance} is not honored by the query layer; " +
                        "PGVectorStorage ranks using cosine similarity only."
            }
        }
    }

    public companion object {
        public const val DEFAULT_NAMESPACE: String = "default"
        internal const val LEGACY_TABLE_NAME: String = "memory_records"
        internal const val DOCUMENTS_TABLE: String = "koog_documents"
        internal const val CHUNKS_TABLE: String = "koog_document_chunks"

        public fun fromJdbcUrl(
            jdbcUrl: String,
            username: String,
            password: String,
            tableName: String = DEFAULT_NAMESPACE,
            vectorDimension: Int = 3072,
            embedder: Embedder? = null,
            maximumPoolSize: Int = 10,
            json: Json = Json { ignoreUnknownKeys = true },
            hnsw: HnswParams = HnswParams(),
            chunking: ChunkingParams = ChunkingParams(),
        ): PGVectorStorage {
            val cfg = HikariConfig().apply {
                this.jdbcUrl = jdbcUrl
                this.username = username
                this.password = password
                this.maximumPoolSize = maximumPoolSize
                this.poolName = "pgvector-koog"
                this.driverClassName = "org.postgresql.Driver"
            }
            val ds = HikariDataSource(cfg)
            return PGVectorStorage(
                dataSource = ds,
                ownsDataSource = true,
                tableName = tableName,
                vectorDimension = vectorDimension,
                embedder = embedder,
                json = json,
                hnsw = hnsw,
                chunking = chunking,
            )
        }
    }

    private fun getConnection(): Connection {
        val conn = dataSource.connection
        PGvector.addVectorType(conn)
        return conn
    }

    private suspend fun <T> inTx(block: (Connection) -> T): T = withContext(Dispatchers.IO) {
        getConnection().use { conn ->
            val prevAutoCommit = conn.autoCommit
            conn.autoCommit = false
            try {
                val result = block(conn)
                conn.commit()
                result
            } catch (e: Throwable) {
                try { conn.rollback() } catch (r: SQLException) { logger.warn(r) { "Rollback failed" } }
                throw e
            } finally {
                try { conn.autoCommit = prevAutoCommit } catch (_: SQLException) { /* ignore */ }
            }
        }
    }

    private suspend fun <T> readOnly(block: (Connection) -> T): T = withContext(Dispatchers.IO) {
        getConnection().use { conn -> block(conn) }
    }

    private fun resolveNamespace(namespace: String?): String {
        val name = namespace ?: tableName
        require(name.matches(Regex("^[a-zA-Z0-9_]+$"))) {
            "Invalid namespace: '$name'. Only alphanumeric characters and underscores are allowed."
        }
        return name
    }

    private suspend fun ensureEmbedderDim() {
        val emb = embedder ?: return
        if (embedderDimValidated.get()) return
        val actual = emb.embed("dimension probe").values.size
        require(actual == vectorDimension) {
            "Configured embedder produces vectors of dimension $actual, " +
                    "but PGVectorStorage is configured for $vectorDimension. " +
                    "Set vectorDimension to match the embedding model."
        }
        embedderDimValidated.set(true)
    }

    // ==================== MIGRATION ====================

    /**
     * Initializes the v2 shared schema and migrates legacy per-namespace tables if present.
     * Automatically attempts to import the legacy `memory_records` table into the shared schema.
     */
    public suspend fun migrate() {
        migrateSchema()
        maybeImportLegacyTable(LEGACY_TABLE_NAME)
        knownNamespaces.add(resolveNamespace(null))
    }

    /**
     * Registers the given namespace as known. In v2 there is one shared schema, so this also
     * ensures the schema exists and, if a legacy table named [namespace] exists, imports its
     * rows into the shared schema (one chunk per legacy row, preserving ids).
     */
    public suspend fun migrate(namespace: String) {
        val ns = resolveNamespace(namespace)
        migrateSchema()
        maybeImportLegacyTable(ns)
        knownNamespaces.add(ns)
    }

    private suspend fun migrateSchema() {
        if (migrated.get()) return
        inTx { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeUpdate("CREATE EXTENSION IF NOT EXISTS vector")
                stmt.executeUpdate("CREATE EXTENSION IF NOT EXISTS pg_trgm")

                stmt.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS $DOCUMENTS_TABLE (
                        id VARCHAR(255) NOT NULL,
                        namespace VARCHAR(255) NOT NULL,
                        content TEXT NOT NULL,
                        content_hash VARCHAR(64) NOT NULL,
                        metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
                        embedding_model VARCHAR(255) NOT NULL,
                        embedding_dim INTEGER NOT NULL,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                        updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                        PRIMARY KEY (namespace, id)
                    )
                    """.trimIndent()
                )
                stmt.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_${DOCUMENTS_TABLE}_namespace " +
                            "ON $DOCUMENTS_TABLE (namespace)"
                )
                stmt.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_${DOCUMENTS_TABLE}_metadata " +
                            "ON $DOCUMENTS_TABLE USING GIN (metadata jsonb_path_ops)"
                )

                stmt.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS $CHUNKS_TABLE (
                        chunk_id VARCHAR(255) NOT NULL PRIMARY KEY,
                        document_id VARCHAR(255) NOT NULL,
                        namespace VARCHAR(255) NOT NULL,
                        chunk_index INTEGER NOT NULL,
                        chunk_count INTEGER NOT NULL,
                        content TEXT NOT NULL,
                        embedding vector($vectorDimension),
                        tsv TSVECTOR,
                        token_count INTEGER,
                        FOREIGN KEY (namespace, document_id)
                            REFERENCES $DOCUMENTS_TABLE(namespace, id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                stmt.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_${CHUNKS_TABLE}_ns " +
                            "ON $CHUNKS_TABLE (namespace)"
                )
                stmt.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_${CHUNKS_TABLE}_doc " +
                            "ON $CHUNKS_TABLE (namespace, document_id, chunk_index)"
                )

                // Trigger that maintains tsv on the chunks table.
                stmt.executeUpdate(
                    """
                    CREATE OR REPLACE FUNCTION ${CHUNKS_TABLE}_tsv_trigger()
                    RETURNS TRIGGER AS ${'$'}${'$'}
                    BEGIN
                      NEW.tsv := to_tsvector('english', COALESCE(NEW.content, ''));
                      RETURN NEW;
                    END;
                    ${'$'}${'$'} LANGUAGE plpgsql
                    """.trimIndent()
                )
                stmt.executeUpdate("DROP TRIGGER IF EXISTS ${CHUNKS_TABLE}_tsv_update ON $CHUNKS_TABLE")
                stmt.executeUpdate(
                    """
                    CREATE TRIGGER ${CHUNKS_TABLE}_tsv_update
                    BEFORE INSERT OR UPDATE OF content ON $CHUNKS_TABLE
                    FOR EACH ROW EXECUTE PROCEDURE ${CHUNKS_TABLE}_tsv_trigger()
                    """.trimIndent()
                )

                stmt.executeUpdate(
                    """
                    CREATE INDEX IF NOT EXISTS idx_${CHUNKS_TABLE}_embedding
                    ON $CHUNKS_TABLE USING hnsw (embedding vector_cosine_ops)
                    WITH (m = ${hnsw.m}, ef_construction = ${hnsw.efConstruction})
                    """.trimIndent()
                )
                stmt.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_${CHUNKS_TABLE}_tsv " +
                            "ON $CHUNKS_TABLE USING GIN (tsv)"
                )
                stmt.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_${CHUNKS_TABLE}_content_trgm " +
                            "ON $CHUNKS_TABLE USING GIN (content gin_trgm_ops)"
                )
            }
        }
        migrated.set(true)
        logger.info {
            "Migrated pgvector v2 schema (dim=$vectorDimension, hnsw.m=${hnsw.m}, " +
                    "hnsw.ef=${hnsw.efConstruction}, chunking=$chunking)"
        }
    }

    /**
     * If a legacy per-namespace table named [ns] exists with the old single-row-per-document
     * shape, copy its rows into the shared schema as one-chunk documents. Idempotent: rows
     * that already exist in $DOCUMENTS_TABLE under the namespace are skipped.
     */
    private suspend fun maybeImportLegacyTable(ns: String) = withContext(Dispatchers.IO) {
        getConnection().use { conn ->
            val exists = tableExists(conn, ns) && columnExists(conn, ns, "embedding")
            if (!exists) return@use
            val prevAutoCommit = conn.autoCommit
            conn.autoCommit = false
            try {
                conn.prepareStatement(
                    """
                    INSERT INTO $DOCUMENTS_TABLE
                        (id, namespace, content, content_hash, metadata, embedding_model, embedding_dim)
                    SELECT src.id, ?, src.content, ?, src.metadata, ?, ?
                    FROM $ns src
                    WHERE NOT EXISTS (
                        SELECT 1 FROM $DOCUMENTS_TABLE d
                        WHERE d.namespace = ? AND d.id = src.id
                    )
                    """.trimIndent()
                ).use { stmt ->
                    stmt.setString(1, ns)
                    stmt.setString(2, "legacy")
                    stmt.setString(3, "legacy")
                    stmt.setInt(4, vectorDimension)
                    stmt.setString(5, ns)
                    stmt.executeUpdate()
                }
                conn.prepareStatement(
                    """
                    INSERT INTO $CHUNKS_TABLE
                        (chunk_id, document_id, namespace, chunk_index, chunk_count, content, embedding, token_count)
                    SELECT src.id || ':0', src.id, ?, 0, 1, src.content, src.embedding,
                           length(src.content)
                    FROM $ns src
                    WHERE NOT EXISTS (
                        SELECT 1 FROM $CHUNKS_TABLE c
                        WHERE c.namespace = ? AND c.document_id = src.id
                    )
                    """.trimIndent()
                ).use { stmt ->
                    stmt.setString(1, ns)
                    stmt.setString(2, ns)
                    stmt.executeUpdate()
                }
                conn.commit()
                logger.info { "Imported legacy table '$ns' into shared schema for namespace '$ns'" }
            } catch (e: Throwable) {
                try { conn.rollback() } catch (_: SQLException) { /* ignore */ }
                logger.warn(e) { "Legacy import from table '$ns' skipped due to error" }
            } finally {
                try { conn.autoCommit = prevAutoCommit } catch (_: SQLException) { /* ignore */ }
            }
        }
    }

    private fun tableExists(conn: Connection, name: String): Boolean =
        conn.metaData.getTables(null, null, name, arrayOf("TABLE")).use { it.next() }

    private fun columnExists(conn: Connection, table: String, column: String): Boolean =
        conn.metaData.getColumns(null, null, table, column).use { it.next() }

    private fun ensureMigrated() {
        require(migrated.get()) {
            "PGVectorStorage schema has not been initialized. Call migrate() first."
        }
    }

    private fun ensureKnownNamespaceForRead(ns: String) {
        // Reads must not silently create schema or rows. Unknown namespaces produce empty results
        // but we require the shared schema to exist.
        ensureMigrated()
    }

    // ==================== CHUNKING ====================

    private data class PreparedDocument(
        val documentId: String,
        val namespace: String,
        val content: String,
        val contentHash: String,
        val metadataJson: String,
        val chunks: List<PreparedChunk>,
    )

    private data class PreparedChunk(
        val chunkId: String,
        val documentId: String,
        val namespace: String,
        val chunkIndex: Int,
        val chunkCount: Int,
        val content: String,
        val embedding: PGvector?,
        val tokenCount: Int,
    )

    internal fun chunkText(text: String): List<String> {
        if (text.isEmpty()) return listOf("")
        val size = chunking.chunkSizeChars
        val overlap = chunking.overlapChars
        val step = (size - overlap).coerceAtLeast(1)
        if (text.length <= size) return listOf(text)
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val end = (start + size).coerceAtMost(text.length)
            val slice = text.substring(start, end)
            if (chunks.isNotEmpty() && slice.length < chunking.minChunkChars) {
                // Merge trailing small chunk into the previous chunk by appending
                // only the non-overlapping tail portion beyond the previous chunk's end.
                val prevEnd = start + overlap  // the previous chunk already covers up to this point
                if (prevEnd < end) {
                    chunks[chunks.lastIndex] = chunks.last() + text.substring(prevEnd, end)
                }
                break
            }
            chunks.add(slice)
            if (end == text.length) break
            start += step
        }
        return chunks
    }

    private fun sha256(s: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(s.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // ==================== CREATE/UPDATE ====================

    override suspend fun add(documents: List<TextDocument>, namespace: String?): List<String> =
        addDetailed(documents, namespace).successes

    /**
     * Upserts documents. Note: this has been documented upsert semantics — existing ids are
     * replaced (including all their chunks).
     */
    public suspend fun addDetailed(documents: List<TextDocument>, namespace: String?): BatchResult {
        if (documents.isEmpty()) return BatchResult(emptyList())
        ensureMigrated()
        ensureEmbedderDim()
        val ns = resolveNamespace(namespace)
        knownNamespaces.add(ns)

        val failures = linkedMapOf<String, String>()
        val prepared = mutableListOf<PreparedDocument>()
        for (doc in documents) {
            val id = doc.id ?: UUID.randomUUID().toString()
            try {
                prepared.add(prepareDocument(id, ns, doc))
            } catch (e: Exception) {
                logger.warn(e) { "Failed to prepare document id=$id" }
                failures[id] = e.message ?: "Embedding/serialization failed"
            }
        }
        if (prepared.isEmpty()) return BatchResult(emptyList(), failures)

        val successes = mutableListOf<String>()
        runCatching {
            inTx { conn ->
                executeUpsert(conn, prepared, successes, failures)
            }
        }.onFailure { e ->
            logger.error(e) { "Transactional add failed; rolling back" }
            prepared.forEach { failures.putIfAbsent(it.documentId, "Transaction rolled back: ${e.message}") }
            successes.clear()
        }
        return BatchResult(successes, failures)
    }

    override suspend fun update(documents: Map<String, TextDocument>, namespace: String?): List<String> =
        updateDetailed(documents, namespace).successes

    /**
     * Replaces existing documents and their chunks. Documents whose ids do not already exist
     * in the namespace are reported as failures (unlike [add], which upserts).
     */
    public suspend fun updateDetailed(documents: Map<String, TextDocument>, namespace: String?): BatchResult {
        if (documents.isEmpty()) return BatchResult(emptyList())
        ensureMigrated()
        ensureEmbedderDim()
        val ns = resolveNamespace(namespace)

        val failures = linkedMapOf<String, String>()
        val prepared = mutableListOf<PreparedDocument>()
        for ((id, doc) in documents) {
            try {
                prepared.add(prepareDocument(id, ns, doc))
            } catch (e: Exception) {
                logger.warn(e) { "Failed to prepare update for id=$id" }
                failures[id] = e.message ?: "Embedding/serialization failed"
            }
        }
        if (prepared.isEmpty()) return BatchResult(emptyList(), failures)

        val successes = mutableListOf<String>()
        runCatching {
            inTx { conn ->
                // Filter to existing ids first.
                val existing = existingIds(conn, ns, prepared.map { it.documentId })
                val (present, missing) = prepared.partition { it.documentId in existing }
                missing.forEach { failures[it.documentId] = "Record not found in namespace '$ns'" }
                if (present.isNotEmpty()) executeUpsert(conn, present, successes, failures)
            }
        }.onFailure { e ->
            logger.error(e) { "Transactional update failed; rolling back" }
            prepared.forEach { failures.putIfAbsent(it.documentId, "Transaction rolled back: ${e.message}") }
            successes.clear()
        }
        return BatchResult(successes, failures)
    }

    private suspend fun prepareDocument(id: String, ns: String, doc: TextDocument): PreparedDocument {
        val pieces = chunkText(doc.content)
        val chunkCount = pieces.size
        val chunks = pieces.mapIndexed { i, piece ->
            val emb = computeEmbedding(piece)
            PreparedChunk(
                chunkId = "$id:$i",
                documentId = id,
                namespace = ns,
                chunkIndex = i,
                chunkCount = chunkCount,
                content = piece,
                embedding = emb?.let { PGvector(it.toFloatArray()) },
                tokenCount = piece.length / 4,
            )
        }
        val metadataJson = json.encodeToString(JsonObject.serializer(), toJsonObject(doc.metadata))
        return PreparedDocument(
            documentId = id,
            namespace = ns,
            content = doc.content,
            contentHash = sha256(doc.content),
            metadataJson = metadataJson,
            chunks = chunks,
        )
    }

    private fun existingIds(conn: Connection, ns: String, ids: List<String>): Set<String> {
        if (ids.isEmpty()) return emptySet()
        val placeholders = ids.joinToString(",") { "?" }
        val out = HashSet<String>()
        conn.prepareStatement(
            "SELECT id FROM $DOCUMENTS_TABLE WHERE namespace = ? AND id IN ($placeholders)"
        ).use { stmt ->
            stmt.setString(1, ns)
            ids.forEachIndexed { i, v -> stmt.setString(i + 2, v) }
            stmt.executeQuery().use { rs -> while (rs.next()) out.add(rs.getString(1)) }
        }
        return out
    }

    private fun executeUpsert(
        conn: Connection,
        docs: List<PreparedDocument>,
        successes: MutableList<String>,
        failures: MutableMap<String, String>,
    ) {
        // Upsert parent rows.
        conn.prepareStatement(
            """
            INSERT INTO $DOCUMENTS_TABLE
                (id, namespace, content, content_hash, metadata, embedding_model, embedding_dim, updated_at)
            VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, now())
            ON CONFLICT (namespace, id) DO UPDATE SET
                content = EXCLUDED.content,
                content_hash = EXCLUDED.content_hash,
                metadata = EXCLUDED.metadata,
                embedding_model = EXCLUDED.embedding_model,
                embedding_dim = EXCLUDED.embedding_dim,
                updated_at = now()
            """.trimIndent()
        ).use { stmt ->
            for (d in docs) {
                stmt.setString(1, d.documentId)
                stmt.setString(2, d.namespace)
                stmt.setString(3, d.content)
                stmt.setString(4, d.contentHash)
                stmt.setString(5, d.metadataJson)
                stmt.setString(6, embeddingModelId)
                stmt.setInt(7, vectorDimension)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }

        // Replace chunks: delete existing chunks for these (namespace, document_id), then insert fresh.
        conn.prepareStatement(
            "DELETE FROM $CHUNKS_TABLE WHERE namespace = ? AND document_id = ?"
        ).use { stmt ->
            for (d in docs) {
                stmt.setString(1, d.namespace)
                stmt.setString(2, d.documentId)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }

        conn.prepareStatement(
            """
            INSERT INTO $CHUNKS_TABLE
                (chunk_id, document_id, namespace, chunk_index, chunk_count, content, embedding, token_count)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { stmt ->
            for (d in docs) {
                for (c in d.chunks) {
                    stmt.setString(1, c.chunkId)
                    stmt.setString(2, c.documentId)
                    stmt.setString(3, c.namespace)
                    stmt.setInt(4, c.chunkIndex)
                    stmt.setInt(5, c.chunkCount)
                    stmt.setString(6, c.content)
                    if (c.embedding != null) stmt.setObject(7, c.embedding) else stmt.setObject(7, null)
                    stmt.setInt(8, c.tokenCount)
                    stmt.addBatch()
                }
            }
            stmt.executeBatch()
        }

        docs.forEach { d ->
            if (!failures.containsKey(d.documentId)) successes.add(d.documentId)
        }
    }

    // ==================== READ ====================

    override suspend fun get(ids: List<String>, namespace: String?): List<TextDocument> {
        if (ids.isEmpty()) return emptyList()
        ensureMigrated()
        val ns = resolveNamespace(namespace)
        ensureKnownNamespaceForRead(ns)
        val placeholders = ids.joinToString(",") { "?" }
        return readOnly { conn ->
            val result = mutableListOf<TextDocument>()
            conn.prepareStatement(
                """
                SELECT id, content, metadata::text
                FROM $DOCUMENTS_TABLE
                WHERE namespace = ? AND id IN ($placeholders)
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, ns)
                ids.forEachIndexed { i, id -> stmt.setString(i + 2, id) }
                stmt.executeQuery().use { rs ->
                    while (rs.next()) result.add(parseDocumentRow(rs))
                }
            }
            result
        }
    }

    // ==================== SEARCH ====================

    override suspend fun search(request: SearchRequest, namespace: String?): List<SearchResult<TextDocument>> {
        ensureMigrated()
        val ns = resolveNamespace(namespace)
        ensureKnownNamespaceForRead(ns)

        if (request is HasFilterExpression) {
            require(request.filterExpression == null) {
                "PGVectorStorage does not implement filterExpression yet; pass null or use a typed filter API."
            }
        }

        val overFetch = (request.limit + request.offset).coerceAtLeast(1)

        val (scored, metric) = when (request) {
            is SimilaritySearchRequest -> {
                val minScore = request.minScore ?: 0.0
                val emb = requireNotNull(embedder) {
                    "SimilaritySearchRequest requires an Embedder to be configured on PGVectorStorage."
                }
                ensureEmbedderDim()
                val qv = emb.embed(request.queryText).values.map { it.toFloat() }
                searchByVector(qv, overFetch, minScore, ns) to ScoreMetric.COSINE_SIMILARITY
            }
            is KeywordSearchRequest -> {
                val minScore = request.minScore ?: 0.0
                val primary = searchByFullText(request.queryText, overFetch, minScore, ns)
                if (primary.isNotEmpty()) {
                    primary to ScoreMetric.BM25
                } else {
                    logger.debug { "FTS empty, falling back to trigram fuzzy search for ns=$ns" }
                    // Trigram similarity threshold is in [0,1]; bias toward the caller's minScore
                    // when provided, otherwise use a conservative 0.2.
                    val trigramThreshold = if (request.minScore != null) minScore else 0.2
                    searchByTrigram(request.queryText, overFetch, trigramThreshold, ns) to ScoreMetric.CUSTOM
                }
            }
            is HybridSearchRequest -> {
                require(request.minScore == null) {
                    "HybridSearchRequest.minScore is not supported: RRF-fused scores are not " +
                            "comparable to raw similarity thresholds. Apply filtering after search."
                }
                val alpha = request.alpha
                // alpha=0.0 → vector-only, alpha=1.0 → keyword-only, 0.5 → equal-weight RRF
                val vec = if (alpha < 1.0) {
                    val emb = requireNotNull(embedder) {
                        "HybridSearchRequest with alpha < 1.0 requires an Embedder to be configured."
                    }
                    ensureEmbedderDim()
                    val qv = emb.embed(request.queryText).values.map { it.toFloat() }
                    searchByVector(qv, overFetch * 2, 0.0, ns)
                } else emptyList()
                val kw = if (alpha > 0.0) {
                    searchByFullText(request.queryText, overFetch * 2, 0.0, ns)
                } else emptyList()
                val results = when {
                    vec.isNotEmpty() && kw.isNotEmpty() -> {
                        reciprocalRankFusion(vec, kw, k = 60, limit = overFetch, vectorWeight = 1.0 - alpha, keywordWeight = alpha)
                    }
                    vec.isNotEmpty() -> vec.take(overFetch)
                    kw.isNotEmpty() -> kw.take(overFetch)
                    else -> searchByTrigram(request.queryText, overFetch, 0.2, ns)
                }
                results to ScoreMetric.HYBRID
            }
            else -> throw UnsupportedOperationException(
                "PGVectorStorage does not support search request of type ${request::class}"
            )
        }

        return scored
            .drop(request.offset)
            .take(request.limit)
            .map { it.toSearchResult(metric, ns) }
    }

    private suspend fun searchByVector(
        queryVector: List<Float>,
        limit: Int,
        similarityThreshold: Double,
        ns: String,
    ): List<ScoredChunk> {
        val pg = PGvector(queryVector.toFloatArray())
        return readOnly { conn ->
            val results = mutableListOf<ScoredChunk>()
            conn.prepareStatement(
                """
                SELECT c.chunk_id, c.document_id, c.namespace, c.chunk_index, c.chunk_count,
                       c.content AS chunk_content, d.metadata::text AS metadata,
                       1 - (c.embedding <=> ?) AS similarity
                FROM $CHUNKS_TABLE c
                JOIN $DOCUMENTS_TABLE d ON d.namespace = c.namespace AND d.id = c.document_id
                WHERE c.namespace = ? AND c.embedding IS NOT NULL
                  AND d.embedding_model = ? AND d.embedding_dim = ?
                ORDER BY c.embedding <=> ?
                LIMIT ?
                """.trimIndent()
            ).use { stmt ->
                stmt.setObject(1, pg)
                stmt.setString(2, ns)
                stmt.setString(3, embeddingModelId)
                stmt.setInt(4, vectorDimension)
                stmt.setObject(5, pg)
                stmt.setInt(6, limit)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val sim = rs.getDouble("similarity").coerceIn(0.0, 1.0)
                        if (sim >= similarityThreshold) results.add(readScoredChunk(rs, sim))
                    }
                }
            }
            results
        }
    }

    private suspend fun searchByFullText(
        query: String,
        limit: Int,
        similarityThreshold: Double,
        ns: String,
    ): List<ScoredChunk> = readOnly { conn ->
        val results = mutableListOf<ScoredChunk>()
        conn.prepareStatement(
            """
            SELECT c.chunk_id, c.document_id, c.namespace, c.chunk_index, c.chunk_count,
                   c.content AS chunk_content, d.metadata::text AS metadata,
                   ts_rank(c.tsv, plainto_tsquery('english', ?)) AS similarity
            FROM $CHUNKS_TABLE c
            JOIN $DOCUMENTS_TABLE d ON d.namespace = c.namespace AND d.id = c.document_id
            WHERE c.namespace = ? AND c.tsv @@ plainto_tsquery('english', ?)
              AND d.embedding_model = ? AND d.embedding_dim = ?
            ORDER BY similarity DESC
            LIMIT ?
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, query)
            stmt.setString(2, ns)
            stmt.setString(3, query)
            stmt.setString(4, embeddingModelId)
            stmt.setInt(5, vectorDimension)
            stmt.setInt(6, limit)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    val sim = rs.getDouble("similarity")
                    if (sim >= similarityThreshold) results.add(readScoredChunk(rs, sim))
                }
            }
        }
        results
    }

    private suspend fun searchByTrigram(
        query: String,
        limit: Int,
        similarityThreshold: Double,
        ns: String,
    ): List<ScoredChunk> = readOnly { conn ->
        val results = mutableListOf<ScoredChunk>()
        conn.prepareStatement(
            """
            SELECT c.chunk_id, c.document_id, c.namespace, c.chunk_index, c.chunk_count,
                   c.content AS chunk_content, d.metadata::text AS metadata,
                   similarity(c.content, ?) AS similarity
            FROM $CHUNKS_TABLE c
            JOIN $DOCUMENTS_TABLE d ON d.namespace = c.namespace AND d.id = c.document_id
            WHERE c.namespace = ? AND c.content % ?
              AND d.embedding_model = ? AND d.embedding_dim = ?
            ORDER BY similarity DESC
            LIMIT ?
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, query)
            stmt.setString(2, ns)
            stmt.setString(3, query)
            stmt.setString(4, embeddingModelId)
            stmt.setInt(5, vectorDimension)
            stmt.setInt(6, limit)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    val sim = rs.getDouble("similarity")
                    if (sim >= similarityThreshold) results.add(readScoredChunk(rs, sim))
                }
            }
        }
        results
    }

    /**
     * RRF fusion over two ranked chunk lists. Ties are broken by original rank order in list a.
     * Fused scores are standard RRF sums, not comparable to cosine/ts_rank values.
     */
    private fun reciprocalRankFusion(
        a: List<ScoredChunk>,
        b: List<ScoredChunk>,
        k: Int,
        limit: Int,
        vectorWeight: Double = 0.5,
        keywordWeight: Double = 0.5,
    ): List<ScoredChunk> {
        val scores = linkedMapOf<String, Double>()
        val chunks = linkedMapOf<String, ScoredChunk>()
        a.forEachIndexed { rank, sd ->
            scores.merge(sd.chunkId, vectorWeight / (k + rank + 1)) { x, y -> x + y }
            chunks.putIfAbsent(sd.chunkId, sd)
        }
        b.forEachIndexed { rank, sd ->
            scores.merge(sd.chunkId, keywordWeight / (k + rank + 1)) { x, y -> x + y }
            chunks.putIfAbsent(sd.chunkId, sd)
        }
        return scores.entries
            .sortedByDescending { it.value }
            .mapNotNull { (id, s) -> chunks[id]?.copy(score = s) }
            .take(limit)
    }

    // ==================== DELETE ====================

    override suspend fun delete(ids: List<String>, namespace: String?): List<String> =
        deleteDetailed(ids, namespace).successes

    /** Deletes documents by id (chunks are removed via ON DELETE CASCADE). */
    public suspend fun deleteDetailed(ids: List<String>, namespace: String?): BatchResult {
        if (ids.isEmpty()) return BatchResult(emptyList())
        ensureMigrated()
        val ns = resolveNamespace(namespace)
        val successes = mutableListOf<String>()
        val failures = linkedMapOf<String, String>()
        runCatching {
            inTx { conn ->
                conn.prepareStatement(
                    "DELETE FROM $DOCUMENTS_TABLE WHERE namespace = ? AND id = ?"
                ).use { stmt ->
                    for (id in ids) {
                        stmt.setString(1, ns)
                        stmt.setString(2, id)
                        stmt.addBatch()
                    }
                    stmt.executeBatch().forEachIndexed { i, code ->
                        val id = ids[i]
                        if (code > 0 || code == Statement.SUCCESS_NO_INFO) successes.add(id)
                        else failures[id] = "Record not found"
                    }
                }
            }
        }.onFailure { e ->
            logger.error(e) { "Transactional delete failed; rolling back" }
            ids.forEach { failures.putIfAbsent(it, "Transaction rolled back: ${e.message}") }
            successes.clear()
        }
        return BatchResult(successes, failures)
    }

    // ==================== LIFECYCLE ====================

    override fun close() {
        if (ownsDataSource && dataSource is AutoCloseable) {
            runCatching { (dataSource as AutoCloseable).close() }
                .onFailure { logger.warn(it) { "Failed to close owned DataSource" } }
        }
    }

    // ==================== HELPERS ====================

    private suspend fun computeEmbedding(content: String): List<Float>? {
        val emb = embedder ?: return null
        return emb.embed(content).values.map { it.toFloat() }
    }

    private fun parseDocumentRow(rs: ResultSet): TextDocument {
        val id = rs.getString("id")
        val content = rs.getString("content")
        val metadata = parseMetadataJson(rs.getString("metadata"))
        return PGTextDocument(
            content = content,
            id = id,
            metadata = metadata.entries.mapNotNull { (k, v) -> jsonElementToAny(v)?.let { k to it } }.toMap(),
        )
    }

    private fun parseMetadataJson(raw: String?): JsonObject {
        if (raw == null || raw == "null") return JsonObject(emptyMap())
        return try {
            json.parseToJsonElement(raw) as? JsonObject ?: JsonObject(emptyMap())
        } catch (e: Exception) {
            throw IllegalStateException("Failed to decode metadata for record", e)
        }
    }

    private data class ScoredChunk(
        val chunkId: String,
        val documentId: String,
        val namespace: String,
        val chunkIndex: Int,
        val chunkCount: Int,
        val chunkContent: String,
        val documentMetadata: JsonObject,
        val score: Double,
    ) {
        fun toSearchResult(metric: ScoreMetric, namespace: String): SearchResult<TextDocument> {
            val systemMeta = mapOf(
                KoogSystemMetadataKeys.DOCUMENT_ID to JsonPrimitive(documentId),
                KoogSystemMetadataKeys.CHUNK_ID to JsonPrimitive(chunkId),
                KoogSystemMetadataKeys.CHUNK_INDEX to JsonPrimitive(chunkIndex),
                KoogSystemMetadataKeys.CHUNK_COUNT to JsonPrimitive(chunkCount),
                KoogSystemMetadataKeys.NAMESPACE to JsonPrimitive(namespace),
            )
            val merged = JsonObject(documentMetadata + systemMeta)
            val chunkDoc = PGTextDocument(
                content = chunkContent,
                id = documentId,
                metadata = merged.entries.mapNotNull { (k, v) -> jsonElementToAny(v)?.let { k to it } }.toMap() +
                        systemMeta.mapValues { (_, v) -> jsonElementToAny(v)!! },
            )
            return SearchResult(
                document = chunkDoc,
                score = Score(score, metric),
                id = documentId,
                metadata = merged,
                namespace = namespace,
            )
        }
    }

    private fun readScoredChunk(rs: ResultSet, score: Double): ScoredChunk = ScoredChunk(
        chunkId = rs.getString("chunk_id"),
        documentId = rs.getString("document_id"),
        namespace = rs.getString("namespace"),
        chunkIndex = rs.getInt("chunk_index"),
        chunkCount = rs.getInt("chunk_count"),
        chunkContent = rs.getString("chunk_content"),
        documentMetadata = parseMetadataJson(rs.getString("metadata")),
        score = score,
    )

    private data class PGTextDocument(
        override val content: String,
        override val id: String?,
        override val metadata: Map<String, Any>,
    ) : TextDocument
}

// ==================== JSON METADATA HELPERS ====================

/** Converts a loose `Map<String, Any>` metadata payload to a structured JsonObject. */
private fun toJsonObject(map: Map<String, Any>): JsonObject =
    JsonObject(map.mapValues { (_, v) -> anyToJsonElement(v) })

private fun anyToJsonElement(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is Boolean -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is String -> JsonPrimitive(value)
    is Map<*, *> -> JsonObject(
        value.entries.associate { (k, v) -> k.toString() to anyToJsonElement(v) }
    )
    is Iterable<*> -> JsonArray(value.map { anyToJsonElement(it) })
    is Array<*> -> JsonArray(value.map { anyToJsonElement(it) })
    else -> JsonPrimitive(value.toString())
}

private fun jsonElementToAny(element: JsonElement): Any? = when (element) {
    is JsonNull -> null
    is JsonPrimitive -> jsonPrimitiveToAny(element)
    is JsonObject -> element.mapValues { (_, v) -> jsonElementToAny(v) }
    is JsonArray -> element.map { jsonElementToAny(it) }
}

private fun jsonPrimitiveToAny(p: JsonPrimitive): Any {
    if (p.isString) return p.content
    p.booleanOrNull?.let { return it }
    p.longOrNull?.let { return it }
    p.intOrNull?.let { return it }
    p.doubleOrNull?.let { return it }
    return p.content
}
