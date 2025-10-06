package ai.koog.spring.sandwich

import org.springframework.boot.info.BuildProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.Properties

@Configuration
open class BuildInfoConfig {
    @Bean
    open fun buildProperties(): BuildProperties {
        // Provide sensible defaults when build-info.properties isn't available
        val props = Properties().apply {
            setProperty("name", System.getProperty("spring.application.name") ?: "koog-spring-sandwich")
            setProperty("version", System.getProperty("app.version") ?: "0.0.0")
        }
        return BuildProperties(props)
    }
}
