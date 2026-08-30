package ai.koog.prompt.pii.structured

import ai.koog.prompt.dsl.PromptBuilder
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.StructureFixingParser
import ai.koog.prompt.executor.model.executeStructured
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.pii.model.PiiDetection
import ai.koog.prompt.pii.model.PiiDetector
import ai.koog.prompt.pii.model.PiiType
import kotlinx.serialization.Serializable

/**
 * LLM-backed detector which extracts only substring and type, then resolves spans locally.
 */
public class StructuredPiiDetector(
    private val executor: PromptExecutor,
    private val model: LLModel,
    private val config: StructuredPiiDetectorConfig = StructuredPiiDetectorConfig(),
) : PiiDetector {
    override suspend fun detect(text: String): List<PiiDetection> {
        if (text.isEmpty()) return emptyList()

        val prompt = prompt(config.promptId) {
            config.promptTemplate(this, text)
        }

        val fixingParser = StructureFixingParser(
            model = model,
            retries = config.fixingRetries,
        )

        val structured: StructuredPiiResponse = executor
            .executeStructured<StructuredPiiResponse>(
                prompt = prompt,
                model = model,
                fixingParser = fixingParser,
            )
            .getOrThrow()
            .data

        return resolveDetections(text, structured.detections)
    }

    private fun resolveDetections(
        text: String,
        detections: List<StructuredPiiItem>,
    ): List<PiiDetection> {
        val result: MutableList<PiiDetection> = mutableListOf()
        val seen: MutableSet<DetectionKey> = linkedSetOf()

        for (detection in detections) {
            if (detection.substring.isBlank()) continue

            for (start in findAllOccurrences(text, detection.substring)) {
                val endExclusive: Int = start + detection.substring.length
                val key = DetectionKey(
                    start = start,
                    endExclusive = endExclusive,
                    type = detection.type
                )
                if (!seen.add(key)) continue

                result += PiiDetection(
                    start = start,
                    endExclusive = endExclusive,
                    type = detection.type
                )
            }
        }

        return result
    }

    private fun findAllOccurrences(text: String, needle: String): List<Int> {
        if (needle.isEmpty()) return emptyList()

        val starts: MutableList<Int> = mutableListOf()
        var fromIndex = 0

        while (fromIndex <= text.length - needle.length) {
            val matchIndex: Int = text.indexOf(needle, startIndex = fromIndex)
            if (matchIndex < 0) break
            starts += matchIndex
            fromIndex = matchIndex + 1
        }

        return starts
    }

    private data class DetectionKey(
        val start: Int,
        val endExclusive: Int,
        val type: PiiType,
    )
}

/**
 * Configuration for [StructuredPiiDetector].
 */
public data class StructuredPiiDetectorConfig(
    public val promptId: String = DEFAULT_PROMPT_ID,
    public val fixingRetries: Int = DEFAULT_FIXING_RETRIES,
    public val promptTemplate: (builder: PromptBuilder, text: String) -> PromptBuilder = ::defaultPromptTemplate,
) {
    init {
        require(fixingRetries >= 0) { "fixingRetries must be >= 0, got $fixingRetries" }
    }

    public companion object {
        public const val DEFAULT_PROMPT_ID: String = "pii-structured-detection"
        public const val DEFAULT_FIXING_RETRIES: Int = 1

        /**
         * Default instruction for extraction that requests substring + enum type only.
         */
        public fun defaultPromptTemplate(
            builder: PromptBuilder,
            text: String,
        ): PromptBuilder =
            builder.apply {
                system(
                    buildString {
                        appendLine("Extract PII detections from input text.")
                        appendLine("Return only values that appear exactly in the input.")
                        appendLine("Do not return start/end positions.")
                        appendLine("Use only these PiiType enum values:")
                        append(PiiType.entries.joinToString(separator = ", ") { it.name })
                    }
                )
                user(
                    buildString {
                        appendLine("Input text:")
                        append(text)
                    }
                )
            }
    }
}

@Serializable
private data class StructuredPiiResponse(
    val detections: List<StructuredPiiItem> = emptyList(),
)

@Serializable
private data class StructuredPiiItem(
    val substring: String,
    val type: PiiType,
)
