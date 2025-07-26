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
 * a [PersistencyStrategy] to determine which coordination approach to use for each agent.
 *
 * @property strategy The strategy that determines coordination selection
 * @property registry The provider registry for resolving provider references
 * @property context The agent context used for strategy decisions
 */
@OptIn(InternalAgentsApi::class)
public open class PersistencyStrategyProvider(
    protected val strategy: PersistencyStrategy,
    protected val registry: ProviderRegistry,
    protected val context: AIAgentContextBase
) : PersistencyStorageProvider {

    private companion object {
        private val logger = KotlinLogging.logger { }
        private val noOpProvider = NoPersistencyStorageProvider()
    }
    
    // Cache the selected coordination strategy to ensure consistency
    private var cachedCoordination: CoordinationStrategy? = null

    override suspend fun saveCheckpoint(agentCheckpointData: AgentCheckpointData) {
        val handler = getCoordinationHandler()
        handler.saveCheckpoint(agentCheckpointData)
    }

    override suspend fun getCheckpoints(): List<AgentCheckpointData> {
        val handler = getCoordinationHandler()
        return handler.getCheckpoints()
    }

    override suspend fun getLatestCheckpoint(): AgentCheckpointData? {
        val handler = getCoordinationHandler()
        return handler.getLatestCheckpoint()
    }

    /**
     * Gets the coordination handler for this agent, using caching to ensure consistency.
     * All operations for an agent will use the same coordination strategy.
     */
    private suspend fun getCoordinationHandler(): PersistencyStorageProvider {
        // Return cached handler if already selected
        cachedCoordination?.let { coordination ->
            return createCoordinationHandler(coordination)
        }
        
        // Select coordination strategy based on strategy
        val coordination = when (strategy) {
            is PersistencyStrategy.None -> {
                return noOpProvider
            }

            is PersistencyStrategy.Fixed -> strategy.coordination

            is PersistencyStrategy.Dynamic -> {
                val agentContext = PersistencyStrategy.Dynamic.AgentContext(
                    agentContext = context
                )
                strategy.selector(agentContext, registry)
            }

            is PersistencyStrategy.AutoSelectForTask -> selectCoordinationWithLLM(strategy)
        }
        
        // Cache the selected coordination to ensure all operations use the same strategy
        cachedCoordination = coordination
        return createCoordinationHandler(coordination)
    }

    /**
     * Creates a handler for the specified coordination strategy.
     */
    private fun createCoordinationHandler(coordination: CoordinationStrategy): PersistencyStorageProvider {
        return CoordinationHandler(coordination, registry)
    }

    /**
     * Data class for LLM coordination selection response.
     */
    @Serializable
    private data class SelectedCoordination(
        val coordinationType: String,
        val reasoning: String? = null
    )

    /**
     * Selects a coordination strategy using LLM based on the task description and available options.
     */
    @OptIn(DetachedPromptExecutorAPI::class)
    private suspend fun selectCoordinationWithLLM(
        strategy: PersistencyStrategy.AutoSelectForTask
    ): CoordinationStrategy {
        // Build coordination descriptions for LLM
        val coordinationDescriptions = strategy.options.mapIndexed { index, coordination ->
            val description = when (coordination) {
                is CoordinationStrategy.Single -> 
                    "Single provider (${coordination.provider.value}) - simple, reliable"
                is CoordinationStrategy.WriteToAll -> 
                    "Write to all providers (${coordination.providers.joinToString { it.value }}) - maximum durability"
                is CoordinationStrategy.WriteAllBestEffort -> 
                    "Best effort write to all providers - high availability"
                is CoordinationStrategy.WriteWithBackup -> 
                    "Primary (${coordination.primary.value}) with backups - reliable with redundancy"
                is CoordinationStrategy.Prioritized -> 
                    "Prioritized providers (${coordination.providers.joinToString { it.value }}) - failover"
                is CoordinationStrategy.FastestFirst -> 
                    "Fast first (${coordination.fast.value}) with fallbacks - optimized performance"
            }
            "Option $index: $description"
        }.joinToString("\n")

        // Determine fallback coordination (first available)
        val fallbackCoordination = strategy.options.firstOrNull()

        var lastException: Exception? = null

        // Attempt LLM selection with retries
        for (attempt in 0 until strategy.maxRetries) {
            try {
                val selected = context.llm.writeSession {
                    val initialPrompt = prompt

                    replaceHistoryWithTLDR()

                    updatePrompt {
                        user {
                            """
                            Select the best coordination strategy for this agent checkpoint task:
                            
                            Task: ${strategy.taskDescription}
                            
                            Available coordination options:
                            $coordinationDescriptions
                            
                            Respond with the option number (0-${strategy.options.size - 1}) that best fits the task requirements.
                            """.trimIndent()
                        }
                    }

                    val selectedCoordination = this.requestLLMStructured(
                        structure = JsonStructuredData.createJsonStructure<SelectedCoordination>(
                            schemaFormat = JsonSchemaGenerator.SchemaFormat.JsonSchema,
                            examples = listOf(
                                SelectedCoordination("0", "Selected single provider for simple task"),
                                SelectedCoordination("1", "Selected write-to-all for critical data")
                            )
                        ),
                        retries = 1, // Single retry per attempt to avoid nested retries
                    ).getOrThrow()

                    prompt = initialPrompt

                    selectedCoordination.structure
                }

                // Validate LLM selection
                val optionIndex = selected.coordinationType.toIntOrNull()
                if (optionIndex != null && optionIndex in strategy.options.indices) {
                    val selectedCoordination = strategy.options[optionIndex]
                    logger.debug {
                        "LLM selected coordination option $optionIndex for task '${strategy.taskDescription}' on attempt ${attempt + 1}" +
                        selected.reasoning?.let { " (reasoning: $it)" }.orEmpty()
                    }
                    return selectedCoordination
                } else {
                    throw IllegalStateException(
                        "LLM selected invalid option '${selected.coordinationType}'. Valid options: 0-${strategy.options.size - 1}"
                    )
                }

            } catch (e: Exception) {
                lastException = e
                logger.warn {
                    "LLM coordination selection failed on attempt ${attempt + 1}/${strategy.maxRetries}: ${e.message}"
                }
            }
        }

        // All LLM attempts failed, use fallback strategy
        if (fallbackCoordination != null) {
            logger.warn {
                "LLM coordination selection failed after ${strategy.maxRetries} attempts, falling back to first option"
            }
            return fallbackCoordination
        } else {
            // No fallback available, throw the last exception
            throw IllegalStateException(
                "LLM coordination selection failed after ${strategy.maxRetries} attempts and no coordination options available",
                lastException
            )
        }
    }

    /**
     * Handler for coordination strategies that coordinates operations across multiple providers.
     */
    private class CoordinationHandler(
        private val coordination: CoordinationStrategy,
        private val registry: ProviderRegistry
    ) : PersistencyStorageProvider {

        private companion object {
            private val logger = KotlinLogging.logger { }
        }

        override suspend fun saveCheckpoint(agentCheckpointData: AgentCheckpointData) {
            when (coordination) {
                is CoordinationStrategy.Single -> {
                    registry.get(coordination.provider).saveCheckpoint(agentCheckpointData)
                }
                
                is CoordinationStrategy.WriteToAll -> {
                    writeToAll(agentCheckpointData, coordination.providers, failOnAnyError = true)
                }
                
                is CoordinationStrategy.WriteAllBestEffort -> {
                    writeToAll(agentCheckpointData, coordination.providers, failOnAnyError = false)
                }
                
                is CoordinationStrategy.WriteWithBackup -> {
                    writeWithBackup(agentCheckpointData, coordination.primary, coordination.backups)
                }
                
                is CoordinationStrategy.Prioritized -> {
                    writePrioritized(agentCheckpointData, coordination.providers)
                }
                
                is CoordinationStrategy.FastestFirst -> {
                    val providers = listOf(coordination.fast) + coordination.fallbacks
                    writePrioritized(agentCheckpointData, providers)
                }
            }
        }

        override suspend fun getCheckpoints(): List<AgentCheckpointData> {
            return when (coordination) {
                is CoordinationStrategy.Single -> {
                    registry.get(coordination.provider).getCheckpoints()
                }
                
                is CoordinationStrategy.WriteToAll -> {
                    registry.get(coordination.readFrom).getCheckpoints()
                }
                
                is CoordinationStrategy.WriteAllBestEffort -> {
                    registry.get(coordination.readFrom).getCheckpoints()
                }
                
                is CoordinationStrategy.WriteWithBackup -> {
                    registry.get(coordination.primary).getCheckpoints()
                }
                
                is CoordinationStrategy.Prioritized -> {
                    readPrioritizedNonNull(coordination.providers) { it.getCheckpoints() }
                }
                
                is CoordinationStrategy.FastestFirst -> {
                    val providers = listOf(coordination.fast) + coordination.fallbacks
                    readPrioritizedNonNull(providers) { it.getCheckpoints() }
                }
            }
        }

        override suspend fun getLatestCheckpoint(): AgentCheckpointData? {
            return when (coordination) {
                is CoordinationStrategy.Single -> {
                    registry.get(coordination.provider).getLatestCheckpoint()
                }
                
                is CoordinationStrategy.WriteToAll -> {
                    registry.get(coordination.readFrom).getLatestCheckpoint()
                }
                
                is CoordinationStrategy.WriteAllBestEffort -> {
                    registry.get(coordination.readFrom).getLatestCheckpoint()
                }
                
                is CoordinationStrategy.WriteWithBackup -> {
                    registry.get(coordination.primary).getLatestCheckpoint()
                }
                
                is CoordinationStrategy.Prioritized -> {
                    readPrioritizedNullable(coordination.providers) { it.getLatestCheckpoint() }
                }
                
                is CoordinationStrategy.FastestFirst -> {
                    val providers = listOf(coordination.fast) + coordination.fallbacks
                    readPrioritizedNullable(providers) { it.getLatestCheckpoint() }
                }
            }
        }

        private suspend fun writeToAll(
            checkpoint: AgentCheckpointData,
            providerIds: List<ProviderId>,
            failOnAnyError: Boolean
        ) {
            val providers = providerIds.map { registry.get(it) }
            val exceptions = mutableListOf<Exception>()
            var successCount = 0

            for ((index, provider) in providers.withIndex()) {
                try {
                    provider.saveCheckpoint(checkpoint)
                    successCount++
                    logger.debug { "Successfully wrote checkpoint to ${providerIds[index].value}" }
                } catch (e: Exception) {
                    exceptions.add(e)
                    logger.warn(e) { "Failed to write checkpoint to ${providerIds[index].value}" }
                    
                    if (failOnAnyError) {
                        throw IllegalStateException("Write failed to ${providerIds[index].value}", e)
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
            primaryId: ProviderId,
            backupIds: List<ProviderId>
        ) {
            val primary = registry.get(primaryId)

            // Write to primary first
            try {
                primary.saveCheckpoint(checkpoint)
                logger.debug { "Successfully wrote checkpoint to primary provider ${primaryId.value}" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to write checkpoint to primary provider ${primaryId.value}" }
                throw e
            }

            // Write to backups (best effort)
            for (backupId in backupIds) {
                try {
                    val backup = registry.get(backupId)
                    backup.saveCheckpoint(checkpoint)
                    logger.debug { "Successfully wrote checkpoint to backup provider ${backupId.value}" }
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to write checkpoint to backup provider ${backupId.value}" }
                    // Continue with other backups
                }
            }
        }

        private suspend fun writePrioritized(
            checkpoint: AgentCheckpointData,
            providerIds: List<ProviderId>
        ) {
            var lastException: Exception? = null

            for (providerId in providerIds) {
                try {
                    val provider = registry.get(providerId)
                    provider.saveCheckpoint(checkpoint)
                    logger.debug { "Successfully wrote checkpoint to provider ${providerId.value}" }
                    return
                } catch (e: Exception) {
                    lastException = e
                    logger.warn(e) { "Failed to write checkpoint to provider ${providerId.value}" }
                    // Continue to next provider
                }
            }

            throw IllegalStateException(
                "All ${providerIds.size} providers failed to save checkpoint",
                lastException
            )
        }

        private suspend fun <T> readPrioritizedNullable(
            providerIds: List<ProviderId>,
            operation: suspend (PersistencyStorageProvider) -> T?
        ): T? {
            for (providerId in providerIds) {
                try {
                    val provider = registry.get(providerId)
                    val result = operation(provider)
                    if (result != null) {
                        logger.debug { "Successfully read non-null result from provider ${providerId.value}" }
                        return result
                    } else {
                        logger.debug { "Provider ${providerId.value} returned null, trying next provider" }
                    }
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to read from provider ${providerId.value}" }
                    // Continue to next provider
                }
            }

            // All providers returned null or failed - return null
            logger.debug { "All ${providerIds.size} providers returned null or failed" }
            return null
        }

        private suspend fun <T> readPrioritizedNonNull(
            providerIds: List<ProviderId>,
            operation: suspend (PersistencyStorageProvider) -> T
        ): T {
            var lastException: Exception? = null

            for (providerId in providerIds) {
                try {
                    val provider = registry.get(providerId)
                    val result = operation(provider)
                    logger.debug { "Successfully read from provider ${providerId.value}" }
                    return result
                } catch (e: Exception) {
                    lastException = e
                    logger.warn(e) { "Failed to read from provider ${providerId.value}" }
                    // Continue to next provider
                }
            }

            throw IllegalStateException(
                "All ${providerIds.size} providers failed to read data",
                lastException
            )
        }
    }
}