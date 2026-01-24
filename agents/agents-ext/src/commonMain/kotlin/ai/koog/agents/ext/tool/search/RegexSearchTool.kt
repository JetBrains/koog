package ai.koog.agents.ext.tool.search

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.prompt.text.text
import ai.koog.rag.base.files.DocumentProvider
import ai.koog.rag.base.files.FileMetadata
import ai.koog.rag.base.files.FileSystemProvider
import ai.koog.rag.base.files.extendRangeByLines
import ai.koog.rag.base.files.model.FileSystemEntry
import ai.koog.rag.base.files.model.buildFileSize
import ai.koog.rag.base.files.readText
import ai.koog.rag.base.files.toPosition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.io.IOException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Regular expression based content search tool.
 *
 * Use to find occurrences of a regex pattern across text files under a path.
 */
public class RegexSearchTool<Path>(
    private val fs: FileSystemProvider.ReadOnly<Path>,
) : Tool<RegexSearchTool.Args, RegexSearchTool.Result>(
    argsSerializer = Args.serializer(),
    resultSerializer = Result.serializer(),
    name = "__search_contents_by_regex__",
    description = text {
        +"Executes a regular expression search on folder or file contents within the specified path."
        +"The tool returns structured results with file paths, line numbers, positions, and excerpts where the text was found."
        +"The tool will solely return search results and does not modify any files."
    }
) {

    /**
     * Parameters for a regex content search.
     *
     * @property path Absolute start directory or file path.
     * @property regex Regex pattern to match in text files.
     * @property limit Max matching files to return (default: 25).
     * @property skip Matching files to skip (default: 0).
     * @property caseSensitive If true, case-sensitive match; otherwise ignore case.
     */
    @Serializable
    public data class Args(
        @param:LLMDescription("Absolute starting directory or file path.")
        val path: String,
        @param:LLMDescription("Regular expression pattern.")
        val regex: String,
        @param:LLMDescription("Maximum number of matching files to return (pagination).")
        val limit: Int = 25,
        @param:LLMDescription("Number of matching files to skip (pagination).")
        val skip: Int = 0,
        @SerialName("case_sensitive")
        @param:LLMDescription("If false, performs case-insensitive matching.")
        val caseSensitive: Boolean = false,
    )

    /**
     * Search output.
     *
     * @property entries Files with at least one match; each contains excerpt snippets around matches.
     * @property original The regex used for the search.
     */
    @Serializable
    public data class Result(val entries: List<FileSystemEntry.File>, val original: String)

    override suspend fun execute(args: Args): Result {
        val path = fs.fromAbsolutePathString(args.path)
        val matches = search(path, args.regex, args.limit, args.skip, args.caseSensitive).toList()
        return Result(matches, original = args.regex)
    }

    private suspend fun search(
        path: Path,
        pattern: String,
        limit: Int,
        skip: Int,
        caseSensitive: Boolean,
        linesAroundSnippet: Int = 2,
    ): Flow<FileSystemEntry.File> {
        val options = mutableSetOf<RegexOption>()
        if (!caseSensitive) options.add(RegexOption.IGNORE_CASE)

        return searchByRegex(
            fs = fs,
            start = path,
            regex = Regex(pattern, options)
        )
            .drop(skip)
            .take(limit)
            .mapNotNull { match ->
                val snippets = match.ranges.map { range ->
                    val extended = extendRangeByLines(match.content, range, linesAroundSnippet, linesAroundSnippet)
                    FileSystemEntry.File.Content.Excerpt.Snippet(
                        text = extended.substring(match.content),
                        range = extended
                    )
                }
                if (snippets.isEmpty()) return@mapNotNull null
                val metadata = fs.metadata(match.file) ?: return@mapNotNull null
                val contentType = fs.getFileContentType(match.file)
                FileSystemEntry.File(
                    name = fs.name(match.file),
                    extension = fs.extension(match.file),
                    path = fs.toAbsolutePathString(match.file),
                    hidden = metadata.hidden,
                    size = buildFileSize(fs, match.file, contentType),
                    contentType = contentType,
                    content = FileSystemEntry.File.Content.Excerpt(snippets)
                )
            }
    }

    /**
     * A match of one file and the ranges within it that matched a regex.
     */
    private data class ContentMatch<Path>(
        val file: Path,
        val content: String,
        val ranges: List<DocumentProvider.DocumentRange>
    )

    /**
     * Recursively searches starting at [start] for text files whose contents match [regex].
     * Returns a flow of [ContentMatch] where each item corresponds to a file and its matched ranges.
     */
    private fun <Path> searchByRegex(
        fs: FileSystemProvider.ReadOnly<Path>,
        start: Path,
        regex: Regex
    ): Flow<ContentMatch<Path>> = flow {
        when (fs.metadata(start)?.type) {
            FileMetadata.FileType.File -> {
                try {
                    if (fs.getFileContentType(start) != FileMetadata.FileContentType.Text) return@flow
                    val content = fs.readText(start)
                    val ranges = regex.findAll(content).map { mr ->
                        val start = mr.range.first
                        val end = mr.range.last + 1 // exclusive
                        DocumentProvider.DocumentRange(start.toPosition(content), end.toPosition(content))
                    }.toList()
                    if (ranges.isNotEmpty()) emit(ContentMatch(start, content, ranges))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: IOException) {
                    // ignore unreadable files
                }
            }
            FileMetadata.FileType.Directory -> {
                val children = try {
                    fs.list(start)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: IOException) {
                    emptyList()
                }
                for (child in children) emitAll(searchByRegex(fs, child, regex))
            }
            else -> { /* ignore */ }
        }
    }
}
