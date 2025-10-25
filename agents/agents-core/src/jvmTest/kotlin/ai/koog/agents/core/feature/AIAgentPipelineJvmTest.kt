package ai.koog.agents.core.feature

import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.GraphAIAgent.FeatureContext
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeDoNothing
import ai.koog.agents.core.feature.config.FeatureSystemVariables
import ai.koog.agents.core.feature.debugger.Debugger
import ai.koog.agents.core.feature.pipeline.AIAgentGraphPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentPipeline
import ai.koog.agents.core.system.mock.testClock
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.testing.network.NetUtil
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.utils.io.use
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.Isolated
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// System Properties set inside this test class affects the general agent logic.
// It causes the other tests, running in parallel, to be affected by this property.
// Isolate the environment by @Isolated annotation for these tests and make sure they are running without the parallelism.
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class AIAgentPipelineJvmTest {

    companion object {
        private val testTimeout = 10.seconds
    }

    @AfterEach
    fun cleanup() {
        System.clearProperty(FeatureSystemVariables.KOOG_FEATURES_VM_OPTION_NAME)
        System.clearProperty(Debugger.KOOG_DEBUGGER_PORT_VM_OPTION)
        System.clearProperty(Debugger.KOOG_DEBUGGER_WAIT_CONNECTION_TIMEOUT_MS_VM_OPTION)
    }

    @EnabledIfEnvironmentVariable(named = FeatureSystemVariables.KOOG_FEATURES_ENV_VAR_NAME, matches = ".+")
    @EnabledIfEnvironmentVariable(named = Debugger.KOOG_DEBUGGER_PORT_ENV_VAR, matches = ".+")
    @EnabledIfEnvironmentVariable(named = Debugger.KOOG_DEBUGGER_WAIT_CONNECTION_MS_ENV_VAR, matches = ".+")
    @Test
    fun `test known system feature in config set by env variable`() = runTest(timeout = testTimeout) {
        val expectedPort = NetUtil.findAvailablePort()
        val expectedWaitConnectionTimeout = 1L

        // Check env variables are set for test
        val actualFeaturesEnvVariable = System.getenv(FeatureSystemVariables.KOOG_FEATURES_ENV_VAR_NAME)
        val actualDebuggerPortEnvVariable = System.getenv(Debugger.KOOG_DEBUGGER_PORT_ENV_VAR)
        val actualDebuggerWaitConnectionTimeoutEnvVariable = System.getenv(Debugger.KOOG_DEBUGGER_WAIT_CONNECTION_MS_ENV_VAR)
        assertNotNull(
            actualFeaturesEnvVariable,
            "'${FeatureSystemVariables.KOOG_FEATURES_ENV_VAR_NAME}' env variable is not set for test"
        )
        assertNotNull(
            actualDebuggerPortEnvVariable,
            "'${Debugger.KOOG_DEBUGGER_PORT_ENV_VAR}' env variable is not set for test"
        )
        assertNotNull(
            actualDebuggerWaitConnectionTimeoutEnvVariable,
            "'${Debugger.KOOG_DEBUGGER_WAIT_CONNECTION_MS_ENV_VAR}' env variable is not set for test"
        )

        // Run prepare features logic
        AIAgentGraphPipeline(clock = testClock).use { pipeline ->
            pipeline.prepareFeatures()

            val actualFeatures = pipeline.features.toList()
            val expectedFeatures = listOf(
                Debugger.key
            )

            assertEquals(expectedFeatures.size, actualFeatures.size)
            assertContentEquals(expectedFeatures, actualFeatures)

            // Check Debugger feature parameters
            val actualFeature = pipeline.feature(Debugger::class, Debugger)

            assertNotNull(actualFeature)
            assertEquals(expectedPort, actualFeature.port)
            assertEquals(expectedWaitConnectionTimeout.milliseconds, actualFeature.awaitInitialConnectionTimeout)
        }
    }

    @Test
    fun `test known system feature in config set by vm option`() = runTest(timeout = testTimeout) {
        val expectedPort = NetUtil.findAvailablePort()
        val expectedWaitConnectionTimeout = 1L

        // Set System properties for test
        System.setProperty(FeatureSystemVariables.KOOG_FEATURES_VM_OPTION_NAME, Debugger.key.name)
        System.setProperty(Debugger.KOOG_DEBUGGER_PORT_VM_OPTION, "$expectedPort")
        System.setProperty(Debugger.KOOG_DEBUGGER_WAIT_CONNECTION_TIMEOUT_MS_VM_OPTION, "$expectedWaitConnectionTimeout")

        // Run prepare features logic
        AIAgentGraphPipeline(clock = testClock).use { pipeline ->
            pipeline.prepareFeatures()

            val actualFeatures = pipeline.features.toList()
            val expectedFeatures = listOf(
                Debugger.key
            )

            assertEquals(expectedFeatures.size, actualFeatures.size)
            assertContentEquals(expectedFeatures, actualFeatures)

            // Check Debugger feature parameters
            val actualFeature = pipeline.feature(Debugger::class, Debugger)

            assertNotNull(actualFeature)
            assertEquals(expectedPort, actualFeature.port)
            assertEquals(expectedWaitConnectionTimeout.milliseconds, actualFeature.awaitInitialConnectionTimeout)
        }
    }

    @Test
    fun `test known system feature is skipped if already installed in agent`() = runTest(timeout = testTimeout) {
        val expectedSystemPort = NetUtil.findAvailablePort()
        val expectedSystemWaitConnectionTimeout = 2L

        val expectedUserPort = NetUtil.findAvailablePort()
        val expectedUserWaitConnectionTimeout = 1L

        assertNotEquals(
            expectedSystemPort,
            expectedUserPort,
            "System port and user port must be different"
        )

        // Set System properties for test
        System.setProperty(FeatureSystemVariables.KOOG_FEATURES_VM_OPTION_NAME, Debugger.key.name)
        System.setProperty(Debugger.KOOG_DEBUGGER_PORT_VM_OPTION, "$expectedSystemPort")
        System.setProperty(Debugger.KOOG_DEBUGGER_WAIT_CONNECTION_TIMEOUT_MS_VM_OPTION, "$expectedSystemWaitConnectionTimeout")

        var agentPipeline: AIAgentGraphPipeline? = null
        createAgent {
            install(Debugger) {
                setPort(expectedUserPort)
                setAwaitInitialConnectionTimeout(expectedUserWaitConnectionTimeout.milliseconds)
            }
        }.use { agent ->
            agentPipeline = agent.exposedPipeline
            // Run agent to make sure the prepareFeatures() api is called
            agent.run("test")
        }

        // Assert pipeline features
        val actualPipeline = assertNotNull(agentPipeline, "Agent pipeline is not set")

        val actualFeatures = actualPipeline.features.toList()
        val expectedFeatures = listOf(
            Debugger.key
        )

        assertEquals(expectedFeatures.size, actualFeatures.size)
        assertContentEquals(expectedFeatures, actualFeatures)

        // Assert Debugger feature parameters
        val actualFeature = actualPipeline.feature(Debugger::class, Debugger)

        assertNotNull(actualFeature)
        assertEquals(expectedUserPort, actualFeature.port)
        assertEquals(expectedUserWaitConnectionTimeout.milliseconds, actualFeature.awaitInitialConnectionTimeout)
    }

    @Test
    fun `test unknown system feature name in config is ignored`() = runTest(timeout = testTimeout) {
        System.setProperty(FeatureSystemVariables.KOOG_FEATURES_VM_OPTION_NAME, "unknown-feature")

        AIAgentGraphPipeline(clock = testClock).use { pipeline ->
            pipeline.prepareFeatures()

            val actualFeatures = pipeline.features.toList()
            assertEquals(0, actualFeatures.size)
        }
    }

    @Test
    fun `test known and unknown system features in config`() = runTest(timeout = testTimeout) {
        // Set System properties for test
        System.setProperty(
            FeatureSystemVariables.KOOG_FEATURES_VM_OPTION_NAME,
            "${Debugger.key.name},unknown-feature"
        )

        // Note: this property is to ignore the default await for connection timeout for Debugger feature
        System.setProperty(Debugger.KOOG_DEBUGGER_WAIT_CONNECTION_TIMEOUT_MS_VM_OPTION, "1")

        // Run prepare features logic
        AIAgentGraphPipeline(clock = testClock).use { pipeline ->
            pipeline.prepareFeatures()

            val actualFeatures = pipeline.features.toList()
            val expectedFeatures = listOf(
                Debugger.key
            )

            assertEquals(expectedFeatures.size, actualFeatures.size)
            assertContentEquals(expectedFeatures, actualFeatures)

            // Check Debugger feature is installed
            val actualFeature = pipeline.feature(Debugger::class, Debugger)
            assertNotNull(actualFeature)
        }
    }

    @Test
    fun `test duplicate system features provided in config`() = runTest(timeout = testTimeout) {
        // Set System properties for test
        System.setProperty(
            FeatureSystemVariables.KOOG_FEATURES_VM_OPTION_NAME,
            "${Debugger.key.name},${Debugger.key.name}"
        )

        // Note: this property is to ignore the default await for connection timeout for Debugger feature
        System.setProperty(Debugger.KOOG_DEBUGGER_WAIT_CONNECTION_TIMEOUT_MS_VM_OPTION, "1")

        // Run prepare features logic
        AIAgentGraphPipeline(clock = testClock).use { pipeline ->
            pipeline.prepareFeatures()

            val actualFeatures = pipeline.features.toList()
            val expectedFeatures = listOf(
                Debugger.key
            )

            assertEquals(expectedFeatures.size, actualFeatures.size)
            assertContentEquals(expectedFeatures, actualFeatures)

            // Check Debugger feature is installed
            val actualFeature = pipeline.feature(Debugger::class, Debugger)
            assertNotNull(actualFeature)
        }
    }

    //region Private Methods

    /**
     * Creates an instance of `TestAIAgent` with exposed pipeline instance for testing.
     *
     * @return A configured instance of `TestAIAgent`.
     */
    private fun createAgent(
        id: String? = null,
        strategy: AIAgentGraphStrategy<String, String>? = null,
        promptExecutor: PromptExecutor? = null,
        installFeatures: FeatureContext.() -> Unit = {}
    ): TestAIAgent {
        val agentConfig = AIAgentConfig(
            prompt = prompt("test", clock = testClock) {
                system("Test system message")
                user("Test user message")
                assistant("Test assistant response")
            },
            model = OpenAIModels.Chat.GPT4o,
            maxAgentIterations = 10
        )

        val strategy: AIAgentGraphStrategy<String, String> = strategy ?: strategy<String, String>("test-interceptors-strategy") {
            val nodeDoNothing by nodeDoNothing<String>("node-do-nothing")

            edge(nodeStart forwardTo nodeDoNothing)
            edge(nodeDoNothing forwardTo nodeFinish)
        }

        return TestAIAgent(
            id = id ?: "test-agent-id",
            promptExecutor = promptExecutor ?: getMockExecutor(clock = testClock) { },
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry { },
            installFeatures = installFeatures,
        )
    }

    //endregion Private Methods

    /**
     * Test agent wrapper for a [GraphAIAgent] with exposed pipeline instance for testing.
     */
    private class TestAIAgent(
        promptExecutor: PromptExecutor,
        agentConfig: AIAgentConfig,
        strategy: AIAgentGraphStrategy<String, String>,
        toolRegistry: ToolRegistry,
        id: String? = "test-agent-id",
        installFeatures: FeatureContext.() -> Unit = {}
    ) : GraphAIAgent<String, String>(
        inputType = typeOf<String>(),
        outputType = typeOf<String>(),
        promptExecutor = promptExecutor,
        agentConfig = agentConfig,
        strategy = strategy,
        toolRegistry = toolRegistry,
        id = id,
        installFeatures = installFeatures,
    ) {
        /**
         * Provides access to the underlying AI agent pipeline associated with a test agent.
         */
        val exposedPipeline: AIAgentGraphPipeline = this.pipeline
    }
}

/**
 * Closable extension for [AIAgentPipeline] to finalize feature stream providers from tests.
 */
private suspend fun AIAgentPipeline.use(block: suspend (AIAgentPipeline) -> Unit) {
    try {
        block(this)
    } finally {
        closeFeaturesStreamProviders()
    }
}
