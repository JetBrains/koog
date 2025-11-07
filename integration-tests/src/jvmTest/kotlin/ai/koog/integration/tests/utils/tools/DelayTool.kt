package ai.koog.integration.tests.utils.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

const val DELAY_MILLIS = 500L

@Serializable
data class DelayArgs(
    @property:LLMDescription("The number of milliseconds to delay")
    val milliseconds: Int = DELAY_MILLIS.toInt()
)

object DelayTool : SimpleTool<DelayArgs>() {
    override val argsSerializer = DelayArgs.serializer()

    override val name = "delay"
    override val description = "A tool that introduces a delay to simulate a time-consuming operation."

    override suspend fun doExecute(args: DelayArgs): String {
        delay(args.milliseconds.toLong())
        return "Delayed for ${args.milliseconds} milliseconds"
    }
}
