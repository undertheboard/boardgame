package com.boardgame.plugin;

import com.boardgame.games.BoardGame;

/**
 * Service Provider Interface (SPI) that lets third parties add new games to the
 * Board Game Hub without modifying the hub itself.
 *
 * <h2>Overview</h2>
 * A {@code GamePlugin} is a factory plus metadata for one game type. The hub
 * discovers plugins at startup through {@link GameRegistry} and exposes each
 * discovered game type to clients, which can then create rooms for it exactly
 * like the built-in games.
 *
 * <h2>Writing a plugin</h2>
 * <ol>
 *   <li>Implement {@link BoardGame} with the rules of your game. The
 *       implementation must be thread-safe (the hub calls it from multiple
 *       client threads; synchronizing every public method is the pattern used
 *       by the built-in games).</li>
 *   <li>Implement this interface, returning a fresh {@link BoardGame} from
 *       {@link #createGame()} on every call.</li>
 *   <li>Declare the implementation in
 *       {@code META-INF/services/com.boardgame.plugin.GamePlugin} (standard
 *       {@link java.util.ServiceLoader} registration).</li>
 *   <li>Package everything as a jar and drop it into the server's
 *       {@code plugins/} directory (configurable via the {@code pluginsDir}
 *       server property), or add it to the server classpath.</li>
 * </ol>
 * See {@code docs/PLUGIN_API.md} for a complete step-by-step guide.
 *
 * <h2>Snapshot contract</h2>
 * {@link BoardGame#snapshot(String)} must produce a pipe-delimited string with
 * this minimum layout (fields 0-2 are read by the generic client UI):
 * <pre>
 *   started|finished|currentPlayerEncoded|...game specific fields...|messageEncoded
 * </pre>
 * <ul>
 *   <li>{@code started}/{@code finished} — {@code true}/{@code false}</li>
 *   <li>{@code currentPlayerEncoded} — the player whose turn it is, encoded
 *       with {@link com.boardgame.protocol.Protocol#encode(String)}; empty if
 *       the game has not started. For simultaneous-move games return the
 *       requesting player's own id while their input is still pending.</li>
 *   <li>{@code messageEncoded} — a human readable status line (always the last
 *       field), encoded with {@code Protocol.encode}.</li>
 * </ul>
 * Clients without a dedicated renderer for the game type fall back to a
 * generic text-based move UI, so any game is playable out of the box.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * public final class NimPlugin implements GamePlugin {
 *     public String gameType()      { return "NIM"; }
 *     public String displayName()   { return "Nim"; }
 *     public String description()   { return "Take 1-3 sticks; whoever takes the last stick loses."; }
 *     public BoardGame createGame() { return new NimGame(); }
 * }
 * }</pre>
 *
 * @see GameRegistry
 * @see BoardGame
 */
public interface GamePlugin {

    /**
     * Unique, stable identifier for the game type, e.g. {@code "NIM"}.
     * <p>Must be non-blank, at most 24 characters, and contain only the
     * characters {@code A-Z}, {@code 0-9} and {@code _} (it travels inside the
     * pipe/colon/comma-delimited wire protocol, so it must never contain
     * {@code |}, {@code :} or {@code ,}). Comparison is case-insensitive; the
     * registry normalizes types to upper case. If two plugins declare the same
     * type, the first registration wins and later ones are ignored.
     *
     * @return the game type identifier
     */
    String gameType();

    /**
     * Human friendly name shown in client UIs, e.g. {@code "Nim"}.
     *
     * @return the display name; defaults to {@link #gameType()}
     */
    default String displayName() {
        return gameType();
    }

    /**
     * Short one-line description of the game shown in client UIs.
     *
     * @return the description; defaults to an empty string
     */
    default String description() {
        return "";
    }

    /**
     * Creates a new, independent game instance for one room.
     * <p>Called every time a player creates a room of this game type. The
     * returned instance must not share mutable state with other instances.
     *
     * @return a fresh {@link BoardGame} in its initial (waiting for players) state
     */
    BoardGame createGame();
}
