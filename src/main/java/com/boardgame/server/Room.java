package com.boardgame.server;

import com.boardgame.games.BoardGame;

import java.util.List;

public final class Room {
    private final String id;
    private final String name;
    private java.util.Map<String, String> options;
    private BoardGame game;
    private boolean resultRecorded;
    private long finishedAt;
    private long emptySince;

    public Room(String id, String name, BoardGame game) {
        this(id, name, game, java.util.Map.of());
    }

    public Room(String id, String name, BoardGame game, java.util.Map<String, String> options) {
        this.id = id;
        this.name = name;
        this.game = game;
        this.options = java.util.Map.copyOf(options);
    }

    /** @return the rule options this room's games are created with */
    public synchronized java.util.Map<String, String> options() {
        return options;
    }

    /** Marks the room as empty (last player left), for delayed cleanup. */
    public synchronized void noteEmpty() {
        if (emptySince == 0) {
            emptySince = System.currentTimeMillis();
        }
    }

    /** Clears the empty marker (a player joined again). */
    public synchronized void noteOccupied() {
        emptySince = 0;
    }

    /** @return milliseconds since the room became empty, or -1 if occupied */
    public synchronized long emptySinceMillis() {
        return emptySince == 0 ? -1 : System.currentTimeMillis() - emptySince;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public synchronized BoardGame game() {
        return game;
    }

    /** Records when the game finished, for stale-room cleanup. */
    public synchronized void noteFinished() {
        if (finishedAt == 0) {
            finishedAt = System.currentTimeMillis();
        }
    }

    /** @return milliseconds since the game finished, or -1 if not finished */
    public synchronized long finishedSinceMillis() {
        return finishedAt == 0 ? -1 : System.currentTimeMillis() - finishedAt;
    }

    /**
     * Replaces the finished game with a fresh instance so the same players can
     * play again. Resets the leaderboard-recording and finished markers.
     */
    public synchronized void resetGame(BoardGame freshGame) {
        resetGame(freshGame, options);
    }

    /**
     * Replaces the finished game with a fresh instance created with new rule
     * options (used when a rematch changes the rules).
     */
    public synchronized void resetGame(BoardGame freshGame, java.util.Map<String, String> newOptions) {
        this.game = freshGame;
        this.options = java.util.Map.copyOf(newOptions);
        this.resultRecorded = false;
        this.finishedAt = 0;
    }

    public synchronized List<String> players() {
        return game.players();
    }

    public String gameType() {
        return game.gameType();
    }

    /**
     * Marks the finished game's result as recorded on the leaderboard.
     *
     * @return {@code true} the first time it is called, {@code false} afterwards
     */
    public synchronized boolean markResultRecorded() {
        if (resultRecorded) {
            return false;
        }
        resultRecorded = true;
        return true;
    }
}
