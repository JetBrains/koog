package ai.koog.agents.ext.tool.edit

import ai.koog.agents.core.tools.*
import ai.koog.agents.ext.tool.edit.diff.Diff
import ai.koog.agents.ext.tool.edit.diff.MyersDiffAlgorithm
import ai.koog.agents.ext.tool.edit.diff.diff
import ai.koog.agents.ext.tool.edit.diff.toUnifiedDiff
import ai.koog.agents.ext.tool.edit.patch.FilePatch
import ai.koog.agents.ext.tool.edit.patch.PatchApplyResult
import ai.koog.agents.ext.tool.edit.patch.applyTokenNormalizedPatch
import ai.koog.agents.ext.tool.edit.patch.isSuccess
import ai.koog.prompt.markdown.markdown
import ai.koog.rag.base.files.FileSystemProvider
import ai.koog.rag.base.files.readText
import ai.koog.rag.base.files.writeText
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

internal class EditFileTool<Path>(
    private val fs: FileSystemProvider.ReadWrite<Path>
) : Tool<EditFileTool.Args, EditFileTool.Result>() {

    @Serializable
    data class Args(
        val path: String,
        val original: String,
        val replacement: String
    ) : ToolArgs

    @Serializable
    data class Result(
        val diff: Diff<String>,
        private val patchApplyResult: PatchApplyResult,
        val path: String
    ) : ToolResult.JSONSerializable<Result> {
        @Serializable
        val applied: Boolean = patchApplyResult.isSuccess()

        override fun getSerializer(): KSerializer<Result> = serializer()

        fun toMarkdown(): String = markdown {
            if (patchApplyResult.isSuccess()) {
                line {
                    bold("Successfully").text(" edited file (").code(path).text(")")
                }
                codeblock(diff.toUnifiedDiff())
            } else {
                line {
                    text("File (").code(path).text(") was ")
                        .bold("not")
                        .text(" modified")
                }
                patchApplyResult.reason.let { reason ->
                    line { text("Reason: ").code(reason) }
                }
                codeblock(diff.toUnifiedDiff())
            }
        }

        override fun toStringDefault(): String = toMarkdown()
        override fun toString(): String = toStringDefault()
    }

    companion object {
        private val logger = KotlinLogging.logger {  }

        val descriptor: ToolDescriptor = ToolDescriptor(
            name = "edit_file",
            description = markdown {
                +"Makes an edit to a target file by applying a single text replacement patch."
                newline()

                +"This tool performs targeted file modifications by replacing specific text segments with new content. "
                +"It can handle partial edits, complete file rewrites, or new file creation. "
                +"The tool uses string matching to locate the original text and replaces it with the provided replacement text."
                newline()

                +"The tool should be used when you need to modify existing files, create new files, or make precise text replacements. "
                +"It handles file operations safely by creating parent directories automatically if they don't exist."
                newline()

                h3("Key Requirements")
                bulleted {
                    item("The 'original' text must match text in the file, whitespaces and line endings will be fuzzy matched")
                    item("Only ONE replacement per tool call - for multiple changes, call the tool multiple times")
                    item("Use empty string (\"\") for 'original' when creating new files or performing complete rewrites")
                    item("The 'original' text must be kept to absolute minimum to ensure effective editing")
                }
                newline()

                h3("This tool does NOT")
                bulleted {
                    item("Perform fuzzy or approximate text matching other than whitespaces")
                    item("Handle multiple replacements in a single call")
                    item("Automatically fix formatting or indentation mismatches")
                    item("Provide file content reading capabilities (use read_file for that)")
                }
                newline()

                h3("Common use cases")
                bulleted {
                    item("Modifying specific functions or code blocks in existing files")
                    item("Adding new imports, methods, or classes to source files")
                    item("Creating new configuration files with complete content")
                    item("Updating documentation or README files")
                    item("Fixing bugs by replacing problematic code segments")
                }
            },
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "path",
                    description = markdown {
                        +"The absolute path to the target file that will be modified or created."
                        newline()

                        +"Must be a valid file system path. If the file doesn't exist, it will be created along with any necessary parent directories. "
                        +"If the file exists, it will be modified according to the patch operation."
                        newline()

                        line {
                            bold("Examples:")
                            space()
                            code("/home/user/project/src/main.kt")
                            text(", ")
                            code("C:\\Users\\user\\project\\main.py")
                            text(", ")
                            code("./config/settings.json")
                        }
                    },
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "original",
                    description = markdown {
                        +"The exact text block that will be located and replaced in the target file."
                        newline()

                        h4("For existing file modifications")
                        bulleted {
                            item("Must match the target text character-for-character")
                            item("Include sufficient surrounding context to ensure unique matching")
                            item("Include complete lines with proper line endings")
                            item("Be concise and minimal to execute specific replacement")
                        }
                        newline()

                        h4("For new files or complete rewrites")
                        bulleted {
                            item {
                                text("Use empty string: ")
                                code("\"\"")
                            }
                        }
                    },
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "replacement",
                    description = markdown {
                        +"The new text content that will replace the original text block."
                        newline()

                        h4("Formatting best practices")
                        bulleted {
                            item("Match the indentation style of surrounding code (tabs vs spaces)")
                            item("Use consistent line endings with the rest of the file")
                            item("Follow the project's coding style and conventions")
                            item("Ensure proper syntax for the target file type")
                        }
                        newline()

                        h4("For new file creation")
                        bulleted {
                            item("Include all necessary file headers, imports, or boilerplate")
                            item("Use appropriate file structure for the target language or format")
                            item("Ensure complete and valid content from start to finish")
                        }
                        newline()

                        h4("For code modifications")
                        bulleted {
                            item("Maintain proper syntax and semantic correctness")
                            item("Include necessary imports if adding new dependencies")
                            item("Preserve existing code structure and patterns")
                        }
                    },
                    type = ToolParameterType.String
                )
            )
        )
    }

    override val argsSerializer: KSerializer<Args> = Args.serializer()

    override val descriptor: ToolDescriptor = EditFileTool.descriptor

    override suspend fun execute(args: Args): Result {
        val path = fs.fromAbsolutePathString(args.path)
        val content = if (fs.exists(path)) fs.readText(path) else ""

        val patch = FilePatch(args.original, args.replacement)
        val patchApplyResult = applyTokenNormalizedPatch(content, patch)

        if (patchApplyResult.isSuccess()) {
            fs.writeText(path, patchApplyResult.updatedContent)

            val diff = MyersDiffAlgorithm.forStrings.diff(content, patchApplyResult.updatedContent)
            logger.info { "Patch was applied with diff: $diff" }

            return Result(diff, patchApplyResult, args.path)
        } else {
            logger.info { "Patch was NOT applied because of ${patchApplyResult.reason}" }
            return Result(MyersDiffAlgorithm.forStrings.diff(content, content), patchApplyResult, args.path)
        }
    }
}
