package ai.grazie.code.agents.tools.registry.tools

import ai.grazie.code.agents.core.tools.*
import ai.grazie.code.agents.tools.registry.prompts.Template
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object RiderProjectTemplateTools {
    abstract class CreateStructureTool : Tool<CreateStructureTool.Args, CreateStructureTool.Result>() {
        @Serializable
        data class Args(
            val question: String,
            val options: List<String>,
            val projects: List<String>,
        ) : Tool.Args

        @Serializable
        sealed class Result : ToolResult {
            @Serializable
            enum class ResponseType(val value: String) {
                @SerialName("answer")
                ANSWER("answer"),
                @SerialName("addition")
                ADDITION("addition"),
                @SerialName("accept")
                ACCEPT("accept")
            }

            @Serializable
            data class Answer(
                val responseType: ResponseType = ResponseType.ANSWER,
                val question: String,
                val options: List<String>,
                val answer: String,
            ) : Result(), ToolResult.JSONSerializable<Answer> {
                override fun getSerializer(): KSerializer<Answer> = serializer()
            }

            @Serializable
            data class Addition(
                val responseType: ResponseType = ResponseType.ADDITION,
                val content: String,
            ) : Result(), ToolResult.JSONSerializable<Addition> {
                override fun getSerializer(): KSerializer<Addition> = serializer()
            }

            @Serializable
            data class Accept(
                val responseType: ResponseType = ResponseType.ACCEPT,
                val content: Map<String, Template?>,
            ) : Result(), ToolResult.JSONSerializable<Accept> {
                override fun getSerializer(): KSerializer<Accept> = serializer()
            }
        }

        final override val argsSerializer = Args.serializer()
        final override val descriptor = ToolDescriptor(
            name = "create_structure",
            description = "Suggest projects list for solution and clarification question.",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "thought",
                    description = "Thoughts on the solution content and project selections.",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "name",
                    description = "Suggested name for the .NET solution",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "projects",
                    description = "List of selected projects to include into solution in format TEMPLATE_ID:PROJECT_NAME:PROJECT_DESCRIPTION",
                    type = ToolParameterType.List(ToolParameterType.String)
                ),
                ToolParameterDescriptor(
                    name = "question",
                    description = "A technical clarification single-choice question focusing on solution components, architecture, or specific technologies relevant to the solution. Ensure it encourages thoughtful input on the selection and implementation of templates.",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "options",
                    description = "List of 3 possible answers that are technically clear, concise (1-3 words each), and directly related to the question.",
                    type = ToolParameterType.List(ToolParameterType.String)
                )
            )
        )
    }
    abstract class CreateDependenciesTool : Tool<CreateDependenciesTool.Args, CreateDependenciesTool.Result>() {
        @Serializable
        data class Args(
            val graph: List<String>,
        ) : Tool.Args

        @Serializable
        enum class Status {
            @SerialName("success") SUCCESS,
            @SerialName("fail")    FAIL,
        }

        @Serializable
        sealed class Result : ToolResult{
            @Serializable
            data class Success(
                val status: Status = Status.SUCCESS,
            ) : Result(), ToolResult.JSONSerializable<Success> {
                override fun getSerializer(): KSerializer<Success> = serializer()
            }

            @Serializable
            data class Fail(
                val status: Status = Status.FAIL,
            ) : Result(), ToolResult.JSONSerializable<Fail> {
                override fun getSerializer(): KSerializer<Fail> = serializer()
            }
        }

        final override val argsSerializer = Args.serializer()
        final override val descriptor = ToolDescriptor(
            name = "create_dependencies",
            description = "Suggest dependency in projects within solution.",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "graph",
                    description = "List of strings, each describing the relationship between a project and its dependencies in format 'PROJECT_NAME:DEPENDENT_PROJECT_NAME_1,DEPENDENT_PROJECT_NAME_2,...,DEPENDENT_PROJECT_NAME_N'",
                    type = ToolParameterType.List(ToolParameterType.String)
                )
            )
        )
    }
}