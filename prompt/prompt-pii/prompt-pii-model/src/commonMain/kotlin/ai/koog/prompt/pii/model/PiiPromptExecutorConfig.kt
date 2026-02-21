package ai.koog.prompt.pii.model

/**
 * Configuration for [PiiPromptExecutor].
 */
public data class PiiPromptExecutorConfig(
    public val instructionMessage: String = DEFAULT_INSTRUCTION_MESSAGE,
    public val tagPattern: Regex = DEFAULT_TAG_PATTERN,
    public val normalizeType: (String) -> String = PiiTypeMapper::defaultNormalizeTypeLabel,
    public val normalizeValueForReuse: (String) -> String = ::defaultNormalizeValueForReuse,
) {
    public companion object {
        /**
         * Matches tags like `[[person 1]]`.
         */
        public val DEFAULT_TAG_PATTERN: Regex = Regex("""\[\[[a-z0-9_]+\s+\d+]]""", RegexOption.IGNORE_CASE)

        /**
         * Extra system message prepended when anonymization was applied.
         */
        public const val DEFAULT_INSTRUCTION_MESSAGE: String =
            "You are receiving anonymized data with placeholder tags like [[person 1]]. Reuse only existing tags. Do not invent new tags."
    }
}

/**
 * Default normalization for reuse-key generation.
 */
public fun defaultNormalizeValueForReuse(value: String): String = value.trim()

