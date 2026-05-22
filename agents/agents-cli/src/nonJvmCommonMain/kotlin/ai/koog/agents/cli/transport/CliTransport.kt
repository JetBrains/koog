package ai.koog.agents.cli.transport

import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

/**
 * Interface for cli transport implementations.
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
}
