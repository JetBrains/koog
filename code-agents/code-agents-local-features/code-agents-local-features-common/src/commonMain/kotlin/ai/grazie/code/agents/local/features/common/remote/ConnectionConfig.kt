package ai.grazie.code.agents.local.features.common.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus

/**
 * Represents a configuration for managing a custom JSON serialization and deserialization setup
 * in the context of feature message handling and remote communication.
 *
 * This abstract class provides mechanisms to work with a configurable `Json` instance
 * and allows appending additional serializers modules dynamically.
 */
abstract class ConnectionConfig {

    private var _jsonConfig: Json = featureMessageJsonConfig()

    val jsonConfig: Json
        get() = _jsonConfig

    fun appendSerializersModule(module: SerializersModule) {
        _jsonConfig = Json(_jsonConfig) {
            this.serializersModule = this.serializersModule.plus(module)
        }
    }
}
