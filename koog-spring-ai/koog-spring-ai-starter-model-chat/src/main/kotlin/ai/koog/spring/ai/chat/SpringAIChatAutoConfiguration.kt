package ai.koog.spring.ai.chat

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.moderation.ModerationModel
import org.springframework.beans.factory.BeanFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.lang.Nullable
import java.util.concurrent.Executors

/**
 * Auto-configuration for the Koog Spring AI Chat Model adapter.
 *
 * This configuration:
 * - Binds [KoogSpringAIChatProperties] under `koog.spring-ai.chat.*`.
 * - Creates an [LLMClient] backed by a Spring AI [ChatModel] when available.
 * - Creates a [PromptExecutor] when an [LLMClient] is available.
 * - Supports multi-model contexts via property-based bean-name selection.
 * - Provides an injectable [CoroutineDispatcher] for blocking model calls.
 * - Optionally injects [ModerationModel] into the [LLMClient] bean.
 *
 * Gated by `koog.spring-ai.chat.enabled=true` (default).
 */
@AutoConfiguration(
    afterName = [
        "org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatAutoConfiguration",
        "org.springframework.ai.model.azure.openai.autoconfigure.AzureOpenAiChatAutoConfiguration",
        "org.springframework.ai.model.bedrock.converse.autoconfigure.BedrockConverseProxyChatAutoConfiguration",
        "org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatAutoConfiguration",
        "org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration",
        "org.springframework.ai.model.huggingface.autoconfigure.HuggingfaceChatAutoConfiguration",
        "org.springframework.ai.model.minimax.autoconfigure.MiniMaxChatAutoConfiguration",
        "org.springframework.ai.model.mistralai.autoconfigure.MistralAiChatAutoConfiguration",
        "org.springframework.ai.model.oci.genai.autoconfigure.OCIGenAiChatAutoConfiguration",
        "org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration",
        "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration",
        "org.springframework.ai.model.openaisdk.autoconfigure.OpenAiSdkChatAutoConfiguration",
        "org.springframework.ai.model.vertexai.autoconfigure.gemini.VertexAiGeminiChatAutoConfiguration",
        "org.springframework.ai.model.zhipuai.autoconfigure.ZhiPuAiChatAutoConfiguration"
    ]
)
@EnableConfigurationProperties(KoogSpringAIChatProperties::class)
@ConditionalOnClass(ChatModel::class)
@ConditionalOnProperty(prefix = "koog.spring-ai.chat", name = ["enabled"], havingValue = "true", matchIfMissing = true)
public open class SpringAIChatAutoConfiguration {

    private val logger = LoggerFactory.getLogger(SpringAIChatAutoConfiguration::class.java)

    /**
     * Creates a [CoroutineDispatcher] for blocking Spring AI chat model calls.
     */
    @Bean
    @ConditionalOnMissingBean(name = ["koogSpringAIChatDispatcher"])
    public open fun koogSpringAIChatDispatcher(
        properties: KoogSpringAIChatProperties,
        @Autowired(required = false) @Qualifier("applicationTaskExecutor") @Nullable asyncTaskExecutor: AsyncTaskExecutor?,
    ): CoroutineDispatcher {
        return when (properties.dispatcher.type) {
            KoogSpringAIChatProperties.DispatcherType.AUTO -> {
                if (asyncTaskExecutor != null) {
                    logger.info("Koog Spring AI Chat: using Spring AsyncTaskExecutor as dispatcher for blocking model calls")
                    asyncTaskExecutor.asCoroutineDispatcher()
                } else {
                    logger.info("Koog Spring AI Chat: no AsyncTaskExecutor found, falling back to Dispatchers.IO for blocking model calls")
                    Dispatchers.IO
                }
            }

            KoogSpringAIChatProperties.DispatcherType.IO -> {
                logger.info("Koog Spring AI Chat: using Dispatchers.IO for blocking model calls")
                Dispatchers.IO
            }

            KoogSpringAIChatProperties.DispatcherType.FIXED_THREAD_POOL -> {
                val parallelism = properties.dispatcher.parallelism.takeIf { it > 0 }
                    ?: Runtime.getRuntime().availableProcessors()
                logger.info("Koog Spring AI Chat: using fixed thread pool with parallelism=$parallelism for blocking model calls")
                val executor = Executors.newFixedThreadPool(parallelism)
                val delegate: ExecutorCoroutineDispatcher = executor.asCoroutineDispatcher()
                object : ExecutorCoroutineDispatcher(), DisposableBean {
                    override val executor: java.util.concurrent.Executor get() = executor
                    override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) =
                        delegate.dispatch(context, block)

                    override fun close() = delegate.close()
                    override fun destroy() {
                        logger.info("Koog Spring AI Chat: shutting down fixed thread pool dispatcher")
                        close()
                    }
                }
            }
        }
    }

    /**
     * Chat model configuration — activated when a bean-name selector is provided.
     */
    @Configuration
    @ConditionalOnProperty(prefix = "koog.spring-ai.chat", name = ["chat-model-bean-name"])
    public open class NamedChatModelConfiguration {
        private val logger = LoggerFactory.getLogger(NamedChatModelConfiguration::class.java)

        @Bean
        @ConditionalOnMissingBean(LLMClient::class)
        public open fun springAIChatModelLLMClient(
            beanFactory: BeanFactory,
            properties: KoogSpringAIChatProperties,
            @Qualifier("koogSpringAIChatDispatcher") dispatcher: CoroutineDispatcher,
            @Autowired(required = false) @Nullable chatOptionsCustomizer: ChatOptionsCustomizer?,
            moderationModelProvider: ObjectProvider<ModerationModel>,
        ): LLMClient {
            val beanName = properties.chatModelBeanName!!
            logger.info("Koog Spring AI Chat: resolving ChatModel bean by name='$beanName'")
            val chatModel = beanFactory.getBean(beanName, ChatModel::class.java)
            val resolvedModerationModel: ModerationModel? = properties.moderationModelBeanName
                ?.also { logger.info("Koog Spring AI Chat: resolving ModerationModel bean by name='$it'") }
                ?.let { beanFactory.getBean(it, ModerationModel::class.java) }
                ?: moderationModelProvider.ifUnique
            return SpringAILLMClient(
                chatModel,
                dispatcher = dispatcher,
                chatOptionsCustomizer = chatOptionsCustomizer ?: ChatOptionsCustomizer.NOOP,
                moderationModel = resolvedModerationModel,
            )
        }
    }

    /**
     * Chat model configuration — activated when no bean-name selector is set and a single ChatModel candidate exists.
     */
    @Configuration
    @ConditionalOnMissingBean(LLMClient::class)
    @ConditionalOnSingleCandidate(ChatModel::class)
    public open class SingleChatModelConfiguration {
        private val logger = LoggerFactory.getLogger(SingleChatModelConfiguration::class.java)

        @Bean
        public open fun springAIChatModelLLMClient(
            chatModel: ChatModel,
            beanFactory: BeanFactory,
            properties: KoogSpringAIChatProperties,
            @Qualifier("koogSpringAIChatDispatcher") dispatcher: CoroutineDispatcher,
            @Autowired(required = false) @Nullable chatOptionsCustomizer: ChatOptionsCustomizer?,
            moderationModelProvider: ObjectProvider<ModerationModel>,
        ): LLMClient {
            logger.info("Koog Spring AI Chat: using single ChatModel candidate as LLMClient backend")
            val moderationModel: ModerationModel? = properties.moderationModelBeanName
                ?.also { logger.info("Koog Spring AI Chat: resolving ModerationModel bean by name='$it'") }
                ?.let { beanFactory.getBean(it, ModerationModel::class.java) }
                ?: moderationModelProvider.ifUnique
            return SpringAILLMClient(
                chatModel,
                dispatcher = dispatcher,
                chatOptionsCustomizer = chatOptionsCustomizer ?: ChatOptionsCustomizer.NOOP,
                moderationModel = moderationModel,
            )
        }
    }

    /**
     * Creates a [MultiLLMPromptExecutor] from all available [LLMClient] beans.
     */
    @Bean
    @ConditionalOnBean(LLMClient::class)
    @ConditionalOnMissingBean(PromptExecutor::class)
    public open fun koogPromptExecutor(llmClientsProvider: ObjectProvider<LLMClient>): PromptExecutor {
        val llmClients = llmClientsProvider.orderedStream().toList()
        logger.info("Koog Spring AI Chat: creating MultiLLMPromptExecutor with {} LLMClient(s)", llmClients.size)
        return MultiLLMPromptExecutor(llmClients = llmClients.toTypedArray())
    }
}
