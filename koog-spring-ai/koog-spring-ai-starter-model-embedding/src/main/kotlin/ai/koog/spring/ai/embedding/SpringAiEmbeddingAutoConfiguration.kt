package ai.koog.spring.ai.embedding

import ai.koog.prompt.executor.clients.LLMEmbeddingProvider
import ai.koog.spring.ai.common.conditions.ConditionalOnPropertyMissingOrEmpty
import ai.koog.spring.ai.common.conditions.ConditionalOnPropertyNotEmpty
import ai.koog.spring.ai.common.resolveDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import org.slf4j.LoggerFactory
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.beans.factory.BeanFactory
import org.springframework.beans.factory.ObjectProvider
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
        @Qualifier("applicationTaskExecutor") asyncTaskExecutorProvider: ObjectProvider<AsyncTaskExecutor>,
    ): CoroutineDispatcher {
        return resolveDispatcher(
            dispatcherConfig = properties.dispatcher,
            asyncTaskExecutor = asyncTaskExecutorProvider.ifAvailable,
            logger = logger,
            componentName = "Koog Spring AI Embedding",
        )
    }

    /**
     * Embedding model configuration — activated when a bean-name selector is provided.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnPropertyNotEmpty(prefix = "koog.spring.ai.embedding", name = "embedding-model-bean-name")
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
     * Embedding model configuration — activated when no bean-name selector is set
     * and a single [EmbeddingModel] candidate exists.
     *
     * This is the default fallback path. It is mutually exclusive with [NamedEmbeddingModelConfiguration] for
     * the common cases:
     * - selector absent → [ConditionalOnPropertyMissingOrEmpty] activates this config; [NamedEmbeddingModelConfiguration] does not match
     * - selector non-empty (e.g. `"myBean"`) → [NamedEmbeddingModelConfiguration] matches; [ConditionalOnPropertyMissingOrEmpty] does not activate
     * - selector set to literal `""` → treated as missing/empty; [ConditionalOnPropertyMissingOrEmpty] activates this config; [NamedEmbeddingModelConfiguration] does not match
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnSingleCandidate(EmbeddingModel::class)
    @ConditionalOnPropertyMissingOrEmpty(prefix = "koog.spring.ai.embedding", name = "embedding-model-bean-name")
    public open class SingleEmbeddingModelConfiguration {
        private val logger = LoggerFactory.getLogger(SingleEmbeddingModelConfiguration::class.java)

        @Bean
        @ConditionalOnMissingBean(LLMEmbeddingProvider::class)
        public open fun springAiEmbeddingModelLLMEmbeddingProvider(
            embeddingModel: EmbeddingModel,
            @Qualifier("koogSpringAiEmbeddingDispatcher") dispatcher: CoroutineDispatcher,
        ): LLMEmbeddingProvider {
            logger.info("Koog Spring AI Embedding: using single EmbeddingModel candidate as LLMEmbeddingProvider backend")
            return SpringAiLLMEmbeddingProvider(embeddingModel = embeddingModel, dispatcher = dispatcher)
        }
    }
}
