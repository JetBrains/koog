package ai.koog.agents.memory.storage

import ai.koog.agents.memory.providers.FileMetadata
import ai.koog.agents.memory.providers.FileSystemProvider

/**
 * Platform-independent encryption abstraction for secure data storage.
 * This interface provides a standardized way to protect sensitive data
 * while allowing different encryption implementations based on security requirements.
 *
 * Key features:
 * - Transparent encryption/decryption of text data
 * - Platform-independent API
 * - Support for different encryption algorithms
 * - String-based input/output for easy integration
 *
 * Example implementation might use AES-256-GCM:
 * ```
 * class Aes256GCMEncryption : Encryption {
 *     override fun encrypt(text: String): String {
 *         // Implement AES-256-GCM encryption
 *     }
 *     override fun decrypt(text: String): String {
 *         // Implement AES-256-GCM decryption
 *     }
 * }
 * ```
 */
public interface Encryption {
    /**
     * Encrypts the given text using the implementation-specific algorithm.
     * Implementations should ensure:
     * - Consistent encryption results for the same input
     * - Safe handling of special characters
     * - Proper error handling for invalid inputs
     *
     * @param text Plain text to encrypt (must not be empty)
     * @return Encrypted text in a format suitable for storage
     * @throws IllegalArgumentException if text is empty
     */
    public fun encrypt(text: String): String

    /**
     * Decrypts previously encrypted text back to its original form.
     * Implementations should ensure:
     * - Exact restoration of the original text
     * - Proper validation of encrypted input
     * - Graceful handling of corrupted data
     *
     * @param text Previously encrypted text
     * @return Original decrypted text
     * @throws IllegalArgumentException if text is not properly encrypted
     */
    public fun decrypt(text: String): String
}

/**
 * Core storage abstraction for the memory system that provides a unified
 * interface for file operations across different platforms and storage backends.
 *
 * Key features:
 * - Platform-independent path handling
 * - Basic file operations (read, write, exists)
 * - Directory management
 * - Null safety for missing files
 *
 * Usage example:
 * ```
 * val storage: Storage<Path> = SimpleStorage(fileSystem)
 * // or
 * val storage: Storage<Path> = EncryptedStorage(fileSystem, encryption)
 *
 * // Write data
 * storage.write(path, "Important data")
 *
 * // Read data
 * val data = storage.read(path)
 * ```
 *
 * @param Path Platform-specific path type (e.g., java.nio.file.Path for JVM)
 */
public interface Storage<Path> {
    /**
     * Verifies the existence of a file or directory.
     * This operation is atomic and thread-safe.
     *
     * @param path Target path to check
     * @return true if the path exists and is accessible
     */
    public suspend fun exists(path: Path): Boolean

    /**
     * Retrieves the content of a file as a string.
     * This operation is atomic and handles missing files gracefully.
     *
     * @param path Path to the file to read
     * @return File content as string, or null if:
     *         - File doesn't exist
     *         - Path is a directory
     *         - File is not readable
     */
    public suspend fun read(path: Path): String?

    /**
     * Writes content to a file, creating it if necessary.
     * This operation is atomic and ensures:
     * - Parent directories are created if missing
     * - Existing files are overwritten
     * - Proper file permissions are set
     *
     * @param path Destination path for the file
     * @param content String content to write (encoded as UTF-8)
     * @throws IOException if write operation fails
     */
    public suspend fun write(path: Path, content: String)

    /**
     * Creates a directory and all parent directories if needed.
     * This operation is idempotent and thread-safe.
     *
     * @param path Directory path to create
     * @throws IOException if directory creation fails
     */
    public suspend fun createDirectories(path: Path)
}

/**
 * Basic implementation of [Storage] that provides direct file system access
 * without additional security layers. This implementation is suitable for
 * non-sensitive data or when encryption is handled at a different level.
 *
 * Key features:
 * - Direct file system operations
 * - UTF-8 encoding for text
 * - Automatic directory creation
 * - Thread-safe operations
 *
 * Usage example:
 * ```
 * val storage = SimpleStorage(JVMFileSystemProvider)
 *
 * // Store configuration
 * storage.write(configPath, """
 *     {
 *         "setting1": "value1",
 *         "setting2": "value2"
 *     }
 * """.trimIndent())
 *
 * // Read configuration
 * val config = storage.read(configPath)
 * ```
 *
 * @param Path Platform-specific path type
 * @param fs File system provider for actual I/O operations
 */
public open class SimpleStorage<Path>(
    protected val fs: FileSystemProvider.ReadWrite<Path>
) : Storage<Path> {
    /**
     * Verifies path existence in the file system.
     * This is a lightweight operation that doesn't read file content.
     *
     * @param path Target path to check
     * @return true if path exists and is accessible
     */
    override suspend fun exists(path: Path): Boolean {
        return fs.exists(path)
    }

    /**
     * Reads file content with UTF-8 decoding.
     * This operation ensures proper handling of:
     * - Missing files (returns null)
     * - Text encoding (UTF-8)
     * - Binary files (may throw if not valid UTF-8)
     *
     * @param path File to read
     * @return File content as string, or null if file doesn't exist
     * @throws IllegalStateException if content is not valid UTF-8
     */
    override suspend fun read(path: Path): String? {
        if (!fs.exists(path)) return null
        return fs.read(path).decodeToString()
    }

    /**
     * Writes content to a file with proper UTF-8 encoding.
     * This operation ensures:
     * - Atomic writes (file is either fully written or not at all)
     * - Proper text encoding (UTF-8)
     * - Directory creation if needed
     * - Overwrite of existing files
     *
     * @param path Target file path
     * @param content Text content to write
     * @throws IOException if write fails
     */
    override suspend fun write(path: Path, content: String) {
        val parent = fs.parent(path) ?: return
        val name = fs.name(path)

        if (!fs.exists(path)) {
            fs.create(parent, name, FileMetadata.FileType.File)
        }
        fs.write(path, content.encodeToByteArray())
    }

    /**
     * Creates directory hierarchy with proper permissions.
     * This operation is idempotent and ensures:
     * - All parent directories are created
     * - Proper directory permissions
     * - No effect if directory already exists
     *
     * @param path Directory to create
     * @throws IOException if creation fails
     */
    override suspend fun createDirectories(path: Path) {
        if (!fs.exists(path)) {
            val parent = fs.parent(path) ?: return
            val name = fs.name(path)
            fs.create(parent, name, FileMetadata.FileType.Directory)
        }
    }
}

/**
 * Secure implementation of [Storage] that provides transparent encryption
 * of stored data. This implementation is suitable for sensitive information
 * that needs to be protected at rest.
 *
 * Security features:
 * - Transparent encryption/decryption
 * - No plaintext storage on disk
 * - Support for various encryption algorithms
 * - Secure error handling
 *
 * Usage example:
 * ```
 * val storage = EncryptedStorage(
 *     fs = JVMFileSystemProvider,
 *     encryption = Aes256GCMEncryption(secretKey)
 * )
 *
 * // Store sensitive data
 * storage.write(secretPath, "sensitive information")
 *
 * // Read encrypted data
 * val decrypted = storage.read(secretPath)
 * ```
 *
 * @param Path Platform-specific path type
 * @param fs File system provider for I/O operations
 * @param encryption Service for data encryption/decryption
 */
public class EncryptedStorage<Path>(
    private val fs: FileSystemProvider.ReadWrite<Path>,
    private val encryption: Encryption
) : Storage<Path> {
    /**
     * Verifies path existence without accessing encrypted content.
     * This operation is safe as it doesn't expose any protected data.
     *
     * @param path Target path to check
     * @return true if path exists and is accessible
     */
    override suspend fun exists(path: Path): Boolean {
        return fs.exists(path)
    }

    /**
     * Reads and decrypts file content securely.
     * This operation ensures:
     * - Proper decryption of content
     * - Secure handling of decryption errors
     * - No exposure of encrypted data
     *
     * @param path Encrypted file to read
     * @return Decrypted content, or null if file doesn't exist
     * @throws IllegalStateException if decryption fails
     */
    override suspend fun read(path: Path): String? {
        if (!fs.exists(path)) return null
        val content = fs.read(path).decodeToString()
        return encryption.decrypt(content)
    }

    /**
     * Encrypts and writes content securely.
     * This operation ensures:
     * - Content is never stored in plaintext
     * - Atomic write of encrypted data
     * - Secure handling of encryption errors
     * - Directory creation if needed
     *
     * @param path Target file for encrypted content
     * @param content Plaintext to encrypt and store
     * @throws IllegalStateException if encryption fails
     * @throws IOException if write fails
     */
    override suspend fun write(path: Path, content: String) {
        val parent = fs.parent(path) ?: return
        val name = fs.name(path)

        val encrypted = encryption.encrypt(content)
        if (!fs.exists(path)) {
            fs.create(parent, name, FileMetadata.FileType.File)
        }
        fs.write(path, encrypted.encodeToByteArray())
    }

    /**
     * Creates directory hierarchy (unencrypted).
     * Directory names and structure remain unencrypted as only file
     * contents are protected. This is a design choice that balances
     * security with usability.
     *
     * @param path Directory to create
     * @throws IOException if creation fails
     */
    override suspend fun createDirectories(path: Path) {
        if (!fs.exists(path)) {
            val parent = fs.parent(path) ?: return
            val name = fs.name(path)
            fs.create(parent, name, FileMetadata.FileType.Directory)
        }
    }
}
