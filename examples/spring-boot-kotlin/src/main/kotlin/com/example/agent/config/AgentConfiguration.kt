package com.example.agent.config

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "agent")
data class AgentConfiguration(
    val version: String,
    val name: String,
    val description: String,
    val model: Model,
    @field:JsonProperty("system_prompt")
    val systemPrompt: String? = null,
    val tools: List<ToolDefinition> = emptyList(),
    val s3Persistence: S3Persistence? = null,
) {
    data class Model(
        val id: String,
        val options: ModelOptions? = null,
    )

    data class ModelOptions(
        val temperature: Double? = null,
    )

    data class ToolDefinition(
        val type: ToolType,
        val id: String,
        val options: ToolOptions = ToolOptions()
    )

    data class ToolOptions(
        val count: Int? = null,
        @field:JsonProperty("server_url")
        val serverUrl: String? = null,
        @field:JsonProperty("docker_image")
        val dockerImage: String? = null,
        @field:JsonProperty("docker_options")
        val dockerOptions: Map<String, String>? = null,

        @field:JsonProperty("other_options")
        val otherOptions: Map<String, String>? = null,
    )

    data class S3Persistence(
        @field:JsonProperty("enabled")
        val enabled: Boolean,
        @field:JsonProperty("region")
        val region: String,
        @field:JsonProperty("bucket")
        val bucket: String,
        @field:JsonProperty("path")
        val path: String,
    )

    enum class ToolType {
        SIMPLE, MCP
    }
}
