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
