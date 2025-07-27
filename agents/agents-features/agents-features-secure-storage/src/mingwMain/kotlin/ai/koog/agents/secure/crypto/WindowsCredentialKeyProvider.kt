package ai.koog.agents.secure.crypto

import kotlinx.cinterop.*
import platform.windows.*

/**
 * Windows Credential Manager-based key provider using DPAPI and Windows Credential Store.
 * 
 * This provider stores encryption keys in the Windows Credential Manager, leveraging:
 * - Data Protection API (DPAPI) for hardware-backed encryption on supported devices
 * - Integration with Windows Hello, PIN, and user passwords
 * - OS-level access controls and automatic user-scoped encryption
 * - Secure storage that survives system restarts and user logoffs
 * 
 * Keys are stored as generic credentials in the Windows Credential Store with the service name
 * and account identifier specified during initialization.
 */
@OptIn(ExperimentalForeignApi::class)
public class WindowsCredentialKeyProvider(
    override val keyIdentifier: String,
    override val serviceName: String
) : PlatformKeyProvider {
    
    private val targetName = "${serviceName}:${keyIdentifier}"
    
    override fun isAvailable(): Boolean {
        // Credential Manager is available on all supported Windows versions
        return true
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
        memScoped {
            val credential = alloc<CREDENTIALW> {
                Type = CRED_TYPE_GENERIC
                TargetName = targetName.wcstr.ptr
                CredentialBlobSize = key.size.toUInt()
                CredentialBlob = allocArray<UByteVar>(key.size)
                Persist = CRED_PERSIST_LOCAL_MACHINE
                
                // Copy key data to credential blob
                for (i in key.indices) {
                    CredentialBlob!![i] = key[i].toUByte()
                }
                
                UserName = keyIdentifier.wcstr.ptr
                Comment = "Koog SecureStorage encryption key".wcstr.ptr
            }
            
            val result = CredWriteW(credential.ptr, 0u)
            
            if (result == 0) {
                val errorCode = GetLastError()
                throw PlatformSecurityException(
                    "Failed to store key in Windows Credential Manager: Error code $errorCode"
                )
            }
        }
    }
    
    override suspend fun removeKey() {
        val result = CredDeleteW(targetName.wcstr.ptr, CRED_TYPE_GENERIC, 0u)
        
        if (result == 0) {
            val errorCode = GetLastError()
            if (errorCode != ERROR_NOT_FOUND.toUInt()) {
                throw PlatformSecurityException(
                    "Failed to remove key from Windows Credential Manager: Error code $errorCode"
                )
            }
        }
    }
    
    override suspend fun hasKey(): Boolean {
        memScoped {
            val credentialPtr = alloc<CPointerVar<CREDENTIALW>>()
            val result = CredReadW(targetName.wcstr.ptr, CRED_TYPE_GENERIC, 0u, credentialPtr.ptr)
            
            if (result != 0) {
                // Key exists, free the retrieved credential
                CredFree(credentialPtr.value)
                return true
            }
            
            return false
        }
    }
    
    private suspend fun retrieveKey(): ByteArray {
        return memScoped {
            val credentialPtr = alloc<CPointerVar<CREDENTIALW>>()
            val result = CredReadW(targetName.wcstr.ptr, CRED_TYPE_GENERIC, 0u, credentialPtr.ptr)
            
            if (result == 0) {
                val errorCode = GetLastError()
                throw PlatformSecurityException(
                    "Failed to retrieve key from Windows Credential Manager: Error code $errorCode"
                )
            }
            
            val credential = credentialPtr.value!!.pointed
            val keySize = credential.CredentialBlobSize.toInt()
            val keyData = ByteArray(keySize)
            
            // Copy credential blob data to byte array
            for (i in 0 until keySize) {
                keyData[i] = credential.CredentialBlob!![i].toByte()
            }
            
            // Free the retrieved credential
            CredFree(credentialPtr.value)
            
            keyData
        }
    }
    
    private fun generateSecureKey(): ByteArray {
        // Generate a 256-bit (32-byte) key using Windows CryptGenRandom
        val keySize = 32
        val keyBytes = ByteArray(keySize)
        
        memScoped {
            val hProvider = alloc<HCRYPTPROVVar>()
            
            // Acquire cryptographic context
            val acquireResult = CryptAcquireContextW(
                hProvider.ptr,
                null,
                null,
                PROV_RSA_FULL,
                CRYPT_VERIFYCONTEXT
            )
            
            if (acquireResult == 0) {
                throw PlatformSecurityException(
                    "Failed to acquire cryptographic context: Error code ${GetLastError()}"
                )
            }
            
            try {
                keyBytes.usePinned { pinned ->
                    val genResult = CryptGenRandom(
                        hProvider.value,
                        keySize.toUInt(),
                        pinned.addressOf(0).reinterpret()
                    )
                    
                    if (genResult == 0) {
                        throw PlatformSecurityException(
                            "Failed to generate secure random key: Error code ${GetLastError()}"
                        )
                    }
                }
            } finally {
                // Release cryptographic context
                CryptReleaseContext(hProvider.value, 0u)
            }
        }
        
        return keyBytes
    }
}