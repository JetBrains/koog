package ai.koog.prompt.llm

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmField

/**
 * Represents a sealed hierarchy for defining Large Language Model (LLM) providers.
 * Each LLM provider is uniquely identified by an id and a display name.
 *
 * This sealed class allows enumeration of specific providers like Google, OpenAI, Meta, etc.,
 * and serves as a common base type for handling providers within the system.
 *
 * @property id The unique identifier of the LLM provider.
 * @property display The human-readable name of the LLM provider.
 */
@Serializable
public abstract class LLMProvider(public val id: String, public val display: String) {
    /**
     * Companion object for the `LLMProvider` class, providing predefined instances of large language model providers.
     *
     * The companion object acts as a container for multiple constant providers,
     * allowing standardized access to specific implementations of LLM providers.
     */
    public companion object {
        /**
         * Represents the OpenAI provider instance of the Large Language Model (LLM).
         *
         * This constant identifies the provider as OpenAI and is used to distinguish
         * between different LLM providers within the system.
         */
        @JvmField
        public val OpenAI: LLMProvider = OpenAILLMProvider()

        /**
         * Represents the Anthropic Large Language Model (LLM) provider.
         *
         * This value is used to identify and work with the Anthropic LLM implementation
         * within the system. Anthropic is one of the predefined providers for managing
         * Large Language Models and their associated operations.
         */
        @JvmField
        public val Anthropic: LLMProvider = AnthropicLLMProvider()

        /**
         * Represents the Google provider for Large Language Models (LLMs).
         *
         * This constant is used as a predefined identifier within the [LLMProvider] class
         * to specify that the associated LLM is provided by Google. It is commonly used
         * to differentiate between various LLM providers when configuring or selecting
         * an LLM instance.
         */
        @JvmField
        public val Google: LLMProvider = GoogleLLMProvider()

        /**
         * Represents the Meta large language model (LLM) provider.
         *
         * This value is a predefined instance of [LLMProvider] specifically for the Meta provider.
         * It can be used to configure or interact with Meta-based language models.
         */
        @JvmField
        public val Meta: LLMProvider = MetaLLMProvider()

        /**
         * Represents the Alibaba Large Language Model (LLM) provider.
         *
         * This constant is part of the available LLM providers within the system
         * and is used to signify the selection or identification of the Alibaba LLM
         * within model configurations or operations.
         */
        @JvmField
        public val Alibaba: LLMProvider = AlibabaLLMProvider()

        /**
         * Represents the OpenRouter provider for large language models (LLMs).
         * This constant is part of the available LLM providers within the [LLMProvider] enumeration.
         */
        @JvmField
        public val OpenRouter: LLMProvider = OpenRouterLLMProvider()

        /**
         * Represents the Ollama provider for a Large Language Model (LLM).
         *
         * This constant is a predefined instance of [LLMProvider] representing the Ollama provider.
         * It can be used to specify or identify the provider associated with an LLM instance.
         */
        @JvmField
        public val Ollama: LLMProvider = OllamaLLMProvider()

        /**
         * Represents the Bedrock provider for Large Language Models (LLMs).
         *
         * This constant is part of the [LLMProvider] enumeration and is used to specify the Bedrock
         * LLM service. It can be utilized to identify or configure functionality specific to the Bedrock provider.
         */
        @JvmField
        public val Bedrock: LLMProvider = BedrockLLMProvider()

        /**
         * Represents the DeepSeek LLM provider, a predefined member of the [LLMProvider] class.
         *
         * DeepSeek is a specific implementation or configuration of a large language model (LLM),
         * designed to handle advanced queries with precision and extended capabilities. It can be
         * used to identify, execute, or assist in tasks requiring substantial context understanding.
         */
        @JvmField
        public val DeepSeek: LLMProvider = DeepSeekLLMProvider()

        /**
         * Represents the MistralAI Large Language Model (LLM) provider.
         *
         * This constant identifies the MistralAI LLM provider, which can be used to configure and interact with
         * instances of MistralAI language models in the system.
         */
        @JvmField
        public val MistralAI: LLMProvider = MistralAILLMProvider()

        /**
         * Represents the Oracle Cloud Infrastructure (OCI) Generative AI provider.
         */
        @JvmField
        public val OCI: LLMProvider = OCILLMProvider()

        /**
         * Represents the MiniMax Large Language Model provider.
         */
        @JvmField
        public val MiniMax: LLMProvider = MiniMaxLLMProvider()

        /**
         * Represents the ZhipuAI (智谱AI) Large Language Model provider.
         */
        @JvmField
        public val ZhipuAI: LLMProvider = ZhipuAILLMProvider()

        /**
         * Represents the Hugging Face Large Language Model provider.
         */
        @JvmField
        public val HuggingFace: LLMProvider = HuggingFaceLLMProvider()

        /**
         * Represents the Azure OpenAI provider.
         */
        @JvmField
        public val Azure: LLMProvider = AzureLLMProvider()

        /**
         * Represents the Google VertexAI provider.
         */
        @JvmField
        public val Vertex: LLMProvider = VertexLLMProvider()
    }
}

/**
 * Represents a specialized implementation of the `LLMProvider` class corresponding to the Google provider.
 *
 * The `Google` object is a predefined instance of `LLMProvider`, with its `id` and `display` properties
 * set to "google" and "Google" respectively. It serves as an enumeration-like representation to
 * identify and work with the Google Large Language Model provider in the system.
 *
 * This object can be used in situations where the provider-specific attributes or operations
 * related to Google's language model are required.
 */
@Serializable
public class GoogleLLMProvider : LLMProvider("google", "Google")

/**
 * Represents the OpenAI provider in the Large Language Model (LLM) ecosystem.
 *
 * OpenAI, identified by the `id` value "openai", is a specific implementation
 * of the `LLMProvider` sealed class. This class is used to define and distinguish
 * the OpenAI provider as part of the supported LLM providers.
 *
 * This provider can be utilized to configure LLM-based models, allowing developers
 * to leverage OpenAI's capabilities within various applications or systems.
 */
@Serializable
public class OpenAILLMProvider : LLMProvider("openai", "OpenAI")

/**
 * Represents the Anthropic LLM provider.
 *
 * This class is a concrete instance of the `LLMProvider` sealed class, specifically for Anthropic.
 * It defines the unique identifier and display name associated with Anthropic as a provider of large language models.
 *
 * Use this object to reference or configure language models provided by Anthropic in the context of an LLM system.
 */
@Serializable
public class AnthropicLLMProvider : LLMProvider("anthropic", "Anthropic")

/**
 * Represents the "Meta" large language model provider in the system.
 *
 * The `Meta` object is a concrete implementation of the `LLMProvider` class, identifying the Meta provider
 * with a predefined `id` and `display` name. It is used to associate models and capabilities specific to the
 * Meta platform across the application.
 */
@Serializable
public class MetaLLMProvider : LLMProvider("meta", "Meta")

/**
 * Represents Alibaba as a specific provider of Large Language Models (LLMs).
 *
 * This class is a subclass of the `LLMProvider` sealed class, and it defines
 * Alibaba's unique identifier and display name. It is used in configurations or model
 * selections to specify Alibaba as the chosen provider.
 */
@Serializable
public class AlibabaLLMProvider : LLMProvider("alibaba", "Alibaba")

/**
 * Represents the OpenRouter provider within the available set of large language model providers.
 *
 * OpenRouter is identified by its unique ID ("openrouter") and display name ("OpenRouter").
 * It extends the `LLMProvider` sealed class, which serves as a base class for all supported language model providers.
 *
 * This class adheres to the structure and serialization requirements defined by the parent class.
 * It is part of the available LLM provider hierarchy, which is used to configure and identify specific
 * providers for large language model functionalities and capabilities.
 */
@Serializable
public class OpenRouterLLMProvider : LLMProvider("openrouter", "OpenRouter")

/**
 * Represents the Ollama provider within the available set of large language model providers.
 *
 * Ollama is identified by its unique ID ("ollama") and display name ("Ollama").
 * It extends the `LLMProvider` sealed class, which serves as a base class for all supported language model providers.
 *
 * This class adheres to the structure and serialization requirements defined by the parent class.
 * It is part of the available LLM provider hierarchy, which is used to configure and identify specific
 * providers for large language model functionalities and capabilities.
 */
@Serializable
public class OllamaLLMProvider : LLMProvider("ollama", "Ollama")

/**
 * Represents the AWS Bedrock provider within the available set of large language model providers.
 *
 * Bedrock is identified by its unique ID ("bedrock") and display name ("AWS Bedrock").
 * It extends the `LLMProvider` sealed class, which serves as a base class for all supported language model providers.
 *
 * This class adheres to the structure and serialization requirements defined by the parent class.
 * It is part of the available LLM provider hierarchy, which is used to configure and identify specific
 * providers for large language model functionalities and capabilities.
 */
@Serializable
public class BedrockLLMProvider : LLMProvider("bedrock", "AWS Bedrock")

/**
 * Represents the DeepSeek provider within the available set of large language model providers.
 *
 * DeepSeek is identified by its unique ID ("deepseek") and display name ("DeepSeek").
 * It extends the `LLMProvider` sealed class,
 * which serves as a base class for all supported language model providers.
 *
 * This class adheres to the structure and serialization requirements defined by the parent class.
 * It is part of the available LLM provider hierarchy, which is used to configure and identify specific
 * providers for large language model functionalities and capabilities.
 */
@Serializable
public class DeepSeekLLMProvider : LLMProvider("deepseek", "DeepSeek")

/**
 * Represents the Mistral AI provider within the available set of large language model providers.
 *
 * MistralAI is identified by its unique ID ("mistralai") and display name ("MistralAI").
 * It extends the `LLMProvider` sealed class,
 * which serves as a base class for all supported language model providers.
 *
 * This class adheres to the structure and serialization requirements defined by the parent class.
 * It is part of the available LLM provider hierarchy, which is used to configure and identify specific
 * providers for large language model functionalities and capabilities.
 */
@Serializable
public class MistralAILLMProvider : LLMProvider("mistralai", "MistralAI")

/**
 * Represents the Oracle Cloud Infrastructure (OCI) Generative AI provider.
 *
 * Oracle is identified by its unique ID ("oci") and display name ("OCI").
 */
@Serializable
public class OCILLMProvider : LLMProvider("oci", "OCI")

/**
 * Represents the MiniMax Large Language Model provider.
 *
 * MiniMax is identified by its unique ID ("minimax") and display name ("MiniMax").
 */
@Serializable
public class MiniMaxLLMProvider : LLMProvider("minimax", "MiniMax")

/**
 * Represents the ZhipuAI (智谱AI) Large Language Model provider.
 *
 * ZhipuAI is identified by its unique ID ("zhipuai") and display name ("ZhipuAI").
 */
@Serializable
public class ZhipuAILLMProvider : LLMProvider("zhipuai", "ZhipuAI")

/**
 * Represents the Hugging Face Large Language Model provider.
 *
 * HuggingFace is identified by its unique ID ("huggingface") and display name ("Hugging Face").
 */
@Serializable
public class HuggingFaceLLMProvider : LLMProvider("huggingface", "Hugging Face")

/**
 * Represents the Azure OpenAI provider.
 *
 * ElevenLabs is identified by its unique ID ("azure") and display name ("Azure OpenAI").
 */
@Serializable
public class AzureLLMProvider : LLMProvider("azure", "Azure OpenAI")

/**
 * Represents the Google VertexAI provider.
 *
 * StabilityAI is identified by its unique ID ("vertex") and display name ("Google VertexAI").
 */
@Serializable
public class VertexLLMProvider : LLMProvider("vertex", "Google VertexAI")
