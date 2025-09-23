package ai.koog.agents.example

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

fun String.normalize(): String = replace("\\n", "\n").replace("\\", "")

fun JsonObject.intValue(key: String): Int = getValue(key).jsonPrimitive.int
fun JsonObject.floatValue(key: String): Float = getValue(key).jsonPrimitive.float
fun JsonObject.stringValue(key: String): String = getValue(key).jsonPrimitive.content

/**
 * Formats a message in a visually distinct way using ANSI colors and box drawing characters.
 * The message is displayed in a purple box with a border.
 *
 * @param message The message to be formatted.
 * @return The formatted message string with ANSI color codes and box drawing characters.
 */
fun consoleFormatMessage(message: String): String {
    val purple = "\u001B[35m"
    val boldPurple = "\u001B[1;35m"
    val reset = "\u001B[0m"
    val border = "═".repeat(140)

    val formattedMessage = buildString {
        append("\n")
        append("$purple╔$border$reset\n")
        message.trimIndent().lines().forEach { line ->
            append("$boldPurple║$reset $line\n")
        }
        append("$purple╚$border$reset\n")
    }
    println(formattedMessage)
    return formattedMessage
}
