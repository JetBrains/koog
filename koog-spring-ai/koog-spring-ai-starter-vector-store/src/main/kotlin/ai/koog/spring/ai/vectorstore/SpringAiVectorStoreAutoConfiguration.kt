package ai.koog.spring.ai.vectorstore

import ai.koog.spring.ai.common.conditions.ConditionalOnPropertyMissingOrEmpty
import ai.koog.spring.ai.common.conditions.ConditionalOnPropertyNotEmpty
import ai.koog.spring.ai.common.resolveDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import org.slf4j.LoggerFactory
import org.springframework.ai.vectorstore.VectorStore
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
 * Auto-configuration for adapting a Spring AI [VectorStore] to Koog storage abstractions.
 *
 * This configuration:
 * - Binds [KoogSpringAiVectorStoreProperties] under `koog.spring.ai.vectorstore.*`.
 * - Creates a [KoogVectorStore] backed by a Spring AI [VectorStore] when available.
 * - Supports multi-store contexts via property-based bean-name selection.
 * - Provides an injectable [CoroutineDispatcher] for blocking vector-store calls.
 *
 * Gated by `koog.spring.ai.vectorstore.enabled=true` (default).
 */
@AutoConfiguration(
    afterName = [
        "org.springframework.ai.vectorstore.azure.autoconfigure.AzureVectorStoreAutoConfiguration",
        "org.springframework.ai.vectorstore.cosmosdb.autoconfigure.CosmosDBVectorStoreAutoConfiguration",
        "org.springframework.ai.vectorstore.cassandra.autoconfigure.CassandraVectorStoreAutoConfiguration",
        "org.springframework.ai.vectorstore.couchbase.autoconfigure.CouchbaseSearchVectorStoreAutoConfiguration",
        "org.springframework.ai.vectorstore.chroma.autoconfigure.ChromaVectorStoreAutoConfiguration",
        "org.springframework.ai.vectorstore.elasticsearch.autoconfigure.ElasticsearchVectorStoreAutoConfiguration",
        "org.springframework.ai.vectorstore.gemfire.autoconfigure.GemFireVectorStoreAutoConfiguration",
        "org.springframework.ai.vectorstore.milvus.autoconfigure.MilvusVectorStoreAutoConfiguration",
        "org.springframework.ai.vectorstore.mongodb.autoconfigure.MongoDBAtlasVectorStoreAutoConfiguration",
        "org.springframework.ai.vectorstore.neo4j.autoconfigure.Neo4jVectorStoreAutoConfiguration",
        "org.springframework.ai.vectorstore.opensearch.autoconfigure.OpenSearchVectorStoreAutoConfiguration",
        "org.springframework.ai.vectorstore.oracle.autoconfigure.OracleVectorStoreAutoConfiguration",
        "org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration",
        "org.springframework.ai.vectorstore.pinecone.autoconfigure.PineconeVectorStoreAutoConfiguration",
        "org.springframework.ai.vectorstore.qdrant.autoconfigure.QdrantVectorStoreAutoConfiguration",
        "org.springframework.ai.vectorstore.redis.autoconfigure.RedisVectorStoreAutoConfiguration",
        "org.springframework.ai.vectorstore.typesense.autoconfigure.TypesenseVectorStoreAutoConfiguration",
        "org.springframework.ai.vectorstore.weaviate.autoconfigure.WeaviateVectorStoreAutoConfiguration"
    ]
)
@EnableConfigurationProperties(KoogSpringAiVectorStoreProperties::class)
@ConditionalOnClass(VectorStore::class)
@ConditionalOnProperty(
    prefix = "koog.spring.ai.vectorstore",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
public open class SpringAiVectorStoreAutoConfiguration {

    private val logger = LoggerFactory.getLogger(SpringAiVectorStoreAutoConfiguration::class.java)

    /**
     * Creates a [CoroutineDispatcher] for blocking Spring AI vector-store calls.
     *
     * Dispatcher selection is controlled by [KoogSpringAiVectorStoreProperties.dispatcher]:
     * - [ai.koog.spring.ai.common.DispatcherType.AUTO]: uses Spring's `AsyncTaskExecutor` when available
     *   (e.g. virtual-thread executor with `spring.threads.virtual.enabled=true`), otherwise falls back to
     *   `Dispatchers.IO`.
     * - [ai.koog.spring.ai.common.DispatcherType.IO]: always uses `Dispatchers.IO`, optionally limited to
     *   `dispatcher.parallelism` threads.
     */
    @Bean
    @ConditionalOnMissingBean(name = ["koogSpringAiVectorStoreDispatcher"])
    public open fun koogSpringAiVectorStoreDispatcher(
        properties: KoogSpringAiVectorStoreProperties,
        @Qualifier("applicationTaskExecutor") asyncTaskExecutorProvider: ObjectProvider<AsyncTaskExecutor>,
    ): CoroutineDispatcher {
        return resolveDispatcher(
            dispatcherConfig = properties.dispatcher,
            asyncTaskExecutor = asyncTaskExecutorProvider.ifAvailable,
            logger = logger,
            componentName = "Koog Spring AI VectorStore",
        )
    }

    /**
     * VectorStore configuration — activated when a bean-name selector is provided.
     *
     * Resolves the [VectorStore] from the application context by the configured bean name.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnPropertyNotEmpty(prefix = "koog.spring.ai.vectorstore", name = "vector-store-bean-name")
    public open class NamedVectorStoreConfiguration {
        private val logger = LoggerFactory.getLogger(NamedVectorStoreConfiguration::class.java)

        @Bean
        @ConditionalOnMissingBean(KoogVectorStore::class)
        public open fun springAiKoogVectorStore(
            beanFactory: BeanFactory,
            properties: KoogSpringAiVectorStoreProperties,
            @Qualifier("koogSpringAiVectorStoreDispatcher") dispatcher: CoroutineDispatcher,
        ): KoogVectorStore {
            val beanName = properties.vectorStoreBeanName!!
            logger.info("Koog Spring AI VectorStore: resolving VectorStore bean by name='{}'", beanName)
            val vectorStore = beanFactory.getBean(beanName, VectorStore::class.java)
            return SpringAiKoogVectorStore(vectorStore = vectorStore, dispatcher = dispatcher)
        }
    }

    /**
     * VectorStore configuration — activated when no bean-name selector is set and a single VectorStore candidate exists.
     *
     * This is the default fallback path. It is mutually exclusive with [NamedVectorStoreConfiguration] for
     * the common cases:
     * - selector absent → [ConditionalOnPropertyMissingOrEmpty] activates this config; [NamedVectorStoreConfiguration] does not match
     * - selector non-empty (e.g. `"myBean"`) → [NamedVectorStoreConfiguration] matches; [ConditionalOnPropertyMissingOrEmpty] does not activate
     * - selector set to literal `""` → treated as missing/empty; [ConditionalOnPropertyMissingOrEmpty] activates this config; [NamedVectorStoreConfiguration] does not match
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnSingleCandidate(VectorStore::class)
    @ConditionalOnPropertyMissingOrEmpty(prefix = "koog.spring.ai.vectorstore", name = "vector-store-bean-name")
    public open class SingleVectorStoreConfiguration {
        private val logger = LoggerFactory.getLogger(SingleVectorStoreConfiguration::class.java)

        @Bean
        @ConditionalOnMissingBean(KoogVectorStore::class)
        public open fun springAiKoogVectorStore(
            vectorStore: VectorStore,
            @Qualifier("koogSpringAiVectorStoreDispatcher") dispatcher: CoroutineDispatcher,
        ): KoogVectorStore {
            logger.info("Koog Spring AI VectorStore: using single VectorStore candidate as Koog storage backend")
            return SpringAiKoogVectorStore(vectorStore = vectorStore, dispatcher = dispatcher)
        }
    }
}
