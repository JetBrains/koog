package ai.koog.agents.example.chess

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable

class Move(val game: ChessGame) : SimpleTool<Move.Args>() {
    @Serializable
    data class Args(
        @property:LLMDescription("The notation of the piece to move")
        val notation: String
    )

    override val argsSerializer = Args.serializer()

    override val name: String = "move"
    override val description: String = "Moves a piece according to the notation:\n${game.moveNotation}"

    override suspend fun doExecute(args: Args): String {
        game.move(args.notation)
        println(game.getBoard())
        return "Current state of the game:\n${game.getBoard()}\n${game.currentPlayer()} to move! Make the move!"
    }
}
