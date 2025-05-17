package ai.koog.agents.example.memory.tools

import ai.koog.agents.core.tools.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

/**
 * Abstract base class for file search tools.
 * This tool provides safe file search and content reading capabilities
 * with support for common project file patterns.
 */
abstract class FileSearchTool : SimpleTool<FileSearchTool.Args>() {
    @Serializable
    data class Args(
        val pattern: String,
        @SerialName("base_dir")
        val baseDir: String = ".",
        @SerialName("read_content")
        val readContent: Boolean = false,
        @SerialName("max_depth")
        val maxDepth: Int = 10
    ) : Tool.Args

    override val argsSerializer = Args.serializer()

    override val descriptor = ToolDescriptor(
        name = "files",
        description = """
            Searches for files using glob patterns and optionally reads their content.
            Common patterns:
            - **/*.{kt,java} (All Kotlin and Java files)
            - **/build.gradle.kts (Gradle build files)
            - **/.editorconfig (Editor config files)
            - **/.idea/**.xml (IntelliJ IDEA config files)

            The tool ensures safe file access by:
            - Validating paths against base directory
            - Limiting search depth
            - Restricting to regular files only
        """.trimIndent(),
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "pattern",
                description = "Glob pattern for file search",
                type = ToolParameterType.String
            ),
            ToolParameterDescriptor(
                name = "base_dir",
                description = "Base directory for search",
                type = ToolParameterType.String
            ),
            ToolParameterDescriptor(
                name = "read_content",
                description = "Whether to read file contents",
                type = ToolParameterType.Boolean
            ),
            ToolParameterDescriptor(
                name = "max_depth",
                description = "Maximum directory depth for search",
                type = ToolParameterType.Integer
            )
        )
    )
}

/**
 * Implementation of FileSearchTool that searches files in the project using glob patterns.
 */
class FileSearchToolImpl : FileSearchTool() {
    override suspend fun doExecute(args: Args): String {
        val baseDir = Path.of(args.baseDir).toAbsolutePath().normalize()
        if (!baseDir.exists()) {
            return "Error: Base directory does not exist: $baseDir"
        }

        val matcher = FileSystems.getDefault().getPathMatcher("glob:${args.pattern}")
        val results = mutableListOf<String>()

        Files.walk(baseDir, args.maxDepth).use { paths ->
            paths.forEach { path ->
                if (path.isRegularFile() && matcher.matches(baseDir.relativize(path))) {
                    if (args.readContent) {
                        try {
                            val content = path.readText()
                            results.add("File: $path\nContent:\n$content\n")
                        } catch (e: Exception) {
                            results.add("File: $path\nError reading content: ${e.message}\n")
                        }
                    } else {
                        results.add(path.toString())
                    }
                }
            }
        }

        return if (results.isEmpty()) {
            "No files found matching pattern: ${args.pattern}"
        } else {
            results.joinToString("\n")
        }
    }
}
