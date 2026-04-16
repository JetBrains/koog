package ai.koog.koogelis

import ai.koog.koogelis.logging.KoogelisLogger
import ai.koog.koogelis.persistence.KoogelisPersistenceStorageProvider


@OptIn(ExperimentalJsExport::class)
@JsExport
data class AgentConfiguration(
    val name: String,
    val llm: Llm,
    val strategy: AgentStrategy = AgentStrategy.SINGLE_RUN,
    val systemPrompt: String = "You are a helpful assistant.",
    val tools: Array<ToolDefinition> = emptyArray(),
    val maxAgentIterations: Int = DEFAULT_MAX_AGENT_ITERATIONS,
    val tracingEnabled: Boolean = false,
    val koogelisLogger: KoogelisLogger? = null,
    val persistenceStorageProvider: KoogelisPersistenceStorageProvider? = null,
) {

    companion object {
        const val DEFAULT_MAX_AGENT_ITERATIONS: Int = 100
    }

    data class Llm(
        val id: String,
        val url: String? = null,
        val authToken: String? = null,
        val llmParams: LlmParams? = null
    )

    data class LlmParams(
        val temperature: Double? = null,
        val maxTokens: Int? = null
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class.js != other::class.js) return false

            other as LlmParams

            if (temperature != other.temperature) return false
            if (maxTokens != other.maxTokens) return false

            return true
        }

        override fun hashCode(): Int {
            var result = temperature?.hashCode() ?: 0
            result = 31 * result + (maxTokens ?: 0)
            return result
        }

    }

    data class ToolDefinition(
        val type: ToolType,
        val id: String,
        val options: ToolOptions?
    )

    data class ToolOptions(
        val serverUrl: String,
        val transportType: TransportType = TransportType.STREAMABLE_HTTP,
        val headersKeys: Array<String> = emptyArray(),
        val headersValues: Array<String> = emptyArray()
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class.js != other::class.js) return false

            other as ToolOptions

            if (serverUrl != other.serverUrl) return false
            if (transportType != other.transportType) return false
            if (!headersKeys.contentEquals(other.headersKeys)) return false
            if (!headersValues.contentEquals(other.headersValues)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = serverUrl.hashCode()
            result = 31 * result + transportType.hashCode()
            result = 31 * result + headersKeys.contentHashCode()
            result = 31 * result + headersValues.contentHashCode()
            return result
        }
    }

    enum class AgentStrategy {
        SINGLE_RUN, RE_ACT
    }

    enum class ToolType {
        SIMPLE, MCP
    }

    enum class TransportType {
        SSE, STREAMABLE_HTTP
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class.js != other::class.js) return false

        other as AgentConfiguration

        if (maxAgentIterations != other.maxAgentIterations) return false
        if (tracingEnabled != other.tracingEnabled) return false
        if (name != other.name) return false
        if (llm != other.llm) return false
        if (strategy != other.strategy) return false
        if (systemPrompt != other.systemPrompt) return false
        if (!tools.contentEquals(other.tools)) return false
        if (koogelisLogger != other.koogelisLogger) return false
        if (persistenceStorageProvider != other.persistenceStorageProvider) return false

        return true
    }

    override fun hashCode(): Int {
        var result = maxAgentIterations
        result = 31 * result + tracingEnabled.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + llm.hashCode()
        result = 31 * result + strategy.hashCode()
        result = 31 * result + systemPrompt.hashCode()
        result = 31 * result + tools.contentHashCode()
        result = 31 * result + (koogelisLogger?.hashCode() ?: 0)
        result = 31 * result + (persistenceStorageProvider?.hashCode() ?: 0)
        return result
    }
}