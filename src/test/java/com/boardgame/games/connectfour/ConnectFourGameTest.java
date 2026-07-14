package com.boardgame.games.connectfour;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectFourGameTest {
    @Test
    void startsAfterTwoPlayers() {
        ConnectFourGame game = new ConnectFourGame();
        game.addPlayer("Alice");
        assertFalse(game.isStarted());
        game.addPlayer("Bob");
        assertTrue(game.isStarted());
    }

    @Test
    void detectsVerticalWin() {
        ConnectFourGame game = new ConnectFourGame();
        game.addPlayer("Alice");
        game.addPlayer("Bob");
        for (int i = 0; i < 4; i++) {
            game.move("Alice", "0");
            if (i < 3) game.move("Bob", "1");
        }
        assertTrue(game.isFinished());
    }

    @Test
    void detectsHorizontalWin() {
        ConnectFourGame game = new ConnectFourGame();
        game.addPlayer("Alice");
        game.addPlayer("Bob");
        game.move("Alice", "0");
        game.move("Bob", "0");
        game.move("Alice", "1");
        game.move("Bob", "1");
        game.move("Alice", "2");
        game.move("Bob", "2");
        game.move("Alice", "3"); // 4 in a row on bottom
        assertTrue(game.isFinished());
    }

    @Test
    void rejectsFullColumn() {
        ConnectFourGame game = new ConnectFourGame();
        game.addPlayer("Alice");
        game.addPlayer("Bob");
        for (int i = 0; i < 6; i++) {
            game.move(i % 2 == 0 ? "Alice" : "Bob", "0");
        }
        assertThrows(IllegalArgumentException.class, () -> game.move("Alice", "0"));
    }

    @Test
    void gameTypeAndLimits() {
        ConnectFourGame game = new ConnectFourGame();
        assertEquals("CONNECTFOUR", game.gameType());
        assertEquals(2, game.minPlayers());
        assertEquals(2, game.maxPlayers());
    }
}
