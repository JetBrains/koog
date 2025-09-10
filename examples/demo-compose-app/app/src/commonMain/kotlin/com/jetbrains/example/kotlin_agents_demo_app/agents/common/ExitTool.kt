package com.jetbrains.example.kotlin_agents_demo_app.agents.common

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import kotlinx.serialization.Serializable

object ExitTool : SimpleTool<ExitTool.Args>() {
    @Serializable
    data class Args(val result: String = "") : ToolArgs

    override val argsSerializer = Args.serializer()

    override val descriptor = ToolDescriptor(
        name = "exit",
        description = "Exit the agent session with the specified result. Call this tool to finish the conversation with the user.",
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "result",
                description = "The result of the agent session. Default is empty, if there's no particular result.",
                type = ToolParameterType.String,
            )
        )
    )

    override suspend fun doExecute(args: Args): String = args.result
}
