package ai.koog.agents.example.goap;

public class GrouperConfig {
    public final FocusGroup focusGroup;
    public final Creatives creatives;
    public final Message message;
    public final double minScore;
    public final int numWordingsRequired;
    public final int numWordingsToShow;
    public final int numProposals;
    public final int maxIterations;
    public final int maxWordingsToStore;

    public GrouperConfig(FocusGroup focusGroup, Creatives creatives, Message message) {
        this(focusGroup, creatives, message, 0.7, 10, 20, 10, 20);
    }

    public GrouperConfig(FocusGroup focusGroup, Creatives creatives, Message message,
                         double minScore, int numWordingsRequired, int numWordingsToShow,
                         int numProposals, int maxIterations) {
        this.focusGroup = focusGroup;
        this.creatives = creatives;
        this.message = message;
        this.minScore = minScore;
        this.numWordingsRequired = numWordingsRequired;
        this.numWordingsToShow = numWordingsToShow;
        this.numProposals = numProposals;
        this.maxIterations = maxIterations;
        this.maxWordingsToStore = Math.max(numWordingsRequired, numWordingsToShow);
    }
}
