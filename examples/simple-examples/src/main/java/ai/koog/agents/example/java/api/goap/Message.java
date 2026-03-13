package ai.koog.agents.example.java.api.goap;

public class Message {
    public final String id;
    public final String content;
    public final String objective;
    public final String deliverable;

    public Message(String id, String content, String objective, String deliverable) {
        this.id = id;
        this.content = content;
        this.objective = objective;
        this.deliverable = deliverable;
    }

    @Override
    public String toString() {
        return "Message is " + content + "\nObjective is " + objective + "\nThe deliverable result is " + deliverable;
    }
}
