package ai.koog.agents.core.system

internal actual object SystemVariablesReader {

    // TODO: Add support for JS platform
    internal actual fun getEnvironmentVariable(name: String): String? {
        throw NotImplementedError("Environment variables are not yet supported on JS platform")
    }

    // TODO: Add support for JS platform
    internal actual fun getVMOption(name: String): String? {
        throw NotImplementedError("VM Options are not supported on JS platform")
    }
}
