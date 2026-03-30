package ai.koog.spring.ai.memory

import ai.koog.agents.chatMemory.feature.ChatHistoryProvider
import ai.koog.spring.ai.common.DispatcherProperties
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.memory.ChatMemoryRepository
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
 * Auto-configuration for the Koog Spring AI Chat Memory adapter.
 *
 * This configuration:
 * - Binds [KoogSpringAiChatMemoryProperties] under `koog.spring.ai.chat-memory.*`.
 * - Creates a [ChatHistoryProvider] backed by a Spring AI [ChatMemoryRepository] when available.
 * - Supports multi-repository contexts via property-based bean-name selection.
 * - Provides an injectable [CoroutineDispatcher] for blocking repository calls.
 *
 * Gated by `koog.spring.ai.chat-memory.enabled=true` (default).
 */
@AutoConfiguration(
    afterName = [
        "org.springframework.ai.model.chat.memory.repository.cassandra.autoconfigure.CassandraChatMemoryRepositoryAutoConfiguration",
        "org.springframework.ai.model.chat.memory.repository.cosmosdb.autoconfigure.CosmosDBChatMemoryRepositoryAutoConfiguration",
        "org.springframework.ai.model.chat.memory.repository.jdbc.autoconfigure.JdbcChatMemoryRepositoryAutoConfiguration",
        "org.springframework.ai.model.chat.memory.repository.neo4j.autoconfigure.Neo4jChatMemoryRepositoryAutoConfiguration",
        "org.springframework.ai.model.chat.memory.repository.mongo.autoconfigure.MongoChatMemoryAutoConfiguration"
    ]
)
@EnableConfigurationProperties(KoogSpringAiChatMemoryProperties::class)
@ConditionalOnClass(ChatMemoryRepository::class)
@ConditionalOnProperty(
    prefix = "koog.spring.ai.chat-memory",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
public open class SpringAiChatMemoryAutoConfiguration {

    private val logger = LoggerFactory.getLogger(SpringAiChatMemoryAutoConfiguration::class.java)

    /**
     * Creates a [CoroutineDispatcher] for blocking Spring AI chat memory repository calls.
     */
    @Bean
    @ConditionalOnMissingBean(name = ["koogSpringAiChatMemoryDispatcher"])
    public open fun koogSpringAiChatMemoryDispatcher(
        properties: KoogSpringAiChatMemoryProperties,
        @Qualifier("applicationTaskExecutor") asyncTaskExecutorProvider: ObjectProvider<AsyncTaskExecutor>,
    ): CoroutineDispatcher {
        val asyncTaskExecutor = asyncTaskExecutorProvider.ifAvailable
        return when (val dispatcher = properties.dispatcher.toDispatcherProperties()) {
            is DispatcherProperties.Auto -> {
                if (asyncTaskExecutor != null) {
                    logger.info("Koog Spring AI Chat Memory: using Spring AsyncTaskExecutor as dispatcher")
                    asyncTaskExecutor.asCoroutineDispatcher()
                } else {
                    logger.info("Koog Spring AI Chat Memory: no AsyncTaskExecutor found, falling back to Dispatchers.IO")
                    Dispatchers.IO
                }
            }

            is DispatcherProperties.IO -> {
                val parallelism = dispatcher.parallelism
                if (parallelism != null && parallelism > 0) {
                    logger.info("Koog Spring AI Chat Memory: using Dispatchers.IO.limitedParallelism($parallelism)")
                    Dispatchers.IO.limitedParallelism(parallelism)
                } else {
                    logger.info("Koog Spring AI Chat Memory: using Dispatchers.IO")
                    Dispatchers.IO
                }
            }
        }
    }

    /**
     * Chat memory repository configuration — activated when a bean-name selector property is provided.
     *
     * Resolves the [ChatMemoryRepository] from the application context by the configured bean name.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "koog.spring.ai.chat-memory", name = ["chat-memory-repository-bean-name"])
    public open class NamedChatMemoryRepositoryConfiguration {
        private val logger = LoggerFactory.getLogger(NamedChatMemoryRepositoryConfiguration::class.java)

        @Bean
        @ConditionalOnMissingBean(ChatHistoryProvider::class)
        public open fun springAiChatHistoryProvider(
            beanFactory: BeanFactory,
            properties: KoogSpringAiChatMemoryProperties,
            @Qualifier("koogSpringAiChatMemoryDispatcher") dispatcher: CoroutineDispatcher,
        ): ChatHistoryProvider {
            val beanName = properties.chatMemoryRepositoryBeanName!!
            logger.info("Koog Spring AI Chat Memory: resolving ChatMemoryRepository bean by name='$beanName' (text-only conversation memory)")
            val repo = beanFactory.getBean(beanName, ChatMemoryRepository::class.java)
            return SpringAiChatHistoryProvider(repository = repo, dispatcher = dispatcher)
        }
    }

    /**
     * Chat memory repository configuration — activated when no bean-name selector is set
     * and a single [ChatMemoryRepository] candidate exists.
     *
     * This is the default fallback path. It is mutually exclusive with [NamedChatMemoryRepositoryConfiguration] for
     * the common cases:
     * - selector absent → `matchIfMissing = true` activates this config; [NamedChatMemoryRepositoryConfiguration] does not match
     * - selector non-empty (e.g. `"myBean"`) → [NamedChatMemoryRepositoryConfiguration] matches; `havingValue = ""` does not
     *   match a non-empty value, so this config does not activate
     * - selector set to literal `""` → both configs activate (Spring treats `""` as a present value that satisfies
     *   both `havingValue = ""` and the plain `@ConditionalOnProperty` on the named path); the named path then
     *   attempts `beanFactory.getBean("", ChatMemoryRepository::class.java)` which fails at startup
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnSingleCandidate(ChatMemoryRepository::class)
    @ConditionalOnProperty(
        prefix = "koog.spring.ai.chat-memory",
        name = ["chat-memory-repository-bean-name"],
        havingValue = "",
        matchIfMissing = true
    )
    public open class SingleChatMemoryRepositoryConfiguration {
        private val logger = LoggerFactory.getLogger(SingleChatMemoryRepositoryConfiguration::class.java)

        @Bean
        @ConditionalOnMissingBean(ChatHistoryProvider::class)
        public open fun springAiChatHistoryProvider(
            repository: ChatMemoryRepository,
            @Qualifier("koogSpringAiChatMemoryDispatcher") dispatcher: CoroutineDispatcher,
        ): ChatHistoryProvider {
            logger.info("Koog Spring AI Chat Memory: using single ChatMemoryRepository candidate as ChatHistoryProvider backend")
            return SpringAiChatHistoryProvider(repository = repository, dispatcher = dispatcher)
        }
    }
}
