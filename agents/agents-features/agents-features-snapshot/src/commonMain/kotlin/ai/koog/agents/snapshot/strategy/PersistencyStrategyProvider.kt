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
        val coordination = getCoordinationStrategy()
        coordination.saveCheckpoint(agentCheckpointData, registry)
    }

    override suspend fun getCheckpoints(): List<AgentCheckpointData> {
        val coordination = getCoordinationStrategy()
        return coordination.getCheckpoints(registry)
    }

    override suspend fun getLatestCheckpoint(): AgentCheckpointData? {
        val coordination = getCoordinationStrategy()
        return coordination.getLatestCheckpoint(registry)
    }

    /**
     * Gets the coordination strategy for this agent, using caching to ensure consistency.
     * All operations for an agent will use the same coordination strategy.
     */
    private suspend fun getCoordinationStrategy(): CoordinationStrategy {
        // Return cached coordination if already selected
        cachedCoordination?.let { return it }
        
        // Select coordination strategy based on strategy
        val coordination = when (strategy) {
            is PersistencyStrategy.None -> {
                // Return a no-op coordination strategy
                return object : CoordinationStrategy {
                    override suspend fun saveCheckpoint(checkpoint: AgentCheckpointData, registry: ProviderRegistry) {}
                    override suspend fun getCheckpoints(registry: ProviderRegistry): List<AgentCheckpointData> = emptyList()
                    override suspend fun getLatestCheckpoint(registry: ProviderRegistry): AgentCheckpointData? = null
                }
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
        return coordination
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
            val description = coordination.toString() // Use toString() for custom implementations
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

}