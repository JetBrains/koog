package com.example.agent

import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.TestPropertySource

@SpringBootTest(
    classes = [
        SpringBootKotlinApplicationTests.TestConfig::class,
    ],
    properties = [
        "debug=false", // set to true for troubleshooting
        "spring.main.banner-mode=off"
    ],
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@TestPropertySource(
    locations = ["classpath:/application.yml"]
)
class SpringBootKotlinApplicationTests {

    @Configuration
    @EnableAutoConfiguration
    @Suppress("unused")
    private class TestConfig

    private val logger = LoggerFactory.getLogger(SpringBootKotlinApplicationTests::class.java)

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Test
    fun `Should register googleExecutor`() {
        val beanName = "googleExecutor"
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
}
