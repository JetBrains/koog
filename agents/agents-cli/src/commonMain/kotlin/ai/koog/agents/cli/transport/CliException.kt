package ai.koog.agents.cli.transport

import kotlin.time.Duration

/**
 * Base class for cli-agent-related exceptions.
 */
public open class CliException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Exception indicating that a CLI agent binary was not found.
 */
public class CliNotFoundException(message: String, cause: Throwable? = null) : CliException(message, cause)

/**
 * Exception indicating that a CLI agent run timed out.
 *
 * @property timeout The duration after which the execution timed out.
 */
public class CliTimeoutException(public val timeout: Duration) :
    CliException("Cli execution timed out after ${timeout.inWholeSeconds} seconds")
