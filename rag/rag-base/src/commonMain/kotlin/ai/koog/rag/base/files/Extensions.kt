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
 * Reads the entire contents of a file as a string.
 *
 * @param address The file path to read
 * @param documentProvider Optional document provider to get text from if provided
 * @return String containing the file contents
 */
public suspend fun <Path, Document> FileSystemProvider.ReadOnly<Path>.readText(
    address: Path,
    documentProvider: DocumentProvider<Path, Document>? = null
): String {
    if (documentProvider != null) {
        val document = documentProvider.document(address)
        if (document != null) {
            return documentProvider.text(document).toString()
        }
    }
    return readBytes(address).decodeToString()
}
/**
 * Reads the entire contents of a file as a string.
 *
 * @param address The file path to read
 * @return String containing the file contents
 */
public suspend fun <Path> FileSystemProvider.ReadOnly<Path>.readText(
    address: Path,
): String {
    return readBytes(address).decodeToString()
}

/**
 * Writes a string to a file, replacing any existing content.
 *
 * @param address The file path to write to
 * @param content The string content to write
 */
public suspend fun <Path> FileSystemProvider.ReadWrite<Path>.writeText(address: Path, content: String) =
    writeBytes(address, content.encodeToByteArray())


/**
 * Creates a file at the specified path.
 *
 * Parent directories will be created automatically if they don't exist.
 *
 * @param path The path where the file should be created
 * @return true if the file was created successfully, false otherwise
 */
public suspend fun <Path> FileSystemProvider.ReadWrite<Path>.createFile(path: Path) {
    create(path, FileMetadata.FileType.File)
}

/**
 * Creates a directory at the specified path.
 *
 * Creates any necessary parent directories.
 *
 * @param path The path where the directory should be created
 * @return true if the directory was created successfully, false otherwise
 */
public suspend fun <Path> FileSystemProvider.ReadWrite<Path>.createDirectory(path: Path) {
    create(path, FileMetadata.FileType.Directory)
}
