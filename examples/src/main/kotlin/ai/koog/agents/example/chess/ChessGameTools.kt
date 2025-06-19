package ai.koog.agents.example.chess

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import kotlinx.serialization.Serializable

class Move(val game: ChessGame) : SimpleTool<Move.Args>() {
    @Serializable
    data class Args(val notation: String) : ToolArgs

    override val argsSerializer = Args.serializer()

    override val descriptor = ToolDescriptor(
        name = "move",
        description = "Moves a piece according to the notation:\n${game.moveNotation}",
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "notation",
                description = "The notation of the piece to move",
                type = ToolParameterType.String,
            )
        )
    )

    override suspend fun doExecute(args: Args): String {
        game.move(args.notation)
        println(game.getBoard())
        return "Current state of the game:\n${game.getBoard()}\n${game.currentPlayer()} to move! Make the move!"
    }
}