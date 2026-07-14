package com.boardgame.games.uno;

import java.util.LinkedHashMap;
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
 *       ({@code MOVE|PLAYDRAWN[|color]}) or keep it in their hand
 *       ({@code MOVE|KEEP}) (default {@code false}; when disabled the drawn
 *       card always goes to the hand and the turn passes)</li>
 *   <li>{@code callUno} — {@code true}/{@code false}; when enabled a player must
 *       call UNO ({@code MOVE|CALLUNO}) before playing their second-to-last
 *       card, otherwise they draw a 2 card penalty (default {@code false})</li>
 *   <li>{@code stackDraws} — {@code true}/{@code false}; when enabled, Draw Two
 *       and Wild Draw Four cards can be stacked onto a matching pending draw,
 *       passing the accumulated penalty to the next player (default {@code false})</li>
 *   <li>{@code sevenZero} — {@code true}/{@code false}; when enabled, playing a
 *       7 swaps your hand with the next player and playing a 0 rotates all
 *       hands in the direction of play (default {@code false})</li>
 * </ul>
 *
 * @param handSize    starting hand size for every player
 * @param maxPlayers  maximum number of players allowed in the game
 * @param drawToMatch when true, DRAW keeps drawing until a playable card is drawn
 * @param playDrawn   when true, a playable drawn card may be played immediately or kept
 * @param callUno     when true, players must call UNO before going to one card
 * @param stackDraws  when true, draw cards can be stacked to pass the penalty on
 * @param sevenZero   when true, 7 swaps hands with the next player and 0 rotates hands
 */
public record UnoRules(int handSize, int maxPlayers, boolean drawToMatch, boolean playDrawn,
                       boolean callUno, boolean stackDraws, boolean sevenZero) {

    public static final int MIN_HAND_SIZE = 3;
    public static final int MAX_HAND_SIZE = 10;
    public static final int DEFAULT_HAND_SIZE = 7;
    public static final int MAX_MAX_PLAYERS = 8;
    public static final int DEFAULT_MAX_PLAYERS = 4;

    /** The classic rule set: 7 cards, up to 4 players, no house rules. */
    public static final UnoRules DEFAULT = new UnoRules(
            DEFAULT_HAND_SIZE, DEFAULT_MAX_PLAYERS, false, false, false, false, false);

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
        return new UnoRules(
                intOption(options, "handSize", DEFAULT_HAND_SIZE),
                intOption(options, "maxPlayers", DEFAULT_MAX_PLAYERS),
                boolOption(options, "drawToMatch"),
                boolOption(options, "playDrawn"),
                boolOption(options, "callUno"),
                boolOption(options, "stackDraws"),
                boolOption(options, "sevenZero"));
    }

    /** @return this rule set as an option map, suitable for the wire format */
    public Map<String, String> toOptions() {
        Map<String, String> options = new LinkedHashMap<>();
        options.put("handSize", Integer.toString(handSize));
        options.put("maxPlayers", Integer.toString(maxPlayers));
        options.put("drawToMatch", Boolean.toString(drawToMatch));
        options.put("playDrawn", Boolean.toString(playDrawn));
        options.put("callUno", Boolean.toString(callUno));
        options.put("stackDraws", Boolean.toString(stackDraws));
        options.put("sevenZero", Boolean.toString(sevenZero));
        return options;
    }

    private static boolean boolOption(Map<String, String> options, String key) {
        return Boolean.parseBoolean(
                options.getOrDefault(key, "false").toLowerCase(Locale.ROOT));
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
