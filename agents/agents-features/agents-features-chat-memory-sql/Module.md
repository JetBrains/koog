# Module features-chat-memory-sql

Provides SQL-based chat history providers for persisting conversation history using JetBrains Exposed ORM with support for multiple database engines.

## Features

- **Multi-Database Support**: PostgreSQL, MySQL, H2
- **Exposed ORM Integration**: Type-safe SQL operations with Kotlin DSL
- **TTL Support**: Automatic cleanup of expired conversations with configurable TTL
- **Independent Installation**: Can be used without the full `agents-features-sql` module

## Dependencies

- `org.jetbrains.exposed:*` - JetBrains Exposed ORM framework
- Database drivers: PostgreSQL, MySQL, H2
- `agents-features-memory` - Provides the `ChatHistoryProvider` interface

## Providers

### PostgresChatHistoryProvider
Production-ready provider for PostgreSQL databases with builder API.

### MySQLChatHistoryProvider
Provider for MySQL 5.7+ and MariaDB 10.2+ with native JSON column support.

### H2ChatHistoryProvider
In-memory and file-based options, perfect for testing and embedded applications.

All providers extend `ExposedChatHistoryProvider` and support configurable TTL for automatic conversation expiration.
