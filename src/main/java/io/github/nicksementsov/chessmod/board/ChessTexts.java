package io.github.nicksementsov.chessmod.board;

import io.github.nicksementsov.chessmod.chess.ChessAI;
import io.github.nicksementsov.chessmod.chess.ChessGame;
import io.github.nicksementsov.chessmod.chess.GameStatus;
import io.github.nicksementsov.chessmod.chess.Side;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.function.Function;

/** Translatable texts describing a game, shared by the chat announcements and the board screen. */
public final class ChessTexts {
    private ChessTexts() {
    }

    /** Why the game ended, e.g. "Checkmate!" or "White resigned." */
    public static MutableComponent reason(ChessGame game) {
        GameStatus status = game.status();
        if (status == GameStatus.RESIGNATION) {
            return Component.translatable("gui.chessmod.result.resignation." + game.resignedSide().serializedName());
        }
        return Component.translatable("gui.chessmod.result." + status.serializedName());
    }

    /**
     * Who won, e.g. "White wins (Steve)" or "Draw".
     *
     * @param names gives the name to show for the winning side, or {@code null} to show none
     */
    public static MutableComponent outcome(ChessGame game, Function<Side, String> names) {
        Side winner = game.winner();
        if (winner == null) {
            return Component.translatable("gui.chessmod.draw");
        }
        MutableComponent text = Component.translatable("gui.chessmod.winner." + winner.serializedName());
        String name = names.apply(winner);
        return name == null ? text : text.append(" (" + name + ")");
    }

    public static MutableComponent result(ChessGame game, Function<Side, String> names) {
        return reason(game).append(" ").append(outcome(game, names));
    }

    /**
     * A move in standard algebraic notation with the piece letters translated on the reader's side,
     * so a Russian player sees {@code Кf3} where an English one sees {@code Nf3}.
     */
    public static MutableComponent san(String san) {
        MutableComponent result = Component.empty();
        int index = 0;
        if (san.startsWith("O-O")) {
            boolean queenside = san.startsWith("O-O-O");
            result.append(Component.translatable(queenside ? "chessmod.notation.castle_long" : "chessmod.notation.castle_short"));
            index = queenside ? 5 : 3;
        }
        StringBuilder plain = new StringBuilder();
        for (; index < san.length(); index++) {
            char c = san.charAt(index);
            boolean pieceLetter = "KQRBN".indexOf(c) >= 0 && (index == 0 || san.charAt(index - 1) == '=');
            if (pieceLetter) {
                result.append(plain.toString());
                plain.setLength(0);
                result.append(Component.translatable("chessmod.notation." + c));
            } else {
                plain.append(c);
            }
        }
        return result.append(plain.toString());
    }

    public static MutableComponent difficultyName(ChessAI.Difficulty difficulty) {
        return Component.translatable("gui.chessmod.difficulty." + difficulty.serializedName());
    }
}
