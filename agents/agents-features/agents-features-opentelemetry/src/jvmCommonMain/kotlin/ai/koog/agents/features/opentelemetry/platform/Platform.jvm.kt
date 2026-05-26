package ai.koog.agents.features.opentelemetry.platform

import java.util.Properties

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
internal actual object PlatformInfo {
    actual val osName: String? = System.getProperty("os.name")
    actual val osVersion: String? = System.getProperty("os.version")
    actual val osArch: String? = System.getProperty("os.arch")
}

internal actual fun errorTypeName(error: Throwable): String? = error.javaClass.typeName

internal actual fun loadProductProperties(): Map<String, String> {
    val props = Properties()
    val classLoader = PlatformInfo::class.java.classLoader
    classLoader.getResourceAsStream("product.properties")?.use { stream ->
        props.load(stream)
    }
    return props.entries.associate { (k, v) -> k.toString() to v.toString() }
}
