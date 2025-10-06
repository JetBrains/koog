package ai.koog.spring.sandwich.checkpoints

import ai.koog.agents.features.sql.providers.PostgresPersistenceStorageProvider
import ai.koog.agents.snapshot.providers.PersistenceStorageProvider
import org.jetbrains.exposed.sql.Database

fun createPostgresStorage(): PersistenceStorageProvider {
    val db = Database.connect(
        url = "https://0.0.0.0:5432",
        driver = "org.postgresql.Driver",
        user = "admin",
        password = "1234567890"
    )

    return PostgresPersistenceStorageProvider(
        database = db,
        tableName = "agent_checkpoints_test"
    )
}