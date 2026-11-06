package ai.koog.a2a.transport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ClientCallContextTest {
    data class User(val name: String)

    @Test
    fun testEqualityComparesHeadersAndState() {
        val key = StateKey<User>("user")
        val ctx = ClientCallContext(headers = mapOf("X-Trace" to listOf("1")), state = mapOf(key to User("alice")))
        val same = ClientCallContext(headers = mapOf("X-Trace" to listOf("1")), state = mapOf(key to User("alice")))

        assertEquals(ctx, same)
        assertEquals(ctx.hashCode(), same.hashCode())
        assertNotEquals(ctx, ctx.copy(headers = emptyMap()))
        assertNotEquals(ctx, ctx.copy(state = emptyMap()))
    }
}
