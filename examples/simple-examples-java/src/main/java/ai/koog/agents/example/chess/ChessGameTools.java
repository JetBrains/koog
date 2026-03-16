package ai.koog.agents.example.chess;

import ai.koog.agents.core.tools.annotations.LLMDescription;
import ai.koog.agents.core.tools.annotations.Tool;
import ai.koog.agents.core.tools.reflect.ToolSet;

public class ChessGameTools implements ToolSet {
    private final ChessGame game;

    public ChessGameTools(ChessGame game) {
        this.game = game;
    }

    @Tool
    @LLMDescription("Moves a chess piece according to the notation:\n" +
        "0-0 - short castle\n" +
        "0-0-0 - long castle\n" +
        "<piece>-<from>-<to> - usual move. e.g. P-e2-e4\n" +
        "<piece>-<from>-<to>-<promotion> - promotion move. e.g. P-e7-e8-Q.\n" +
        "Piece names (uppercase for white, lowercase for black):\n" +
        "    P/p - pawn, N/n - knight, B/b - bishop, R/r - rook, Q/q - queen, K/k - king")
    public String move(
            @LLMDescription("The notation of the move to make")
            String notation
    ) {
        game.move(notation);
        System.out.println(game.getBoard());
        return "Current state of the game:\n" + game.getBoard() + "\n" + game.currentPlayer() + " to move! Make the move!";
    }
}
