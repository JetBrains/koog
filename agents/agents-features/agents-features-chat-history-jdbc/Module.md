# Module features-chat-history-jdbc

Provides pure JDBC chat history providers for persisting conversation history without ORM dependencies, supporting multiple database engines.

## Features

- **No ORM Dependency**: Uses plain `javax.sql.DataSource` and JDBC — no Exposed or other ORM required
- **Multi-Database Support**: PostgreSQL, MySQL, H2
- **TTL Support**: Automatic cleanup of expired conversations with configurable TTL
- **Java Interop**: `@JvmOverloads` constructors and `migrateBlocking()` for convenient Java usage

## Dependencies

- Database drivers (compileOnly): PostgreSQL, MySQL, H2
- `agents-features-memory` - Provides the `ChatHistoryProvider` interface
- `agents-features-chat-memory-sql` - Provides the `SQLChatHistoryProvider` base class

## Providers

### PostgresJdbcChatHistoryProvider
Production-ready provider for PostgreSQL using `INSERT ... ON CONFLICT DO UPDATE`.

### MySQLJdbcChatHistoryProvider
Provider for MySQL 5.7+ and MariaDB 10.2+ using `INSERT ... ON DUPLICATE KEY UPDATE`.

### H2JdbcChatHistoryProvider
In-memory and file-based options using `MERGE INTO ... KEY`, ideal for testing and embedded applications.

All providers extend `JdbcChatHistoryProvider` and include matching schema migrators for automatic table creation.
