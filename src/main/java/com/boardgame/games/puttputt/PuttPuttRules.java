package com.boardgame.games.puttputt;

import java.util.Locale;
import java.util.Map;

/**
 * Configurable rules for a Putt Putt round, chosen by the room creator.
 *
 * <p>Options (wire format {@code key=value;key=value}, see
 * {@link com.boardgame.plugin.GamePlugin#createGame(Map)}):
 * <ul>
 *   <li>{@code holes} — number of holes in the round, 1-18 (default 9)</li>
 *   <li>{@code difficulty} — {@code EASY}, {@code MEDIUM} or {@code HARD};
 *       controls wall count and hole size (default MEDIUM)</li>
 *   <li>{@code courseSize} — {@code SMALL}, {@code MEDIUM} or {@code LARGE};
 *       controls the green's dimensions (default MEDIUM)</li>
 *   <li>{@code powerUps} — {@code true}/{@code false}: spawn collectible
 *       power-ups on the course (default true)</li>
 *   <li>{@code maxPlayers} — 2-4 (default 4)</li>
 * </ul>
 */
public record PuttPuttRules(int holes, Difficulty difficulty, CourseSize courseSize,
                            boolean powerUps, int maxPlayers) {

    /** Course difficulty: how cluttered the green is and how big the cup is. */
    public enum Difficulty {
        EASY(2, 2.8), MEDIUM(4, 2.2), HARD(6, 1.7);

        private final int wallCount;
        private final double holeRadius;

        Difficulty(int wallCount, double holeRadius) {
            this.wallCount = wallCount;
            this.holeRadius = holeRadius;
        }

        public int wallCount() {
            return wallCount;
        }

        public double holeRadius() {
            return holeRadius;
        }
    }

    /** Overall size of the green. */
    public enum CourseSize {
        SMALL(80, 48), MEDIUM(100, 60), LARGE(130, 78);

        private final double width;
        private final double height;

        CourseSize(double width, double height) {
            this.width = width;
            this.height = height;
        }

        public double width() {
            return width;
        }

        public double height() {
            return height;
        }
    }

    public PuttPuttRules {
        if (holes < 1 || holes > 18) {
            throw new IllegalArgumentException("holes must be 1-18");
        }
        if (maxPlayers < 2 || maxPlayers > 4) {
            throw new IllegalArgumentException("maxPlayers must be 2-4");
        }
        if (difficulty == null || courseSize == null) {
            throw new IllegalArgumentException("difficulty and courseSize are required");
        }
    }

    /** The standard nine-hole, medium difficulty, medium course, power-ups on. */
    public static PuttPuttRules defaults() {
        return new PuttPuttRules(9, Difficulty.MEDIUM, CourseSize.MEDIUM, true, 4);
    }

    /**
     * Parses rules from a room-creation option map. Unknown keys are ignored;
     * missing keys fall back to the defaults.
     *
     * @param options option map; never null
     * @return the parsed rules
     * @throws IllegalArgumentException if a value is present but invalid
     */
    public static PuttPuttRules fromOptions(Map<String, String> options) {
        PuttPuttRules defaults = defaults();
        int holes = parseInt(options, "holes", defaults.holes());
        Difficulty difficulty = parseEnum(options, "difficulty", Difficulty.class,
                defaults.difficulty());
        CourseSize courseSize = parseEnum(options, "courseSize", CourseSize.class,
                defaults.courseSize());
        boolean powerUps = parseBoolean(options, "powerUps", defaults.powerUps());
        int maxPlayers = parseInt(options, "maxPlayers", defaults.maxPlayers());
        return new PuttPuttRules(holes, difficulty, courseSize, powerUps, maxPlayers);
    }

    private static int parseInt(Map<String, String> options, String key, int fallback) {
        String value = options.get(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid value for " + key + ": " + value);
        }
    }

    private static boolean parseBoolean(Map<String, String> options, String key, boolean fallback) {
        String value = options.get(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("true") || normalized.equals("false")) {
            return Boolean.parseBoolean(normalized);
        }
        throw new IllegalArgumentException("Invalid value for " + key + ": " + value);
    }

    private static <E extends Enum<E>> E parseEnum(Map<String, String> options, String key,
                                                   Class<E> type, E fallback) {
        String value = options.get(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid value for " + key + ": " + value);
        }
    }
}
