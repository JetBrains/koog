package com.jetbrains.example.koog.compose.agents.common

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable

object ExitTool : SimpleTool<ExitTool.Args>(
    argsSerializer = Args.serializer(),
    name = "exit",
    description = "Exit the agent session with the specified result. Call this tool to finish the conversation with the user."
) {
    @Serializable
    data class Args(
        @property:LLMDescription("The result of the agent session. Default is empty, if there's no particular result.")
        val result: String = ""
    )

    override suspend fun execute(args: Args): String = args.result
}
