package ai.koog.agents.ext.tool.search

import ai.koog.agents.ext.tool.file.model.FileSystemEntry
import ai.koog.rag.base.files.FileSystemProvider
import ai.koog.rag.base.files.JVMFileSystemProvider
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RegexSearchToolTest {
    private fun buildFsWithSampleProject(): Pair<FileSystemProvider.ReadOnly<Path>, Path> {
        val tempDir = Files.createTempDirectory("regex-search-tool-test").toAbsolutePath()
        val fs: FileSystemProvider.ReadOnly<Path> = JVMFileSystemProvider.ReadOnly

        // project layout
        val srcMainKotlin = tempDir.resolve("src/main/kotlin").createDirectories()
        val srcMainJava = tempDir.resolve("src/main/java").createDirectories()
        val srcTestKotlin = tempDir.resolve("src/test/kotlin").createDirectories()
        val docs = tempDir.resolve("docs").createDirectories()

        // kotlin files
        srcMainKotlin.resolve("File1.kt").writeText("fun main() { println(\"Hello, World!\") }")
        srcMainKotlin.resolve("File2.kt").writeText("class User(val name: String, val age: Int)")

        // java files
        srcMainJava.resolve("File1.java")
            .writeText("public class File1 { public static void main(String[] args) { System.out.println(\"Hello, Java!\"); } }")
        srcMainJava.resolve("File2.java").writeText("public class User { private String name; private int age; }")

        // test files
        srcTestKotlin.resolve("Test1.kt").writeText("fun testFunction() { assertEquals(expected, actual) }")
        srcTestKotlin.resolve("TestUtils.kt").writeText("fun assertSomething() { assertTrue(condition) }")

        // docs
        docs.resolve("readme.txt").writeText("This is a sample project with Kotlin and Java files.")
        docs.resolve("api.txt").writeText("API Documentation: Use the User class to create user instances.")
        docs.resolve("multiline.txt").writeText(
            """line 0 This is a multiline string.
            |Line 1 has some content.
            |Line 2 has different content.
            |Line 3 ends with a number: 42
            |Line 4 starts with a number: 100 and continues.
            |Line 5 is also there.
            |Line 6 is the last line.
            """.trimMargin()
        )

        return fs to tempDir
    }

    private fun String.norm(): String = replace('\\', '/')

    private fun List<String>.containsSuffix(suffix: String): Boolean = any { it.norm().endsWith(suffix) }

    private fun List<String>.containsAllSuffixes(vararg suffixes: String): Boolean = suffixes.all { containsSuffix(it) }

    private fun List<String>.containsNoneOfSuffixes(vararg suffixes: String): Boolean =
        suffixes.none { containsSuffix(it) }

    @Test
    fun testRegexSearchBasic() = runBlocking {
        val (fs, root) = buildFsWithSampleProject()
        val tool = RegexSearchTool(fs)

        val result = tool.execute(
            RegexSearchTool.Args(
                path = fs.toAbsolutePathString(root),
                regex = "class\\s+User",
                limit = 10,
                skip = 0,
                caseSensitive = true
            )
        )

        assertEquals("class\\s+User", result.original)
        val paths = result.entries.map { it.path }
        assertTrue(paths.containsAllSuffixes("/src/main/java/File2.java", "/src/main/kotlin/File2.kt"))
    }

    @Test
    fun testRegexSearchLimit() = runBlocking {
        val (fs, root) = buildFsWithSampleProject()
        val tool = RegexSearchTool(fs)

        val result = tool.execute(
            RegexSearchTool.Args(
                path = fs.toAbsolutePathString(root),
                regex = "class",
                limit = 1,
                skip = 0,
                caseSensitive = true
            )
        )

        assertEquals(1, result.entries.size)
    }

    @Test
    fun testRegexSearchNoResults() = runBlocking {
        val (fs, root) = buildFsWithSampleProject()
        val tool = RegexSearchTool(fs)

        val result = tool.execute(
            RegexSearchTool.Args(
                path = fs.toAbsolutePathString(root),
                regex = "non-existent-pattern",
                limit = 10,
                skip = 0,
                caseSensitive = true
            )
        )

        assertTrue(result.entries.isEmpty())
    }

    @Test
    fun testRegexSearchCaseInsensitive() = runBlocking {
        val (fs, root) = buildFsWithSampleProject()
        val tool = RegexSearchTool(fs)

        val result = tool.execute(
            RegexSearchTool.Args(
                path = fs.toAbsolutePathString(root),
                regex = "CLASS",
                limit = 10,
                skip = 0,
                caseSensitive = false
            )
        )

        val paths = result.entries.map { it.path }
        assertTrue(
            paths.containsAllSuffixes(
                "/src/main/kotlin/File2.kt",
                "/src/main/java/File1.java",
                "/src/main/java/File2.java"
            )
        )
    }

    @Test
    fun testRegexSearchSpecificDirectory() = runBlocking {
        val (fs, root) = buildFsWithSampleProject()
        val tool = RegexSearchTool(fs)

        val result = tool.execute(
            RegexSearchTool.Args(
                path = root.resolve("src/main/kotlin").toString(),
                regex = "fun|class",
                limit = 10,
                skip = 0,
                caseSensitive = true
            )
        )

        val paths = result.entries.map { it.path }
        assertTrue(
            paths.containsAllSuffixes(
                "/src/main/kotlin/File1.kt",
                "/src/main/kotlin/File2.kt"
            )
        )
        assertTrue(
            paths.containsNoneOfSuffixes(
                "/src/main/java/File1.java",
                "/src/main/java/File2.java"
            )
        )
    }

    @Test
    fun testRegexSearchComplexPattern() = runBlocking {
        val (fs, root) = buildFsWithSampleProject()
        val tool = RegexSearchTool(fs)

        val result = tool.execute(
            RegexSearchTool.Args(
                path = fs.toAbsolutePathString(root),
                regex = "assert\\w+\\(",
                limit = 10,
                skip = 0,
                caseSensitive = true
            )
        )

        val paths = result.entries.map { it.path }
        assertTrue(
            paths.containsAllSuffixes(
                "/src/test/kotlin/Test1.kt",
                "/src/test/kotlin/TestUtils.kt"
            )
        )
    }

    @Test
    fun testRegexSearchMultilineMatches() = runBlocking {
        val (fs, root) = buildFsWithSampleProject()
        val tool = RegexSearchTool(fs)

        val result = tool.execute(
            RegexSearchTool.Args(
                path = root.resolve("docs").toString(),
                regex = "Line.*?\\d+.*?\\n.*?Line",
                limit = 10,
                skip = 0,
                caseSensitive = true
            )
        )

        val entry = result.entries.firstOrNull { it.path.norm().endsWith("/docs/multiline.txt") }
        assertTrue(entry != null, "docs/multiline.txt should be in results")
        val content = entry.content
        assertTrue(content is FileSystemEntry.File.Content.Excerpt)
        val hasContext = content.snippets.all { snippet ->
            val t = snippet.text
            t.contains("Line 3 ends with a number: 42") && t.contains("Line 4 starts with a number: 100 and continues.")
        }
        assertTrue(hasContext)
    }
}
