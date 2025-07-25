package ai.koog.agents.snapshot.strategy

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.context.DetachedPromptExecutorAPI
import ai.koog.agents.core.dsl.extension.replaceHistoryWithTLDR
import ai.koog.agents.snapshot.feature.AgentCheckpointData
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
public class PersistencyStrategyProvider(
    private val strategy: PersistencyStrategy,
    private val context: AIAgentContextBase
) : PersistencyStorageProvider {
    
    private companion object {
        private val logger = KotlinLogging.logger { }
        private val noOpProvider = NoPersistencyStorageProvider()
    }
    
    override suspend fun saveCheckpoint(agentCheckpointData: AgentCheckpointData) {
        val provider = selectProvider(
            operation = PersistencyStrategy.Dynamic.Operation.SaveCheckpoint,
            checkpoint = agentCheckpointData
        )
        provider.saveCheckpoint(agentCheckpointData)
    }
    
    override suspend fun getCheckpoints(): List<AgentCheckpointData> {
        val provider = selectProvider(PersistencyStrategy.Dynamic.Operation.GetCheckpoints)
        return provider.getCheckpoints()
    }
    
    override suspend fun getLatestCheckpoint(): AgentCheckpointData? {
        val provider = selectProvider(PersistencyStrategy.Dynamic.Operation.GetLatestCheckpoint)
        return provider.getLatestCheckpoint()
    }
    
    
    /**
     * Selects a provider based on the strategy and operation context.
     */
    private suspend fun selectProvider(
        operation: PersistencyStrategy.Dynamic.Operation,
        checkpoint: AgentCheckpointData? = null
    ): PersistencyStorageProvider {
        return when (strategy) {
            is PersistencyStrategy.Single -> strategy.provider
            
            is PersistencyStrategy.None -> noOpProvider
            
            is PersistencyStrategy.Failover -> selectWithFailover(strategy.providers, operation)
            
            is PersistencyStrategy.Dynamic -> {
                val context = PersistencyStrategy.Dynamic.OperationContext(
                    operation = operation,
                    agentContext = context,
                    checkpoint = checkpoint
                )
                val providerName = strategy.selector(context)
                strategy.providers[providerName]
                    ?: throw IllegalStateException("Provider '$providerName' not found in Dynamic strategy")
            }
            
            is PersistencyStrategy.Hybrid -> selectHybridProvider(strategy, operation, checkpoint)
            
            is PersistencyStrategy.AutoSelectForTask -> selectWithLLM(strategy, operation, checkpoint)
        }
    }
    
    /**
     * Attempts to find a working provider from the failover list.
     */
    private suspend fun selectWithFailover(
        providers: List<PersistencyStorageProvider>,
        operation: PersistencyStrategy.Dynamic.Operation
    ): PersistencyStorageProvider {
        if (providers.isEmpty()) {
            throw IllegalStateException("No providers configured in Failover strategy")
        }
        
        // For read operations, try each provider until we find one that works
        if (operation is PersistencyStrategy.Dynamic.Operation.GetLatestCheckpoint ||
            operation is PersistencyStrategy.Dynamic.Operation.GetCheckpoints
        ) {
            for ((index, provider) in providers.withIndex()) {
                try {
                    // Test if provider is accessible by trying to get checkpoints
                    provider.getCheckpoints()
                    return provider
                } catch (e: Exception) {
                    logger.warn { "Provider at index $index failed health check: ${e.message}" }
                    if (index == providers.lastIndex) {
                        throw IllegalStateException("All providers in failover list are unavailable", e)
                    }
                }
            }
        }
        
        // For write operations, use the first available provider
        return providers.first()
    }
    
    /**
     * Selects a provider based on the Hybrid strategy logic.
     */
    private suspend fun selectHybridProvider(
        strategy: PersistencyStrategy.Hybrid,
        operation: PersistencyStrategy.Dynamic.Operation,
        checkpoint: AgentCheckpointData?
    ): PersistencyStorageProvider {
        // Use custom selector if provided
        if (strategy.selector != null) {
            val context = PersistencyStrategy.Dynamic.OperationContext(
                operation = operation,
                agentContext = context,
                checkpoint = checkpoint
            )
            return when (strategy.selector.invoke(context)) {
                PersistencyStrategy.Hybrid.ProviderType.EPHEMERAL -> strategy.ephemeralProvider
                PersistencyStrategy.Hybrid.ProviderType.DURABLE -> strategy.durableProvider
                PersistencyStrategy.Hybrid.ProviderType.CRITICAL -> 
                    strategy.criticalProvider ?: strategy.durableProvider
            }
        }
        
        // Default hybrid logic
        return when (operation) {
            // Fast operations use ephemeral storage
            is PersistencyStrategy.Dynamic.Operation.SaveCheckpoint -> {
                // Determine if this is a mid-execution checkpoint based on context
                if (isMidExecutionCheckpoint()) {
                    strategy.ephemeralProvider
                } else {
                    strategy.durableProvider
                }
            }
            
            // Read operations try ephemeral first, then durable
            is PersistencyStrategy.Dynamic.Operation.GetLatestCheckpoint,
            is PersistencyStrategy.Dynamic.Operation.GetCheckpoints -> {
                // Try ephemeral first for recent checkpoints
                val ephemeralResult = runCatching {
                    when (operation) {
                        is PersistencyStrategy.Dynamic.Operation.GetLatestCheckpoint ->
                            strategy.ephemeralProvider.getLatestCheckpoint()
                        is PersistencyStrategy.Dynamic.Operation.GetCheckpoints ->
                            strategy.ephemeralProvider.getCheckpoints()
                        else -> null
                    }
                }.getOrNull()
                
                if (ephemeralResult != null && 
                    (ephemeralResult !is List<*> || ephemeralResult.isNotEmpty())) {
                    strategy.ephemeralProvider
                } else {
                    strategy.durableProvider
                }
            }
            
            // Other operations use durable storage
            else -> strategy.durableProvider
        }
    }
    
    /**
     * Determines if the current checkpoint is a mid-execution checkpoint.
     * This is a heuristic based on the agent's execution state.
     */
    private fun isMidExecutionCheckpoint(): Boolean {
        // Check if we're in the middle of a strategy execution
        // This is a simplified heuristic - real implementation might check:
        // - Node depth in the strategy  
        // - Time since last checkpoint
        // - Checkpoint frequency
        // - Explicit metadata
        
        // For now, we'll use a simple heuristic
        // In a real implementation, this could be determined by:
        // - Checking if we're not at a start or finish node
        // - Analyzing the checkpoint metadata
        // - Using custom indicators set by the agent
        
        // Default to false, allowing users to override with custom selector
        return false
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
     * Selects a provider using LLM based on the context and provider descriptions.
     */
    @OptIn(DetachedPromptExecutorAPI::class)
    private suspend fun selectWithLLM(
        strategy: PersistencyStrategy.AutoSelectForTask,
        operation: PersistencyStrategy.Dynamic.Operation,
        checkpoint: AgentCheckpointData?
    ): PersistencyStorageProvider {
        // Build provider descriptions for LLM
        val providerDescriptions = strategy.providers.entries.joinToString("\n") { (name, info) ->
            "- $name: ${info.description}" + 
            if (info.capabilities.isNotEmpty()) " (capabilities: ${info.capabilities.joinToString(", ")})" else ""
        }
        
        // Build operation context
        val operationDescription = when (operation) {
            is PersistencyStrategy.Dynamic.Operation.SaveCheckpoint -> 
                "Saving a checkpoint (nodeId: ${checkpoint?.nodeId}, messageCount: ${checkpoint?.messageHistory?.size})"
            is PersistencyStrategy.Dynamic.Operation.GetLatestCheckpoint -> 
                "Retrieving the latest checkpoint"
            is PersistencyStrategy.Dynamic.Operation.GetCheckpoints -> 
                "Retrieving all checkpoints"
            is PersistencyStrategy.Dynamic.Operation.GetCheckpointById -> 
                "Retrieving checkpoint by ID: ${operation.id}"
            is PersistencyStrategy.Dynamic.Operation.DeleteCheckpoint -> 
                "Deleting checkpoint: ${operation.id}"
            is PersistencyStrategy.Dynamic.Operation.DeleteAllCheckpoints -> 
                "Deleting all checkpoints"
            is PersistencyStrategy.Dynamic.Operation.GetCheckpointCount -> 
                "Getting checkpoint count"
        }
        
        val selected = context.llm.writeSession {
            val initialPrompt = prompt
            
            replaceHistoryWithTLDR()
            
            updatePrompt {
                user {
                    """
                    Select the most appropriate persistence provider for the following operation.
                    
                    Task context: ${strategy.taskDescription}
                    
                    Current operation: $operationDescription
                    
                    Available providers:
                    $providerDescriptions
                    
                    Consider factors like:
                    - Speed requirements (ephemeral vs durable)
                    - Data criticality
                    - Query needs
                    - Cost implications
                    
                    Return the name of the most suitable provider.
                    """.trimIndent()
                }
            }
            
            val selectedProvider = this.requestLLMStructured(
                structure = JsonStructuredData.createJsonStructure<SelectedProvider>(
                    schemaFormat = JsonSchemaGenerator.SchemaFormat.JsonSchema,
                    examples = listOf(
                        SelectedProvider("redis", "Fast ephemeral storage for mid-execution checkpoints"),
                        SelectedProvider("postgres", "Durable storage for session persistence")
                    )
                ),
                retries = strategy.maxRetries,
            ).getOrThrow()
            
            prompt = initialPrompt
            
            selectedProvider.structure
        }
        
        logger.debug { 
            "LLM selected provider '${selected.providerName}' for operation $operation" + 
            selected.reasoning?.let { " (reasoning: $it)" }.orEmpty()
        }
        
        return strategy.providers[selected.providerName]?.provider
            ?: throw IllegalStateException("LLM selected unknown provider: ${selected.providerName}")
    }
}