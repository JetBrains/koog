package ai.koog.agents.example.funApi;

public class Switch {
    private boolean state = false;

    public void switchState(boolean on) {
        this.state = on;
    }

    public boolean isOn() {
        return state;
    }
}