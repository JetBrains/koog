package com.jetbrains.example.kotlin_agents_demo_app.agents.common

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable

object ExitTool : SimpleTool<ExitTool.Args>() {
    @Serializable
    data class Args(
        @property:LLMDescription("The result of the agent session. Default is empty, if there's no particular result.")
        val result: String = ""
    )

    override val argsSerializer = Args.serializer()
    override val description: String =
        "Exit the agent session with the specified result. Call this tool to finish the conversation with the user."

    override suspend fun doExecute(args: Args): String = args.result
}
