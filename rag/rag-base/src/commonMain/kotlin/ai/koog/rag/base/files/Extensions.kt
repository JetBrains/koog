package ai.koog.rag.base.files

internal fun <Path> Path.contains(
    other: Path,
    fs: FileSystemProvider.ReadOnly<Path>
): Boolean {
    val currentComponents = this.components(fs)
    val otherComponents = other.components(fs)
    return currentComponents.zip(otherComponents)
        .all { it.first == it.second } &&
        otherComponents.size >= currentComponents.size
}

private fun <Path> Path.components(fs: FileSystemProvider.ReadOnly<Path>): List<String> {
    return buildList {
        var path: Path? = this@components
        while (path != null) {
            add(fs.name(path))
            path = fs.parent(path)
        }
    }.asReversed()
}

/**
 * Reads the entire content of a file as a string.
 *
 * @param path The file path to read.
 * @param documentProvider document provider to get unsaved text from.
 * @return The file content as a string.
 *
 * @see [FileSystemProvider.ReadOnly.readBytes]
 * @see [DocumentProvider.document]
 * @see [DocumentProvider.text]
 */
public suspend fun <Path, Document> FileSystemProvider.ReadOnly<Path>.readText(
    path: Path,
    documentProvider: DocumentProvider<Path, Document>?
): String {
    if (documentProvider != null) {
        val document = documentProvider.document(path)
        if (document != null) {
            return documentProvider.text(document).toString()
        }
    }
    return readBytes(path).decodeToString()
}

/**
 * Reads the entire content of a file as a string.
 *
 * @param path The file path to read.
 * @return The file content as a string.
 *
 * @see [FileSystemProvider.ReadOnly.readBytes]
 */
public suspend fun <Path> FileSystemProvider.ReadOnly<Path>.readText(path: Path): String {
    return readBytes(path).decodeToString()
}

/**
 * Writes a string to a file, replacing any existing content.
 *
 * @param path The file path to write to.
 * @param content The string content to write.
 *
 * @see [FileSystemProvider.ReadWrite.writeBytes]
 */
public suspend fun <Path> FileSystemProvider.ReadWrite<Path>.writeText(path: Path, content: String) {
    writeBytes(path, content.encodeToByteArray())
}

/**
 * Creates a file at the specified [path].
 * Parent directories will be created automatically if they don't exist.
 *
 * @param path The path where the file should be created.
 *
 * @see [FileSystemProvider.ReadWrite.create]
 */
public suspend fun <Path> FileSystemProvider.ReadWrite<Path>.createFile(path: Path) {
    create(path, FileMetadata.FileType.File)
}

/**
 * Creates a directory at the specified [path].
 * Parent directories will be created automatically if they don't exist.
 *
 * @param path The path where the directory should be created.
 *
 * @see [FileSystemProvider.ReadWrite.create]
 */
public suspend fun <Path> FileSystemProvider.ReadWrite<Path>.createDirectory(path: Path) {
    create(path, FileMetadata.FileType.Directory)
}
