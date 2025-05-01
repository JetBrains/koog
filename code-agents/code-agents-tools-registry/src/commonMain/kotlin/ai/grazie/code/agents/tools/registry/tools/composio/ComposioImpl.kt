package ai.grazie.code.agents.tools.registry.tools.composio

import ai.grazie.code.agents.core.tools.ToolStage
import ai.grazie.code.agents.tools.registry.GlobalAgentToolStages
import ai.grazie.code.agents.tools.registry.tools.composio.ComposioTools.FileTools.ChangeWorkingDirectory
import ai.grazie.code.agents.tools.registry.tools.composio.ComposioTools.FileTools.CreateFile
import ai.grazie.code.agents.tools.registry.tools.composio.ComposioTools.FileTools.DumpGitRepoTreeFile
import ai.grazie.code.agents.tools.registry.tools.composio.ComposioTools.FileTools.EditFile
import ai.grazie.code.agents.tools.registry.tools.composio.ComposioTools.FileTools.FindFile
import ai.grazie.code.agents.tools.registry.tools.composio.ComposioTools.FileTools.GitPatch
import ai.grazie.code.agents.tools.registry.tools.composio.ComposioTools.FileTools.ListFiles
import ai.grazie.code.agents.tools.registry.tools.composio.ComposioTools.FileTools.OpenFile
import ai.grazie.code.agents.tools.registry.tools.composio.ComposioTools.FileTools.Scroll
import ai.grazie.code.agents.tools.registry.tools.composio.ComposioTools.FileTools.SearchWord
import ai.grazie.code.agents.tools.registry.tools.composio.ComposioTools.FileTools.Write
import ai.grazie.code.files.model.DocumentProvider
import ai.grazie.code.files.model.DocumentProvider.DocumentRange
import ai.grazie.code.files.model.DocumentProvider.Position
import ai.grazie.code.files.model.FileMetadata
import ai.grazie.code.files.model.FileSystemProvider
import ai.grazie.code.files.model.isDirectory
import kotlinx.coroutines.flow.*

/**
 * Creates a Composio tool stage implementation using providers from the Code Engine API.
 *
 * @param root The root path for file system operations.
 * @param fileSelect An implementation to select and manage files in the file system.
 * @param fileWrite An implementation to write files to the file system.
 * @param documentProvider A provider for managing and accessing documents related to the file system.
 * @param documentEdit A provider for editing text within documents.
 * @param editorManager A manager for handling file editors and current working directories.
 * @param getMethodBody Tool for extracting the body of methods from a given source.
 * @param getClassInfo Tool for retrieving information about classes within the source files.
 * @param getMethodSignature Tool for extracting method signatures from a given source.
 * @return A ToolStage instance implementing the Composio functionalities.
 */
@Suppress("FunctionName")
fun <Path, Document> GlobalAgentToolStages.SWE.ComposioImpl(
    root: Path,
    fileSelect: FileSystemProvider.Select<Path>,
    fileWrite: FileSystemProvider.Write<Path>,
    documentProvider: DocumentProvider<Path, Document>,
    documentEdit: DocumentProvider.Edit<Path, Document>,
    editorManager: EditorManager<Path>,
    // Code Analysis tools:
    getMethodBody: ComposioTools.CodeAnalysisTools.GetMethodBody,
    getClassInfo: ComposioTools.CodeAnalysisTools.GetClassInfo,
    getMethodSignature: ComposioTools.CodeAnalysisTools.GetMethodSignature,
): ToolStage {
    val fileTreeProvider = FileTreeProvider(root, fileSelect)

    return Composio(
        getMethodBody = getMethodBody,
        getClassInfo = getClassInfo,
        getMethodSignature = getMethodSignature,
        gitRepoTree = GitRepoTreeTool(
            root = root,
            fileSelect = fileSelect,
            fileWrite = fileWrite,
            documentProvider = documentProvider,
            documentEdit = documentEdit,
            fileTreeProvider = fileTreeProvider
        ),
        changeWorkingDirectory = ChangeWorkingDirectoryTool(),
        createFile = CreateFileTool(
            root = root,
            fileSelect = fileSelect,
            fileWrite = fileWrite,
            editorManager = editorManager
        ),
        editFile = EditFileTool(
            root = root,
            fileSelect = fileSelect,
            documentProvider = documentProvider,
            documentEdit = documentEdit,
            editorManager = editorManager
        ),
        findFile = FindFileTool(
            root = root,
            fileSelect = fileSelect,
            editorManager = editorManager,
            fileTreeProvider = fileTreeProvider
        ),
        gitPatch = GitPatchTool(),
        listFiles = ListFilesTool(
            root = root,
            fileSelect = fileSelect,
            editorManager = editorManager
        ),
        openFile = OpenFileTool(
            root = root,
            fileSelect = fileSelect,
            editorManager = editorManager
        ),
        scroll = ScrollTool(
            root = root,
            fileSelect = fileSelect,
            editorManager = editorManager
        ),
        searchWord = SearchWordTool(
            root = root,
            fileSelect = fileSelect,
            documentProvider = documentProvider,
            editorManager = editorManager,
            fileTreeProvider = fileTreeProvider
        ),
        write = WriteTool(
            root = root,
            fileSelect = fileSelect,
            documentProvider = documentProvider,
            documentEdit = documentEdit,
            editorManager = editorManager
        ),
        stageName = ToolStage.DEFAULT_STAGE_NAME,
        toolListName = ToolStage.DEFAULT_TOOL_LIST_NAME
    )
}


private class GitRepoTreeTool<Path, Document>(
    private val root: Path,
    private val fileSelect: FileSystemProvider.Select<Path>,
    private val fileWrite: FileSystemProvider.Write<Path>,
    private val documentProvider: DocumentProvider<Path, Document>,
    private val documentEdit: DocumentProvider.Edit<Path, Document>,
    private val fileTreeProvider: FileTreeProvider<Path>
) : DumpGitRepoTreeFile() {
    override suspend fun execute(args: Args): Result {
        try {
            val path = fileSelect.fromRelativeString(root, args.gitRepoPath)
            val treeFilePath = fileSelect.fromRelativeString(root, "git_repo_tree.txt")

            if (!fileSelect.exists(treeFilePath)) {
                fileWrite.create(root, "git_repo_tree.txt", FileMetadata.FileType.File)
            }

            val gitTreeText = fileTreeProvider.getTree(path).toList().joinToString("\n") { it.first }

            documentProvider.document(treeFilePath)?.let { gitTreeDocument ->
                documentEdit.setText(gitTreeDocument, gitTreeText)
                return Result(
                    message = "Written folder tree structure to the file `${
                        fileSelect.relativize(
                            root,
                            treeFilePath
                        )
                    }`",
                    success = true
                )
            } ?: return Result(
                message = "Failed to write to the file git_repo_tree.txt, it couldn't be created or found",
                success = false
            )
        } catch (e: Exception) {
            return Result(
                error = e.message ?: "Unknown error",
                message = e.stackTraceToString(),
                success = false
            )
        }
    }
}

private class FileTreeProvider<Path>(
    private val root: Path,
    private val fileSelect: FileSystemProvider.Select<Path>
) {
    fun getTree(path: Path, maxDepth: Int? = null): Flow<Pair<String, Path>> = flow {
        suspend fun collectFilesRecursively(currentPath: Path, currentDepth: Int) {
            if (fileSelect.isDirectory(currentPath) && (maxDepth == null || currentDepth < maxDepth)) {
                val subPaths = fileSelect.list(currentPath)
                for (subPath in subPaths) {
                    collectFilesRecursively(subPath, currentDepth + 1)
                }
            } else {
                fileSelect.relativize(root, currentPath)?.let { emit(it to currentPath) }
            }
        }
        collectFilesRecursively(path, 0)
    }
}

private class WriteTool<Path, Document>(
    private val root: Path,
    private val fileSelect: FileSystemProvider.Select<Path>,
    private val documentProvider: DocumentProvider<Path, Document>,
    private val documentEdit: DocumentProvider.Edit<Path, Document>,
    private val editorManager: EditorManager<Path>
) : Write() {
    override suspend fun execute(args: Args): Result {
        try {
            val filePath = args.filePath?.let { fileSelect.fromRelativeString(root, it) }
                ?: editorManager.getCurrentlyOpenedFile()
                ?: return Result(
                    currentWorkingDirectory = fileSelect.relativize(
                        root,
                        editorManager.getCurrentWorkingDir()
                    )!!,
                    error = "No file path provided"
                )

            documentProvider.document(filePath)?.let { document ->
                documentEdit.setText(document, args.text)
                return Result(
                    currentWorkingDirectory = fileSelect.relativize(
                        root,
                        editorManager.getCurrentWorkingDir()
                    )!!
                )
            } ?: return Result(
                currentWorkingDirectory = fileSelect.relativize(
                    root,
                    editorManager.getCurrentWorkingDir()
                )!!,
                error = "Couldn't open or find file $filePath"
            )
        } catch (e: Exception) {
            return Result(
                currentWorkingDirectory = fileSelect.relativize(
                    root,
                    editorManager.getCurrentWorkingDir()
                )!!,
                error = e.message ?: "Unknown error"
            )
        }
    }
}

private class SearchWordTool<Path, Document>(
    private val root: Path,
    private val fileSelect: FileSystemProvider.Select<Path>,
    private val documentProvider: DocumentProvider<Path, Document>,
    private val editorManager: EditorManager<Path>,
    private val fileTreeProvider: FileTreeProvider<Path>
) : SearchWord() {
    override suspend fun execute(args: Args): Result {
        fun updateCase(value: String) = if (!args.caseInsensitive) value.lowercase() else value

        try {
            val result = fileTreeProvider.getTree(root, if (args.recursive) null else 1)
                .filter { (path, _) ->
                    val caseUpdatedPath = updateCase(path)

                    args.pattern == null || caseUpdatedPath.matches(Regex(args.pattern))
                }
                .mapNotNull { (pathStr, path) ->
                    documentProvider.document(path)?.let {
                        val text = documentProvider.text(it).toString()
                        if (updateCase(text).contains(args.word)) {
                            text.lines().mapIndexedNotNull { lineNumber, line ->
                                if (updateCase(line).contains(args.word))
                                    lineNumber to line
                                else null
                            }
                        } else null
                    }?.let { pathStr to it }
                }
                .toList()
                .toMap()

            return Result(
                currentWorkingDirectory = fileSelect.relativize(
                    root,
                    editorManager.getCurrentWorkingDir()
                )!!,
                results = result
            )
        } catch (e: Exception) {
            return Result(
                currentWorkingDirectory = fileSelect.relativize(
                    root,
                    editorManager.getCurrentWorkingDir()
                )!!,
                error = e.message ?: "Unknown error",
                message = e.stackTraceToString()
            )
        }
    }
}

private class ScrollTool<Path>(
    private val root: Path,
    private val fileSelect: FileSystemProvider.Select<Path>,
    private val editorManager: EditorManager<Path>
) : Scroll() {
    override suspend fun execute(args: Args): Result {
        try {
            val direction = when (args.direction.lowercase()) {
                "down" -> EditorManager.ScrollDirection.DOWN
                "up" -> EditorManager.ScrollDirection.UP
                else -> return Result(
                    currentWorkingDirectory = fileSelect.relativize(
                        root,
                        editorManager.getCurrentWorkingDir()
                    )!!,
                    error = "Unknown direction provided: ${args.direction} (expected: `up` or `down`)"
                )
            }

            return Result(
                currentWorkingDirectory = fileSelect.relativize(
                    root,
                    editorManager.getCurrentWorkingDir()
                )!!,
                lines = editorManager.scroll(direction, args.lines, args.scrollId)
            )
        } catch (e: Exception) {
            return Result(
                currentWorkingDirectory = fileSelect.relativize(
                    root,
                    editorManager.getCurrentWorkingDir()
                )!!,
                error = e.message ?: "Unknown error",
                message = e.stackTraceToString()
            )
        }
    }
}

private class OpenFileTool<Path>(
    private val root: Path,
    private val fileSelect: FileSystemProvider.Select<Path>,
    private val editorManager: EditorManager<Path>
) : OpenFile() {
    override suspend fun execute(args: Args): Result {
        try {
            val lines = editorManager.openFile(
                fileSelect.fromRelativeString(root, args.filePath),
                args.lineNumber
            )
            return Result(
                currentWorkingDirectory = fileSelect.relativize(
                    root,
                    editorManager.getCurrentWorkingDir()
                )!!,
                lines = lines,
                startLineNumber = args.lineNumber
            )
        } catch (e: Exception) {
            return Result(
                currentWorkingDirectory = fileSelect.relativize(
                    root,
                    editorManager.getCurrentWorkingDir()
                )!!,
                error = e.message ?: "Unknown error",
                message = e.stackTraceToString()
            )
        }
    }
}

private class ListFilesTool<Path>(
    private val root: Path,
    private val fileSelect: FileSystemProvider.Select<Path>,
    private val editorManager: EditorManager<Path>
) : ListFiles() {
    override suspend fun execute(args: Args): Result {
        try {
            editorManager
                .getCurrentlyOpenedFile()
                ?.let { fileSelect.parent(it) }
                ?.let { dir ->
                    return Result(
                        currentWorkingDirectory = fileSelect.relativize(
                            root,
                            editorManager.getCurrentWorkingDir()
                        )!!,
                        files = fileSelect.list(dir).mapNotNull { child ->
                            val childPath = fileSelect.relativize(root, child)
                            val type = if (fileSelect.isDirectory(child)) "dir" else "file"

                            childPath?.to(type)
                        }
                    )
                }
        } catch (e: Exception) {
            return Result(
                currentWorkingDirectory = fileSelect.relativize(
                    root,
                    editorManager.getCurrentWorkingDir()
                )!!,
                error = e.message ?: "Unknown error",
            )
        }
        return Result()
    }
}

private class GitPatchTool : GitPatch() {
    override suspend fun execute(args: Args): Result {
        return Result()
    }
}

private class EditFileTool<Path, Document>(
    private val root: Path,
    private val fileSelect: FileSystemProvider.Select<Path>,
    private val documentProvider: DocumentProvider<Path, Document>,
    private val documentEdit: DocumentProvider.Edit<Path, Document>,
    private val editorManager: EditorManager<Path>
) : EditFile() {
    override suspend fun execute(args: Args): Result {
        try {
            val filePath = args.filePath?.let { fileSelect.fromRelativeString(root, it) }
                ?: editorManager.getCurrentlyOpenedFile()
                ?: return Result(
                    currentWorkingDirectory = fileSelect.relativize(
                        root,
                        editorManager.getCurrentWorkingDir()
                    )!!,
                    error = "No file path provided"
                )

            documentProvider.document(filePath)?.let { document ->
                val startLine = args.startLine
                val lines = documentProvider.text(document).lines()
                val endLine = args.endLine ?: lines.lastIndex

                val endColumn = lines.last().lastIndex

                val range = DocumentRange(
                    start = Position(
                        line = startLine,
                        column = 0
                    ),
                    end = Position(
                        line = endLine,
                        column = endColumn
                    )
                )

                val oldText = documentProvider.textFragment(document, range)
                documentEdit.setText(document, args.text, range)

                return Result(
                    currentWorkingDirectory = fileSelect.relativize(
                        root,
                        editorManager.getCurrentWorkingDir()
                    )!!,
                    oldText = oldText,
                    updatedText = documentProvider.textFragment(document, range)
                )
            } ?: return Result(
                currentWorkingDirectory = fileSelect.relativize(
                    root,
                    editorManager.getCurrentWorkingDir()
                )!!,
                error = "Couldn't open or find file $filePath"
            )
        } catch (e: Exception) {
            return Result(
                error = e.message ?: "Unknown error",
            )
        }
    }
}

private class CreateFileTool<Path>(
    private val root: Path,
    private val fileSelect: FileSystemProvider.Select<Path>,
    private val fileWrite: FileSystemProvider.Write<Path>,
    private val editorManager: EditorManager<Path>
) : CreateFile() {
    override suspend fun execute(args: Args): Result {
        try {
            fileWrite.create(root, args.path, FileMetadata.FileType.File)
            val createdFile = fileSelect.fromRelativeString(root, args.path)

            return Result(
                currentWorkingDirectory = fileSelect.relativize(
                    root,
                    editorManager.getCurrentWorkingDir()
                )!!,
                path = fileSelect.toAbsolutePathString(createdFile)
            )
        } catch (e: Exception) {
            return Result(
                error = e.message ?: "Unknown error",
            )
        }
    }
}

private class ChangeWorkingDirectoryTool : ChangeWorkingDirectory() {
    override suspend fun execute(args: Args): Result {
        return Result(error = "Cannot change the working directory")
    }
}

private class FindFileTool<Path>(
    private val root: Path,
    private val fileSelect: FileSystemProvider.Select<Path>,
    private val editorManager: EditorManager<Path>,
    private val fileTreeProvider: FileTreeProvider<Path>
) : FindFile() {
    override suspend fun execute(args: Args): Result {
        fun updateCase(value: String) = if (!args.caseSensitive) value.lowercase() else value

        try {
            val matchingFiles = fileTreeProvider.getTree(root, args.depth)
                .filter { (path, _) ->
                    val caseUpdatedPath = updateCase(path)
                    val includedInSearch = (args.include == null)
                            || args.include.map(::updateCase).any(caseUpdatedPath::startsWith)
                    val excludedFromSearch = (args.exclude != null)
                            && args.exclude.map(::updateCase).any(caseUpdatedPath::startsWith)

                    includedInSearch && !excludedFromSearch && caseUpdatedPath.matches(Regex(args.pattern))
                }
                .map { it.first }
                .toList()

            return Result(
                currentWorkingDirectory = fileSelect.relativize(
                    root,
                    editorManager.getCurrentWorkingDir()
                )!!,
                results = matchingFiles
            )
        } catch (e: Exception) {
            return Result(
                currentWorkingDirectory = fileSelect.relativize(
                    root,
                    editorManager.getCurrentWorkingDir()
                )!!,
                error = e.message ?: "Unknown error",
                message = e.stackTraceToString()
            )
        }
    }
}

