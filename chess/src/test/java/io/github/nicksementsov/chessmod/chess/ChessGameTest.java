package io.github.nicksementsov.chessmod.chess;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChessGameTest {
    private static ChessGame playFrom(String fen, String... moves) {
        ChessGame game = new ChessGame(fen);
        for (String uci : moves) {
            Move move = game.findMove(uci);
            assertTrue(move != null && game.play(move), "move should be legal: " + uci);
        }
        return game;
    }

    private static ChessGame play(String... moves) {
        return playFrom(Position.START_FEN, moves);
    }

    @Test
    void fenRoundTrip() {
        for (String fen : List.of(
                Position.START_FEN,
                "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
                "rnbqkbnr/pp1ppppp/8/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2",
                "8/8/8/8/8/8/8/K6k b - - 99 120")) {
            assertEquals(fen, Position.fromFen(fen).toFen());
        }
    }

    @Test
    void invalidFenIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Position.fromFen("not a fen"));
        assertThrows(IllegalArgumentException.class, () -> Position.fromFen("8/8/8/8/8/8/8/8 w - - 0 1"));
        assertThrows(IllegalArgumentException.class, () -> Position.fromFen("rnbqkbnr/pppppppp/9/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"));
    }

    @Test
    void foolsMateIsCheckmate() {
        ChessGame game = play("f2f3", "e7e5", "g2g4", "d8h4");
        assertEquals(GameStatus.CHECKMATE, game.status());
        assertEquals(Side.BLACK, game.winner());
        assertEquals("Qh4#", game.sanMoves().get(3));
        assertTrue(game.legalMoves().isEmpty());
    }

    @Test
    void stalemate() {
        ChessGame game = playFrom("7k/5Q2/6K1/8/8/8/8/8 w - - 0 1", "g6h6");
        assertFalse(game.position().inCheck());
        assertEquals(GameStatus.STALEMATE, game.status());
        assertNull(game.winner());
    }

    @Test
    void threefoldRepetition() {
        ChessGame game = play("g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1");
        assertEquals(GameStatus.ONGOING, game.status());
        game.play(game.findMove("f6g8"));
        assertEquals(GameStatus.THREEFOLD_REPETITION, game.status());
    }

    @Test
    void fiftyMoveRule() {
        ChessGame game = playFrom("4k3/8/8/8/8/8/8/R3K3 w - - 99 80", "a1a2");
        assertEquals(GameStatus.FIFTY_MOVE_RULE, game.status());
    }

    @Test
    void insufficientMaterial() {
        assertEquals(GameStatus.INSUFFICIENT_MATERIAL, new ChessGame("8/8/4k3/8/8/2B5/8/4K3 w - - 0 1").status());
        assertEquals(GameStatus.INSUFFICIENT_MATERIAL, new ChessGame("8/8/4k3/2b5/8/2B5/8/4K3 w - - 0 1").status());
        assertEquals(GameStatus.ONGOING, new ChessGame("8/8/4k3/1b6/8/2B5/8/4K3 w - - 0 1").status());
        assertEquals(GameStatus.ONGOING, new ChessGame("8/8/4k3/8/8/2N5/8/3NK3 w - - 0 1").status());
    }

    @Test
    void standardAlgebraicNotation() {
        ChessGame game = play("e2e4", "d7d5", "e4d5", "d8d5", "b1c3", "d5a5", "d2d4", "c7c6", "g1f3", "c8g4",
                "f1e2", "e7e6", "e1g1");
        assertEquals(List.of("e4", "d5", "exd5", "Qxd5", "Nc3", "Qa5", "d4", "c6", "Nf3", "Bg4", "Be2", "e6", "O-O"),
                game.sanMoves());
    }

    @Test
    void notationDisambiguatesAndMarksPromotion() {
        assertEquals("Nbd2", playFrom("4k3/8/8/8/8/5N2/8/1N2K3 w - - 0 1", "b1d2").sanMoves().get(0));
        assertEquals("R1a3", playFrom("4k3/8/8/8/R7/8/8/R3K3 w - - 0 1", "a1a3").sanMoves().get(0));
        assertEquals("Qh4e1", playFrom("2k5/8/8/8/4Q2Q/8/8/K6Q w - - 0 1", "h4e1").sanMoves().get(0));
        assertEquals("exd6", playFrom("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1", "e5d6").sanMoves().get(0));
        assertEquals("b8=Q+", playFrom("4k3/1P6/8/8/8/8/8/4K3 w - - 0 1", "b7b8q").sanMoves().get(0));
        assertEquals("O-O-O", playFrom("r3k3/8/8/8/8/8/8/4K3 b q - 0 1", "e8c8").sanMoves().get(0));
    }

    @Test
    void castlingIsBlockedThroughCheck() {
        ChessGame game = new ChessGame("4k3/8/8/8/8/8/5r2/R3K2R w KQ - 0 1");
        assertNull(game.findMove("e1g1"), "f1 is attacked by the rook");
        game = new ChessGame("4k3/8/8/8/8/8/3r4/R3K2R w KQ - 0 1");
        assertNull(game.findMove("e1c1"), "d1 is attacked by the rook");
        assertTrue(game.findMove("e1g1") != null);
    }

    @Test
    void promotionNeedsAPiece() {
        ChessGame game = new ChessGame("4k3/1P6/8/8/8/8/8/4K3 w - - 0 1");
        int from = Square.parse("b7");
        int to = Square.parse("b8");
        assertTrue(game.isPromotion(from, to));
        assertNull(game.findMove(from, to, 0));
        assertTrue(game.play(game.findMove(from, to, PieceType.KNIGHT.id())));
        assertEquals(Piece.of(Side.WHITE, PieceType.KNIGHT), game.position().pieceAt(to));
    }

    @Test
    void undoAndResign() {
        ChessGame game = play("e2e4", "e7e5");
        assertTrue(game.undo());
        assertEquals(1, game.ply());
        assertEquals(Side.BLACK, game.sideToMove());
        game.resign(Side.BLACK);
        assertEquals(GameStatus.RESIGNATION, game.status());
        assertEquals(Side.WHITE, game.winner());
        assertFalse(game.undo());
        assertTrue(game.legalMoves().isEmpty());
    }

    @Test
    void drawByAgreement() {
        ChessGame game = play("e2e4", "e7e5");
        game.agreeDraw();
        assertEquals(GameStatus.DRAW_AGREEMENT, game.status());
        assertTrue(game.status().isDraw());
        assertNull(game.winner());
        assertFalse(game.undo(), "an agreed draw is final");
        assertTrue(game.legalMoves().isEmpty());
    }

    @Test
    void countsMovesPerSide() {
        ChessGame game = new ChessGame();
        assertEquals(0, game.movesPlayedBy(Side.WHITE));
        assertEquals(0, game.movesPlayedBy(Side.BLACK));
        game = play("e2e4", "e7e5", "g1f3");
        assertEquals(2, game.movesPlayedBy(Side.WHITE));
        assertEquals(1, game.movesPlayedBy(Side.BLACK));
        // A game that starts with black to move
        ChessGame fromBlack = playFrom("4k3/8/8/8/8/8/8/R3K3 b - - 0 1", "e8d7");
        assertEquals(1, fromBlack.movesPlayedBy(Side.BLACK));
        assertEquals(0, fromBlack.movesPlayedBy(Side.WHITE));
    }

    @Test
    void historyIsRestoredAndStopsAtBadMoves() {
        ChessGame original = play("e2e4", "c7c5", "g1f3");
        ChessGame restored = ChessGame.fromHistory(original.startFen(), original.uciMoves(), null);
        assertEquals(original.position().toFen(), restored.position().toFen());
        assertEquals(original.sanMoves(), restored.sanMoves());

        ChessGame damaged = ChessGame.fromHistory(Position.START_FEN, List.of("e2e4", "e2e4", "e7e5"), null);
        assertEquals(1, damaged.ply());

        ChessGame resigned = ChessGame.fromHistory("garbage", List.of("d2d4"), Side.WHITE);
        assertEquals(GameStatus.RESIGNATION, resigned.status());
        assertEquals(1, resigned.ply());
    }

    @Test
    void enPassantOnlyCountsForRepetitionWhenPossible() {
        Position withSquare = Position.fromFen("4k3/8/8/8/4P3/8/8/4K3 b - e3 0 1");
        Position withoutSquare = Position.fromFen("4k3/8/8/8/4P3/8/8/4K3 b - - 0 1");
        assertEquals(withoutSquare.hash(), withSquare.hash());

        Position capturable = Position.fromFen("4k3/8/8/8/3pP3/8/8/4K3 b - e3 0 1");
        Position notCapturable = Position.fromFen("4k3/8/8/8/3pP3/8/8/4K3 b - - 0 1");
        assertFalse(capturable.hash() == notCapturable.hash());
    }
}
