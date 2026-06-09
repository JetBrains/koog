@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package ai.koog.prompt.executor.clients.foundationmodels

import foundationModels.KoogFMBridge
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Production [FoundationModelsSession] backed by the bundled `@objc` shim.
 *
 * The shim class is un-gated and FoundationModels is weak-linked, so constructing
 * [KoogFMBridge] (or calling [availabilityToken]) is safe on any OS version — pre-26
 * systems get the stable `"osVersionTooOld"` token instead of a crash. Cancellation is
 * one-sided in this POC: cancelling the coroutine unsuspends the continuation, but the
 * Swift `Task` keeps running.
 */
internal class CInteropFoundationModelsSession : FoundationModelsSession {
    private val bridge by lazy { KoogFMBridge() }

    override fun availabilityToken(): String? = bridge.availabilityToken()

    override suspend fun respond(prompt: String, instructions: String?): String =
        suspendCancellableCoroutine { cont ->
            bridge.respond(prompt, instructions = instructions) { content, error ->
                when {
                    error != null -> cont.resumeWithException(FoundationModelsException.Generation(error))
                    else -> cont.resume(content ?: "")
                }
            }
        }
}

internal actual fun defaultFoundationModelsSession(): FoundationModelsSession =
    CInteropFoundationModelsSession()
