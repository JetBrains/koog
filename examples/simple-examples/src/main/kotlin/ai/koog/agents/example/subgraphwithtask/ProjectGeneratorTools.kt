package ai.koog.agents.example.subgraphwithtask

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.pathString

object ProjectGeneratorTools {
    class CreateFileTool(val rootProjectPath: Path) : Tool<CreateFileTool.Args, CreateFileTool.Result>() {
        @Serializable
        data class Args(val path: String, val content: String)

        @Serializable
        data class Result(val successful: Boolean, val comment: String? = null)

        override val argsSerializer: KSerializer<Args> = Args.serializer()
        override val resultSerializer: KSerializer<Result> = Result.serializer()

        override val descriptor: ToolDescriptor = ToolDescriptor(
            name = "create_file",
            description = "Creates a file under the provided relative path, with the specified content",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "path",
                    description = "Path to the file",
                    type = ToolParameterType.String,
                ),
                ToolParameterDescriptor(
                    name = "content",
                    description = "Content of the file",
                    type = ToolParameterType.String,
                )
            )
        )

        override suspend fun execute(args: Args): Result {
            val path = rootProjectPath.resolve(args.path).normalize()
            val content = args.content
            return try {
                val filePath = path.toAbsolutePath()
                if (!Files.exists(filePath.parent)) {
                    return Result(successful = false, comment = "Parent directory does not exist")
                }
                Files.writeString(filePath, content)
                Result(successful = true)
            } catch (e: Exception) {
                Result(successful = false, comment = e.message)
            }
        }
    }

    class ReadFileTool(val rootProjectPath: Path) : Tool<ReadFileTool.Args, ReadFileTool.Result>() {
        @Serializable
        data class Args(val path: String)

        @Serializable
        data class Result(
            val successful: Boolean,
            val fileContent: String? = null,
            val comment: String? = null
        )

        override val argsSerializer: KSerializer<Args> = Args.serializer()
        override val resultSerializer: KSerializer<Result> = Result.serializer()

        override val descriptor: ToolDescriptor = ToolDescriptor(
            name = "read_file",
            description = "Reads a file under the provided RELATIVE path, with the specified content",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "path",
                    description = "Path to the file",
                    type = ToolParameterType.String,
                )
            )
        )

        override suspend fun execute(args: Args): Result {
            val path = rootProjectPath.resolve(args.path).normalize()
            return try {
                if (!Files.exists(path)) {
                    return Result(
                        successful = false,
                        comment = "File does not exist at the provided path: $path"
                    )
                }
                if (!Files.isRegularFile(path)) {
                    return Result(
                        successful = false,
                        comment = "$path is NOT a file"
                    )
                }

                val content = Files.readString(path)
                Result(successful = true, fileContent = content)
            } catch (e: Exception) {
                Result(
                    successful = false,
                    comment = "Failed to read file: ${e::class.qualifiedName} ${e.message}\nStacktrace: ${
                        e.stackTraceToString().take(300)
                    }"
                )
            }
        }
    }

    class LSDirectoriesTool(val rootProjectPath: Path) : Tool<LSDirectoriesTool.Args, LSDirectoriesTool.Result>() {
        @Serializable
        data class Args(val path: String)

        @Serializable
        data class Result(
            val successful: Boolean,
            val comment: String? = null,
            val content: List<String>? = null,
        )

        override val argsSerializer: KSerializer<Args> = Args.serializer()
        override val resultSerializer: KSerializer<Result> = Result.serializer()

        override val descriptor: ToolDescriptor = ToolDescriptor(
            name = "ls_directory",
            description = "Lists all the files and directories under the provided RELATIVE path",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "path",
                    description = "Path to the directory",
                    type = ToolParameterType.String,
                )
            )
        )

        override suspend fun execute(args: Args): Result {
            val path = rootProjectPath.resolve(args.path).normalize()
            return try {
                if (!Files.exists(path)) {
                    return Result(
                        successful = false,
                        comment = "Directory does not exist at the provided path: $path"
                    )
                }
                if (!Files.isDirectory(path)) {
                    return Result(
                        successful = false,
                        comment = "$path is NOT a directory"
                    )
                }

                val content = Files.list(path).map { rootProjectPath.relativize(it).pathString }.toList()
                Result(successful = true, content = content)
            } catch (e: Exception) {
                Result(
                    successful = false,
                    comment = "Failed to read file: ${e::class.qualifiedName} ${e.message}\nStacktrace: ${
                        e.stackTraceToString().take(300)
                    }"
                )
            }
        }
    }

    class CreateDirectoryTool(val rootProjectPath: Path) :
        Tool<CreateDirectoryTool.Args, CreateDirectoryTool.Result>() {
        @Serializable
        data class Args(val path: String)

        @Serializable
        data class Result(val successful: Boolean, val comment: String? = null)

        override val argsSerializer: KSerializer<Args> = Args.serializer()
        override val resultSerializer: KSerializer<Result> = Result.serializer()

        override val descriptor: ToolDescriptor = ToolDescriptor(
            name = "create_directory",
            description = "Creates a directory under the provided relative path",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "path",
                    description = "Path to the file",
                    type = ToolParameterType.String,
                )
            )
        )

        override suspend fun execute(args: Args): Result {
            val path = rootProjectPath.resolve(args.path).normalize()
            return try {
                val dirPath = path
                if (!Files.exists(dirPath)) {
                    Files.createDirectories(dirPath)
                }
                Result(successful = true)
            } catch (e: Exception) {
                Result(successful = false, comment = e.message)
            }
        }
    }

    class DeleteDirectoryTool(val rootProjectPath: Path) :
        Tool<DeleteDirectoryTool.Args, DeleteDirectoryTool.Result>() {
        @Serializable
        data class Args(val path: String)

        @Serializable
        data class Result(val successful: Boolean, val comment: String? = null)

        override val argsSerializer: KSerializer<Args> = Args.serializer()
        override val resultSerializer: KSerializer<Result> = Result.serializer()

        override val descriptor: ToolDescriptor = ToolDescriptor(
            name = "delete_directory",
            description = "Removes a directory under the provided relative path",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "path",
                    description = "Path to the directory to remove",
                    type = ToolParameterType.String,
                )
            )
        )

        override suspend fun execute(args: Args): Result {
            val path = rootProjectPath.resolve(args.path).normalize()
            return try {
                val dirPath = path
                if (!Files.exists(dirPath)) {
                    return Result(successful = false, comment = "Directory already does not exist")
                }
                if (!Files.isDirectory(dirPath)) {
                    return Result(successful = false, comment = "The provided path is not a directory")
                }
                Files.walk(dirPath)
                    .sorted(Comparator.reverseOrder())
                    .forEach(Files::delete)
                Result(successful = true)
            } catch (e: Exception) {
                Result(successful = false, comment = e.message)
            }
        }
    }

    class DeleteFileTool(val rootProjectPath: Path) : Tool<DeleteFileTool.Args, DeleteFileTool.Result>() {
        @Serializable
        data class Args(val path: String)

        @Serializable
        data class Result(val successful: Boolean, val comment: String? = null)

        override val argsSerializer: KSerializer<Args> = Args.serializer()
        override val resultSerializer: KSerializer<Result> = Result.serializer()

        override val descriptor: ToolDescriptor = ToolDescriptor(
            name = "delete_file",
            description = "Deletes a file under the provided relative path",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "path",
                    description = "Path to the file to delete",
                    type = ToolParameterType.String,
                )
            )
        )

        override suspend fun execute(args: Args): Result {
            val path = rootProjectPath.resolve(args.path).normalize()
            return try {
                val filePath = path
                if (!Files.exists(filePath)) {
                    return Result(successful = false, comment = "File does not exist")
                }
                if (!Files.isRegularFile(filePath)) {
                    return Result(successful = false, comment = "The provided path is not a file")
                }
                Files.delete(filePath)
                Result(successful = true)
            } catch (e: Exception) {
                Result(successful = false, comment = e.message)
            }
        }
    }

    class RunCommand(val rootProjectPath: Path) : Tool<RunCommand.Args, RunCommand.Result>() {
        @Serializable
        data class Args(val bashCommand: String)

        @Serializable
        data class Result(val successful: Boolean, val comment: String? = null)

        override val argsSerializer: KSerializer<Args> = Args.serializer()
        override val resultSerializer: KSerializer<Result> = Result.serializer()

        override val descriptor: ToolDescriptor = ToolDescriptor(
            name = "run_bash_command",
            description = "Runs the provided bash command in the project root directory",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "bashCommand",
                    description = "Command to run",
                    type = ToolParameterType.String,
                )
            )
        )

        override suspend fun execute(args: Args): Result {
            if (args.bashCommand.isBlank()) {
                return Result(successful = false, comment = "Bash command is blank")
            }
            if (args.bashCommand.startsWith("ls")) {
                return Result(
                    successful = false,
                    comment = "Please, use `${LSDirectoriesTool::class.java.simpleName}` tool for listing files and directories"
                )
            }
            if (args.bashCommand.startsWith("cat")) {
                return Result(
                    successful = false,
                    comment = "Please, use `${ReadFileTool::class.java.simpleName}` tool for reading files"
                )
            }
            if (args.bashCommand.startsWith("rm")) {
                return Result(
                    successful = false,
                    comment = "Removing files or directories is not allowed! Please, only observe and run builds/tests/etc."
                )
            }

            return try {
                val processBuilder = ProcessBuilder()
                    .command("bash", "-c", args.bashCommand)
                    .directory(rootProjectPath.toFile())
                    .redirectErrorStream(true)

                val process = processBuilder.start()
                val output = process.inputStream.bufferedReader().readText()
                val successful = process.waitFor(50, TimeUnit.SECONDS)

                if (successful == true) {
                    Result(successful = true, comment = output)
                } else {
                    Result(
                        successful = false,
                        comment = "Command failed. Output: ${process.errorStream.bufferedReader().readText()}"
                    )
                }
            } catch (e: Exception) {
                Result(
                    successful = false,
                    comment = "Exception occurred: ${e::class.qualifiedName} ${e.message}\nStacktrace: ${
                        e.stackTraceToString().take(300)
                    }"
                )
            }
        }
    }
}
