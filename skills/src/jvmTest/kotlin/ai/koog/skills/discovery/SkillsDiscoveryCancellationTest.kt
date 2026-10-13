package ai.koog.skills.discovery

import ai.koog.rag.base.files.FileMetadata
import ai.koog.rag.base.files.FileSystemProvider
import ai.koog.rag.base.files.JVMFileSystemProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SkillsDiscoveryCancellationTest {
    @Test
    fun testCancellationDuringListingStopsDiscovery() = testCancellation(read = false)

    @Test
    fun testCancellationDuringReadingStopsDiscovery() = testCancellation(read = true)

    @Test
    fun testListingFailureSkipsDirectory() = testOrdinaryFailure(read = false)

    @Test
    fun testReadingFailureSkipsSkill() = testOrdinaryFailure(read = true)

    private fun testCancellation(read: Boolean) = runTest {
        val entered = CompletableDeferred<Unit>()
        val fs = DiscoveryFileSystem(read) {
            entered.complete(Unit)
            awaitCancellation()
        }
        var continued = false
        val discovery = launch {
            discoverSkills(fs, listOf(fs.broken.toString(), fs.healthy.toString()))
            continued = true
        }
        entered.await()
        val callsAtCancellation = fs.calls.toList()
        discovery.cancelAndJoin()

        assertEquals(callsAtCancellation, fs.calls, "Discovery must perform no later I/O after cancellation")
        assertFalse(continued, "Caller code must not continue after discoverSkills")
    }

    private fun testOrdinaryFailure(read: Boolean) = runTest {
        val fs = DiscoveryFileSystem(read) { throw IOException("Cannot access skill") }

        val skills = discoverSkills(fs, listOf(fs.broken.toString(), fs.healthy.toString()))

        assertEquals(listOf("healthy"), skills.map { it.name })
        assertEquals(fs.toAbsolutePathString(fs.healthy.resolve("SKILL.md")), skills.single().location)
    }

    private class DiscoveryFileSystem(
        private val failRead: Boolean,
        private val failure: suspend () -> Nothing,
    ) : FileSystemProvider.ReadOnly<Path> by JVMFileSystemProvider.ReadOnly {
        val broken = Path.of("broken").toAbsolutePath()
        val healthy = Path.of("healthy").toAbsolutePath()
        val calls = mutableListOf<String>()

        override suspend fun metadata(path: Path): FileMetadata {
            calls += "metadata:$path"
            val type = if (path.fileName.toString() == "SKILL.md") {
                FileMetadata.FileType.File
            } else {
                FileMetadata.FileType.Directory
            }
            return FileMetadata(type, hidden = false)
        }

        override suspend fun list(directory: Path): List<Path> {
            calls += "list:$directory"
            if (!failRead && directory == broken) failure()
            return listOf(directory.resolve("SKILL.md"))
        }

        override suspend fun readBytes(path: Path): ByteArray {
            calls += "read:$path"
            if (failRead && path.parent == broken) failure()
            return "---\nname: healthy\ndescription: A healthy skill.\n---".encodeToByteArray()
        }
    }
}
