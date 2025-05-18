package ai.koog.agents.example.redcode

import ai.koog.agents.example.redcode.RedCodeFixingTools.DependencyFixingTools
import ai.koog.agents.example.redcode.RedCodeFixingTools.ImportFixingTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

private data class FileErrorSummary(
    val filePath: String,
    val lineNumber: Int,
    val position: Int,
    val error: String,
)

private fun runGradleProcess(projectPath: String): String {
    val process = ProcessBuilder("bash", "-c", "cd $projectPath && ./gradlew build").start()
    val output = process.inputStream.bufferedReader().readText()
    val error = process.errorStream.bufferedReader().readText()
    process.waitFor()
    return (output + error)
}

private fun checkGradleBuild(projectPath: String): List<String> {
    val gradleOutput = runGradleProcess(projectPath)
    return gradleOutput
        .lines()
        .filter { it.startsWith("e: file://") }
        .map { it.removePrefix("e: file://") }
}

private fun fileErrors(projectPath: String): List<FileErrorSummary> = checkGradleBuild(projectPath).map { error ->
    val fileCoordinates = error.substringBefore(' ')
    val errorMessage = error.substringAfter(' ')

    val (filePath, lineNumber, position) = fileCoordinates.split(":")

    FileErrorSummary(filePath.removePrefix(projectPath + "/"), lineNumber.toInt(), position.toInt(), errorMessage)
}

private fun readFile(path: String): String = File(path).readText()

private fun writeFile(path: String, newText: String) = File(path).writeText(newText)

private fun filePath(rootPath: String, path: String): String {
    val fixedPath = path.removePrefix(".").removePrefix("/")
    return if (fixedPath.startsWith(rootPath))
        fixedPath
    else
        rootPath + "/" + fixedPath
}

private fun String.lineRangeWithAddedLines(range: IntRange): String = this
    .lines()
    .mapIndexed { index, s ->
        index to s
    }
    .filter { it.first in range }
    .joinToString("\n") {
        "${it.first + 1}: ${it.second}"
    }

private fun String.lineRangeWithAddedLines() = lineRangeWithAddedLines(0 until lines().size)

class SimpleKotlinListFilesWithErrorsTool(val projectRootPath: String) : RedCodeFixingTools.ListFilesWithErrorsTool() {
    override suspend fun execute(args: EmptyArgs): Result {
        val fileErrors = fileErrors(projectRootPath)
        val totalErrors = fileErrors.size

        // single module
        return Result(
            totalNumberOfErrorsInProject = totalErrors,
            moduleErrors = listOf(
                Result.ModuleError(
                    moduleRootPath = ".",
                    fileErrors = fileErrors.groupBy(FileErrorSummary::filePath).map { (file, errors) ->
                        Result.FileError(
                            filePath = file,
                            numberOfErrorsInFile = errors.size
                        )
                    },
                    totalNumberOfErrorsInModule = totalErrors
                )
            )
        )
    }
}

class SimpleKotlinFindErrorsInFileTool(val projectRootPath: String) : RedCodeFixingTools.FindErrorsInFileTool() {
    override suspend fun execute(args: Args): Result {
        val fileErrors = fileErrors(projectRootPath).filter { it.filePath == args.filePath }.map {
            Result.ErrorDetail(
                line = it.lineNumber,
                errorMessage = it.error,
                affectedCode = readFile(filePath(projectRootPath, it.filePath))
                    .lineRangeWithAddedLines((it.lineNumber - 2)..(it.lineNumber + 1))
            )
        }

        return Result(
            filePath = args.filePath,
            errors = fileErrors,
            totalNumberOfErrors = fileErrors.size,
            buildErrorsInModule = emptyList()
        )
    }
}

class SimpleKotlinDetermineModuleRootsTool(val projectRootPath: String) : RedCodeFixingTools.DetermineModuleRootsTool() {
    override suspend fun execute(args: Args): Result {
        return Result(
            currentModuleRootPath = ".",
            parentModuleRoots = emptyList()
        )
    }
}

class SimpleKotlinFindBuildScriptTool(val projectRootPath: String) : RedCodeFixingTools.FindBuildScriptTool() {
    override suspend fun execute(args: Args): Result {
        return Result("./build.gradle.kts")
    }
}

class SimpleKotlinReadFileTextTool(val projectRootPath: String) : RedCodeFixingTools.ReadFileTextTool() {
    override suspend fun execute(args: Args): Result {
        val filePath = filePath(projectRootPath, args.filePath)
        val fileContent = readFile(filePath)
        return Result(
            fileContent = fileContent.lineRangeWithAddedLines((args.fromLine - 1) until fileContent.lines().size),
            isPartialFileContent = args.fromLine > 1
        )
    }
}

class SimpleKotlinEditFileTextTool(val projectRootPath: String) : RedCodeFixingTools.EditFileTextTool() {
    override suspend fun execute(args: Args): Result {
        try {
            val filePath = filePath(projectRootPath, args.filePath)
            val range = (args.fromLine - 1)..(args.untilLine - 1)
            val fileContent = readFile(filePath)
            val oldText = fileContent.lineRangeWithAddedLines(range)

            val newTextWithoutLineNumbers = args.newText.lines().map {
                if (it.matches(Regex("(\\d+): (.+)")))
                    it.substringAfter(": ")
                else it
            }

            val newFileContent = fileContent.lines().let { lines ->
                lines.subList(0, range.first) + newTextWithoutLineNumbers + lines.subList(range.last + 1, lines.size)
            }.joinToString("\n")

            writeFile(newText = newFileContent, path = filePath)

            return Result(
                success = true,
                oldText = oldText,
                newText = readFile(filePath).lineRangeWithAddedLines(range),
                errorMessage = null
            )
        } catch (e: Throwable) {
            return Result(
                success = false,
                oldText = null,
                newText = null,
                errorMessage = e.message + "\n" + e.stackTraceToString().take(200)
            )
        }
    }
}

class SimpleKotlinInsertTextToFileTool(val projectRootPath: String) : RedCodeFixingTools.InsertIntoFileTool() {
    override suspend fun execute(args: Args): Result {
        try {
            val filePath = filePath(projectRootPath, args.filePath)

            val range = (args.lineNumber - 1)..(args.lineNumber - 1)
            val oldText = readFile(filePath).lineRangeWithAddedLines(range)
            val fileContent = readFile(filePath)

            val newFileContent = fileContent.lines().let { lines ->
                when (args.relativePosition) {
                    Position.BEFORE -> lines.subList(0, args.lineNumber) + args.textToInsert.lines() + lines.subList(
                        args.lineNumber,
                        lines.size
                    )

                    Position.AFTER -> lines.subList(0, args.lineNumber + 1) + args.textToInsert.lines() + lines.subList(
                        args.lineNumber + 2,
                        lines.size
                    )
                }
            }.joinToString("\n")

            return Result(
                success = true,
                oldText = oldText,
                newText = readFile(filePath).lineRangeWithAddedLines(range),
                errorMessage = null
            )
        } catch (e: Throwable) {
            return Result(
                success = false,
                oldText = null,
                newText = null,
                errorMessage = e.message + "\n" + e.stackTraceToString().take(200)
            )
        }
    }
}


class SimpleKotlinSearchWordProjectTool(val projectRootPath: String) : RedCodeFixingTools.SearchWordProjectTool() {
    override suspend fun execute(args: Args): Result {
        val occurrences = mutableListOf<Result.Occurrence>()

        withContext(Dispatchers.IO) {
            Files.walkFileTree(File(projectRootPath).toPath(), object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    val filePath = file.toString().removePrefix(projectRootPath + "/")

                    if (filePath.startsWith("gradlew") || filePath.startsWith("build") || filePath.startsWith(".")) {
                        return FileVisitResult.SKIP_SUBTREE
                    }
                    if (file.toFile().isFile) {
                        val lines = file.toFile().readLines()
                        lines.forEachIndexed { lineNumber, line ->
                            if (line.contains(args.word, ignoreCase = true)) {
                                occurrences.add(
                                    Result.Occurrence(
                                        filePath = file.toString().removePrefix(projectRootPath + "/"),
                                        modulePath = projectRootPath,
                                        lineNumber = lineNumber + 1,
                                        lineText = line
                                    )
                                )
                            }
                        }
                    }
                    return FileVisitResult.CONTINUE
                }
            })
        }

        return Result(
            occurrences = occurrences,
            totalOccurrences = occurrences.size
        )
    }
}

class SimpleKotlinListImportsInFileTool(val projectRootPath: String) : ImportFixingTools.ListImportsInFileTool() {
    override suspend fun execute(args: Args): Result {
        val filePath = filePath(projectRootPath, args.filePath)
        val content = readFile(filePath)

        val imports = content.lines()
            .filter { it.startsWith("import ") }
            .map { it.substringAfter("import ").trim() }

        return Result(imports)
    }
}

class SimpleKotlinAddImportsToFileTool(val projectRootPath: String) : ImportFixingTools.AddImportsToFileTool() {
    override suspend fun execute(args: Args): Result {
        val filePath = filePath(projectRootPath, args.filePath)
        val content = readFile(filePath)

        val lines = content.lines()

        val lineWithFirstImport = lines.indexOfFirst { it.startsWith("import ") }

        val newLines = when (lineWithFirstImport) {
            -1 -> {
                val lineWithPackage = lines.indexOfFirst { it.startsWith("package ") }
                lines.subList(0, lineWithPackage + 1) +
                        listOf("") +
                        args.importsToAdd.map { "import ${it.toImport()}" } +
                        lines.subList(lineWithPackage + 1, lines.size)
            }

            else -> lines.subList(0, lineWithFirstImport) +
                    args.importsToAdd.map { "import ${it.toImport()}" } +
                    lines.subList(lineWithFirstImport, lines.size)
        }

        val newContent = newLines.joinToString("\n")

        writeFile(filePath, newContent)

        val imports = content.lines()
            .filter { it.startsWith("import ") }
            .map { it.substringAfter("import ").trim() }

        return Result(imports)
    }
}

class SimpleKotlinRemoveImportFromFileTool(val projectRootPath: String) : ImportFixingTools.RemoveImportFromFileTool() {
    override suspend fun execute(args: Args): Result {
        val filePath = filePath(projectRootPath, args.filePath)
        val content = readFile(filePath)

        val lines = content.lines()

        val lineWithGivenImport = lines.indexOfFirst { it.startsWith("import ${args.importToRemove.toImport()}") }

        if (lineWithGivenImport != -1) {
            val newLines = lines.subList(0, lineWithGivenImport) +
                    lines.subList(lineWithGivenImport + 1, lines.size)

            val newContent = newLines.joinToString("\n")

            writeFile(filePath, newContent)
        }

        val imports = content.lines()
            .filter { it.startsWith("import ") }
            .map { it.substringAfter("import ").trim() }

        return Result(imports)
    }
}

class SimpleKotlinListDependenciesInModuleTool(val projectRootPath: String) :
    DependencyFixingTools.ListDependenciesInModuleTool() {
    override suspend fun execute(args: Args): Result {
        val filePath = filePath(projectRootPath, args.moduleRootPath + "/build.gradle.kts")
        val content = readFile(filePath)

        val dependencies = content.lines()
            .filter { it.startsWith("    implementation") }
            .map { it.substringAfter("    implementation").trim() }

        return Result(dependencies)
    }
}

class SimpleKotlinAddDependenciesToModuleTool(val projectRootPath: String) :
    DependencyFixingTools.AddDependenciesToModuleTool() {
    override suspend fun execute(args: Args): Result {
        val filePath = filePath(projectRootPath, args.moduleRootPath + "/build.gradle.kts")
        val content = readFile(filePath)

        val lines = content.lines()

        val lineWithFirstDependency = lines.indexOfFirst { it.startsWith("    implementation") }

        val newLines = lines.subList(0, lineWithFirstDependency) +
                args.dependenciesToAdd.map { "    implementation(${it.toDependency()})" } +
                lines.subList(lineWithFirstDependency, lines.size)

        val newContent = newLines.joinToString("\n")

        writeFile(filePath, newContent)

        val dependencies = content.lines()
            .filter { it.startsWith("    implementation") }
            .map { it.substringAfter("    implementation").trim() }

        return Result(dependencies)
    }
}

class SimpleKotlinRemoveDependencyFromModuleTool(val projectRootPath: String) :
    DependencyFixingTools.RemoveDependencyFromModuleTool() {
    override suspend fun execute(args: Args): Result {
        val filePath = filePath(projectRootPath, args.moduleRootPath + "/build.gradle.kts")
        val content = readFile(filePath)

        val lines = content.lines()

        val lineWithGivenDependency = lines.indexOfFirst { it.startsWith("    implementation(${args.dependencyToRemove.toDependency()})") }

        if (lineWithGivenDependency != -1) {
            val newLines = lines.subList(0, lineWithGivenDependency) +
                    lines.subList(lineWithGivenDependency + 1, lines.size)

            val newContent = newLines.joinToString("\n")

            writeFile(filePath, newContent)
        }

        val dependencies = content.lines()
            .filter { it.startsWith("    implementation") }
            .map { it.substringAfter("    implementation").trim() }

        return Result(dependencies)
    }
}

private fun String.toDependency() = removePrefix("implementation(").removeSuffix(")").trim()
private fun String.toImport() = removePrefix("import ").trim()