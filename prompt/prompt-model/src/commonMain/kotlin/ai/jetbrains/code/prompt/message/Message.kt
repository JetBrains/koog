package ai.jetbrains.code.prompt.message

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject


@Serializable
sealed interface Message {
    val content: String
    val role: Role

    @Serializable
    sealed interface Request : Message
    @Serializable
    sealed interface Response : Message


    @Serializable
    enum class Role {
        System,
        User,
        Assistant,
        Tool
    }

    @Serializable
    data class User(
        override val content: String
    ) : Request {
        override val role: Role = Role.User
    }

    @Serializable
    data class Assistant(
        override val content: String
    ) : Response {
        override val role: Role = Role.Assistant
    }

    @Serializable
    sealed interface Tool : Message {
        // Not all LLM backends support tool call ids for now
        val id: String?
        val tool: String

        @Serializable
        data class Call(
            override val id: String?,
            override val tool: String,
            override val content: String
        ) : Tool, Response {
            override val role: Role = Role.Tool

            val contentJson: JsonObject by lazy {
                Json.parseToJsonElement(content).jsonObject
            }
        }

        @Serializable
        data class Result(
            override val id: String?,
            override val tool: String,
            override val content: String
        ) : Tool, Request {
            override val role: Role = Role.Tool
        }
    }

    @Serializable
    data class System(
        override val content: String
    ) : Request {
        override val role: Role = Role.System
    }
}