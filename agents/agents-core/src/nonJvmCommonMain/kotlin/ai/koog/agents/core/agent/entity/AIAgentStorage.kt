@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.agents.core.agent.entity

public actual class AIAgentStorage internal actual constructor(
    internal actual val delegate: AIAgentStorageImpl,
) : AIAgentStorageAPI by delegate {
    public actual constructor() : this(
        delegate = AIAgentStorageImpl()
    )

    internal actual suspend fun copy(): AIAgentStorage {
        return AIAgentStorage(delegate = delegate.copy())
    }
}
