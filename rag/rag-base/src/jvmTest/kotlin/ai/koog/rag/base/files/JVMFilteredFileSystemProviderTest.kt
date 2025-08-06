package ai.koog.rag.base.files

import ai.koog.rag.base.files.filter.PathFilters
import ai.koog.rag.base.files.filter.TraversalFilter
import ai.koog.rag.base.files.filter.TraversalFilter.Companion.not
import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JVMFilteredFileSystemProviderTest : KoogTestBase() {

    private lateinit var fsReadOnly: FileSystemProvider.ReadOnly<Path>
    private lateinit var fsReadWrite: FileSystemProvider.ReadWrite<Path>

    private val none = TraversalFilter<Path> { _, _ -> false }

    /* all the tests below have an assumption that JVMFileSystemProvider is covered by tests,
     and hence only the filtering should be verified */

    override fun setup() {
        super.setup()
        val filter = TraversalFilter.any<Path>() and not(none) and PathFilters.byRoot(src1)
        fsReadOnly = JVMFileSystemProvider.ReadOnly.filter(filter)
        fsReadWrite = JVMFileSystemProvider.ReadWrite.filter(filter)
    }

    @Test
    fun `test read`(): Unit = runBlocking {
        assertThrows<IllegalArgumentException> {
            fsReadOnly.read(file3)
        }

        fsReadOnly.read(resource1)
    }

    @Test
    fun `test source`(): Unit = runBlocking {
        assertThrows<IllegalArgumentException> {
            fsReadOnly.source(file3).close()
        }

        fsReadOnly.source(resource1).close()
    }

    @Test
    fun `test size`(): Unit = runBlocking {
        assertThrows<IllegalArgumentException> {
            fsReadOnly.size(file3)
        }

        fsReadOnly.size(resource1)
    }

    @Test
    fun `test list`(): Unit = runBlocking {
        assertThrows<IllegalArgumentException> {
            fsReadOnly.list(src2)
        }

        val child = fsReadOnly.list(tempDir).single()
        assertEquals(dir1, child)
    }

    @Test
    fun `test metadata`(): Unit = runBlocking {
        assertNull(fsReadOnly.metadata(dir3))
        assertNull(fsReadOnly.metadata(file2))

        assertNotNull(fsReadOnly.metadata(resources))
        assertNotNull(fsReadOnly.metadata(file1))
    }

    @Test
    fun `test exists`(): Unit = runBlocking {
        assertFalse(fsReadOnly.exists(dir3))
        assertFalse(fsReadOnly.exists(file2))

        assertTrue(fsReadOnly.exists(resources))
        assertTrue(fsReadOnly.exists(file1))
    }

    @Test
    fun `test select does not throw`() {
        assertNotNull(fsReadOnly.parent(src2))
        assertNotNull(fsReadOnly.fromRelativeString(src2, assertNotNull(fsReadOnly.relativize(src2, file2))))
        assertNotNull(fsReadOnly.fromAbsoluteString(assertNotNull(fsReadOnly.toAbsolutePathString(file2))))
        assertNotNull(fsReadOnly.name(src2))
        assertNotNull(fsReadOnly.extension(file2))
    }

    @Test
    fun `test create`() = runBlocking {
        assertThrows<IOException> {
            // forbidden parent
            fsReadWrite.create(dir2, "myNewDir", FileMetadata.FileType.Directory)
        }

        assertThrows<IOException> {
            // allowed parent
            fsReadWrite.create(dir1, "myNewFile", FileMetadata.FileType.File)
        }

        fsReadWrite.create(src1, "myNewDir", FileMetadata.FileType.Directory)
        fsReadWrite.create(src1, "myNewFile", FileMetadata.FileType.File)
    }

    @Test
    fun `test write`() = runBlocking {
        assertThrows<IOException> {
            fsReadWrite.write(file2, "myNewContent".toByteArray())
        }

        fsReadWrite.write(file1, "myNewContent".toByteArray())
    }

    @Test
    fun `test sink`() = runBlocking {
        assertThrows<IOException> {
            fsReadWrite.sink(file2).close()
        }

        fsReadWrite.sink(file1).close()
    }

    @Test
    fun `test move`() = runBlocking {
        assertThrows<IOException> {
            // source is not allowed
            fsReadWrite.move(file2, src1.resolve(file2.name))
        }

        assertThrows<IOException> {
            // destination is not allowed
            fsReadWrite.move(resources, dir2.resolve(resources.name))
        }

        fsReadWrite.move(file1, resources.resolve(file1.name))
    }

    @Test
    fun `test delete`() = runBlocking {
        assertThrows<IOException> {
            // forbidden parent
            fsReadWrite.delete(src2, file2.name)
        }

        assertThrows<IOException> {
            // allowed parent
            fsReadWrite.delete(dir1, dir2.name)
        }

        fsReadWrite.delete(src1, file1.name)
    }
}
