package ai.grazie.code.agents.example.simpleapi

import ai.grazie.code.agents.core.tools.SimpleTool
import ai.grazie.code.agents.core.tools.Tool
import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.code.agents.core.tools.ToolParameterDescriptor
import ai.grazie.code.agents.core.tools.ToolParameterType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

class Switch {
    private var state: Boolean = false

    fun switch(on: Boolean) {
        state = on
    }

    fun isOn(): Boolean {
        return state
    }
}

class SwitchTool(private val switch: Switch) : SimpleTool<SwitchTool.Args>() {
    override suspend fun doExecute(args: Args): String {
        switch.switch(args.state)
        return "Switched to ${if (args.state) "on" else "off"}"
    }

    override val argsSerializer: KSerializer<Args>
        get() = Args.serializer()

    override val descriptor: ToolDescriptor
        get() = ToolDescriptor(
            "switch",
            "Switches the state of the switch",
            listOf(
                ToolParameterDescriptor(
                    name = "state",
                    description = "The state to switch to",
                    type = ToolParameterType.Boolean
                )
            ))

    @Serializable
    data class Args(val state: Boolean) : Tool.Args
}

class SwitchStateTool(private val switch: Switch) : SimpleTool<Tool.EmptyArgs>() {
    override suspend fun doExecute(args: EmptyArgs): String {
        return "Switch is ${if (switch.isOn()) "on" else "off"}"
    }

    override val argsSerializer: KSerializer<EmptyArgs>
        get() = EmptyArgs.serializer()

    override val descriptor: ToolDescriptor
        get() = ToolDescriptor(
            "switch_state",
            "Returns the state of the switch"
        )
}