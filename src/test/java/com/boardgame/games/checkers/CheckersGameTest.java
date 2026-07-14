package com.boardgame.games.checkers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckersGameTest {
    @Test
    void startsAfterTwoPlayers() {
        CheckersGame game = new CheckersGame();
        game.addPlayer("Alice");
        assertFalse(game.isStarted());
        game.addPlayer("Bob");
        assertTrue(game.isStarted());
    }

    @Test
    void blackMovesForward() {
        CheckersGame game = new CheckersGame();
        game.addPlayer("Alice"); // black
        game.addPlayer("Bob");   // white
        // Black piece at (2,1) can move to (3,0) or (3,2)
        game.move("Alice", "2,1,3,0");
        char[][] board = game.getBoard();
        assertEquals('.', board[2][1]);
        assertEquals('b', board[3][0]);
    }

    @Test
    void rejectsBackwardMoveForNonKing() {
        CheckersGame game = new CheckersGame();
        game.addPlayer("Alice");
        game.addPlayer("Bob");
        // Try to move black piece backward
        assertThrows(IllegalArgumentException.class, () -> game.move("Alice", "2,1,1,0"));
    }

    @Test
    void capturesOpponentPiece() {
        CheckersGame game = new CheckersGame();
        game.addPlayer("Alice");
        game.addPlayer("Bob");
        // Move black from (2,1) to (3,2)
        game.move("Alice", "2,1,3,2");
        // Move white from (5,0) to (4,1) — vacates (5,0)
        game.move("Bob", "5,0,4,1");
        // Black at (3,2) jumps white at (4,1), lands on (5,0)
        game.move("Alice", "3,2,5,0");
        char[][] board = game.getBoard();
        assertEquals('.', board[3][2]);
        assertEquals('.', board[4][1]); // captured
        assertEquals('b', board[5][0]);
    }

    @Test
    void gameTypeAndLimits() {
        CheckersGame game = new CheckersGame();
        assertEquals("CHECKERS", game.gameType());
        assertEquals(2, game.minPlayers());
        assertEquals(2, game.maxPlayers());
    }
}
