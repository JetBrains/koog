package ai.koog.agents.cli

import ai.koog.agents.cli.transport.CliEvent
import ai.koog.agents.cli.transport.CliNotFoundException
import ai.koog.agents.cli.transport.CliUnavailable
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.prompt.message.Message
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList

/**
 * Strategy for executing AI agents using a command-line interface (CLI).
 */
public class AIAgentCliStrategy<Input, Output> internal constructor(
    private val config: CliConfig<Input, Output>
) : AIAgentStrategy<Input, Output, AIAgentCliContext> {
    override val name: String = config.name

    override suspend fun execute(context: AIAgentCliContext, input: Input): Output {
        checkAvailability()

        val model = context.config.model
        val systemMessages = context.config.prompt.messages.filterIsInstance<Message.System>()

        val command = listOf(config.binaryPath) + config.flags(model, systemMessages) + config.generateRequest(input)
        logger.info { "Executing CLI command: ${command.joinToString(" ")}" }

        val events = config.transport.execute(
            command = command,
            workspace = config.workspace,
            env = config.env,
            timeout = config.timeout
        )
            .onEach { logEvent(it) }
            .toList()

        val result = config.extractOutput(events, logger)

        return result
    }

    private suspend fun checkAvailability() {
        val availability = config.transport.checkAvailability(config.binaryPath, config.workspace, config.timeout)
        if (availability is CliUnavailable) {
            throw CliNotFoundException(
                "CLI '${config.binaryPath}' is not available: ${availability.reason}",
                availability.cause
            )
        }
    }

    /**
     * Json utils for cli agent implementations
     */
    public companion object {
        private val logger = KotlinLogging.logger {}

        /**
         * Logs the cli agent events
         */
        private fun logEvent(event: CliEvent) {
            logger.trace {
                when (event) {
                    is CliEvent.Stdout -> "[STDOUT] ${event.content}"
                    is CliEvent.Stderr -> "[STDERR] ${event.content}"
                    is CliEvent.Exit -> "Agent Exited (code: ${event.code})"
                    is CliEvent.Failed -> "Agent Failed: ${event.message}"
                }
            }
        }
    }
}
