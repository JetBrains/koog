package ai.koog.spring.ai.memory

import ai.koog.agents.chatMemory.feature.ChatHistoryProvider
import ai.koog.spring.ai.common.DispatcherType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.memory.ChatMemoryRepository
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
        @Autowired(required = false) @Qualifier("applicationTaskExecutor") @Nullable asyncTaskExecutor: AsyncTaskExecutor?,
    ): CoroutineDispatcher {
        return when (properties.dispatcher.type) {
            DispatcherType.AUTO -> {
                if (asyncTaskExecutor != null) {
                    logger.info("Koog Spring AI Chat Memory: using Spring AsyncTaskExecutor as dispatcher")
                    asyncTaskExecutor.asCoroutineDispatcher()
                } else {
                    logger.info("Koog Spring AI Chat Memory: no AsyncTaskExecutor found, falling back to Dispatchers.IO")
                    Dispatchers.IO
                }
            }

            DispatcherType.IO -> {
                val parallelism = properties.dispatcher.parallelism
                if (parallelism > 0) {
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
     * Repository configuration — activated when a bean-name selector is provided.
     */
    @Configuration
    @ConditionalOnProperty(prefix = "koog.spring.ai.chat-memory", name = ["chat-memory-repository-bean-name"])
    public open class NamedRepositoryConfiguration {
        private val logger = LoggerFactory.getLogger(NamedRepositoryConfiguration::class.java)

        @Bean
        @ConditionalOnMissingBean(ChatHistoryProvider::class)
        public open fun springAiChatHistoryProvider(
            beanFactory: BeanFactory,
            properties: KoogSpringAiChatMemoryProperties,
            @Qualifier("koogSpringAiChatMemoryDispatcher") dispatcher: CoroutineDispatcher,
        ): ChatHistoryProvider {
            val beanName = properties.chatMemoryRepositoryBeanName!!
            logger.info("Koog Spring AI Chat Memory: resolving ChatMemoryRepository bean by name='$beanName' (text-only conversation memory)")
            val repository = beanFactory.getBean(beanName, ChatMemoryRepository::class.java)
            return SpringAiChatHistoryProvider(repository = repository, dispatcher = dispatcher)
        }
    }

    /**
     * Repository configuration — activated when no bean-name selector is set and a single ChatMemoryRepository candidate exists.
     */
    @Configuration
    @ConditionalOnMissingBean(ChatHistoryProvider::class)
    @ConditionalOnSingleCandidate(ChatMemoryRepository::class)
    public open class SingleRepositoryConfiguration {
        private val logger = LoggerFactory.getLogger(SingleRepositoryConfiguration::class.java)

        @Bean
        public open fun springAiChatHistoryProvider(
            repository: ChatMemoryRepository,
            @Qualifier("koogSpringAiChatMemoryDispatcher") dispatcher: CoroutineDispatcher,
        ): ChatHistoryProvider {
            logger.info("Koog Spring AI Chat Memory: creating text-only ChatHistoryProvider backed by single ChatMemoryRepository")
            return SpringAiChatHistoryProvider(repository = repository, dispatcher = dispatcher)
        }
    }
}
