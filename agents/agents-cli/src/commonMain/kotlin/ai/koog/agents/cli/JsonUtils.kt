package ai.koog.agents.cli

import ai.koog.agents.cli.transport.CliEvent
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Json configuration for cli agent implementations
 */
internal val json: Json = Json { ignoreUnknownKeys = true }

/**
 * Converts a list of agent events to a list of JSON objects from stdout
 */
internal fun toJsonStdoutEvents(events: List<CliEvent>, logger: KLogger): List<JsonObject> =
    events
        .filterIsInstance<CliEvent.Line>()
        .mapNotNull { line ->
            try {
                json.decodeFromString<JsonObject>(line.content).jsonObject
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn(e) { "Failed to parse cli event: ${line.content}" }
                null
            }
        }

/**
 * Converts a JSON primitive to a string
 */
internal val JsonElement.stringVal: String?
    get() = (this as? JsonPrimitive)?.contentOrNull

/**
 * Converts a JSON primitive to an integer
 */
internal val JsonElement.intVal: Int?
    get() = (this as? JsonPrimitive)?.intOrNull

/**
 * Converted a JSON primitive to a double
 */
internal val JsonElement.doubleVal: Double?
    get() = (this as? JsonPrimitive)?.doubleOrNull

/**
 * Converts a JSON primitive to a boolean
 */
internal val JsonElement.boolVal: Boolean?
    get() = (this as? JsonPrimitive)?.booleanOrNull
