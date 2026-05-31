@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package ai.koog.prompt.executor.clients.foundationmodels

import foundationModels.KoogFMBridge
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Production [FoundationModelsSession] backed by the bundled `@objc` shim.
 *
 * The 26-only [KoogFMBridge] is constructed lazily so merely instantiating this class
 * on a sub-26 OS does not touch the gated Obj-C symbol; [availabilityReason] is the
 * first thing the client calls. Cancellation is one-sided in this POC: cancelling the
 * coroutine unsuspends the continuation, but the Swift `Task` keeps running.
 */
internal class CInteropFoundationModelsSession : FoundationModelsSession {
    private val bridge by lazy { KoogFMBridge() }

    override fun availabilityReason(): String? = bridge.availabilityReason()

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
