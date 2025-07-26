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
     * Can be overridden to add support for custom strategy types.
     */
    protected open suspend fun selectProvider(
        operation: PersistencyStrategy.Dynamic.Operation,
        checkpoint: AgentCheckpointData? = null
    ): PersistencyStorageProvider {
        return when (strategy) {
            is PersistencyStrategy.Single -> strategy.provider

            is PersistencyStrategy.None -> noOpProvider

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

            is PersistencyStrategy.AutoSelectForTask -> selectWithLLM(strategy)
        }
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
}
