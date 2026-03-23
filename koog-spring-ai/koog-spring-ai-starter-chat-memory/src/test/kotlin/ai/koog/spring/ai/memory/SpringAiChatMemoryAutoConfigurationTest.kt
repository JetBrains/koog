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
                assertThrows<NoSuchBeanDefinitionException> { context.getBean<ChatHistoryProvider>() }
            }
    }

    // ---- named repository selection ----

    @Test
    fun `should resolve ChatMemoryRepository by bean name when configured`() {
        contextRunner()
            .withPropertyValues("koog.spring.ai.chat-memory.chat-memory-repository-bean-name=myRepo")
            .withBean("myRepo", ChatMemoryRepository::class.java, { mockk<ChatMemoryRepository>(relaxed = true) })
            .withBean("otherRepo", ChatMemoryRepository::class.java, { mockk<ChatMemoryRepository>(relaxed = true) })
            .run { context ->
                val provider = context.getBean<ChatHistoryProvider>()
                assertInstanceOf<SpringAiChatHistoryProvider>(provider)
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
