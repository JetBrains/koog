package ai.koog.rag.vector.database

import ai.koog.embeddings.base.Embedder
import com.pgvector.PGvector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Statement
import java.util.*

/**
 * PostgreSQL-specific implementation of [VectorDatabase] using pgvector extension
 * for efficient vector similarity search.
 *
 * This implementation stores records with their embeddings in PostgreSQL and uses
 * pgvector's cosine distance operator for similarity search.
 *
 * ## Features:
 * - Stores embeddings as pgvector VECTOR type for efficient similarity search
 * - Supports cosine similarity search using pgvector's `<=>` operator
 * - Stores metadata as JSONB for flexible filtering
 * - Automatic schema migration with pgvector extension creation
 * - Optional embedder for automatic embedding generation when records don't have embeddings
 * - Supports namespace isolation (table name override per operation)
 *
 * ## Requirements:
 * - PostgreSQL 11+ with pgvector extension installed
 * - The pgvector extension must be available (CREATE EXTENSION vector)
 *
 * @param jdbcUrl The JDBC URL for connecting to PostgreSQL
 * @param username The database username
 * @param password The database password
 * @param tableName Name of the default table to store records (default: "memory_records").
 *                  Can be overridden per operation using the `namespace` parameter.
 * @param vectorDimension The dimension of embedding vectors (default: 1536 for OpenAI embeddings)
 * @param embedder Optional embedder for generating embeddings when records don't have them.
 *                 If null and a record without embedding is added, an exception will be thrown.
 * @param json JSON serializer for metadata
 */
public class PGVectorDatabase(
    private val jdbcUrl: String,
    private val username: String,
    private val password: String,
    private val tableName: String = "memory_records",
    private val vectorDimension: Int = 1536,
    private val embedder: Embedder? = null,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : VectorDatabase {

    private fun getConnection(): Connection {
        val conn = DriverManager.getConnection(jdbcUrl, username, password)
        PGvector.addVectorType(conn)
        return conn
    }

    /**
     * Initializes the repository by running schema migrations for the default table.
     * This should be called before using the repository.
     */
    public suspend fun migrate() {
        migrate(tableName)
    }

    /**
     * Initializes the repository by running schema migrations for a specific namespace (table).
     * This should be called before using the repository with a custom namespace.
     *
     * @param namespace The namespace (table name) to migrate
     */
    public suspend fun migrate(namespace: String) {
        withContext(Dispatchers.IO) {
            getConnection().use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.executeUpdate("CREATE EXTENSION IF NOT EXISTS vector")

                    stmt.executeUpdate(
                        """
                        CREATE TABLE IF NOT EXISTS $namespace (
                            id VARCHAR(255) PRIMARY KEY,
                            content TEXT NOT NULL,
                            embedding vector($vectorDimension),
                            metadata JSONB DEFAULT '{}'::jsonb
                        )
                        """.trimIndent()
                    )

                    stmt.executeUpdate(
                        """
                        CREATE INDEX IF NOT EXISTS idx_${namespace}_embedding 
                        ON $namespace USING hnsw (embedding vector_cosine_ops)
                        """.trimIndent()
                    )

                    stmt.executeUpdate(
                        """
                        CREATE INDEX IF NOT EXISTS idx_${namespace}_metadata 
                        ON $namespace USING gin (metadata)
                        """.trimIndent()
                    )
                }
            }
        }
    }

    private suspend fun <T> dbQuery(block: (Connection) -> T): T =
        withContext(Dispatchers.IO) {
            getConnection().use { conn -> block(conn) }
        }

    private fun resolveTableName(namespace: String?): String {
        val name = namespace ?: tableName
        if (!name.matches(Regex("^[a-zA-Z0-9_]+$"))) {
            throw IllegalArgumentException("Invalid table name or namespace: $name. Only alphanumeric characters and underscores are allowed.")
        }
        return name
    }

    // ==================== CREATE/UPDATE OPERATIONS ====================

    private data class RecordData(
        val id: String,
        val content: String,
        val embedding: PGvector?,
        val metadataJson: String
    )

    override suspend fun add(records: List<Record>, namespace: String?): BatchOperationResult {
        if (records.isEmpty()) return BatchOperationResult(emptyList())

        val successIds = mutableListOf<String>()
        val failedIds = mutableMapOf<String, String>()
        val effectiveTableName = resolveTableName(namespace)

        val preparedData = mutableListOf<RecordData>()
        for (record in records) {
            val recordId = record.id ?: UUID.randomUUID().toString()
            try {
                val embedding = getOrComputeEmbedding(record)
                val pgvector = embedding?.let { PGvector(it.toFloatArray()) }
                val metadataJson = json.encodeToString(record.metadata)
                preparedData.add(RecordData(recordId, record.content, pgvector, metadataJson))
            } catch (e: Exception) {
                failedIds[recordId] = e.message ?: "Embedding computation failed"
            }
        }

        if (preparedData.isEmpty()) return BatchOperationResult(successIds, failedIds)

        dbQuery { conn ->
            val (withEmbedding, withoutEmbedding) = preparedData.partition { it.embedding != null }

            if (withEmbedding.isNotEmpty()) {
                val sql = """
                    INSERT INTO $effectiveTableName (id, content, embedding, metadata)
                    VALUES (?, ?, ?, ?::jsonb)
                    ON CONFLICT (id) DO UPDATE SET
                        content = EXCLUDED.content,
                        embedding = EXCLUDED.embedding,
                        metadata = EXCLUDED.metadata
                """.trimIndent()

                conn.prepareStatement(sql).use { stmt ->
                    for (data in withEmbedding) {
                        stmt.setString(1, data.id)
                        stmt.setString(2, data.content)
                        stmt.setObject(3, data.embedding)
                        stmt.setString(4, data.metadataJson)
                        stmt.addBatch()
                    }
                    stmt.executeBatch().forEachIndexed { index, result ->
                        val recordId = withEmbedding[index].id
                        if (result >= 0 || result == Statement.SUCCESS_NO_INFO) {
                            successIds.add(recordId)
                        } else {
                            failedIds[recordId] = "Batch update failed with code $result"
                        }
                    }
                }
            }

            if (withoutEmbedding.isNotEmpty()) {
                val sql = """
                    INSERT INTO $effectiveTableName (id, content, embedding, metadata)
                    VALUES (?, ?, NULL, ?::jsonb)
                    ON CONFLICT (id) DO UPDATE SET
                        content = EXCLUDED.content,
                        embedding = EXCLUDED.embedding,
                        metadata = EXCLUDED.metadata
                """.trimIndent()

                conn.prepareStatement(sql).use { stmt ->
                    for (data in withoutEmbedding) {
                        stmt.setString(1, data.id)
                        stmt.setString(2, data.content)
                        stmt.setString(3, data.metadataJson)
                        stmt.addBatch()
                    }
                    stmt.executeBatch().forEachIndexed { index, result ->
                        val recordId = withoutEmbedding[index].id
                        if (result >= 0 || result == Statement.SUCCESS_NO_INFO) {
                            successIds.add(recordId)
                        } else {
                            failedIds[recordId] = "Batch update failed with code $result"
                        }
                    }
                }
            }
        }

        return BatchOperationResult(successIds, failedIds)
    }

    private suspend fun getOrComputeEmbedding(record: Record): List<Float>? {
        if (record is Record.Embedded && record.embedding.isNotEmpty()) {
            return record.embedding
        }
        if (embedder != null) {
            return embedder.embed(record.content).values.map { it.toFloat() } // TODO: Vector should contain float array
        }
        return null
    }

    override suspend fun update(records: List<Record>, namespace: String?): BatchOperationResult {
        if (records.isEmpty()) return BatchOperationResult(emptyList())

        val successIds = mutableListOf<String>()
        val failedIds = mutableMapOf<String, String>()
        val effectiveTableName = resolveTableName(namespace)

        val preparedData = mutableListOf<RecordData>()
        for (record in records) {
            val recordId = record.id
            if (recordId == null) {
                failedIds[UUID.randomUUID().toString()] = "Record ID is required for update"
                continue
            }

            try {
                val embedding = getOrComputeEmbedding(record)
                val pgvector = embedding?.let { PGvector(it.toFloatArray()) }
                val metadataJson = json.encodeToString(record.metadata)
                preparedData.add(RecordData(recordId, record.content, pgvector, metadataJson))
            } catch (e: Exception) {
                failedIds[recordId] = e.message ?: "Embedding computation failed"
            }
        }

        if (preparedData.isEmpty()) return BatchOperationResult(successIds, failedIds)

        dbQuery { conn ->
            val (withEmbedding, withoutEmbedding) = preparedData.partition { it.embedding != null }

            if (withEmbedding.isNotEmpty()) {
                val sql = """
                    UPDATE $effectiveTableName 
                    SET content = ?, 
                        embedding = ?,
                        metadata = ?::jsonb
                    WHERE id = ?
                """.trimIndent()

                conn.prepareStatement(sql).use { stmt ->
                    for (data in withEmbedding) {
                        stmt.setString(1, data.content)
                        stmt.setObject(2, data.embedding)
                        stmt.setString(3, data.metadataJson)
                        stmt.setString(4, data.id)
                        stmt.addBatch()
                    }
                    stmt.executeBatch().forEachIndexed { index, result ->
                        val recordId = withEmbedding[index].id
                        if (result > 0 || result == Statement.SUCCESS_NO_INFO) {
                            successIds.add(recordId)
                        } else {
                            failedIds[recordId] = "Record not found or update failed"
                        }
                    }
                }
            }

            if (withoutEmbedding.isNotEmpty()) {
                val sql = """
                    UPDATE $effectiveTableName 
                    SET content = ?, 
                        embedding = NULL,
                        metadata = ?::jsonb
                    WHERE id = ?
                """.trimIndent()

                conn.prepareStatement(sql).use { stmt ->
                    for (data in withoutEmbedding) {
                        stmt.setString(1, data.content)
                        stmt.setString(2, data.metadataJson)
                        stmt.setString(3, data.id)
                        stmt.addBatch()
                    }
                    stmt.executeBatch().forEachIndexed { index, result ->
                        val recordId = withoutEmbedding[index].id
                        if (result > 0 || result == Statement.SUCCESS_NO_INFO) {
                            successIds.add(recordId)
                        } else {
                            failedIds[recordId] = "Record not found or update failed"
                        }
                    }
                }
            }
        }

        return BatchOperationResult(successIds, failedIds)
    }

    // ==================== READ OPERATIONS ====================

    override suspend fun getAll(ids: List<String>, namespace: String?): List<Record> {
        if (ids.isEmpty()) return emptyList()

        val effectiveTableName = resolveTableName(namespace)
        val placeholders = ids.joinToString(",") { "?" }
        return dbQuery { conn ->
            val result = mutableListOf<Record>()
            conn.prepareStatement(
                "SELECT id, content, embedding, metadata::text FROM $effectiveTableName WHERE id IN ($placeholders)"
            ).use { stmt ->
                ids.forEachIndexed { index, id ->
                    stmt.setString(index + 1, id)
                }
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        result.add(parseRecord(rs))
                    }
                }
            }
            result
        }
    }

    // ==================== SEARCH OPERATIONS ====================

    override suspend fun search(request: SearchRequest, namespace: String?): List<SearchResult> {
        val effectiveTableName = resolveTableName(namespace)
        return when (request) {
            is VectorSearchRequest -> searchByVectorInternal(
                request.queryVector,
                request.limit,
                request.similarityThreshold,
                request.filterExpression,
                effectiveTableName
            )

            is SimilaritySearchRequest -> {
                val currentEmbedder = embedder
                    ?: throw VectorDatabaseException(
                        "SimilaritySearchRequest requires an embedder to be configured. " +
                            "Either provide an embedder when creating PGVectorDatabase " +
                            "or use VectorSearchRequest with pre-computed embeddings instead."
                    )
                val queryVector = currentEmbedder.embed(request.query).values.map { it.toFloat() }
                searchByVectorInternal(
                    queryVector,
                    request.limit,
                    request.similarityThreshold,
                    request.filterExpression,
                    effectiveTableName
                )
            }

            is KeywordSearchRequest -> searchByKeyword(
                request.query,
                request.limit,
                request.filterExpression,
                effectiveTableName
            )

            is HybridSearchRequest -> {
                val queryVector = request.queryVector ?: embedder?.let { emb ->
                    emb.embed(request.query).values.map { it.toFloat() }
                }
                if (queryVector != null) {
                    val vectorResults = searchByVectorInternal(
                        queryVector,
                        request.limit * 2,
                        request.similarityThreshold,
                        request.filterExpression,
                        effectiveTableName
                    )
                    val keywordResults = searchByKeyword(
                        request.query,
                        request.limit * 2,
                        request.filterExpression,
                        effectiveTableName
                    )
                    combineHybridResults(vectorResults, keywordResults, request.alpha, request.limit)
                } else {
                    searchByKeyword(request.query, request.limit, request.filterExpression, effectiveTableName)
                }
            }
        }
    }

    private fun Expression.toSqlWhereClause(): String = when (type) {
        ExpressionType.EQ -> "${(left as Key).name} = ${sqlValue(right)}"
        ExpressionType.NE -> "${(left as Key).name} != ${sqlValue(right)}"
        ExpressionType.GT -> "${(left as Key).name} > ${sqlValue(right)}"
        ExpressionType.GTE -> "${(left as Key).name} >= ${sqlValue(right)}"
        ExpressionType.LT -> "${(left as Key).name} < ${sqlValue(right)}"
        ExpressionType.LTE -> "${(left as Key).name} <= ${sqlValue(right)}"
        ExpressionType.IN -> "${(left as Key).name} IN (${sqlListValue(right)})"
        ExpressionType.NIN -> "${(left as Key).name} NOT IN (${sqlListValue(right)})"
        ExpressionType.AND -> "(${(left as Expression).toSqlWhereClause()} AND ${(right as Expression).toSqlWhereClause()})"
        ExpressionType.OR -> "(${(left as Expression).toSqlWhereClause()} OR ${(right as Expression).toSqlWhereClause()})"
        ExpressionType.NOT -> "NOT (${(left as Expression).toSqlWhereClause()})"
        ExpressionType.ISNULL -> "${(left as Key).name} IS NULL"
        ExpressionType.ISNOTNULL -> "${(left as Key).name} IS NOT NULL"
    }

    private fun sqlValue(value: Any?): String {
        val v = (value as Value).value
        return when (v) {
            is String -> "'${v.replace("'", "''")}'"
            is Number -> v.toString()
            is Boolean -> v.toString()
            else -> "'$v'"
        }
    }

    private fun sqlListValue(value: Any?): String {
        val list = (value as Value).value as List<*>
        return list.joinToString(", ") { item ->
            when (item) {
                is String -> "'${item.replace("'", "''")}'"
                else -> item.toString()
            }
        }
    }

    private suspend fun searchByVectorInternal(
        queryVector: List<Float>,
        limit: Int,
        similarityThreshold: Double,
        filterExpression: Expression?,
        effectiveTableName: String
    ): List<SearchResult> {
        val queryPgVector = PGvector(queryVector.toFloatArray())
        val filterClause = filterExpression?.let { " AND (${it.toSqlWhereClause()})" } ?: ""

        return dbQuery { conn ->
            val results = mutableListOf<SearchResult>()
            conn.prepareStatement(
                """
                SELECT id, content, embedding, metadata::text,
                       1 - (embedding <=> ?) as similarity
                FROM $effectiveTableName
                WHERE embedding IS NOT NULL $filterClause
                ORDER BY embedding <=> ?
                LIMIT ?
                """.trimIndent()
            ).use { stmt ->
                stmt.setObject(1, queryPgVector)
                stmt.setObject(2, queryPgVector)
                stmt.setInt(3, limit)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val similarity = rs.getDouble("similarity")
                        if (similarity >= similarityThreshold) {
                            val record = parseRecord(rs)
                            results.add(SearchResult(record, similarity))
                        }
                    }
                }
            }
            results
        }
    }

    private suspend fun searchByKeyword(
        query: String,
        limit: Int,
        filterExpression: Expression?,
        effectiveTableName: String
    ): List<SearchResult> {
        val filterClause = filterExpression?.let { " AND (${it.toSqlWhereClause()})" } ?: ""
        val searchPattern = "%${query.lowercase()}%"

        return dbQuery { conn ->
            val results = mutableListOf<SearchResult>()
            conn.prepareStatement(
                """
                SELECT id, content, embedding, metadata::text,
                       CASE WHEN LOWER(content) LIKE ? THEN 1.0 ELSE 0.0 END as similarity
                FROM $effectiveTableName
                WHERE LOWER(content) LIKE ? $filterClause
                ORDER BY similarity DESC
                LIMIT ?
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, searchPattern)
                stmt.setString(2, searchPattern)
                stmt.setInt(3, limit)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val record = parseRecord(rs)
                        val similarity = rs.getDouble("similarity")
                        results.add(SearchResult(record, similarity))
                    }
                }
            }
            results
        }
    }

    private fun combineHybridResults(
        vectorResults: List<SearchResult>,
        keywordResults: List<SearchResult>,
        alpha: Double,
        limit: Int
    ): List<SearchResult> {
        val vectorScores = vectorResults.associateBy({ it.record.id }, { it.similarity })
        val keywordScores = keywordResults.associateBy({ it.record.id }, { it.similarity })

        val allRecords = (vectorResults.map { it.record } + keywordResults.map { it.record })
            .distinctBy { it.id }

        return allRecords.map { record ->
            val vectorScore = vectorScores[record.id] ?: 0.0
            val keywordScore = keywordScores[record.id] ?: 0.0
            val combinedScore = (1 - alpha) * vectorScore + alpha * keywordScore
            SearchResult(record, combinedScore)
        }
            .sortedByDescending { it.similarity }
            .take(limit)
    }

    // ==================== DELETE OPERATIONS ====================

    override suspend fun deleteAll(ids: List<String>, namespace: String?): BatchOperationResult {
        if (ids.isEmpty()) return BatchOperationResult(emptyList())

        val effectiveTableName = resolveTableName(namespace)
        val successIds = mutableListOf<String>()
        val failedIds = mutableMapOf<String, String>()

        dbQuery { conn ->
            conn.prepareStatement(
                "DELETE FROM $effectiveTableName WHERE id = ?"
            ).use { stmt ->
                for (id in ids) {
                    stmt.setString(1, id)
                    stmt.addBatch()
                }
                stmt.executeBatch().forEachIndexed { index, result ->
                    val id = ids[index]
                    if (result > 0 || result == Statement.SUCCESS_NO_INFO) {
                        successIds.add(id)
                    } else {
                        failedIds[id] = "Record not found"
                    }
                }
            }
        }

        return BatchOperationResult(successIds, failedIds)
    }

    override suspend fun deleteByFilter(filterExpression: Expression, namespace: String?): Int {
        val effectiveTableName = resolveTableName(namespace)
        val whereClause = filterExpression.toSqlWhereClause()
        return dbQuery { conn ->
            conn.prepareStatement(
                "DELETE FROM $effectiveTableName WHERE $whereClause"
            ).use { stmt ->
                stmt.executeUpdate()
            }
        }
    }

    // ==================== HELPER METHODS ====================

    private fun parseRecord(rs: ResultSet): Record {
        val id = rs.getString("id")
        val content = rs.getString("content")
        val pgvector = rs.getObject("embedding") as PGvector?
        val metadataStr = rs.getString("metadata")

        val embedding = pgvector?.toArray()?.toList()
        val metadata: Map<String, JsonPrimitive> = if (metadataStr != null && metadataStr != "null") {
            try {
                json.decodeFromString(metadataStr)
            } catch (e: Exception) {
                throw VectorDatabaseException("Failed to decode metadata for record $id", e)
            }
        } else {
            emptyMap()
        }

        return if (embedding != null) {
            Record.Embedded(
                id = id,
                content = content,
                embedding = embedding,
                metadata = metadata
            )
        } else {
            Record.Plain(
                id = id,
                content = content,
                metadata = metadata
            )
        }
    }
}
