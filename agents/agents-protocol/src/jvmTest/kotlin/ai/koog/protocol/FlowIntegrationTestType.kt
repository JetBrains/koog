package ai.koog.protocol

import ai.koog.protocol.flow.toKoogFlow
import ai.koog.protocol.parser.FlowJsonConfigParser
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Disabled
import kotlin.test.Test

class FlowIntegrationTestType : FlowTestBase() {

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    @Disabled("Ignore the integration test on a real Prompt executor by default")
    @Test
    fun testRunKoogFlow_TaskVerify(): Unit = runTest {
        val jsonContent = readFlow("json/basic_task_flow.json")

        val parser = FlowJsonConfigParser()
        val config = parser.parse(jsonContent)

        val agentsString = config.agents.joinToString("\n") { agent -> " - ${agent.type}: ${agent.name}" }
        val transitionsString = config.transitions.joinToString("\n") { transition -> " - ${transition.from} -> ${transition.to}" }
        val toolsString = config.tools.joinToString("\n") { tool -> " - $tool" }

        logger.info {
            "Parsed flow json (" +
                "id: ${config.id}, " +
                "version: ${config.version}, " +
                "agents ${config.agents.size}, " +
                "transitions: ${config.transitions.size})\n" +
                "Agents:\n$agentsString\n" +
                "Transitions:\n$transitionsString\n" +
                "Tools:\n$toolsString\n"
        }

        logger.info { "Running the flow..." }
        val flow = config.toKoogFlow()

        val result = flow.run(null)
        logger.info { "Flow execution result: $result" }
    }

    @Test
    fun testRunRealFlow_Verify(): Unit = runBlocking {
        val jsonContent = readFlow("json/real_flow.json")

        val parser = FlowJsonConfigParser()
        val config = parser.parse(jsonContent)

        val agentsString = config.agents.joinToString("\n") { agent -> " - ${agent.type}: ${agent.name}" }
        val transitionsString =
            config.transitions.joinToString("\n") { transition -> " - ${transition.from} -> ${transition.to}" }

        val toolsString = config.tools.joinToString("\n") { tool -> " - $tool" }

        logger.info {
            "Parsed flow json (" +
                "id: ${config.id}, " +
                "version: ${config.version}, " +
                "agents ${config.agents.size}, " +
                "transitions: ${config.transitions.size})\n" +
                "Agents:\n$agentsString\n" +
                "Transitions:\n$transitionsString\n" +
                "Tools:\n$toolsString\n"
        }

        logger.info { "Running the flow..." }
        val flow = config.toKoogFlow()

        val result = flow.run(null)
        logger.info { "Flow execution result: $result" }
    }
}
