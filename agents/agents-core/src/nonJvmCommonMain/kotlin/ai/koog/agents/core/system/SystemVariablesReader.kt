package ai.koog.agents.core.system

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
internal actual object SystemVariablesReader {

    // TODO: Add support for non-jvm platforms
    internal actual fun getEnvironmentVariable(name: String): String? {
        throw NotImplementedError("Environment variables are not yet supported on non-jvm platforms")
    }

    // TODO: Add support for non-jvm platforms
    internal actual fun getVMOption(name: String): String? {
        throw NotImplementedError("VM Options are not yet supported on non-jvm platforms")
    }
}
