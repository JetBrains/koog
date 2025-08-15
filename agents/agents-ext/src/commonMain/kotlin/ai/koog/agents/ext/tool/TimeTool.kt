package ai.koog.agents.ext.tool

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * A tool for retrieving the current time, with timezone and formatting options.
 *
 * This tool allows querying the current time, either in the system's default timezone or a timezone
 * specified via argument, e.g. `"UTC"`, `"Europe/Paris"`, `"+01:00"`.
 *
 * It provides an option to truncate the time precision to seconds.
 *
 * @param clock The clock used to retrieve the current time. Defaults to the system clock.
 * @param timezone The default timezone used when no timezone is specified in the arguments.
 * Defaults to the system's default timezone.
 * @param truncateToSeconds A flag to determine whether the time should be truncated to seconds. Defaults to `true`.
 */
public class TimeTool(
    private val clock: Clock = Clock.System,
    private val timezone: TimeZone = TimeZone.currentSystemDefault(),
    private val truncateToSeconds: Boolean = true,
) : SimpleTool<TimeTool.Args>() {

    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    init {
        logger.debug { "TimeTool initialized with timezone=$timezone" }
    }

    /**
     * Represents the arguments for the [TimeTool] tool.
     *
     * Examples: `"UTC"`, `"Europe/Paris"`, `"+01:00"`
     *
     * @property timezone A string representing a time zone as a zone name or offset,
     * required for tool execution.
     */
    @Serializable
    public data class Args(val timezone: String? = null) : ToolArgs

    /**
     * Executes the TimeTool functionality, determining and returning the current time in the specified timezone.
     * If a timezone is provided in the arguments, it attempts to use it. If the timezone is invalid, an error
     * message is returned. If no timezone is provided, the default timezone of the tool is used.
     *
     * @param args The arguments containing the optional timezone for determining the current time.
     * @return A string representing the current time and timezone, or an error message if the specified timezone is invalid.
     */
    override suspend fun doExecute(args: Args): String {
        val now = if (truncateToSeconds) {
            Instant.fromEpochSeconds(clock.now().epochSeconds)
        } else {
            clock.now()
        }

        val timezone = args.timezone?.let {
            try {
                TimeZone.of(it)
            } catch (e: IllegalArgumentException) {
                logger.debug(e) { "Invalid timezone parameter: $it" }
                return "Invalid timezone: $it. Please provide a valid timezone like 'Europe/Paris' or an offset like '+01:00'."
            }
        } ?: timezone

        val localDateTime = now.toLocalDateTime(timezone)
        logger.debug { "Returning current time: \"$localDateTime\" at timeZone: \"$timezone\"" }
        return "Current time: $localDateTime (Timezone: ${timezone.id})"
    }

    override val argsSerializer: KSerializer<Args> = Args.serializer()

    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = "time",
        description = "Service tool, used by the agent to get current time",
        requiredParameters = emptyList(),
        optionalParameters = listOf(
            ToolParameterDescriptor(
                name = "timezone",
                description = "TimeZone as name (Europe/Paris) or offset (+01:00) or null for default system timezone",
                type = ToolParameterType.String
            ),
        )
    )
}
