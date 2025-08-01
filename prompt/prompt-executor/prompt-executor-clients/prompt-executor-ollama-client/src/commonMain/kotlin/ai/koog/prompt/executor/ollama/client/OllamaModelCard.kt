package ai.koog.prompt.executor.ollama.client

import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Represents metadata and configuration details about an Ollama model card.
 * This class contains descriptive properties about the model, such as its name,
 * family of models it belongs to, capabilities it supports, and specific technical
 * attributes like parameter count and size.
 *
 * @property name The name of the model.
 * @property family The family classification of the model, typically indicating related models or versions.
 * @property families A list of family classifications the model belongs to, providing additional grouping information.
 * @property size The size of the model, often represented in bytes or other units relevant to deployment.
 * @property parameterCount The number of parameters in the model, indicating its complexity or capacity.
 * @property contextLength The maximum context length supported by the model, defining how much text input the model can process at once.
 * @property embeddingLength The size of the embedding vector produced by the model, if applicable.
 * @property quantizationLevel The quantization level of the model, describing any compression or optimization techniques applied to the model weights.
 * @property capabilities A list of capabilities supported by the model, represented as instances of the `LLMCapability` class. This provides details on functionalities such as tools
 * , embeddings, and specific processing abilities.
 */
public class OllamaModelCard internal constructor(
    public val name: String,
    public val family: String,
    public val families: List<String>?,
    public val size: Long,
    public val parameterCount: Long?,
    public val contextLength: Long?,
    public val embeddingLength: Long?,
    public val quantizationLevel: String?,
    public val capabilities: List<LLMCapability>,
)

/**
 * Returns the model name without any associated tags.
 * This property extracts and provides a clean version of the `name` property
 * from the `OllamaModelCard` by removing any tags or additional decorations.
 */
public val OllamaModelCard.nameWithoutTag: String get() = name.withoutTag

private val logger = KotlinLogging.logger { }

private const val DEFAULT_CONTEXT_LENGTH: Long = 4_096

/**
 * Converts an instance of `OllamaModelCard` to an `LLModel` representation.
 *
 * This method maps the properties of the `OllamaModelCard` object to create
 * a corresponding `LLModel` instance. It designates the provider as `Ollama`,
 * sets the identifier as the model's name, and assigns the list of capabilities
 * directly from the source model card.
 *
 * @return A new `LLModel` instance based on the properties of the `OllamaModelCard`.
 */
public fun OllamaModelCard.toLLModel(): LLModel = LLModel(
    provider = LLMProvider.Ollama,
    id = name,
    capabilities = capabilities,
    contextLength = contextLength.let { contextLength ->
        if (contextLength == null) {
            logger.warn { "Context length was null for model '${this.name}', falling back to $DEFAULT_CONTEXT_LENGTH" }
        }
        contextLength ?: DEFAULT_CONTEXT_LENGTH
    },
)
