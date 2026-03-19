package ai.koog.spring.ai.vectorstore

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for the Koog Spring AI vector-store adapter.
 *
 * Prefix: `koog.spring.ai.vectorstore`
 */
@ConfigurationProperties(prefix = "koog.spring.ai.vectorstore")
public data class KoogSpringAiVectorStoreProperties(
    val enabled: Boolean = true,
    val vectorStoreBeanName: String? = null,
    val dispatcher: DispatcherProperties = DispatcherProperties(),
) {
    /**
     * Dispatcher settings for blocking Spring AI VectorStore calls.
     */
    public data class DispatcherProperties(
        val type: DispatcherType = DispatcherType.AUTO,
        val parallelism: Int = 0,
    )

    /**
     * Dispatcher type for blocking VectorStore calls.
     */
    public enum class DispatcherType {
        AUTO,
        IO,
    }
}
