package ai.koog.utils.io

import kotlinx.coroutines.test.runTest
import kotlin.js.JsName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CloseableTest {

    class TestCloseable : Closeable {
        var isClosed: Boolean = false

        override suspend fun close() {
            isClosed = true
        }
    }

    @Test
    @JsName("testCloseableInitiallyNotClosed")
    fun `test closeable initially not closed`() {
        val closeable = TestCloseable()
        assertFalse(closeable.isClosed, "Closeable should not be closed initially")
    }

    @Test
    @JsName("testCloseableIsClosedAfterCloseMethodCall")
    fun `test closeable is closed after close method call`() = runTest {
        val closeable = TestCloseable()
        assertFalse(closeable.isClosed, "Closeable should not be closed initially")
        closeable.close()
        assertTrue(closeable.isClosed, "Closeable should be closed after close() is called")
    }

    @Test
    @JsName("testUseExtensionFunctionClosesTheResource")
    fun `test use extension function closes the resource`() = runTest {
        val closeable = TestCloseable()
        closeable.use { resource ->
            assertFalse(resource.isClosed, "Resource should not be closed during use block execution")
        }

        assertTrue(closeable.isClosed, "Closeable should be closed after use block execution")
    }

    @Test
    @JsName("testUseReturnsUnitByDefault")
    fun `test use returns Unit by default`() = runTest {
        val closeable = TestCloseable()

        val returnObject = closeable.use { resource -> }

        assertTrue(closeable.isClosed, "Closeable should be closed after close() is called")
        assertEquals(Unit, returnObject, "The return object should return an object of type Unit")
    }

    @Test
    @JsName("testUseReturnsTheDesiredObject")
    fun `test use returns the desired object`() = runTest {
        val expectedMessage = "Hello world"
        val closeable = TestCloseable()

        val returnObject = closeable.use { resource ->
            expectedMessage
        }

        assertTrue(closeable.isClosed, "Closeable should be closed after close() is called")
        assertEquals(expectedMessage, returnObject, "The return object should be the desired object")
    }
}
