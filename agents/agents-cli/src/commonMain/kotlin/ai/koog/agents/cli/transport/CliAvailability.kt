package ai.koog.agents.cli.transport

/**
 * Represents the availability of a Cli tool.
 */
public sealed interface CliAvailability

/**
 * Indicates that the tool is available.
 */
public object CliAvailable : CliAvailability

/**
 * Indicates that the tool is unavailable.
 *
 * @property reason A description of why the tool is unavailable.
 * @property cause The underlying exception that caused the unavailability, if any.
 */
public class CliUnavailable(
    public val reason: String? = null,
    public val cause: Throwable? = null,
) : CliAvailability
