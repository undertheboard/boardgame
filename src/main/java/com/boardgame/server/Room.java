package com.boardgame.server;

import com.boardgame.games.BoardGame;

import java.util.List;

public final class Room {
    private final String id;
    private final String name;
    private BoardGame game;
    private boolean resultRecorded;
    private long finishedAt;

    public Room(String id, String name, BoardGame game) {
        this.id = id;
        this.name = name;
        this.game = game;
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
        this.game = freshGame;
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
