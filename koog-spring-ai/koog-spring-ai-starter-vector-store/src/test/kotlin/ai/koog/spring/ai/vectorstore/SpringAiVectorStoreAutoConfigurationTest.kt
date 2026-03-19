package ai.koog.spring.ai.vectorstore

import ai.koog.rag.base.storage.DeletionStorage
import ai.koog.rag.base.storage.SearchStorage
import ai.koog.rag.base.storage.WriteStorage
import ai.koog.rag.base.storage.search.SimilaritySearchRequest
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertInstanceOf
import org.junit.jupiter.api.assertThrows
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.BeanCreationException
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.beans.factory.getBean
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.core.task.AsyncTaskExecutor

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SpringAiVectorStoreAutoConfigurationTest {

    private fun contextRunner(): ApplicationContextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(SpringAiVectorStoreAutoConfiguration::class.java))

    @Test
    fun `should not create Koog storage beans when no VectorStore is present`() {
        contextRunner().run { context ->
            assertThrows<NoSuchBeanDefinitionException> { context.getBean<KoogVectorStore>() }
            assertThrows<NoSuchBeanDefinitionException> { context.getBean<WriteStorage<DocumentWithMetadata>>() }
            assertThrows<NoSuchBeanDefinitionException> { context.getBean<SearchStorage<DocumentWithMetadata, SimilaritySearchRequest>>() }
            assertThrows<NoSuchBeanDefinitionException> { context.getBean<DeletionStorage>() }
        }
    }

    @Test
    fun `should create adapter and Koog storage beans when single VectorStore is present`() {
        contextRunner()
            .withBean(VectorStore::class.java, { mockk<VectorStore>(relaxed = true) })
            .run { context ->
                assertInstanceOf<SpringAiKoogVectorStore>(context.getBean<KoogVectorStore>())
                assertInstanceOf<SpringAiKoogVectorStore>(context.getBean<WriteStorage<DocumentWithMetadata>>())
                assertInstanceOf<SpringAiKoogVectorStore>(context.getBean<SearchStorage<DocumentWithMetadata, SimilaritySearchRequest>>())
                assertInstanceOf<SpringAiKoogVectorStore>(context.getBean<DeletionStorage>())
            }
    }

    @Test
    fun `should not create Koog storage beans when multiple VectorStores are present without selector`() {
        contextRunner()
            .withBean("store1", VectorStore::class.java, { mockk<VectorStore>(relaxed = true) })
            .withBean("store2", VectorStore::class.java, { mockk<VectorStore>(relaxed = true) })
            .run { context ->
                assertThrows<NoSuchBeanDefinitionException> { context.getBean<KoogVectorStore>() }
            }
    }

    @Test
    fun `should resolve VectorStore by bean name when configured`() {
        contextRunner()
            .withPropertyValues("koog.spring.ai.vectorstore.vector-store-bean-name=myVectorStore")
            .withBean("myVectorStore", VectorStore::class.java, { mockk<VectorStore>(relaxed = true) })
            .withBean("otherVectorStore", VectorStore::class.java, { mockk<VectorStore>(relaxed = true) })
            .run { context ->
                assertInstanceOf<SpringAiKoogVectorStore>(context.getBean<KoogVectorStore>())
            }
    }

    @Test
    fun `should not create beans when disabled`() {
        contextRunner()
            .withPropertyValues("koog.spring.ai.vectorstore.enabled=false")
            .withBean(VectorStore::class.java, { mockk<VectorStore>(relaxed = true) })
            .run { context ->
                assertThrows<NoSuchBeanDefinitionException> { context.getBean<KoogVectorStore>() }
                assertThrows<NoSuchBeanDefinitionException> { context.getBean("koogSpringAiVectorStoreDispatcher") }
            }
    }

    @Test
    fun `should create dispatcher bean`() {
        contextRunner().run { context ->
            assertNotNull(context.getBean("koogSpringAiVectorStoreDispatcher"))
        }
    }

    @Test
    fun `should bind properties`() {
        contextRunner()
            .withPropertyValues(
                "koog.spring.ai.vectorstore.enabled=true",
                "koog.spring.ai.vectorstore.vector-store-bean-name=vectorStore",
                "koog.spring.ai.vectorstore.dispatcher.type=IO"
            )
            .withBean("vectorStore", VectorStore::class.java, { mockk<VectorStore>(relaxed = true) })
            .run { context ->
                val properties = context.getBean<KoogSpringAiVectorStoreProperties>()
                assertTrue(properties.enabled)
                assertEquals("vectorStore", properties.vectorStoreBeanName)
                assertSame(KoogSpringAiVectorStoreProperties.DispatcherType.IO, properties.dispatcher.type)
            }
    }

    @Test
    fun `AUTO dispatcher should use AsyncTaskExecutor when available`() {
        val executor = mockk<AsyncTaskExecutor>(relaxed = true)
        contextRunner()
            .withBean("applicationTaskExecutor", AsyncTaskExecutor::class.java, { executor })
            .run { context ->
                assertInstanceOf<kotlinx.coroutines.ExecutorCoroutineDispatcher>(
                    context.getBean("koogSpringAiVectorStoreDispatcher")
                )
            }
    }

    @Test
    fun `AUTO dispatcher should fall back to Dispatchers_IO when no AsyncTaskExecutor`() {
        contextRunner().run { context ->
            val dispatcher = context.getBean("koogSpringAiVectorStoreDispatcher") as CoroutineDispatcher
            assertNotNull(dispatcher)
            assertSame(kotlinx.coroutines.Dispatchers.IO, dispatcher)
        }
    }

    @Test
    fun `should resolve by bean name when selector present and single VectorStore exists`() {
        val store = mockk<VectorStore>(relaxed = true)
        contextRunner()
            .withPropertyValues("koog.spring.ai.vectorstore.vector-store-bean-name=myStore")
            .withBean("myStore", VectorStore::class.java, { store })
            .run { context ->
                assertInstanceOf<SpringAiKoogVectorStore>(context.getBean<KoogVectorStore>())
            }
    }

    @Test
    fun `should fail when vector-store-bean-name refers to non-existent bean`() {
        contextRunner()
            .withPropertyValues("koog.spring.ai.vectorstore.vector-store-bean-name=doesNotExist")
            .withBean("actualStore", VectorStore::class.java, { mockk<VectorStore>(relaxed = true) })
            .run { context ->
                assertInstanceOf<BeanCreationException>(context.startupFailure)
            }
    }
}
