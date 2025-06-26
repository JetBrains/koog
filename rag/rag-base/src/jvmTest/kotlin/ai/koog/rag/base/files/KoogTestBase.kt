package ai.koog.rag.base.files

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.file.Path
import kotlin.io.path.*

open class KoogTestBase {
    @TempDir
    lateinit var tempDir: Path

    /*
    ├── dir1 (module1)
    │   └── src(1)
    │       ├── resources
    │       │   ├── resource1
    │       │   ├── resource1xml
    │       │   ├── zip1
    │       │   └── image1
    │       └── file1
    ├── dir2 (module2)
    │   └── src(2)
    │       ├── dir3
    │       │   └── src(3)
    │       │       └── file3
    │       └── file2
    ├── dirExl (moduleExl)
    │   └── src(Exl)
    │       └── fileExl
    └── dirEmpty
    */

    lateinit var dir1: Path
    lateinit var src1: Path
    lateinit var resources: Path
    lateinit var dir2: Path
    lateinit var src2: Path
    lateinit var dir3: Path
    lateinit var src3: Path
    lateinit var dirExl: Path
    lateinit var srcExl: Path
    lateinit var dirEmpty: Path
    lateinit var file1: Path
    lateinit var file2: Path
    lateinit var file3: Path
    lateinit var fileExl: Path
    lateinit var resource1: Path
    lateinit var resource1xml: Path
    lateinit var zip1: Path
    lateinit var image1: Path


    val testCode = loadTextFromResource("/testCode.kt")
    private val testCode2 = loadTextFromResource("files/testCode2.kt")

    @BeforeEach
    fun setup() {
        dir1 = tempDir.resolve("dir1").apply { this.createDirectories() }
        src1 = dir1.resolve("src").apply { this.createDirectories() }
        resources = src1.resolve("resources").apply { this.createDirectories() }

        dir2 = tempDir.resolve("dir2").apply { this.createDirectories() }
        src2 = dir2.resolve("src").apply { this.createDirectories() }
        dir3 = src2.resolve("dir3").apply { this.createDirectories() }
        src3 = dir3.resolve("src").apply { this.createDirectories() }

        dirExl = tempDir.resolve("dirExl").apply { this.createDirectories() }
        srcExl = dirExl.resolve("src").apply { this.createDirectories() }
        dirEmpty = tempDir.resolve("dirEmpty").apply { this.createDirectories() }

        file1 = src1.resolve("TestGenerator.kt").apply { this.createFile() }
        file2 = src2.resolve("Dummy.kt").apply { this.createFile() }
        file3 = src3.resolve("TestGenerator").apply { this.createFile() }
        fileExl = srcExl.resolve("Dummy").apply { this.createFile() }
        resource1 = resources.resolve("resource1").apply { this.createFile() }
        resource1xml = resources.resolve("resource1.xml").apply { this.createFile() }
        zip1 = resources.resolve("testArchive.zip").apply { this.createFile() }
        image1 = resources.resolve("testImage.jpg").apply { this.createFile() }

        // Load binary resources with better error handling
        val testZipBytes = loadBinaryFromResource("files/testArchive.zip")
        val testImageBytes = loadBinaryFromResource("files/testImage.jpg")

        zip1.writeBytes(testZipBytes)
        image1.writeBytes(testImageBytes)

        file1.writeText(testCode)
        file2.writeText(testCode2)
        file3.writeText(testCode)
        fileExl.writeText(testCode2)
        resource1.writeText(testCode)
        resource1xml.writeText(testCode)
    }

    private fun loadTextFromResource(resourcePath: String): String {
        val inputStream: InputStream? = javaClass.classLoader.getResourceAsStream(resourcePath)
        return inputStream?.bufferedReader(Charset.defaultCharset())?.use { it.readText() }
            ?: "// Placeholder content for $resourcePath"
    }

    private fun loadBinaryFromResource(resourcePath: String): ByteArray {
        val inputStream: InputStream? = javaClass.classLoader.getResourceAsStream(resourcePath)
        return inputStream?.use { it.readAllBytes() }
            ?: ByteArray(10) // Return empty byte array if resource not found
    }

    fun getRoot(path: Path): Path {
        var current = path.absolute()
        while (current.parent != null) {
            current = current.parent
        }
        return current
    }

    fun String.normalizeForAssertion(): String {
        return this.replace("\r\n", "\n").replace("\r", "\n")
    }
}
