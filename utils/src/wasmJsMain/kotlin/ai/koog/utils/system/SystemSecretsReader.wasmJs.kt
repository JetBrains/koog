package ai.koog.utils.system

public actual fun systemSecretsReader(): SystemSecretsReader {
    throw NotImplementedError("SystemSecretsReader not yet supported on WasmJS platform")
}
