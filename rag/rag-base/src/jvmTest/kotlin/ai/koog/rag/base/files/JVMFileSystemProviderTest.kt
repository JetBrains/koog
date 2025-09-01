package ai.koog.rag.base.files

import kotlinx.coroutines.runBlocking
import kotlinx.io.readString
import kotlinx.io.writeString
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.io.File
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.createDirectory
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JVMFileSystemProviderTest : KoogTestBase() {
    private val readOnly = JVMFileSystemProvider.ReadOnly
    private val readWrite = JVMFileSystemProvider.ReadWrite

    @Test
    fun `test fromAbsolutePathString with non-existent path`() {
        val nonExistentPath = file1.absolute().pathString + "non_existent"
        val result = readOnly.fromAbsolutePathString(nonExistentPath).toString()
        assertEquals(nonExistentPath, result)
    }

    @Test
    fun `test parent with non-existing path`() = runBlocking {
        val testParent = readOnly.parent(Path.of("non-existing-path"))
        assertNull(testParent)
    }

    @Test
    fun `test relative with no common prefix`() = runBlocking {
        val target = src1
        val targetParent = Path.of("non-existing-path")
        val testPath = readOnly.relativize(targetParent, target)
        assertNull(testPath)
    }

    @Test
    fun `test extension empty`() = runBlocking {
        val testExtension = readOnly.extension(file3)
        assertEquals("", testExtension)
    }

    @Test
    fun `test extension dir`() = runBlocking {
        val testExtension = readOnly.extension(dir1)
        assertEquals("", testExtension)
    }

    @Test
    fun `test name no extension`() = runBlocking {
        val testName = readOnly.name(fileExl)
        assertEquals("Dummy", testName)
    }

    @Test
    fun `test name dir`() = runBlocking {
        val testName = readOnly.name(dir1)
        assertEquals("dir1", testName)
    }

    @Test
    fun `test name no file`() = runBlocking {
        val testName = readOnly.name(Path.of(file1.pathString + "fake"))
        assertEquals("TestGenerator.ktfake", testName)
    }

    @Test
    fun `test name file delimiter at the end`() = runBlocking {
        val testName = readOnly.name(Path.of(file1.pathString + FileSystems.getDefault().separator))
        assertEquals("TestGenerator.kt", testName)
    }

    @Test
    fun `test name dir delimiter at the end`() = runBlocking {
        val testName = readOnly.name(Path.of(dir1.pathString + FileSystems.getDefault().separator))
        assertEquals("dir1", testName)
    }

    @Test
    fun `test readBytes dir`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { readOnly.readBytes(dir2) }
        }
    }

    @Test
    fun `test readBytes not exist`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { readOnly.readBytes(Path.of(file1.pathString + "fake")) }
        }
    }

    @Test
    fun `test size dir`() {
        assertThrows(Exception::class.java) {
            runBlocking { readOnly.size(dir2) }
        }
    }

    @Test
    fun `test size empty dir`() {
        assertThrows(Exception::class.java) {
            runBlocking { readOnly.size(dirEmpty) }
        }
    }

    @Test
    fun `test size not exist`() {
        assertThrows(Exception::class.java) {
            runBlocking {
                readOnly.size(Path.of(file1.pathString + "fake"))
            }
        }
    }

    //region JVMFileSystemProvider.ReadOnly
    @Test
    fun `test ReadOnly toAbsolutePathString`() {
        val filePathString = file1.absolute().pathString
        val testPathString = readOnly.toAbsolutePathString(file1)
        assertEquals(filePathString, testPathString)
    }

    @Test
    fun `test ReadOnly fromAbsolutePathString`() {
        val filePathString = file1.absolute().pathString
        val fileFromPath = readOnly.fromAbsolutePathString(filePathString)
        assertEquals(file1, fileFromPath)
    }

    @Test
    fun `test ReadOnly joinPath`() {
        val fileFullPath = readOnly.joinPath(src1, file1.name)
        assertEquals(file1, fileFullPath)
    }

    @Test
    fun `test ReadOnly joinPath with absolute path`() {
        val absolutePath = file1.absolute().pathString
        assertThrows(IllegalArgumentException::class.java) {
            readOnly.joinPath(file3, absolutePath)
        }
    }

    @Test
    fun `test ReadOnly joinPath with absolute path in the middle`() {
        val absolutePath = file1.absolute().pathString
        assertThrows(IllegalArgumentException::class.java) {
            readOnly.joinPath(file3, "abc", absolutePath, "def")
        }
    }

    @Test
    fun `test ReadOnly name`() = runBlocking {
        val testName = readOnly.name(file1)
        assertEquals("TestGenerator.kt", testName)
    }

    @Test
    fun `test ReadOnly extension`() = runBlocking {
        val testExtension = readOnly.extension(file1)
        assertEquals("kt", testExtension)
    }

    @Test
    fun `test ReadOnly metadata`() = runBlocking {
        val metadata = FileMetadata(FileMetadata.FileType.File, hidden = false)
        val testMetadata = readOnly.metadata(file1)
        assertEquals(metadata, testMetadata)
    }

    @Test
    fun `test ReadOnly list`() = runBlocking {
        val testList = readOnly.list(resources)
        val children = listOf(resource1, resource1xml, zip1, image1).sortedBy { it.name }
        assertEquals(children, testList)
    }

    @Test
    fun `test ReadOnly parent`() = runBlocking {
        val testParent = readOnly.parent(file1)
        assertEquals(src1, testParent)
    }

    @Test
    fun `test ReadOnly relativize`() = runBlocking {
        val target = resource1
        val targetParent = src1
        val expectedRelativePath =
            target.pathString.substringAfter(targetParent.pathString + FileSystems.getDefault().separator)
        val testPath = readOnly.relativize(targetParent, target)
        assertEquals(expectedRelativePath, testPath)
    }

    @Test
    fun `test ReadOnly exists`() = runBlocking {
        assertTrue(readOnly.exists(file1))
        assertFalse(readOnly.exists(Path.of(file1.pathString + "fake")))
    }

    @Test
    fun `test ReadOnly readBytes`() = runBlocking {
        val content = String(readOnly.readBytes(file1))
        assertEquals(testCode, content)
    }

    @Test
    fun `test ReadOnly readText`() = runBlocking {
        val content = readOnly.readText(file1)
        assertEquals(testCode, content)
    }

    @Test
    fun `test ReadOnly inputStream`() = runBlocking {
        val actualContent = readOnly.inputStream(file1).use { inputStream ->
            inputStream.readString()
        }
        assertEquals(testCode, actualContent)
    }

    @Test
    fun `test ReadOnly inputStream with non-existing file`() {
        assertThrows<IllegalArgumentException> {
            runBlocking {
                readOnly.inputStream(Path.of(file1.pathString + "fake")).use { inputStream ->
                    inputStream.readString()
                }
            }
        }
    }

    @Test
    fun `test ReadOnly inputStream with directory`() {
        assertThrows<IllegalArgumentException> {
            runBlocking {
                readOnly.inputStream(dir2).use { inputStream ->
                    inputStream.readString()
                }
            }
        }
    }

    @Test
    fun `test ReadOnly size`() = runBlocking {
        val size = readOnly.size(file1)
        assertTrue(size > 0) { "Expected size more than a zero, but was $size" }
    }

    @Test
    fun `test ReadOnly path windows delimiters`() {
        val filePathString = file1.absolute().pathString.replace("/", "\\")
        val fileFromString = readOnly.fromAbsolutePathString(filePathString)
        assertEquals(file1, fileFromString)
    }

    @Test
    fun `test ReadOnly path unix delimiters`() {
        val filePathString = file1.absolute().pathString.replace("\\", "/")
        val fileFromString = readOnly.fromAbsolutePathString(filePathString)
        assertEquals(file1, fileFromString)
    }

    @Test
    fun `test ReadOnly toAbsolutePathString with non-existent path`() {
        val nonExistentPath = Path.of(file1.absolute().pathString + "non_existent")
        val result = readOnly.toAbsolutePathString(nonExistentPath)
        assertEquals(nonExistentPath.toString(), result)
    }

    @Test
    fun `test ReadOnly fromAbsolutePathString throws exception when resolved path is not absolute`() {
        val relativePathString = "relative/test/path"
        assertThrows(IllegalArgumentException::class.java) {
            readOnly.fromAbsolutePathString(relativePathString)
        }
    }

    @Test
    fun `test ReadOnly joinPath to parent dir`() {
        val dirPath = readOnly.joinPath(file3, "..${FileSystems.getDefault().separator}")
        assertEquals(src3, dirPath)
    }

    @Test
    fun `test ReadOnly joinPath with non-existent path`() {
        val nonExistentRelativePath = "non_existent_folder/non_existent_file.txt"
        val result = readOnly.joinPath(src1, nonExistentRelativePath)
        assertTrue(result.pathString.contains("non_existent_folder"))
        assertTrue(result.pathString.contains("non_existent_file.txt"))
    }

    @Test
    fun `test ReadOnly path starting with slash windows`() {
        val filePathString = "\\" + file1.absolute().pathString.replace("/", "\\")
        val fileFromString = readOnly.fromAbsolutePathString(filePathString)
        assertEquals(file1, fileFromString)
    }

    @Test
    fun `test ReadOnly list empty dir`() = runBlocking {
        val testList = readOnly.list(dirEmpty)
        assertEquals(emptyList<Path>(), testList)
    }

    @Test
    fun `test ReadOnly list is not recursive`() = runBlocking {
        val testList = readOnly.list(dir1)
        assertEquals(listOf(src1), testList)
    }

    @Test
    fun `test ReadOnly list fake dir`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                readOnly.list(Path.of(dir1.pathString + "fake"))
            }
        }
    }

    @Test
    fun `test ReadOnly list text file`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { readOnly.list(file1.absolute()) }
        }
    }

    @Test
    fun `test ReadOnly list zip`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { readOnly.list(zip1.absolute()) }
        }
    }

    @Test
    fun `test metadata file`() = runBlocking {
        val expectedMetadata = FileMetadata(FileMetadata.FileType.File, hidden = false)
        val actualMetadata = readOnly.metadata(file1)
        assertEquals(expectedMetadata, actualMetadata)
    }

    @Test
    fun `test getFileContentType with Text type`() = runBlocking {
        val contentType = readOnly.getFileContentType(file1)
        assertEquals(FileMetadata.FileContentType.Text, contentType)
    }

    @Test
    fun `test getFileContentType with directory throws IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> {
            runBlocking { readOnly.getFileContentType(dirEmpty) }
        }
    }

    @Test
    fun `test getFileContentType with Binary type`() = runBlocking {
        val contentType = readOnly.getFileContentType(zip1)
        assertEquals(FileMetadata.FileContentType.Binary, contentType)
    }

    @Test
    fun `test metadata dir`() = runBlocking {
        val metadata = FileMetadata(
            FileMetadata.FileType.Directory,
            hidden = false,
        )
        val testMetadata = readOnly.metadata(dir1)
        assertEquals(metadata, testMetadata)
    }

    @Test
    fun `test ReadOnly metadata file not exist`() = runBlocking {
        val testMetadata = readOnly.metadata(Path.of(file1.pathString + "fake"))
        assertEquals(null, testMetadata)
    }

    @Test
    fun `test ReadOnly parent root`() = runBlocking {
        val testParent = readOnly.parent(tempDir)
        assertEquals(tempDir.parent.pathString, testParent.toString())
    }

    //endregion

    //region JVMFileSystemProvider.ReadWrite
    @Test
    fun `test ReadWrite toAbsolutePathString`() {
        val filePathString = file1.absolute().pathString
        val testPathString = readWrite.toAbsolutePathString(file1)
        assertEquals(filePathString, testPathString)
    }

    @Test
    fun `test ReadWrite fromAbsolutePathString`() {
        val filePathString = file1.absolute().pathString
        val fileFromPath = readWrite.fromAbsolutePathString(filePathString)
        assertEquals(file1, fileFromPath)
    }

    @Test
    fun `test ReadWrite fromRelativeString`() {
        val fileFullPath = readWrite.joinPath(src1, file1.name)
        assertEquals(file1, fileFullPath)
    }

    @Test
    fun `test ReadWrite fromRelativeString with absolute path`() {
        val absolutePath = file1.absolute().pathString
        assertThrows(IllegalArgumentException::class.java) {
            readWrite.joinPath(file3, absolutePath)
        }
    }

    @Test
    fun `test ReadWrite name`() = runBlocking {
        val testName = readWrite.name(file1)
        assertEquals("TestGenerator.kt", testName)
    }

    @Test
    fun `test ReadWrite extension`() = runBlocking {
        val testExtension = readWrite.extension(file1)
        assertEquals("kt", testExtension)
    }

    @Test
    fun `test ReadWrite metadata`() = runBlocking {
        val expectedMetadata = FileMetadata(FileMetadata.FileType.File, hidden = false)
        val testMetadata = readWrite.metadata(file1)
        assertEquals(expectedMetadata, testMetadata)
    }

    @Test
    fun `test ReadWrite list`() = runBlocking {
        val testList = readWrite.list(resources)
        val children = listOf(resource1, resource1xml, zip1, image1).sortedBy { it.name }
        assertEquals(children, testList)
    }

    @Test
    fun `test ReadWrite parent`() = runBlocking {
        val testParent = readWrite.parent(file1)
        assertEquals(src1, testParent)
    }

    @Test
    fun `test ReadWrite relativize`() = runBlocking {
        val target = resource1
        val targetParent = src1
        val expectedRelativePath =
            target.pathString.substringAfter(targetParent.pathString + FileSystems.getDefault().separator)
        val testPath = readWrite.relativize(targetParent, target)
        assertEquals(expectedRelativePath, testPath)
    }

    @Test
    fun `test ReadWrite exists`() = runBlocking {
        assertTrue(readWrite.exists(file1))
        assertFalse(readWrite.exists(Path.of(file1.pathString + "fake")))
    }

    @Test
    fun `test ReadWrite readBytes`() = runBlocking {
        val content = String(readWrite.readBytes(file1))
        assertEquals(testCode, content)
    }

    @Test
    fun `test ReadWrite readText`() = runBlocking {
        val content = readWrite.readText(file1)
        assertEquals(testCode, content)
    }

    @Test
    fun `test ReadWrite inputStream`() = runBlocking {
        val actualContent = readWrite.inputStream(file1).use { inputStream ->
            inputStream.readString()
        }
        assertEquals(testCode, actualContent)
    }

    @Test
    fun `test ReadWrite inputStream with non-existing file`() {
        assertThrows<IllegalArgumentException> {
            runBlocking {
                readWrite.inputStream(Path.of(file1.pathString + "fake")).use { inputStream ->
                    inputStream.readString()
                }
            }
        }
    }

    @Test
    fun `test ReadWrite inputStream with directory`() {
        assertThrows<IllegalArgumentException> {
            runBlocking {
                readWrite.inputStream(dir2).use { inputStream ->
                    inputStream.readString()
                }
            }
        }
    }

    @Test
    fun `test ReadWrite size`() = runBlocking {
        val size = readWrite.size(file1)
        assertTrue(size > 0) { "Expected size more than a zero, but was $size" }
    }

    @Test
    fun `test ReadWrite write method overwrites existing file content`() = runBlocking {
        val dirPath = dirEmpty
        val fileName = "fileToOverwrite.txt"
        val filePath = Path.of(dirPath.pathString + FileSystems.getDefault().separator + fileName)

        val initialContent = "Initial content"
        filePath.writeText(initialContent)
        assertEquals(initialContent, filePath.readText())

        val newContent = "New content"
        readWrite.writeText(filePath, newContent)

        val actualContent = filePath.readText()
        assertEquals(newContent, actualContent)
        assertFalse(actualContent.contains(initialContent), "File contents should be overwritten")
    }

    @Test
    fun `test ReadWrite move throws IOException when source file doesn't exist`() {
        val sourcePath = dirEmpty.resolve("non-existing-file.txt")
        val targetPath = dirEmpty.resolve("target-path")

        assertThrows(IOException::class.java) {
            runBlocking {
                readWrite.move(sourcePath, targetPath)
            }
        }
    }

    @Test
    fun `test ReadWrite move throws FileAlreadyExistsException when target file already exists`() {
        val sourcePath = dirEmpty.resolve("source-file.txt").apply {
            createFile()
            writeText("source content")
        }
        val targetPath = dirEmpty.resolve("target-file.txt").apply {
            createFile()
            writeText("target content")
        }

        assertTrue(sourcePath.exists())
        assertTrue(targetPath.exists())

        assertThrows(FileAlreadyExistsException::class.java) {
            runBlocking {
                readWrite.move(sourcePath, targetPath)
            }
        }
    }

    @Test
    fun `test ReadWrite move file`() = runBlocking {
        val dirPath = dirEmpty
        val sourceFileName = "sourceFileReadWrite.txt"
        val targetFileName = "targetFileReadWrite.txt"
        val sourcePath = Path.of(dirPath.pathString + FileSystems.getDefault().separator + sourceFileName)
        val targetPath = Path.of(dirPath.pathString + FileSystems.getDefault().separator + targetFileName)

        sourcePath.createFile()
        val testContent = "Test content for move operation"
        sourcePath.writeText(testContent)

        assertTrue(sourcePath.exists())
        assertFalse(targetPath.exists())

        readWrite.move(sourcePath, targetPath)

        assertFalse(sourcePath.exists())
        assertTrue(targetPath.exists())
        assertEquals(testContent, targetPath.readText())
    }

    @Test
    fun `test ReadWrite move dir`(): Unit = runBlocking {
        // dirEmpty -> dirA -> fileA;dirB -> fileB
        // and then
        // dirEmpty -> dirC -> dirA -> fileA;dirB -> fileB

        val dirA = readWrite.joinPath(dirEmpty, "dirA")

        val fileA = readWrite.joinPath(dirA, "fileA")
        readWrite.createFile(fileA)
        readWrite.writeText(fileA, "fileA content")

        val dirB = readWrite.joinPath(dirA, "dirB")

        val fileB = readWrite.joinPath(dirB, "fileB")
        readWrite.createFile(fileB)
        readWrite.writeText(fileB, "fileB content")

        val targetPath = readWrite.joinPath(dirEmpty, "dirC", "dirA")
        readWrite.move(dirA, targetPath)

        readWrite.list(dirEmpty).let { children ->
            assertEquals(1, children.size)
            assertNotNull(children.find { it.name == "dirC" }) { "Expected dirC to exist" }
        }

        readWrite.list(targetPath).let { children ->
            assertEquals(2, children.size)
            assertNotNull(children.find { it.name == "fileA" }) { "Expected fileA to exist" }
            assertNotNull(children.find { it.name == "dirB" }) { "Expected dirB to exist" }
        }

        val newFileA = readWrite.joinPath(targetPath, "fileA")
        assertEquals("fileA content", newFileA.readText())

        val newDirB = readWrite.joinPath(targetPath, "dirB")
        readWrite.list(newDirB).let { children ->
            assertEquals(1, children.size)
            assertNotNull(children.find { it.name == "fileB" }) { "Expected fileB to exist" }
        }

        assertEquals("fileB content", readWrite.joinPath(newDirB, "fileB").readText())
    }

    @Test
    fun `test ReadWrite copy throws IOException when source file doesn't exist`() {
        val sourcePath = dirEmpty.resolve("non-existing-file.txt")
        val targetPath = dirEmpty.resolve("target-path")

        assertThrows(IOException::class.java) {
            runBlocking {
                readWrite.copy(sourcePath, targetPath)
            }
        }
    }

    @Test
    fun `test ReadWrite copy throws FileAlreadyExistsException when target file already exists`() {
        val sourcePath = dirEmpty.resolve("source-file.txt").apply {
            createFile()
            writeText("source content")
        }
        val targetPath = dirEmpty.resolve("target-file.txt").apply {
            createFile()
            writeText("target content")
        }

        assertTrue(sourcePath.exists())
        assertTrue(targetPath.exists())

        assertThrows(FileAlreadyExistsException::class.java) {
            runBlocking {
                readWrite.copy(sourcePath, targetPath)
            }
        }
    }

    @Test
    fun `test ReadWrite copy file`() = runBlocking {
        val dirPath = dirEmpty
        val sourceFileName = "sourceFileReadWrite.txt"
        val targetFileName = "targetFileReadWrite.txt"
        val sourcePath = Path.of(dirPath.pathString + FileSystems.getDefault().separator + sourceFileName)
        val targetPath = Path.of(dirPath.pathString + FileSystems.getDefault().separator + targetFileName)

        sourcePath.createFile()
        val testContent = "Test content for copy operation"
        sourcePath.writeText(testContent)

        assertTrue(sourcePath.exists())
        assertFalse(targetPath.exists())

        readWrite.copy(sourcePath, targetPath)

        assertTrue(sourcePath.exists())
        assertTrue(targetPath.exists())
        assertEquals(testContent, targetPath.readText())
    }

    @Test
    fun `test ReadWrite copy dir`(): Unit = runBlocking {
        // dirEmpty -> dirA -> fileA;dirB -> fileB
        // and then additionally
        // dirEmpty -> dirC -> dirA -> fileA;dirB -> fileB

        val dirA = readWrite.joinPath(dirEmpty, "dirA")

        val fileA = readWrite.joinPath(dirA, "fileA")
        readWrite.createFile(fileA)
        readWrite.writeText(fileA, "fileA content")

        val dirB = readWrite.joinPath(dirA, "dirB")

        val fileB = readWrite.joinPath(dirB, "fileB")
        readWrite.createFile(fileB)
        readWrite.writeText(fileB, "fileB content")

        val targetPath = readWrite.joinPath(dirEmpty, "dirC", "dirA")
        readWrite.copy(dirA, targetPath)

        readWrite.list(dirEmpty).let { children ->
            assertEquals(2, children.size)
            assertNotNull(children.find { it.name == "dirA" }) { "Expected dirA to exist" }
            assertNotNull(children.find { it.name == "dirC" }) { "Expected dirC to exist" }
        }

        listOf(dirA, targetPath).forEach { targetPath ->
            readWrite.list(targetPath).let { children ->
                assertEquals(2, children.size)
                assertNotNull(children.find { it.name == "fileA" }) { "Expected fileA to exist" }
                assertNotNull(children.find { it.name == "dirB" }) { "Expected dirB to exist" }
            }

            val newFileA = readWrite.joinPath(targetPath, "fileA")
            assertEquals("fileA content", newFileA.readText())

            val newDirB = readWrite.joinPath(targetPath, "dirB")
            readWrite.list(newDirB).let { children ->
                assertEquals(1, children.size)
                assertNotNull(children.find { it.name == "fileB" }) { "Expected fileB to exist" }
            }

            assertEquals("fileB content", readWrite.joinPath(newDirB, "fileB").readText())
        }
    }

    @Test
    fun `test ReadWrite create already existing file`() {
        val parent = dir1.parent
        val fileName = "newFile.txt"
        assertThrows(FileAlreadyExistsException::class.java) {
            runBlocking {
                readWrite.createFile(readWrite.joinPath(parent, fileName))
                readWrite.createFile(readWrite.joinPath(parent, fileName))
            }
        }
    }

    @Test
    fun `test ReadWrite create Unix invalid name file`() {
        val dirPath = dirEmpty
        assertEmpty(dirPath.listDirectoryEntries())
        val fileName = "Dir" + String(byteArrayOf(0))
        assertThrows(InvalidPathException::class.java) {
            runBlocking { readWrite.createFile(readWrite.joinPath(dirEmpty, fileName)) }
        }
        assertEmpty(dirPath.listDirectoryEntries())
        dirPath.listDirectoryEntries().forEach { it.toFile().deleteRecursively() }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `test ReadWrite create Windows invalid name file`() {
        val dirPath = dirEmpty

        assertEmpty(dirPath.listDirectoryEntries())
        val fileName = "D|ir"
        assertThrows(InvalidPathException::class.java) {
            runBlocking { readWrite.createFile(readWrite.joinPath(dirEmpty, fileName)) }
        }
        dirPath.listDirectoryEntries().forEach { it.toFile().deleteRecursively() }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `test ReadWrite create Windows reserved name file`() {
        val dirPath = dirEmpty
        assertEmpty(dirPath.listDirectoryEntries())
        val fileName = "NUL"
        assertThrows(IOException::class.java) {
            runBlocking { readWrite.createFile(readWrite.joinPath(dirEmpty, fileName)) }
        }
        dirPath.listDirectoryEntries().forEach { it.toFile().deleteRecursively() }
    }

    @Test
    fun `test ReadWrite create directory`() {
        runBlocking {
            val parent = dir3.parent
            val dirName = "newDir"
            val result = Path.of(parent.pathString + FileSystems.getDefault().separator + dirName)
            assertFalse(result.exists())
            readWrite.createDirectory(readWrite.joinPath(parent, dirName))
            assertAll(
                { assertTrue(result.exists()) { "Created directory does not exist" } },
                { assertTrue(result.isDirectory()) { "Created directory is not a directory" } },
            )
        }
    }

    @Test
    fun `test ReadWrite create already existing directory`() {
        val parent = dir3.parent
        val dirName = "newDir"
        assertThrows(IOException::class.java) {
            runBlocking {
                readWrite.createDirectory(readWrite.joinPath(parent, dirName))
                readWrite.createDirectory(readWrite.joinPath(parent, dirName))
            }
        }
    }

    @Test
    fun `test ReadWrite create Unix invalid name directory`() {
        val dirPath = dirEmpty
        assertEmpty(dirPath.listDirectoryEntries())
        assertThrows(Exception::class.java) {
            runBlocking {
                readWrite.createDirectory(
                    readWrite.joinPath(dirEmpty, "newDir" + String(byteArrayOf(0))),
                )
            }
        }
        dirPath.listDirectoryEntries().forEach { it.toFile().deleteRecursively() }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `test ReadWrite create Windows invalid name directory`() {
        val dirPath = dirEmpty
        assertEmpty(dirPath.listDirectoryEntries())
        assertThrows(InvalidPathException::class.java) {
            runBlocking { readWrite.createDirectory(readWrite.joinPath(dirEmpty, "new>Dir")) }
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `test ReadWrite create Windows reserved name directory`() {
        val dirPath = dirEmpty
        assertEmpty(dirPath.listDirectoryEntries())
        assertThrows(IOException::class.java) {
            runBlocking { readWrite.createDirectory(readWrite.joinPath(dirEmpty, "NUL")) }
        }
        dirPath.listDirectoryEntries().forEach { it.toFile().deleteRecursively() }
    }

    @Test
    fun `test ReadWrite delete directory`() {
        val dirPath = dirEmpty
        assertEmpty(dirPath.listDirectoryEntries())
        val dirName = "dirToDelete"
        val dirTest = File(dirPath.pathString + FileSystems.getDefault().separator + dirName).apply { mkdirs() }
        assertAll(
            { assertTrue(dirTest.exists()) },
            { assertTrue(dirTest.isDirectory) },
        )
        runBlocking { readWrite.delete(readWrite.joinPath(dirEmpty, dirName)) }
        assertFalse(dirTest.exists())
    }

    @Test
    fun `test ReadWrite delete not empty directory`() {
        val dirPath = dirEmpty
        assertEmpty(dirPath.listDirectoryEntries())
        val dirName = "dirToDelete"
        val dirTest = Path.of(dirPath.pathString + FileSystems.getDefault().separator + dirName).apply {
            this.createDirectory()
            Path.of(pathString + FileSystems.getDefault().separator + "file1").createFile()
            Path.of(pathString + FileSystems.getDefault().separator + "dir1").createDirectory()
        }
        assertAll(
            { assertTrue(dirTest.exists()) },
            { assertTrue(dirTest.isDirectory()) },
            { assertTrue(Files.newDirectoryStream(dirTest).use { it.iterator().hasNext() }) },
        )
        runBlocking { readWrite.delete(readWrite.joinPath(dirEmpty, dirName)) }
        assertFalse(dirTest.exists())
    }

    @Test
    fun `test ReadWrite outputStream to non-existing directory and file`() = runBlocking {
        val dirPath = dirEmpty
        val nonExistingDir = dirPath.resolve("non-existing-dir")
        val fileName = "newFile.txt"
        val filePath = nonExistingDir.resolve(fileName)

        assertFalse(nonExistingDir.exists())
        assertFalse(filePath.exists())

        val testContent = "Test content for non-existing file"
        readWrite.outputStream(filePath, false).use { outputStream ->
            outputStream.writeString(testContent)
            outputStream.flush()
        }

        assertTrue(nonExistingDir.exists())
        assertTrue(filePath.exists())
        assertEquals(testContent, filePath.readText())
    }

    @Test
    fun `test ReadWrite write to a file with outputStream with overwrite mode`() = runBlocking {
        val fileName = "newFile.txt"
        val tempFilePath = Path.of(dirEmpty.pathString + FileSystems.getDefault().separator + fileName)
        val initialContent = "Hello"
        tempFilePath.writeText(initialContent)

        assertTrue(tempFilePath.exists())
        assertEquals(initialContent, tempFilePath.readText())

        val newContent = " world"
        readWrite.outputStream(tempFilePath, false).use { outputStream ->
            outputStream.writeString(newContent)
            outputStream.flush()
        }

        val actualContent = tempFilePath.readText()

        assertAll(
            {
                assertEquals(
                    newContent,
                    actualContent
                ) { "Expected content to be overwritten with '$newContent', but was '$actualContent'" }
            },
            {
                assertFalse(
                    actualContent.contains(initialContent),
                    "File should not contain the initial content when in overwrite mode"
                )
            }
        )
    }

    @Test
    fun `test ReadWrite write to a file with outputStream with append mode`() = runBlocking {
        val fileName = "newFile.txt"
        val tempFilePath = Path.of(dirEmpty.pathString + FileSystems.getDefault().separator + fileName)

        val initialContent = "Hello"
        tempFilePath.writeText(initialContent)

        assertTrue(tempFilePath.exists())
        assertEquals(initialContent, tempFilePath.readText())

        val additionalContent = " world"
        readWrite.outputStream(tempFilePath, true).use { outputStream ->
            outputStream.writeString(additionalContent)
            outputStream.flush()
        }

        val expectedCombinedContent = initialContent + additionalContent
        val actualContent = tempFilePath.readText()

        assertAll(
            {
                assertEquals(
                    expectedCombinedContent,
                    actualContent
                ) { "Expected content to be '$expectedCombinedContent', but was '$actualContent'" }
            },
            {
                assertTrue(
                    actualContent.startsWith(initialContent),
                    "File should start with the initial content when in append mode"
                )
            },
            {
                assertTrue(
                    actualContent.endsWith(additionalContent),
                    "File should end with the additional content when in append mode"
                )
            }
        )
    }

    @Test
    fun `test ReadWrite create file`() {
        runBlocking {
            val parent = dir1.parent
            val fileName = "newFileReadWrite.txt"
            val result = Path.of(parent.pathString + FileSystems.getDefault().separator + fileName)
            assertFalse(result.exists())
            readWrite.createFile(readWrite.joinPath(parent, fileName))
            assertAll(
                { assertTrue(result.exists()) { "Created file does not exist" } },
                { assertTrue(result.isRegularFile()) { "Created file is not a file" } },
            )
        }
    }

    @Test
    fun `ReadWrite should create missing file when needed`() = runBlocking {
        val dirPath = dirEmpty
        val fileName = "newFileReadWrite.txt"
        val tempFilePath = Path.of(dirPath.pathString + FileSystems.getDefault().separator + fileName)

        assertFalse(tempFilePath.exists())

        val testMessage = "Hello world from ReadWrite"
        readWrite.writeText(tempFilePath, testMessage)

        assertAll(
            { assertTrue(tempFilePath.exists()) { "Created file does not exist" } },
            { assertTrue(tempFilePath.isRegularFile()) { "Created file is not a file" } },
        )

        val actualContent = tempFilePath.readText()
        assertEquals(testMessage, actualContent)
    }

    @Test
    fun `ReadWrite should create missing directory when needed`() = runBlocking {
        val dirPath = dirEmpty
        val nonExistingDir = dirPath.resolve("non-existing-dir")
        val fileName = "newFile.txt"
        val filePath = nonExistingDir.resolve(fileName)

        assertFalse(nonExistingDir.exists())
        assertFalse(filePath.exists())

        val testContent = "Test content for non-existing file"
        readWrite.writeText(filePath, testContent)

        assertTrue(nonExistingDir.exists())
        assertTrue(filePath.exists())
        assertEquals(testContent, filePath.readText())
    }

    @Test
    fun `test ReadWrite outputStream`() = runBlocking {
        val dirPath = dirEmpty
        val fileName = "newFileReadWriteSink.txt"
        val tempFilePath = Path.of(dirPath.pathString + FileSystems.getDefault().separator + fileName)

        assertFalse(tempFilePath.exists())

        val testMessage = "Hello world from ReadWrite sink"
        readWrite.outputStream(tempFilePath, false).use { outputStream ->
            outputStream.writeString(testMessage)
            outputStream.flush()
        }

        assertAll(
            { assertTrue(tempFilePath.exists()) { "Created file does not exist" } },
            { assertTrue(tempFilePath.isRegularFile()) { "Created file is not a file" } },
        )

        val actualContent = tempFilePath.readText()
        assertEquals(testMessage, actualContent)
    }

    @Test
    fun `test ReadWrite delete`() = runBlocking {
        val dirPath = dirEmpty
        val fileName = "fileToDeleteReadWrite.txt"
        val filePath = Path.of(dirPath.pathString + FileSystems.getDefault().separator + fileName)
        filePath.createFile()

        assertTrue(filePath.exists())

        readWrite.delete(readWrite.joinPath(dirPath, fileName))

        assertFalse(filePath.exists())
    }

    @Test
    fun `test ReadWrite delete with non-existing file`() {
        val dirPath = dirEmpty
        val fileName = dirPath.resolve("non-existing.txt").fileName.toString()
        val exception = assertThrows(NoSuchFileException::class.java) {
            runBlocking {
                readWrite.delete(readWrite.joinPath(dirPath, fileName))
            }
        }
        val expectedPath = dirPath.resolve(fileName).toString()
        assertTrue(
            exception.message?.contains(expectedPath) == true,
            "Exception message should contain the file path: $expectedPath, but was: ${exception.message}"
        )
    }

    //endregion

    @TestOnly
    private fun <T> assertEmpty(collection: Iterable<T>) {
        assertTrue(
            collection.count() == 0,
            "Expected empty collection, but it contains ${collection.count()} elements, with content: $collection"
        )
    }
}
