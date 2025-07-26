package ai.koog.agents.snapshot.strategy

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.context.DetachedPromptExecutorAPI
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.extension.replaceHistoryWithTLDR
import ai.koog.agents.core.tools.reflect.getPreferredClassDescriptionAnnotation
import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.prompts.SnapshotPrompts
import ai.koog.agents.snapshot.providers.NoPersistencyStorageProvider
import ai.koog.agents.snapshot.providers.PersistencyStorageProvider
import ai.koog.prompt.structure.json.JsonSchemaGenerator
import ai.koog.prompt.structure.json.JsonStructuredData
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable

/**
 * A persistence provider that delegates operations to different providers based on a strategy.
 *
 * This class implements the [PersistencyStorageProvider] interface while internally using
 * a [PersistencyStrategy] to determine which actual provider to use for each operation.
 *
 * @property strategy The strategy that determines provider selection
 * @property context The agent context used for strategy decisions
 */
@OptIn(InternalAgentsApi::class)
public open class PersistencyStrategyProvider(
    protected val strategy: PersistencyStrategy,
    protected val context: AIAgentContextBase
) : PersistencyStorageProvider {

    private companion object {
        private val logger = KotlinLogging.logger { }
        private val noOpProvider = NoPersistencyStorageProvider()
    }
    
    // Cache the selected provider to ensure all operations use the same provider
    private var cachedProvider: PersistencyStorageProvider? = null

    override suspend fun saveCheckpoint(agentCheckpointData: AgentCheckpointData) {
        val provider = getSelectedProvider()
        provider.saveCheckpoint(agentCheckpointData)
    }

    override suspend fun getCheckpoints(): List<AgentCheckpointData> {
        val provider = getSelectedProvider()
        return provider.getCheckpoints()
    }

    override suspend fun getLatestCheckpoint(): AgentCheckpointData? {
        val provider = getSelectedProvider()
        return provider.getLatestCheckpoint()
    }


    /**
     * Gets the selected provider for this agent, using caching to ensure consistency.
     * All operations for an agent will use the same provider to prevent data corruption.
     */
    private suspend fun getSelectedProvider(): PersistencyStorageProvider {
        // Return cached provider if already selected
        cachedProvider?.let { return it }
        
        // Select provider based on strategy
        val provider = when (strategy) {
            is PersistencyStrategy.Single -> strategy.provider

            is PersistencyStrategy.None -> noOpProvider

            is PersistencyStrategy.Dynamic -> {
                val agentContext = PersistencyStrategy.Dynamic.AgentContext(
                    agentContext = context
                )
                val providerName = strategy.selector(agentContext)
                strategy.providers[providerName]
                    ?: throw IllegalStateException("Provider '$providerName' not found in Dynamic strategy")
            }

            is PersistencyStrategy.MultiProvider -> MultiProviderHandler(strategy)

            is PersistencyStrategy.AutoSelectForTask -> selectWithLLM(strategy)
        }
        
        // Cache the selected provider to ensure all operations use the same provider
        cachedProvider = provider
        return provider
    }

    /**
     * Data class for LLM provider selection response.
     */
    @Serializable
    private data class SelectedProvider(
        val providerName: String,
        val reasoning: String? = null
    )

    /**
     * Selects a provider using LLM based on the task description and provider annotations.
     * Includes retry mechanism and fallback logic for robustness.
     */
    @OptIn(DetachedPromptExecutorAPI::class)
    private suspend fun selectWithLLM(
        strategy: PersistencyStrategy.AutoSelectForTask
    ): PersistencyStorageProvider {
        // Build provider descriptions for LLM using annotations
        val providerDescriptions = strategy.providers.entries.joinToString("\n") { (name, provider) ->
            val description = provider::class.getPreferredClassDescriptionAnnotation()?.description 
                ?: "${provider::class.simpleName} persistence provider"
            "- $name: $description"
        }


        // Determine fallback provider (first available, preferring "durable" providers)
        val fallbackProvider = strategy.providers.entries.minByOrNull { (name, _) ->
            when {
                name.lowercase().contains("durable") -> 0
                name.lowercase().contains("postgres") -> 1
                name.lowercase().contains("sql") -> 2
                else -> 3
            }
        }?.value

        var lastException: Exception? = null

        // Attempt LLM selection with retries
        for (attempt in 0 until strategy.maxRetries) {
            try {
                val selected = context.llm.writeSession {
                    val initialPrompt = prompt

                    replaceHistoryWithTLDR()

                    updatePrompt {
                        user {
                            SnapshotPrompts.selectPersistencyProvider(
                                taskDescription = strategy.taskDescription,
                                providerDescriptions = providerDescriptions,
                                availableProviderNames = strategy.providers.keys.joinToString(", ")
                            )
                        }
                    }

                    val selectedProvider = this.requestLLMStructured(
                        structure = JsonStructuredData.createJsonStructure<SelectedProvider>(
                            schemaFormat = JsonSchemaGenerator.SchemaFormat.JsonSchema,
                            examples = strategy.providers.keys.take(2).map { providerName ->
                                SelectedProvider(
                                    providerName,
                                    "Selected $providerName based on operation requirements"
                                )
                            }
                        ),
                        retries = 1, // Single retry per attempt to avoid nested retries
                    ).getOrThrow()

                    prompt = initialPrompt

                    selectedProvider.structure
                }

                // Validate LLM selection
                val selectedProvider = strategy.providers[selected.providerName]
                if (selectedProvider != null) {
                    logger.debug {
                        "LLM selected provider '${selected.providerName}' for task '${strategy.taskDescription}' on attempt ${attempt + 1}" +
                        selected.reasoning?.let { " (reasoning: $it)" }.orEmpty()
                    }
                    return selectedProvider
                } else {
                    val availableProviders = strategy.providers.keys.joinToString(", ")
                    throw IllegalStateException(
                        "LLM selected unknown provider '${selected.providerName}'. Available providers: $availableProviders"
                    )
                }

            } catch (e: Exception) {
                lastException = e
                logger.warn {
                    "LLM provider selection failed on attempt ${attempt + 1}/${strategy.maxRetries}: ${e.message}"
                }

                // Continue to next iteration for retry
            }
        }

        // All LLM attempts failed, use fallback strategy
        if (fallbackProvider != null) {
            logger.warn {
                "LLM provider selection failed after ${strategy.maxRetries} attempts, falling back to ${fallbackProvider::class.simpleName}"
            }
            return fallbackProvider
        } else {
            // No fallback available, throw the last exception
            throw IllegalStateException(
                "LLM provider selection failed after ${strategy.maxRetries} attempts and no fallback provider available",
                lastException
            )
        }
    }

    /**
     * Handler for MultiProvider strategy that coordinates operations across multiple providers.
     */
    private class MultiProviderHandler(
        private val strategy: PersistencyStrategy.MultiProvider
    ) : PersistencyStorageProvider {

        private companion object {
            private val logger = KotlinLogging.logger { }
        }

        override suspend fun saveCheckpoint(agentCheckpointData: AgentCheckpointData) {
            when (val writeStrategy = strategy.writeStrategy) {
                is PersistencyStrategy.MultiProvider.WriteStrategy.WriteToAll -> {
                    writeToAll(agentCheckpointData, writeStrategy.providerNames, failOnAnyError = true)
                }
                
                is PersistencyStrategy.MultiProvider.WriteStrategy.WriteToAllBestEffort -> {
                    writeToAll(agentCheckpointData, writeStrategy.providerNames, failOnAnyError = false)
                }
                
                is PersistencyStrategy.MultiProvider.WriteStrategy.WriteWithBackup -> {
                    writeWithBackup(agentCheckpointData, writeStrategy.primary, writeStrategy.backups)
                }
            }
        }

        override suspend fun getCheckpoints(): List<AgentCheckpointData> {
            return when (val readStrategy = strategy.readStrategy) {
                is PersistencyStrategy.MultiProvider.ReadStrategy.Prioritized -> {
                    readPrioritizedNonNull(readStrategy.providerNames) { it.getCheckpoints() }
                }
                
                is PersistencyStrategy.MultiProvider.ReadStrategy.PrimaryOnly -> {
                    val provider = strategy.providers[readStrategy.primary]
                        ?: throw IllegalStateException("Primary provider '${readStrategy.primary}' not found")
                    provider.getCheckpoints()
                }
                
                is PersistencyStrategy.MultiProvider.ReadStrategy.FastestFirst -> {
                    val providers = listOf(readStrategy.fast) + readStrategy.fallbacks
                    readPrioritizedNonNull(providers) { it.getCheckpoints() }
                }
            }
        }

        override suspend fun getLatestCheckpoint(): AgentCheckpointData? {
            return when (val readStrategy = strategy.readStrategy) {
                is PersistencyStrategy.MultiProvider.ReadStrategy.Prioritized -> {
                    readPrioritizedNullable(readStrategy.providerNames) { it.getLatestCheckpoint() }
                }
                
                is PersistencyStrategy.MultiProvider.ReadStrategy.PrimaryOnly -> {
                    val provider = strategy.providers[readStrategy.primary]
                        ?: throw IllegalStateException("Primary provider '${readStrategy.primary}' not found")
                    provider.getLatestCheckpoint()
                }
                
                is PersistencyStrategy.MultiProvider.ReadStrategy.FastestFirst -> {
                    val providers = listOf(readStrategy.fast) + readStrategy.fallbacks
                    readPrioritizedNullable(providers) { it.getLatestCheckpoint() }
                }
            }
        }

        private suspend fun writeToAll(
            checkpoint: AgentCheckpointData,
            providerNames: List<String>,
            failOnAnyError: Boolean
        ) {
            val providers = providerNames.mapNotNull { name ->
                strategy.providers[name] ?: run {
                    logger.warn { "Provider '$name' not found in MultiProvider strategy" }
                    null
                }
            }

            if (providers.isEmpty()) {
                throw IllegalStateException("No valid providers found for write operation")
            }

            val exceptions = mutableListOf<Exception>()
            var successCount = 0

            for (provider in providers) {
                try {
                    provider.saveCheckpoint(checkpoint)
                    successCount++
                    logger.debug { "Successfully wrote checkpoint to ${provider::class.simpleName}" }
                } catch (e: Exception) {
                    exceptions.add(e)
                    logger.warn(e) { "Failed to write checkpoint to ${provider::class.simpleName}" }
                    
                    if (failOnAnyError) {
                        throw IllegalStateException("Write failed to ${provider::class.simpleName}", e)
                    }
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

        private suspend fun writeWithBackup(
            checkpoint: AgentCheckpointData,
            primaryName: String,
            backupNames: List<String>
        ) {
            val primary = strategy.providers[primaryName]
                ?: throw IllegalStateException("Primary provider '$primaryName' not found")

            // Write to primary first
            try {
                primary.saveCheckpoint(checkpoint)
                logger.debug { "Successfully wrote checkpoint to primary provider ${primary::class.simpleName}" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to write checkpoint to primary provider ${primary::class.simpleName}" }
                throw e
            }

            // Write to backups (best effort)
            for (backupName in backupNames) {
                val backup = strategy.providers[backupName]
                if (backup != null) {
                    try {
                        backup.saveCheckpoint(checkpoint)
                        logger.debug { "Successfully wrote checkpoint to backup provider ${backup::class.simpleName}" }
                    } catch (e: Exception) {
                        logger.warn(e) { "Failed to write checkpoint to backup provider ${backup::class.simpleName}" }
                        // Continue with other backups
                    }
                } else {
                    logger.warn { "Backup provider '$backupName' not found" }
                }
            }
        }

        /**
         * Read from providers in priority order, returning first non-null result.
         * Used for nullable operations like getLatestCheckpoint().
         */
        private suspend fun <T> readPrioritizedNullable(
            providerNames: List<String>,
            operation: suspend (PersistencyStorageProvider) -> T?
        ): T? {
            var lastException: Exception? = null

            for (providerName in providerNames) {
                val provider = strategy.providers[providerName]
                if (provider != null) {
                    try {
                        val result = operation(provider)
                        if (result != null) {
                            logger.debug { "Successfully read non-null result from provider ${provider::class.simpleName}" }
                            return result
                        } else {
                            logger.debug { "Provider ${provider::class.simpleName} returned null, trying next provider" }
                        }
                    } catch (e: Exception) {
                        lastException = e
                        logger.warn(e) { "Failed to read from provider ${provider::class.simpleName}" }
                        // Continue to next provider
                    }
                } else {
                    logger.warn { "Provider '$providerName' not found in MultiProvider strategy" }
                }
            }

            // All providers returned null or failed - return null
            logger.debug { "All ${providerNames.size} providers returned null or failed" }
            return null
        }

        /**
         * Read from providers in priority order, returning first successful result.
         * Used for non-nullable operations like getCheckpoints().
         */
        private suspend fun <T> readPrioritizedNonNull(
            providerNames: List<String>,
            operation: suspend (PersistencyStorageProvider) -> T
        ): T {
            var lastException: Exception? = null

            for (providerName in providerNames) {
                val provider = strategy.providers[providerName]
                if (provider != null) {
                    try {
                        val result = operation(provider)
                        logger.debug { "Successfully read from provider ${provider::class.simpleName}" }
                        return result
                    } catch (e: Exception) {
                        lastException = e
                        logger.warn(e) { "Failed to read from provider ${provider::class.simpleName}" }
                        // Continue to next provider
                    }
                } else {
                    logger.warn { "Provider '$providerName' not found in MultiProvider strategy" }
                }
            }

            throw IllegalStateException(
                "All ${providerNames.size} providers failed to read data",
                lastException
            )
        }
    }
}
