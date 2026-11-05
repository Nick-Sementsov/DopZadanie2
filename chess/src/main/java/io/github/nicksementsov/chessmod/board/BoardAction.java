package io.github.nicksementsov.chessmod.board;

/** What a player asks a chess board to do; sent from the client in a {@code ChessActionPacket}. */
public enum BoardAction {
    /** Data: the move in UCI notation, e.g. {@code e2e4} or {@code e7e8q}. */
    MOVE,
    /** Data: {@code mode;color;difficulty}, see {@link ChessBoardBlockEntity#encodeNewGame}. */
    NEW_GAME,
    /** Takes back a move; against another player this asks the opponent first. */
    UNDO,
    RESIGN,
    /** Data: {@code white} or {@code black}. Takes a free seat in a two-player game. */
    SIT,
    /** Offers a draw; the computer answers at once, another player answers with {@link #ANSWER_OFFER}. */
    OFFER_DRAW,
    /** Data: {@code accept} or {@code decline}. Answers the opponent's take-back or draw offer. */
    ANSWER_OFFER,
    /** Data: a {@link io.github.nicksementsov.chessmod.style.ChessStyle} name. Changes the look of the board and pieces. */
    SET_STYLE
}
