package com.boardgame.games.gomoku;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GomokuGameTest {
    private static GomokuGame newGame() {
        GomokuGame game = new GomokuGame();
        game.addPlayer("Alice");
        game.addPlayer("Bob");
        return game;
    }

    @Test
    void startsAfterTwoPlayers() {
        GomokuGame game = new GomokuGame();
        game.addPlayer("Alice");
        assertFalse(game.isStarted());
        game.addPlayer("Bob");
        assertTrue(game.isStarted());
    }

    @Test
    void rejectsOccupiedCell() {
        GomokuGame game = newGame();
        game.move("Alice", "7,7");
        assertThrows(IllegalArgumentException.class, () -> game.move("Bob", "7,7"));
    }

    @Test
    void rejectsOutOfRange() {
        GomokuGame game = newGame();
        assertThrows(IllegalArgumentException.class, () -> game.move("Alice", "15,0"));
        assertThrows(IllegalArgumentException.class, () -> game.move("Alice", "0,-1"));
        assertThrows(IllegalArgumentException.class, () -> game.move("Alice", "junk"));
    }

    @Test
    void rejectsOutOfTurn() {
        GomokuGame game = newGame();
        assertThrows(IllegalStateException.class, () -> game.move("Bob", "0,0"));
    }

    @Test
    void detectsHorizontalWin() {
        GomokuGame game = newGame();
        for (int i = 0; i < 4; i++) {
            game.move("Alice", "0," + i);
            game.move("Bob", "1," + i);
        }
        game.move("Alice", "0,4");
        assertTrue(game.isFinished());
        assertEquals("Alice", game.winner());
    }

    @Test
    void detectsDiagonalWin() {
        GomokuGame game = newGame();
        for (int i = 0; i < 4; i++) {
            game.move("Alice", i + "," + i);
            game.move("Bob", "14," + i);
        }
        game.move("Alice", "4,4");
        assertTrue(game.isFinished());
        assertEquals("Alice", game.winner());
    }

    @Test
    void noWinnerWhilePlaying() {
        GomokuGame game = newGame();
        game.move("Alice", "7,7");
        assertNull(game.winner());
        assertFalse(game.isFinished());
    }

    @Test
    void endsWhenPlayerLeaves() {
        GomokuGame game = newGame();
        game.removePlayer("Bob");
        assertTrue(game.isFinished());
    }

    @Test
    void snapshotHasExpectedShape() {
        GomokuGame game = newGame();
        String[] fields = game.snapshot("Alice").split("\\|", -1);
        assertEquals(6, fields.length);
        assertEquals("true", fields[0]);
        assertEquals("false", fields[1]);
        assertEquals(15, fields[3].split(",").length);
        assertEquals("b", fields[4]);
    }
}
