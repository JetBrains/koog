package ai.koog.agents.cli

import ai.koog.agents.cli.claude.ClaudeCliConfig
import ai.koog.agents.cli.claude.ClaudeCliStructuredConfig
import ai.koog.agents.cli.claude.ClaudePermissionMode
import ai.koog.agents.cli.codex.CodexApprovalPolicy
import ai.koog.agents.cli.codex.CodexCliConfig
import ai.koog.agents.cli.codex.CodexSandboxMode
import ai.koog.agents.cli.transport.CliTransport
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.AIAgentBase
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentLLMContext
import ai.koog.agents.core.agent.entity.AIAgentStateManager
import ai.koog.agents.core.agent.entity.AIAgentStorage
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.environment.ContextualAgentEnvironment
import ai.koog.agents.core.environment.GenericAgentEnvironment
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.AIAgentFunctionalFeature
import ai.koog.agents.core.feature.ContextualPromptExecutor
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.pipeline.AIAgentFunctionalPipeline
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.Structure
import ai.koog.prompt.structure.json.JsonStructure
import ai.koog.serialization.kotlinx.KotlinxSerializer
import ai.koog.serialization.typeToken
import ai.koog.utils.time.KoogClock
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlin.reflect.KClass
import kotlin.time.Duration

/**
 * Represents the core AI agent for processing input and generating output using
 * a defined configuration, toolset, and prompt execution pipeline.
 *
 * @param Input The type of input data expected by the agent.
 * @param Output The type of output data produced by the agent.
 * @property id The unique identifier for the agent instance.
 * @property agentConfig The configuration for the agent, including the prompt structure and execution parameters.
 * @property strategy The strategy for processing input and generating output.
 * @property clock The clock used to calculate message timestamps
 * @param id Unique identifier for the agent. Random UUID will be generated if set to null.
 * @property installFeatures Lambda for installing additional features within the agent environment.
 */
@OptIn(InternalAgentsApi::class)
public class CliAIAgent<Input, Output> internal constructor(
    override val agentConfig: AIAgentConfig,
    override val strategy: AIAgentCliStrategy<Input, Output>,
    id: String?,
    public val clock: KoogClock,
    @property:InternalAgentsApi
    public val installFeatures: FeatureContext.() -> Unit
) : AIAgentBase<Input, Output, AIAgentCliContext>(
    logger = logger,
    id = id,
) {
    internal constructor(
        systemPrompt: String?,
        llModel: LLModel?,
        strategy: AIAgentCliStrategy<Input, Output>,
        id: String?,
        clock: KoogClock = KoogClock.System,
        installFeatures: FeatureContext.() -> Unit = {}
    ) : this(
        AIAgentConfig(
            prompt = prompt(Prompt.Empty) { systemPrompt?.let { system(it) } },
            model = llModel ?: DummyModel,
            maxAgentIterations = 50,
        ),
        strategy,
        id,
        clock,
        installFeatures
    )

    private val toolRegistry: ToolRegistry = ToolRegistry.EMPTY

    override val pipeline: AIAgentFunctionalPipeline = AIAgentFunctionalPipeline(agentConfig, clock)

    /**
     * Represents a context for managing and configuring features in an AI agent.
     * Provides functionality to install and configure features into a specific instance of an AI agent.
     */
    public class FeatureContext internal constructor(private val agent: CliAIAgent<*, *>) {
        /**
         * Installs and configures a feature into the current AI agent context.
         *
         * @param feature the feature to be added, defined by an implementation of [AIAgentFeature], which provides specific functionality
         * @param configure an optional lambda to customize the configuration of the feature, where the provided [Config] can be modified
         */
        public fun <Config : FeatureConfig, Feature : Any> install(
            feature: AIAgentFunctionalFeature<Config, Feature>,
            configure: Config.() -> Unit = {}
        ) {
            agent.pipeline.install(feature, configure)
        }
    }

    init {
        FeatureContext(this).installFeatures()
    }

    override suspend fun prepareContext(agentInput: Input, runId: String, eventId: String): AIAgentCliContext {
        val environment = prepareEnvironment()

        val initialLLMContext = AIAgentLLMContext(
            tools = toolRegistry.tools.map { it.descriptor },
            toolRegistry = toolRegistry,
            prompt = agentConfig.prompt,
            model = agentConfig.model,
            responseProcessor = agentConfig.responseProcessor,
            promptExecutor = DummyPromptExecutor,
            environment = environment,
            config = agentConfig,
            clock = clock
        )

        val executionInfo = AgentExecutionInfo(parent = null, partName = id)

        // Context
        val initialAgentContext = AIAgentCliContext(
            environment = environment,
            agentId = id,
            runId = runId,
            agentInput = agentInput,
            config = agentConfig,
            llm = initialLLMContext,
            stateManager = AIAgentStateManager(),
            storage = AIAgentStorage(KotlinxSerializer()),
            strategyName = strategy.name,
            pipeline = pipeline,
            executionInfo = executionInfo,
            parentContext = null
        )

        // Updated environment
        val contextualEnvironment = ContextualAgentEnvironment(
            environment = environment,
            context = initialAgentContext,
        )

        val contextualPromptExecutor = ContextualPromptExecutor(
            executor = DummyPromptExecutor,
            context = initialAgentContext,
        )

        val updatedLLMContext = initialAgentContext.llm.copy(
            environment = contextualEnvironment,
            promptExecutor = contextualPromptExecutor,
        )

        val updatedAgentContext = initialAgentContext.copy(
            llm = updatedLLMContext,
            environment = contextualEnvironment,
            parentRootContext = initialAgentContext.parentContext, // Keep the original parent context
        )

        return updatedAgentContext
    }

    //region Private Methods

    private fun prepareEnvironment(): AIAgentEnvironment {
        val baseEnvironment = GenericAgentEnvironment(
            agentId = id,
            logger = logger,
            toolRegistry = toolRegistry,
            serializer = agentConfig.serializer,
        )

        return baseEnvironment
    }

    //endregion Private Methods

    /**
     * Companion object containing factory methods for creating instances of [CliAIAgent].
     */
    public companion object {
        /**
         * Creates a new instance of [CliAIAgent] using Claude.
         *
         * @param transport The transport used to execute cli commands.
         * @param apiKey The API key for the Claude code.
         * @param binaryPath The path to the binary.
         * @param name The name of the cli strategy.
         * @param systemPrompt The system prompt for the agent.
         * @param llModel The LLM model to be used for the agent.
         * @param permissionMode The [ClaudePermissionMode] mode for the agent.
         * @param additionalFlags Additional flags to be passed to the claude cli.
         * @param workspace The workspace directory for the agent.
         * @param timeout The timeout duration for the agent.
         * @param id The unique identifier for the agent.
         * @param clock The clock used to calculate message timestamps.
         * @param installFeatures Lambda for installing additional features.
         */
        @JvmStatic
        @JvmOverloads
        public fun claude(
            transport: CliTransport,
            apiKey: String? = null,
            binaryPath: String? = null,
            name: String? = null,
            systemPrompt: String? = null,
            llModel: LLModel? = null,
            permissionMode: ClaudePermissionMode? = null,
            additionalFlags: List<String> = emptyList(),
            workspace: String = ".",
            timeout: Duration? = null,
            id: String? = null,
            clock: KoogClock = KoogClock.System,
            installFeatures: FeatureContext.() -> Unit = {},
        ): CliAIAgent<String, CliAIAgentResponse> = claude<String>(
            transport = transport,
            apiKey = apiKey,
            binaryPath = binaryPath,
            name = name,
            systemPrompt = systemPrompt,
            llModel = llModel,
            permissionMode = permissionMode,
            additionalFlags = additionalFlags,
            workspace = workspace,
            timeout = timeout,
            id = id,
            clock = clock,
            generateRequest = { it },
            installFeatures = installFeatures,
        )

        /**
         * Creates a new instance of [CliAIAgent] using Claude.
         *
         * @param transport The transport used to execute cli commands.
         * @param apiKey The API key for the Claude code.
         * @param binaryPath The path to the binary.
         * @param name The name of the cli strategy.
         * @param systemPrompt The system prompt for the agent.
         * @param llModel The LLM model to be used for the agent.
         * @param permissionMode The [ClaudePermissionMode] mode for the agent.
         * @param additionalFlags Additional flags to be passed to the claude cli.
         * @param workspace The workspace directory for the agent.
         * @param timeout The timeout duration for the agent.
         * @param id The unique identifier for the agent.
         * @param clock The clock used to calculate message timestamps.
         * @param generateRequest Lambda for generating the request for the agent.
         * @param installFeatures Lambda for installing additional features.
         */
        @JvmStatic
        @JvmOverloads
        public fun <Input> claude(
            transport: CliTransport,
            apiKey: String? = null,
            binaryPath: String? = null,
            name: String? = null,
            systemPrompt: String? = null,
            llModel: LLModel? = null,
            permissionMode: ClaudePermissionMode? = null,
            additionalFlags: List<String> = emptyList(),
            workspace: String = ".",
            timeout: Duration? = null,
            id: String? = null,
            clock: KoogClock = KoogClock.System,
            generateRequest: CliConfig.GenerateRequest<Input>,
            installFeatures: FeatureContext.() -> Unit = {},
        ): CliAIAgent<Input, CliAIAgentResponse> {
            val strategy = AIAgentCliStrategy(
                config = ClaudeCliConfig(
                    transport = transport,
                    apiKey = apiKey,
                    binaryPath = binaryPath,
                    name = name,
                    permissionMode = permissionMode,
                    additionalFlags = additionalFlags,
                    workspace = workspace,
                    timeout = timeout,
                    generateRequest = generateRequest
                )
            )

            return CliAIAgent(
                systemPrompt = systemPrompt,
                llModel = llModel,
                strategy = strategy,
                id = id,
                clock = clock,
                installFeatures = installFeatures
            )
        }

        /**
         * Creates a new instance of [CliAIAgent] in structured output mode.
         *
         * @param transport The transport used to execute cli commands.
         * @param structure [Structure] defining the structured output format.
         * @param apiKey The API key for the Claude code.
         * @param binaryPath The path to the binary.
         * @param name The name of the cli strategy.
         * @param systemPrompt The system prompt for the agent.
         * @param llModel The LLM model to be used for the agent.
         * @param permissionMode The [ClaudePermissionMode] mode for the agent.
         * @param additionalFlags Additional flags to be passed to the claude cli.
         * @param workspace The workspace directory for the agent.
         * @param timeout The timeout duration for the agent.
         * @param id The unique identifier for the agent.
         * @param clock The clock used to calculate message timestamps.
         * @param generateRequest Lambda for generating the request for the agent.
         * @param installFeatures Lambda for installing additional features.
         */
        @JvmStatic
        @JvmOverloads
        public fun <Input, Output> claude(
            transport: CliTransport,
            structure: Structure<Output, LLMParams.Schema.JSON>,
            apiKey: String? = null,
            binaryPath: String? = null,
            name: String? = null,
            systemPrompt: String? = null,
            llModel: LLModel? = null,
            permissionMode: ClaudePermissionMode? = null,
            additionalFlags: List<String> = emptyList(),
            workspace: String = ".",
            timeout: Duration? = null,
            id: String? = null,
            clock: KoogClock = KoogClock.System,
            generateRequest: CliConfig.GenerateRequest<Input> = { it.toString() },
            installFeatures: FeatureContext.() -> Unit = {},
        ): CliAIAgent<Input, CliAgentStructuredResponse<Output>> {
            val strategy = AIAgentCliStrategy(
                config = ClaudeCliStructuredConfig(
                    transport = transport,
                    apiKey = apiKey,
                    binaryPath = binaryPath,
                    name = name,
                    structure = structure,
                    permissionMode = permissionMode,
                    additionalFlags = additionalFlags,
                    workspace = workspace,
                    timeout = timeout,
                    generateRequest = generateRequest
                )
            )

            return CliAIAgent(
                systemPrompt = systemPrompt,
                llModel = llModel,
                strategy = strategy,
                id = id,
                clock = clock,
                installFeatures = installFeatures
            )
        }

        /**
         * Creates a new instance of [CliAIAgent] in structured output mode.
         *
         * @param transport The transport used to execute cli commands.
         * @param apiKey The API key for the Claude code.
         * @param binaryPath The path to the binary.
         * @param name The name of the cli strategy.
         * @param Output The type of the structured output.
         * @param systemPrompt The system prompt for the agent.
         * @param llModel The LLM model to be used for the agent.
         * @param permissionMode The [ClaudePermissionMode] mode for the agent.
         * @param additionalFlags Additional flags to be passed to the claude cli.
         * @param workspace The workspace directory for the agent.
         * @param timeout The timeout duration for the agent.
         * @param id The unique identifier for the agent.
         * @param clock The clock used to calculate message timestamps.
         * @param generateRequest Lambda for generating the request for the agent.
         * @param installFeatures Lambda for installing additional features.
         */
        @JvmName("claudeStructured")
        public inline fun <Input, reified Output> claude(
            transport: CliTransport,
            apiKey: String? = null,
            binaryPath: String? = null,
            name: String? = null,
            systemPrompt: String? = null,
            llModel: LLModel? = null,
            permissionMode: ClaudePermissionMode? = null,
            additionalFlags: List<String> = emptyList(),
            workspace: String = ".",
            timeout: Duration? = null,
            id: String? = null,
            clock: KoogClock = KoogClock.System,
            generateRequest: CliConfig.GenerateRequest<Input> = { it.toString() },
            noinline installFeatures: FeatureContext.() -> Unit = {}
        ): CliAIAgent<Input, CliAgentStructuredResponse<Output>> = claude(
            transport = transport,
            apiKey = apiKey,
            binaryPath = binaryPath,
            name = name,
            structure = JsonStructure.create(serializer = serializer<Output>()),
            systemPrompt = systemPrompt,
            llModel = llModel,
            permissionMode = permissionMode,
            additionalFlags = additionalFlags,
            workspace = workspace,
            timeout = timeout,
            id = id,
            clock = clock,
            generateRequest = generateRequest,
            installFeatures = installFeatures
        )

        /**
         * Creates a new instance of [CliAIAgent] in structured output mode.
         *
         * @param transport The transport used to execute cli commands.
         * @param Output The type of the structured output.
         * @param apiKey The API key for the Claude code.
         * @param binaryPath The path to the binary.
         * @param name The name of the cli strategy.
         * @param systemPrompt The system prompt for the agent.
         * @param llModel The LLM model to be used for the agent.
         * @param permissionMode The [ClaudePermissionMode] mode for the agent.
         * @param additionalFlags Additional flags to be passed to the claude cli.
         * @param workspace The workspace directory for the agent.
         * @param timeout The timeout duration for the agent.
         * @param id The unique identifier for the agent.
         * @param clock The clock used to calculate message timestamps.
         * @param generateRequest Lambda for generating the request for the agent.
         * @param installFeatures Lambda for installing additional features.
         */
        @JvmStatic
        @JvmOverloads
        @OptIn(InternalSerializationApi::class)
        public fun <Input, Output : Any> claude(
            transport: CliTransport,
            outputClass: KClass<Output>,
            apiKey: String? = null,
            binaryPath: String? = null,
            name: String? = null,
            systemPrompt: String? = null,
            llModel: LLModel? = null,
            permissionMode: ClaudePermissionMode? = null,
            additionalFlags: List<String> = emptyList(),
            workspace: String = ".",
            timeout: Duration? = null,
            id: String? = null,
            clock: KoogClock = KoogClock.System,
            generateRequest: CliConfig.GenerateRequest<Input> = { it.toString() },
            installFeatures: FeatureContext.() -> Unit = {},
        ): CliAIAgent<Input, CliAgentStructuredResponse<Output>> {
            val serializer = outputClass.serializer()
            return claude(
                transport = transport,
                apiKey = apiKey,
                binaryPath = binaryPath,
                name = name,
                structure = JsonStructure.create(serializer = serializer),
                systemPrompt = systemPrompt,
                llModel = llModel,
                permissionMode = permissionMode,
                additionalFlags = additionalFlags,
                workspace = workspace,
                timeout = timeout,
                id = id,
                clock = clock,
                generateRequest = generateRequest,
                installFeatures = installFeatures
            )
        }

        // codex constructors

        /**
         * Creates a new instance of [CliAIAgent] using Codex.
         *
         * @param transport The transport used to execute cli commands.
         * @param apiKey The API key for the Codex code.
         * @param binaryPath The path to the binary.
         * @param name The name of the cli strategy.
         * @param systemPrompt The system prompt for the agent.
         * @param llModel The LLM model to be used for the agent.
         * @param sandbox The [CodexSandboxMode] for the agent.
         * @param askForApproval The [CodexApprovalPolicy] for the agent.
         * @param additionalFlags Additional flags to be passed to the codex cli.
         * @param workspace The workspace directory for the agent.
         * @param timeout The timeout duration for the agent.
         * @param id The unique identifier for the agent.
         * @param clock The clock used to calculate message timestamps.
         * @param installFeatures Lambda for installing additional features.
         */
        @JvmStatic
        @JvmOverloads
        public fun codex(
            transport: CliTransport,
            apiKey: String? = null,
            binaryPath: String? = null,
            name: String? = null,
            systemPrompt: String? = null,
            llModel: LLModel? = null,
            sandbox: CodexSandboxMode? = null,
            askForApproval: CodexApprovalPolicy? = null,
            additionalFlags: List<String> = emptyList(),
            workspace: String = ".",
            timeout: Duration? = null,
            id: String? = null,
            clock: KoogClock = KoogClock.System,
            installFeatures: FeatureContext.() -> Unit = {},
        ): CliAIAgent<String, CliAIAgentResponse> = codex(
            transport = transport,
            apiKey = apiKey,
            binaryPath = binaryPath,
            name = name,
            systemPrompt = systemPrompt,
            llModel = llModel,
            sandbox = sandbox,
            askForApproval = askForApproval,
            additionalFlags = additionalFlags,
            workspace = workspace,
            timeout = timeout,
            id = id,
            clock = clock,
            generateRequest = { it },
            installFeatures = installFeatures
        )

        /**
         * Creates a new instance of [CliAIAgent] using Codex.
         *
         * @param transport The transport used to execute cli commands.
         * @param apiKey The API key for the Codex code.
         * @param binaryPath The path to the binary.
         * @param name The name of the cli strategy.
         * @param systemPrompt The system prompt for the agent.
         * @param llModel The LLM model to be used for the agent.
         * @param sandbox The [CodexSandboxMode] for the agent.
         * @param askForApproval The [CodexApprovalPolicy] for the agent.
         * @param additionalFlags Additional flags to be passed to the codex cli.
         * @param workspace The workspace directory for the agent.
         * @param timeout The timeout duration for the agent.
         * @param id The unique identifier for the agent.
         * @param clock The clock used to calculate message timestamps.
         * @param generateRequest Lambda for generating the request for the agent.
         * @param installFeatures Lambda for installing additional features.
         */
        @JvmStatic
        @JvmOverloads
        public fun <Input> codex(
            transport: CliTransport,
            apiKey: String? = null,
            binaryPath: String? = null,
            name: String? = null,
            systemPrompt: String? = null,
            llModel: LLModel? = null,
            sandbox: CodexSandboxMode? = null,
            askForApproval: CodexApprovalPolicy? = null,
            additionalFlags: List<String> = emptyList(),
            workspace: String = ".",
            timeout: Duration? = null,
            id: String? = null,
            clock: KoogClock = KoogClock.System,
            generateRequest: CliConfig.GenerateRequest<Input>,
            installFeatures: FeatureContext.() -> Unit = {}
        ): CliAIAgent<Input, CliAIAgentResponse> {
            val strategy = AIAgentCliStrategy(
                config = CodexCliConfig(
                    transport = transport,
                    apiKey = apiKey,
                    binaryPath = binaryPath,
                    name = name,
                    sandbox = sandbox,
                    askForApproval = askForApproval,
                    additionalFlags = additionalFlags,
                    workspace = workspace,
                    timeout = timeout,
                    generateRequest = generateRequest
                )
            )

            return CliAIAgent(
                systemPrompt = systemPrompt,
                llModel = llModel,
                strategy = strategy,
                id = id,
                clock = clock,
                installFeatures = installFeatures
            )
        }

        /**
         * Creates a new instance of [CliAIAgent] using provided configuration of the cli.
         *
         * @param cliConfig The configuration for the cli.
         * @param systemPrompt The system prompt for the agent.
         * @param llModel The LLM model to be used for the agent.
         * @param id The unique identifier for the agent.
         * @param clock The clock used to calculate message timestamps.
         * @param installFeatures Lambda for installing additional features.
         */
        public fun <Input, Output> withCliConfig(
            cliConfig: CliConfig<Input, Output>,
            systemPrompt: String? = null,
            llModel: LLModel? = null,
            id: String? = null,
            clock: KoogClock = KoogClock.System,
            installFeatures: FeatureContext.() -> Unit = {}
        ): CliAIAgent<Input, Output> = CliAIAgent(
            systemPrompt = systemPrompt,
            llModel = llModel,
            strategy = AIAgentCliStrategy(config = cliConfig),
            id = id,
            clock = clock,
            installFeatures = installFeatures
        )

        /**
         * Creates a new [CliAgentBuilder].
         */
        @JvmStatic
        public fun builder(transport: CliTransport): CliAgentBuilder = CliAgentBuilder(transport)

        private val logger = KotlinLogging.logger {}

        private val DummyLLMProvider: LLMProvider = object : LLMProvider("None", "Provider is not set") {}

        internal val DummyModel: LLModel = LLModel(
            provider = DummyLLMProvider,
            id = "model_not_set"
        )
    }

    /**
     * Cli agents like claude and codex have a built-in layer of llm requests and do not require koog's prompt executor.
     * This implementation is needed to initialize the [AIAgentCliContext] and cannot be used for actual execution.
     */
    private object DummyPromptExecutor : PromptExecutor() {
        override suspend fun execute(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>
        ): Message.Assistant {
            throw NotImplementedError("DummyPromptExecutor does not support execution")
        }

        override fun executeStreaming(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>
        ): Flow<StreamFrame> {
            throw NotImplementedError("DummyPromptExecutor does not support execution")
        }

        override suspend fun moderate(
            prompt: Prompt,
            model: LLModel
        ): ModerationResult {
            throw NotImplementedError("DummyPromptExecutor does not support execution")
        }

        override fun close() {}
    }
}

/**
 * Generates a node that runs the [AIAgent].
 *
 * @param name Optional name of the node.
 * @param generateSessionId Lambda defining the sessionId based on the sessionId of the outer agent.
 * Provide sessionId = { null } to generate a random sessionId on every execution of this node.
 * Defaults to sessionId identical to the sessionId of the outer agent.
 */
public inline fun <reified Input, reified Output> AIAgent<Input, Output>.asNode(
    name: String? = null,
    noinline generateSessionId: (String) -> String? = { it }
): AIAgentNodeDelegate<Input, Output> {
    return AIAgentNodeDelegate(
        name = name,
        inputType = typeToken<Input>(),
        outputType = typeToken<Output>(),
        execute = { input -> run(input, generateSessionId(runId)) }
    )
}
