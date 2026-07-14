package com.boardgame.games.puttputt;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PuttPuttGameTest {

    private static PuttPuttGame twoPlayerGame() {
        return twoPlayerGame(PuttPuttRules.defaults());
    }

    private static PuttPuttGame twoPlayerGame(PuttPuttRules rules) {
        PuttPuttGame game = new PuttPuttGame(rules, new Random(42));
        game.addPlayer("Alice");
        game.addPlayer("Bob");
        return game;
    }

    /** Burns through the current hole with tiny shots until every ball is holed. */
    private static void exhaustHole(PuttPuttGame game) {
        String startHole = game.snapshot("Alice").split("\\|")[9];
        for (int i = 0; i < PuttPuttGame.MAX_STROKES * 4 && !game.isFinished(); i++) {
            String[] fields = game.snapshot("Alice").split("\\|");
            if (!fields[9].equals(startHole)) {
                return; // rolled over to the next hole
            }
            String current = com.boardgame.protocol.Protocol.decode(fields[2]);
            game.move(current, "SHOT|90|1");
        }
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
    void defaultsToNineHoles() {
        assertEquals(9, PuttPuttRules.defaults().holes());
        PuttPuttGame game = twoPlayerGame();
        String holeField = game.snapshot("Alice").split("\\|")[9];
        assertEquals("1/9", holeField);
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
        PuttPuttGame game = twoPlayerGame(new PuttPuttRules(9,
                PuttPuttRules.Difficulty.EASY, PuttPuttRules.CourseSize.MEDIUM, false, 4));
        game.move("Alice", "SHOT|0|20");
        String snapshot = game.snapshot("Alice");
        String[] fields = snapshot.split("\\|");
        // fields: started|finished|current|dims|hole|walls|balls|shooter|path|holeNum|powerups|effects|message
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
    void ballStaysOnCourse() {
        PuttPuttGame game = twoPlayerGame();
        game.move("Alice", "SHOT|0|100");
        String alice = game.snapshot("Alice").split("\\|")[6].split(",")[0];
        double x = Double.parseDouble(alice.split(":")[1]);
        double y = Double.parseDouble(alice.split(":")[2]);
        assertTrue(x >= 1 && x <= game.width() - 1, "x stays on course");
        assertTrue(y >= 1 && y <= game.height() - 1, "y stays on course");
    }

    @Test
    void advancesToNextHoleWhenAllBallsHoled() {
        PuttPuttGame game = twoPlayerGame(new PuttPuttRules(2,
                PuttPuttRules.Difficulty.MEDIUM, PuttPuttRules.CourseSize.MEDIUM, false, 4));
        exhaustHole(game);
        assertFalse(game.isFinished(), "round continues to hole 2");
        String[] fields = game.snapshot("Alice").split("\\|");
        assertEquals("2/2", fields[9], "must be on the second hole");
        for (String entry : fields[6].split(",")) {
            String[] row = entry.split(":");
            assertFalse(Boolean.parseBoolean(row[4]), "balls reset for the new hole");
            assertEquals(PuttPuttGame.MAX_STROKES, Integer.parseInt(row[3]),
                    "total strokes carry over");
            assertEquals(0, Integer.parseInt(row[5]), "hole strokes reset");
        }
    }

    @Test
    void finishesAfterLastHoleWithFewestTotalStrokesWinner() {
        PuttPuttGame game = twoPlayerGame(new PuttPuttRules(1,
                PuttPuttRules.Difficulty.MEDIUM, PuttPuttRules.CourseSize.MEDIUM, false, 4));
        exhaustHole(game);
        assertTrue(game.isFinished());
        assertNotNull(game.winner());
    }

    @Test
    void powerUpsSpawnWhenEnabledAndNotWhenDisabled() {
        PuttPuttGame withPowerUps = twoPlayerGame();
        assertFalse(withPowerUps.snapshot("Alice").split("\\|")[10].isEmpty(),
                "power-ups must spawn by default");
        PuttPuttGame without = twoPlayerGame(new PuttPuttRules(9,
                PuttPuttRules.Difficulty.MEDIUM, PuttPuttRules.CourseSize.MEDIUM, false, 4));
        assertTrue(without.snapshot("Alice").split("\\|")[10].isEmpty(),
                "no power-ups when disabled");
    }

    @Test
    void courseSizeAffectsDimensions() {
        PuttPuttGame small = twoPlayerGame(new PuttPuttRules(9,
                PuttPuttRules.Difficulty.MEDIUM, PuttPuttRules.CourseSize.SMALL, true, 4));
        PuttPuttGame large = twoPlayerGame(new PuttPuttRules(9,
                PuttPuttRules.Difficulty.MEDIUM, PuttPuttRules.CourseSize.LARGE, true, 4));
        assertTrue(large.width() > small.width());
        assertTrue(large.height() > small.height());
    }

    @Test
    void difficultyAffectsWallCountAndHoleSize() {
        PuttPuttGame easy = twoPlayerGame(new PuttPuttRules(9,
                PuttPuttRules.Difficulty.EASY, PuttPuttRules.CourseSize.MEDIUM, false, 4));
        PuttPuttGame hard = twoPlayerGame(new PuttPuttRules(9,
                PuttPuttRules.Difficulty.HARD, PuttPuttRules.CourseSize.MEDIUM, false, 4));
        double easyRadius = Double.parseDouble(
                easy.snapshot("Alice").split("\\|")[4].split(",")[2]);
        double hardRadius = Double.parseDouble(
                hard.snapshot("Alice").split("\\|")[4].split(",")[2]);
        assertTrue(easyRadius > hardRadius, "easy cups are bigger");
    }

    @Test
    void rulesParseFromOptions() {
        PuttPuttRules rules = PuttPuttRules.fromOptions(Map.of(
                "holes", "3",
                "difficulty", "hard",
                "courseSize", "LARGE",
                "powerUps", "false",
                "maxPlayers", "2"));
        assertEquals(3, rules.holes());
        assertEquals(PuttPuttRules.Difficulty.HARD, rules.difficulty());
        assertEquals(PuttPuttRules.CourseSize.LARGE, rules.courseSize());
        assertFalse(rules.powerUps());
        assertEquals(2, rules.maxPlayers());
    }

    @Test
    void rulesRejectInvalidOptions() {
        assertThrows(IllegalArgumentException.class,
                () -> PuttPuttRules.fromOptions(Map.of("holes", "0")));
        assertThrows(IllegalArgumentException.class,
                () -> PuttPuttRules.fromOptions(Map.of("holes", "19")));
        assertThrows(IllegalArgumentException.class,
                () -> PuttPuttRules.fromOptions(Map.of("difficulty", "IMPOSSIBLE")));
        assertThrows(IllegalArgumentException.class,
                () -> PuttPuttRules.fromOptions(Map.of("powerUps", "maybe")));
        assertThrows(IllegalArgumentException.class,
                () -> PuttPuttRules.fromOptions(Map.of("maxPlayers", "9")));
    }

    @Test
    void rulesIgnoreUnknownKeysAndUseDefaults() {
        PuttPuttRules rules = PuttPuttRules.fromOptions(Map.of("someFutureKey", "x"));
        assertEquals(PuttPuttRules.defaults(), rules);
    }

    @Test
    void maxPlayersRuleIsEnforced() {
        PuttPuttGame game = new PuttPuttGame(new PuttPuttRules(9,
                PuttPuttRules.Difficulty.MEDIUM, PuttPuttRules.CourseSize.MEDIUM, true, 2),
                new Random(1));
        game.addPlayer("Alice");
        game.addPlayer("Bob");
        assertThrows(IllegalStateException.class, () -> game.addPlayer("Carol"));
    }

    @Test
    void leavingPlayerEndsShortGame() {
        PuttPuttGame game = twoPlayerGame();
        game.removePlayer("Alice");
        assertTrue(game.isFinished());
    }
}
