package ai.grazie.code.agents.example.errors

import ai.grazie.code.agents.core.tools.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object ErrorFixingTools {
    abstract class SearchFileTool : SimpleTool<SearchFileTool.Args>() {
        @Serializable
        data class Args(val path: String) : Tool.Args

        final override val argsSerializer = Args.serializer()

        final override val descriptor: ToolDescriptor = ToolDescriptor(
            name = "search_file",
            description = """
            Returns structure and code of the file given its path.
        
            Call this function if you need information about the file's contents.
            """.trimIndent(),
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "path",
                    description = "The path to file. Should be relative to the root folder of the project.",
                    type = ToolParameterType.String
                )
            )
        )
    }

    abstract class SearchReplaceTool : SimpleTool<SearchReplaceTool.Args>() {
        @kotlinx.serialization.Serializable
        data class Args(
            @SerialName("file_path")
            val filePath: String,
            @SerialName("search_content")
            val searchContent: String,
            @SerialName("replace_content")
            val replaceContent: String,
        ) : Tool.Args

        final override val argsSerializer = Args.serializer()

        final override val descriptor = ToolDescriptor(
            name = "search_replace",
            description = """
            Edits the contents of the specified file.
            The tool implement editing by searching for the specified content and replacing it with the specified replacement content.
            """.trimIndent(),
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "file_path",
                    description = "The path to the file to be edited. Should be relative to the root folder of the project.",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "search_content",
                    description = "The content to search for replacement.",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "replace_content",
                    description = "The content to replace with.",
                    type = ToolParameterType.String
                )
            )
        )
    }

    abstract class SearchObjectTool : SimpleTool<SearchObjectTool.Args>() {
        @Serializable
        data class Args(
            val type: String,
            val name: String
        ) : Tool.Args

        final override val argsSerializer = Args.serializer()

        final override val descriptor = ToolDescriptor(
            name = "search_object",
            description = """
            Returns source code of the code entity by type and name of the entity.
        
            Call this function if you need information about the code entity.
        
            Remember not to include encompassing identifiers (like 'foo.bar.etc') in 'name' argument, always search only with the entity's name (like 'etc').
            This function will return the line numbers for corresponding entity, which might be useful for editing files correctly.
            """.trimIndent(),
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "type",
                    description = "Entity type. Can be: module, function, class.",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "name",
                    description = "Entity name. Do not include the encompassing entities here: if you're searching for class Bar in module foo, still pass only 'Bar'.",
                    type = ToolParameterType.String
                )
            )
        )
    }

    abstract class EditTool : SimpleTool<EditTool.Args>() {
        @Serializable
        data class Args(
            @SerialName("file_path")
            val filePath: String,
            @SerialName("start_line_inclusive")
            val startLineInclusive: Int,
            @SerialName("end_line_exclusive")
            val endLineExclusive: Int,
            @SerialName("new_content")
            val newContent: String,
        ) : Tool.Args

        final override val argsSerializer = Args.serializer()

        final override val descriptor = ToolDescriptor(
            name = "edit",
            description = """
            Edits the contents of the specified file.
        
            Specifically, replaces ['start_line_inclusive', 'end_line_exclusive') fragment of the 'file_path' with 'new_content'.
        
            Note that the 'end_line' is exclusive and that the line numbers start with 1.
            """.trimIndent(),
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "file_path",
                    description = "The path to the file to be edited. Should be relative to the root folder of the project.",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "start_line_inclusive",
                    description = "Start line number of the file fragment to be replaced. Note that the line numbers start with 1.",
                    type = ToolParameterType.Integer
                ),
                ToolParameterDescriptor(
                    name = "end_line_exclusive",
                    description = "End line number of the file fragment to be replaced. Note that the 'end_line' is exclusive and that the line numbers start with 1.",
                    type = ToolParameterType.Integer
                ),
                ToolParameterDescriptor(
                    name = "new_content",
                    description = "The content to replace the old one between ['start_line', 'end_line').",
                    type = ToolParameterType.String
                )
            )
        )
    }

    abstract class RunTestTool : SimpleTool<RunTestTool.Args>() {
        @Serializable
        data class Args(val path: String) : Tool.Args

        final override val argsSerializer = Args.serializer()

        final override val descriptor = ToolDescriptor(
            name = "run_test",
            description = "Executes the tests from the specified file. Useful to check if the error has been fixed or not.",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "path",
                    description = "The path to file with tests to execute.",
                    type = ToolParameterType.String
                )
            )
        )
    }
}