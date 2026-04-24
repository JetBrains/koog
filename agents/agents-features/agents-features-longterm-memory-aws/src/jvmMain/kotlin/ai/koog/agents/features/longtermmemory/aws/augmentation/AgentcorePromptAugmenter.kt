package ai.koog.agents.features.longtermmemory.aws.augmentation

import ai.koog.agents.features.longtermmemory.aws.AgentcoreMemoryRecord
import ai.koog.agents.longtermmemory.retrieval.augmentation.PromptAugmenter
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.rag.base.TextDocument
import ai.koog.rag.base.storage.search.SearchResult

public class AgentcorePromptAugmenter @JvmOverloads constructor(
    private val contextPrefix: String = PromptAugmenter.DEFAULT_CONTEXT_PREFIX,
) : PromptAugmenter {

    public companion object {
        /** Default section header for EPISODIC episodes (session-scoped past turns). */
        public const val SECTION_EPISODES: String = "Relevant past interactions"

        /** Default section header for EPISODIC reflections (actor-scoped lessons learned). */
        public const val SECTION_REFLECTIONS: String = "Lessons learned"

        /** Trailing newline separator between sections. */
        private const val SECTION_SEPARATOR: String = "\n\n"
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
        //      ("Relevant past interactions" / "Lessons learned") matching the Java advisor's
        //      formatEpisodicContext; either section is omitted when its bucket is empty.
        //    - Plain semantic/preference content follows, using the generic contextPrefix.
        val systemParts = buildList {
            if (episodesBucket.isNotEmpty()) {
                add(PromptAugmenter.formatContext(episodesBucket, "$SECTION_EPISODES:\n"))
            }
            if (reflectionsBucket.isNotEmpty()) {
                add(PromptAugmenter.formatContext(reflectionsBucket, "$SECTION_REFLECTIONS:\n"))
            }
            if (systemBucket.isNotEmpty()) add(formatPlain(systemBucket))
        }
        val systemText = systemParts.joinToString(SECTION_SEPARATOR)
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
                val mergedContent = existing.content + SECTION_SEPARATOR + contextText
                val merged = Message.System(mergedContent, existing.metaInfo, existing.cacheControl)
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
            // No user message to rewrite — fall back to system-message augmentation so the
            // retrieved summaries are still delivered to the model.
            return augmentSystemMessage(prompt, formatPlain(context))
        }
        val contextText = formatPlain(context)
        if (contextText.isBlank()) return prompt
        return prompt.withMessages { messages ->
            val original = messages[userIndex] as Message.User
            val rewritten = Message.User(
                content = "$contextText\nUser question: ${original.content}",
                metaInfo = original.metaInfo,
                cacheControl = original.cacheControl,
            )
            messages.toMutableList().also { it[userIndex] = rewritten }
        }
    }

    // --- formatting -----------------------------------------------------------

    private fun formatPlain(context: List<SearchResult<TextDocument>>): String =
        PromptAugmenter.formatContext(context, contextPrefix)
}
