package ai.koog.agents.example.chess

import java.lang.IllegalArgumentException

/**
 * Simple chess game without checks for valid moves.
 * Stores a correct state of the board if the entered moves are valid
 */
class ChessGame {
    private val board: ChessBoard = ChessBoard()
    private var currentPlayer: Player = Player.White
    val moveNotation: String = """
        0-0 - short castle
        0-0-0 - long castle
        <piece>-<from>-<to> - usual move. e.g. p-e2-e4
        <piece>-<from>-<to>-<promotion> - promotion move. e.g. p-e7-e8-q.
        Piece names:
            p - pawn
            n - knight
            b - bishop
            r - rook
            q - queen
            k - king
    """.trimIndent()

    fun move(move: String) {
        if (move == "0-0") {
            castleMove(Side.King)
        } else if (move == "0-0-0") {
            castleMove(Side.Queen)
        } else if (move.split("-").size == 3) {
            val (_, from, to) = move.split("-")
            usualMove(Position(from), Position(to))
        } else if (move.split("-").size == 4) {
            val (piece, from, to, promotion) = move.split("-")

            require(PieceType.fromId(piece) == PieceType.Pawn) { "Only pawn can be promoted" }

            usualMove(Position(from), Position(to))
            board.setPiece(Position(to), Piece(PieceType.fromId(promotion), currentPlayer))
        } else {
            throw IllegalArgumentException("Invalid move: $move")
        }

        updateCurrentPlayer()
    }

    fun getBoard(): String = board.toString()
    fun currentPlayer(): String = currentPlayer.name.lowercase()

    private fun updateCurrentPlayer() {
        currentPlayer = currentPlayer.opponent()
    }

    private fun usualMove(from: Position, to: Position) {
        if (board.getPiece(from).pieceType == PieceType.Pawn && from.col != to.col && board.getPiece(to).isNone()) {
            // the move is en passant
            board.setPiece(Position(from.row, to.col), Piece.None)
        }

        movePiece(from, to)
    }

    private fun castleMove(side: Side) {
        val row = if (currentPlayer == Player.White) 1 else 8
        val kingFrom = Position(row, 'e')
        val (rookFrom, kingTo, rookTo) = if (side == Side.King) {
            Triple(Position(row, 'h'), Position(row, 'g'), Position(row, 'f'))
        } else {
            Triple(Position(row, 'a'), Position(row, 'c'), Position(row, 'd'))
        }

        movePiece(kingFrom, kingTo)
        movePiece(rookFrom, rookTo)
    }

    private fun movePiece(from: Position, to: Position) {
        board.setPiece(to, board.getPiece(from))
        board.setPiece(from, Piece.None)
    }

    enum class Player {
        White, Black, None;

        fun opponent(): Player = when (this) {
            White -> Black
            Black -> White
            None -> throw IllegalArgumentException("No opponent for None player")
        }
    }

    enum class PieceType(val id: Char) {
        King('K'), Queen('Q'), Rook('R'), Bishop('B'), Knight('N'), Pawn('P'), None('*');

        companion object {
            fun fromId(id: String): PieceType {
                require(id.length == 1) { "Invalid piece id: $id" }

                return entries.first { it.id == id.single() }
            }
        }
    }

    private data class Piece(val pieceType: PieceType, val player: Player) {
        init {
            require((pieceType == PieceType.None) == (player == Player.None)) { "Invalid piece: $pieceType $player" }
        }

        fun toChar(): Char = when (player) {
            Player.White -> pieceType.id.uppercaseChar()
            Player.Black -> pieceType.id.lowercaseChar()
            Player.None -> pieceType.id
        }

        fun isNone(): Boolean = pieceType == PieceType.None

        companion object {
            val None = Piece(PieceType.None, Player.None)
        }
    }

    private data class Position(val row: Int, val col: Char) {
        init {
            require(row in 1..8 && col in 'a'..'h') { "Invalid position: $col$row" }
        }

        constructor(position: String): this(
            position[1].digitToIntOrNull() ?: throw IllegalArgumentException("Incorrect position: $position"),
            position[0],
        ) {
            require(position.length == 2) { "Invalid position: $position" }
        }
    }

    private class ChessBoard {
        private val backRow = listOf(
            PieceType.Rook, PieceType.Knight, PieceType.Bishop, PieceType.Queen, PieceType.King, PieceType.Bishop, PieceType.Knight, PieceType.Rook
        )

        private val board: List<MutableList<Piece>> = listOf(
            backRow.map { Piece(it, Player.Black) }.toMutableList(),
            List(8) { Piece(PieceType.Pawn, Player.Black) }.toMutableList(),
            List(8) { Piece.None }.toMutableList(),
            List(8) { Piece.None }.toMutableList(),
            List(8) { Piece.None }.toMutableList(),
            List(8) { Piece.None }.toMutableList(),
            List(8) { Piece(PieceType.Pawn, Player.White) }.toMutableList(),
            backRow.map { Piece(it, Player.White) }.toMutableList()
        )

        override fun toString(): String = board
            .withIndex().joinToString("\n") { (index, row) ->
                "${8 - index} ${row.map { it.toChar() }.joinToString(" ")}"
            } + "\n  a b c d e f g h"

        fun getPiece(position: Position): Piece = board[8 - position.row][position.col - 'a']
        fun setPiece(position: Position, piece: Piece) {
            board[8 - position.row][position.col - 'a'] = piece
        }
    }

    enum class Side {
        King, Queen
    }
}