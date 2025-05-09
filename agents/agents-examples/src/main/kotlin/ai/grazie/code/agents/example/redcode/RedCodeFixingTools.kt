package ai.grazie.code.agents.example.redcode

import ai.grazie.code.agents.core.tools.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object RedCodeFixingTools {
    object ImportFixingTools {
        abstract class ListImportsInFileTool : Tool<ListImportsInFileTool.Args, ListImportsInFileTool.Result>() {
            @Serializable
            data class Args(
                @SerialName("file_path")
                val filePath: String
            ) : Tool.Args

            @Serializable
            data class Result(val imports: List<String>) : ToolResult.JSONSerializable<Result> {
                override fun getSerializer(): KSerializer<Result> = serializer()
            }

            final override val argsSerializer = Args.serializer()

            final override val descriptor: ToolDescriptor = ToolDescriptor(
                name = "list_imports_in_file",
                description = """
                    Lists all imports in the current file.
                """.trimIndent(),
                requiredParameters = listOf(
                    ToolParameterDescriptor(
                        name = "file_path",
                        description = "A path to the file to analyze.",
                        type = ToolParameterType.String
                    )
                )
            )
        }

        abstract class AddImportsToFileTool : Tool<AddImportsToFileTool.Args, AddImportsToFileTool.Result>() {
            @Serializable
            data class Args(
                @SerialName("file_path")
                val filePath: String,
                @SerialName("imports_to_add")
                val importsToAdd: List<String>
            ) : Tool.Args

            @Serializable
            data class Result(
                @SerialName("new_imports_in_file")
                val newImportsInFile: List<String>
            ) : ToolResult.JSONSerializable<Result> {
                override fun getSerializer(): KSerializer<Result> = serializer()
            }


            final override val argsSerializer = Args.serializer()

            final override val descriptor: ToolDescriptor = ToolDescriptor(
                name = "add_imports_to_file",
                description = """
                    Adds provided new imports to the imports in the current file. 
                """.trimIndent(),
                requiredParameters = listOf(
                    ToolParameterDescriptor(
                        name = "imports_to_add",
                        description = "List of imports to add to the file.",
                        type = ToolParameterType.List(ToolParameterType.String)
                    ),
                    ToolParameterDescriptor(
                        name = "file_path",
                        description = "A path to the file with code.",
                        type = ToolParameterType.String
                    )
                )
            )
        }

        abstract class RemoveImportFromFileTool :
            Tool<RemoveImportFromFileTool.Args, RemoveImportFromFileTool.Result>() {
            @Serializable
            data class Args(
                @SerialName("file_path")
                val filePath: String,
                @SerialName("import_to_remove")
                val importToRemove: String
            ) : Tool.Args

            @Serializable
            data class Result(
                @SerialName("new_imports_in_file")
                val newImportsInFile: List<String>
            ) : ToolResult.JSONSerializable<Result> {
                override fun getSerializer(): KSerializer<Result> = serializer()
            }


            final override val argsSerializer = Args.serializer()

            final override val descriptor: ToolDescriptor = ToolDescriptor(
                name = "remove_import_from_file",
                description = """
                    Removes the given import from the file with code. 
                """.trimIndent(),
                requiredParameters = listOf(
                    ToolParameterDescriptor(
                        name = "import_to_remove",
                        description = "Import that must be removed from the file.",
                        type = ToolParameterType.String
                    ),
                    ToolParameterDescriptor(
                        name = "file_path",
                        description = "A path to the file with code.",
                        type = ToolParameterType.String
                    )
                )
            )
        }
    }

    object DependencyFixingTools {
        abstract class ListDependenciesInModuleTool :
            Tool<ListDependenciesInModuleTool.Args, ListDependenciesInModuleTool.Result>() {
            @Serializable
            data class Args(
                @SerialName("module_root_path")
                val moduleRootPath: String
            ) : Tool.Args

            @Serializable
            data class Result(
                @SerialName("dependencies")
                val dependencies: List<String>
            ) : ToolResult.JSONSerializable<Result> {
                override fun getSerializer(): KSerializer<Result> = serializer()
            }

            final override val argsSerializer = Args.serializer()

            final override val descriptor: ToolDescriptor = ToolDescriptor(
                name = "list_dependencies_of_module",
                description = """
                    Lists all dependencies defined in the build script of the current module.
                """.trimIndent(),
                requiredParameters = listOf(
                    ToolParameterDescriptor(
                        name = "module_root_path",
                        description = "The root path of the module to analyze.",
                        type = ToolParameterType.String
                    )
                )
            )
        }

        abstract class AddDependenciesToModuleTool :
            Tool<AddDependenciesToModuleTool.Args, AddDependenciesToModuleTool.Result>() {
            @Serializable
            data class Args(
                @SerialName("module_root_path")
                val moduleRootPath: String,
                @SerialName("dependencies_to_add")
                val dependenciesToAdd: List<String>
            ) : Tool.Args

            @Serializable
            data class Result(
                @SerialName("new_all_dependencies_in_module")
                val newAllDependenciesInModule: List<String>
            ) : ToolResult.JSONSerializable<Result> {
                override fun getSerializer(): KSerializer<Result> = serializer()
            }

            final override val argsSerializer = Args.serializer()

            final override val descriptor: ToolDescriptor = ToolDescriptor(
                name = "add_dependencies_to_module",
                description = """
                    Adds provided new dependencies to the given module. 
                """.trimIndent(),
                requiredParameters = listOf(
                    ToolParameterDescriptor(
                        name = "dependencies_to_add",
                        description = "List of dependencies to add to the given module.",
                        type = ToolParameterType.List(ToolParameterType.String)
                    ),
                    ToolParameterDescriptor(
                        name = "module_root_path",
                        description = "The root path of the module to analyze.",
                        type = ToolParameterType.String
                    )
                )
            )
        }

        abstract class RemoveDependencyFromModuleTool :
            Tool<RemoveDependencyFromModuleTool.Args, RemoveDependencyFromModuleTool.Result>() {
            @Serializable
            data class Args(
                @SerialName("module_root_path")
                val moduleRootPath: String,
                @SerialName("dependency_to_remove")
                val dependencyToRemove: String
            ) : Tool.Args

            @Serializable
            data class Result(
                @SerialName("new_imports_in_file")
                val newImportsInFile: List<String>
            ) : ToolResult.JSONSerializable<Result> {
                override fun getSerializer(): KSerializer<Result> = serializer()
            }

            final override val argsSerializer = Args.serializer()

            final override val descriptor: ToolDescriptor = ToolDescriptor(
                name = "remove_dependency_from_module",
                description = """
                    Removes the given dependency from the module. 
                """.trimIndent(),
                requiredParameters = listOf(
                    ToolParameterDescriptor(
                        name = "dependency_to_remove",
                        description = "Dependency that must be removed from the given module.",
                        type = ToolParameterType.String
                    ),
                    ToolParameterDescriptor(
                        name = "module_root_path",
                        description = "The root path of the module to analyze.",
                        type = ToolParameterType.String
                    )
                )
            )
        }
    }

    abstract class ListFilesWithErrorsTool : Tool<Tool.EmptyArgs, ListFilesWithErrorsTool.Result>() {

        @Serializable
        data class Result(
            @SerialName("module_errors")
            val moduleErrors: List<ModuleError>,
            @SerialName("total_number_of_errors_in_project")
            val totalNumberOfErrorsInProject: Int
        ) : ToolResult.JSONSerializable<Result> {
            override fun getSerializer(): KSerializer<Result> = serializer()

            @Serializable
            data class ModuleError(
                @SerialName("module_root_path")
                val moduleRootPath: String,
                @SerialName("file_errors")
                val fileErrors: List<FileError>,
                @SerialName("total_number_of_errors_in_module")
                val totalNumberOfErrorsInModule: Int
            )

            @Serializable
            data class FileError(
                @SerialName("file_path")
                val filePath: String,
                @SerialName("number_of_errors_in_file")
                val numberOfErrorsInFile: Int
            )
        }

        final override val argsSerializer = EmptyArgs.serializer()

        final override val descriptor: ToolDescriptor = ToolDescriptor(
            name = "list_files_with_errors",
            description = """
            Scans all files within the specified root directory (including subdirectories) and returns a summary of errors 
            grouped by module and file. The result includes the root path of each module, the number of errors in each file, 
            and a total error count for the project.

            Example JSON format of the output:
            {
                "module_errors": [
                    {
                        "module_root_path": "/projects/my-app/module-a",
                        "file_errors": [
                            {
                                "file_path": "/projects/my-app/module-a/src/Main.kt",
                                "number_of_errors_in_file": 2
                            },
                            {
                                "file_path": "/projects/my-app/module-a/src/Utils.kt",
                                "number_of_errors_in_file": 1
                            }
                        ],
                        "total_number_of_errors_in_module": 3
                    },
                    {
                        "module_root_path": "/projects/my-app/module-b",
                        "file_errors": [
                            {
                                "file_path": "/projects/my-app/module-b/src/Example.kt",
                                "number_of_errors_in_file": 1
                            }
                        ],
                        "total_number_of_errors_in_module": 1
                    }
                ],
                "total_number_of_errors_in_project": 4
            }
            """.trimIndent(),
            requiredParameters = emptyList()
        )
    }

    abstract class FindErrorsInFileTool : Tool<FindErrorsInFileTool.Args, FindErrorsInFileTool.Result>() {
        @Serializable
        data class Args(
            @SerialName("file_path")
            val filePath: String
        ) : Tool.Args

        @Serializable
        data class Result(
            @SerialName("file_path")
            val filePath: String,
            @SerialName("total_number_of_errors")
            val totalNumberOfErrors: Int,
            @SerialName("errors")
            val errors: List<ErrorDetail>,
            @SerialName("build_errors_in_module")
            val buildErrorsInModule: List<BuildErrorDetail>?
        ) : ToolResult.JSONSerializable<Result> {
            override fun getSerializer(): KSerializer<Result> = serializer()

            @Serializable
            data class ErrorDetail(
                @SerialName("line")
                val line: Int,
                @SerialName("error_message")
                val errorMessage: String,
                @SerialName("affected_code")
                val affectedCode: String
            )

            @Serializable
            data class BuildErrorDetail(
                @SerialName("error_description")
                val errorDescription: String
            )
        }

        final override val argsSerializer = Args.serializer()

        final override val descriptor: ToolDescriptor = ToolDescriptor(
            name = "find_errors_in_file",
            description = """
            Analyzes the given file and identifies lines with errors, providing detailed error messages for each error, 
            along with small code snippets surrounding the error lines. Additionally, captures build-related errors
            for the module.

            Example JSON format of the output:
            {
                "file_path": "/projects/my-app/module-a/src/Main.kt",
                "total_number_of_errors": 2,
                "errors": [
                    {
                        "line": 15,
                        "error_message": "Syntax error: unexpected token aaaaaaaaaaaa",
                        "affected_code": "13: val x = 10\n14: val y = {\n15:     aaaaaaaaaaaa\n16: }\n17: println(x)"
                    },
                    {
                        "line": 42,
                        "error_message": "Variable 'x' might not have been initialized",
                        "affected_code": "40: fun example() {\n41:     var x: Int\n42:     println(x)\n43: }\n44: example()"
                    }
                ],
                "build_errors_in_module": [
                    {
                        "error_description": "* What went wrong:\nCould not determine the dependencies of task ':shadowJar'."
                    }
                ]
            }
            """.trimIndent(),
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "file_path",
                    description = "The path to the file to analyze for errors.",
                    type = ToolParameterType.String
                )
            )
        )
    }

    abstract class DetermineModuleRootsTool : Tool<DetermineModuleRootsTool.Args, DetermineModuleRootsTool.Result>() {
        @Serializable
        data class Args(
            @SerialName("current_file_path")
            val currentFilePath: String
        ) : Tool.Args

        @Serializable
        data class Result(
            @SerialName("current_module_root_path")
            val currentModuleRootPath: String,
            @SerialName("parent_module_roots")
            val parentModuleRoots: List<String>
        ) : ToolResult.JSONSerializable<Result> {
            override fun getSerializer(): KSerializer<Result> = serializer()
        }

        final override val argsSerializer = Args.serializer()

        final override val descriptor: ToolDescriptor = ToolDescriptor(
            name = "determine_module_roots",
            description = """
            Finds the root path of the module where the given file is located. 
            Additionally, this tool retrieves a list of parent module roots if the module is nested, 
            or an empty list if the current module is not a sub-module of any other module.

            Example JSON format of the output:
            {
                "current_module_root_path": "/projects/my-app/module-a",
                "parent_module_roots": ["/projects/my-app", "/projects"]
            }
            """.trimIndent(),
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "current_file_path",
                    description = "The absolute or relative file path used to locate the module root.",
                    type = ToolParameterType.String
                )
            )
        )
    }

    abstract class FindBuildScriptTool : Tool<FindBuildScriptTool.Args, FindBuildScriptTool.Result>() {
        @Serializable
        data class Args(
            @SerialName("current_module_root")
            val currentModuleRoot: String
        ) : Tool.Args

        @Serializable
        data class Result(
            @SerialName("build_script_location")
            val buildScriptLocation: String?
        ): ToolResult.JSONSerializable<Result> {
            override fun getSerializer(): KSerializer<Result> = serializer()
        }

        final override val argsSerializer = Args.serializer()

        final override val descriptor: ToolDescriptor = ToolDescriptor(
            name = "find_build_script",
            description = """
            Locates the build script file within the root of the specified module. 

            If the build script is not found, the result will indicate a null location.

            Example JSON format of the output:
            {
                "build_script_location": "/projects/my-app/module-a/build.gradle.kts"
            }
            """.trimIndent(),
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "current_module_root",
                    description = "The root path of the module in which to search for the build script.",
                    type = ToolParameterType.String
                )
            )
        )
    }

    abstract class ReadFileTextTool : Tool<ReadFileTextTool.Args, ReadFileTextTool.Result>() {
        @Serializable
        data class Args(
            @SerialName("file_path")
            val filePath: String,
            @SerialName("from_line")
            val fromLine: Int
        ) : Tool.Args

        @Serializable
        data class Result(
            @SerialName("file_content")
            val fileContent: String,
            @SerialName("is_partial_file_content")
            val isPartialFileContent: Boolean
        ): ToolResult.JSONSerializable<Result> {
            override fun getSerializer(): KSerializer<Result> = serializer()
        }

        final override val argsSerializer = Args.serializer()

        final override val descriptor: ToolDescriptor = ToolDescriptor(
            name = "read_file_text",
            description = """
            Reads the content of the specified file as plain text starting from the `fromLine` parameter. The tool retrieves up to 100 lines of code at a time.

            If the file contains more than 100 lines beyond `fromLine`, you can call this tool again with an updated `fromLine` parameter to read additional lines. 
            The result will indicate whether only a partial content of the file was returned using "isPartialFileContent": true.

            In case the file cannot be read due to a missing file, inaccessible path, or any other issues, an error will be returned.

            Example JSON format of the output:
            {
                "file_content": "1: package com.example\n2: val x = 10\n3: val y = {\n4:     println(x)\n5: }",
                "is_partial_file_content": false
            }
            """.trimIndent(),
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "file_path",
                    description = "The absolute or relative path to the file to read as text.",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "from_line",
                    description = "The line number from which the tool should start reading the file content. Line numbers start from 1.",
                    type = ToolParameterType.Integer
                )
            )
        )
    }

    abstract class EditFileTextTool : Tool<EditFileTextTool.Args, EditFileTextTool.Result>() {
        @Serializable
        data class Args(
            @SerialName("file_path")
            val filePath: String,
            @SerialName("from_line")
            val fromLine: Int,
            @SerialName("until_line")
            val untilLine: Int,
            @SerialName("new_text")
            val newText: String
        ) : Tool.Args

        @Serializable
        data class Result(
            @SerialName("success")
            val success: Boolean,
            @SerialName("old_text")
            val oldText: String?,
            @SerialName("new_text")
            val newText: String?,
            @SerialName("error_message")
            val errorMessage: String?
        ) : ToolResult.JSONSerializable<Result> {
            override fun getSerializer(): KSerializer<Result> = serializer()
        }

        final override val argsSerializer = Args.serializer()

        final override val descriptor: ToolDescriptor = ToolDescriptor(
            name = "replace_file_text",
            description = """
            Replaces the content of the specified file between the `from_line` and `until_line` parameters with the `new_text`. 

            This tool can modify up to 100 lines of code (from `from_line` to `until_line` inclusive). In case the line range exceeds the limit,
            you must call the tool multiple times with updated line ranges.

            If the file cannot be updated (e.g., due to a missing file, invalid path, or insufficient permission), the result will indicate a failure.
            """.trimIndent(),
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "file_path",
                    description = "The absolute or relative path to the file to edit.",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "from_line",
                    description = "The starting line number for the edit (inclusive). Line numbers start from 1.",
                    type = ToolParameterType.Integer
                ),
                ToolParameterDescriptor(
                    name = "until_line",
                    description = "The ending line number for the edit (inclusive).",
                    type = ToolParameterType.Integer
                ),
                ToolParameterDescriptor(
                    name = "new_text",
                    description = "The new text content to replace the specified line range.",
                    type = ToolParameterType.String
                )
            )
        )
    }

    abstract class InsertIntoFileTool : Tool<InsertIntoFileTool.Args, InsertIntoFileTool.Result>() {
        @Serializable
        data class Args(
            @SerialName("file_path")
            val filePath: String,
            @SerialName("line_number")
            val lineNumber: Int,
            @SerialName("relative_position")
            val relativePosition: Position,
            @SerialName("text_to_insert")
            val textToInsert: String
        ) : Tool.Args

        @Serializable
        data class Result(
            @SerialName("success")
            val success: Boolean,
            @SerialName("old_text")
            val oldText: String?,
            @SerialName("new_text")
            val newText: String?,
            @SerialName("error_message")
            val errorMessage: String?
        ) : ToolResult.JSONSerializable<Result> {
            override fun getSerializer(): KSerializer<Result> = serializer()
        }

        @Serializable
        enum class Position {
            BEFORE, AFTER
        }

        final override val argsSerializer = Args.serializer()

        final override val descriptor: ToolDescriptor = ToolDescriptor(
            name = "insert_into_file",
            description = """
            Inserts the specified `textToInsert` into the file either BEFORE or AFTER the specified `line_number`.

            The tool does not modify the existing text but adds the new text at the given position relative to the specified line.

            If the file cannot be updated (e.g., due to a missing file, invalid path, or insufficient permission), the result will indicate a failure.
            """.trimIndent(),
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "file_path",
                    description = "The absolute or relative path to the file to edit.",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "line_number",
                    description = "The line number where the new text should be inserted. Line numbers start from 1.",
                    type = ToolParameterType.Integer
                ),
                ToolParameterDescriptor(
                    name = "relative_position",
                    description = "Specifies whether to insert the new text BEFORE or AFTER the specified line. Can be: BEFORE or AFTER.",
                    type = ToolParameterType.Enum(Position.entries)
                ),
                ToolParameterDescriptor(
                    name = "text_to_insert",
                    description = "The new text content to insert.",
                    type = ToolParameterType.String
                )
            )
        )
    }

    abstract class SearchWordProjectTool : Tool<SearchWordProjectTool.Args, SearchWordProjectTool.Result>() {
        @Serializable
        data class Args(
            @SerialName("word")
            val word: String
        ) : Tool.Args

        @Serializable
        data class Result(
            @SerialName("occurrences")
            val occurrences: List<Occurrence>,
            @SerialName("total_occurrences")
            val totalOccurrences: Int
        ): ToolResult.JSONSerializable<Result> {
            override fun getSerializer(): KSerializer<Result> = serializer()

            @Serializable
            data class Occurrence(
                @SerialName("file_path")
                val filePath: String,
                @SerialName("module_path")
                val modulePath: String,
                @SerialName("line_number")
                val lineNumber: Int,
                @SerialName("line_text")
                val lineText: String
            )
        }

        final override val argsSerializer = Args.serializer()

        final override val descriptor: ToolDescriptor = ToolDescriptor(
            name = "search_word_project",
            description = """
            Searches for a given word in all files of the project and returns up to the first 100 occurrences along with:
            - File path
            - Module path
            - Line number
            - Line text

            The search is case-sensitive. If the word is not found or files cannot be accessed, the result will indicate
            no occurrences.

            Example JSON format of the output:
            {
                "occurrences": [
                    {
                        "file_path": "/projects/my-app/module-a/src/Main.kt",
                        "module_path": "/projects/my-app/module-a",
                        "line_number": 5,
                        "line_text": "val word = \"example\""
                    },
                    {
                        "file_path": "/projects/my-app/module-b/src/Utils.kt",
                        "module_path": "/projects/my-app/module-b",
                        "line_number": 15,
                        "line_text": "println(word)"
                    }
                ],
                "total_occurrences": 2
            }
            """.trimIndent(),
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "word",
                    description = "The word to search for in the project. The search is case-sensitive.",
                    type = ToolParameterType.String
                )
            )
        )
    }
}
