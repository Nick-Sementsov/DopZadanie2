package io.github.nicksementsov.chessmod.chess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChessAITest {
    private static Move choose(String fen, ChessAI.Difficulty difficulty) {
        return ChessAI.chooseMove(Position.fromFen(fen), difficulty, Set.of(), 2000, new Random(1));
    }

    @ParameterizedTest
    @EnumSource(ChessAI.Difficulty.class)
    void playsALegalMove(ChessAI.Difficulty difficulty) {
        Position start = Position.start();
        Move move = ChessAI.chooseMove(start, difficulty, Set.of(), 1000, new Random(7));
        assertTrue(start.legalMoves().contains(move));
    }

    @ParameterizedTest
    @EnumSource(ChessAI.Difficulty.class)
    void findsMateInOne(ChessAI.Difficulty difficulty) {
        assertEquals("a1a8", choose("6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1", difficulty).uci());
    }

    @Test
    void findsMateInTwo() {
        // Back-rank combination: 1. Rd8+ Rxd8 2. Rxd8#.
        String fen = "2r3k1/5ppp/8/8/8/8/3R1PPP/3R2K1 w - - 0 1";
        Move move = choose(fen, ChessAI.Difficulty.HARD);
        ChessGame game = new ChessGame(fen);
        game.play(move);
        assertTrue(!game.legalMoves().isEmpty(), "there is no mate in one here");
        for (Move reply : game.legalMoves()) {
            ChessGame line = new ChessGame(game.position().toFen());
            line.play(reply);
            Move finish = ChessAI.chooseMove(line.position(), ChessAI.Difficulty.HARD, Set.of(), 2000, new Random(3));
            line.play(finish);
            assertEquals(GameStatus.CHECKMATE, line.status(), "after " + move + " " + reply);
        }
    }

    @Test
    void takesAHangingQueen() {
        assertEquals("d1d5", choose("4k3/8/8/3q4/8/8/8/3QK3 w - - 0 1", ChessAI.Difficulty.MEDIUM).uci());
    }

    @Test
    void doesNotTakeAPoisonedPawn() {
        // Qxb7 would lose the queen to the rook on b8.
        Move move = choose("1r2k3/1p6/8/8/8/8/8/1Q2K3 w - - 0 1", ChessAI.Difficulty.MEDIUM);
        assertTrue(!move.uci().equals("b1b7"), "should not play " + move);
    }

    @Test
    void acceptsDrawOnlyWhenWorse() {
        // White has an extra queen: the computer playing black is glad to draw, playing white it refuses.
        Position position = Position.fromFen("4k3/8/8/8/8/8/8/3QK3 b - - 0 1");
        assertTrue(ChessAI.acceptsDraw(position, Side.BLACK));
        assertTrue(!ChessAI.acceptsDraw(position, Side.WHITE));
        assertTrue(!ChessAI.acceptsDraw(Position.start(), Side.BLACK), "an equal position is played on");
    }

    @Test
    void noMovesMeansNoChoice() {
        assertNull(choose("7k/5Q2/7K/8/8/8/8/8 b - - 0 1", ChessAI.Difficulty.HARD));
    }
}
