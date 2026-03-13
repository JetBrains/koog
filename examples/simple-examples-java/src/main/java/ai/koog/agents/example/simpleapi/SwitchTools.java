package ai.koog.agents.example.simpleapi;

import ai.koog.agents.core.tools.annotations.LLMDescription;
import ai.koog.agents.core.tools.annotations.Tool;
import ai.koog.agents.core.tools.reflect.ToolSet;

public class SwitchTools implements ToolSet {
    private final Switch theSwitch;

    public SwitchTools(Switch theSwitch) {
        this.theSwitch = theSwitch;
    }

    @Tool
    @LLMDescription("Switches the state of the switch")
    public String switchState(boolean state) {
        theSwitch.switchState(state);
        return "Switched to " + (state ? "on" : "off");
    }

    @Tool
    @LLMDescription("Returns the current state of the switch")
    public String getSwitchState() {
        return "Switch is " + (theSwitch.isOn() ? "on" : "off");
    }
}
