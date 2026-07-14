package com.boardgame.games.puttputt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PuttPuttGameTest {

    private static PuttPuttGame twoPlayerGame() {
        PuttPuttGame game = new PuttPuttGame();
        game.addPlayer("Alice");
        game.addPlayer("Bob");
        return game;
    }

    @Test
    void startsWithTwoPlayers() {
        PuttPuttGame game = new PuttPuttGame();
        game.addPlayer("Alice");
        assertFalse(game.isStarted());
        game.addPlayer("Bob");
        assertTrue(game.isStarted());
        assertEquals("PUTTPUTT", game.gameType());
    }

    @Test
    void rejectsShotsOutOfTurn() {
        PuttPuttGame game = twoPlayerGame();
        assertThrows(IllegalStateException.class, () -> game.move("Bob", "SHOT|0|50"));
    }

    @Test
    void rejectsInvalidShots() {
        PuttPuttGame game = twoPlayerGame();
        assertThrows(IllegalArgumentException.class, () -> game.move("Alice", "SHOT|0|0"));
        assertThrows(IllegalArgumentException.class, () -> game.move("Alice", "SHOT|0|101"));
        assertThrows(IllegalArgumentException.class, () -> game.move("Alice", "SHOT|abc|50"));
        assertThrows(IllegalArgumentException.class, () -> game.move("Alice", "JUMP|0|50"));
    }

    @Test
    void shotMovesBallAndPublishesAnimationPath() {
        PuttPuttGame game = twoPlayerGame();
        game.move("Alice", "SHOT|0|30");
        String snapshot = game.snapshot("Alice");
        String[] fields = snapshot.split("\\|");
        // fields: started|finished|current|dims|hole|walls|balls|shooter|path|message
        assertEquals("true", fields[0]);
        String balls = fields[6];
        String alice = balls.split(",")[0];
        double x = Double.parseDouble(alice.split(":")[1]);
        assertTrue(x > 8, "ball must have moved right from the tee");
        assertEquals(1, Integer.parseInt(alice.split(":")[3]), "one stroke taken");
        String path = fields[8];
        assertTrue(path.split(";").length > 2, "path must contain sampled points");
    }

    @Test
    void turnAlternatesBetweenPlayers() {
        PuttPuttGame game = twoPlayerGame();
        game.move("Alice", "SHOT|0|20");
        assertThrows(IllegalStateException.class, () -> game.move("Alice", "SHOT|0|20"));
        game.move("Bob", "SHOT|0|20");
    }

    @Test
    void ballBouncesOffWallsAndStaysOnCourse() {
        PuttPuttGame game = twoPlayerGame();
        // Full-power shot straight right: wall at x=30 blocks the tee line (y=20)
        game.move("Alice", "SHOT|0|100");
        String alice = game.snapshot("Alice").split("\\|")[6].split(",")[0];
        double x = Double.parseDouble(alice.split(":")[1]);
        double y = Double.parseDouble(alice.split(":")[2]);
        assertTrue(x >= 1 && x <= PuttPuttGame.WIDTH - 1, "x stays on course");
        assertTrue(y >= 1 && y <= PuttPuttGame.HEIGHT - 1, "y stays on course");
        assertTrue(x < 30, "ball must bounce back off the first wall");
    }

    @Test
    void maxStrokesEndsGameWithFewestStrokesWinner() {
        PuttPuttGame game = twoPlayerGame();
        // Alice burns all strokes with tiny shots; Bob too — game must finish.
        for (int i = 0; i < PuttPuttGame.MAX_STROKES && !game.isFinished(); i++) {
            if (!game.isFinished()) {
                game.move("Alice", "SHOT|90|1");
            }
            if (!game.isFinished()) {
                game.move("Bob", "SHOT|90|1");
            }
        }
        assertTrue(game.isFinished());
        assertNotNull(game.winner());
    }

    @Test
    void leavingPlayerEndsShortGame() {
        PuttPuttGame game = twoPlayerGame();
        game.removePlayer("Alice");
        assertTrue(game.isFinished());
    }
}
