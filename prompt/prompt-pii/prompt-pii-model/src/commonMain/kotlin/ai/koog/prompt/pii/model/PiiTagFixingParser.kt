package ai.koog.prompt.pii.model

import ai.koog.prompt.dsl.PromptBuilder
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message

/**
 * Optional LLM-assisted fixer for unknown anonymization tags in non-streaming responses.
 */
public class PiiTagFixingParser(
    public val model: LLModel,
    public val retries: Int,
    private val promptTemplate: (
        builder: PromptBuilder,
        content: String,
        knownTags: Set<String>,
        unknownTags: Set<String>,
    ) -> PromptBuilder = ::defaultFixingPrompt,
) {
    init {
        require(retries >= 0) { "retries must be >= 0, got $retries" }
    }

    /**
     * Attempts to rewrite [content] until no unknown tags remain or retries are exhausted.
     */
    public suspend fun fix(
        executor: PromptExecutor,
        content: String,
        knownTags: Set<String>,
        tagPattern: Regex = PiiPromptExecutorConfig.DEFAULT_TAG_PATTERN,
    ): String {
        var currentContent: String = content
        var unknownTags: Set<String> = extractUnknownTags(currentContent, knownTags, tagPattern)
        var attempt: Int = 0

        while (unknownTags.isNotEmpty() && attempt < retries) {
            attempt += 1

            val fixingPrompt = prompt("pii-tag-fixing") {
                promptTemplate(this, currentContent, knownTags, unknownTags)
            }

            val response: Message.Response = executor.execute(
                prompt = fixingPrompt,
                model = model,
                tools = emptyList()
            ).single()

            require(response is Message.Assistant) {
                "Response for PII tag fixing must be assistant, got ${response::class.simpleName}"
            }

            currentContent = response.content
            unknownTags = extractUnknownTags(currentContent, knownTags, tagPattern)
        }

        if (unknownTags.isNotEmpty()) {
            throw UnknownPiiTagsException(
                unknownTags = unknownTags,
                knownTags = knownTags,
                content = currentContent
            )
        }

        return currentContent
    }

    public companion object {
        /**
         * Default fixing instruction prompt.
         */
        public fun defaultFixingPrompt(
            builder: PromptBuilder,
            content: String,
            knownTags: Set<String>,
            unknownTags: Set<String>,
        ): PromptBuilder = builder.apply {
            system(
                "You fix anonymized tag usage in text. Use only known tags, preserve meaning, and do not invent new tags."
            )
            user(
                buildString {
                    appendLine("Known tags:")
                    appendLine(knownTags.sorted().joinToString(separator = "\n"))
                    appendLine()
                    appendLine("Unknown tags to replace:")
                    appendLine(unknownTags.sorted().joinToString(separator = "\n"))
                    appendLine()
                    appendLine("Return only the fixed text:")
                    append(content)
                }
            )
        }
    }
}

private fun extractUnknownTags(
    content: String,
    knownTags: Set<String>,
    tagPattern: Regex,
): Set<String> =
    tagPattern
        .findAll(content)
        .map { it.value }
        .filter { it !in knownTags }
        .toSet()
