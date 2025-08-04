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
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

class JVMFileSystemProviderTest : KoogTestBase() {
    private val serialization = JVMFileSystemProvider.Serialization
    private val select = JVMFileSystemProvider.Select
    private val read = JVMFileSystemProvider.Read
    private val write = JVMFileSystemProvider.Write
    private val readOnly = JVMFileSystemProvider.ReadOnly
    private val readWrite = JVMFileSystemProvider.ReadWrite

    //region JVMFileSystemProvider.Serialization
    @Test
    fun `test path from absolute string`() {
        val filePathString = file1.absolute().pathString
        val fileFromPath = serialization.fromAbsoluteString(filePathString)
        assertEquals(file1, fileFromPath)
    }

    @Test
    fun `test path windows delimiters`() {
        val filePathString = file1.absolute().pathString.replace("/", "\\")
        val fileFromString = serialization.fromAbsoluteString(filePathString)
        assertEquals(file1, fileFromString)
    }

    @Test
    fun `test path unix delimiters`() {
        val filePathString = file1.absolute().pathString.replace("\\", "/")
        val fileFromString = serialization.fromAbsoluteString(filePathString)
        assertEquals(file1, fileFromString)
    }

    @Test
    fun `test fromAbsoluteString with non-existent path`() {
        val nonExistentPath = file1.absolute().pathString + "non_existent"
        val result = serialization.fromAbsoluteString(nonExistentPath).toString()
        assertEquals(nonExistentPath, result)
    }

    @Test
    fun `test toAbsolutePathString with non-existent path`() {
        val nonExistentPath = Path.of(file1.absolute().pathString + "non_existent")
        val result = serialization.toAbsolutePathString(nonExistentPath)
        assertEquals(nonExistentPath.toString(), result)
    }

    @Test
    fun `test fromAbsoluteString throws exception when resolved path is not absolute`() {
        val relativePathString = "relative/test/path"
        assertThrows(IllegalArgumentException::class.java) {
            serialization.fromAbsoluteString(relativePathString)
        }
    }

    @Test
    fun `test to path string`() {
        val filePathString = file1.absolute().pathString

        val testPathString = serialization.toAbsolutePathString(file1)
        assertEquals(filePathString, testPathString)

        assertEquals(filePathString, serialization.toAbsolutePathString(file1))
    }

    @Test
    fun `test from relative string`() {
        val fileFullPath = serialization.fromRelativeString(src1, file1.name)
        assertEquals(file1, fileFullPath)
    }

    @Test
    fun `test from relative string to parent dir`() {
        val dirPath = serialization.fromRelativeString(file3, "..${FileSystems.getDefault().separator}")
        assertEquals(src3, dirPath)
    }

    @Test
    fun `test from relative string with absolute path`() {
        val absolutePath = file1.absolute().pathString
        assertThrows(IllegalArgumentException::class.java) {
            serialization.fromRelativeString(file3, absolutePath)
        }
    }

    @Test
    fun `test from relative string with absolute path throws exception`() {
        val absolutePath = file1.absolute().pathString

        assertThrows(IllegalArgumentException::class.java) {
            serialization.fromRelativeString(src3, absolutePath)
        }
    }

    @Test
    fun `test fromRelativeString with non-existent path`() {
        val nonExistentRelativePath = "non_existent_folder/non_existent_file.txt"
        val result = serialization.fromRelativeString(src1, nonExistentRelativePath)
        assertTrue(result.pathString.contains("non_existent_folder"))
        assertTrue(result.pathString.contains("non_existent_file.txt"))
    }

    //endregion

    //region JVMFileSystemProvider.Select

    @Test
    fun `test list dir sorted`() = runBlocking {
        val testList = select.list(resources)
        val children = listOf(resource1, resource1xml, zip1, image1).sortedBy { it.name }
        assertEquals(children, testList)
    }

    @Test
    fun `test list empty dir`() = runBlocking {
        val testList = select.list(dirEmpty)
        assertEquals(emptyList<Path>(), testList)
    }

    @Test
    fun `test list is not recursive`() = runBlocking {
        // dir1 contains src1, which contains resources and file1
        // We should only get src1 when listing dir1, not the nested contents
        val testList = select.list(dir1)
        assertEquals(listOf(src1), testList)
    }

    @Test
    fun `test list fake dir`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                select.list(Path.of(dir1.pathString + "fake"))
            }
        }
    }

    @Test
    fun `test list text file`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                select.list(file1.absolute())
            }
        }
    }

    @Test
    fun `test list zip`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                select.list(zip1.absolute())
            }
        }
    }

    @Test
    fun `test metadata file`() = runBlocking {
        val metadata = FileMetadata(FileMetadata.FileType.File, hidden = false, content = FileMetadata.FileContent.Text)
        val testMetadata = select.metadata(file1)
        assertEquals(metadata, testMetadata)
    }

    @Test
    fun `test metadata dir`() = runBlocking {
        val metadata = FileMetadata(
            FileMetadata.FileType.Directory,
            hidden = false,
            content = FileMetadata.FileContent.Inapplicable
        )
        val testMetadata = select.metadata(dir1)
        assertEquals(metadata, testMetadata)
    }

    @Test
    fun `test metadata file not exist`() = runBlocking {
        val testMetadata = select.metadata(Path.of(file1.pathString + "fake"))
        assertEquals(null, testMetadata)
    }

    @Test
    fun `test parent`() = runBlocking {
        val testParent = select.parent(file1)
        assertEquals(src1, testParent)
    }

    @Test
    fun `test parent root`() = runBlocking {
        val testParent = select.parent(tempDir)
        assertEquals(tempDir.parent.pathString, testParent.toString())
    }

    @Test
    fun `test parent with non-existing path`() = runBlocking {
        val testParent = select.parent(Path.of("non-existing-path"))
        assertNull(testParent)
    }

    @Test
    fun `test relative`() = runBlocking {
        val target = resource1
        val targetParent = src1
        val expectedRelativePath =
            target.pathString.substringAfter(targetParent.pathString + FileSystems.getDefault().separator)
        val testPath = select.relativize(targetParent, target)
        assertEquals(expectedRelativePath, testPath)
    }

    @Test
    fun `test relative with no common prefix`() = runBlocking {
        val target = src1
        val targetParent = Path.of("non-existing-path")
        val testPath = select.relativize(targetParent, target)
        assertNull(testPath)
    }
    //endregion

    //region FileSystemProvider.Read
    @Test
    fun `test extension`() = runBlocking {
        val testExtension = select.extension(file1)
        assertEquals("kt", testExtension)
    }

    @Test
    fun `test extension empty`() = runBlocking {
        val testExtension = select.extension(file3)
        assertEquals("", testExtension)
    }

    @Test
    fun `test extension dir`() = runBlocking {
        val testExtension = select.extension(dir1)
        assertEquals("", testExtension)
    }

    @Test
    fun `test name with extension`() = runBlocking {
        val testName = select.name(file1)
        assertEquals("TestGenerator.kt", testName)
    }

    @Test
    fun `test name no extension`() = runBlocking {
        val testName = select.name(fileExl)
        assertEquals("Dummy", testName)
    }

    @Test
    fun `test name dir`() = runBlocking {
        val testName = select.name(dir1)
        assertEquals("dir1", testName)
    }

    @Test
    fun `test name no file`() = runBlocking {
        val testName = select.name(Path.of(file1.pathString + "fake"))
        assertEquals("TestGenerator.ktfake", testName)
    }

    @Test
    fun `test name file delimiter at the end`() = runBlocking {
        val testName = select.name(Path.of(file1.pathString + FileSystems.getDefault().separator))
        assertEquals("TestGenerator.kt", testName)
    }

    @Test
    fun `test name dir delimiter at the end`() = runBlocking {
        val testName = select.name(Path.of(dir1.pathString + FileSystems.getDefault().separator))
        assertEquals("dir1", testName)
    }

    @Test
    fun `test read file`() = runBlocking {
        val content = String(read.read(file1))
        assertEquals(testCode, content)
    }

    @Test
    fun `test read dir`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { read.read(dir2) }
        }
    }

    @Test
    fun `test read not exist`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { read.read(Path.of(file1.pathString + "fake")) }
        }
    }

    @Test
    fun `test size file`() = runBlocking {
        val size = read.size(file1)
        assertTrue(size > 0) { "Expected size more than a zero, but was $size" }
    }

    @Test
    fun `test size dir`() {
        assertThrows(Exception::class.java) {
            runBlocking { read.size(dir2) }
        }
    }

    @Test
    fun `test size empty dir`() {
        assertThrows(Exception::class.java) {
            runBlocking { read.size(dirEmpty) }
        }
    }

    @Test
    fun `test size not exist`() {
        assertThrows(Exception::class.java) {
            runBlocking {
                read.size(Path.of(file1.pathString + "fake"))
            }
        }
    }

    @Test
    fun `test source method read existing file`() = runBlocking {
        val content = String(read.read(file1))
        assertEquals(testCode, content)

        val actualContent = read.source(file1).use { source ->
            source.readString()
        }
        assertEquals(testCode, actualContent)
    }

    @Test
    fun `test source method read non-existing file`() {
        assertThrows<IllegalArgumentException> {
            runBlocking {
                read.source(Path.of(file1.pathString + "fake")).use { source ->
                    source.readString()
                }
            }
        }
    }

    @Test
    fun `test source method read directory`() {
        assertThrows<IllegalArgumentException> {
            runBlocking {
                read.source(dir2).use { source ->
                    source.readString()
                }
            }
        }
    }
    //endregion

    //region JVMFileSystemProvider.Write
    @Test
    fun `test write method overwrites existing file content`() = runBlocking {
        val dirPath = dirEmpty
        val fileName = "fileToOverwrite.txt"
        val filePath = Path.of(dirPath.pathString + FileSystems.getDefault().separator + fileName)

        val initialContent = "Initial content"
        filePath.writeText(initialContent)
        assertEquals(initialContent, filePath.readText())

        val newContent = "New content"
        write.write(filePath, newContent.toByteArray())

        val actualContent = filePath.readText()
        assertEquals(newContent, actualContent)
        assertFalse(actualContent.contains(initialContent), "File contents should be overwritten")
    }

    @Test
    fun `test move throws IOException when source file doesn't exist`() {
        val sourcePath = dirEmpty.resolve("non-existing-file.txt")
        val targetPath = dirEmpty.resolve("target-path")

        assertThrows(IOException::class.java) {
            runBlocking {
                write.move(sourcePath, targetPath)
            }
        }
    }

    @Test
    fun `test move throws FileAlreadyExistsException when target file already exists`() {
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
                write.move(sourcePath, targetPath)
            }
        }
    }

    @Test
    fun `test create file`() {
        runBlocking {
            val parent = dir1.parent
            val fileName = "newFile.txt"
            val result = Path.of(parent.pathString + FileSystems.getDefault().separator + fileName)
            assertFalse(result.exists())
            write.create(parent, fileName, FileMetadata.FileType.File)
            assertAll(
                { assertTrue(result.exists()) { "Created file does not exist" } },
                { assertTrue(result.isRegularFile()) { "Created file is not a file" } },
            )
        }
    }

    @Test
    fun `test create already existing file`() {
        val parent = dir1.parent
        val fileName = "newFile.txt"
        assertThrows(FileAlreadyExistsException::class.java) {
            runBlocking {
                write.create(parent, fileName, FileMetadata.FileType.File)
                write.create(parent, fileName, FileMetadata.FileType.File)
            }
        }
    }

    @Test
    fun `test create Unix invalid name file`() {
        val dirPath = dirEmpty
        assertEmpty(dirPath.listDirectoryEntries())
        val fileName = "Dir" + String(byteArrayOf(0))
        assertThrows(InvalidPathException::class.java) {
            runBlocking { write.create(dirEmpty, fileName, FileMetadata.FileType.File) }
        }
        assertEmpty(dirPath.listDirectoryEntries())
        dirPath.listDirectoryEntries().forEach { it.toFile().deleteRecursively() }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `test create Windows invalid name file`() {
        val dirPath = dirEmpty

        assertEmpty(dirPath.listDirectoryEntries())
        val fileName = "D|ir"
        assertThrows(InvalidPathException::class.java) {
            runBlocking { write.create(dirEmpty, fileName, FileMetadata.FileType.File) }
        }
        dirPath.listDirectoryEntries().forEach { it.toFile().deleteRecursively() }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `test create Windows reserved name file`() {
        val dirPath = dirEmpty
        assertEmpty(dirPath.listDirectoryEntries())
        val fileName = "NUL"
        assertThrows(IOException::class.java) {
            runBlocking { write.create(dirEmpty, fileName, FileMetadata.FileType.File) }
        }
        dirPath.listDirectoryEntries().forEach { it.toFile().deleteRecursively() }
    }

    @Test
    fun `test create directory`() {
        runBlocking {
            val parent = dir3.parent
            val dirName = "newDir"
            val result = Path.of(parent.pathString + FileSystems.getDefault().separator + dirName)
            assertFalse(result.exists())
            write.create(parent, dirName, FileMetadata.FileType.Directory)
            assertAll(
                { assertTrue(result.exists()) { "Created directory does not exist" } },
                { assertTrue(result.isDirectory()) { "Created directory is not a directory" } },
            )
        }
    }

    @Test
    fun `test create already existing directory`() {
        val parent = dir3.parent
        val dirName = "newDir"
        assertThrows(IOException::class.java) {
            runBlocking {
                write.create(parent, dirName, FileMetadata.FileType.Directory)
                write.create(parent, dirName, FileMetadata.FileType.Directory)
            }
        }
    }

    @Test
    fun `test create Unix invalid name directory`() {
        val dirPath = dirEmpty
        assertEmpty(dirPath.listDirectoryEntries())
        assertThrows(Exception::class.java) {
            runBlocking {
                write.create(dirEmpty, "newDir" + String(byteArrayOf(0)), FileMetadata.FileType.Directory)
            }
        }
        dirPath.listDirectoryEntries().forEach { it.toFile().deleteRecursively() }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `test create Windows invalid name directory`() {
        val dirPath = dirEmpty
        assertEmpty(dirPath.listDirectoryEntries())
        assertThrows(InvalidPathException::class.java) {
            runBlocking { write.create(dirEmpty, "new>Dir", FileMetadata.FileType.Directory) }
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `test create Windows reserved name directory`() {
        val dirPath = dirEmpty
        assertEmpty(dirPath.listDirectoryEntries())
        assertThrows(IOException::class.java) {
            runBlocking { write.create(dirEmpty, "NUL", FileMetadata.FileType.Directory) }
        }
        dirPath.listDirectoryEntries().forEach { it.toFile().deleteRecursively() }
    }

    @Test
    fun `test delete file`() = runBlocking {
        val dirPath = dirEmpty
        assertEmpty(dirPath.listDirectoryEntries())
        val fileName = "fileToDelete.txt"
        val fileTest =
            Path.of(dirPath.pathString + FileSystems.getDefault().separator + fileName).apply { this.createFile() }
        assertTrue(fileTest.exists())
        write.delete(dirEmpty, fileName)
        assertFalse(fileTest.exists())
    }

    @Test
    fun `test delete non-existing file`() {
        val dirPath = dirEmpty
        assertEmpty(dirPath.listDirectoryEntries())
        assertThrows(IOException::class.java) {
            runBlocking { write.delete(dirEmpty, "fileToDelete.txt") }
        }
    }

    @Test
    fun `test delete directory`() {
        val dirPath = dirEmpty
        assertEmpty(dirPath.listDirectoryEntries())
        val dirName = "dirToDelete"
        val dirTest = File(dirPath.pathString + FileSystems.getDefault().separator + dirName).apply { mkdirs() }
        assertAll(
            { assertTrue(dirTest.exists()) },
            { assertTrue(dirTest.isDirectory) },
        )
        runBlocking { write.delete(dirEmpty, dirName) }
        assertFalse(dirTest.exists())
    }

    @Test
    fun `test delete not empty directory`() {
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
        runBlocking { write.delete(dirEmpty, dirName) }
        assertFalse(dirTest.exists())
    }

    @Test
    fun `test write to a file with sink`() = runBlocking {
        val dirPath = dirEmpty

        val fileName = "newFile.txt"
        val tempFilePath = Path.of(dirPath.pathString + FileSystems.getDefault().separator + fileName)

        assertFalse(tempFilePath.exists())

        val testMessage = "Hello world"
        write.sink(tempFilePath, false).use { sink ->
            sink.writeString(testMessage)
            sink.flush()
        }

        assertAll(
            { assertTrue(tempFilePath.exists()) { "Created file does not exist" } },
            { assertTrue(tempFilePath.isRegularFile()) { "Created file is not a file" } },
        )

        val actualLines = tempFilePath.readLines()
        val expectedLines = listOf(testMessage)

        assertAll(
            { assertEquals(1, actualLines.size) { "Expected 1 line, but was ${actualLines.size}" } },
            { assertContentEquals(expectedLines, actualLines) }
        )
    }

    @Test
    fun `test sink to non-existing directory and file`() = runBlocking {
        val dirPath = dirEmpty
        val nonExistingDir = dirPath.resolve("non-existing-dir")
        val fileName = "newFile.txt"
        val filePath = nonExistingDir.resolve(fileName)

        assertFalse(nonExistingDir.exists())
        assertFalse(filePath.exists())

        val testContent = "Test content for non-existing file"
        write.sink(filePath, false).use { sink ->
            sink.writeString(testContent)
            sink.flush()
        }

        assertTrue(nonExistingDir.exists())
        assertTrue(filePath.exists())
        assertEquals(testContent, filePath.readText())
    }

    @Test
    fun `test write to a file with sink with overwrite mode`() = runBlocking {
        val fileName = "newFile.txt"
        val tempFilePath = Path.of(dirEmpty.pathString + FileSystems.getDefault().separator + fileName)
        val initialContent = "Hello"
        tempFilePath.writeText(initialContent)

        assertTrue(tempFilePath.exists())
        assertEquals(initialContent, tempFilePath.readText())

        val newContent = " world"
        write.sink(tempFilePath, false).use { sink ->
            sink.writeString(newContent)
            sink.flush()
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
    fun `test write to a file with sink with append mode`() = runBlocking {
        val fileName = "newFile.txt"
        val tempFilePath = Path.of(dirEmpty.pathString + FileSystems.getDefault().separator + fileName)

        val initialContent = "Hello"
        tempFilePath.writeText(initialContent)

        assertTrue(tempFilePath.exists())
        assertEquals(initialContent, tempFilePath.readText())

        val additionalContent = " world"
        write.sink(tempFilePath, true).use { sink ->
            sink.writeString(additionalContent)
            sink.flush()
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
    fun `test path starting with slash windows`() {
        val filePathString = "\\" + file1.absolute().pathString.replace("/", "\\")
        val fileFromString = serialization.fromAbsoluteString(filePathString)
        assertEquals(file1, fileFromString)
    }

    //endregion

    //region JVMFileSystemProvider.ReadOnly
    @Test
    fun `test ReadOnly toAbsolutePathString`() {
        val filePathString = file1.absolute().pathString
        val testPathString = readOnly.toAbsolutePathString(file1)
        assertEquals(filePathString, testPathString)
    }

    @Test
    fun `test ReadOnly fromAbsoluteString`() {
        val filePathString = file1.absolute().pathString
        val fileFromPath = readOnly.fromAbsoluteString(filePathString)
        assertEquals(file1, fileFromPath)
    }

    @Test
    fun `test ReadOnly fromRelativeString`() {
        val fileFullPath = readOnly.fromRelativeString(src1, file1.name)
        assertEquals(file1, fileFullPath)
    }

    @Test
    fun `test ReadOnly fromRelativeString with absolute path`() {
        val absolutePath = file1.absolute().pathString
        assertThrows(IllegalArgumentException::class.java) {
            readOnly.fromRelativeString(file3, absolutePath)
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
        val metadata =
            FileMetadata(FileMetadata.FileType.File, hidden = false, content = FileMetadata.FileContent.Text)
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
    fun `test ReadOnly read`() = runBlocking {
        val content = String(readOnly.read(file1))
        assertEquals(testCode, content)
    }

    @Test
    fun `test ReadOnly source`() = runBlocking {
        val actualContent = readOnly.source(file1).use { source ->
            source.readString()
        }
        assertEquals(testCode, actualContent)
    }

    @Test
    fun `test ReadOnly source with non-existing file`() {
        assertThrows<IllegalArgumentException> {
            runBlocking {
                readOnly.source(Path.of(file1.pathString + "fake")).use { source ->
                    source.readString()
                }
            }
        }
    }

    @Test
    fun `test ReadOnly source with directory`() {
        assertThrows<IllegalArgumentException> {
            runBlocking {
                readOnly.source(dir2).use { source ->
                    source.readString()
                }
            }
        }
    }

    @Test
    fun `test ReadOnly size`() = runBlocking {
        val size = readOnly.size(file1)
        assertTrue(size > 0) { "Expected size more than a zero, but was $size" }
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
    fun `test ReadWrite fromAbsoluteString`() {
        val filePathString = file1.absolute().pathString
        val fileFromPath = readWrite.fromAbsoluteString(filePathString)
        assertEquals(file1, fileFromPath)
    }

    @Test
    fun `test ReadWrite fromRelativeString`() {
        val fileFullPath = readWrite.fromRelativeString(src1, file1.name)
        assertEquals(file1, fileFullPath)
    }

    @Test
    fun `test ReadWrite fromRelativeString with absolute path`() {
        val absolutePath = file1.absolute().pathString
        assertThrows(IllegalArgumentException::class.java) {
            readWrite.fromRelativeString(file3, absolutePath)
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
        val metadata =
            FileMetadata(FileMetadata.FileType.File, hidden = false, content = FileMetadata.FileContent.Text)
        val testMetadata = readWrite.metadata(file1)
        assertEquals(metadata, testMetadata)
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
    fun `test ReadWrite read`() = runBlocking {
        val content = String(readWrite.read(file1))
        assertEquals(testCode, content)
    }

    @Test
    fun `test ReadWrite source`() = runBlocking {
        val actualContent = readWrite.source(file1).use { source ->
            source.readString()
        }
        assertEquals(testCode, actualContent)
    }

    @Test
    fun `test ReadWrite source with non-existing file`() {
        assertThrows<IllegalArgumentException> {
            runBlocking {
                readWrite.source(Path.of(file1.pathString + "fake")).use { source ->
                    source.readString()
                }
            }
        }
    }

    @Test
    fun `test ReadWrite source with directory`() {
        assertThrows<IllegalArgumentException> {
            runBlocking {
                readWrite.source(dir2).use { source ->
                    source.readString()
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
    fun `test ReadWrite create file`() {
        runBlocking {
            val parent = dir1.parent
            val fileName = "newFileReadWrite.txt"
            val result = Path.of(parent.pathString + FileSystems.getDefault().separator + fileName)
            assertFalse(result.exists())
            readWrite.create(parent, fileName, FileMetadata.FileType.File)
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
        readWrite.write(tempFilePath, testMessage.toByteArray())

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
        readWrite.write(filePath, testContent.toByteArray())

        assertTrue(nonExistingDir.exists())
        assertTrue(filePath.exists())
        assertEquals(testContent, filePath.readText())
    }

    @Test
    fun `test ReadWrite sink`() = runBlocking {
        val dirPath = dirEmpty
        val fileName = "newFileReadWriteSink.txt"
        val tempFilePath = Path.of(dirPath.pathString + FileSystems.getDefault().separator + fileName)

        assertFalse(tempFilePath.exists())

        val testMessage = "Hello world from ReadWrite sink"
        readWrite.sink(tempFilePath, false).use { sink ->
            sink.writeString(testMessage)
            sink.flush()
        }

        assertAll(
            { assertTrue(tempFilePath.exists()) { "Created file does not exist" } },
            { assertTrue(tempFilePath.isRegularFile()) { "Created file is not a file" } },
        )

        val actualContent = tempFilePath.readText()
        assertEquals(testMessage, actualContent)
    }

    @Test
    fun `test ReadWrite move`() = runBlocking {
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
    fun `test ReadWrite delete`() = runBlocking {
        val dirPath = dirEmpty
        val fileName = "fileToDeleteReadWrite.txt"
        val filePath = Path.of(dirPath.pathString + FileSystems.getDefault().separator + fileName)
        filePath.createFile()

        assertTrue(filePath.exists())

        readWrite.delete(dirPath, fileName)

        assertFalse(filePath.exists())
    }

    @Test
    fun `test ReadWrite delete with non-existing file`() {
        val dirPath = dirEmpty
        val fileName = dirPath.resolve("non-existing.txt").fileName.toString()
        val exception = assertThrows(NoSuchFileException::class.java) {
            runBlocking {
                readWrite.delete(dirPath, fileName)
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
