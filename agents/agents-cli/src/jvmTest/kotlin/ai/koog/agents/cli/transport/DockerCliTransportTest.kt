package ai.koog.agents.cli.transport

import ai.koog.test.utils.DockerAvailableCondition
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.extension.ExtendWith
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertIs

@ExtendWith(DockerAvailableCondition::class)
class DockerCliTransportTest {
    private val imageName = "alpine:latest"

    private val tempdirName = "docker-test"
    private val filename = "test.txt"
    private val content = "volume-test-content"

    @Test
    fun testDockerVolumeMapping() = runTest {
        val tmpDir = Files.createTempDirectory(tempdirName)
        val containerPath = "/mnt/test"

        val testFile = tmpDir.resolve(filename).toFile()
        testFile.writeText(content)

        val volumes = listOf(DockerVolume(tmpDir.toFile(), containerPath))
        val command = listOf("cat", "$containerPath/$filename")

        try {
            val transport = DockerCliTransport(
                imageName = imageName,
                volumes = volumes
            )

            val events = transport.execute(
                command = command,
                workspace = "."
            ).toList()

            events.filterIsInstance<CliEvent.Stdout>()
                .firstOrNull()
                .shouldNotBeNull()
                .content.trim().shouldBe(content)
        } finally {
            tmpDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun testDockerWorkspaceMapping() = runTest {
        val tmpDir = Files.createTempDirectory("workspace-test")
        val testFile = tmpDir.resolve(filename).toFile()
        testFile.writeText(content)

        val command = listOf("cat", filename)

        try {
            val transport = DockerCliTransport(imageName)
            val events = transport.execute(
                command = command,
                workspace = tmpDir.toAbsolutePath().toString()
            ).toList()

            events.filterIsInstance<CliEvent.Stdout>()
                .firstOrNull()
                .shouldNotBeNull()
                .content.trim().shouldBe(content)
        } finally {
            tmpDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun testDockerWorkspaceMappingCurrentDir() = runTest {
        val testFile = File(filename)
        testFile.writeText(content)

        val command = listOf("cat", filename)

        try {
            val transport = DockerCliTransport(imageName)
            val events = transport.execute(
                command = command,
                workspace = "."
            ).toList()

            events.filterIsInstance<CliEvent.Stdout>()
                .firstOrNull()
                .shouldNotBeNull()
                .content.trim().shouldBe(content)
        } finally {
            testFile.delete()
        }
    }

    @Test
    fun testDockerEnvVars() = runTest {
        val transport = DockerCliTransport(imageName)
        val varName = "TEST_VAR"
        val varValue = "test-value"
        val env = mapOf(varName to varValue)
        val command = listOf("sh", "-c", "echo \$$varName")

        val events = transport.execute(
            command = command,
            workspace = ".",
            env = env
        ).toList()

        events.filterIsInstance<CliEvent.Stdout>()
            .firstOrNull()
            .shouldNotBeNull()
            .content.trim().shouldBe(varValue)
    }

    @Test
    fun testIncorrectDockerExecutable() = runTest {
        val transport = DockerCliTransport(imageName, dockerPath = "non-existent-path")

        val availability = transport.checkAvailability("java", ".")
        assertIs<CliUnavailable>(availability, "incorrect docker should be unavailable")
    }
}
