package ai.koog.agents.core.agent.entity

import ai.koog.serialization.JSONSerializer

@Suppress("MissingKDocForPublicAPI", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public actual class AIAgentStorage internal actual constructor(
    internal actual val delegate: AIAgentStorageImpl,
) : AIAgentStorageAPI by delegate {
    public actual constructor(
        serializer: JSONSerializer,
    ) : this(
        delegate = AIAgentStorageImpl(serializer)
    )
}
