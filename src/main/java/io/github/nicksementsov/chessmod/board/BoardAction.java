package io.github.nicksementsov.chessmod.board;

/** What a player asks a chess board to do; sent from the client in a {@code ChessActionPacket}. */
public enum BoardAction {
    /** Data: the move in UCI notation, e.g. {@code e2e4} or {@code e7e8q}. */
    MOVE,
    /** Data: {@code mode;color;difficulty}, see {@link ChessBoardBlockEntity#encodeNewGame}. */
    NEW_GAME,
    UNDO,
    RESIGN
}
