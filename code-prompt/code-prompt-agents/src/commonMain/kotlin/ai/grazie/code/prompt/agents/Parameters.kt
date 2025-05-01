package ai.grazie.code.prompt.agents

import ai.grazie.code.agents.core.tools.ToolParameterDescriptor
import ai.grazie.code.agents.core.tools.ToolParameterType
import ai.jetbrains.code.prompt.text.text


object AgentParameters {

    val CODE_CONTEXT_PARAMETER = ToolParameterDescriptor(
        name = "context",
        description = text {
            +"Additional context to guide the call, must be as specific as possible and based on what is already known at the moment of call"
            br()
            +"Examples:"
            +" - The name of a specific file or directory that is likely to be related"
            +" - A specific keyword or phrase"
            +" - Information about the code structure or content, like that is uses gradle or maven"
            +" - A specific code element or identifier to concentrate on"
        },
        type = ToolParameterType.String
    )
}
