import ai.koog.agents.core.tools.annotations.LLMDescription;
import ai.koog.agents.core.tools.annotations.Tool;
import ai.koog.agents.core.tools.reflect.ToolSet;


public class ExampleSwitchController {

    public class Switch {
        private boolean state;

        public Switch(boolean state) {
            this.state = state;
        }

        // "switch" is a reserved keyword in Java, so we use a different method name
        public void setState(boolean state) {
            this.state = state;
        }

        public boolean isOn() {
            return state;
        }
    }

    @LLMDescription(description = "Tools for controlling a switch")
    public class SwitchTools implements ToolSet {
        private final Switch sw;

        public SwitchTools(Switch sw) {
            this.sw = sw;
        }

        @Tool
        @LLMDescription(description = "Switches the state of the switch")
        public String switchStateTo(
                @LLMDescription(description = "The state to set (true for on, false for off)") boolean state
        ) {
            sw.setState(state);
            return "Switched to " + (state ? "on" : "off");
        }

        @Tool
        @LLMDescription(description = "Returns the current state of the switch")
        public String switchState() {
            return "Switch is " + (sw.isOn() ? "on" : "off");
        }
    }
}