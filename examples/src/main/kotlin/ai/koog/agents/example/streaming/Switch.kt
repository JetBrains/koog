package ai.koog.agents.example.streaming

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet

class Switch {
    private var state: Boolean = false

    fun switch(on: Boolean) {
        state = on
    }

    fun isOn(): Boolean {
        return state
    }
}

// Tools from this tool set class are used in BasicChatWithTools example
class SwitchTools(val switch: Switch) : ToolSet {
    @Tool
    @LLMDescription("Switches the state of the switch")
    fun switch(state: Boolean): String {
        switch.switch(state)
        return "Switched to ${if (state) "on" else "off"}"
    }

    @Tool
    @LLMDescription("Returns the state of the switch")
    fun switchState(state: Boolean): String {
        return "Switch is ${if (switch.isOn()) "on" else "off"}"
    }
}
