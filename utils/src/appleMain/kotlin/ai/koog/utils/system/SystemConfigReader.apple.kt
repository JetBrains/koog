package ai.koog.utils.system

public actual fun systemConfigReader(): SystemConfigReader = UserDefaultsSystemConfigReader.shared
