package ai.koog.agents.snapshot.feature

/**
 * A builder class responsible for creating a RollbackToolRegistry while managing associations
 * between tools and their corresponding rollback tools.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public expect open class RollbackToolRegistryBuilder constructor() :
    RollbackToolRegistryBuilderCommon<RollbackToolRegistryBuilder> {
    protected override fun self(): RollbackToolRegistryBuilder
}
