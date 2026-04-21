# Module features-persistence-jdbc

Provides pure JDBC persistence storage providers for persisting agent checkpoints without ORM dependencies, supporting multiple database engines.

## Features

- **No ORM Dependency**: Uses plain `javax.sql.DataSource` and JDBC — no Exposed or other ORM required
- **Multi-Database Support**: PostgreSQL, MySQL, H2
- **TTL Support**: Automatic cleanup of expired checkpoints with configurable TTL
- **Java Interop**: `@JvmOverloads` constructors and blocking methods (`migrateBlocking()`, `saveCheckpointBlocking()`, etc.) for convenient Java usage

## Dependencies

- Database drivers (compileOnly): PostgreSQL, MySQL, H2
- `agents-features-snapshot` - Provides the `PersistenceStorageProvider` interface
- `agents-features-sql` - Provides the `SQLPersistenceStorageProvider` base class

## Providers

### PostgresJdbcPersistenceStorageProvider
Production-ready provider for PostgreSQL using `INSERT ... ON CONFLICT DO UPDATE`.

### MySQLJdbcPersistenceStorageProvider
Provider for MySQL 5.7+ and MariaDB 10.2+ using `INSERT ... ON DUPLICATE KEY UPDATE`.

### H2JdbcPersistenceStorageProvider
In-memory and file-based options using `MERGE INTO ... KEY`, ideal for testing and embedded applications.

All providers extend `JdbcPersistenceStorageProvider` and include matching schema migrators for automatic table creation.
