package ai.koog.prompt.executor.clients.google.genai

import com.google.genai.AsyncModels
import com.google.genai.Client
import org.mockito.Mockito

/**
 * Creates a Mockito mock of [Client] with the `async.models` field chain
 * wired up for stubbing.
 *
 * The Google GenAI Java SDK uses public Java **fields** (`Client.async`,
 * `Client.Async.models`) rather than getter methods. Mockito's
 * [Mockito.RETURNS_DEEP_STUBS] only intercepts method calls, so we set
 * the fields via reflection instead.
 *
 * @return Pair of (mocked Client, mocked AsyncModels) — stub
 *   `generateContent` on the AsyncModels instance.
 */
internal fun mockGoogleGenaiClient(): Pair<Client, AsyncModels> {
    val client = Mockito.mock(Client::class.java)
    val asyncModels = Mockito.mock(AsyncModels::class.java)
    val asyncClient = Mockito.mock(Client.Async::class.java)

    setField(asyncClient, "models", asyncModels)
    setField(client, "async", asyncClient)

    return client to asyncModels
}

private fun setField(target: Any, fieldName: String, value: Any) {
    var clazz: Class<*> = target.javaClass
    while (clazz != Any::class.java) {
        try {
            val field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(target, value)
            return
        } catch (_: NoSuchFieldException) {
            clazz = clazz.superclass ?: break
        }
    }
    throw NoSuchFieldException("Field '$fieldName' not found in hierarchy of ${target.javaClass}")
}
