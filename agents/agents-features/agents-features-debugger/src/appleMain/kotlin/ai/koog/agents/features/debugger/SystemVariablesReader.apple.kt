package ai.koog.agents.features.debugger

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
internal actual object SystemVariablesReader {

    // TODO: Add support for Apple platform
    internal actual fun getEnvironmentVariable(name: String): String? {
        throw NotImplementedError("Environment variables are not yet supported on Apple platforms")
    }

    // TODO: Add support for Apple platform
    internal actual fun getVMOption(name: String): String? {
        throw NotImplementedError("VM Options are not supported on Apple platforms")
    }
}
