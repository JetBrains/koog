package ai.koog.protocol.cli

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies that `installDist` produces a binary named exactly `flow`.
 *
 * The binary name is controlled by `applicationName = "flow"` in build.gradle.kts.
 * Without that setting the Gradle application plugin defaults to the project name
 * (`agents-protocol-cli`), so this test would catch any accidental removal of
 * the override.
 *
 * Requires `installDist` to have run first — guaranteed by the `dependsOn` in
 * build.gradle.kts.
 */
class FlowCommandNameTest {

    private val binDir: File
        get() {
            val projectDir = checkNotNull(System.getProperty("projectDir")) {
                "System property 'projectDir' not set — run via Gradle, not directly"
            }
            return File(projectDir, "build/install/flow/bin")
        }

    @Test
    fun testInstalledBinaryIsNamedFlow() {
        val binary = File(binDir, "flow")
        assertTrue(
            binary.exists(),
            "Binary not found at ${binary.absolutePath}. " +
                "Ensure applicationName = \"flow\" is set in build.gradle.kts"
        )
    }

    @Test
    fun testInstalledBinaryIsExecutable() {
        val binary = File(binDir, "flow")
        assertTrue(binary.canExecute(), "Binary ${binary.absolutePath} is not executable")
    }

    @Test
    fun testHelpFlagExitsWithCode0AndPrintsUsage() {
        val result = runBinary("--help")
        assertEquals(0, result.exitCode, "Expected exit 0 for --help, stderr: ${result.stderr}")
        assertContains(result.stdout, "Usage: flow")
    }

    @Test
    fun testNoArgsExitsWithCode2AndPrintsUsage() {
        val result = runBinary()
        assertEquals(2, result.exitCode, "Expected exit 2 for no args, stderr: ${result.stderr}")
        assertContains(result.stdout, "Usage: flow")
    }

    // region Helpers

    private data class ProcessResult(val exitCode: Int, val stdout: String, val stderr: String)

    private fun runBinary(vararg args: String): ProcessResult {
        val binary = File(binDir, "flow")
        val process = ProcessBuilder(listOf(binary.absolutePath) + args.toList()).start()

        // Read both streams concurrently to avoid blocking if buffers fill
        var stdout = ""
        var stderr = ""
        val stdoutThread = Thread { stdout = process.inputStream.bufferedReader().readText() }
        val stderrThread = Thread { stderr = process.errorStream.bufferedReader().readText() }
        stdoutThread.start()
        stderrThread.start()
        val exitCode = process.waitFor()
        stdoutThread.join()
        stderrThread.join()

        return ProcessResult(exitCode, stdout, stderr)
    }

    // endregion
}
