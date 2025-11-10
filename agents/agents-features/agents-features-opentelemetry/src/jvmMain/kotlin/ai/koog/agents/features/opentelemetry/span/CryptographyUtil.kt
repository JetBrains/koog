package ai.koog.agents.features.opentelemetry.span

import io.ktor.utils.io.core.toByteArray
import java.security.MessageDigest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Computes the SHA-256 hash of the current string, encodes the resulting hash in base64 using
 * URL-safe encoding, and returns the encoded string.
 *
 * @return The base64 URL-safe encoded SHA-256 hash of the string.
 */
internal fun String.sha256base64(): String {
    val hash = this.toByteArray().sha256()

    @OptIn(ExperimentalEncodingApi::class)
    return Base64.UrlSafe.encode(hash)
}

/**
 * Computes the SHA-256 hash of the current ByteArray.
 *
 * @return A ByteArray containing the SHA-256 hash of the input ByteArray.
 */
internal fun ByteArray.sha256(): ByteArray =
    MessageDigest
        .getInstance("SHA-256")
        .digest(this)
