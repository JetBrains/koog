package ai.koog.utils.system

public actual fun systemSecretsReader(): SystemSecretsReader {
    throw NotImplementedError("SystemSecretsReader is not yet supported on Android platform")
}
