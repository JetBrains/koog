package ai.koog.agents.local.memory.storage

import ai.koog.agents.local.memory.providers.JVMFileSystemProvider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JvmStorageTest {
    private val testKey = Aes256GCMEncryptor.run {
        keyToString(generateRandomKey())
    }

    @Test
    fun testEncryptedStorageWithJvmProvider(@TempDir tempDir: Path) = runTest {
        val fs = JVMFileSystemProvider.ReadWrite
        val encryptor = Aes256GCMEncryptor(testKey)
        val storage = EncryptedStorage(fs, encryptor)

        // Test file creation and writing
        val testPath = fs.fromRelativeString(tempDir, "test.txt")
        val testContent = "Hello, World!"
        storage.write(testPath, testContent)

        // Test file existence
        assertTrue(storage.exists(testPath))

        // Test file reading
        val readContent = storage.read(testPath)
        assertEquals(testContent, readContent)

        // Test directory creation
        val dirPath = fs.fromRelativeString(tempDir, "test/nested/dir")
        storage.createDirectories(dirPath)
        assertTrue(storage.exists(dirPath))

        // Test file in nested directory
        val nestedPath = fs.fromRelativeString(tempDir, "test/nested/dir/nested.txt")
        storage.write(nestedPath, testContent)
        assertTrue(storage.exists(nestedPath))
        assertEquals(testContent, storage.read(nestedPath))

        // Test non-existent file
        val nonExistentPath = fs.fromRelativeString(tempDir, "non-existent.txt")
        assertFalse(storage.exists(nonExistentPath))
        assertEquals(null, storage.read(nonExistentPath))

        // Test encryption
        val encryptedContent = fs.read(testPath).decodeToString()
        assertFalse(encryptedContent.contains(testContent), "Content should be encrypted")
    }

    @Test
    fun testAes256GCMEncryption() {
        val encryptor = Aes256GCMEncryptor(testKey)
        val testData = "Sensitive data"

        // Test encryption
        val encrypted = encryptor.encrypt(testData)
        assertFalse(encrypted.contains(testData), "Encrypted data should not contain original text")

        // Test decryption
        val decrypted = encryptor.decrypt(encrypted)
        assertEquals(testData, decrypted)

        // Test different data produces different encryption
        val anotherEncrypted = encryptor.encrypt(testData)
        assertFalse(encrypted == anotherEncrypted, "Same data should produce different encryption due to IV")
    }
}
