package ai.koog.agents.local.memory.storage

import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val KEY_SIZE = 256
private const val KEY_LENGTH_BYTES = 32
private const val GCM_NONCE_LENGTH = 12 // 96 bits
private const val GCM_TAG_LENGTH = 128 // 128 bits

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

    override fun encrypt(text: String): String {
        val (nonce, ciphertext) = encryptImpl(text)
        return Base64.getEncoder().encodeToString(nonce + ciphertext)
    }

    override fun decrypt(value: String): String {
        val valueBytes = Base64.getDecoder().decode(value)
        val nonce = valueBytes.take(GCM_NONCE_LENGTH).toByteArray()
        val ciphertext = valueBytes.drop(GCM_NONCE_LENGTH).toByteArray()

        return decryptImpl(nonce, ciphertext)
    }

    public companion object {
        private val keyGenerator = KeyGenerator.getInstance("AES").apply {
            init(KEY_SIZE, SecureRandom())
        }

        public fun generateRandomKey(): SecretKey {
            return keyGenerator.generateKey()
        }

        public fun keyFromString(keyString: String): SecretKey {
            val base64Key = Base64.getDecoder().decode(keyString)
            if (base64Key.size != KEY_LENGTH_BYTES) {
                error(
                    "Secret key must be $KEY_LENGTH_BYTES bytes long but is ${base64Key.size}"
                )
            }
            return SecretKeySpec(base64Key, 0, KEY_LENGTH_BYTES, "AES")
        }

        public fun keyToString(key: SecretKey): String {
            return Base64.getEncoder().encodeToString(key.encoded)
        }
    }
}
