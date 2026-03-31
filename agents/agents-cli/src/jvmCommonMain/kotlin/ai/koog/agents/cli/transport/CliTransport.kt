package ai.koog.agents.cli.transport

import kotlinx.coroutines.flow.Flow
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlin.time.Duration

/**
 * Interface for running cli tools.
 */
public actual interface CliTransport {
    /**
     * Checks if the cli is available at the specified path.
     *
     * @param binaryPath The path to the cli binary.
     * @param workspace The workspace directory where the cli will be executed.
     * @param timeout The maximum duration to wait for the cli availability check.
     */
    public actual suspend fun checkAvailability(binaryPath: String, workspace: String, timeout: Duration?): CliAvailability

    /**
     * Executes the cli command and returns a Flow of AgentEvents.
     *
     * @param command The command to execute.
     * @param workspace The workspace directory where the cli will be executed.
     * @param env The environment variables to set for the cli process.
     * @param timeout The maximum duration to wait for the cli process to complete.
     */
    public actual fun execute(
        command: List<String>,
        workspace: String,
        env: Map<String, String>,
        timeout: Duration?
    ): Flow<CliEvent>

    /**
     * Default implementation of [ProcessCliTransport] using a ProcessBuilder to spawn a new process in available shell.
     */
    public object LocalProcess : ProcessCliTransport() {
        private val isWindows = System.getProperty("os.name").lowercase().contains("win")

        override fun buildCommand(
            command: List<String>,
            workspace: String,
            env: Map<String, String>
        ): List<String> = if (isWindows) {
            listOf("cmd", "/c") + command
        } else {
            command
        }
    }

    /**
     * Companion object defining factory methods for CliTransport implementations.
     */
    public companion object {
        /**
         * Default implementation of ProcessTransport using a ProcessBuilder to spawn a new process in available shell.
         */
        @JvmStatic
        @JvmName("getDefault")
        public fun default(): CliTransport = LocalProcess

        /**
         * Creates an instance of [CliTransport] that uses a local process via the [ProcessBuilder] to execute the command.
         */
        @JvmStatic
        public fun withLocalProcess(): CliTransport = LocalProcess

        /**
         * Creates an instance of [CliTransport] spawning a docker process with the specified image and optional volumes and executing the provided command in the container.
         */
        @JvmStatic
        @JvmOverloads
        public fun withDocker(
            imageName: String,
            volumes: List<DockerVolume> = emptyList()
        ): CliTransport = DockerCliTransport(imageName, volumes)
    }
}
