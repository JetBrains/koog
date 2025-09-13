# SQL Features Module

Provides SQL-based persistence providers for agent checkpoints using JetBrains Exposed ORM with support for multiple database engines.

## Features

- **Multi-Database Support**: PostgreSQL, MySQL, H2, SQLite
- **Exposed ORM Integration**: Type-safe SQL operations with Kotlin DSL
- **Connection Pooling**: HikariCP integration for production environments
- **TTL Support**: Automatic cleanup of expired checkpoints with configurable intervals
- **Database-Specific Optimizations**: Vendor-specific performance tuning

## Dependencies

- `org.jetbrains.exposed:*` - JetBrains Exposed ORM framework
- `com.zaxxer:HikariCP` - Connection pooling
- Database drivers: PostgreSQL, MySQL, H2, SQLite
- `org.testcontainers:postgresql` - Testing infrastructure

## Providers

### ExposedPersistencyStorageProvider
Base provider using Exposed ORM with configurable cleanup behavior.

### PostgresPersistencyStorageProvider  
Production-ready provider with JSONB support and HikariCP pooling.

### MySQLPersistencyStorageProvider
Enterprise provider with JSON column support (MySQL 5.7+).

### H2PersistencyStorageProvider
Perfect for testing and embedded applications.

### SQLitePersistencyStorageProvider
Zero-configuration provider for desktop and mobile applications.

All providers implement `AutoCloseable` for proper resource management and support configurable TTL cleanup.