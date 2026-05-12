package ai.koog.integration.tests.utils.tools

import ai.koog.agents.core.tools.Tool
import ai.koog.serialization.typeToken
import kotlinx.serialization.Serializable

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
object PickColorTool : Tool<Unit, Colors>(
    argsType = typeToken<Unit>(),
    resultType = typeToken<Colors>(),
    name = "pick_color",
    description = "Picks a random color"
) {
    override suspend fun execute(args: Unit): Colors {
        return Colors.entries.toTypedArray().random()
    }
}

/**
 * Use to test tool with a list of enum arguments
 */
object PickColorFromListTool : Tool<PickColorFromListTool.Args, Colors>(
    argsType = typeToken<Args>(),
    resultType = typeToken<Colors>(),
    name = "pick_color",
    description = "Picks a random color from a given list of colors"
) {
    @Serializable
    data class Args(val colors: List<Colors>)

    override suspend fun execute(args: Args): Colors {
        return args.colors.random()
    }
}

/**
 * Use to test tool with enum arguments
 */
object PaintTool : Tool<Colors, Unit>(
    argsType = typeToken<Colors>(),
    resultType = typeToken<Unit>(),
    name = "paint",
    description = "Paints the picture with selected color"
) {
    override suspend fun execute(args: Colors) {
        println("Painting with color: $args")
    }
}
