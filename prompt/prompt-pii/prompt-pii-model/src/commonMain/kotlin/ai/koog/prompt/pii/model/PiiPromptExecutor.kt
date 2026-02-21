package ai.koog.prompt.pii.model

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock

/**
 * Transparent prompt executor wrapper that anonymizes PII before model call
 * and deanonymizes known tags in model responses.
 */
public class PiiPromptExecutor(
    private val detector: PiiDetector,
    private val nested: PromptExecutor,
    private val config: PiiPromptExecutorConfig = PiiPromptExecutorConfig(),
    private val fixingParser: PiiTagFixingParser? = null,
    private val clock: Clock = kotlin.time.Clock.System,
) : PromptExecutor {
    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): List<Message.Response> {
        val context: AnonymizationContext = anonymizePrompt(prompt)
        val responses: List<Message.Response> = nested.execute(context.prompt, model, tools)

        if (!context.hasTags) return responses

        return deanonymizeResponses(responses, context)
    }

    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): List<LLMChoice> {
        val context: AnonymizationContext = anonymizePrompt(prompt)
        val choices: List<LLMChoice> = nested.executeMultipleChoices(context.prompt, model, tools)

        if (!context.hasTags) return choices

        return choices.map { choice ->
            deanonymizeResponses(choice, context)
        }
    }

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Flow<StreamFrame> =
        flow {
            val context: AnonymizationContext = anonymizePrompt(prompt)

            if (!context.hasTags) {
                nested.executeStreaming(context.prompt, model, tools).collect { frame ->
                    emit(frame)
                }
                return@flow
            }

            var appendCarry: String = ""

            nested.executeStreaming(context.prompt, model, tools).collect { frame ->
                when (frame) {
                    is StreamFrame.Append -> {
                        val (processable, newCarry) = splitByPotentialTagBoundary(appendCarry + frame.text)
                        appendCarry = newCarry

                        if (processable.isNotEmpty()) {
                            val validated: String = validateNoUnknownTags(processable, context)
                            val deanonymized: String = deanonymizeKnownTags(validated, context.tagToValue)
                            if (deanonymized.isNotEmpty()) {
                                emit(StreamFrame.Append(deanonymized))
                            }
                        }
                    }

                    is StreamFrame.ToolCall -> {
                        val validated: String = validateNoUnknownTags(frame.content, context)
                        val deanonymized: String = deanonymizeKnownTags(validated, context.tagToValue)
                        emit(frame.copy(content = deanonymized))
                    }

                    is StreamFrame.End -> {
                        if (appendCarry.isNotEmpty()) {
                            val deanonymizedCarry: String = deanonymizeKnownTags(appendCarry, context.tagToValue)
                            if (deanonymizedCarry.isNotEmpty()) {
                                emit(StreamFrame.Append(deanonymizedCarry))
                            }
                            appendCarry = ""
                        }
                        emit(frame)
                    }
                }
            }

            if (appendCarry.isNotEmpty()) {
                val deanonymizedCarry: String = deanonymizeKnownTags(appendCarry, context.tagToValue)
                if (deanonymizedCarry.isNotEmpty()) {
                    emit(StreamFrame.Append(deanonymizedCarry))
                }
            }
        }

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
        nested.moderate(prompt, model)

    override suspend fun models(): List<LLModel> = nested.models()

    override fun close() {
        nested.close()
    }

    private suspend fun anonymizePrompt(prompt: Prompt): AnonymizationContext {
        val state = TagState(config)
        val anonymizedMessages: List<Message> = buildList {
            for (message in prompt.messages) {
                add(anonymizeMessage(message, state))
            }
        }

        val tagToValue: Map<String, String> = state.snapshot()
        val finalMessages: List<Message> = if (tagToValue.isEmpty()) {
            anonymizedMessages
        } else {
            listOf(Message.System(config.instructionMessage, RequestMetaInfo.create(clock))) + anonymizedMessages
        }

        return AnonymizationContext(
            prompt = prompt.copy(messages = finalMessages),
            tagToValue = tagToValue
        )
    }

    private suspend fun anonymizeMessage(message: Message, state: TagState): Message =
        when (message) {
            is Message.User -> message.copy(parts = anonymizeTextParts(message.parts, state))
            is Message.System -> message.copy(parts = anonymizeTextOnlyParts(message.parts, state))
            is Message.Assistant -> message.copy(parts = anonymizeTextParts(message.parts, state))
            is Message.Reasoning -> message.copy(parts = anonymizeTextOnlyParts(message.parts, state))
            is Message.Tool.Call -> message.copy(parts = anonymizeTextOnlyParts(message.parts, state))
            is Message.Tool.Result -> message.copy(parts = anonymizeTextOnlyParts(message.parts, state))
        }

    private suspend fun anonymizeTextParts(parts: List<ContentPart>, state: TagState): List<ContentPart> = buildList {
        for (part in parts) {
            add(
                when (part) {
                    is ContentPart.Text -> ContentPart.Text(anonymizeText(part.text, state))
                    else -> part
                }
            )
        }
    }

    private suspend fun anonymizeTextOnlyParts(
        parts: List<ContentPart.Text>,
        state: TagState,
    ): List<ContentPart.Text> = buildList {
        for (part in parts) {
            add(ContentPart.Text(anonymizeText(part.text, state)))
        }
    }

    private suspend fun anonymizeText(text: String, state: TagState): String {
        val detections: List<PiiDetection> = pickNonOverlappingDetections(text, detector.detect(text))
        if (detections.isEmpty()) return text

        val builder = StringBuilder(text.length)
        var cursor: Int = 0

        for (detection in detections) {
            builder.append(text.substring(cursor, detection.start))
            val value: String = text.substring(detection.start, detection.endExclusive)
            val tag: String = state.getOrCreateTag(detection.type, value)
            builder.append(tag)
            cursor = detection.endExclusive
        }

        builder.append(text.substring(cursor))
        return builder.toString()
    }

    private suspend fun deanonymizeResponses(
        responses: List<Message.Response>,
        context: AnonymizationContext,
    ): List<Message.Response> =
        responses.map { response ->
            when (response) {
                is Message.Assistant -> {
                    val updatedParts: List<ContentPart> = response.parts.map { part ->
                        when (part) {
                            is ContentPart.Text -> ContentPart.Text(
                                deanonymizeText(content = part.text, context = context)
                            )

                            else -> part
                        }
                    }
                    response.copy(parts = updatedParts)
                }

                is Message.Reasoning -> {
                    val updatedParts: List<ContentPart.Text> = response.parts.map { part ->
                        ContentPart.Text(deanonymizeText(content = part.text, context = context))
                    }
                    response.copy(parts = updatedParts)
                }

                is Message.Tool.Call -> {
                    val updatedParts: List<ContentPart.Text> = response.parts.map { part ->
                        ContentPart.Text(deanonymizeText(content = part.text, context = context))
                    }
                    response.copy(parts = updatedParts)
                }
            }
        }

    private suspend fun deanonymizeText(content: String, context: AnonymizationContext): String {
        val unknownTags: Set<String> = extractUnknownTags(content, context.knownTags)
        if (unknownTags.isNotEmpty()) {
            val fixedContent: String = fixingParser?.fix(
                executor = nested,
                content = content,
                knownTags = context.knownTags,
                tagPattern = config.tagPattern
            ) ?: throw UnknownPiiTagsException(
                unknownTags = unknownTags,
                knownTags = context.knownTags,
                content = content
            )

            return deanonymizeKnownTags(fixedContent, context.tagToValue)
        }

        return deanonymizeKnownTags(content, context.tagToValue)
    }

    private fun validateNoUnknownTags(content: String, context: AnonymizationContext): String {
        val unknownTags: Set<String> = extractUnknownTags(content, context.knownTags)
        if (unknownTags.isNotEmpty()) {
            throw UnknownPiiTagsException(
                unknownTags = unknownTags,
                knownTags = context.knownTags,
                content = content
            )
        }
        return content
    }

    private fun extractUnknownTags(content: String, knownTags: Set<String>): Set<String> =
        config
            .tagPattern
            .findAll(content)
            .map { it.value }
            .filter { it !in knownTags }
            .toSet()

    private fun deanonymizeKnownTags(content: String, tagToValue: Map<String, String>): String {
        var result: String = content
        for ((tag, value) in tagToValue.entries.sortedByDescending { it.key.length }) {
            result = result.replace(tag, value)
        }
        return result
    }

    private fun splitByPotentialTagBoundary(text: String): Pair<String, String> {
        val lastTagStart: Int = text.lastIndexOf(TAG_PREFIX)
        val lastTagEnd: Int = text.lastIndexOf(TAG_SUFFIX)

        return if (lastTagStart > lastTagEnd) {
            text.substring(0, lastTagStart) to text.substring(lastTagStart)
        } else {
            text to ""
        }
    }

    private fun pickNonOverlappingDetections(text: String, detections: List<PiiDetection>): List<PiiDetection> {
        // Stable type ordering makes same-span collisions deterministic across detectors and model outputs.
        val sorted: List<PiiDetection> = detections
            .filter { it.start in 0 until text.length && it.endExclusive <= text.length && it.endExclusive > it.start }
            .sortedWith(
                compareBy<PiiDetection> { it.start }
                    .thenByDescending { it.endExclusive - it.start }
                    .thenBy { it.endExclusive }
                    .thenBy { it.type.name }
            )

        val selected: MutableList<PiiDetection> = mutableListOf()
        var lastEnd: Int = -1

        for (detection in sorted) {
            if (detection.start >= lastEnd) {
                selected += detection
                lastEnd = detection.endExclusive
            }
        }

        return selected
    }

    private data class TagKey(
        val token: String,
        val normalizedValue: String,
    )

    private class TagState(
        private val config: PiiPromptExecutorConfig,
    ) {
        private val tagByKey: LinkedHashMap<TagKey, String> = linkedMapOf()
        private val valueByTag: LinkedHashMap<String, String> = linkedMapOf()
        private val countersByToken: MutableMap<String, Int> = mutableMapOf()

        fun getOrCreateTag(type: PiiType, value: String): String {
            val token: String = resolveToken(type)
            val key = TagKey(token = token, normalizedValue = config.normalizeValueForReuse(value))

            return tagByKey.getOrPut(key) {
                val next: Int = (countersByToken[token] ?: 0) + 1
                countersByToken[token] = next
                val tag: String = "[[$token $next]]"
                valueByTag[tag] = value
                tag
            }
        }

        fun snapshot(): Map<String, String> = valueByTag.toMap()

        private fun resolveToken(type: PiiType): String {
            val normalized: String = config.normalizeType(type.tagToken).trim('_')
            return normalized.ifBlank { type.tagToken }
        }
    }

    private data class AnonymizationContext(
        val prompt: Prompt,
        val tagToValue: Map<String, String>,
    ) {
        val knownTags: Set<String>
            get() = tagToValue.keys

        val hasTags: Boolean
            get() = tagToValue.isNotEmpty()
    }

    private companion object {
        private const val TAG_PREFIX: String = "[["
        private const val TAG_SUFFIX: String = "]]"
    }
}

/**
 * Convenience helper to wrap a [PromptExecutor] with [PiiPromptExecutor].
 */
public fun PromptExecutor.withPii(
    detector: PiiDetector,
    config: PiiPromptExecutorConfig = PiiPromptExecutorConfig(),
    fixingParser: PiiTagFixingParser? = null,
): PromptExecutor =
    PiiPromptExecutor(
        detector = detector,
        nested = this,
        config = config,
        fixingParser = fixingParser
    )
