package com.boardgame.games.rps;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RockPaperScissorsGameTest {
    private static RockPaperScissorsGame newGame() {
        RockPaperScissorsGame game = new RockPaperScissorsGame();
        game.addPlayer("Alice");
        game.addPlayer("Bob");
        return game;
    }

    @Test
    void startsAfterTwoPlayers() {
        RockPaperScissorsGame game = new RockPaperScissorsGame();
        game.addPlayer("Alice");
        assertFalse(game.isStarted());
        game.addPlayer("Bob");
        assertTrue(game.isStarted());
    }

    @Test
    void rejectsInvalidChoice() {
        RockPaperScissorsGame game = newGame();
        assertThrows(IllegalArgumentException.class, () -> game.move("Alice", "LIZARD"));
    }

    @Test
    void rejectsDoubleChoice() {
        RockPaperScissorsGame game = newGame();
        game.move("Alice", "ROCK");
        assertThrows(IllegalStateException.class, () -> game.move("Alice", "PAPER"));
    }

    @Test
    void rejectsNonPlayer() {
        RockPaperScissorsGame game = newGame();
        assertThrows(IllegalArgumentException.class, () -> game.move("Mallory", "ROCK"));
    }

    @Test
    void firstToThreeWins() {
        RockPaperScissorsGame game = newGame();
        for (int i = 0; i < 3; i++) {
            game.move("Alice", "ROCK");
            game.move("Bob", "SCISSORS");
        }
        assertTrue(game.isFinished());
        assertEquals("Alice", game.winner());
    }

    @Test
    void tieRoundScoresNothing() {
        RockPaperScissorsGame game = newGame();
        game.move("Alice", "ROCK");
        game.move("Bob", "ROCK");
        assertFalse(game.isFinished());
        String snapshot = game.snapshot("Alice");
        assertTrue(snapshot.contains(":0"));
    }

    @Test
    void pendingPlayerIsCurrentInOwnSnapshot() {
        RockPaperScissorsGame game = newGame();
        game.move("Alice", "ROCK");
        String[] aliceFields = game.snapshot("Alice").split("\\|", -1);
        String[] bobFields = game.snapshot("Bob").split("\\|", -1);
        assertEquals("", aliceFields[2]);
        assertFalse(bobFields[2].isEmpty());
    }

    @Test
    void endsWhenPlayerLeaves() {
        RockPaperScissorsGame game = newGame();
        game.removePlayer("Alice");
        assertTrue(game.isFinished());
    }
}
