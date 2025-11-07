package ai.koog.integration.tests.utils.tools.files

sealed interface OperationResult<T> {
    class Success<T>(val result: T) : OperationResult<T>
    class Failure<T>(val error: String) : OperationResult<T>
}

class MockFileSystem {
    private val fileContents: MutableMap<String, String> = mutableMapOf()

    fun create(path: String, content: String): OperationResult<Unit> {
        if (path in fileContents) return OperationResult.Failure("File already exists")
        fileContents[path] = content
        return OperationResult.Success(Unit)
    }

    fun delete(path: String): OperationResult<Unit> {
        if (path !in fileContents) return OperationResult.Failure("File does not exist")
        fileContents.remove(path)
        return OperationResult.Success(Unit)
    }

    fun read(path: String): OperationResult<String> {
        if (path !in fileContents) return OperationResult.Failure("File does not exist")
        return OperationResult.Success(fileContents[path]!!)
    }

    fun ls(path: String): OperationResult<List<String>> {
        if (path in fileContents) {
            return OperationResult.Failure("Path $path points to a file, but not a directory!")
        }
        val matchingFiles = fileContents
            .filter { (filePath, _) -> filePath.startsWith(path) }
            .map { (filePath, _) -> filePath }

        if (matchingFiles.isEmpty()) {
            return OperationResult.Failure("No files in the directory. Directory doesn't exist or is empty.")
        }
        return OperationResult.Success(matchingFiles)
    }

    fun fileCount(): Int = fileContents.size
}
