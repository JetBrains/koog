package ai.koog.agents.features.debugger

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
internal actual object EnvironmentVariablesReader {

    // TODO: Add support for JS platform
    internal actual fun getEnvironmentVariable(name: String): String? {
        throw NotImplementedError("Environment variables are not supported on JS platform")
    }
}
