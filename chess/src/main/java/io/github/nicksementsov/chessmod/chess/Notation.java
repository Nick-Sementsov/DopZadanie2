package io.github.nicksementsov.chessmod.chess;

/** Standard algebraic notation (SAN), e.g. {@code Nbd2}, {@code exd6}, {@code e8=Q+}, {@code O-O-O#}. */
public final class Notation {
    private Notation() {
    }

    /** Describes a legal move played from the given position. */
    public static String san(Position position, Move move) {
        StringBuilder san = new StringBuilder();
        int type = Piece.typeId(move.piece());
        if (move.isCastle()) {
            san.append(move.to() > move.from() ? "O-O" : "O-O-O");
        } else {
            if (type != Piece.PAWN) {
                san.append(Character.toUpperCase(PieceType.byId(type).symbol()));
                appendDisambiguation(san, position, move);
            } else if (move.isCapture()) {
                san.append(Square.fileChar(move.from()));
            }
            if (move.isCapture()) {
                san.append('x');
            }
            san.append(Square.name(move.to()));
            if (move.isPromotion()) {
                san.append('=').append(Character.toUpperCase(PieceType.byId(move.promotion()).symbol()));
            }
        }

        Position next = position.apply(move);
        if (next.inCheck()) {
            san.append(next.legalMoves().isEmpty() ? '#' : '+');
        }
        return san.toString();
    }

    private static void appendDisambiguation(StringBuilder san, Position position, Move move) {
        boolean ambiguous = false;
        boolean sameFile = false;
        boolean sameRank = false;
        for (Move other : position.legalMoves()) {
            if (other.to() != move.to() || other.piece() != move.piece() || other.from() == move.from()) {
                continue;
            }
            ambiguous = true;
            sameFile |= Square.file(other.from()) == Square.file(move.from());
            sameRank |= Square.rank(other.from()) == Square.rank(move.from());
        }
        if (!ambiguous) {
            return;
        }
        if (!sameFile) {
            san.append(Square.fileChar(move.from()));
        } else if (!sameRank) {
            san.append(Square.rankChar(move.from()));
        } else {
            san.append(Square.name(move.from()));
        }
    }
}
