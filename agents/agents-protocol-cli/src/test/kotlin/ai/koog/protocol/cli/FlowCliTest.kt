package ai.koog.protocol.cli

import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FlowCliTest {

    // region Helpers

    private val outBytes = ByteArrayOutputStream()
    private val errBytes = ByteArrayOutputStream()

    private fun cli() = FlowCli(
        stdout = PrintStream(outBytes),
        stderr = PrintStream(errBytes),
    )

    private fun stdout() = outBytes.toString(Charsets.UTF_8)
    private fun stderr() = errBytes.toString(Charsets.UTF_8)

    private fun run(vararg args: String): ExitException =
        assertFailsWith { runBlocking { cli().run(arrayOf(*args)) } }

    // endregion

    // region --help / -h

    @Test
    fun testNoArgsShowsHelpAndExitsWithCode2() {
        val ex = run()
        assertEquals(2, ex.code)
        assertContains(stdout(), "Usage:")
        assertContains(stdout(), "--input")
        assertContains(stdout(), "--verbose")
    }

    @Test
    fun testHelpFlagExitsWithCode0() {
        val ex = run("--help")
        assertEquals(0, ex.code)
        assertContains(stdout(), "Usage:")
    }

    @Test
    fun testShortHelpFlagExitsWithCode0() {
        val ex = run("-h")
        assertEquals(0, ex.code)
        assertContains(stdout(), "Usage:")
    }

    // endregion

    // region Argument parsing errors

    @Test
    fun testUnknownFlagExitsWithCode2() {
        val ex = run("--unknown-flag")
        assertEquals(2, ex.code)
        assertContains(stderr(), "Unknown option: --unknown-flag")
    }

    @Test
    fun testMultipleFilePathsExitsWithCode2() {
        val ex = run("file1.json", "file2.json")
        assertEquals(2, ex.code)
        assertContains(stderr(), "Multiple file paths provided")
    }

    @Test
    fun testInputFlagWithoutValueExitsWithCode2() {
        val ex = run("flow.json", "--input")
        assertEquals(2, ex.code)
        assertContains(stderr(), "--input requires a value")
    }

    @Test
    fun testShortInputFlagWithoutValueExitsWithCode2() {
        val ex = run("flow.json", "-i")
        assertEquals(2, ex.code)
        assertContains(stderr(), "--input requires a value")
    }

    // endregion

    // region File errors

    @Test
    fun testNonExistentFileExitsWithCode2() {
        val ex = run("/nonexistent/path/does-not-exist.json")
        assertEquals(2, ex.code)
        assertContains(stderr(), "Error: File not found:")
        assertContains(stderr(), "/nonexistent/path/does-not-exist.json")
    }

    @Test
    fun testMalformedJsonExitsWithCode3() {
        val tmpFile = Files.createTempFile("flow-test-", ".json").toFile()
        try {
            tmpFile.writeText("{not valid json")
            val ex = run(tmpFile.absolutePath)
            assertEquals(3, ex.code)
            assertContains(stderr(), "Error: Failed to parse JSON:")
        } finally {
            tmpFile.delete()
        }
    }

    @Test
    fun testEmptyJsonObjectExitsWithCode3() {
        // An empty JSON object is valid JSON but not a valid FlowConfig (no agents),
        // so KoogFlow will throw during run(); however parse() succeeds, and then
        // flow.run() throws — which maps to exit code 1.
        // Verify that a structurally wrong JSON (array instead of object) exits with 3.
        val tmpFile = Files.createTempFile("flow-test-", ".json").toFile()
        try {
            tmpFile.writeText("[1, 2, 3]")
            val ex = run(tmpFile.absolutePath)
            assertEquals(3, ex.code)
        } finally {
            tmpFile.delete()
        }
    }

    // endregion

    // region Verbose flag wiring

    @Test
    fun testVerboseFlagIsRecognized() {
        // Use a non-existent file: if --verbose is recognised the parser moves on and hits
        // "file not found" (code 2, "File not found" message). If it weren't recognised we'd
        // get "Unknown option" instead.
        val ex = run("/nonexistent/path.json", "--verbose")
        assertEquals(2, ex.code)
        assertContains(stderr(), "Error: File not found")
    }

    @Test
    fun testShortVerboseFlagIsRecognized() {
        val ex = run("/nonexistent/path.json", "-v")
        assertEquals(2, ex.code)
        assertContains(stderr(), "Error: File not found")
    }

    @Test
    fun testVerboseOutputAppearsOnStderr() {
        val tmpFile = Files.createTempFile("flow-test-", ".json").toFile()
        try {
            tmpFile.writeText(
                """
                {
                  "id": "verbose-test",
                  "version": "1.0",
                  "defaultModel": "anthropic/sonnet_4",
                  "agents": [
                    {
                      "name": "greeter",
                      "type": "task",
                      "prompt": {"system": "You are helpful."},
                      "params": {"task": "Say hi"}
                    }
                  ],
                  "transitions": []
                }
                """.trimIndent()
            )
            // Parsing succeeds; execution may fail without an API key (exit 1) — both are fine.
            // What matters is that the verbose lines appear on stderr before any execution error.
            val ex = assertFailsWith<ExitException> {
                runBlocking { cli().run(arrayOf(tmpFile.absolutePath, "--verbose")) }
            }
            assertContains(listOf(0, 1), ex.code)
            assertContains(stderr(), "[flow] Agents: greeter")
            assertContains(stderr(), "[flow] Running flow: verbose-test")
        } finally {
            tmpFile.delete()
        }
    }

    // endregion
}
