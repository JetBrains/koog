package ai.koog.agents.file.tools.render

import ai.koog.agents.file.tools.model.FileSystemEntry
import ai.koog.prompt.text.TextContentBuilder
import ai.koog.rag.base.files.FileMetadata

private const val FOLDER_INDENTATION = "  "

private val CODE_EXTENSIONS = setOf(
    "kt", "java", "js", "ts", "py", "cpp", "c", "h", "hpp", "cs", "cxx", "cc",
    "go", "rs", "php", "rb", "swift", "scala", "sh", "bash", "sql", "r",
    "html", "css", "xml", "json", "yaml", "yml", "toml", "md", "dockerfile",
    "gradle", "properties", "conf", "ini", "cfg", "makefile", "cmake",
    "dart", "lua", "perl", "powershell", "ps1", "bat", "cmd", "vim"
)

private val LANGUAGE_ID_MAPPINGS = mapOf(
    "kt" to "kotlin", "js" to "javascript", "ts" to "typescript",
    "py" to "python", "sh" to "bash", "yml" to "yaml",
    "cpp" to "cpp", "cxx" to "cpp", "cc" to "cpp", "hpp" to "cpp",
    "cs" to "csharp", "ps1" to "powershell", "md" to "markdown"
)

public fun TextContentBuilder.entry(entry: FileSystemEntry, parent: FileSystemEntry? = null) {
    when (entry) {
        is FileSystemEntry.File -> file(entry, parent)
        is FileSystemEntry.Folder -> folder(entry, parent)
    }
}

public fun TextContentBuilder.folder(folder: FileSystemEntry.Folder, parent: FileSystemEntry? = null) {
    folder.entries?.singleOrNull()?.let { singleEntry ->
        entry(singleEntry, parent)
        return
    }

    val displayPath = calculateDisplayPath(folder.path, folder.name, parent)
    val metadataSuffix = if (folder.hidden) " (hidden)" else ""

    +"$displayPath/$metadataSuffix"

    renderFolderEntries(folder)
}

public fun TextContentBuilder.file(file: FileSystemEntry.File, parent: FileSystemEntry? = null) {
    val displayPath = calculateDisplayPath(file.path, file.name, parent)
    val metadata = buildFileMetadata(file)

    +"$displayPath (${metadata.joinToString(", ")})"

    renderFileContent(file.content, file.extension)
}

private fun calculateDisplayPath(path: String, name: String, parent: FileSystemEntry?): String {
    return when (parent) {
        null -> path
        else -> {
            val relativePath = path.removePrefix(parent.path).trimStart('/', '\\')
            relativePath.ifEmpty { name }
        }
    }
}

private fun buildFileMetadata(file: FileSystemEntry.File): List<String> = buildList {
    if (file.contentType != FileMetadata.FileContentType.Text) {
        add(file.contentType.display)
    }
    add(file.size.joinToString(", ") { it.display() })
    if (file.hidden) {
        add("hidden")
    }
}

private fun TextContentBuilder.renderFolderEntries(folder: FileSystemEntry.Folder) {
    val entries = folder.entries
    if (entries.isNullOrEmpty()) return

    padding(FOLDER_INDENTATION) {
        entries.forEach { entry(it, folder) }
    }
}

private fun TextContentBuilder.renderFileContent(content: FileSystemEntry.File.Content, extension: String?) {
    when (content) {
        is FileSystemEntry.File.Content.Excerpt -> renderExcerptContent(content, extension)
        is FileSystemEntry.File.Content.Text -> renderTextContent(content, extension)
        FileSystemEntry.File.Content.None -> { /* No content to render */ }
    }
}

private fun TextContentBuilder.renderExcerptContent(
    content: FileSystemEntry.File.Content.Excerpt,
    extension: String?
) {
    if (content.snippets.isEmpty()) {
        +"(No excerpt)"
        return
    }

    +"Excerpt:"
    content.snippets.forEach { snippet ->
        +"Lines ${snippet.range.start.line}-${snippet.range.end.line}:"
        codeBlock(snippet.text.trim(), extension)
    }
}

private fun TextContentBuilder.renderTextContent(
    content: FileSystemEntry.File.Content.Text,
    extension: String?
) {
    +"Content:"
    codeBlock(content.text.trim(), extension)
}

private fun TextContentBuilder.codeBlock(text: String, extension: String? = null) {
    when {
        extension?.lowercase() in CODE_EXTENSIONS -> {
            +"```${extension.toLanguageId()}"
            +text
            +"```"
        }
        else -> +text
    }
}

private fun String?.toLanguageId(): String {
    val lowercase = this?.lowercase() ?: return ""
    return LANGUAGE_ID_MAPPINGS[lowercase] ?: lowercase
}
