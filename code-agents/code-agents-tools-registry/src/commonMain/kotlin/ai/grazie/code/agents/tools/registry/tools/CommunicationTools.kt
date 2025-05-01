package ai.grazie.code.agents.tools.registry.tools

import ai.grazie.code.agents.core.tools.*
import ai.grazie.code.agents.core.tools.serialization.ToolResultStringSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object CommunicationTools {
    /**
     * This is a special service tool that is always assumed to be present for some agents with chat.
     * The name is reserved and all descriptions would be ignored and won't affect how it is invoked,
     * since it's already configured on the strategy side.
     */
    abstract class TalkTool : SimpleTool<TalkTool.Args>() {
        @Serializable
        data class Args(
            val message: String,
            /**
             * Whether to show the message
             */
            @SerialName("do_print")
            val doPrint: Boolean,
            /**
             * Whether an agent excepts some response. If the value is `false`, result will always be ignored.
             * You can reply with just an empty string in this case.
             */
            @SerialName("do_input")
            val doInput: Boolean,
        ) : Tool.Args

        final override val argsSerializer = Args.serializer()

        final override val descriptor = ToolDescriptor(
            name = "__talk__",
            description = "Internal communication tool for some agents with chat. It is always implicitly assumed to be present for them.",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "message",
                    description = "The message to communicate",
                    type = ToolParameterType.String
                )
            )
        )
    }

    abstract class ReflectTool : SimpleTool<ReflectTool.Args>() {
        @Serializable
        data class Args(val thoughts: String) : Tool.Args

        final override val argsSerializer = Args.serializer()

        final override val descriptor = ToolDescriptor(
            name = "reflect",
            description = """
                Reflect on the information you have collected and the steps you have finished
                to determine what your next step should be.

                Also use this tool to share with the user things that might be relevant for him to know
                or to log things that could be valuable for the user to look up
                when looking back at the logs of why the next step was the right one to take.
            """.trimIndent(),
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "thoughts",
                    description = "Your most recent thoughts",
                    type = ToolParameterType.String
                )
            )
        )
    }

    abstract class AskTool : SimpleTool<AskTool.Args>() {
        @Serializable
        data class Args(val question: String) : Tool.Args

        final override val argsSerializer = Args.serializer()

        final override val descriptor = ToolDescriptor(
            name = "ask",
            description = """
                Ask a question to the user either to get more context or to get his preferences.

                If you can get context from running other tools without destracting the user prioritise that first.
            """.trimIndent(),
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "question",
                    description = "The question you need an answer to.",
                    type = ToolParameterType.String
                )
            )
        )
    }

    abstract class SubmitAllTasksAsFinishedTool : Tool<SubmitAllTasksAsFinishedTool.Args, SubmitAllTasksAsFinishedTool.Result>() {
        @Serializable
        enum class Action {
            /** User accepts completion and wants to terminate */
            Terminate,
            /** Need to process and reflect on user feedback */
            Reflect,
            /** User provided specific instructions or feedback */
            UserFeedback
        }

        @Serializable
        data class Args(
            val finalReport: String,
            val availableCommands: List<String> = emptyList()
        ) : Tool.Args

        @Serializable
        data class Result(
            val action: Action,
            val userAnswer: String
        ) : ToolResult {
            override fun toStringDefault(): String {
                return buildString {
                    appendLine()
                    appendLine("<Submission Response>")
                    appendLine("<Action>${action}</Action>")
                    appendLine("<User Answer>")
                    appendLine(userAnswer)
                    appendLine("</User Answer>")
                    appendLine("</Submission Response>")
                }.trimEnd() + "\n"
            }
        }

        final override val argsSerializer = Args.serializer()

        final override val descriptor = ToolDescriptor(
            name = "submit_all_tasks_as_finished",
            description = """
                State that you are confident all tasks have been completed to the users request.
                And provide a final report of the work done.

                This will provide the user the option to confirm thanking you for your work and terminate the process.

                Or to provide follow up requests.
            """.trimIndent(),
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "finalReport",
                    description = """
                        The final report containing:
                         1) A list of the tasks that have been fulfilled.
                         2) Optionally a list of next steps the user could consider looking into.
                    """.trimIndent(),
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "available_commands",
                    description = """
                        A list of commands that would be sensible for the user to execute next
                        For example things like: 
                        <build>gradlew build</build>
                        <test>pytest .</test>
                        <run>npm run start-webapp</run>
                        <verify>python -m compileall .</verify>
                        
                        For python also include an activation command if relevant
                        <activate>. ~/project/.venv/bin/activate</activate>
                        
                        For python provide a command to access the python interpreter
                        <python>. ~/project/.venv/bin/activate && python</python>
                        <python>poetry run python</python>
                        <python>uv run python</python>
                    """.trimIndent(),
                    type = ToolParameterType.List<String>(ToolParameterType.String),
                )
            )
        )
    }

}
