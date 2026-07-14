package com.boardgame.games.uno;

import java.util.Locale;
import java.util.Map;

/**
 * Configurable house rules for {@link UnoGame}.
 *
 * <p>Rules are supplied as simple string options (the same map that arrives on
 * the wire with {@code CREATE|UNO|roomName|options} or
 * {@code PLAYAGAIN|options}) and validated by {@link #fromOptions(Map)}.
 * Unknown option keys are ignored so newer clients can talk to older servers.
 *
 * <p>Supported options:
 * <ul>
 *   <li>{@code handSize} — starting hand size, {@value #MIN_HAND_SIZE}-{@value #MAX_HAND_SIZE}
 *       (default {@value #DEFAULT_HAND_SIZE})</li>
 *   <li>{@code maxPlayers} — maximum players in the room, 2-{@value #MAX_MAX_PLAYERS}
 *       (default {@value #DEFAULT_MAX_PLAYERS})</li>
 *   <li>{@code drawToMatch} — {@code true}/{@code false}; when enabled, a player
 *       who cannot (or chooses not to) play keeps drawing until they draw a
 *       playable card instead of drawing a single card (default {@code false})</li>
 *   <li>{@code playDrawn} — {@code true}/{@code false}; when enabled, a player
 *       who draws a playable card may immediately place it on the discard pile
 *       or pass to keep it in their hand (default {@code false}; when disabled
 *       the drawn card always goes to the hand and the turn passes)</li>
 * </ul>
 *
 * @param handSize    starting hand size for every player
 * @param maxPlayers  maximum number of players allowed in the game
 * @param drawToMatch when true, DRAW keeps drawing until a playable card is drawn
 * @param playDrawn   when true, a playable drawn card may be played immediately or kept
 */
public record UnoRules(int handSize, int maxPlayers, boolean drawToMatch, boolean playDrawn) {

    public static final int MIN_HAND_SIZE = 3;
    public static final int MAX_HAND_SIZE = 10;
    public static final int DEFAULT_HAND_SIZE = 7;
    public static final int MAX_MAX_PLAYERS = 8;
    public static final int DEFAULT_MAX_PLAYERS = 4;

    /** The classic rule set: 7 cards, up to 4 players, draw one card per turn. */
    public static final UnoRules DEFAULT =
            new UnoRules(DEFAULT_HAND_SIZE, DEFAULT_MAX_PLAYERS, false, false);

    public UnoRules {
        if (handSize < MIN_HAND_SIZE || handSize > MAX_HAND_SIZE) {
            throw new IllegalArgumentException(
                    "handSize must be " + MIN_HAND_SIZE + "-" + MAX_HAND_SIZE);
        }
        if (maxPlayers < 2 || maxPlayers > MAX_MAX_PLAYERS) {
            throw new IllegalArgumentException("maxPlayers must be 2-" + MAX_MAX_PLAYERS);
        }
    }

    /**
     * Builds a rule set from string options, applying defaults for missing keys.
     *
     * @param options option map (may be null or empty for all defaults)
     * @return the validated rule set
     * @throws IllegalArgumentException if a supplied value is invalid
     */
    public static UnoRules fromOptions(Map<String, String> options) {
        if (options == null || options.isEmpty()) {
            return DEFAULT;
        }
        int handSize = intOption(options, "handSize", DEFAULT_HAND_SIZE);
        int maxPlayers = intOption(options, "maxPlayers", DEFAULT_MAX_PLAYERS);
        boolean drawToMatch = Boolean.parseBoolean(
                options.getOrDefault("drawToMatch", "false").toLowerCase(Locale.ROOT));
        boolean playDrawn = Boolean.parseBoolean(
                options.getOrDefault("playDrawn", "false").toLowerCase(Locale.ROOT));
        return new UnoRules(handSize, maxPlayers, drawToMatch, playDrawn);
    }

    /** @return this rule set as an option map, suitable for the wire format */
    public Map<String, String> toOptions() {
        return Map.of(
                "handSize", Integer.toString(handSize),
                "maxPlayers", Integer.toString(maxPlayers),
                "drawToMatch", Boolean.toString(drawToMatch),
                "playDrawn", Boolean.toString(playDrawn));
    }

    private static int intOption(Map<String, String> options, String key, int fallback) {
        String raw = options.get(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Option " + key + " must be a number");
        }
    }
}
