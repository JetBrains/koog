package ai.koog.agents.snapshot.feature

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "MissingKDocForPublicAPI")
public actual open class RollbackToolRegistryBuilder actual constructor() :
    RollbackToolRegistryBuilderCommon<RollbackToolRegistryBuilder>() {
    protected actual override fun self(): RollbackToolRegistryBuilder = this
}
