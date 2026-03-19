package ai.koog.spring.ai.vectorstore

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import org.slf4j.LoggerFactory
import org.springframework.ai.vectorstore.VectorStore
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
     */
    @Bean
    @ConditionalOnMissingBean(name = ["koogSpringAiVectorStoreDispatcher"])
    public open fun koogSpringAiVectorStoreDispatcher(
        properties: KoogSpringAiVectorStoreProperties,
        @Autowired(required = false) @Qualifier("applicationTaskExecutor") @Nullable asyncTaskExecutor: AsyncTaskExecutor?,
    ): CoroutineDispatcher {
        return when (properties.dispatcher.type) {
            KoogSpringAiVectorStoreProperties.DispatcherType.AUTO -> {
                if (asyncTaskExecutor != null) {
                    logger.info("Koog Spring AI VectorStore: using Spring AsyncTaskExecutor as dispatcher for blocking vector-store calls")
                    asyncTaskExecutor.asCoroutineDispatcher()
                } else {
                    logger.info("Koog Spring AI VectorStore: no AsyncTaskExecutor found, falling back to Dispatchers.IO")
                    Dispatchers.IO
                }
            }

            KoogSpringAiVectorStoreProperties.DispatcherType.IO -> {
                val parallelism = properties.dispatcher.parallelism
                if (parallelism > 0) {
                    logger.info("Koog Spring AI VectorStore: using Dispatchers.IO.limitedParallelism($parallelism)")
                    Dispatchers.IO.limitedParallelism(parallelism)
                } else {
                    logger.info("Koog Spring AI VectorStore: using Dispatchers.IO")
                    Dispatchers.IO
                }
            }
        }
    }

    /**
     * VectorStore configuration — activated when a bean-name selector is provided.
     */
    @Configuration
    @ConditionalOnProperty(prefix = "koog.spring.ai.vectorstore", name = ["vector-store-bean-name"])
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
     */
    @Configuration
    @ConditionalOnMissingBean(KoogVectorStore::class)
    @ConditionalOnSingleCandidate(VectorStore::class)
    public open class SingleVectorStoreConfiguration {
        private val logger = LoggerFactory.getLogger(SingleVectorStoreConfiguration::class.java)

        @Bean
        public open fun springAiKoogVectorStore(
            vectorStore: VectorStore,
            @Qualifier("koogSpringAiVectorStoreDispatcher") dispatcher: CoroutineDispatcher,
        ): KoogVectorStore {
            logger.info("Koog Spring AI VectorStore: using single VectorStore candidate as Koog storage backend")
            return SpringAiKoogVectorStore(vectorStore = vectorStore, dispatcher = dispatcher)
        }
    }
}
