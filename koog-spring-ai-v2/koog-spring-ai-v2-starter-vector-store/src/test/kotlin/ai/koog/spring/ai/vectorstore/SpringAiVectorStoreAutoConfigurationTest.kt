package ai.koog.spring.ai.vectorstore

import ai.koog.rag.base.TextDocument
import ai.koog.rag.base.storage.DeletionStorage
import ai.koog.rag.base.storage.SearchStorage
import ai.koog.rag.base.storage.WriteStorage
import ai.koog.rag.base.storage.search.SimilaritySearchRequest
import ai.koog.spring.ai.common.DispatcherType
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

    // ---- enabled / disabled ----

    @Test
    fun testCreateDispatcherWhenEnabledByDefault() {
        contextRunner().run { context ->
            assertNotNull(context.getBean("koogSpringAiVectorStoreDispatcher"))
        }
    }

    @Test
    fun testNotCreateAnyBeansWhenDisabled() {
        contextRunner()
            .withPropertyValues("koog.spring.ai.vectorstore.enabled=false")
            .withBean(VectorStore::class.java, { mockk<VectorStore>(relaxed = true) })
            .run { context ->
                assertThrows<NoSuchBeanDefinitionException> { context.getBean<KoogVectorStore>() }
                assertThrows<NoSuchBeanDefinitionException> { context.getBean("koogSpringAiVectorStoreDispatcher") }
            }
    }

    // ---- single VectorStore ----

    @Test
    fun testCreateAdapterAndKoogStorageBeansWhenSingleVectorStoreIsPresent() {
        contextRunner()
            .withBean(VectorStore::class.java, { mockk<VectorStore>(relaxed = true) })
            .run { context ->
                assertInstanceOf<SpringAiKoogVectorStore>(context.getBean<KoogVectorStore>())
                assertInstanceOf<SpringAiKoogVectorStore>(context.getBean<WriteStorage<TextDocument>>())
                assertInstanceOf<SpringAiKoogVectorStore>(context.getBean<SearchStorage<TextDocument, SimilaritySearchRequest>>())
                assertInstanceOf<SpringAiKoogVectorStore>(context.getBean<DeletionStorage>())
            }
    }

    @Test
    fun testNotCreateKoogStorageBeansWhenNoVectorStoreIsPresent() {
        contextRunner().run { context ->
            assertThrows<NoSuchBeanDefinitionException> { context.getBean<KoogVectorStore>() }
            assertThrows<NoSuchBeanDefinitionException> { context.getBean<WriteStorage<TextDocument>>() }
            assertThrows<NoSuchBeanDefinitionException> { context.getBean<SearchStorage<TextDocument, SimilaritySearchRequest>>() }
            assertThrows<NoSuchBeanDefinitionException> { context.getBean<DeletionStorage>() }
        }
    }

    // ---- user-supplied KoogVectorStore ----

    @Test
    fun testNotCreateKoogVectorStoreWhenUserProvidesOne() {
        val userStore = mockk<KoogVectorStore>(relaxed = true)
        contextRunner()
            .withBean(VectorStore::class.java, { mockk<VectorStore>(relaxed = true) })
            .withBean(KoogVectorStore::class.java, { userStore })
            .run { context ->
                assertSame(userStore, context.getBean<KoogVectorStore>())
            }
    }

    // ---- multiple VectorStores without selector ----

    @Test
    fun testNotCreateKoogStorageBeansWhenMultipleVectorStoresArePresentWithoutSelector() {
        contextRunner()
            .withBean("store1", VectorStore::class.java, { mockk<VectorStore>(relaxed = true) })
            .withBean("store2", VectorStore::class.java, { mockk<VectorStore>(relaxed = true) })
            .run { context ->
                assertTrue(context.startupFailure == null, "Context should start without failure")
                assertThrows<NoSuchBeanDefinitionException> { context.getBean<KoogVectorStore>() }
            }
    }

    // ---- named VectorStore selection ----

    @Test
    fun testResolveVectorStoreByBeanNameWhenConfigured() {
        contextRunner()
            .withPropertyValues("koog.spring.ai.vectorstore.vector-store-bean-name=myVectorStore")
            .withBean("myVectorStore", VectorStore::class.java, { mockk<VectorStore>(relaxed = true) })
            .withBean("otherVectorStore", VectorStore::class.java, { mockk<VectorStore>(relaxed = true) })
            .run { context ->
                assertInstanceOf<SpringAiKoogVectorStore>(context.getBean<KoogVectorStore>())
            }
    }

    @Test
    fun testResolveByBeanNameWhenSelectorPresentAndSingleVectorStoreExists() {
        val store = mockk<VectorStore>(relaxed = true)
        contextRunner()
            .withPropertyValues("koog.spring.ai.vectorstore.vector-store-bean-name=myStore")
            .withBean("myStore", VectorStore::class.java, { store })
            .run { context ->
                assertInstanceOf<SpringAiKoogVectorStore>(context.getBean<KoogVectorStore>())
            }
    }

    @Test
    fun testFailWhenVectorStoreBeanNameRefersToNonExistentBean() {
        contextRunner()
            .withPropertyValues("koog.spring.ai.vectorstore.vector-store-bean-name=doesNotExist")
            .withBean("actualStore", VectorStore::class.java, { mockk<VectorStore>(relaxed = true) })
            .run { context ->
                assertInstanceOf<BeanCreationException>(context.startupFailure)
            }
    }

    // ---- mutual exclusion: single candidate + selector set ----

    @Test
    fun testCreateExactlyOneKoogVectorStoreUsingNamedPathWhenSingleStoreAndSelectorAreSet() {
        val myStore = mockk<VectorStore>(relaxed = true)
        contextRunner()
            .withPropertyValues("koog.spring.ai.vectorstore.vector-store-bean-name=myStore")
            .withBean("myStore", VectorStore::class.java, { myStore })
            .run { context ->
                assertTrue(context.startupFailure == null, "Context should start without failure")
                val stores = context.getBeansOfType(KoogVectorStore::class.java)
                assertTrue(stores.size == 1, "Expected exactly one KoogVectorStore, got ${stores.size}")
            }
    }

    // ---- mutual exclusion: selector set to empty string ----
    @Test
    fun testEmptyStringSelectorTreatedAsMissingAndUsesSingleCandidate() {
        contextRunner()
            .withPropertyValues("koog.spring.ai.vectorstore.vector-store-bean-name=")
            .withBean("myStore", VectorStore::class.java, { mockk<VectorStore>(relaxed = true) })
            .run { context ->
                // @ConditionalOnPropertyMissingOrEmpty treats "" as missing/empty, so only
                // SingleVectorStoreConfiguration activates; NamedVectorStoreConfiguration does not match.
                assertTrue(context.startupFailure == null, "Context should start successfully when selector is empty string")
                assertInstanceOf<SpringAiKoogVectorStore>(context.getBean<KoogVectorStore>())
            }
    }

    // ---- properties binding ----

    @Test
    fun testBindProperties() {
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
                assertSame(DispatcherType.IO, properties.dispatcher.type)
            }
    }

    // ---- dispatcher ----

    @Test
    fun testAutoDispatcherUsesAsyncTaskExecutorWhenAvailable() {
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
    fun testAutoDispatcherFallsBackToDispatchersIOWhenNoAsyncTaskExecutor() {
        contextRunner().run { context ->
            val dispatcher = context.getBean("koogSpringAiVectorStoreDispatcher") as CoroutineDispatcher
            assertSame(kotlinx.coroutines.Dispatchers.IO, dispatcher)
        }
    }

    @Test
    fun testNotOverrideUserProvidedDispatcher() {
        val customDispatcher = kotlinx.coroutines.Dispatchers.Unconfined
        contextRunner()
            .withBean("koogSpringAiVectorStoreDispatcher", CoroutineDispatcher::class.java, { customDispatcher })
            .run { context ->
                val dispatcher = context.getBean("koogSpringAiVectorStoreDispatcher") as CoroutineDispatcher
                assertSame(customDispatcher, dispatcher)
            }
    }
}
