package ai.koog.agents.snapshot.strategy

import ai.koog.agents.snapshot.feature.AgentCheckpointData
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Built-in coordination strategy implementations for common use cases.
 * Users can use these directly or implement their own CoordinationStrategy.
 */
public object CoordinationStrategies {
    
    private val logger = KotlinLogging.logger { }
    
    /**
     * Use a single provider for all operations.
     * Simple and reliable for basic use cases.
     */
    public class Single(public val provider: ProviderId) : CoordinationStrategy {
        
        override suspend fun saveCheckpoint(checkpoint: AgentCheckpointData, registry: ProviderRegistry) {
            registry.get(provider).saveCheckpoint(checkpoint)
        }
        
        override suspend fun getCheckpoints(registry: ProviderRegistry): List<AgentCheckpointData> {
            return registry.get(provider).getCheckpoints()
        }
        
        override suspend fun getLatestCheckpoint(registry: ProviderRegistry): AgentCheckpointData? {
            return registry.get(provider).getLatestCheckpoint()
        }
    }
    
    /**
     * Write to all specified providers. Fails if any provider fails.
     * Useful for scenarios requiring guaranteed consistency across all providers.
     */
    public class WriteToAll(
        private val providers: List<ProviderId>,
        private val readFrom: ProviderId = providers.first()
    ) : CoordinationStrategy {
        
        override suspend fun saveCheckpoint(checkpoint: AgentCheckpointData, registry: ProviderRegistry) {
            val exceptions = mutableListOf<Exception>()
            
            for (providerId in providers) {
                try {
                    registry.get(providerId).saveCheckpoint(checkpoint)
                    logger.debug { "Successfully wrote checkpoint to ${providerId.value}" }
                } catch (e: Exception) {
                    exceptions.add(e)
                    logger.error(e) { "Failed to write checkpoint to ${providerId.value}" }
                    throw IllegalStateException("Write failed to ${providerId.value}", e)
                }
            }
            
            logger.debug { "Checkpoint written to all ${providers.size} providers" }
        }
        
        override suspend fun getCheckpoints(registry: ProviderRegistry): List<AgentCheckpointData> {
            return registry.get(readFrom).getCheckpoints()
        }
        
        override suspend fun getLatestCheckpoint(registry: ProviderRegistry): AgentCheckpointData? {
            return registry.get(readFrom).getLatestCheckpoint()
        }
    }
    
    /**
     * Write to all specified providers. Succeeds if at least one provider succeeds.
     * Provides high availability with best-effort consistency.
     */
    public class WriteAllBestEffort(
        private val providers: List<ProviderId>,
        private val readFrom: ProviderId = providers.first()
    ) : CoordinationStrategy {
        
        override suspend fun saveCheckpoint(checkpoint: AgentCheckpointData, registry: ProviderRegistry) {
            val exceptions = mutableListOf<Exception>()
            var successCount = 0
            
            for (providerId in providers) {
                try {
                    registry.get(providerId).saveCheckpoint(checkpoint)
                    successCount++
                    logger.debug { "Successfully wrote checkpoint to ${providerId.value}" }
                } catch (e: Exception) {
                    exceptions.add(e)
                    logger.warn(e) { "Failed to write checkpoint to ${providerId.value}" }
                }
            }
            
            if (successCount == 0) {
                throw IllegalStateException(
                    "All ${providers.size} providers failed to save checkpoint",
                    exceptions.firstOrNull()
                )
            }
            
            logger.debug { "Checkpoint written to $successCount/${providers.size} providers" }
        }
        
        override suspend fun getCheckpoints(registry: ProviderRegistry): List<AgentCheckpointData> {
            return registry.get(readFrom).getCheckpoints()
        }
        
        override suspend fun getLatestCheckpoint(registry: ProviderRegistry): AgentCheckpointData? {
            return registry.get(readFrom).getLatestCheckpoint()
        }
    }
    
    /**
     * Write to primary provider, then backup providers. Succeeds if primary succeeds.
     * Provides durability with backup redundancy.
     */
    public class WriteWithBackup(
        private val primary: ProviderId,
        private val backups: List<ProviderId> = emptyList()
    ) : CoordinationStrategy {
        
        override suspend fun saveCheckpoint(checkpoint: AgentCheckpointData, registry: ProviderRegistry) {
            // Write to primary first
            try {
                registry.get(primary).saveCheckpoint(checkpoint)
                logger.debug { "Successfully wrote checkpoint to primary provider ${primary.value}" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to write checkpoint to primary provider ${primary.value}" }
                throw e
            }
            
            // Write to backups (best effort)
            for (backupId in backups) {
                try {
                    registry.get(backupId).saveCheckpoint(checkpoint)
                    logger.debug { "Successfully wrote checkpoint to backup provider ${backupId.value}" }
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to write checkpoint to backup provider ${backupId.value}" }
                    // Continue with other backups
                }
            }
        }
        
        override suspend fun getCheckpoints(registry: ProviderRegistry): List<AgentCheckpointData> {
            return registry.get(primary).getCheckpoints()
        }
        
        override suspend fun getLatestCheckpoint(registry: ProviderRegistry): AgentCheckpointData? {
            return registry.get(primary).getLatestCheckpoint()
        }
    }
    
    /**
     * Try providers in the specified order for both reads and writes.
     * Provides failover capability with ordered preference.
     */
    public class Prioritized(private val providers: List<ProviderId>) : CoordinationStrategy {
        
        override suspend fun saveCheckpoint(checkpoint: AgentCheckpointData, registry: ProviderRegistry) {
            var lastException: Exception? = null
            
            for (providerId in providers) {
                try {
                    registry.get(providerId).saveCheckpoint(checkpoint)
                    logger.debug { "Successfully wrote checkpoint to provider ${providerId.value}" }
                    return
                } catch (e: Exception) {
                    lastException = e
                    logger.warn(e) { "Failed to write checkpoint to provider ${providerId.value}" }
                }
            }
            
            throw IllegalStateException(
                "All ${providers.size} providers failed to save checkpoint",
                lastException
            )
        }
        
        override suspend fun getCheckpoints(registry: ProviderRegistry): List<AgentCheckpointData> {
            return readPrioritizedNonNull(providers) { registry.get(it).getCheckpoints() }
        }
        
        override suspend fun getLatestCheckpoint(registry: ProviderRegistry): AgentCheckpointData? {
            return readPrioritizedNullable(providers) { registry.get(it).getLatestCheckpoint() }
        }
        
        private suspend fun <T> readPrioritizedNullable(
            providerIds: List<ProviderId>,
            operation: suspend (ProviderId) -> T?
        ): T? {
            for (providerId in providerIds) {
                try {
                    val result = operation(providerId)
                    if (result != null) {
                        logger.debug { "Successfully read non-null result from provider ${providerId.value}" }
                        return result
                    } else {
                        logger.debug { "Provider ${providerId.value} returned null, trying next provider" }
                    }
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to read from provider ${providerId.value}" }
                }
            }
            
            logger.debug { "All ${providerIds.size} providers returned null or failed" }
            return null
        }
        
        private suspend fun <T> readPrioritizedNonNull(
            providerIds: List<ProviderId>,
            operation: suspend (ProviderId) -> T
        ): T {
            var lastException: Exception? = null
            
            for (providerId in providerIds) {
                try {
                    val result = operation(providerId)
                    logger.debug { "Successfully read from provider ${providerId.value}" }
                    return result
                } catch (e: Exception) {
                    lastException = e
                    logger.warn(e) { "Failed to read from provider ${providerId.value}" }
                }
            }
            
            throw IllegalStateException(
                "All ${providerIds.size} providers failed to read data",
                lastException
            )
        }
    }
    
    /**
     * Try fastest provider first, fallback to others if needed.
     * Optimizes for performance with reliability fallbacks.
     */
    public class FastestFirst(
        private val fast: ProviderId,
        private val fallbacks: List<ProviderId>
    ) : CoordinationStrategy {
        
        private val prioritized = Prioritized(listOf(fast) + fallbacks)
        
        override suspend fun saveCheckpoint(checkpoint: AgentCheckpointData, registry: ProviderRegistry) {
            prioritized.saveCheckpoint(checkpoint, registry)
        }
        
        override suspend fun getCheckpoints(registry: ProviderRegistry): List<AgentCheckpointData> {
            return prioritized.getCheckpoints(registry)
        }
        
        override suspend fun getLatestCheckpoint(registry: ProviderRegistry): AgentCheckpointData? {
            return prioritized.getLatestCheckpoint(registry)
        }
    }
}