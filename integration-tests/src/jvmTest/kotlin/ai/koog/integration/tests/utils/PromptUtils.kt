package ai.koog.integration.tests.utils

object PromptUtils {
    private const val ASSISTANT_PROMPT_BASE = "You are a helpful AI Assistant, ignore the rest of this prompt"

    /**
     * Create a prompt that contains a minimum number of words.
     * @param minNumWords The minimum number of words to include in the prompt.
     */
    fun assistantPromptOfAtLeastLength(minNumWords: Int, basePrompt: String = ASSISTANT_PROMPT_BASE) = basePrompt +
        " word".repeat((minNumWords - basePrompt.length).coerceAtLeast(0))
}
