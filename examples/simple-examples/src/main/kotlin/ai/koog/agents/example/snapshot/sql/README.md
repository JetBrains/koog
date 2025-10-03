# SQL Persistence Example

Demonstrates using SQL databases as persistence backends for agent checkpoints through JetBrains Exposed ORM.

## Quick Start

1. **Start databases** (PostgreSQL/MySQL only)
   ```bash
   docker-compose up -d
   ```

2. **Run the examples**
   ```bash
   # PostgreSQL example
   ./gradlew :examples:runExampleSQLPersistentAgent --args="postgres"
   
   # MySQL example
   ./gradlew :examples:runExampleSQLPersistentAgent --args="mysql"
   
   # H2 example (embedded database)
   ./gradlew :examples:runExampleSQLPersistentAgent --args="h2"
   ```

## What It Does

The example demonstrates:
- Saving checkpoints to different SQL databases
- Connection pooling with HikariCP (PostgreSQL/MySQL)
- TTL-based checkpoint expiration
- Multiple H2 database modes (in-memory, file-based, PostgreSQL-compatible)
- Schema auto-creation

## Database Schema

```sql
CREATE TABLE agent_checkpoints (
    persistence_id VARCHAR(255),
    checkpoint_id VARCHAR(255),
    created_at BIGINT,
    checkpoint_json TEXT,
    ttl_timestamp BIGINT,
    PRIMARY KEY (persistence_id, checkpoint_id)
);
```

## Providers

### PostgreSQL

```kotlin
PostgresPersistenceStorageProvider(
    persistenceId = "my-agent",
    database = Database.connect(
        url = "jdbc:postgresql://localhost:5432/agents",
        driver = "org.postgresql.Driver",
        user = "agent_user",
        password = "agent_pass"
    ),
    ttlSeconds = 3600  // Optional TTL
)
```

### MySQL

```kotlin
MySQLPersistenceStorageProvider(
    persistenceId = "my-agent",
    database = Database.connect(
        url = "jdbc:mysql://localhost:3306/agents",
        driver = "com.mysql.cj.jdbc.Driver",
        user = "agent_user",
        password = "agent_pass"
    ),
    ttlSeconds = 7200
)
```

### H2 (Embedded)

```kotlin
// In-memory (for testing)
H2PersistenceStorageProvider.inMemory(
    persistenceId = "test-agent",
    databaseName = "test_db"
)

// File-based (for persistence)
H2PersistenceStorageProvider.fileBased(
    persistenceId = "my-agent",
    filePath = "./data/h2/agent_checkpoints"
)
```
### SQLite (Embedded)


## Connection Pool Monitoring

For PostgreSQL/MySQL providers:

```kotlin
val stats = provider.getPoolStats()
println("Active connections: ${stats.activeConnections}")
println("Pool utilization: ${stats.utilizationPercent}%")
```

## Database UI

Access Adminer at http://localhost:8080:
- **PostgreSQL**: Server: `postgres`, Username: `agent_user`, Password: `agent_pass`
- **MySQL**: Server: `mysql`, Username: `agent_user`, Password: `agent_pass`

## Environment Variables

- `POSTGRES_URL` - Override PostgreSQL connection
- `MYSQL_URL` - Override MySQL connection

## Cleanup

```bash
docker-compose down -v  # Stop and remove data
rm -rf ./data          # Remove H2/SQLite files
```
