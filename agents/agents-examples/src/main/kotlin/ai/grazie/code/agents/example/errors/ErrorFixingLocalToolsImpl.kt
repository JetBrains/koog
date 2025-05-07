package ai.grazie.code.agents.example.errors

import ai.grazie.code.agents.core.tools.ToolResult
import ai.grazie.code.agents.example.normalize
import ai.grazie.code.agents.tools.registry.tools.ErrorFixingTools
import ai.grazie.code.files.jvm.JVMDocumentProvider
import ai.grazie.code.files.model.DocumentProvider.DocumentRange
import ai.grazie.code.files.model.DocumentProvider.Position
import ai.grazie.code.prompt.structure.json.LLMDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText

class SearchFileErrorFixingToolImpl(val file: Path) : ErrorFixingTools.SearchFileTool() {
    override suspend fun doExecute(args: Args): String {
        return if (args.path == file.name) {
            "Content: `${JVMDocumentProvider.text(file)}`"
        } else {
            "File ${args.path} doesn't exist"
        }
    }
}

class SearchObjectErrorFixingToolImpl(val code: String) : ErrorFixingTools.SearchObjectTool() {
    override suspend fun doExecute(args: Args): String {
        return if (args.type == "function" && args.name == "main") {
            "start_line: ${code.lines().indexOfFirst { it.contains("main") } + 1}, " +
                    "end_line: ${code.lines().size}, file: Main.kt"
        } else {
            "${args.type} ${args.name} does not exist"
        }
    }
}

class EditErrorFixingToolImpl(val file: Path) : ErrorFixingTools.EditTool() {
    override suspend fun doExecute(args: Args): String {
        val startLine = args.startLineInclusive - 1
        val endLine = args.endLineExclusive - 1
        val newContent = args.newContent.normalize()
        val startPosition = Position(startLine, 0)
        val endPosition = Position(endLine, 0)
        val range = DocumentRange(startPosition, endPosition)
        JVMDocumentProvider.Edit.setText(file, newContent, range)
        return "New content: `${JVMDocumentProvider.text(file)}`"
    }
}

class RunTestErrorFixingToolImpl(val file: Path, val regex: Regex) : ErrorFixingTools.RunTestTool() {
    override suspend fun doExecute(args: Args): String {
        val content = JVMDocumentProvider.text(file)
        return if (regex.containsMatchIn(content)) "Passed!" else "Failed!"
    }
}

class SearchReplaceToolImpl(val testFile: Path) : ErrorFixingTools.SearchReplaceTool() {
    override suspend fun doExecute(args: Args): String {
        try {
            val content = testFile.readText()
            val newContent = content.replace(args.searchContent, args.replaceContent)

            if (content == newContent) {
                return "No changes made. Could not find the search content in the file."
            }

            testFile.writeText(newContent)
            return "Successfully edited ${testFile.fileName}"
        } catch (e: Exception) {
            return "Failed to edit file: ${e.message}"
        }
    }
}

@Serializable
@SerialName("CritiqueRsult")
@LLMDescription("LLM as a judge feedback on the agent result.")
data class CritiqueResultWithMessage(
    @LLMDescription("Critique result: true if results is correct and meets all requirements, false if agent should continue improve result.")
    val result: Boolean,
    @LLMDescription("Critique messages: string with comment on critique result.")
    val message: String
) : ToolResult {
    override fun toStringDefault(): String {
        return Json.encodeToString(serializer(), this)
    }
}
