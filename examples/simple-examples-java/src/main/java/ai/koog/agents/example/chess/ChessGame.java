package ai.koog.agents.example.chess;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Simple chess game without checks for valid moves.
 * Stores a correct state of the board if the entered moves are valid.
 */
public class ChessGame {

    public enum Player {
        WHITE, BLACK, NONE;

        public Player opponent() {
            switch (this) {
                case WHITE: return BLACK;
                case BLACK: return WHITE;
                default: throw new IllegalArgumentException("No opponent for NONE player");
            }
        }
    }

    public enum PieceType {
        KING('K'), QUEEN('Q'), ROOK('R'), BISHOP('B'), KNIGHT('N'), PAWN('P'), NONE('*');

        private final char id;

        PieceType(char id) {
            this.id = id;
        }

        public char getId() {
            return id;
        }

        public static PieceType fromId(String id) {
            if (id.length() != 1) {
                throw new IllegalArgumentException("Invalid piece id: " + id);
            }
            char c = id.charAt(0);
            for (PieceType type : values()) {
                if (type.id == c) return type;
            }
            throw new IllegalArgumentException("Unknown piece: " + id);
        }
    }

    private enum Side {
        KING, QUEEN
    }

    private static class Piece {
        static final Piece NONE = new Piece(PieceType.NONE, Player.NONE);

        final PieceType pieceType;
        final Player player;

        Piece(PieceType pieceType, Player player) {
            if ((pieceType == PieceType.NONE) != (player == Player.NONE)) {
                throw new IllegalArgumentException("Invalid piece: " + pieceType + " " + player);
            }
            this.pieceType = pieceType;
            this.player = player;
        }

        char toChar() {
            switch (player) {
                case WHITE: return Character.toUpperCase(pieceType.id);
                case BLACK: return Character.toLowerCase(pieceType.id);
                default: return pieceType.id;
            }
        }

        boolean isNone() {
            return pieceType == PieceType.NONE;
        }
    }

    private static class Position {
        final int row;
        final char col;

        Position(int row, char col) {
            if (row < 1 || row > 8 || col < 'a' || col > 'h') {
                throw new IllegalArgumentException("Invalid position: " + col + row);
            }
            this.row = row;
            this.col = col;
        }

        Position(String position) {
            if (position.length() != 2) {
                throw new IllegalArgumentException("Invalid position: " + position);
            }
            this.col = position.charAt(0);
            try {
                this.row = Integer.parseInt(String.valueOf(position.charAt(1)));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Incorrect position: " + position);
            }
            if (row < 1 || row > 8 || col < 'a' || col > 'h') {
                throw new IllegalArgumentException("Invalid position: " + position);
            }
        }
    }

    private static class ChessBoard {
        private static final PieceType[] BACK_ROW = {
            PieceType.ROOK, PieceType.KNIGHT, PieceType.BISHOP, PieceType.QUEEN,
            PieceType.KING, PieceType.BISHOP, PieceType.KNIGHT, PieceType.ROOK
        };

        private final List<List<Piece>> board;

        ChessBoard() {
            board = new ArrayList<>();

            List<Piece> blackBack = new ArrayList<>();
            for (PieceType type : BACK_ROW) blackBack.add(new Piece(type, Player.BLACK));
            board.add(blackBack);

            List<Piece> blackPawns = new ArrayList<>();
            for (int i = 0; i < 8; i++) blackPawns.add(new Piece(PieceType.PAWN, Player.BLACK));
            board.add(blackPawns);

            for (int i = 0; i < 4; i++) {
                List<Piece> emptyRow = new ArrayList<>();
                for (int j = 0; j < 8; j++) emptyRow.add(Piece.NONE);
                board.add(emptyRow);
            }

            List<Piece> whitePawns = new ArrayList<>();
            for (int i = 0; i < 8; i++) whitePawns.add(new Piece(PieceType.PAWN, Player.WHITE));
            board.add(whitePawns);

            List<Piece> whiteBack = new ArrayList<>();
            for (PieceType type : BACK_ROW) whiteBack.add(new Piece(type, Player.WHITE));
            board.add(whiteBack);
        }

        Piece getPiece(Position pos) {
            return board.get(8 - pos.row).get(pos.col - 'a');
        }

        void setPiece(Position pos, Piece piece) {
            board.get(8 - pos.row).set(pos.col - 'a', piece);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                if (i > 0) sb.append("\n");
                sb.append(8 - i).append(" ");
                List<Piece> row = board.get(i);
                for (int j = 0; j < 8; j++) {
                    if (j > 0) sb.append(" ");
                    sb.append(row.get(j).toChar());
                }
            }
            sb.append("\n  a b c d e f g h");
            return sb.toString();
        }
    }

    public final String moveNotation =
        "0-0 - short castle\n" +
        "0-0-0 - long castle\n" +
        "<piece>-<from>-<to> - usual move. e.g. P-e2-e4\n" +
        "<piece>-<from>-<to>-<promotion> - promotion move. e.g. P-e7-e8-Q.\n" +
        "Piece names (uppercase for white, lowercase for black):\n" +
        "    P/p - pawn\n" +
        "    N/n - knight\n" +
        "    B/b - bishop\n" +
        "    R/r - rook\n" +
        "    Q/q - queen\n" +
        "    K/k - king";

    private final ChessBoard board = new ChessBoard();
    private Player currentPlayer = Player.WHITE;

    public void move(String move) {
        if (move.equals("0-0")) {
            castleMove(Side.KING);
        } else if (move.equals("0-0-0")) {
            castleMove(Side.QUEEN);
        } else {
            String[] parts = move.split("-");
            if (parts.length == 3) {
                usualMove(new Position(parts[1]), new Position(parts[2]));
            } else if (parts.length == 4) {
                if (PieceType.fromId(parts[0].toUpperCase()) != PieceType.PAWN) {
                    throw new IllegalArgumentException("Only pawn can be promoted");
                }
                Position to = new Position(parts[2]);
                usualMove(new Position(parts[1]), to);
                board.setPiece(to, new Piece(PieceType.fromId(parts[3].toUpperCase()), currentPlayer));
            } else {
                throw new IllegalArgumentException("Invalid move: " + move);
            }
        }
        currentPlayer = currentPlayer.opponent();
    }

    public String getBoard() {
        return board.toString();
    }

    public String currentPlayer() {
        return currentPlayer.name().toLowerCase();
    }

    private void usualMove(Position from, Position to) {
        if (board.getPiece(from).pieceType == PieceType.PAWN
                && from.col != to.col
                && board.getPiece(to).isNone()) {
            // en passant
            board.setPiece(new Position(from.row, to.col), Piece.NONE);
        }
        movePiece(from, to);
    }

    private void castleMove(Side side) {
        int row = (currentPlayer == Player.WHITE) ? 1 : 8;
        Position kingFrom = new Position(row, 'e');
        Position rookFrom, kingTo, rookTo;
        if (side == Side.KING) {
            rookFrom = new Position(row, 'h');
            kingTo = new Position(row, 'g');
            rookTo = new Position(row, 'f');
        } else {
            rookFrom = new Position(row, 'a');
            kingTo = new Position(row, 'c');
            rookTo = new Position(row, 'd');
        }
        movePiece(kingFrom, kingTo);
        movePiece(rookFrom, rookTo);
    }

    private void movePiece(Position from, Position to) {
        board.setPiece(to, board.getPiece(from));
        board.setPiece(from, Piece.NONE);
    }
}
