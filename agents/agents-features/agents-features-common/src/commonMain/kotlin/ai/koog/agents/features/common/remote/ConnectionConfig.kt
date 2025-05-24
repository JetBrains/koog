package ai.koog.agents.features.common.remote

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
public abstract class ConnectionConfig {

    private var _jsonConfig: Json = featureMessageJsonConfig()

    /**
     * Provides access to the current JSON serialization and deserialization configuration.
     *
     * This property is used to retrieve the configured [Json] instance for handling feature messages
     * and remote communication. It incorporates custom serialization modules and settings
     * designed for efficient processing of [FeatureMessage] objects.
     *
     * The configuration can be modified dynamically by appending additional serializer modules
     * to accommodate new data types or adjust serialization behavior.
     *
     * This property is commonly utilized in contexts where the serialization or deserialization of
     * messages, such as [FeatureMessage], is required for communication between systems.
     *
     * @return The current [Json] instance used for serialization and deserialization.
     */
    public val jsonConfig: Json
        get() = _jsonConfig

    /**
     * Appends the specified serializers module to the existing JSON configuration.
     * This method allows dynamic addition of custom serialization and deserialization logic
     * by merging the provided module with the current serializers module.
     *
     * @param module The serializers module that needs to be appended to the existing JSON configuration.
     */
    public fun appendSerializersModule(module: SerializersModule) {
        _jsonConfig = Json(_jsonConfig) {
            this.serializersModule = this.serializersModule.plus(module)
        }
    }
}
