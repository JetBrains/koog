package ai.koog.spring.ai.embedding

import ai.koog.prompt.executor.clients.LLMEmbeddingProvider
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertInstanceOf
import org.junit.jupiter.api.assertThrows
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.beans.factory.getBean
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.core.task.AsyncTaskExecutor

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SpringAIEmbeddingAutoConfigurationTest {

    private fun contextRunner(): ApplicationContextRunner = ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                SpringAIEmbeddingAutoConfiguration::class.java,
            )
        )

    @Test
    fun `should not create LLMEmbeddingProvider bean when no EmbeddingModel is present`() {
        contextRunner()
            .run { context ->
                assertThrows<NoSuchBeanDefinitionException> { context.getBean<LLMEmbeddingProvider>() }
            }
    }

    @Test
    fun `should create SpringAIEmbeddingModelLLMEmbeddingProvider when single EmbeddingModel is present`() {
        contextRunner()
            .withBean(EmbeddingModel::class.java, { mockk<EmbeddingModel>(relaxed = true) })
            .run { context ->
                val provider = context.getBean<LLMEmbeddingProvider>()
                assertInstanceOf<SpringAILLMEmbeddingProvider>(provider)
            }
    }

    @Test
    fun `should not create LLMEmbeddingProvider when EmbeddingModel is present but LLMEmbeddingProvider already exists`() {
        val existingProvider = mockk<LLMEmbeddingProvider>(relaxed = true)
        contextRunner()
            .withBean(EmbeddingModel::class.java, { mockk<EmbeddingModel>(relaxed = true) })
            .withBean(LLMEmbeddingProvider::class.java, { existingProvider })
            .run { context ->
                val provider = context.getBean<LLMEmbeddingProvider>()
                assertTrue(provider === existingProvider)
            }
    }

    @Test
    fun `should not create LLMEmbeddingProvider when multiple EmbeddingModels are present without selector`() {
        contextRunner()
            .withBean("embModel1", EmbeddingModel::class.java, { mockk<EmbeddingModel>(relaxed = true) })
            .withBean("embModel2", EmbeddingModel::class.java, { mockk<EmbeddingModel>(relaxed = true) })
            .run { context ->
                assertThrows<NoSuchBeanDefinitionException> { context.getBean<LLMEmbeddingProvider>() }
            }
    }

    @Test
    fun `should not create beans when disabled`() {
        contextRunner()
            .withPropertyValues("koog.spring-ai.embedding.enabled=false")
            .withBean(EmbeddingModel::class.java, { mockk<EmbeddingModel>(relaxed = true) })
            .run { context ->
                assertThrows<NoSuchBeanDefinitionException> { context.getBean<LLMEmbeddingProvider>() }
            }
    }

    @Test
    fun `should resolve EmbeddingModel by bean name when configured`() {
        val targetModel = mockk<EmbeddingModel>(relaxed = true)
        contextRunner()
            .withPropertyValues("koog.spring-ai.embedding.embedding-model-bean-name=myEmb")
            .withBean("myEmb", EmbeddingModel::class.java, { targetModel })
            .withBean("otherEmb", EmbeddingModel::class.java, { mockk<EmbeddingModel>(relaxed = true) })
            .run { context ->
                val provider = context.getBean<LLMEmbeddingProvider>()
                assertInstanceOf<SpringAILLMEmbeddingProvider>(provider)
            }
    }

    @Test
    fun `should create dispatcher bean`() {
        contextRunner()
            .run { context ->
                assertNotNull(context.getBean("koogSpringAIEmbeddingDispatcher"))
            }
    }

    @Test
    fun `should bind KoogSpringAIEmbeddingProperties`() {
        contextRunner()
            .withPropertyValues(
                "koog.spring-ai.embedding.enabled=true",
                "koog.spring-ai.embedding.dispatcher.type=IO"
            )
            .run { context ->
                val props = context.getBean<KoogSpringAIEmbeddingProperties>()
                assertTrue(props.enabled)
                assertTrue(props.dispatcher.type == KoogSpringAIEmbeddingProperties.DispatcherType.IO)
            }
    }

    @Test
    fun `should create FIXED_THREAD_POOL dispatcher that implements DisposableBean`() {
        contextRunner()
            .withPropertyValues(
                "koog.spring-ai.embedding.dispatcher.type=FIXED_THREAD_POOL",
                "koog.spring-ai.embedding.dispatcher.parallelism=2"
            )
            .run { context ->
                val dispatcher = context.getBean("koogSpringAIEmbeddingDispatcher")
                assertNotNull(dispatcher)
                assertInstanceOf<DisposableBean>(dispatcher)
            }
    }

    @Test
    fun `FIXED_THREAD_POOL dispatcher destroy does not throw`() {
        contextRunner()
            .withPropertyValues(
                "koog.spring-ai.embedding.dispatcher.type=FIXED_THREAD_POOL",
                "koog.spring-ai.embedding.dispatcher.parallelism=1"
            )
            .run { context ->
                val dispatcher = context.getBean("koogSpringAIEmbeddingDispatcher") as DisposableBean
                dispatcher.destroy()
            }
    }

    @Test
    fun `AUTO dispatcher should use AsyncTaskExecutor when available`() {
        val executor = mockk<AsyncTaskExecutor>(relaxed = true)
        contextRunner()
            .withBean("applicationTaskExecutor", AsyncTaskExecutor::class.java, { executor })
            .run { context ->
                val dispatcher = context.getBean("koogSpringAIEmbeddingDispatcher")
                assertNotNull(dispatcher)
                assertInstanceOf<kotlinx.coroutines.ExecutorCoroutineDispatcher>(dispatcher)
            }
    }

    @Test
    fun `AUTO dispatcher should fall back to Dispatchers_IO when no AsyncTaskExecutor`() {
        contextRunner()
            .run { context ->
                val dispatcher = context.getBean("koogSpringAIEmbeddingDispatcher") as CoroutineDispatcher
                assertNotNull(dispatcher)
                assertSame(kotlinx.coroutines.Dispatchers.IO, dispatcher)
            }
    }

    @Test
    fun `should not create dispatcher when disabled`() {
        contextRunner()
            .withPropertyValues("koog.spring-ai.embedding.enabled=false")
            .run { context ->
                assertThrows<NoSuchBeanDefinitionException> { context.getBean("koogSpringAIEmbeddingDispatcher") }
            }
    }
}
