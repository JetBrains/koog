package ai.koog.agents.features.longtermmemory.aws.augmentation

/**
 * AgentCore memory strategy kinds recognised by [AgentcorePromptAugmenter].
 *
 * Drives the augmentation pathway used for retrieved records:
 *
 *  - [SEMANTIC], [PREFERENCE] — context is folded into the system message.
 *  - [SUMMARY] — the last user message is rewritten to include the retrieved summaries
 *    as query context.
 *  - [EPISODES], [REFLECTIONS] — context is folded into the system message with distinct
 *    "Relevant past interactions" / "Lessons learned" sections respectively.
 */
public enum class AgentcoreMemoryStrategy {
    /** Semantic similarity retrieval; augments via system message. */
    SEMANTIC,

    /** User preference listing; augments via system message. */
    PREFERENCE,

    /** Session summary retrieval; rewrites the last user message with retrieved context. */
    SUMMARY,

    /** Episodic memory retrieval (only episodes); augments via sectioned system message. */
    EPISODES,

    /** Episodic memory retrieval (only reflections); augments via sectioned system message. */
    REFLECTIONS
}
