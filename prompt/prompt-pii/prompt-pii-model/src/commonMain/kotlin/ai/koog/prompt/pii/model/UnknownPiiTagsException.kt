package ai.koog.prompt.pii.model

/**
 * Raised when a model response contains anonymization tags not present in the known tag set.
 */
public class UnknownPiiTagsException(
    public val unknownTags: Set<String>,
    public val knownTags: Set<String>,
    public val content: String,
) : IllegalStateException(
    "Unknown PII tags detected: ${unknownTags.sorted()}. Known tags: ${knownTags.sorted()}."
)
