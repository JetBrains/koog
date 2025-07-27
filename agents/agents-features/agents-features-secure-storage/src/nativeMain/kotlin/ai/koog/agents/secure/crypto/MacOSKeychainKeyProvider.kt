package ai.koog.agents.secure.crypto

import kotlinx.cinterop.*
import platform.Security.*
import platform.CoreFoundation.*
import platform.Foundation.*
import platform.darwin.OSStatus

/**
 * macOS Keychain-based key provider using the Security framework.
 * 
 * This provider stores encryption keys in the macOS Keychain, leveraging:
 * - Hardware-backed security on supported devices (Secure Enclave)
 * - Integration with Touch ID, Apple Watch unlock, and user passwords
 * - OS-level access controls and encryption
 * - Automatic synchronization across devices (if iCloud Keychain enabled)
 * 
 * Keys are stored as generic passwords in the Keychain with the service name
 * and account identifier specified during initialization.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
public class MacOSKeychainKeyProvider(
    override val keyIdentifier: String,
    override val serviceName: String
) : PlatformKeyProvider {
    
    override fun isAvailable(): Boolean {
        // Security framework is always available on macOS
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
            // Create attributes dictionary for keychain item
            val attributes = CFDictionaryCreateMutable(
                kCFAllocatorDefault, 
                0, 
                null, 
                null
            )
            
            try {
                // Set item class as generic password
                CFDictionarySetValue(attributes, kSecClass, kSecClassGenericPassword)
                
                // Set service name
                val serviceData = serviceName.encodeToByteArray()
                val serviceRef = CFDataCreate(kCFAllocatorDefault, serviceData.refTo(0), serviceData.size.toLong())
                CFDictionarySetValue(attributes, kSecAttrService, serviceRef)
                
                // Set account identifier
                val accountData = keyIdentifier.encodeToByteArray()
                val accountRef = CFDataCreate(kCFAllocatorDefault, accountData.refTo(0), accountData.size.toLong())
                CFDictionarySetValue(attributes, kSecAttrAccount, accountRef)
                
                // Set the key data
                val keyData = CFDataCreate(kCFAllocatorDefault, key.refTo(0), key.size.toLong())
                CFDictionarySetValue(attributes, kSecValueData, keyData)
                
                // Set access control - require user presence for access
                CFDictionarySetValue(attributes, kSecAttrAccessible, kSecAttrAccessibleWhenUnlockedThisDeviceOnly)
                
                // Delete existing item first (SecItemAdd fails if item exists)
                SecItemDelete(attributes)
                
                // Add the new keychain item
                val status = SecItemAdd(attributes, null)
                
                if (status != errSecSuccess) {
                    throw PlatformSecurityException(
                        "Failed to store key in macOS Keychain: ${getSecurityErrorString(status)}"
                    )
                }
                
                // Release Core Foundation objects
                CFRelease(serviceRef)
                CFRelease(accountRef)
                CFRelease(keyData)
                
            } finally {
                CFRelease(attributes)
            }
        }
    }
    
    override suspend fun removeKey() {
        memScoped {
            val query = CFDictionaryCreateMutable(
                kCFAllocatorDefault,
                0,
                null,
                null
            )
            
            try {
                // Set search parameters
                CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
                
                val serviceData = serviceName.encodeToByteArray()
                val serviceRef = CFDataCreate(kCFAllocatorDefault, serviceData.refTo(0), serviceData.size.toLong())
                CFDictionarySetValue(query, kSecAttrService, serviceRef)
                
                val accountData = keyIdentifier.encodeToByteArray()
                val accountRef = CFDataCreate(kCFAllocatorDefault, accountData.refTo(0), accountData.size.toLong())
                CFDictionarySetValue(query, kSecAttrAccount, accountRef)
                
                val status = SecItemDelete(query)
                
                if (status != errSecSuccess && status != errSecItemNotFound) {
                    throw PlatformSecurityException(
                        "Failed to remove key from macOS Keychain: ${getSecurityErrorString(status)}"
                    )
                }
                
                CFRelease(serviceRef)
                CFRelease(accountRef)
                
            } finally {
                CFRelease(query)
            }
        }
    }
    
    override suspend fun hasKey(): Boolean {
        return memScoped {
            val query = CFDictionaryCreateMutable(
                kCFAllocatorDefault,
                0, 
                null,
                null
            )
            
            try {
                // Set search parameters
                CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
                
                val serviceData = serviceName.encodeToByteArray()
                val serviceRef = CFDataCreate(kCFAllocatorDefault, serviceData.refTo(0), serviceData.size.toLong())
                CFDictionarySetValue(query, kSecAttrService, serviceRef)
                
                val accountData = keyIdentifier.encodeToByteArray()
                val accountRef = CFDataCreate(kCFAllocatorDefault, accountData.refTo(0), accountData.size.toLong())
                CFDictionarySetValue(query, kSecAttrAccount, accountRef)
                
                // We only want to check existence, not retrieve data
                CFDictionarySetValue(query, kSecReturnData, kCFBooleanFalse)
                
                val status = SecItemCopyMatching(query, null)
                
                CFRelease(serviceRef)
                CFRelease(accountRef)
                
                status == errSecSuccess
                
            } finally {
                CFRelease(query)
            }
        }
    }
    
    private suspend fun retrieveKey(): ByteArray {
        return memScoped {
            val query = CFDictionaryCreateMutable(
                kCFAllocatorDefault,
                0,
                null, 
                null
            )
            
            try {
                // Set search parameters
                CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
                
                val serviceData = serviceName.encodeToByteArray()
                val serviceRef = CFDataCreate(kCFAllocatorDefault, serviceData.refTo(0), serviceData.size.toLong())
                CFDictionarySetValue(query, kSecAttrService, serviceRef)
                
                val accountData = keyIdentifier.encodeToByteArray()
                val accountRef = CFDataCreate(kCFAllocatorDefault, accountData.refTo(0), accountData.size.toLong())
                CFDictionarySetValue(query, kSecAttrAccount, accountRef)
                
                // Request the data
                CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue)
                CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne)
                
                val result = alloc<CFTypeRefVar>()
                val status = SecItemCopyMatching(query, result.ptr)
                
                if (status != errSecSuccess) {
                    throw PlatformSecurityException(
                        "Failed to retrieve key from macOS Keychain: ${getSecurityErrorString(status)}"
                    )
                }
                
                val dataRef = result.value as CFDataRef
                val length = CFDataGetLength(dataRef).toInt()
                val bytes = CFDataGetBytePtr(dataRef)
                
                val keyBytes = ByteArray(length)
                for (i in 0 until length) {
                    keyBytes[i] = bytes!![i]
                }
                
                CFRelease(serviceRef)
                CFRelease(accountRef)
                CFRelease(dataRef)
                
                keyBytes
                
            } finally {
                CFRelease(query)
            }
        }
    }
    
    private fun generateSecureKey(): ByteArray {
        // Generate a 256-bit (32-byte) key using SecRandomCopyBytes
        val keySize = 32
        val keyBytes = ByteArray(keySize)
        
        keyBytes.usePinned { pinned ->
            val status = SecRandomCopyBytes(kSecRandomDefault, keySize.toULong(), pinned.addressOf(0))
            if (status != errSecSuccess) {
                throw PlatformSecurityException(
                    "Failed to generate secure random key: ${getSecurityErrorString(status)}"
                )
            }
        }
        
        return keyBytes
    }
    
    private fun getSecurityErrorString(status: OSStatus): String {
        return when (status) {
            errSecSuccess -> "Success"
            errSecItemNotFound -> "Item not found"
            errSecDuplicateItem -> "Duplicate item"
            errSecAuthFailed -> "Authentication failed"
            errSecUserCanceled -> "User canceled"
            errSecInteractionNotAllowed -> "Interaction not allowed"
            else -> "Security error: $status"
        }
    }
}