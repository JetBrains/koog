package ai.koog.utils.system

public actual fun systemConfigReader(): SystemConfigReader {
    throw NotImplementedError("SystemConfigReader not yet supported on WasmJS platform")
}
