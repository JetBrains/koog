package ai.koog.agents.example.chess

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable

class Move(val game: ChessGame) : SimpleTool<Move.Args>(
    argsSerializer = Args.serializer(),
    name = "move",
    description = "Moves a piece according to the notation:\n${game.moveNotation}"
) {
    @Serializable
    data class Args(
        @property:LLMDescription("The notation of the piece to move")
        val notation: String
    )

    override suspend fun execute(args: Args): String {
        game.move(args.notation)
        println(game.getBoard())
        return "Current state of the game:\n${game.getBoard()}\n${game.currentPlayer()} to move! Make the move!"
    }
}
