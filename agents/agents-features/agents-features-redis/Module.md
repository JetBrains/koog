# Redis Features Module

Provides Redis-based persistence providers for agent checkpoints using Lettuce with Kotlin coroutines.

## Features

- **Single Connection Provider**: Basic Redis provider for simple scenarios
- **Connection Pool Provider**: Pooled Redis provider for high-concurrency applications  
- **TTL Support**: Automatic cleanup of expired checkpoints
- **Kotlin Coroutines**: Async/non-blocking operations using Lettuce coroutines API
- **Testcontainers Integration**: Comprehensive testing with real Redis instances

## Dependencies

- `io.lettuce:lettuce-core` - Redis client with coroutines support
- `org.apache.commons:commons-pool2` - Connection pooling
- `org.jetbrains.kotlinx:kotlinx-coroutines-reactive` - Reactive streams integration

## Providers

### JVMRedisPersistencyStorageProvider
Basic provider using a single Redis connection. Suitable for:
- Development environments
- Single-agent applications
- Low-concurrency scenarios

### PooledJVMRedisPersistencyStorageProvider  
Advanced provider using connection pooling. Suitable for:
- Production environments
- High-concurrency applications
- Multi-agent scenarios

Both providers implement `Closeable` for proper resource management.