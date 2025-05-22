package com.jetbrains.example.kotlin_agents_demo_app.agents.common

import ai.koog.agents.core.tools.*
import kotlinx.serialization.Serializable

object ExitTool : SimpleTool<ExitTool.Args>() {
    @Serializable
    data class Args(val result: String = ""): Tool.Args
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