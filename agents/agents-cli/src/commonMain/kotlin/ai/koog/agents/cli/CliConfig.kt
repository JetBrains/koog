package ai.koog.agents.cli

import ai.koog.agents.cli.transport.CliEvent
import ai.koog.agents.cli.transport.CliTransport
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import io.github.oshai.kotlinlogging.KLogger
import kotlin.time.Duration

/**
 * Configuration for [AIAgentCliStrategy].
 */
public interface CliConfig<Input, Output> {
    /** CLI transport for executing commands. */
    public val transport: CliTransport

    /** Name of the CLI tool. */
    public val name: String

    /** Path to the binary of the CLI tool. */
    public val binaryPath: String

    /** Working directory for command execution. */
    public val workspace: String

    /** Environment variables for command execution. */
    public val env: Map<String, String>

    /** Execution timeout. */
    public val timeout: Duration?

    /** Generates command-line flags based on LLM model and system messages. */
    public fun flags(model: LLModel, systemMessages: List<Message.System>): List<String>

    /** Generates the request string from context and input. */
    public fun generateRequest(input: Input): String

    /** Extracts the output from CLI event lines. */
    public fun extractOutput(events: List<CliEvent>, logger: KLogger): Output

    /**
     * Represents a function that generates a request string from context and input.
     */
    public fun interface GenerateRequest<Input> {
        /**
         * Generates a request string from context and input.
         */
        public fun generateRequest(input: Input): String
    }

    /**
     * Represents a function that extracts the output from CLI event lines.
     */
    public fun interface ExtractOutput<Output> {
        /**
         * Extracts the output from CLI event lines.
         */
        public fun extractOutput(events: List<CliEvent>, logger: KLogger): Output
    }
}
