package ai.koog.spring

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.TestPropertySource

@SpringBootTest(
    classes = [
        KoogAutoConfigurationIntegrationTest.TestConfig::class,
    ],
    properties = [
        "debug=true", // set to true for troubleshooting
        "spring.main.banner-mode=off"
    ],
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@TestPropertySource(
    locations = ["classpath:/it-application.properties"]
)
class KoogAutoConfigurationIntegrationTest {

    @Configuration
    @EnableAutoConfiguration
    @Suppress("unused")
    private class TestConfig

    private val logger = LoggerFactory.getLogger(KoogAutoConfigurationIntegrationTest::class.java)

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @ParameterizedTest
    @ValueSource(
        classes = [
            AnthropicLLMClient::class,
            DeepSeekLLMClient::class,
            GoogleLLMClient::class,
            OpenAILLMClient::class,
            OpenRouterLLMClient::class,
            OllamaClient::class,
        ]
    )
    fun `Should register LLMClient`(clazz: Class<*>) {
        verifyBeanIsRegistered<LLMClient>(clazz)
    }

    @ParameterizedTest
    @ValueSource(
        classes = [
            MultiLLMPromptExecutor::class,
        ]
    )
    fun `Should register beans(classes)`(clazz: Class<*>) {
        verifyBeanIsRegistered<Any>(clazz)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "anthropicExecutor",
            "deepSeekExecutor",
            "googleExecutor",
            "ollamaExecutor",
            "openAIExecutor",
            "openRouterExecutor",
        ]
    )
    fun `Should register SingleLLMExecutors`(beanName: String) {
        val llmExecutorBeanNames = applicationContext.getBeanNamesForType(
            SingleLLMPromptExecutor::class.java
        )
        assertTrue(llmExecutorBeanNames.contains(beanName)) {
            logger.info(
                "Registered ${SingleLLMPromptExecutor::class.simpleName} beans:${
                    llmExecutorBeanNames
                        .joinToString(separator = "\n\t", prefix = "\n\t")
                }"
            )

            "Bean named `$beanName` should have been registered"
        }
    }

    private inline fun <reified EXTRA> verifyBeanIsRegistered(clazz: Class<*>) {
        assertTrue(applicationContext.getBeansOfType(clazz).size == 1) {
            logger.info(
                "Registered beans:${
                    applicationContext.beanDefinitionNames
                        .joinToString(separator = "\n\t", prefix = "\n\t")
                }"
            )

            "Bean of type ${clazz.simpleName} should have been registered"
        }

        if (EXTRA::class != Any::class) {
            val bean = applicationContext.getBean(clazz)
            assertTrue(bean is EXTRA) {
                "Registered bean of type $clazz should be also a ${EXTRA::class}"
            }
        }
    }
}
