package ai.koog.agents.ext.tool.file

import ai.koog.agents.core.tools.ToolException
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.ext.tool.file.render.norm
import ai.koog.rag.base.files.JVMFileSystemProvider
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(InternalAgentToolsApi::class)
class ListDirectoryToolJvmTest {

    private val fs = JVMFileSystemProvider.ReadOnly
    private val tool = ListDirectoryTool(fs)

    @TempDir
    lateinit var tempDir: Path

    private fun createDir(name: String): Path = tempDir.resolve(name).createDirectories()

    private suspend fun list(path: Path, depth: Int = 1, filter: String? = null): ListDirectoryTool.Result =
        tool.execute(ListDirectoryTool.Args(path.toString(), depth, filter))

    @Test
    fun `Args uses correct defaults`() {
        val args = ListDirectoryTool.Args("/tmp/test")
        assertEquals("/tmp/test", args.path)
        assertEquals(1, args.depth)
        assertNull(args.filter)
    }

    @Test
    fun `descriptor is configured correctly`() {
        val descriptor = tool.descriptor
        assertEquals("__list_directory__", descriptor.name)
        assertTrue(descriptor.description.isNotEmpty())
        assertEquals(listOf("path"), descriptor.requiredParameters.map { it.name })
        assertEquals(setOf("depth", "filter"), descriptor.optionalParameters.map { it.name }.toSet())
    }

    @Test
    fun `throws ValidationFailure for non-existent path`() {
        val nonExistent = tempDir.resolve("missing")
        assertThrows<ToolException.ValidationFailure> { runBlocking { list(nonExistent) } }
    }

    @Test
    fun `throws ValidationFailure when path points to a file`() {
        val file = tempDir.resolve("f.txt").createFile().apply { writeText("x") }
        assertThrows<ToolException.ValidationFailure> { runBlocking { list(file) } }
    }

    @Test
    fun `throws ValidationFailure for invalid depth`() {
        val dir = createDir("d")
        assertThrows<ToolException.ValidationFailure> { runBlocking { list(dir, depth = 0) } }
    }

    @Test
    fun `empty directory shows only root`() = runBlocking {
        // Structure:
        // empty/ (empty directory)
        val empty = createDir("empty")

        val resultText = tool.encodeResultToString(list(empty, depth = 1))

        // Expected: /path/to/empty/
        val expectedText = "${empty.toAbsolutePath().toString().norm()}/"

        assertEquals(expectedText, resultText)
    }

    @Test
    fun `single file in directory shows collapsed file`() = runBlocking {
        // Structure:
        // project/
        // └── README.md
        val dir = createDir("project")
        val readmeFile = dir.resolve("README.md").createFile().apply { writeText("hello world") }

        val resultText = tool.encodeResultToString(list(dir, depth = 1))

        // Expected: /path/to/project/README.md (<0.1 KiB, 1 line)
        val expectedText = "${readmeFile.toAbsolutePath().toString().norm()} (<0.1 KiB, 1 line)"

        assertEquals(expectedText, resultText)
    }

    @Test
    fun `multiple files in directory shows folder with indented files`() = runBlocking {
        // Structure:
        // project/
        // ├── LICENSE.txt
        // └── README.md
        val dir = createDir("project")
        dir.resolve("README.md").createFile().writeText("hello") // 5 bytes
        dir.resolve("LICENSE.txt").createFile().writeText("MIT") // 3 bytes

        val resultText = tool.encodeResultToString(list(dir, depth = 2))

        // Expected:
        // /path/to/project/
        //   LICENSE.txt (<0.1 KiB, 1 line)
        //   README.md (<0.1 KiB, 1 line)
        val expectedText = """
            ${dir.toAbsolutePath().toString().norm()}/
              LICENSE.txt (<0.1 KiB, 1 line)
              README.md (<0.1 KiB, 1 line)
        """.trimIndent()

        assertEquals(expectedText, resultText)
    }

    @Test
    fun `single directory shows collapsed empty directory`() = runBlocking {
        // Structure:
        // root/
        // └── src/ (empty)
        val root = createDir("root")
        val srcDir = root.resolve("src").createDirectories()

        val resultText = tool.encodeResultToString(list(root, depth = 1))

        // Expected: /path/to/root/src/
        val expectedText = "${srcDir.toAbsolutePath().toString().norm()}/"

        assertEquals(expectedText, resultText)
    }

    @Test
    fun `multiple directories shows direct contents with depth 1`() = runBlocking {
        // Structure:
        // root/
        // ├── src/
        // └── test/
        val root = createDir("root")
        root.resolve("src").createDirectories()
        root.resolve("test").createDirectories()

        val resultText = tool.encodeResultToString(list(root, depth = 1))

        // Expected:
        // /path/to/root/
        //   src/
        //   test/
        val expectedText = """
            ${root.toAbsolutePath().toString().norm()}/
              src/
              test/
        """.trimIndent()

        assertEquals(expectedText, resultText)
    }

    @Test
    fun `unwrapping chain directories with depth 1 shows final file`() = runBlocking {
        // Structure:
        // project/
        // └── src/
        //     └── main/
        //         └── kotlin/
        //             └── Main.kt
        val project = createDir("project")
        val src = project.resolve("src").createDirectories()
        val main = src.resolve("main").createDirectories()
        val kotlin = main.resolve("kotlin").createDirectories()
        val mainFile = kotlin.resolve("Main.kt").createFile().apply { writeText("fun main() {}") }

        val resultText = tool.encodeResultToString(list(project, depth = 1))

        // Expected: /path/to/project/src/main/kotlin/Main.kt (<0.1 KiB, 1 line)
        val expectedText = "${mainFile.toAbsolutePath().toString().norm()} (<0.1 KiB, 1 line)"

        assertEquals(expectedText, resultText)
    }

    @Test
    fun `unwrapping shows multiple files with depth 1`() = runBlocking {
        // Structure:
        // project/
        // └── src/
        //     └── main/
        //         └── kotlin/
        //             ├── Main.kt
        //             └── Utils.kt
        val project = createDir("project")
        val src = project.resolve("src").createDirectories()
        val main = src.resolve("main").createDirectories()
        val kotlin = main.resolve("kotlin").createDirectories()
        kotlin.resolve("Main.kt").createFile().writeText("fun main() {}")
        kotlin.resolve("Utils.kt").createFile().writeText("class Utils")

        val resultText = tool.encodeResultToString(list(project, depth = 1))

        // Expected:
        // /path/to/project/src/main/kotlin/
        //   Main.kt (<0.1 KiB, 1 line)
        //   Utils.kt (<0.1 KiB, 1 line)
        val expectedText = """
            ${kotlin.toAbsolutePath().toString().norm()}/
              Main.kt (<0.1 KiB, 1 line)
              Utils.kt (<0.1 KiB, 1 line)
        """.trimIndent()
        assertEquals(expectedText, resultText)
    }

    @Test
    fun `unwrapping shows mixed files and directories`() = runBlocking {
        // Structure:
        // project/
        // └── src/
        //     └── main/
        //         └── kotlin/
        //             ├── Main.kt
        //             └── utils/
        val project = createDir("project")
        val src = project.resolve("src").createDirectories()
        val main = src.resolve("main").createDirectories()
        val kotlin = main.resolve("kotlin").createDirectories()
        kotlin.resolve("Main.kt").createFile().writeText("fun main() {}")
        kotlin.resolve("utils").createDirectories()

        val resultText = tool.encodeResultToString(list(project, depth = 1))

        // Expected:
        // /path/to/project/src/main/kotlin/
        //   Main.kt (<0.1 KiB, 1 line)
        //   utils/
        val expectedText = """
            ${kotlin.toAbsolutePath().toString().norm()}/
              Main.kt (<0.1 KiB, 1 line)
              utils/
        """.trimIndent()
        assertEquals(expectedText, resultText)
    }

    @Test
    fun `multiple entries at root level shows direct contents`() = runBlocking {
        // Structure:
        // project/
        // ├── README.md
        // └── src/
        //     └── main/
        //         └── kotlin/
        //             └── Main.kt
        //
        // Unwrap: project/ (stops - multiple root entries)
        val project = createDir("project")
        project.resolve("README.md").createFile().writeText("readme")
        val src = project.resolve("src").createDirectories()
        val main = src.resolve("main").createDirectories()
        val kotlin = main.resolve("kotlin").createDirectories()
        kotlin.resolve("Main.kt").createFile().writeText("fun main() {}")

        val resultText = tool.encodeResultToString(list(project, depth = 1))

        // Expected:
        // /path/to/project/
        //   README.md (<0.1 KiB, 1 line)
        //   src/
        val expectedText = """
            ${project.toAbsolutePath().toString().norm()}/
              README.md (<0.1 KiB, 1 line)
              src/
        """.trimIndent()

        assertEquals(expectedText, resultText)
    }

    @Test
    fun `unwrapping with multiple empty directories in chain`() = runBlocking {
        // Structure:
        // project/
        // └── a/
        //     └── b/
        //         └── c/
        //             └── d/ (empty)
        val project = createDir("project")
        val a = project.resolve("a").createDirectories()
        val b = a.resolve("b").createDirectories()
        val c = b.resolve("c").createDirectories()
        val d = c.resolve("d").createDirectories()

        val resultText = tool.encodeResultToString(list(project, depth = 1))

        // Expected: /path/to/project/a/b/c/d/
        val expectedText = "${d.toAbsolutePath().toString().norm()}/"

        assertEquals(expectedText, resultText)
    }

    @Test
    fun `unwrapping with multiple files shows them at higher depth`() = runBlocking {
        // Structure:
        // project/
        // └── src/
        //     └── main/
        //         └── kotlin/
        //             ├── Main.kt
        //             └── Utils.kt
        val project = createDir("project")
        val src = project.resolve("src").createDirectories()
        val main = src.resolve("main").createDirectories()
        val kotlin = main.resolve("kotlin").createDirectories()
        kotlin.resolve("Main.kt").createFile().writeText("fun main() {}")
        kotlin.resolve("Utils.kt").createFile().writeText("class Utils")

        val resultText = tool.encodeResultToString(list(project, depth = 2))

        // Expected:
        // /path/to/project/src/main/kotlin/
        //   Main.kt (<0.1 KiB, 1 line)
        //   Utils.kt (<0.1 KiB, 1 line)
        val expectedText = """
            ${kotlin.toAbsolutePath().toString().norm()}/
              Main.kt (<0.1 KiB, 1 line)
              Utils.kt (<0.1 KiB, 1 line)
        """.trimIndent()

        assertEquals(expectedText, resultText)
    }

    @Test
    fun `high depth shows full nested structure`() = runBlocking {
        // Structure:
        // project/
        // ├── README.md
        // └── src/
        //     └── main/
        //         └── kotlin/
        //             └── Main.kt
        val project = createDir("project")
        project.resolve("README.md").createFile().writeText("hello\nworld")
        val src = project.resolve("src").createDirectories()
        val main = src.resolve("main").createDirectories()
        val kotlin = main.resolve("kotlin").createDirectories()
        kotlin.resolve("Main.kt").createFile().writeText("fun main(){}\n")

        val resultText = tool.encodeResultToString(list(project, depth = 4))

        // Expected:
        // /path/to/project/
        //   README.md (<0.1 KiB, 2 lines)
        //   src/main/kotlin/Main.kt (<0.1 KiB, 1 line)
        val expectedText = """
            ${project.toAbsolutePath().toString().norm()}/
              README.md (<0.1 KiB, 2 lines)
              src/main/kotlin/Main.kt (<0.1 KiB, 2 lines)
        """.trimIndent()

        assertEquals(expectedText, resultText)
    }

    @Test
    fun `filter shows only matching files collapsed`() = runBlocking {
        // Structure:
        // project/
        // ├── Main.java
        // ├── Main.kt
        // └── README.md
        val dir = createDir("project")
        val mainKtFile = dir.resolve("Main.kt").createFile().apply { writeText("kotlin") }
        dir.resolve("Main.java").createFile().writeText("java")
        dir.resolve("README.md").createFile().writeText("readme")

        val resultText = tool.encodeResultToString(list(dir, depth = 1, filter = "*.kt"))

        // Expected: /path/to/project/Main.kt (<0.1 KiB, 1 line)
        val expectedText = "${mainKtFile.toAbsolutePath().toString().norm()} (<0.1 KiB, 1 line)"

        assertEquals(expectedText, resultText)
    }

    @Test
    fun `shallow filter with deeper files throws ValidationFailure`(): Unit = runBlocking {
        // Structure:
        // project/
        // └── src/
        //     └── main/
        //         └── kotlin/
        //             ├── Main.kt
        //             └── Utils.java
        val project = createDir("project")
        val src = project.resolve("src").createDirectories()
        val main = src.resolve("main").createDirectories()
        val kotlin = main.resolve("kotlin").createDirectories()
        kotlin.resolve("Main.kt").createFile().writeText("fun main() {}")
        kotlin.resolve("Utils.java").createFile().writeText("class Utils {}")

        assertThrows<ToolException.ValidationFailure> {
            list(project, depth = 2, filter = "*/*.kt")
        }
    }

    @Test
    fun `filter with multiple matching files shows indented structure`() = runBlocking {
        // Structure:
        // project/
        // └── src/
        //     ├── Main.kt
        //     ├── Test.java
        //     └── Utils.kt
        val project = createDir("project")
        val src = project.resolve("src").createDirectories()
        src.resolve("Main.kt").createFile().writeText("main")
        src.resolve("Utils.kt").createFile().writeText("utils")
        src.resolve("Test.java").createFile().writeText("test")

        val resultText = tool.encodeResultToString(list(project, depth = 2, filter = "*/*.kt"))

        // Expected:
        // /path/to/project/src/
        //   Main.kt (<0.1 KiB, 1 line)
        //   Utils.kt (<0.1 KiB, 1 line)
        val expectedText = """
            ${src.toAbsolutePath().toString().norm()}/
              Main.kt (<0.1 KiB, 1 line)
              Utils.kt (<0.1 KiB, 1 line)
        """.trimIndent()

        assertEquals(expectedText, resultText)
    }

    @Test
    fun `filter matches files by name pattern`() = runBlocking {
        // Structure:
        // project/
        // ├── src/
        // │   └── Main.kt
        // └── test/
        //     ├── TestMain.kt  # This will match "Test*" pattern
        //     └── helper.kt
        val project = createDir("project")
        val testDir = project.resolve("test").createDirectories()
        val testMainFile = testDir.resolve("TestMain.kt").createFile().apply { writeText("test") }
        testDir.resolve("helper.kt").createFile().writeText("helper")
        val srcDir = project.resolve("src").createDirectories()
        srcDir.resolve("Main.kt").createFile().writeText("main")

        val resultText = tool.encodeResultToString(list(project, depth = 2, filter = "*/Test*"))

        // Expected: /path/to/project/test/TestMain.kt (<0.1 KiB, 1 line)
        val expectedText = "${testMainFile.toAbsolutePath().toString().norm()} (<0.1 KiB, 1 line)"

        assertEquals(expectedText, resultText)
    }

    @Test
    fun `complex nested structure with mixed content`() = runBlocking {
        // Structure:
        // project/
        // ├── README.md
        // └── src/
        //     ├── main/
        //     │   └── kotlin/
        //     │       └── com/
        //     │           └── example/
        //     │               ├── Main.kt
        //     │               └── Utils.kt
        //     └── test/
        //         └── TestUtils.kt
        val project = createDir("project")
        project.resolve("README.md").createFile().writeText("project readme") // 14 bytes

        val src = project.resolve("src").createDirectories()
        val main = src.resolve("main").createDirectories()
        val kotlin = main.resolve("kotlin").createDirectories()
        val com = kotlin.resolve("com").createDirectories()
        val example = com.resolve("example").createDirectories()
        example.resolve("Main.kt").createFile().writeText("fun main() {}") // 13 bytes
        example.resolve("Utils.kt").createFile().writeText("class Utils") // 11 bytes

        val test = src.resolve("test").createDirectories()
        test.resolve("TestUtils.kt").createFile().writeText("test") // 4 bytes

        val resultText = tool.encodeResultToString(list(project, depth = 3))

        // Expected:
        // /path/to/project/
        //   README.md (<0.1 KiB, 1 line)
        //   src/
        //     main/kotlin/com/example/
        //       Main.kt (<0.1 KiB, 1 line)
        //       Utils.kt (<0.1 KiB, 1 line)
        //     test/TestUtils.kt (<0.1 KiB, 1 line)
        val expectedText = """
            ${project.toAbsolutePath().toString().norm()}/
              README.md (<0.1 KiB, 1 line)
              src/
                main/kotlin/com/example/
                  Main.kt (<0.1 KiB, 1 line)
                  Utils.kt (<0.1 KiB, 1 line)
                test/TestUtils.kt (<0.1 KiB, 1 line)
        """.trimIndent()

        assertEquals(expectedText, resultText)
    }

    @Test
    fun `unwrapping shows branches at first branching point`() = runBlocking {
        // Structure:
        // project/
        // └── a/
        //     └── b/
        //         ├── c1/
        //         └── c2/
        val project = createDir("project")
        val a = project.resolve("a").createDirectories()
        val b = a.resolve("b").createDirectories()
        b.resolve("c1").createDirectories()
        b.resolve("c2").createDirectories()

        val resultText = tool.encodeResultToString(list(project, depth = 1))

        // Expected:
        // /path/to/project/a/b/
        //   c1/
        //   c2/
        val expectedText = """
            ${b.toAbsolutePath().toString().norm()}/
              c1/
              c2/
        """.trimIndent()

        assertEquals(expectedText, resultText)
    }

    @Test
    fun `list shows direct children`() = runBlocking {
        // Structure:
        // project/
        // ├── LICENSE.txt
        // └── README.md
        val dir = createDir("project-depth1")
        dir.resolve("README.md").createFile().writeText("hello") // 1 line
        dir.resolve("LICENSE.txt").createFile().writeText("MIT") // 1 line

        val resultText = tool.encodeResultToString(list(dir, depth = 1))

        // Expected:
        // /path/to/project-depth1/
        //   LICENSE.txt (<0.1 KiB, 1 line)
        //   README.md (<0.1 KiB, 1 line)
        val expectedText = """
            ${dir.toAbsolutePath().toString().norm()}/
              LICENSE.txt (<0.1 KiB, 1 line)
              README.md (<0.1 KiB, 1 line)
        """.trimIndent()

        assertEquals(expectedText, resultText)
    }
}
