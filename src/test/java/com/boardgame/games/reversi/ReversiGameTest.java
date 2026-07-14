package com.boardgame.games.reversi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReversiGameTest {
    @Test
    void startsAfterTwoPlayers() {
        ReversiGame game = new ReversiGame();
        game.addPlayer("Alice");
        assertFalse(game.isStarted());
        game.addPlayer("Bob");
        assertTrue(game.isStarted());
    }

    @Test
    void validMoveFlipsDiscs() {
        ReversiGame game = new ReversiGame();
        game.addPlayer("Alice"); // Black
        game.addPlayer("Bob");   // White
        // Initial: B at (3,4),(4,3), W at (3,3),(4,4)
        // Black plays at (2,3) to flip (3,3)
        game.move("Alice", "2,3");
        char[][] board = game.getBoard();
        assertEquals('B', board[2][3]);
        assertEquals('B', board[3][3]); // flipped
    }

    @Test
    void rejectsInvalidMove() {
        ReversiGame game = new ReversiGame();
        game.addPlayer("Alice");
        game.addPlayer("Bob");
        assertThrows(IllegalArgumentException.class, () -> game.move("Alice", "0,0"));
    }

    @Test
    void passWhenNoValidMoves() {
        ReversiGame game = new ReversiGame();
        game.addPlayer("Alice");
        game.addPlayer("Bob");
        // Alice cannot PASS when she has valid moves
        assertThrows(IllegalArgumentException.class, () -> game.move("Alice", "PASS"));
    }

    @Test
    void gameTypeAndLimits() {
        ReversiGame game = new ReversiGame();
        assertEquals("REVERSI", game.gameType());
        assertEquals(2, game.minPlayers());
        assertEquals(2, game.maxPlayers());
    }
}
