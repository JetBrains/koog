package ai.koog.agents.core.system.feature

import ai.koog.agents.core.annotation.ExperimentalAgentsApi
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.feature.debugger.Debugger
import ai.koog.agents.core.feature.remote.server.config.DefaultServerConnectionConfig
import ai.koog.agents.core.system.feature.DebuggerTestAPI.defaultClientServerTimeout
import ai.koog.agents.core.system.feature.DebuggerTestAPI.runAgentConnectionWaitConfigThroughSystemVariablesTest
import ai.koog.agents.core.system.feature.DebuggerTestAPI.runAgentPortConfigThroughSystemVariablesTest
import ai.koog.agents.core.system.getEnvironmentVariableOrNull
import ai.koog.agents.core.system.getVMOptionOrNull
import ai.koog.agents.core.system.mock.createAgent
import ai.koog.agents.testing.network.NetUtil
import ai.koog.agents.testing.network.NetUtil.findAvailablePort
import ai.koog.utils.io.use
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.Isolated
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.measureTime
import kotlin.time.toDuration

// System Properties set inside this test class affects other tests
// Isolate the environment by @Isolated annotation for these tests and make sure they are running without the parallelism.
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class DebuggerConfigTest {

    @AfterEach
    fun cleanup() {
        // Clean up system properties user in tests to not affect other test runs
        @OptIn(ExperimentalAgentsApi::class)
        System.clearProperty(Debugger.KOOG_DEBUGGER_PORT_VM_OPTION)
        @OptIn(ExperimentalAgentsApi::class)
        System.clearProperty(Debugger.KOOG_DEBUGGER_WAIT_CONNECTION_TIMEOUT_MS_VM_OPTION)
    }

    //region Port

    @Test
    @Disabled(
        """
        '${@OptIn(ExperimentalAgentsApi::class) Debugger.KOOG_DEBUGGER_PORT_ENV_VAR}' environment variable need to be set for a particular test via test framework.
        Currently, test framework that is used for Koog tests does not have ability to set env variables.
        Setting env variable in Gradle task does not work either, because there are tests that verify both
        cases when env variable is set and when it is not set.
        Disable test for now. Need to be enabled when we can set env variables in tests.
    """
    )
    fun `test read port from env variable`() = runBlocking {
        @OptIn(ExperimentalAgentsApi::class)
        val portEnvVar = getEnvironmentVariableOrNull(Debugger.KOOG_DEBUGGER_PORT_ENV_VAR)

        @OptIn(ExperimentalAgentsApi::class)
        assertNotNull(portEnvVar, "'${Debugger.KOOG_DEBUGGER_PORT_ENV_VAR}' env variable is not set")

        runAgentPortConfigThroughSystemVariablesTest(port = portEnvVar.toInt())
    }

    @Test
    @Disabled("Flaky, see #1124")
    fun `test read port from vm option`() = runBlocking {
        // Set VM option
        val port = 56712
        val portVmOptionName = @OptIn(ExperimentalAgentsApi::class) Debugger.KOOG_DEBUGGER_PORT_VM_OPTION
        val portEnvVarName = @OptIn(ExperimentalAgentsApi::class) Debugger.KOOG_DEBUGGER_PORT_ENV_VAR
        System.setProperty(portVmOptionName, port.toString())

        val portEnvVar = getEnvironmentVariableOrNull(name = portEnvVarName)
        assertNull(
            portEnvVar,
            "Expected '$portEnvVarName' env variable is not set, but it is defined with value: <$portEnvVar>"
        )

        val portVMOption = getVMOptionOrNull(name = portVmOptionName)
        assertNotNull(
            portVMOption,
            "Expected '$portVmOptionName' VM option is not set"
        )

        runAgentPortConfigThroughSystemVariablesTest(port = portVMOption.toInt())
    }

    @Test
    @Disabled("Flaky, see #1124")
    fun `test read default port when not set by property or env variable or vm option`() = runBlocking {
        val portVmOptionName = @OptIn(ExperimentalAgentsApi::class) Debugger.KOOG_DEBUGGER_PORT_VM_OPTION
        val portEnvVarName = @OptIn(ExperimentalAgentsApi::class) Debugger.KOOG_DEBUGGER_PORT_ENV_VAR

        val portEnvVar = getEnvironmentVariableOrNull(portEnvVarName)
        assertNull(portEnvVar, "Expected '$portEnvVarName' env variable is not set, but it exists with value: $portEnvVar")

        val portVMOption = getEnvironmentVariableOrNull(portEnvVarName)
        assertNull(portVMOption, "Expected '$portVmOptionName' VM option is not set, but it exists with value: $portVMOption")

        // Check default port available
        val isDefaultPortAvailable = NetUtil.isPortAvailable(DefaultServerConnectionConfig.DEFAULT_PORT)
        assertTrue(
            isDefaultPortAvailable,
            "Default port ${DefaultServerConnectionConfig.DEFAULT_PORT} is not available"
        )

        runAgentPortConfigThroughSystemVariablesTest(port = DefaultServerConnectionConfig.DEFAULT_PORT)
    }

    //endregion Port

    //region Client Connection Wait Timeout

    @Test
    fun `test client connection waiting timeout is set by property`() = runBlocking {
        // Agent Config
        val agentId = "test-agent-id"
        val strategyName = "test-strategy"
        val userPrompt = "Call the dummy tool with argument: test"

        // Test Data
        val port = findAvailablePort()
        val clientConnectionWaitTimeout = 1.seconds
        var actualAgentRunTime = Duration.ZERO

        // Server
        // The server will read the env variable or VM option to get a port value.
        val serverJob = launch {
            val strategy = strategy<String, String>(strategyName) {
                edge(nodeStart forwardTo nodeFinish)
            }

            createAgent(
                agentId = agentId,
                strategy = strategy,
                userPrompt = userPrompt,
            ) {
                @OptIn(ExperimentalAgentsApi::class)
                install(Debugger) {
                    setPort(port)
                    // Set connection awaiting timeout
                    setAwaitInitialConnectionTimeout(clientConnectionWaitTimeout)
                }
            }.use { agent ->
                actualAgentRunTime = measureTime {
                    withTimeoutOrNull(defaultClientServerTimeout) {
                        agent.run(userPrompt)
                    }
                }
            }
        }

        val isFinishedOrNull = withTimeoutOrNull(defaultClientServerTimeout) {
            serverJob.join()
        }

        assertNotNull(isFinishedOrNull, "Client or server did not finish in time")

        assertTrue(
            actualAgentRunTime in clientConnectionWaitTimeout..<defaultClientServerTimeout,
            "Expected actual agent run time is over <$clientConnectionWaitTimeout>, but got: <$actualAgentRunTime>"
        )
    }

    @Disabled(
        """
        '${@OptIn(ExperimentalAgentsApi::class) Debugger.KOOG_DEBUGGER_WAIT_CONNECTION_MS_ENV_VAR}' environment variable need to be set for a particular test via test framework.
        Currently, test framework that is used for Koog tests does not have ability to set env variables.
        Setting env variable in Gradle task does not work either, because there are tests that verify both 
        cases when env variable is set and when it is not set.
        Disable test for now. Need to be enabled when we can set env variables in tests.
    """
    )
    @Test
    fun `test client connection waiting timeout is set by env variable`() = runBlocking {
        val portWaitConnectionMsEnvVarName = @OptIn(ExperimentalAgentsApi::class) Debugger.KOOG_DEBUGGER_WAIT_CONNECTION_MS_ENV_VAR

        val connectionWaitTimeoutMsEnvVar = getEnvironmentVariableOrNull(portWaitConnectionMsEnvVarName)
        assertNotNull(connectionWaitTimeoutMsEnvVar, "'$portWaitConnectionMsEnvVarName' env variable is not set")

        runAgentConnectionWaitConfigThroughSystemVariablesTest(
            timeout = connectionWaitTimeoutMsEnvVar.toLong().toDuration(DurationUnit.MILLISECONDS)
        )
    }

    @Test
    fun `test client connection waiting timeout is set by vm option`() = runBlocking {
        val portWaitConnectionMsVmOptionName = @OptIn(ExperimentalAgentsApi::class) Debugger.KOOG_DEBUGGER_WAIT_CONNECTION_TIMEOUT_MS_VM_OPTION
        val portWaitConnectionMsEnvVarName = @OptIn(ExperimentalAgentsApi::class) Debugger.KOOG_DEBUGGER_WAIT_CONNECTION_MS_ENV_VAR

        // Set VM option
        val timeout = 1.seconds
        System.setProperty(portWaitConnectionMsVmOptionName, timeout.inWholeMilliseconds.toString())

        val connectionWaitTimeoutEnvVar = getEnvironmentVariableOrNull(name = portWaitConnectionMsEnvVarName)

        assertNull(
            connectionWaitTimeoutEnvVar,
            "Expected '$portWaitConnectionMsEnvVarName' env variable is not set, " +
                "but it is defined with value: <$connectionWaitTimeoutEnvVar>"
        )

        val connectionWaitTimeoutMsVMOption = getVMOptionOrNull(name = portWaitConnectionMsVmOptionName)

        assertNotNull(
            connectionWaitTimeoutMsVMOption,
            "Expected '$portWaitConnectionMsVmOptionName' VM option is not set"
        )

        runAgentConnectionWaitConfigThroughSystemVariablesTest(
            timeout = connectionWaitTimeoutMsVMOption.toLong().toDuration(DurationUnit.MILLISECONDS)
        )
    }

    //endregion Client Connection Wait Timeout

    //region Filter

    @Test
    fun `test filter is not allowed for debugger feature`() = runBlocking {
        val strategy = strategy<String, String>("test-strategy") {
            nodeStart then nodeFinish
        }

        val throwable = assertFailsWith<UnsupportedOperationException> {
            createAgent(strategy = strategy) {
                @OptIn(ExperimentalAgentsApi::class)
                install(Debugger) {
                    // Try to filter out all events. OpenTelemetryConfig should ignore this filter
                    setEventFilter { false }
                }
            }
        }

        assertEquals(
            "Events filtering is not allowed for the Debugger feature.",
            throwable.message
        )
    }

    //endregion Filter
}
