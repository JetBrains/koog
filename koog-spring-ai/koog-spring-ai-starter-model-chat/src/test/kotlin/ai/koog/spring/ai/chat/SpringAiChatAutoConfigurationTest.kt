package ai.koog.spring.ai.chat

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMProvider
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertInstanceOf
import org.junit.jupiter.api.assertThrows
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.moderation.ModerationModel
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.beans.factory.getBean
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.core.task.AsyncTaskExecutor

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SpringAiChatAutoConfigurationTest {

    private fun contextRunner(): ApplicationContextRunner = ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                SpringAiChatAutoConfiguration::class.java,
            )
        )

    @Test
    fun `should not create LLMClient bean when no ChatModel is present`() {
        contextRunner()
            .run { context ->
                assertThrows<NoSuchBeanDefinitionException> { context.getBean<LLMClient>() }
            }
    }

    @Test
    fun `should create SpringAiChatModelLLMClient when single ChatModel is present`() {
        contextRunner()
            .withBean(ChatModel::class.java, { mockk<ChatModel>(relaxed = true) })
            .run { context ->
                val client = context.getBean<LLMClient>()
                assertInstanceOf<SpringAiLLMClient>(client)
            }
    }

    @Test
    fun `should not create LLMClient when ChatModel is present but LLMClient already exists`() {
        val existingClient = mockk<LLMClient>(relaxed = true)
        contextRunner()
            .withBean(ChatModel::class.java, { mockk<ChatModel>(relaxed = true) })
            .withBean(LLMClient::class.java, { existingClient })
            .run { context ->
                val client = context.getBean<LLMClient>()
                assertTrue(client === existingClient)
            }
    }

    @Test
    fun `should not create LLMClient when multiple ChatModels are present without selector`() {
        contextRunner()
            .withBean("chatModel1", ChatModel::class.java, { mockk<ChatModel>(relaxed = true) })
            .withBean("chatModel2", ChatModel::class.java, { mockk<ChatModel>(relaxed = true) })
            .run { context ->
                assertTrue(context.startupFailure == null, "Context should start without failure")
                assertThrows<NoSuchBeanDefinitionException> { context.getBean<LLMClient>() }
            }
    }

    @Test
    fun `should use named config only when single ChatModel and selector property are both set`() {
        val targetModel = mockk<ChatModel>(relaxed = true)
        contextRunner()
            .withPropertyValues("koog.spring.ai.chat.chat-model-bean-name=myChat")
            .withBean("myChat", ChatModel::class.java, { targetModel })
            .run { context ->
                assertTrue(context.startupFailure == null, "Context should start without failure")
                val client = context.getBean<LLMClient>()
                assertInstanceOf<SpringAiLLMClient>(client)
                // Only one LLMClient bean — no duplicate
                assertTrue(context.getBeansOfType(LLMClient::class.java).size == 1)
            }
    }

    // ---- mutual exclusion: single candidate + selector set ----
    @Test
    fun `should create exactly one LLMClient using named path when single ChatModel and selector are both set`() {
        val targetModel = mockk<ChatModel>(relaxed = true)
        contextRunner()
            .withPropertyValues("koog.spring.ai.chat.chat-model-bean-name=myChat")
            .withBean("myChat", ChatModel::class.java, { targetModel })
            .run { context ->
                assertTrue(context.startupFailure == null, "Context should start without failure")
                val clients = context.getBeansOfType(LLMClient::class.java)
                assertTrue(clients.size == 1, "Expected exactly one LLMClient, got ${clients.size}")
                assertInstanceOf<SpringAiLLMClient>(clients.values.single())
            }
    }

    // ---- mutual exclusion: multiple candidates + no selector ----
    @Test
    fun `should not create LLMClient and not fail when multiple ChatModels exist and no selector is set`() {
        contextRunner()
            .withBean("chatModel1", ChatModel::class.java, { mockk<ChatModel>(relaxed = true) })
            .withBean("chatModel2", ChatModel::class.java, { mockk<ChatModel>(relaxed = true) })
            .run { context ->
                assertTrue(context.startupFailure == null, "Context should start without failure")
                assertTrue(
                    context.getBeansOfType(LLMClient::class.java).isEmpty(),
                    "Expected no LLMClient when multiple ChatModels exist and no selector is set"
                )
            }
    }

    // ---- mutual exclusion: selector set to empty string ----
    @Test
    fun `should fail on startup when chat-model-bean-name is set to empty string because named path activates with empty bean name`() {
        contextRunner()
            .withPropertyValues("koog.spring.ai.chat.chat-model-bean-name=")
            .withBean("myChat", ChatModel::class.java, { mockk<ChatModel>(relaxed = true) })
            .run { context ->
                // Spring treats "" as a present value equal to "", so havingValue="" on SingleChatModelConfiguration
                // matches (the condition passes), AND the plain @ConditionalOnProperty on NamedChatModelConfiguration
                // also matches (any non-false present value satisfies it). Both configs activate; the named path
                // then calls beanFactory.getBean("", ChatModel::class.java) with an empty name, which fails.
                assertTrue(
                    context.startupFailure != null,
                    "Expected startup failure when chat-model-bean-name is set to empty string"
                )
            }
    }

    @Test
    fun `should not create beans when disabled`() {
        contextRunner()
            .withPropertyValues("koog.spring.ai.chat.enabled=false")
            .withBean(ChatModel::class.java, { mockk<ChatModel>(relaxed = true) })
            .run { context ->
                assertThrows<NoSuchBeanDefinitionException> { context.getBean<LLMClient>() }
            }
    }

    @Test
    fun `should resolve ChatModel by bean name when configured`() {
        val targetModel = mockk<ChatModel>(relaxed = true)
        contextRunner()
            .withPropertyValues("koog.spring.ai.chat.chat-model-bean-name=myChat")
            .withBean("myChat", ChatModel::class.java, { targetModel })
            .withBean("otherChat", ChatModel::class.java, { mockk<ChatModel>(relaxed = true) })
            .run { context ->
                val client = context.getBean<LLMClient>()
                assertInstanceOf<SpringAiLLMClient>(client)
            }
    }

    @Test
    fun `should create dispatcher bean`() {
        contextRunner()
            .run { context ->
                assertNotNull(context.getBean("koogSpringAiChatDispatcher"))
            }
    }

    @Test
    fun `should bind KoogSpringAiChatProperties`() {
        contextRunner()
            .withPropertyValues(
                "koog.spring.ai.chat.enabled=true",
                "koog.spring.ai.chat.dispatcher.type=IO"
            )
            .run { context ->
                val props = context.getBean<KoogSpringAiChatProperties>()
                assertTrue(props.enabled)
                assertTrue(props.dispatcher.type == ai.koog.spring.ai.common.DispatcherType.IO)
                assertTrue(props.dispatcher.toDispatcherProperties() is ai.koog.spring.ai.common.DispatcherProperties.IO)
            }
    }

    @Test
    fun `IO dispatcher with parallelism should create limited dispatcher`() {
        contextRunner()
            .withPropertyValues(
                "koog.spring.ai.chat.dispatcher.type=IO",
                "koog.spring.ai.chat.dispatcher.parallelism=2"
            )
            .run { context ->
                val dispatcher = context.getBean("koogSpringAiChatDispatcher")
                assertNotNull(dispatcher)
                assertInstanceOf<CoroutineDispatcher>(dispatcher)
            }
    }

    @Test
    fun `should use user-provided ChatOptionsCustomizer bean`() {
        val customizer = ChatOptionsCustomizer { options, _, _ -> options }
        contextRunner()
            .withBean(ChatModel::class.java, { mockk<ChatModel>(relaxed = true) })
            .withBean(ChatOptionsCustomizer::class.java, { customizer })
            .run { context ->
                assertSame(customizer, context.getBean<ChatOptionsCustomizer>())
                assertInstanceOf<SpringAiLLMClient>(context.getBean<LLMClient>())
            }
    }

    @Test
    fun `named config should wire ModerationModel from context when no bean name property set`() {
        val moderationModel = mockk<ModerationModel>(relaxed = true)
        contextRunner()
            .withPropertyValues("koog.spring.ai.chat.chat-model-bean-name=myChat")
            .withBean("myChat", ChatModel::class.java, { mockk<ChatModel>(relaxed = true) })
            .withBean(ModerationModel::class.java, { moderationModel })
            .run { context ->
                assertInstanceOf<SpringAiLLMClient>(context.getBean<LLMClient>())
            }
    }

    @Test
    fun `AUTO dispatcher should use AsyncTaskExecutor when available`() {
        val executor = mockk<AsyncTaskExecutor>(relaxed = true)
        contextRunner()
            .withBean("applicationTaskExecutor", AsyncTaskExecutor::class.java, { executor })
            .run { context ->
                val dispatcher = context.getBean("koogSpringAiChatDispatcher")
                assertNotNull(dispatcher)
                assertInstanceOf<kotlinx.coroutines.ExecutorCoroutineDispatcher>(dispatcher)
            }
    }

    @Test
    fun `AUTO dispatcher should fall back to Dispatchers_IO when no AsyncTaskExecutor`() {
        contextRunner()
            .run { context ->
                val dispatcher = context.getBean("koogSpringAiChatDispatcher") as CoroutineDispatcher
                assertNotNull(dispatcher)
                assertSame(kotlinx.coroutines.Dispatchers.IO, dispatcher)
            }
    }

    @Test
    fun `should not create dispatcher when disabled`() {
        contextRunner()
            .withPropertyValues("koog.spring.ai.chat.enabled=false")
            .run { context ->
                assertThrows<NoSuchBeanDefinitionException> { context.getBean("koogSpringAiChatDispatcher") }
            }
    }

    // ---- PromptExecutor auto-configuration tests ----

    @Test
    fun `should create PromptExecutor when LLMClient is present`() {
        contextRunner()
            .withBean(ChatModel::class.java, { mockk<ChatModel>(relaxed = true) })
            .run { context ->
                val executor = context.getBean<PromptExecutor>()
                assertInstanceOf<MultiLLMPromptExecutor>(executor)
            }
    }

    @Test
    fun `should not create PromptExecutor when no LLMClient is present`() {
        contextRunner()
            .run { context ->
                assertThrows<NoSuchBeanDefinitionException> { context.getBean<PromptExecutor>() }
            }
    }

    @Test
    fun `should not create PromptExecutor when user provides one`() {
        val userExecutor = mockk<PromptExecutor>(relaxed = true)
        contextRunner()
            .withBean(ChatModel::class.java, { mockk<ChatModel>(relaxed = true) })
            .withBean(PromptExecutor::class.java, { userExecutor })
            .run { context ->
                val executor = context.getBean<PromptExecutor>()
                assertSame(userExecutor, executor)
            }
    }

    @Test
    fun `should not create PromptExecutor when disabled`() {
        contextRunner()
            .withPropertyValues("koog.spring.ai.chat.enabled=false")
            .withBean(ChatModel::class.java, { mockk<ChatModel>(relaxed = true) })
            .run { context ->
                assertThrows<NoSuchBeanDefinitionException> { context.getBean<PromptExecutor>() }
            }
    }

    // ---- Moderation model bean-name resolution in single-ChatModel mode ----

    @Test
    fun `single ChatModel with moderation-model-bean-name should wire the named ModerationModel`() {
        val targetModeration = mockk<ModerationModel>(relaxed = true)
        val otherModeration = mockk<ModerationModel>(relaxed = true)
        contextRunner()
            .withBean(ChatModel::class.java, { mockk<ChatModel>(relaxed = true) })
            .withBean("myModeration", ModerationModel::class.java, { targetModeration })
            .withBean("otherModeration", ModerationModel::class.java, { otherModeration })
            .withPropertyValues("koog.spring.ai.chat.moderation-model-bean-name=myModeration")
            .run { context ->
                assertTrue(context.startupFailure == null, "Context should start without failure")
                val client = context.getBean<LLMClient>()
                assertInstanceOf<SpringAiLLMClient>(client)
            }
    }

    @Test
    fun `single ChatModel with invalid moderation-model-bean-name should fail on startup`() {
        contextRunner()
            .withBean(ChatModel::class.java, { mockk<ChatModel>(relaxed = true) })
            .withBean("realModeration", ModerationModel::class.java, { mockk<ModerationModel>(relaxed = true) })
            .withPropertyValues("koog.spring.ai.chat.moderation-model-bean-name=nonExistentModeration")
            .run { context ->
                assertTrue(context.startupFailure != null)
                val rootCause = generateSequence(context.startupFailure) { it.cause }.last()
                assertInstanceOf<NoSuchBeanDefinitionException>(rootCause)
                assertTrue(rootCause.message?.contains("nonExistentModeration") == true)
            }
    }

    // ---- LLMProvider resolution tests ----

    @Test
    fun `should use explicit provider property when set`() {
        contextRunner()
            .withPropertyValues("koog.spring.ai.chat.provider=google")
            .withBean(ChatModel::class.java, { mockk<ChatModel>(relaxed = true) })
            .run { context ->
                val client = context.getBean<LLMClient>() as SpringAiLLMClient
                assertSame(LLMProvider.Google, client.llmProvider())
            }
    }

    @Test
    fun `should use explicit provider property for openai`() {
        contextRunner()
            .withPropertyValues("koog.spring.ai.chat.provider=openai")
            .withBean(ChatModel::class.java, { mockk<ChatModel>(relaxed = true) })
            .run { context ->
                val client = context.getBean<LLMClient>() as SpringAiLLMClient
                assertSame(LLMProvider.OpenAI, client.llmProvider())
            }
    }

    @Test
    fun `should fail on invalid provider property`() {
        contextRunner()
            .withPropertyValues("koog.spring.ai.chat.provider=unknown-provider")
            .withBean(ChatModel::class.java, { mockk<ChatModel>(relaxed = true) })
            .run { context ->
                assertTrue(context.startupFailure != null)
                val rootCause = generateSequence(context.startupFailure) { it.cause }.last()
                assertInstanceOf<IllegalArgumentException>(rootCause)
                assertTrue(rootCause.message?.contains("unknown-provider") == true)
            }
    }

    @Test
    fun `should use user-provided LLMProvider bean over property`() {
        contextRunner()
            .withPropertyValues("koog.spring.ai.chat.provider=openai")
            .withBean(ChatModel::class.java, { mockk<ChatModel>(relaxed = true) })
            .withBean(LLMProvider::class.java, { LLMProvider.Anthropic })
            .run { context ->
                val client = context.getBean<LLMClient>() as SpringAiLLMClient
                assertSame(LLMProvider.Anthropic, client.llmProvider())
            }
    }

    @Test
    fun `should fallback to SpringAiLLMProvider when no property and unknown ChatModel`() {
        contextRunner()
            .withBean(ChatModel::class.java, { mockk<ChatModel>(relaxed = true) })
            .run { context ->
                val client = context.getBean<LLMClient>() as SpringAiLLMClient
                assertInstanceOf<SpringAiLLMProvider>(client.llmProvider())
            }
    }

    @Test
    fun `named config should use explicit provider property`() {
        contextRunner()
            .withPropertyValues(
                "koog.spring.ai.chat.chat-model-bean-name=myChat",
                "koog.spring.ai.chat.provider=google"
            )
            .withBean("myChat", ChatModel::class.java, { mockk<ChatModel>(relaxed = true) })
            .run { context ->
                val client = context.getBean<LLMClient>() as SpringAiLLMClient
                assertSame(LLMProvider.Google, client.llmProvider())
            }
    }

    @Test
    fun `named config should use LLMProvider bean`() {
        contextRunner()
            .withPropertyValues("koog.spring.ai.chat.chat-model-bean-name=myChat")
            .withBean("myChat", ChatModel::class.java, { mockk<ChatModel>(relaxed = true) })
            .withBean(LLMProvider::class.java, { LLMProvider.Google })
            .run { context ->
                val client = context.getBean<LLMClient>() as SpringAiLLMClient
                assertSame(LLMProvider.Google, client.llmProvider())
            }
    }
}
