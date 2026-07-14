package com.boardgame.games;

import java.util.List;

/**
 * A game hosted by the hub. Implementations must be thread-safe; the hub
 * invokes them from multiple client handler threads. See
 * {@link com.boardgame.plugin.GamePlugin} for how to contribute new games and
 * for the {@link #snapshot(String)} wire format contract.
 */
public interface BoardGame {
    void addPlayer(String playerId);

    void removePlayer(String playerId);

    void move(String playerId, String moveData);

    String snapshot(String playerId);

    /**
     * The player who won the game, used for leaderboard stat recording.
     *
     * @return the winning player's id once {@link #isFinished()} is true, or
     *         {@code null} if the game is unfinished or ended in a draw
     */
    default String winner() {
        return null;
    }

    int minPlayers();

    int maxPlayers();

    boolean isStarted();

    boolean isFinished();

    List<String> players();

    String gameType();
}
