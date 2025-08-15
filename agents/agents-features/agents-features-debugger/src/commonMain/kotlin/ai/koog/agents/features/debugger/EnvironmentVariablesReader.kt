package ai.koog.agents.features.debugger

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
internal expect object EnvironmentVariablesReader {

    internal fun getEnvironmentVariable(name: String): String?
}
