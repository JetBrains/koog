@file:JvmName("SkillsDiscovery")
package ai.koog.skills.discovery

import ai.koog.rag.base.files.FileMetadata
import ai.koog.rag.base.files.FileSystemProvider
import ai.koog.skills.model.Skill
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.jvm.JvmName

private val logger = KotlinLogging.logger {}

/**
 * Defines which discovered skill wins when multiple skills share the same name.
 */
public enum class SkillCollisionPrecedence {
    FIRST_FOUND,
    LAST_FOUND,
}

/**
 * Discovers and parses skills from [directoriesToSearch] using breadth-first traversal.
 *
 * For each visited directory, this function looks for a file named [skillFileName].
 * If found, the file is parsed as a skill definition and validated. Skills with missing
 * required frontmatter fields (`name`, `description`) or malformed frontmatter are ignored.
 *
 * Name collisions are resolved by [precedenceRule]:
 * - [SkillCollisionPrecedence.FIRST_FOUND] keeps the first discovered skill.
 * - [SkillCollisionPrecedence.LAST_FOUND] replaces with the most recently discovered skill.
 *
 * Discovery is bounded by [maxDepth] and [maxDirectories], and directories listed in
 * [skippedDirectoryNames] are not traversed.
 *
 * Warnings (ignored roots/directories, malformed skill files, collisions, and format mismatches)
 * are reported through [warningLogger].
 *
 * @param fs file system provider used to interact with files.
 * @param directoriesToSearch absolute root directories to scan for skills.
 * @param maxDepth maximum traversal depth relative to each root directory. Must be `>= 0`.
 * @param maxDirectories maximum number of directories to visit across all roots. Must be `> 0`.
 * @param precedenceRule rule for resolving duplicate skill names.
 * @param skillFileName skill descriptor file name to search for in each directory.
 * @param skippedDirectoryNames directory names that should be ignored during traversal.
 * @param skillNamePattern regular expression used to validate discovered skill names.
 * @param warningLogger callback used to emit non-fatal discovery warnings.
 *
 * @return discovered skills after applying validation and collision precedence.
 *
 * @throws IllegalArgumentException if [maxDepth] is negative or [maxDirectories] is not positive.
 */
public suspend fun <Path> discoverSkills(
    fs: FileSystemProvider.ReadOnly<Path>,
    directoriesToSearch: List<String>,
    maxDepth: Int = 4,
    maxDirectories: Int = 2000,
    precedenceRule: SkillCollisionPrecedence = SkillCollisionPrecedence.LAST_FOUND,
    skillFileName: String = DEFAULT_SKILL_FILE_NAME,
    skippedDirectoryNames: Set<String> = DEFAULT_SKIPPED_DIRECTORY_NAMES,
    skillNamePattern: Regex = DEFAULT_SKILL_NAME_PATTERN,
    warningLogger: (String) -> Unit = ::defaultWarningLogger,
): List<Skill> {
    require(maxDepth >= 0) { "maxDepth must be >= 0" }
    require(maxDirectories > 0) { "maxDirectories must be > 0" }

    val discoveredByName = mutableMapOf<String, Skill>()
    val queue = ArrayDeque<DirectoryDepth<Path>>()

    directoriesToSearch.forEach { root ->
        val path = fs.fromAbsolutePathString(root)
        if (fs.metadata(path)?.type == FileMetadata.FileType.Directory) {
            queue.addLast(DirectoryDepth(path, 0))
        } else {
            warningLogger("Skill discovery ignored non-directory root: $root")
        }
    }

    var visitedDirectories = 0

    while (queue.isNotEmpty() && visitedDirectories < maxDirectories) {
        val (directory, depth) = queue.removeFirst()
        visitedDirectories++

        val children = runCatching { fs.list(directory) }.getOrDefault(emptyList())
        val skillFile = children.firstOrNull { fs.name(it) == skillFileName }
        if (skillFile != null) {
            fs.parseSkill(skillFile, skillNamePattern, warningLogger)?.let { skill ->
                val existing = discoveredByName[skill.name]
                if (existing != null) {
                    warningLogger(
                        "Skill name collision for '${skill.name}': '${existing.location}' and '${skill.location}'"
                    )
                }

                if (precedenceRule == SkillCollisionPrecedence.LAST_FOUND || skill.name !in discoveredByName) {
                    discoveredByName[skill.name] = skill
                }
            }
        }

        if (depth >= maxDepth) {
            continue
        }

        children.forEach { child ->
            val childName = fs.name(child)
            if (childName in skippedDirectoryNames) {
                warningLogger(
                    "Skill discovery ignored directory '$childName' at '${fs.toAbsolutePathString(child)}'"
                )
                return@forEach
            }

            if (fs.metadata(child)?.type == FileMetadata.FileType.Directory) {
                queue.addLast(DirectoryDepth(child, depth + 1))
            }
        }
    }

    return discoveredByName.values.toList()
}

private data class DirectoryDepth<Path>(
    val directory: Path,
    val depth: Int,
)

private data class ParsedFrontmatter(
    val name: String?,
    val description: String?,
    val license: String?,
    val compatibility: String?,
    val metadata: Map<String, String>?,
    val allowedTools: String?,
    val isValidFrontmatter: Boolean,
)

private suspend fun <Path> FileSystemProvider.ReadOnly<Path>.parseSkill(
    skillFile: Path,
    skillNamePattern: Regex,
    warningLogger: (String) -> Unit,
): Skill? {
    val content = runCatching { readBytes(skillFile).decodeToString() }.getOrNull() ?: return null
    val frontmatter = parseFrontmatter(content)
    val location = toAbsolutePathString(skillFile)

    if (!frontmatter.isValidFrontmatter) {
        warningLogger("Skill file '$location' has malformed frontmatter and was ignored")
        return null
    }

    val skillName = frontmatter.name?.takeIf { it.isNotBlank() }
    if (skillName == null) {
        warningLogger("Skill file '$location' is missing required 'name' and was ignored")
        return null
    }

    val skillDescription = frontmatter.description?.takeIf { it.isNotBlank() }
    if (skillDescription == null) {
        warningLogger("Skill '$skillName' at '$location' is missing required 'description' and was ignored")
        return null
    }

    if (!skillName.matches(skillNamePattern)) {
        warningLogger("Skill '$skillName' at '$location' does not match expected name format")
    }

    val parentDirectoryName = parent(skillFile)?.let { name(it) }
    if (parentDirectoryName != skillName) {
        warningLogger(
            "Skill '$skillName' at '$location' does not match parent directory name '$parentDirectoryName'"
        )
    }

    return Skill(
        name = skillName,
        description = skillDescription,
        location = location,
        license = frontmatter.license,
        compatibility = frontmatter.compatibility,
        metadata = frontmatter.metadata,
        allowedTools = frontmatter.allowedTools,
    )
}

private fun parseFrontmatter(fileContent: String): ParsedFrontmatter {
    val lines = fileContent.lines()
    if (lines.isEmpty() || lines.first().trim() != "---") {
        return invalidParsedFrontmatter()
    }

    val closingIndex = lines.indexOfFirstFrom(1) { it.trim() == "---" }
    if (closingIndex == -1) {
        return invalidParsedFrontmatter()
    }

    val yamlLines = lines.subList(1, closingIndex)
    val topLevel = linkedMapOf<String, String>()
    val metadata = linkedMapOf<String, String>()
    var inMetadata = false

    yamlLines.forEach { rawLine ->
        val line = rawLine.trimEnd()
        if (line.isBlank() || line.trimStart().startsWith("#")) {
            return@forEach
        }

        val isIndented = rawLine.startsWith(" ") || rawLine.startsWith("\t")
        if (inMetadata && isIndented) {
            val metadataLine = line.trimStart()
            val separator = metadataLine.indexOf(':')
            if (separator > 0) {
                val key = metadataLine.substring(0, separator).trim()
                val value = metadataLine.substring(separator + 1).trim().trimQuotes()
                if (key.isNotEmpty() && value.isNotEmpty()) {
                    metadata[key] = value
                }
            }
            return@forEach
        }

        inMetadata = false
        val separator = line.indexOf(':')
        if (separator <= 0) {
            return@forEach
        }

        val key = line.substring(0, separator).trim()
        val value = line.substring(separator + 1).trim().trimQuotes()
        if (key == "metadata") {
            inMetadata = true
            return@forEach
        }
        topLevel[key] = value
    }

    return ParsedFrontmatter(
        name = topLevel["name"],
        description = topLevel["description"],
        license = topLevel["license"],
        compatibility = topLevel["compatibility"],
        metadata = metadata.takeIf { it.isNotEmpty() },
        allowedTools = topLevel["allowed-tools"],
        isValidFrontmatter = true,
    )
}

private fun invalidParsedFrontmatter(): ParsedFrontmatter =
    ParsedFrontmatter(null, null, null, null, null, null, false)

private fun defaultWarningLogger(message: String) {
    logger.warn { message }
}

private fun String.trimQuotes(): String =
    trim().removeSurrounding("\"").removeSurrounding("'")

private inline fun List<String>.indexOfFirstFrom(startIndex: Int, predicate: (String) -> Boolean): Int {
    for (index in startIndex..lastIndex) {
        if (predicate(this[index])) {
            return index
        }
    }
    return -1
}

private const val DEFAULT_SKILL_FILE_NAME: String = "SKILL.md"
private val DEFAULT_SKIPPED_DIRECTORY_NAMES: Set<String> = setOf(".git", "node_modules")
private val DEFAULT_SKILL_NAME_PATTERN = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$")
