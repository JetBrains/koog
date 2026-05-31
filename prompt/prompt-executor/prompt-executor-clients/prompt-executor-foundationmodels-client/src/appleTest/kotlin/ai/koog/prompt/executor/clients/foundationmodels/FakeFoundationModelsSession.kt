package ai.koog.prompt.executor.clients.foundationmodels

/**
 * Test double for [FoundationModelsSession]. Never touches the framework, so client
 * logic runs green on the simulator with no model present.
 */
internal class FakeFoundationModelsSession(
    private val unavailableReason: String? = null,
    private val response: String = "",
    private val error: String? = null,
) : FoundationModelsSession {
    var lastPrompt: String? = null
        private set
    var lastInstructions: String? = null
        private set

    override fun availabilityReason(): String? = unavailableReason

    override suspend fun respond(prompt: String, instructions: String?): String {
        lastPrompt = prompt
        lastInstructions = instructions
        error?.let { throw FoundationModelsException.Generation(it) }
        return response
    }
}
