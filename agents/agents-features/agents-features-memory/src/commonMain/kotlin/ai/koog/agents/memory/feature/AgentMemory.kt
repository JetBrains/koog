package ai.koog.agents.memory.feature

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.AIAgentLLMContext
import ai.koog.agents.core.agent.context.featureOrThrow
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.agent.session.AIAgentLLMWriteSession
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.extension.dropTrailingToolCalls
import ai.koog.agents.core.feature.AIAgentFunctionalFeature
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.pipeline.AIAgentFunctionalPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentGraphPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentPipeline
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.memory.config.MemoryScopeType
import ai.koog.agents.memory.config.MemoryScopesProfile
import ai.koog.agents.memory.model.Concept
import ai.koog.agents.memory.model.DefaultTimeProvider.getCurrentTimestamp
import ai.koog.agents.memory.model.Fact
import ai.koog.agents.memory.model.FactType
import ai.koog.agents.memory.model.MemoryScope
import ai.koog.agents.memory.model.MemorySubject
import ai.koog.agents.memory.model.MultipleFacts
import ai.koog.agents.memory.model.SingleFact
import ai.koog.agents.memory.prompts.MemoryPrompts
import ai.koog.agents.memory.providers.AgentMemoryProvider
import ai.koog.agents.memory.providers.NoMemory
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.structure.StructuredOutput
import ai.koog.prompt.structure.StructuredOutputConfig
import ai.koog.prompt.structure.json.JsonStructuredData
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable

/**
 * Memory implementation for AI agents that provides persistent storage and retrieval of facts.
 *
 * The AgentMemory feature enables agents to:
 * - Store information (facts) for later retrieval
 * - Organize memory by concepts, subjects, and scopes
 * - Share knowledge between different agents based on scope
 * - Extract facts from conversation history
 * - Load relevant facts into the agent's context
 *
 * This class serves as the main interface for memory operations within an agent,
 * combining the memory provider with the agent's LLM context to seamlessly
 * integrate memory capabilities into the agent's workflow.
 *
 * To install the AgentMemory feature in your agent:
 * ```kotlin
 * val agent = AIAgents(
 *     strategy = myStrategy,
 *     promptExecutor = myExecutor
 * ) {
 *     // Install memory feature with custom configuration
 *     install(AgentMemory) {
 *         // Configure memory provider (required)
 *         memoryProvider = LocalFileMemoryProvider(
 *             config = LocalMemoryConfig("my-agent-memory"),
 *             storage = SimpleStorage(JVMFileSystemProvider),
 *             root = Path("memory/data")
 *         )
 *
 *         // Configure scope names (optional)
 *         featureName = "code-assistant"
 *         productName = "my-ide"
 *         organizationName = "my-company"
 *     }
 * }
 * ```
 *
 * Example usage within an agent node:
 * ```kotlin
 * val rememberUserPreference by node {
 *     withMemory {
 *         // Save a fact about user preference
 *         agentMemory.save(
 *             fact = SingleFact(
 *                 concept = Concept("preferred-language", "User's preferred programming language"),
 *                 value = "Kotlin"
 *             ),
 *             subject = MemorySubjects.User,
 *             scope = MemoryScope.Product("my-ide")
 *         )
 *     }
 * }
 * ```
 *
 * @property agentMemory The provider that handles the actual storage and retrieval of facts
 * @property scopesProfile Profile containing scope names for memory operations
 *
 * @see AgentMemoryProvider
 * @see MemoryScopesProfile
 */
@OptIn(InternalAgentsApi::class)
public class AgentMemory(
    @property:InternalAgentsApi
    public val agentMemory: AgentMemoryProvider,
    @property:InternalAgentsApi
    public val scopesProfile: MemoryScopesProfile
) {
    private val logger = KotlinLogging.logger { }

    /**
     * Configuration for the AgentMemory feature.
     *
     * This class allows configuring:
     * - The memory provider to use for storage and retrieval
     * - Names for different memory scopes (agent, feature, product, organization)
     *
     * The scope names are used to create concrete [MemoryScope] instances when
     * performing memory operations, determining the visibility of stored facts.
     */
    public class Config : FeatureConfig() {
        /**
         * The provider that handles the actual storage and retrieval of facts.
         * Defaults to [NoMemory], which doesn't store anything.
         */
        public var memoryProvider: AgentMemoryProvider = NoMemory

        /**
         * Profile containing scope names for memory operations.
         * This is used internally to map scope types to concrete scopes.
         */
        internal var scopesProfile: MemoryScopesProfile = MemoryScopesProfile()

        /**
         * The name of the agent for AGENT-scoped memory operations.
         * Facts stored with AGENT scope will only be accessible to this specific agent.
         */
        public var agentName: String
            get() = scopesProfile.names[MemoryScopeType.AGENT] ?: UNKNOWN_NAME
            set(value) {
                scopesProfile.names[MemoryScopeType.AGENT] = value
            }

        /**
         * The name of the feature for FEATURE-scoped memory operations.
         * Facts stored with FEATURE scope will be shared between all agents of this feature.
         */
        public var featureName: String
            get() = scopesProfile.names[MemoryScopeType.FEATURE] ?: UNKNOWN_NAME
            set(value) {
                scopesProfile.names[MemoryScopeType.FEATURE] = value
            }

        /**
         * The name of the organization for ORGANIZATION-scoped memory operations.
         * Facts stored with ORGANIZATION scope will be shared across different products.
         */
        public var organizationName: String
            get() = scopesProfile.names[MemoryScopeType.ORGANIZATION] ?: UNKNOWN_NAME
            set(value) {
                scopesProfile.names[MemoryScopeType.ORGANIZATION] = value
            }

        /**
         * The name of the product for PRODUCT-scoped memory operations.
         * Facts stored with PRODUCT scope will be available across the entire product.
         */
        public var productName: String
            get() = scopesProfile.names[MemoryScopeType.PRODUCT] ?: UNKNOWN_NAME
            set(value) {
                scopesProfile.names[MemoryScopeType.PRODUCT] = value
            }

        internal companion object {
            const val UNKNOWN_NAME = "unknown"
        }
    }

    /**
     * Companion object implementing agent feature, handling [AgentMemory] creation and installation.
     */
    public companion object Feature :
        AIAgentGraphFeature<Config, AgentMemory>,
        AIAgentFunctionalFeature<Config, AgentMemory> {
        override val key: AIAgentStorageKey<AgentMemory> =
            createStorageKey<AgentMemory>("local-ai-agent-memory-feature")

        override fun createInitialConfig(): Config = Config()

        /**
         * Create a feature implementation using the provided configuration.
         */
        private fun createFeature(
            config: Config,
            pipeline: AIAgentPipeline,
        ): AgentMemory {
            val memory = AgentMemory(config.memoryProvider, config.scopesProfile)

            pipeline.interceptStrategyStarting(this) { ctx ->
                // Setting default agent name the same as strategy name
                // TODO not very robust
                memory.scopesProfile.let {
                    if (MemoryScopeType.AGENT !in it.names) {
                        it.names[MemoryScopeType.AGENT] = ctx.strategy.name
                    }
                }
            }

            return memory
        }

        override fun install(
            config: Config,
            pipeline: AIAgentGraphPipeline,
        ): AgentMemory = createFeature(config, pipeline)

        override fun install(
            config: Config,
            pipeline: AIAgentFunctionalPipeline,
        ): AgentMemory = createFeature(config, pipeline)
    }

    /**
     * Extracts and saves facts from the LLM chat history based on the provided concept.
     *
     * This method:
     * 1. Asks the LLM to extract facts about the specified concept from the conversation history
     * 2. Formats the response as a Fact object (SingleFact or MultipleFacts)
     * 3. Saves the fact to memory with the specified subject and scope
     *
     * Example usage:
     * ```kotlin
     * // Extract and save project dependencies from the conversation
     * memory.saveFactsFromHistory(
     *     concept = Concept("project-dependencies", "Project build dependencies", FactType.MULTIPLE),
     *     subject = MemorySubjects.Project,
     *     scope = MemoryScope.Product("my-ide")
     * )
     * ```
     *
     * @param llm Current LLM context to interact with the agent's chat history
     * @param concept The concept to extract facts about
     * @param subject The subject categorization for the facts (e.g., User, Project)
     * @param scope The visibility scope for the facts (e.g., Agent, Feature, Product)
     * @param retrievalModel LLM that will be used for fact retrieval from the history (by default, the same model as the current one will be used)
     */
    public suspend fun saveFactsFromHistory(
        llm: AIAgentLLMContext,
        concept: Concept,
        subject: MemorySubject,
        scope: MemoryScope,
        retrievalModel: LLModel? = null
    ) {
        llm.writeSession {
            val initialModel = model
            if (retrievalModel != null) {
                model = retrievalModel
                logger.info { "Using model: ${retrievalModel.id}" }
            }
            val facts = retrieveFactsFromHistory(concept)

            // Save facts to memory
            agentMemory.save(facts, subject, scope)
            logger.info { "Saved fact for concept '${concept.keyword}' in scope $scope: $facts" }
            if (retrievalModel != null) {
                model = initialModel
                logger.info { "Switching back to model: ${initialModel.id}" }
            }
        }
    }

    /**
     * Loads facts about a specific concept from memory and adds them to the LLM chat history.
     *
     * This method retrieves facts about the specified concept from all requested scopes and subjects,
     * then adds them to the agent's LLM context as user messages. This makes the information
     * available to the LLM for subsequent interactions.
     *
     * Facts are loaded with priority given to more specific subjects (lower priority level).
     * For single facts with the same concept, only the most specific one is used.
     *
     * Example usage:
     * ```kotlin
     * // Load user preferences into the agent's context
     * memory.loadFactsToAgent(
     *     concept = Concept("preferred-language", "User's preferred programming language"),
     *     scopes = listOf(MemoryScopeType.PRODUCT, MemoryScopeType.AGENT),
     *     subjects = listOf(MemorySubjects.User)
     * )
     * ```
     *
     * @param llm Current LLM context to interact with the agent's chat history.
     * @param concept The concept to load facts about
     * @param scopes List of memory scopes to search in (Agent, Feature, etc.). By default all scopes are used.
     * @param subjects List of subjects to search in (User, Project, etc.). By default all registered subjects are used.
     */
    @OptIn(InternalAgentsApi::class)
    public suspend fun loadFactsToAgent(
        llm: AIAgentLLMContext,
        concept: Concept,
        scopes: List<MemoryScopeType> = MemoryScopeType.entries,
        subjects: List<MemorySubject> = MemorySubject.registeredSubjects,
    ): Unit = loadFactsToAgentImpl(llm, scopes, subjects) { subject, scope ->
        agentMemory.load(concept, subject, scope)
    }

    /**
     * Loads all available facts from memory and adds them to the LLM chat history.
     *
     * This method is similar to [loadFactsToAgent] but retrieves facts for all concepts
     * instead of a specific one. It's useful for initializing an agent with all available
     * relevant information.
     *
     * Example usage:
     * ```kotlin
     * // Load all project-related facts from the product scope
     * memory.loadAllFactsToAgent(
     *     scopes = listOf(MemoryScopeType.PRODUCT),
     *     subjects = listOf(MemorySubjects.Project)
     * )
     * ```
     *
     * @param llm Current LLM context to interact with the agent's chat history.
     * @param scopes List of memory scopes to search in (Agent, Feature, etc.). By default all scopes are used.
     * @param subjects List of subjects to search in (User, Project, etc.). By default all registered subjects are used.
     */
    public suspend fun loadAllFactsToAgent(
        llm: AIAgentLLMContext,
        scopes: List<MemoryScopeType> = MemoryScopeType.entries,
        subjects: List<MemorySubject> = MemorySubject.registeredSubjects,
    ): Unit = loadFactsToAgentImpl(llm, scopes, subjects, agentMemory::loadAll)

    /**
     * Implementation method for loading facts from memory and adding them to the LLM chat history.
     *
     * This method handles the complex logic of:
     * 1. Loading facts from multiple scopes and subjects
     * 2. Prioritizing facts from more specific subjects
     * 3. Handling both single and multiple facts
     * 4. Formatting facts for the LLM context
     * 5. Adding the formatted facts to the LLM chat history
     *
     * @param llm Current LLM context to interact with the agent's chat history
     * @param scopes List of memory scopes to search in
     * @param subjects List of subjects to search in
     * @param loadFacts Function that loads facts for a given subject and scope
     */
    private suspend fun loadFactsToAgentImpl(
        llm: AIAgentLLMContext,
        scopes: List<MemoryScopeType>,
        subjects: List<MemorySubject>,
        loadFacts: suspend (subject: MemorySubject, scope: MemoryScope) -> List<Fact>
    ) {
        // Load facts for all matching scopes
        val facts = mutableListOf<Fact>()

        // Sort subjects by specificity (MACHINE -> USER -> PROJECT -> ORGANIZATION)
        val sortedSubjects = subjects.sortedBy { it.priorityLevel }

        // Track single facts by concept keyword and subject specificity
        val singleFactsByKeyword = mutableMapOf<String, Pair<MemorySubject, SingleFact>>()

        // Get all possible scopes based on the profile
        logger.info { "Using scopes: $scopes" }

        for (scope in scopes) {
            for (subject in sortedSubjects) {
                logger.info { "Loading facts for scope: $scope, subject: $subject" }
                val subjectFacts = loadFacts(subject, scopesProfile.getScope(scope) ?: continue)
                logger.info { "Loaded ${subjectFacts.size} facts" }

                for (fact in subjectFacts) {
                    when (fact) {
                        is SingleFact -> {
                            val existingFact = singleFactsByKeyword[fact.concept.keyword]
                            logger.info {
                                "Processing single fact: ${fact.value}, existing: ${existingFact?.second?.value}"
                            }
                            // Replace fact only if current subject is more specific (lower ordinal)
                            if (existingFact == null || subject.priorityLevel < existingFact.first.priorityLevel) {
                                logger.info {
                                    "Using fact from subject $subject (priorityLevel: ${subject.priorityLevel})"
                                }
                                singleFactsByKeyword[fact.concept.keyword] = subject to fact
                            }
                        }

                        is MultipleFacts -> {
                            logger.info { "Adding multiple facts: ${fact.values.joinToString()}" }
                            facts.add(fact)
                        }
                    }
                }
            }
        }

        logger.info { "Single facts by keyword: ${singleFactsByKeyword.mapValues { it.value.second.value }}" }
        // Add the most specific single facts to the result
        facts.addAll(singleFactsByKeyword.values.map { it.second })

        val factsByConcept = facts.groupBy { it.concept }

        logger.info { "Found ${facts.size} facts for ${factsByConcept.size} concepts" }

        // Add facts to LLM chat history
        if (factsByConcept.isNotEmpty()) {
            factsByConcept.forEach { (concept, facts) ->
                llm.writeSession {
                    val message = buildString {
                        appendLine(
                            "Here are the relevant facts from memory about [${concept.keyword}](${concept.description.shortened()}):"
                        )
                        facts.forEach { fact ->
                            when (fact) {
                                is SingleFact -> appendLine(
                                    "- [${fact.concept.keyword}]: ${fact.value}"
                                )

                                is MultipleFacts -> {
                                    appendLine("- [${fact.concept.keyword}]:")
                                    fact.values.forEach { value ->
                                        appendLine("  - $value")
                                    }
                                }
                            }
                        }
                    }
                    logger.info { "Built message for LLM: $message" }
                    logger.info { "Updating prompt with message" }
                    updatePrompt { user(message) }
                    logger.info { "Prompt updated" }
                }
            }
            logger.info { "Loaded ${facts.size} facts into LLM memory" }
        }
    }
}

/**
 * Extracts facts about a specific concept from the LLM chat history.
 *
 * This internal function:
 * 1. Adds a prompt to the LLM asking it to extract facts about the concept
 * 2. Processes the LLM's response into a structured Fact object
 * 3. Optionally removes the extraction messages from the chat history
 *
 * The function handles both single and multiple facts based on the concept's factType.
 *
 * @param concept The concept to extract facts about
 * @return A Fact object (either SingleFact or MultipleFacts) containing the extracted information
 */
@OptIn(InternalAgentsApi::class)
internal suspend fun AIAgentLLMWriteSession.retrieveFactsFromHistory(
    concept: Concept
): Fact {
    @Serializable
    @LLMDescription("Fact text")
    data class FactStructure(
        val fact: String
    )

    @Serializable
    @LLMDescription("Facts list")
    data class FactListStructure(
        val facts: List<FactStructure>
    )

    // Add a message asking to retrieve facts about the concept
    val promptForCompression = when (concept.factType) {
        FactType.SINGLE -> MemoryPrompts.singleFactPrompt(concept)
        FactType.MULTIPLE -> MemoryPrompts.multipleFactsPrompt(concept)
    }

    // remove tailing tool calls as we didn't provide any result for them
    dropTrailingToolCalls()

    val oldPrompt = this.prompt

    rewritePrompt {
        // Combine all history into one message with XML tags
        // to prevent LLM from continuing answering in a tool_call -> tool_result pattern
        val combinedMessage = buildString {
            append("<${MemoryPrompts.historyWrapperTag}>\n")
            oldPrompt.messages.forEach { message ->
                when (message) {
                    is Message.System -> append("<system>\n${message.content}\n</system>\n")
                    is Message.User -> append("<user>\n${message.content}\n</user>\n")
                    is Message.Assistant -> append("<assistant>\n${message.content}\n</assistant>\n")
                    is Message.Tool.Call -> append(
                        "<tool_call tool=${message.tool}>\n${message.content}\n</tool_call>\n"
                    )
                    is Message.Tool.Result -> append(
                        "<tool_result tool=${message.tool}>\n${message.content}\n</tool_result>\n"
                    )
                }
            }
            append("</${MemoryPrompts.historyWrapperTag}>\n")
        }

        // Put Compression prompt as a System instruction
        val newPrompt = Prompt.build(id = oldPrompt.id) {
            system(promptForCompression)
            user(combinedMessage)
        }

        return@rewritePrompt newPrompt
    }

    val timestamp = getCurrentTimestamp()

    val facts = when (concept.factType) {
        FactType.SINGLE -> {
            val response = requestLLMStructured(
                config = StructuredOutputConfig(default = StructuredOutput.Manual(JsonStructuredData.createJsonStructure<FactStructure>()))
            )

            SingleFact(
                concept = concept,
                value = response.getOrNull()?.structure?.fact ?: "No facts extracted",
                timestamp = timestamp
            )
        }

        FactType.MULTIPLE -> {
            val response = requestLLMStructured(
                config = StructuredOutputConfig(default = StructuredOutput.Manual(JsonStructuredData.createJsonStructure<FactListStructure>()))
            )
            val factsList = response.getOrNull()?.structure?.facts ?: emptyList()
            MultipleFacts(concept = concept, values = factsList.map { it.fact }, timestamp = timestamp)
        }
    }

    // Restore the original prompt
    rewritePrompt { oldPrompt }

    return facts
}

/**
 * Utility function to shorten a string for display purposes.
 * Takes the first line and truncates it to 100 characters.
 */
private fun String.shortened() = lines().first().take(100) + "..."

/**
 * Extension function to access the AgentMemory feature from a AIAgentStageContext.
 *
 * This provides a convenient way to access memory operations within agent nodes.
 *
 * Example usage:
 * ```kotlin
 * val rememberUserPreference by node {
 *     // Access memory directly
 *     val memory = stageContext.memory()
 *     // Use memory operations...
 * }
 * ```
 *
 * @return The AgentMemory instance for this agent context
 */
public fun AIAgentContext.memory(): AgentMemory = featureOrThrow(AgentMemory)

/**
 * Extension function to perform memory operations within a AIAgentStageContext.
 *
 * This provides a convenient way to use memory operations within agent nodes
 * with a more concise syntax using the `withMemory` block.
 *
 * Example usage:
 * ```kotlin
 * val loadUserPreferences by node {
 *     // Use memory operations in a block
 *     stageContext.withMemory {
 *         loadFactsToAgent(
 *             concept = Concept("preferred-language", "User's preferred programming language"),
 *             subjects = listOf(MemorySubjects.User)
 *         )
 *     }
 * }
 * ```
 *
 * @param action The memory operations to perform
 * @return The result of the action
 */
public suspend fun <T> AIAgentContext.withMemory(action: suspend AgentMemory.() -> T): T = memory().action()
