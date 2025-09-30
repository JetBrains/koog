package ai.koog.agents.ext.tool.file.render

import ai.koog.agents.ext.tool.file.model.FileSystemEntry
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
    "cs" to "csharp", "ps1" to "powershell", "md" to "markdown",
    "rb" to "ruby", "dockerfile" to "docker", "gradle" to "groovy",
    "bat" to "batch", "cmd" to "batch"
)

/**
 * Renders a generic [FileSystemEntry] by delegating to [file] or [folder].
 *
 * @param entry the entry to render
 * @param parent optional parent entry used to compute a relative display path
 */
internal fun TextContentBuilder.entry(entry: FileSystemEntry, parent: FileSystemEntry? = null) {
    when (entry) {
        is FileSystemEntry.File -> file(entry, parent)
        is FileSystemEntry.Folder -> folder(entry, parent)
    }
}

/**
 * Renders a folder as a single line with an optional "(hidden)" suffix, followed by its nested entries with indentation.
 *
 * Special behavior for single-entry folders: If the folder contains exactly one entry, that entry is rendered
 * directly instead of the folder itself to avoid unnecessary nesting levels in the output.
 *
 * The folder line shows the path ending with "/" and "(hidden)" if the folder is hidden.
 * Child entries are rendered with 2-space indentation, recursively maintaining the folder hierarchy.
 * Folders with a null or empty entries list show only the folder line with no children.
 *
 * @param folder the folder to render
 * @param parent optional parent entry used to compute a relative display path
 */
internal fun TextContentBuilder.folder(folder: FileSystemEntry.Folder, parent: FileSystemEntry? = null) {
    folder.entries?.singleOrNull()?.let { singleEntry ->
        entry(singleEntry, parent)
        return
    }

    val displayPath = calculateDisplayPath(folder.path, folder.name, parent)
    val metadataSuffix = if (folder.hidden) " (hidden)" else ""

    +"$displayPath/$metadataSuffix"

    renderFolderEntries(folder)
}

/**
 * Renders a file as a single line with metadata, followed by its content if present.
 *
 * The metadata line shows the file path and a parenthesized list containing:
 * - Content type (only for non-text files like "binary")
 * - File size(s) (e.g., "1.5 MiB", "12 lines")
 * - "hidden" flag if the file is hidden
 *
 * When file content is available, it's rendered below the metadata line:
 * - Text content becomes a Markdown code block if the file extension is recognized
 * - Excerpt content shows line ranges and creates code blocks for each snippet
 * - All content text is trimmed of leading/trailing whitespace
 *
 * @param file the file to render
 * @param parent optional parent entry used to compute a relative display path
 */
internal fun TextContentBuilder.file(file: FileSystemEntry.File, parent: FileSystemEntry? = null) {
    val displayPath = calculateDisplayPath(file.path, file.name, parent)
    val metadata = buildFileMetadata(file)

    +"$displayPath (${metadata.joinToString(", ")})"

    renderFileContent(file.content, file.extension)
}

internal fun String.norm(): String = replace('\\', '/')

private fun calculateDisplayPath(path: String, name: String, parent: FileSystemEntry?): String {
    return when (parent) {
        null -> path.norm()
        else -> {
            val rel = path.removePrefix(parent.path)
                .trimStart('/', '\\')
                .ifEmpty { name }
            rel.norm()
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
