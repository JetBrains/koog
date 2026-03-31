package ai.koog.spring.ai.memory

import ai.koog.agents.chatMemory.feature.ChatHistoryProvider
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertInstanceOf
import org.junit.jupiter.api.assertThrows
import org.springframework.ai.chat.memory.ChatMemoryRepository
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.beans.factory.getBean
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.core.task.AsyncTaskExecutor

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SpringAiChatMemoryAutoConfigurationTest {

    private fun contextRunner(): ApplicationContextRunner = ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                SpringAiChatMemoryAutoConfiguration::class.java,
            )
        )

    // ---- enabled / disabled ----

    @Test
    fun `should create dispatcher when enabled by default`() {
        contextRunner()
            .run { context ->
                assertNotNull(context.getBean("koogSpringAiChatMemoryDispatcher"))
            }
    }

    @Test
    fun `should not create any beans when disabled`() {
        contextRunner()
            .withPropertyValues("koog.spring.ai.chat-memory.enabled=false")
            .withBean(ChatMemoryRepository::class.java, { mockk<ChatMemoryRepository>(relaxed = true) })
            .run { context ->
                assertThrows<NoSuchBeanDefinitionException> { context.getBean<ChatHistoryProvider>() }
                assertThrows<NoSuchBeanDefinitionException> { context.getBean("koogSpringAiChatMemoryDispatcher") }
            }
    }

    // ---- single repository ----

    @Test
    fun `should create ChatHistoryProvider when single ChatMemoryRepository is present`() {
        contextRunner()
            .withBean(ChatMemoryRepository::class.java, { mockk<ChatMemoryRepository>(relaxed = true) })
            .run { context ->
                val provider = context.getBean<ChatHistoryProvider>()
                assertInstanceOf<SpringAiChatHistoryProvider>(provider)
            }
    }

    @Test
    fun `should not create ChatHistoryProvider when no ChatMemoryRepository is present`() {
        contextRunner()
            .run { context ->
                assertThrows<NoSuchBeanDefinitionException> { context.getBean<ChatHistoryProvider>() }
            }
    }

    // ---- user-supplied ChatHistoryProvider ----

    @Test
    fun `should not create ChatHistoryProvider when user provides one`() {
        val userProvider = mockk<ChatHistoryProvider>(relaxed = true)
        contextRunner()
            .withBean(ChatMemoryRepository::class.java, { mockk<ChatMemoryRepository>(relaxed = true) })
            .withBean(ChatHistoryProvider::class.java, { userProvider })
            .run { context ->
                val provider = context.getBean<ChatHistoryProvider>()
                assertSame(userProvider, provider)
            }
    }

    // ---- multiple repositories without selector ----

    @Test
    fun `should not create ChatHistoryProvider when multiple repositories exist without selector`() {
        contextRunner()
            .withBean("repo1", ChatMemoryRepository::class.java, { mockk<ChatMemoryRepository>(relaxed = true) })
            .withBean("repo2", ChatMemoryRepository::class.java, { mockk<ChatMemoryRepository>(relaxed = true) })
            .run { context ->
                assertTrue(context.startupFailure == null, "Context should start without failure")
                assertThrows<NoSuchBeanDefinitionException> { context.getBean<ChatHistoryProvider>() }
            }
    }

    @Test
    fun `should use named config only when single ChatMemoryRepository and selector property are both set`() {
        val myRepo = mockk<ChatMemoryRepository>(relaxed = true)
        contextRunner()
            .withPropertyValues("koog.spring.ai.chat-memory.chat-memory-repository-bean-name=myRepo")
            .withBean("myRepo", ChatMemoryRepository::class.java, { myRepo })
            .run { context ->
                assertTrue(context.startupFailure == null, "Context should start without failure")
                val provider = context.getBean<ChatHistoryProvider>()
                assertInstanceOf<SpringAiChatHistoryProvider>(provider)
                // Only one ChatHistoryProvider bean — no duplicate
                assertTrue(context.getBeansOfType(ChatHistoryProvider::class.java).size == 1)
            }
    }

    // ---- mutual exclusion: single candidate + selector set ----
    @Test
    fun `should create exactly one ChatHistoryProvider using named path when single repo and selector are both set`() {
        val myRepo = mockk<ChatMemoryRepository>(relaxed = true)
        contextRunner()
            .withPropertyValues("koog.spring.ai.chat-memory.chat-memory-repository-bean-name=myRepo")
            .withBean("myRepo", ChatMemoryRepository::class.java, { myRepo })
            .run { context ->
                assertTrue(context.startupFailure == null, "Context should start without failure")
                val providers = context.getBeansOfType(ChatHistoryProvider::class.java)
                assertTrue(providers.size == 1, "Expected exactly one ChatHistoryProvider, got ${providers.size}")
                val provider = providers.values.single()
                assertInstanceOf<SpringAiChatHistoryProvider>(provider)
                assertSame(myRepo, provider.repository)
            }
    }

    // ---- mutual exclusion: multiple candidates + no selector ----
    @Test
    fun `should not create ChatHistoryProvider and not fail when multiple repos exist and no selector is set`() {
        contextRunner()
            .withBean("repo1", ChatMemoryRepository::class.java, { mockk<ChatMemoryRepository>(relaxed = true) })
            .withBean("repo2", ChatMemoryRepository::class.java, { mockk<ChatMemoryRepository>(relaxed = true) })
            .run { context ->
                assertTrue(context.startupFailure == null, "Context should start without failure")
                assertTrue(
                    context.getBeansOfType(ChatHistoryProvider::class.java).isEmpty(),
                    "Expected no ChatHistoryProvider when multiple repos exist and no selector is set"
                )
            }
    }

    // ---- mutual exclusion: selector set to empty string ----
    @Test
    fun `should fail on startup when selector is set to empty string because named path activates with empty bean name`() {
        contextRunner()
            .withPropertyValues("koog.spring.ai.chat-memory.chat-memory-repository-bean-name=")
            .withBean("myRepo", ChatMemoryRepository::class.java, { mockk<ChatMemoryRepository>(relaxed = true) })
            .run { context ->
                // Spring treats "" as a present value equal to "", so havingValue="" on SingleChatMemoryRepositoryConfiguration
                // matches (the condition passes), AND the plain @ConditionalOnProperty on NamedChatMemoryRepositoryConfiguration
                // also matches (any non-false present value satisfies it). Both configs activate; the named path
                // then calls beanFactory.getBean("", ChatMemoryRepository::class.java) with an empty name, which fails.
                assertTrue(
                    context.startupFailure != null,
                    "Expected startup failure when selector is set to empty string"
                )
            }
    }

    // ---- named repository selection ----

    @Test
    fun `should resolve ChatMemoryRepository by bean name when configured`() {
        val myRepo = mockk<ChatMemoryRepository>(relaxed = true)
        contextRunner()
            .withPropertyValues("koog.spring.ai.chat-memory.chat-memory-repository-bean-name=myRepo")
            .withBean("myRepo", ChatMemoryRepository::class.java, { myRepo })
            .withBean("otherRepo", ChatMemoryRepository::class.java, { mockk<ChatMemoryRepository>(relaxed = true) })
            .run { context ->
                val provider = assertInstanceOf<SpringAiChatHistoryProvider>(context.getBean<ChatHistoryProvider>())
                assertSame(myRepo, provider.repository)
            }
    }

    @Test
    fun `should fail when named repository bean does not exist`() {
        contextRunner()
            .withPropertyValues("koog.spring.ai.chat-memory.chat-memory-repository-bean-name=nonExistent")
            .withBean("realRepo", ChatMemoryRepository::class.java, { mockk<ChatMemoryRepository>(relaxed = true) })
            .run { context ->
                assertTrue(context.startupFailure != null)
                val rootCause = generateSequence(context.startupFailure) { it.cause }.last()
                assertInstanceOf<NoSuchBeanDefinitionException>(rootCause)
            }
    }

    // ---- dispatcher override ----

    @Test
    fun `should use AsyncTaskExecutor as dispatcher when available`() {
        val executor = mockk<AsyncTaskExecutor>(relaxed = true)
        contextRunner()
            .withBean("applicationTaskExecutor", AsyncTaskExecutor::class.java, { executor })
            .run { context ->
                val dispatcher = context.getBean("koogSpringAiChatMemoryDispatcher")
                assertInstanceOf<kotlinx.coroutines.ExecutorCoroutineDispatcher>(dispatcher)
            }
    }

    @Test
    fun `should fall back to Dispatchers IO when no AsyncTaskExecutor`() {
        contextRunner()
            .run { context ->
                val dispatcher = context.getBean("koogSpringAiChatMemoryDispatcher") as CoroutineDispatcher
                assertSame(kotlinx.coroutines.Dispatchers.IO, dispatcher)
            }
    }

    @Test
    fun `should not override user-provided dispatcher`() {
        val customDispatcher = kotlinx.coroutines.Dispatchers.Unconfined
        contextRunner()
            .withBean("koogSpringAiChatMemoryDispatcher", CoroutineDispatcher::class.java, { customDispatcher })
            .run { context ->
                val dispatcher = context.getBean("koogSpringAiChatMemoryDispatcher") as CoroutineDispatcher
                assertSame(customDispatcher, dispatcher)
            }
    }
}
