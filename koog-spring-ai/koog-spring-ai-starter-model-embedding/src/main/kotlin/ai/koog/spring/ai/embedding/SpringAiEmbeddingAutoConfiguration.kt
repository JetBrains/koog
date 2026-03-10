package ai.koog.spring.ai.embedding

import ai.koog.prompt.executor.clients.LLMEmbeddingProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import org.slf4j.LoggerFactory
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.beans.factory.BeanFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.lang.Nullable

/**
 * Auto-configuration for the Koog Spring AI Embedding Model adapter.
 *
 * This configuration:
 * - Binds [KoogSpringAiEmbeddingProperties] under `koog.spring.ai.embedding.*`.
 * - Creates an [LLMEmbeddingProvider] backed by a Spring AI [EmbeddingModel] when available.
 * - Supports multi-model contexts via property-based bean-name selection.
 * - Provides an injectable [CoroutineDispatcher] for blocking model calls.
 *
 * Gated by `koog.spring.ai.embedding.enabled=true` (default).
 */
@AutoConfiguration(
    afterName = [
        "org.springframework.ai.model.anthropic.autoconfigure.AnthropicEmbeddingAutoConfiguration",
        "org.springframework.ai.model.azure.openai.autoconfigure.AzureOpenAiEmbeddingAutoConfiguration",
        "org.springframework.ai.model.bedrock.cohere.autoconfigure.BedrockCohereEmbeddingAutoConfiguration",
        "org.springframework.ai.model.bedrock.titan.autoconfigure.BedrockTitanEmbeddingAutoConfiguration",
        "org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiTextEmbeddingAutoConfiguration",
        "org.springframework.ai.model.huggingface.autoconfigure.HuggingfaceEmbeddingAutoConfiguration",
        "org.springframework.ai.model.mistralai.autoconfigure.MistralAiEmbeddingAutoConfiguration",
        "org.springframework.ai.model.oci.genai.autoconfigure.OCIGenAiEmbeddingAutoConfiguration",
        "org.springframework.ai.model.ollama.autoconfigure.OllamaEmbeddingAutoConfiguration",
        "org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration",
        "org.springframework.ai.model.transformers.autoconfigure.TransformersEmbeddingModelAutoConfiguration",
        "org.springframework.ai.model.vertexai.autoconfigure.embedding.VertexAiTextEmbeddingAutoConfiguration",
        "org.springframework.ai.model.zhipuai.autoconfigure.ZhiPuAiEmbeddingAutoConfiguration"
    ]
)
@EnableConfigurationProperties(KoogSpringAiEmbeddingProperties::class)
@ConditionalOnClass(EmbeddingModel::class)
@ConditionalOnProperty(prefix = "koog.spring.ai.embedding", name = ["enabled"], havingValue = "true", matchIfMissing = true)
public open class SpringAiEmbeddingAutoConfiguration {

    private val logger = LoggerFactory.getLogger(SpringAiEmbeddingAutoConfiguration::class.java)

    /**
     * Creates a [CoroutineDispatcher] for blocking Spring AI embedding model calls.
     */
    @Bean
    @ConditionalOnMissingBean(name = ["koogSpringAiEmbeddingDispatcher"])
    public open fun koogSpringAiEmbeddingDispatcher(
        properties: KoogSpringAiEmbeddingProperties,
        @Autowired(required = false) @Qualifier("applicationTaskExecutor") @Nullable asyncTaskExecutor: AsyncTaskExecutor?,
    ): CoroutineDispatcher {
        return when (properties.dispatcher.type) {
            KoogSpringAiEmbeddingProperties.DispatcherType.AUTO -> {
                if (asyncTaskExecutor != null) {
                    logger.info("Koog Spring AI Embedding: using Spring AsyncTaskExecutor as dispatcher for blocking model calls")
                    asyncTaskExecutor.asCoroutineDispatcher()
                } else {
                    logger.info("Koog Spring AI Embedding: no AsyncTaskExecutor found, falling back to Dispatchers.IO for blocking model calls")
                    Dispatchers.IO
                }
            }

            KoogSpringAiEmbeddingProperties.DispatcherType.IO -> {
                val parallelism = properties.dispatcher.parallelism
                if (parallelism > 0) {
                    logger.info("Koog Spring AI Embedding: using Dispatchers.IO.limitedParallelism($parallelism) for blocking model calls")
                    Dispatchers.IO.limitedParallelism(parallelism)
                } else {
                    logger.info("Koog Spring AI Embedding: using Dispatchers.IO for blocking model calls")
                    Dispatchers.IO
                }
            }
        }
    }

    /**
     * Embedding model configuration — activated when a bean-name selector is provided.
     */
    @Configuration
    @ConditionalOnProperty(prefix = "koog.spring.ai.embedding", name = ["embedding-model-bean-name"])
    public open class NamedEmbeddingModelConfiguration {
        private val logger = LoggerFactory.getLogger(NamedEmbeddingModelConfiguration::class.java)

        @Bean
        @ConditionalOnMissingBean(LLMEmbeddingProvider::class)
        public open fun springAiEmbeddingModelLLMEmbeddingProvider(
            beanFactory: BeanFactory,
            properties: KoogSpringAiEmbeddingProperties,
            @Qualifier("koogSpringAiEmbeddingDispatcher") dispatcher: CoroutineDispatcher,
        ): LLMEmbeddingProvider {
            val beanName = properties.embeddingModelBeanName!!
            logger.info("Koog Spring AI Embedding: resolving EmbeddingModel bean by name='$beanName'")
            val embeddingModel = beanFactory.getBean(beanName, EmbeddingModel::class.java)
            return SpringAiLLMEmbeddingProvider(embeddingModel = embeddingModel, dispatcher = dispatcher)
        }
    }

    /**
     * Embedding model configuration — activated when no bean-name selector is set and a single EmbeddingModel candidate exists.
     */
    @Configuration
    @ConditionalOnMissingBean(LLMEmbeddingProvider::class)
    @ConditionalOnSingleCandidate(EmbeddingModel::class)
    public open class SingleEmbeddingModelConfiguration {
        private val logger = LoggerFactory.getLogger(SingleEmbeddingModelConfiguration::class.java)

        @Bean
        public open fun springAiEmbeddingModelLLMEmbeddingProvider(
            embeddingModel: EmbeddingModel,
            @Qualifier("koogSpringAiEmbeddingDispatcher") dispatcher: CoroutineDispatcher,
        ): LLMEmbeddingProvider {
            logger.info("Koog Spring AI Embedding: using single EmbeddingModel candidate as LLMEmbeddingProvider backend")
            return SpringAiLLMEmbeddingProvider(embeddingModel = embeddingModel, dispatcher = dispatcher)
        }
    }
}
