package ai.koog.agents.cli.transport

/**
 * Events reporting signals from a running cli tool.
 */
public sealed interface CliEvent {

    /**
     * Regular line of text output from the CLI.
     */
    public sealed interface Line : CliEvent {
        /**
         * Content of the line.
         */
        public val content: String
    }

    /**
     * Reports a line from stdout
     */
    public data class Stdout(override val content: String) : Line

    /**
     * Reports a line from stderr
     */
    public data class Stderr(override val content: String) : Line

    /**
     * Reports the exit status of the CLI execution.
     */
    public data class Exit(public val code: Int) : CliEvent

    /**
     * Reports a failure during cli execution.
     */
    public data class Failed(public val message: String) : CliEvent
}
