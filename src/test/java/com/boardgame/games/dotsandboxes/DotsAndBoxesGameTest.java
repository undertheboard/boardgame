package com.boardgame.games.dotsandboxes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DotsAndBoxesGameTest {
    @Test
    void startsAfterTwoPlayers() {
        DotsAndBoxesGame game = new DotsAndBoxesGame();
        game.addPlayer("Alice");
        assertFalse(game.isStarted());
        game.addPlayer("Bob");
        assertTrue(game.isStarted());
    }

    @Test
    void completingBoxScoresAndGrantsExtraTurn() {
        DotsAndBoxesGame game = new DotsAndBoxesGame();
        game.addPlayer("Alice");
        game.addPlayer("Bob");
        // Complete box (0,0): needs H(0,0), H(1,0), V(0,0), V(0,1)
        game.move("Alice", "H|0|0"); // top
        game.move("Bob", "H|1|0");   // bottom
        game.move("Alice", "V|0|0"); // left
        // Now Alice draws right side to complete box
        game.move("Bob", "V|0|1");   // right - Bob completes it!
        assertEquals(1, game.getScore(1)); // Bob gets the point
        // Bob should get another turn (game not finished)
        assertFalse(game.isFinished());
    }

    @Test
    void rejectsDuplicateLine() {
        DotsAndBoxesGame game = new DotsAndBoxesGame();
        game.addPlayer("Alice");
        game.addPlayer("Bob");
        game.move("Alice", "H|0|0");
        assertThrows(IllegalArgumentException.class, () -> game.move("Bob", "H|0|0"));
    }

    @Test
    void gameTypeAndLimits() {
        DotsAndBoxesGame game = new DotsAndBoxesGame();
        assertEquals("DOTSANDBOXES", game.gameType());
        assertEquals(2, game.minPlayers());
        assertEquals(4, game.maxPlayers());
    }
}
