package ai.grazie.code.agents.tools.registry.tools.composio

import ai.grazie.code.agents.core.tools.Tool
import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.code.agents.core.tools.ToolParameterDescriptor
import ai.grazie.code.agents.core.tools.ToolParameterType
import ai.grazie.code.agents.core.tools.ToolResult
import ai.grazie.code.agents.core.tools.serialization.ToolResultStringSerializer
import ai.grazie.code.agents.tools.registry.utils.formatLinesWithNumbers
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.collections.component1
import kotlin.collections.component2

object ComposioTools {
    sealed interface ComposioToolArgs : Tool.Args {
        val thought: String?

        companion object {
            val optionalParameters = listOf(
                ToolParameterDescriptor(
                    name = "thought",
                    description = "LLM reasoning for calling this tool.",
                    type = ToolParameterType.String
                )
            )
        }
    }

    /**
     * CodeAnalysisTools contains tool descriptions for code analysis.
     *
     * Please, reference tools and parameter names directly from within this object (e.g., [GetClassInfo], [GetMethodBody], etc.)
     * to avoid typos in your code.
     */
    object CodeAnalysisTools {
        abstract class GetClassInfo : Tool<GetClassInfo.Args, GetClassInfo.Result>() {
            @Serializable
            data class Args(
                @SerialName("class_name")
                val className: String,
                override val thought: String? = null
            ) : ComposioToolArgs

            @Serializable
            data class Result(val matchingClasses: List<ClassMetaInfo>) : ToolResult {
                override fun toStringDefault(): String {
                    return """
                                <Total ${matchingClasses.size} result(s) found:>
                                ${
                        ComposioJson.encodeToString(
                            ComposioListSerializer(ClassMetaInfo.Serializer),
                            matchingClasses
                        )
                    }
                            """.trimIndent()
                }
            }

            override val argsSerializer: KSerializer<Args> = Args.serializer()

            override val descriptor: ToolDescriptor = ToolDescriptor(
                name = "CODE_ANALYSIS_TOOL_GET_CLASS_INFO",
                description = "This Tool Retrieves And Formats Detailed Information About A Specified Class In A Given Repository. " +
                        "Use This Action To Obtain Details About Structure, Methods, Attributes, Class Summary, Variables, etc.",
                requiredParameters = listOf(
                    ToolParameterDescriptor(
                        name = "class_name",
                        description = "Name of the class for which information is requested.",
                        type = ToolParameterType.String,
                    )
                ),
                optionalParameters = ComposioToolArgs.optionalParameters
            )
        }

        abstract class GetMethodBody : Tool<GetMethodBody.Args, GetMethodBody.Result>() {
            @Serializable
            data class Args(
                @SerialName("method_name")
                val methodName: String,
                @SerialName("class_name")
                val className: String? = null,
                override val thought: String? = null
            ) : ComposioToolArgs

            @Serializable
            data class Result(val matchingMethods: List<FullMethodData>) : ToolResult {
                override fun toStringDefault(): String {
                    return """
                                <Total ${matchingMethods.size} result(s) found:>
                                ${
                        ComposioJson.encodeToString(
                            ComposioSearchResultSerializer(FullMethodData.Serializer),
                            matchingMethods
                        )
                    }
                            """.trimIndent()
                }
            }

            override val argsSerializer: KSerializer<Args> = Args.serializer()

            override val descriptor: ToolDescriptor = ToolDescriptor(
                name = "CODE_ANALYSIS_TOOL_GET_METHOD_BODY",
                description = "Retrieves The Body Of A Specified Method From A Given Repository. " +
                        "This Action Can Retrieve Methods From Either Class Scope or Global Scope.",
                requiredParameters = listOf(
                    ToolParameterDescriptor(
                        name = "method_name",
                        description = "Name of the method whose body is to be retrieved.",
                        type = ToolParameterType.String
                    )
                ),
                optionalParameters = listOf(
                    ToolParameterDescriptor(
                        name = "class_name",
                        description = "Name of the class containing the target method.",
                        type = ToolParameterType.String
                    )
                ) + ComposioToolArgs.optionalParameters
            )
        }

        abstract class GetMethodSignature : Tool<GetMethodSignature.Args, GetMethodSignature.Result>() {
            @Serializable
            data class Args(
                @SerialName("method_name")
                val methodName: String,
                @SerialName("class_name")
                val className: String?,
                override val thought: String? = null
            ) : ComposioToolArgs

            @Serializable
            data class Result(val matchingMethods: List<MethodMetaInfo>) : ToolResult {
                override fun toStringDefault(): String {
                    return ComposioJson.encodeToString(
                        ComposioSearchResultSerializer(MethodMetaInfo.Serializer),
                        matchingMethods
                    )
                }
            }

            override val argsSerializer: KSerializer<Args> = Args.serializer()

            override val descriptor: ToolDescriptor = ToolDescriptor(
                name = "CODE_ANALYSIS_TOOL_GET_METHOD_SIGNATURE",
                description = "Retrieves The Signature Of A Specified Method From A Given Repository. " +
                        "Can Retrieve Methods From Either Class Scope or Global Scope.",
                requiredParameters = listOf(
                    ToolParameterDescriptor(
                        name = "method_name",
                        description = "Name of the method whose signature is to be retrieved.",
                        type = ToolParameterType.String
                    )
                ),
                optionalParameters = listOf(
                    ToolParameterDescriptor(
                        name = "class_name",
                        description = "Name of the class containing the target method.",
                        type = ToolParameterType.String
                    )
                ) + ComposioToolArgs.optionalParameters
            )
        }
    }

    /**
     * FileTools contains tool descriptions for file-related operations.
     */
    object FileTools {
        /**
         * Creates a tree representation of the Git repository and outputs to the `git_repo_tree.txt` file.
         *
         *     This action generates a text file containing the tree structure of the
         *     current Git repository. It lists all files tracked by Git in the repository.
         *
         *     Usage example:
         *         Provide the git_repo_path to generate the repository tree for that specific
         *         repository. If not provided, it will use the current directory.
         *
         *
         */
        abstract class DumpGitRepoTreeFile : Tool<DumpGitRepoTreeFile.Args, DumpGitRepoTreeFile.Result>() {
            @Serializable
            data class Args(
                @SerialName("git_repo_path")
                val gitRepoPath: String = ".",
                override val thought: String? = null
            ) : ComposioToolArgs

            /**
             * Result of the [DumpGitRepoTreeFile] request.
             *
             * @property error Error message if the action failed.
             * @property message Status message or error description.
             * @property success Whether the tree creation was successful.
             */
            @Serializable
            data class Result(
                val error: String? = null,
                val message: String = "",
                val success: Boolean = true
            ) : ToolResult {
                override fun toStringDefault(): String {
                    return buildString {
                        if (!success) {
                            appendLine("Error: ${error ?: "Unknown error"}")
                        }
                        if (message.isNotEmpty()) {
                            appendLine(message)
                        }
                    }.trimEnd()
                }
            }

            override val argsSerializer: KSerializer<Args> = Args.serializer()

            override val descriptor: ToolDescriptor = ToolDescriptor(
                name = "FILETOOL_GIT_REPO_TREE",
                description = "Creates A Tree Representation Of The Git Repository. " +
                        "Lists all files tracked by Git in the repo.",
                requiredParameters = emptyList(),
                optionalParameters = listOf(
                    ToolParameterDescriptor(
                        name = "git_repo_path",
                        description = "Relative path of the git repository. Defaults to current directory.",
                        type = ToolParameterType.String
                    )
                ) + ComposioToolArgs.optionalParameters
            )
        }

        abstract class ListFiles : Tool<ListFiles.Args, ListFiles.Result>() {
            @Serializable
            data class Args(override val thought: String? = null) : ComposioToolArgs

            /**
             * Result of the [ListFiles] request.
             *
             * @property currentWorkingDirectory Current working directory of the file manager.
             * @property error Error message, if any.
             * @property files List of files and their types (e.g., file or dir).
             */
            @Serializable
            data class Result(
                val currentWorkingDirectory: String = "",
                val error: String = "",
                val files: List<Pair<String, String>> = emptyList()
            ) : ToolResult {
                override fun toStringDefault(): String {
                    return buildString {
                        appendLine("Current directory: ${currentWorkingDirectory}")
                        if (error.isNotEmpty()) {
                            appendLine("Error: ${error}")
                        }
                        if (files.isNotEmpty()) {
                            appendLine("Files:")
                            files.forEach { (name, type) ->
                                appendLine("* $name ($type)")
                            }
                        } else {
                            appendLine("No files found")
                        }
                    }.trimEnd()
                }
            }

            override val argsSerializer: KSerializer<Args> = Args.serializer()


            override val descriptor: ToolDescriptor = ToolDescriptor(
                name = "FILETOOL_LIST_FILES",
                description = "Lists all files and directories in the current directory.",
                requiredParameters = emptyList(),
                optionalParameters = ComposioToolArgs.optionalParameters
            )
        }

        abstract class ChangeWorkingDirectory : Tool<ChangeWorkingDirectory.Args, ChangeWorkingDirectory.Result>() {
            @Serializable
            data class Args(val path: String, override val thought: String? = null) : ComposioToolArgs

            /**
             * Result of the [ChangeWorkingDirectory] request.
             *
             * @property error Error message if the action failed.
             */
            @Serializable
            data class Result(
                val error: String? = null,
            ) : ToolResult {
                override fun toStringDefault(): String {
                    return error?.let { "Error: $it" } ?: "Directory changed successfully"

                }
            }

            override val argsSerializer: KSerializer<Args> = Args.serializer()

            override val descriptor: ToolDescriptor = ToolDescriptor(
                name = "FILETOOL_CHANGE_WORKING_DIRECTORY",
                description = "Changes the current working directory to the specified path.",
                requiredParameters = listOf(
                    ToolParameterDescriptor(
                        name = "path",
                        description = "The path to change the current working directory to. " +
                                "Can be absolute, relative to the current working directory, or use '..' to navigate up the directory tree.",
                        type = ToolParameterType.String
                    )
                ),
                optionalParameters = ComposioToolArgs.optionalParameters
            )
        }

        abstract class OpenFile : Tool<OpenFile.Args, OpenFile.Result>() {
            @Serializable
            data class Args(
                @SerialName("file_path")
                val filePath: String,
                @SerialName("line_number")
                val lineNumber: Int = 0,
                override val thought: String? = null
            ) : ComposioToolArgs

            /**
             * Result of the [OpenFile] request.
             *
             * @property currentWorkingDirectory Current working directory of the file manager.
             * @property error Error message if the action failed.
             * @property lines File content with their line numbers.
             * @property message Message to display to the user.
             */
            @Serializable
            data class Result(
                val currentWorkingDirectory: String = "",
                val error: String? = null,
                val lines: List<String>? = null,
                val startLineNumber: Int = 0,
                val message: String = ""
            ) : ToolResult {
                override fun toStringDefault(): String {
                    val resultJson = ResultJson(
                        currentWorkingDirectory = currentWorkingDirectory,
                        error = error,
                        message = message,
                        lines = lines?.let {
                            formatLinesWithNumbers(lines, startLineNumber)
                        } ?: ""
                    )

                    return ComposioJson.encodeToString(ResultJson.serializer(), resultJson)
                }
            }

            override val argsSerializer: KSerializer<Args> = Args.serializer()

            @Serializable
            private data class ResultJson(
                val currentWorkingDirectory: String,
                val error: String?,
                val lines: String,
                val message: String
            )

            override val descriptor: ToolDescriptor = ToolDescriptor(
                name = "FILETOOL_OPEN_FILE",
                description = "Opens a file in the editor. If line number is provided, it will display from that line.",
                requiredParameters = listOf(
                    ToolParameterDescriptor(
                        name = "file_path",
                        description = "Path to the file to open.",
                        type = ToolParameterType.String
                    )
                ),
                optionalParameters = listOf(
                    ToolParameterDescriptor(
                        name = "line_number",
                        description = "Line number from which to begin display. Defaults to the start of the file.",
                        type = ToolParameterType.Integer
                    )
                ) + ComposioToolArgs.optionalParameters
            )
        }

        abstract class Scroll : Tool<Scroll.Args, Scroll.Result>() {
            @Serializable
            data class Args(
                val direction: String = "down",
                val lines: Int = 0,
                @SerialName("scroll_id")
                val scrollId: Int = 0,
                override val thought: String? = null
            ) : ComposioToolArgs

            /**
             * Result of the [Scroll] request.
             *
             * @property currentWorkingDirectory Current working directory of the file manager.
             * @property error Error message, if any.
             * @property lines Visible content of the file in the editor.
             * @property startLineNumber First visible line number in the editor.
             * @property message Message to display to the user.
             */
            @Serializable
            data class Result(
                val currentWorkingDirectory: String = "",
                val error: String = "",
                val lines: List<String>? = null,
                val startLineNumber: Int = 0,
                val message: String = ""
            ) : ToolResult {
                override fun toStringDefault(): String {

                    val resultJson = ResultJson(
                        currentWorkingDirectory = currentWorkingDirectory,
                        error = error,
                        message = message,
                        lines = lines?.let {
                            formatLinesWithNumbers(lines, startLineNumber)
                        } ?: ""
                    )

                    return ComposioJson.encodeToString(ResultJson.serializer(), resultJson)
                }
            }

            override val argsSerializer: KSerializer<Args> = Args.serializer()

            @Serializable
            private data class ResultJson(
                val currentWorkingDirectory: String,
                val error: String?,
                val lines: String,
                val message: String
            )

            override val descriptor: ToolDescriptor = ToolDescriptor(
                name = "FILETOOL_SCROLL",
                description = "Scrolls the view of the open file.",
                requiredParameters = emptyList(),
                optionalParameters = listOf(
                    ToolParameterDescriptor(
                        name = "direction",
                        description = "Scroll direction: up or down.",
                        type = ToolParameterType.String
                    ),
                    ToolParameterDescriptor(
                        name = "lines",
                        description = "Number of lines to scroll by. Defaults to 0 (automatic).",
                        type = ToolParameterType.Integer
                    ),
                    ToolParameterDescriptor(
                        name = "scroll_id",
                        description = "Unique ID for consecutive scrolling commands.",
                        type = ToolParameterType.Integer
                    )
                ) + ComposioToolArgs.optionalParameters
            )
        }

        abstract class EditFile : Tool<EditFile.Args, EditFile.Result>() {
            @Serializable
            data class Args(
                val text: String,
                @SerialName("start_line")
                val startLine: Int,
                @SerialName("end_line")
                val endLine: Int? = null,
                @SerialName("file_path")
                val filePath: String? = null,
                override val thought: String? = null
            ) : ComposioToolArgs

            /**
             * Result of the [EditFile] request.
             *
             * @property currentWorkingDirectory Current working directory of the file manager.
             * @property error Error message if the action failed.
             * @property oldText The original text before edits.
             * @property updatedText The text updated after the edit.
             */
            @Serializable
            data class Result(
                val currentWorkingDirectory: String = "",
                val error: String? = null,
                val oldText: String? = null,
                val updatedText: String? = null
            ) : ToolResult {
                override fun toStringDefault(): String {
                    return buildString {
                        if (error != null) {
                            appendLine("Error: ${error}")
                            return@buildString
                        }
                        appendLine("Working directory: ${currentWorkingDirectory}")
                        if (oldText != null) {
                            appendLine("Original text:")
                            appendLine("```")
                            appendLine(oldText)
                            appendLine("```")
                        }
                        if (updatedText != null) {
                            appendLine("Updated text:")
                            appendLine("```")
                            appendLine(updatedText)
                            appendLine("```")
                        }
                    }.trimEnd()
                }
            }

            override val argsSerializer: KSerializer<Args> = Args.serializer()

            override val descriptor: ToolDescriptor = ToolDescriptor(
                name = "FILETOOL_EDIT_FILE",
                description = "Edits a file at specific line numbers. Ensure proper syntax and indentation.",
                requiredParameters = listOf(
                    ToolParameterDescriptor(
                        name = "text",
                        description = "Text to insert or replace in the file.",
                        type = ToolParameterType.String
                    ),
                    ToolParameterDescriptor(
                        name = "start_line",
                        description = "Start line of the edit range.",
                        type = ToolParameterType.Integer
                    )
                ),
                optionalParameters = listOf(
                    ToolParameterDescriptor(
                        name = "end_line",
                        description = "End line of the edit range. If omitted, will just insert text.",
                        type = ToolParameterType.Integer
                    ),
                    ToolParameterDescriptor(
                        name = "file_path",
                        description = "Path of the file to edit. If omitted, edits the currently open file.",
                        type = ToolParameterType.String
                    )
                ) + ComposioToolArgs.optionalParameters
            )
        }

        abstract class CreateFile : Tool<CreateFile.Args, CreateFile.Result>() {
            @Serializable
            data class Args(
                val path: String,
                @SerialName("is_directory")
                val isDirectory: Boolean = false,
                override val thought: String? = null
            ) : ComposioToolArgs

            /**
             * Result of the [CreateFile] request.
             *
             * @property currentWorkingDirectory Current working directory of the file manager.
             * @property error Error message if any.
             * @property path Path of the created file or directory.
             */
            @Serializable
            data class Result(
                val currentWorkingDirectory: String = "",
                val error: String? = null,
                val path: String? = null
            ) : ToolResult {
                override fun toStringDefault(): String {
                    return buildString {
                        if (error != null) {
                            appendLine("Error: ${error}")
                        } else {
                            appendLine("Working directory: ${currentWorkingDirectory}")
                            appendLine("Successfully created: ${path}")
                        }
                    }.trimEnd()
                }
            }

            override val argsSerializer: KSerializer<Args> = Args.serializer()

            override val descriptor: ToolDescriptor = ToolDescriptor(
                name = "FILETOOL_CREATE_FILE",
                description = "Creates a new file or directory.",
                requiredParameters = listOf(
                    ToolParameterDescriptor(
                        name = "path",
                        description = "Path of the file/directory to create.",
                        type = ToolParameterType.String
                    )
                ),
                optionalParameters = listOf(
                    ToolParameterDescriptor(
                        name = "is_directory",
                        description = "Whether to create a directory instead of a file.",
                        type = ToolParameterType.Boolean
                    )
                ) + ComposioToolArgs.optionalParameters
            )
        }

        abstract class FindFile : Tool<FindFile.Args, FindFile.Result>() {
            @Serializable
            data class Args(
                val pattern: String,
                val depth: Int? = null,
                @SerialName("case_sensitive")
                val caseSensitive: Boolean = false,
                val include: List<String>? = null,
                val exclude: List<String>? = null,
                override val thought: String? = null
            ) : ComposioToolArgs

            /**
             * Result of the [FindFile] request.
             *
             * @property currentWorkingDirectory Current working directory of the file manager.
             * @property error Error message if any.
             * @property message Informational message regarding the search results.
             * @property results List of file paths matching the search pattern.
             */
            @Serializable
            data class Result(
                val currentWorkingDirectory: String = "",
                val error: String = "",
                val message: String = "",
                val results: List<String> = emptyList()
            ) : ToolResult {
                override fun toStringDefault(): String {
                    return buildString {
                        appendLine("Working directory: ${currentWorkingDirectory}")
                        if (error.isNotEmpty()) {
                            appendLine("Error: ${error}")
                            return@buildString
                        }
                        if (message.isNotEmpty()) {
                            appendLine(message)
                        }
                        if (results.isNotEmpty()) {
                            appendLine("Found files:")
                            results.forEach { path ->
                                appendLine("* $path")
                            }
                        } else {
                            appendLine("No matching files found")
                        }
                    }.trimEnd()
                }
            }

            override val argsSerializer: KSerializer<Args> = Args.serializer()

            override val descriptor: ToolDescriptor = ToolDescriptor(
                name = "FILETOOL_FIND_FILE",
                description = "Finds files or directories matching a wildcard pattern.",
                requiredParameters = listOf(
                    ToolParameterDescriptor(
                        name = "pattern",
                        description = "Glob pattern to search for.",
                        type = ToolParameterType.String
                    )
                ),
                optionalParameters = listOf(
                    ToolParameterDescriptor(
                        name = "depth",
                        description = "Max depth to search for files. Leave null for unlimited depth.",
                        type = ToolParameterType.Integer
                    ),
                    ToolParameterDescriptor(
                        name = "case_sensitive",
                        description = "Whether the search is case sensitive.",
                        type = ToolParameterType.Boolean
                    ),
                    ToolParameterDescriptor(
                        name = "include",
                        description = "Directories to include in the search.",
                        type = ToolParameterType.List(
                            itemsType = ToolParameterType.String,
                        )
                    ),
                    ToolParameterDescriptor(
                        name = "exclude",
                        description = "Directories to exclude from the search.",
                        type = ToolParameterType.List(
                            itemsType = ToolParameterType.String,
                        )
                    )
                ) + ComposioToolArgs.optionalParameters
            )
        }

        abstract class SearchWord : Tool<SearchWord.Args, SearchWord.Result>() {
            @Serializable
            data class Args(
                val word: String,
                val pattern: String? = null,
                val recursive: Boolean = true,
                @SerialName("case_insensitive")
                val caseInsensitive: Boolean = true,
                val exclude: List<String>? = null,
                override val thought: String? = null
            ) : ComposioToolArgs

            /**
             * Result of the [SearchWord] request.
             *
             * @property currentWorkingDirectory Current working directory of the file manager.
             * @property error Error message if any.
             * @property message An informational message regarding the search.
             * @property results A map where keys are file paths and values are lists of (line number, line content).
             */
            @Serializable
            data class Result(
                val currentWorkingDirectory: String = "",
                val error: String = "",
                val message: String = "",
                val results: Map<String, List<Pair<Int, String>>> = emptyMap()
            ) : ToolResult {
                override fun toStringDefault(): String {
                    return buildString {
                        appendLine("Working directory: ${currentWorkingDirectory}")
                        if (error.isNotEmpty()) {
                            appendLine("Error: ${error}")
                            return@buildString
                        }
                        if (message.isNotEmpty()) {
                            appendLine(message)
                        }
                        if (results.isNotEmpty()) {
                            appendLine("Found matches in files:")
                            results.forEach { (file, matches) ->
                                appendLine("\nFile: $file")
                                matches.forEach { (lineNumber, text) ->
                                    appendLine("Line $lineNumber: $text")
                                }
                            }
                        } else {
                            appendLine("No matches found")
                        }
                    }.trimEnd()
                }
            }

            override val argsSerializer: KSerializer<Args> = Args.serializer()

            override val descriptor: ToolDescriptor = ToolDescriptor(
                name = "FILETOOL_SEARCH_WORD",
                description = "Search for a specific word or phrase across files matching a pattern.",
                requiredParameters = listOf(
                    ToolParameterDescriptor(
                        name = "word",
                        description = "The word or phrase to search for.",
                        type = ToolParameterType.String
                    )
                ),
                optionalParameters = listOf(
                    ToolParameterDescriptor(
                        name = "pattern",
                        description = "Glob pattern for files to search in.",
                        type = ToolParameterType.String
                    ),
                    ToolParameterDescriptor(
                        name = "recursive",
                        description = "Whether to search recursively in subdirectories.",
                        type = ToolParameterType.Boolean
                    ),
                    ToolParameterDescriptor(
                        name = "case_insensitive",
                        description = "Whether the search is case insensitive.",
                        type = ToolParameterType.Boolean
                    ),
                    ToolParameterDescriptor(
                        name = "exclude",
                        description = "Directories to exclude from the search.",
                        type = ToolParameterType.List(
                            itemsType = ToolParameterType.String,
                        )
                    )
                ) + ComposioToolArgs.optionalParameters
            )
        }

        abstract class Write : Tool<Write.Args, Write.Result>() {
            @Serializable
            data class Args(
                @SerialName("file_path")
                val filePath: String? = null,
                val text: String,
                override val thought: String? = null
            ) : ComposioToolArgs

            /**
             * Result of the [Write] request.
             *
             * @property currentWorkingDirectory Current working directory of the file manager.
             * @property error Error message if any.
             */
            @Serializable
            data class Result(
                val currentWorkingDirectory: String = "",
                val error: String = ""
            ) : ToolResult {
                override fun toStringDefault(): String {
                    return buildString {
                        appendLine("Working directory: ${currentWorkingDirectory}")
                        if (error.isNotEmpty()) {
                            appendLine("Error: ${error}")
                        } else {
                            appendLine("File written successfully")
                        }
                    }.trimEnd()
                }
            }

            override val argsSerializer: KSerializer<Args> = Args.serializer()

            override val descriptor: ToolDescriptor = ToolDescriptor(
                name = "FILETOOL_WRITE",
                description = "Writes text to a file, replacing its entire content.",
                requiredParameters = listOf(
                    ToolParameterDescriptor(
                        name = "text",
                        description = "Text content to write.",
                        type = ToolParameterType.String
                    )
                ),
                optionalParameters = listOf(
                    ToolParameterDescriptor(
                        name = "file_path",
                        description = "Path of the file to write to. If omitted, defaults to the currently open file.",
                        type = ToolParameterType.String
                    )
                ) + ComposioToolArgs.optionalParameters
            )
        }

        abstract class GitPatch : Tool<GitPatch.Args, GitPatch.Result>() {
            @Serializable
            data class Args(
                @SerialName("new_file_paths")
                val newFilePaths: List<String> = emptyList(),
                override val thought: String? = null
            ) : ComposioToolArgs

            /**
             * Result of the [GitPatch] request.
             *
             * @property currentWorkingDirectory Current working directory of the file manager.
             * @property error Error message if any.
             * @property patch The generated Git patch as a string.
             */
            @Serializable
            data class Result(
                val currentWorkingDirectory: String = "",
                val error: String? = null,
                val patch: String = ""
            ) : ToolResult {
                override fun toStringDefault(): String {
                    return buildString {
                        appendLine("Working directory: ${currentWorkingDirectory}")
                        if (error != null) {
                            appendLine("Error: ${error}")
                            return@buildString
                        }
                        if (patch.isNotEmpty()) {
                            appendLine("Generated patch:")
                            appendLine("```diff")
                            appendLine(patch)
                            appendLine("```")
                        } else {
                            appendLine("No changes to create patch")
                        }
                    }.trimEnd()
                }
            }

            override val argsSerializer: KSerializer<Args> = Args.serializer()


            override val descriptor: ToolDescriptor = ToolDescriptor(
                name = "FILETOOL_GIT_PATCH",
                description = "Generates a Git patch that includes all changes in the current working directory.",
                requiredParameters = emptyList(),
                optionalParameters = listOf(
                    ToolParameterDescriptor(
                        name = "new_file_paths",
                        description = "Paths of the newly created files to be included in the patch. Provide an array of strings.",
                        type = ToolParameterType.List(
                            itemsType = ToolParameterType.String,
                        )
                    )
                ) + ComposioToolArgs.optionalParameters
            )
        }

    }
}

interface EditorManager<Path> {
    /**
     * @return lines of the file available in the current edit
     * */
    suspend fun openFile(path: Path, lineNumber: Int?): List<String>

    /**
     * Should keep track of the consecutive scrolls, and if called with the same ID multiple times -- raise an error that LLM run into some loop
     * */
    suspend fun scroll(direction: ScrollDirection, lines: Int, scrollId: Int = 0): List<String>

    suspend fun getCurrentlyOpenedFile(): Path?

    suspend fun getCurrentWorkingDir(): Path

    enum class ScrollDirection {
        UP, DOWN
    }
}

interface WordSearchProvider<Path> {

}
