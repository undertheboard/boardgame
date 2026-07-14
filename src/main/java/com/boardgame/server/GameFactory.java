package com.boardgame.server;

import com.boardgame.games.BoardGame;
import com.boardgame.plugin.GameRegistry;

import java.util.List;

/**
 * Facade over {@link GameRegistry} kept for backwards compatibility.
 * All game types — built-in and plugin-provided — are resolved through the
 * registry, so plugin games work everywhere the built-ins do.
 *
 * @see com.boardgame.plugin.GamePlugin
 */
public final class GameFactory {
    private GameFactory() {
    }

    /** @return all currently registered game types, built-ins first */
    public static List<String> gameTypes() {
        return GameRegistry.types();
    }

    /**
     * Creates a fresh game of the given type.
     *
     * @throws IllegalArgumentException if the type is unknown
     */
    public static BoardGame create(String gameType) {
        return GameRegistry.create(gameType);
    }
}
