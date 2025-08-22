package ai.koog.agents.memory.storage

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val KEY_SIZE = 256
private const val KEY_LENGTH_BYTES = 32
private const val GCM_NONCE_LENGTH = 12 // 96 bits
private const val GCM_TAG_LENGTH = 128 // 128 bits

/**
 * Aes256GCMEncryptor is a cryptographic utility for performing AES-256 encryption and decryption
 * in GCM (Galois/Counter Mode). This class implements the `Encryption` interface and provides
 * methods to securely encrypt and decrypt text data.
 *
 * The class uses a specified secret key for encryption and decryption operations, ensuring that
 * the integrity and confidentiality of the data are maintained. Nonces are generated randomly to
 * ensure uniqueness and mitigate the risk of ciphertext reuse attacks.
 *
 * Key features:
 * - Secure AES-256-GCM encryption and decryption implementation
 * - Random nonce generation for each encryption operation
 * - Base64 encoding/decoding for easy integration with external systems
 * - Implements platform-independent `Encryption` interface
 *
 * @constructor Creates an instance of Aes256GCMEncryptor using the provided secret key.
 * The secret key must be a Base64-encoded string representing a valid 256-bit AES key.
 *
 * @param secretKey A Base64-encoded string representing the AES-256 secret key to initialize the encryptor.
 *
 * @throws IllegalArgumentException if the provided secret key is invalid or does not meet the required size.
 */
public class Aes256GCMEncryptor(secretKey: String) : Encryption {
    internal val key: SecretKey

    init {
        key = keyFromString(secretKey)
    }

    private val random = SecureRandom()

    private val cipher = Cipher.getInstance("AES/GCM/NoPadding")

    internal fun encryptImpl(plaintext: String): Pair<ByteArray, ByteArray> {
        // Generate a unique nonce
        val nonce = ByteArray(GCM_NONCE_LENGTH).apply {
            random.nextBytes(this)
        }

        // Initialize the cipher for encryption
        val gcmParameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmParameterSpec)

        // Encrypt the data
        val ciphertext = cipher.doFinal(plaintext.toByteArray())

        // Return the nonce and ciphertext
        return Pair(nonce, ciphertext)
    }

    internal fun decryptImpl(nonce: ByteArray, ciphertext: ByteArray): String {
        // Initialize the cipher for decryption
        val gcmParameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, nonce)
        cipher.init(Cipher.DECRYPT_MODE, key, gcmParameterSpec)

        // Decrypt the data
        val plaintext = cipher.doFinal(ciphertext)

        // Return the plaintext as a string
        return String(plaintext)
    }

    override fun encrypt(value: String): String {
        val (nonce, ciphertext) = encryptImpl(value)
        return Base64.getEncoder().encodeToString(nonce + ciphertext)
    }

    override fun decrypt(value: String): String {
        val valueBytes = Base64.getDecoder().decode(value)
        val nonce = valueBytes.take(GCM_NONCE_LENGTH).toByteArray()
        val ciphertext = valueBytes.drop(GCM_NONCE_LENGTH).toByteArray()

        return decryptImpl(nonce, ciphertext)
    }

    /**
     * Companion object for the Aes256GCMEncryptor class.
     * Provides utility methods for managing AES-256-GCM encryption keys, including generation,
     * serialization, and deserialization.
     *
     * Features:
     * - Generates a random AES-256 key securely using a cryptographically strong random number generator.
     * - Converts a Base64-encoded string back to a SecretKey, ensuring the key format and size are valid.
     * - Serializes a SecretKey to a Base64-encoded string for easy storage and transport.
     */
    public companion object {
        private val keyGenerator = KeyGenerator.getInstance("AES").apply {
            init(KEY_SIZE, SecureRandom())
        }

        /**
         * Generates a random secret key to be used for encryption purposes.
         * The method relies on a cryptographic key generator to create a secure key.
         *
         * @return A newly generated SecretKey object
         */
        public fun generateRandomKey(): SecretKey {
            return keyGenerator.generateKey()
        }

        /**
         * Converts a base64-encoded string representation of a secret key into a `SecretKey` object.
         * The input string must represent a valid AES-256 key (32 bytes long after decoding).
         *
         * @param keyString The base64-encoded string representation of the secret key.
         * @return A `SecretKey` object created from the input string.
         * @throws IllegalArgumentException if the decoded key length is not the required size.
         */
        public fun keyFromString(keyString: String): SecretKey {
            val base64Key = Base64.getDecoder().decode(keyString)
            if (base64Key.size != KEY_LENGTH_BYTES) {
                error(
                    "Secret key must be $KEY_LENGTH_BYTES bytes long but is ${base64Key.size}"
                )
            }
            return SecretKeySpec(base64Key, 0, KEY_LENGTH_BYTES, "AES")
        }

        /**
         * Converts the provided secret key into its Base64-encoded string representation.
         * This method is useful for securely exporting or storing the key as a string.
         *
         * @param key The secret key to be converted. Must be a valid and non-null instance of `SecretKey`.
         * @return A Base64-encoded string representation of the secret key.
         */
        public fun keyToString(key: SecretKey): String {
            return Base64.getEncoder().encodeToString(key.encoded)
        }
    }
}
