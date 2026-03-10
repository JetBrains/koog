@file:Suppress("MissingKDocForPublicAPI", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

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
