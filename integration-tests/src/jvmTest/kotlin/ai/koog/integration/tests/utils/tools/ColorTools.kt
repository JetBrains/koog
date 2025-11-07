package ai.koog.integration.tests.utils.tools

import ai.koog.agents.core.tools.Tool
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

@Serializable
enum class Colors {
    WHITE,
    BLACK,
    RED,
    ORANGE,
    YELLOW,
    GREEN,
    BLUE,
    INDIGO,
    VIOLET
}

/**
 * Use to test tool with empty arguments
 */
object PickColorTool : Tool<Unit, Colors>() {
    override val name = "pick_color"
    override val description = "Picks a random color"
    override val argsSerializer = Unit.serializer()
    override val resultSerializer = Colors.serializer()

    override suspend fun execute(args: Unit): Colors {
        return Colors.entries.toTypedArray().random()
    }
}

/**
 * Use to test tool with a list of enum arguments
 */
object PickColorFromListTool : Tool<List<Colors>, Colors>() {
    override val name = "pick_color"
    override val description = "Picks a random color from a given list of colors"
    override val argsSerializer = ListSerializer(Colors.serializer())
    override val resultSerializer = Colors.serializer()

    override suspend fun execute(args: List<Colors>): Colors {
        return args.random()
    }
}

/**
 * Use to test tool with enum arguments
 */
object PaintTool : Tool<Colors, Unit>() {
    override val name = "paint"
    override val description = "Paints the picture with selected color"
    override val argsSerializer = Colors.serializer()
    override val resultSerializer = Unit.serializer()

    override suspend fun execute(args: Colors) {
        println("Painting with color: $args")
    }
}
