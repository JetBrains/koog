package ai.koog.agents.features.longtermmemory.aws.augmentation

import ai.koog.agents.features.longtermmemory.aws.AgentcoreMemoryRecord
import ai.koog.agents.longtermmemory.retrieval.augmentation.PromptAugmenter
import ai.koog.prompt.Prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.rag.base.TextDocument
import ai.koog.rag.base.storage.search.SearchResult

/**
 * A [PromptAugmenter] that injects retrieved [AgentcoreMemoryRecord] documents into a prompt,
 * routing each record to the appropriate augmentation pathway based on its [AgentcoreMemoryStrategy]:
 *
 * - [AgentcoreMemoryStrategy.SEMANTIC] and [AgentcoreMemoryStrategy.PREFERENCE] — content is
 *   appended to the system message using [contextPrefix] as a header.
 * - [AgentcoreMemoryStrategy.EPISODES] — content is appended to the system message under a
 *   dedicated "[SECTION_EPISODES]" section.
 * - [AgentcoreMemoryStrategy.REFLECTIONS] — content is appended to the system message under a
 *   dedicated "[SECTION_REFLECTIONS]" section.
 * - [AgentcoreMemoryStrategy.SUMMARY] — the retrieved summaries (rendered via [userTemplate])
 *   are appended to the last user message as an additional [MessagePart.Text] part.
 *
 * If no system message exists in the prompt, one is created. If no user message exists when
 * handling [AgentcoreMemoryStrategy.SUMMARY] records, the summaries fall back to system-message
 * augmentation.
 *
 * @param contextPrefix Header text prepended to SEMANTIC/PREFERENCE context blocks.
 *   Defaults to [PromptAugmenter.DEFAULT_CONTEXT_PREFIX].
 * @param sectionEpisodes Section header for EPISODES records. Defaults to [SECTION_EPISODES].
 * @param sectionReflections Section header for REFLECTIONS records. Defaults to [SECTION_REFLECTIONS].
 * @param sectionSeparator Separator string between sections. Defaults to
 *   [PromptAugmenter.SECTION_SEPARATOR].
 * @param userTemplate Template used to render SUMMARY context appended to the last user message.
 *   Must contain the [PromptAugmenter.RELEVANT_CONTEXT_PLACEHOLDER] placeholder. Defaults to
 *   [DEFAULT_USER_TEMPLATE].
 */
public class AgentcorePromptAugmenter @JvmOverloads constructor(
    private val contextPrefix: String = PromptAugmenter.DEFAULT_CONTEXT_PREFIX,
    private val sectionEpisodes: String = SECTION_EPISODES,
    private val sectionReflections: String = SECTION_REFLECTIONS,
    private val sectionSeparator: String = PromptAugmenter.SECTION_SEPARATOR,
    private val userTemplate: String = DEFAULT_USER_TEMPLATE,
) : PromptAugmenter {

    public companion object {
        /** Default section header for EPISODIC episodes (session-scoped past turns). */
        public const val SECTION_EPISODES: String = "Relevant past interactions"

        /** Default section header for EPISODIC reflections (actor-scoped lessons learned). */
        public const val SECTION_REFLECTIONS: String = "Lessons learned"

        /**
         * Default template used to wrap SUMMARY context appended to the last user message.
         * Use [PromptAugmenter.RELEVANT_CONTEXT_PLACEHOLDER] as the placeholder for the rendered
         * context block.
         */
        public val DEFAULT_USER_TEMPLATE: String =
            """
            |Here is a relevant conversation summary for the question above:
            |
            |${PromptAugmenter.RELEVANT_CONTEXT_PLACEHOLDER}
            """.trimMargin().trim()
    }

    override fun augment(
        originalPrompt: Prompt,
        relevantContext: List<SearchResult<TextDocument>>,
    ): Prompt {
        if (relevantContext.isEmpty()) return originalPrompt

        val summaryBucket = mutableListOf<SearchResult<TextDocument>>()
        val episodesBucket = mutableListOf<SearchResult<TextDocument>>()
        val reflectionsBucket = mutableListOf<SearchResult<TextDocument>>()
        val systemBucket = mutableListOf<SearchResult<TextDocument>>()

        for (result in relevantContext) {
            val amr = result.document as? AgentcoreMemoryRecord
                ?: throw IllegalArgumentException(
                    "AgentcorePromptAugmenter requires AgentcoreMemoryRecord documents, " +
                        "got ${result.document::class.qualifiedName}"
                )

            when (amr.strategy) {
                AgentcoreMemoryStrategy.SUMMARY -> summaryBucket += result
                AgentcoreMemoryStrategy.EPISODES -> episodesBucket += result
                AgentcoreMemoryStrategy.REFLECTIONS -> reflectionsBucket += result
                AgentcoreMemoryStrategy.SEMANTIC -> systemBucket += result
                AgentcoreMemoryStrategy.PREFERENCE -> systemBucket += result
            }
        }

        // 1) System-side content:
        //    - Episodic results are rendered as two distinct labelled sections
        //      ("Relevant past interactions" / "Lessons learned"); either section is omitted when its bucket is empty.
        //    - Plain semantic/preference content follows, using the generic contextPrefix.
        val systemParts = buildList {
            if (episodesBucket.isNotEmpty()) {
                add(PromptAugmenter.formatContext(episodesBucket, "$sectionEpisodes:\n"))
            }
            if (reflectionsBucket.isNotEmpty()) {
                add(PromptAugmenter.formatContext(reflectionsBucket, "$sectionReflections:\n"))
            }
            if (systemBucket.isNotEmpty()) add(formatPlain(systemBucket))
        }
        val systemText = systemParts.joinToString(sectionSeparator)
        val afterSystem =
            if (systemText.isNotBlank()) augmentSystemMessage(originalPrompt, systemText) else originalPrompt

        // 2) User-side content (SUMMARY rewrite). Applied after the system injection so the
        //    two are independent.
        return if (summaryBucket.isNotEmpty()) {
            augmentUserMessage(afterSystem, summaryBucket)
        } else {
            afterSystem
        }
    }

    // --- system-message branch ------------------------------------------------

    private fun augmentSystemMessage(prompt: Prompt, contextText: String): Prompt {
        if (contextText.isBlank()) return prompt
        val systemIndex = prompt.messages.indexOfFirst { it is Message.System }
        return prompt.withMessages { messages ->
            if (systemIndex >= 0) {
                val existing = messages[systemIndex] as Message.System
                val merged = existing.copy(
                    parts = buildList {
                        addAll(existing.parts)
                        add(MessagePart.Text(sectionSeparator))
                        add(MessagePart.Text(contextText))
                    }
                )
                messages.toMutableList().also { it[systemIndex] = merged }
            } else {
                listOf<Message>(Message.System(contextText, RequestMetaInfo.Empty)) + messages
            }
        }
    }

    // --- user-message branch --------------------------------------------------

    private fun augmentUserMessage(
        prompt: Prompt,
        context: List<SearchResult<TextDocument>>,
    ): Prompt {
        val userIndex = prompt.messages.indexOfLast { it is Message.User }
        if (userIndex < 0) {
            // No user message to augment — return the current prompt.
            return prompt
        }
        val contextText = formatPlain(context)
        if (contextText.isBlank()) return prompt
        val rendered = PromptAugmenter.formatTemplate(userTemplate, contextText)
        if (rendered.isBlank()) return prompt
        return prompt.withMessages { messages ->
            val original = messages[userIndex] as Message.User
            val rewritten = original.copy(
                parts = original.parts + MessagePart.Text(rendered)
            )
            messages.toMutableList().also { it[userIndex] = rewritten }
        }
    }

    // --- formatting -----------------------------------------------------------

    private fun formatPlain(context: List<SearchResult<TextDocument>>): String =
        PromptAugmenter.formatContext(context, contextPrefix)
}
