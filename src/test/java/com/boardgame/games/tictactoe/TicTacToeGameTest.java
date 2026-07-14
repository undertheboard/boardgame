package com.boardgame.games.tictactoe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicTacToeGameTest {
    @Test
    void startsAfterTwoPlayers() {
        TicTacToeGame game = new TicTacToeGame();
        game.addPlayer("Alice");
        assertFalse(game.isStarted());
        game.addPlayer("Bob");
        assertTrue(game.isStarted());
    }

    @Test
    void rejectsOccupiedCell() {
        TicTacToeGame game = new TicTacToeGame();
        game.addPlayer("Alice");
        game.addPlayer("Bob");
        game.move("Alice", "4");
        assertThrows(IllegalArgumentException.class, () -> game.move("Bob", "4"));
    }

    @Test
    void detectsWin() {
        TicTacToeGame game = new TicTacToeGame();
        game.addPlayer("Alice");
        game.addPlayer("Bob");
        game.move("Alice", "0");
        game.move("Bob", "3");
        game.move("Alice", "1");
        game.move("Bob", "4");
        game.move("Alice", "2"); // top row
        assertTrue(game.isFinished());
    }

    @Test
    void detectsDraw() {
        TicTacToeGame game = new TicTacToeGame();
        game.addPlayer("Alice");
        game.addPlayer("Bob");
        // X O X
        // X X O
        // O X O
        game.move("Alice", "0"); // X
        game.move("Bob", "1");   // O
        game.move("Alice", "2"); // X
        game.move("Bob", "5");   // O
        game.move("Alice", "3"); // X
        game.move("Bob", "6");   // O
        game.move("Alice", "4"); // X
        game.move("Bob", "8");   // O
        game.move("Alice", "7"); // X
        assertTrue(game.isFinished());
    }

    @Test
    void rejectsMoveOutOfTurn() {
        TicTacToeGame game = new TicTacToeGame();
        game.addPlayer("Alice");
        game.addPlayer("Bob");
        assertThrows(IllegalStateException.class, () -> game.move("Bob", "0"));
    }

    @Test
    void gameTypeAndLimits() {
        TicTacToeGame game = new TicTacToeGame();
        assertEquals("TICTACTOE", game.gameType());
        assertEquals(2, game.minPlayers());
        assertEquals(2, game.maxPlayers());
    }
}
