package ai.koog.agents.features.sql.providers

/**
 * Represents an interface to handle database schema migrations using Exposed SQL library.
 *
 * This interface is designed to be implemented by classes that need to handle schema migrations
 * for databases, ensuring compatibility and flexibility in schema evolution.
 *
 * ExposedSQLMigrator provides an abstraction for executing migrations asynchronously, allowing
 * for better control and management of database schema changes as part of the application's lifecycle.
 *
 * The primary function to be implemented is [migrate], which encapsulates the details of performing
 * the required schema update or adjustments based on the application's requirements.
 */
public interface SQLPersistenceSchemaMigrator {
    /**
     * Performs a database schema migration asynchronously.
     */
    public suspend fun migrate()
}

/**
 * A no-operation implementation of the [SQLPersistenceSchemaMigrator] interface.
 *
 * This class is designed to be used in scenarios where schema migrations are not required
 * or are managed externally. It provides an empty implementation of the [migrate] method,
 * effectively acting as a placeholder.
 *
 * Use this class if you need to satisfy the dependency on [SQLPersistenceSchemaMigrator] but do not
 * want to perform any migration actions.
 */
public object NoOpSQLPersistenceSchemaMigrator : SQLPersistenceSchemaMigrator {
    override suspend fun migrate() { }
}
