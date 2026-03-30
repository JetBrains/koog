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
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.beans.factory.getBean
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.core.task.AsyncTaskExecutor

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SpringAiEmbeddingAutoConfigurationTest {

    private fun contextRunner(): ApplicationContextRunner = ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                SpringAiEmbeddingAutoConfiguration::class.java,
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
    fun `should create SpringAiEmbeddingModelLLMEmbeddingProvider when single EmbeddingModel is present`() {
        contextRunner()
            .withBean(EmbeddingModel::class.java, { mockk<EmbeddingModel>(relaxed = true) })
            .run { context ->
                val provider = context.getBean<LLMEmbeddingProvider>()
                assertInstanceOf<SpringAiLLMEmbeddingProvider>(provider)
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
            .withPropertyValues("koog.spring.ai.embedding.enabled=false")
            .withBean(EmbeddingModel::class.java, { mockk<EmbeddingModel>(relaxed = true) })
            .run { context ->
                assertThrows<NoSuchBeanDefinitionException> { context.getBean<LLMEmbeddingProvider>() }
            }
    }

    @Test
    fun `should resolve EmbeddingModel by bean name when configured`() {
        val targetModel = mockk<EmbeddingModel>(relaxed = true)
        contextRunner()
            .withPropertyValues("koog.spring.ai.embedding.embedding-model-bean-name=myEmb")
            .withBean("myEmb", EmbeddingModel::class.java, { targetModel })
            .withBean("otherEmb", EmbeddingModel::class.java, { mockk<EmbeddingModel>(relaxed = true) })
            .run { context ->
                val provider = context.getBean<LLMEmbeddingProvider>()
                assertInstanceOf<SpringAiLLMEmbeddingProvider>(provider)
            }
    }

    @Test
    fun `should use named config only when single EmbeddingModel and selector property are both set`() {
        val targetModel = mockk<EmbeddingModel>(relaxed = true)
        contextRunner()
            .withPropertyValues("koog.spring.ai.embedding.embedding-model-bean-name=myEmb")
            .withBean("myEmb", EmbeddingModel::class.java, { targetModel })
            .run { context ->
                assertTrue(context.startupFailure == null, "Context should start without failure")
                val provider = context.getBean<LLMEmbeddingProvider>()
                assertInstanceOf<SpringAiLLMEmbeddingProvider>(provider)
                // Only one LLMEmbeddingProvider bean — no duplicate
                assertTrue(context.getBeansOfType(LLMEmbeddingProvider::class.java).size == 1)
            }
    }

    // ---- mutual exclusion: single candidate + selector set ----
    @Test
    fun `should create exactly one LLMEmbeddingProvider using named path when single EmbeddingModel and selector are both set`() {
        contextRunner()
            .withPropertyValues("koog.spring.ai.embedding.embedding-model-bean-name=myEmb")
            .withBean("myEmb", EmbeddingModel::class.java, { mockk<EmbeddingModel>(relaxed = true) })
            .run { context ->
                assertTrue(context.startupFailure == null, "Context should start without failure")
                val providers = context.getBeansOfType(LLMEmbeddingProvider::class.java)
                assertTrue(providers.size == 1, "Expected exactly one LLMEmbeddingProvider, got ${providers.size}")
                assertInstanceOf<SpringAiLLMEmbeddingProvider>(providers.values.single())
            }
    }

    // ---- mutual exclusion: multiple candidates + no selector ----
    @Test
    fun `should not create LLMEmbeddingProvider and not fail when multiple EmbeddingModels exist and no selector is set`() {
        contextRunner()
            .withBean("embModel1", EmbeddingModel::class.java, { mockk<EmbeddingModel>(relaxed = true) })
            .withBean("embModel2", EmbeddingModel::class.java, { mockk<EmbeddingModel>(relaxed = true) })
            .run { context ->
                assertTrue(context.startupFailure == null, "Context should start without failure")
                assertTrue(
                    context.getBeansOfType(LLMEmbeddingProvider::class.java).isEmpty(),
                    "Expected no LLMEmbeddingProvider when multiple EmbeddingModels exist and no selector is set"
                )
            }
    }

    // ---- mutual exclusion: selector set to empty string ----
    @Test
    fun `should fail on startup when embedding-model-bean-name is set to empty string because named path activates with empty bean name`() {
        contextRunner()
            .withPropertyValues("koog.spring.ai.embedding.embedding-model-bean-name=")
            .withBean("myEmb", EmbeddingModel::class.java, { mockk<EmbeddingModel>(relaxed = true) })
            .run { context ->
                // Spring treats "" as a present value equal to "", so havingValue="" on SingleEmbeddingModelConfiguration
                // matches (the condition passes), AND the plain @ConditionalOnProperty on NamedEmbeddingModelConfiguration
                // also matches (any non-false present value satisfies it). Both configs activate; the named path
                // then calls beanFactory.getBean("", EmbeddingModel::class.java) with an empty name, which fails.
                assertTrue(
                    context.startupFailure != null,
                    "Expected startup failure when embedding-model-bean-name is set to empty string"
                )
            }
    }

    @Test
    fun `should create dispatcher bean`() {
        contextRunner()
            .run { context ->
                assertNotNull(context.getBean("koogSpringAiEmbeddingDispatcher"))
            }
    }

    @Test
    fun `should bind KoogSpringAiEmbeddingProperties`() {
        contextRunner()
            .withPropertyValues(
                "koog.spring.ai.embedding.enabled=true",
                "koog.spring.ai.embedding.dispatcher.type=IO"
            )
            .run { context ->
                val props = context.getBean<KoogSpringAiEmbeddingProperties>()
                assertTrue(props.enabled)
                assertTrue(props.dispatcher.type == ai.koog.spring.ai.common.DispatcherType.IO)
                assertTrue(props.dispatcher.toDispatcherProperties() is ai.koog.spring.ai.common.DispatcherProperties.IO)
            }
    }

    @Test
    fun `IO dispatcher with parallelism should create limited dispatcher`() {
        contextRunner()
            .withPropertyValues(
                "koog.spring.ai.embedding.dispatcher.type=IO",
                "koog.spring.ai.embedding.dispatcher.parallelism=2"
            )
            .run { context ->
                val dispatcher = context.getBean("koogSpringAiEmbeddingDispatcher")
                assertNotNull(dispatcher)
                assertInstanceOf<CoroutineDispatcher>(dispatcher)
            }
    }

    @Test
    fun `AUTO dispatcher should use AsyncTaskExecutor when available`() {
        val executor = mockk<AsyncTaskExecutor>(relaxed = true)
        contextRunner()
            .withBean("applicationTaskExecutor", AsyncTaskExecutor::class.java, { executor })
            .run { context ->
                val dispatcher = context.getBean("koogSpringAiEmbeddingDispatcher")
                assertNotNull(dispatcher)
                assertInstanceOf<kotlinx.coroutines.ExecutorCoroutineDispatcher>(dispatcher)
            }
    }

    @Test
    fun `AUTO dispatcher should fall back to Dispatchers_IO when no AsyncTaskExecutor`() {
        contextRunner()
            .run { context ->
                val dispatcher = context.getBean("koogSpringAiEmbeddingDispatcher") as CoroutineDispatcher
                assertNotNull(dispatcher)
                assertSame(kotlinx.coroutines.Dispatchers.IO, dispatcher)
            }
    }

    @Test
    fun `should not create dispatcher when disabled`() {
        contextRunner()
            .withPropertyValues("koog.spring.ai.embedding.enabled=false")
            .run { context ->
                assertThrows<NoSuchBeanDefinitionException> { context.getBean("koogSpringAiEmbeddingDispatcher") }
            }
    }
}
