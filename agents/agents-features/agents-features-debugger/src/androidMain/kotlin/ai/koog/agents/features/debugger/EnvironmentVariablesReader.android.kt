package ai.koog.agents.features.debugger

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
internal actual object EnvironmentVariablesReader {

    internal actual fun getEnvironmentVariable(name: String): String? {
        TODO("Environment variables are not supported on Android platform")
    }
}
