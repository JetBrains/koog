package ai.koog.ktor

import io.ktor.server.config.yaml.YamlConfig
import io.ktor.server.testing.testApplication
import kotlin.test.Test

class YamlConfigurationLoadingTest {

    @Test
    fun testYamlConfigWithoutTimeout() = testApplication {
        environment {
            config = YamlConfig("application-no-timeout.yaml")!!
        }
        install(Koog)
        startApplication()
    }

    @Test
    fun testYamlConfigWithTimeout() = testApplication {
        environment {
            config = YamlConfig("application-with-timeout.yaml")!!
        }
        install(Koog)
        startApplication()
    }
}
