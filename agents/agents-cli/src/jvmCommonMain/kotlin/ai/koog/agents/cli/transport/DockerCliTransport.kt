package ai.koog.agents.cli.transport

import java.io.File
import kotlin.time.Duration

/**
 * A volume mapping for Docker.
 * @property hostPath Path on the host machine.
 * @property containerPath Path inside the container.
 * @property readOnly Whether the volume should be mounted as read-only.
 */
public class DockerVolume @JvmOverloads constructor(
    public val hostPath: File,
    public val containerPath: String,
    public val readOnly: Boolean = false,
) {
    /**
     * Converts the volume mapping to a Docker mount argument string.
     *
     * @return A string in the format "type=bind,source=<hostPath>,target=<containerPath>[,readonly]".
     */
    public fun toMountArg(): String = buildString {
        append("type=bind,source=")
        val path = hostPath.absolutePath
        append(path)
        append(",target=")
        append(containerPath)
        if (readOnly) append(",readonly")
    }
}

private fun MutableList<String>.mount(volume: DockerVolume) {
    add("--mount")
    add(volume.toMountArg())
}

/**
 * Executes CLI commands inside a Docker container.
 *
 * @property imageName The Docker image to use.
 * @property volumes List of volume mappings.
 */
public class DockerCliTransport @JvmOverloads constructor(
    private val imageName: String,
    private val volumes: List<DockerVolume> = emptyList(),
    dockerPath: String? = null,
) : ProcessCliTransport() {

    private val dockerPath = dockerPath ?: System.getenv("DOCKER_PATH") ?: "docker"

    private val isWindows = System.getProperty("os.name").lowercase().contains("win")

    override suspend fun checkAvailability(binaryPath: String, workspace: String, timeout: Duration?): CliAvailability {
        val dockerAvailability = checkAvailability(
            buildList {
                if (isWindows) {
                    add("cmd")
                    add("/c")
                }
                add(dockerPath)
                add("--version")
            },
            workspace,
            timeout
        )
        if (dockerAvailability is CliUnavailable) {
            return CliUnavailable("Docker is not available: ${dockerAvailability.reason}", dockerAvailability.cause)
        }
        return super.checkAvailability(binaryPath, workspace, timeout)
    }

    override fun buildCommand(
        command: List<String>,
        workspace: String,
        env: Map<String, String>
    ): List<String> = buildList {
        if (isWindows) {
            add("cmd")
            add("/c")
        }
        add(dockerPath)
        add("run")
        add("--rm")

        // Environment variables
        env.forEach { (key, value) ->
            add("-e")
            add("$key=$value")
        }

        // Workspace volume
        val dockerWorkspace = "/workspace"
        val workspaceVolume = DockerVolume(File(workspace), dockerWorkspace)

        mount(workspaceVolume)
        add("-w")
        add(dockerWorkspace)

        // Additional volumes
        volumes.forEach(::mount)

        add(imageName)
        addAll(command)
    }
}
