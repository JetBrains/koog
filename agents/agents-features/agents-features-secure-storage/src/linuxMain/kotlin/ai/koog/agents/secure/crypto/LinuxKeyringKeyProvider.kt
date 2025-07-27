package ai.koog.agents.secure.crypto

import kotlinx.cinterop.*
import platform.posix.*

/**
 * Linux keyring-based key provider using libsecret or gnome-keyring.
 * 
 * This provider stores encryption keys in the Linux desktop keyring, leveraging:
 * - Integration with desktop session keyrings (GNOME Keyring, KWallet)
 * - User authentication integration (password, biometrics where available)
 * - Session-based or persistent storage options
 * - Desktop environment-specific secure storage
 * 
 * Falls back to filesystem-based storage with appropriate permissions if
 * desktop keyring services are unavailable.
 */
@OptIn(ExperimentalForeignApi::class)
public class LinuxKeyringKeyProvider(
    override val keyIdentifier: String,
    override val serviceName: String
) : PlatformKeyProvider {
    
    private val keyringKey = "${serviceName}.${keyIdentifier}"
    private val fallbackPath = "${getenv("HOME")?.toKString() ?: "/tmp"}/.koog-keys/${serviceName}-${keyIdentifier}.key"
    
    override fun isAvailable(): Boolean {
        // Check if we're in a desktop environment with keyring support
        val desktopSession = getenv("DESKTOP_SESSION")?.toKString()
        val xdgCurrentDesktop = getenv("XDG_CURRENT_DESKTOP")?.toKString()
        
        return desktopSession != null || xdgCurrentDesktop != null
    }
    
    override suspend fun getEncryptionKey(): ByteArray {
        if (!hasKey()) {
            // Generate a new key if none exists
            val newKey = generateSecureKey()
            storeKey(newKey)
            return newKey
        }
        return retrieveKey()
    }
    
    override suspend fun storeKey(key: ByteArray) {
        if (isDesktopKeyringAvailable()) {
            storeInKeyring(key)
        } else {
            storeInFallbackLocation(key)
        }
    }
    
    override suspend fun removeKey() {
        if (isDesktopKeyringAvailable()) {
            removeFromKeyring()
        } else {
            removeFromFallbackLocation()
        }
    }
    
    override suspend fun hasKey(): Boolean {
        return if (isDesktopKeyringAvailable()) {
            hasKeyInKeyring()
        } else {
            hasKeyInFallbackLocation()
        }
    }
    
    private fun isDesktopKeyringAvailable(): Boolean {
        // Try to detect if secret-tool or gnome-keyring tools are available
        val secretToolCheck = system("which secret-tool > /dev/null 2>&1")
        if (secretToolCheck == 0) return true
        
        val gnomeKeyringCheck = system("which gnome-keyring-daemon > /dev/null 2>&1")
        if (gnomeKeyringCheck == 0) return true
        
        return false
    }
    
    private suspend fun storeInKeyring(key: ByteArray) {
        // Use secret-tool to store the key
        val keyHex = key.joinToString("") { "%02x".format(it) }
        
        memScoped {
            val tempFile = "/tmp/koog-key-$keyIdentifier.tmp"
            val file = fopen(tempFile, "w")
            
            if (file == null) {
                throw PlatformSecurityException("Failed to create temporary file for keyring storage")
            }
            
            try {
                fputs(keyHex, file)
                fclose(file)
                
                // Store using secret-tool
                val command = "secret-tool store --label='Koog SecureStorage Key: $keyIdentifier' service '$serviceName' account '$keyIdentifier' < '$tempFile'"
                val result = system(command)
                
                if (result != 0) {
                    throw PlatformSecurityException("Failed to store key in Linux keyring: secret-tool returned $result")
                }
            } finally {
                // Clean up temporary file
                unlink(tempFile)
            }
        }
    }
    
    private suspend fun retrieveKey(): ByteArray {
        return if (isDesktopKeyringAvailable()) {
            retrieveFromKeyring()
        } else {
            retrieveFromFallbackLocation()
        }
    }
    
    private suspend fun retrieveFromKeyring(): ByteArray {
        memScoped {
            val tempFile = "/tmp/koog-key-retrieve-$keyIdentifier.tmp"
            
            // Retrieve using secret-tool
            val command = "secret-tool lookup service '$serviceName' account '$keyIdentifier' > '$tempFile'"
            val result = system(command)
            
            if (result != 0) {
                throw PlatformSecurityException("Failed to retrieve key from Linux keyring: secret-tool returned $result")
            }
            
            val file = fopen(tempFile, "r")
            if (file == null) {
                throw PlatformSecurityException("Failed to read retrieved key from temporary file")
            }
            
            try {
                val buffer = allocArray<ByteVar>(1024)
                val bytesRead = fread(buffer, 1u, 1023u, file)
                buffer[bytesRead.toInt()] = 0 // Null terminate
                
                val keyHex = buffer.toKString()
                
                // Convert hex string back to byte array
                val keyBytes = ByteArray(keyHex.length / 2)
                for (i in keyBytes.indices) {
                    val hex = keyHex.substring(i * 2, i * 2 + 2)
                    keyBytes[i] = hex.toInt(16).toByte()
                }
                
                keyBytes
            } finally {
                fclose(file)
                unlink(tempFile)
            }
        }
    }
    
    private suspend fun removeFromKeyring() {
        val command = "secret-tool clear service '$serviceName' account '$keyIdentifier'"
        val result = system(command)
        
        if (result != 0) {
            throw PlatformSecurityException("Failed to remove key from Linux keyring: secret-tool returned $result")
        }
    }
    
    private suspend fun hasKeyInKeyring(): Boolean {
        val command = "secret-tool lookup service '$serviceName' account '$keyIdentifier' > /dev/null 2>&1"
        return system(command) == 0
    }
    
    // Fallback implementations for systems without desktop keyring
    
    private suspend fun storeInFallbackLocation(key: ByteArray) {
        memScoped {
            // Create directory if it doesn't exist
            val dirPath = fallbackPath.substringBeforeLast('/')
            mkdir(dirPath, 0o700u) // Owner read/write/execute only
            
            val file = fopen(fallbackPath, "wb")
            if (file == null) {
                throw PlatformSecurityException("Failed to create fallback key file: $fallbackPath")
            }
            
            try {
                key.usePinned { pinned ->
                    val written = fwrite(pinned.addressOf(0), 1u, key.size.toULong(), file)
                    if (written != key.size.toULong()) {
                        throw PlatformSecurityException("Failed to write complete key to fallback location")
                    }
                }
                
                // Set restrictive permissions (owner read/write only)
                chmod(fallbackPath, 0o600u)
                
            } finally {
                fclose(file)
            }
        }
    }
    
    private suspend fun retrieveFromFallbackLocation(): ByteArray {
        memScoped {
            val file = fopen(fallbackPath, "rb")
            if (file == null) {
                throw PlatformSecurityException("Failed to open fallback key file: $fallbackPath")
            }
            
            try {
                // Get file size
                fseek(file, 0, SEEK_END)
                val fileSize = ftell(file).toInt()
                fseek(file, 0, SEEK_SET)
                
                val keyBytes = ByteArray(fileSize)
                keyBytes.usePinned { pinned ->
                    val bytesRead = fread(pinned.addressOf(0), 1u, fileSize.toULong(), file)
                    if (bytesRead != fileSize.toULong()) {
                        throw PlatformSecurityException("Failed to read complete key from fallback location")
                    }
                }
                
                keyBytes
            } finally {
                fclose(file)
            }
        }
    }
    
    private suspend fun removeFromFallbackLocation() {
        val result = unlink(fallbackPath)
        if (result != 0 && errno != ENOENT) {
            throw PlatformSecurityException("Failed to remove fallback key file: $fallbackPath")
        }
    }
    
    private suspend fun hasKeyInFallbackLocation(): Boolean {
        return access(fallbackPath, F_OK) == 0
    }
    
    private fun generateSecureKey(): ByteArray {
        // Generate a 256-bit (32-byte) key using /dev/urandom
        val keySize = 32
        val keyBytes = ByteArray(keySize)
        
        val file = fopen("/dev/urandom", "rb")
        if (file == null) {
            throw PlatformSecurityException("Failed to open /dev/urandom for key generation")
        }
        
        try {
            keyBytes.usePinned { pinned ->
                val bytesRead = fread(pinned.addressOf(0), 1u, keySize.toULong(), file)
                if (bytesRead != keySize.toULong()) {
                    throw PlatformSecurityException("Failed to read sufficient random data for key generation")
                }
            }
        } finally {
            fclose(file)
        }
        
        return keyBytes
    }
}