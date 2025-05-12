package ai.jetbrains.code.prompt.message

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

@Serializable
public sealed interface Message {
    public val content: String
    public val role: Role

    @Serializable
    public sealed interface Request : Message
    @Serializable
    public sealed interface Response : Message


    @Serializable
    public enum class Role {
        System,
        User,
        Assistant,
        Tool
    }

    @Serializable
    public data class User(
        override val content: String
    ) : Request {
        override val role: Role = Role.User
    }

    @Serializable
    public data class Assistant(
        override val content: String
    ) : Response {
        override val role: Role = Role.Assistant
    }

    @Serializable
    public sealed interface Tool : Message {
        // Not all LLM backends support tool call ids for now
        public val id: String?
        public val tool: String

        @Serializable
        public data class Call(
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
        public data class Result(
            override val id: String?,
            override val tool: String,
            override val content: String
        ) : Tool, Request {
            override val role: Role = Role.Tool
        }
    }

    @Serializable
    public data class System(
        override val content: String
    ) : Request {
        override val role: Role = Role.System
    }
}
