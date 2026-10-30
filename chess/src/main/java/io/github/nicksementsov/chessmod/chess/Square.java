package io.github.nicksementsov.chessmod.chess;

/** Squares are numbered 0..63: a1 = 0, b1 = 1, ..., h8 = 63. */
public final class Square {
    public static final int NONE = -1;

    private Square() {
    }

    public static int of(int file, int rank) {
        return rank * 8 + file;
    }

    public static int file(int square) {
        return square & 7;
    }

    public static int rank(int square) {
        return square >> 3;
    }

    public static boolean isLight(int square) {
        return ((file(square) + rank(square)) & 1) == 1;
    }

    public static char fileChar(int square) {
        return (char) ('a' + file(square));
    }

    public static char rankChar(int square) {
        return (char) ('1' + rank(square));
    }

    public static String name(int square) {
        return "" + fileChar(square) + rankChar(square);
    }

    /** Parses names like {@code "e4"}; returns {@link #NONE} for anything else. */
    public static int parse(String name) {
        if (name == null || name.length() != 2) {
            return NONE;
        }
        int file = name.charAt(0) - 'a';
        int rank = name.charAt(1) - '1';
        if (file < 0 || file > 7 || rank < 0 || rank > 7) {
            return NONE;
        }
        return of(file, rank);
    }
}
