package com.boardgame.server;

import com.boardgame.games.BoardGame;

import java.util.List;

public final class Room {
    private final String id;
    private final String name;
    private final BoardGame game;

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

    public BoardGame game() {
        return game;
    }

    public synchronized List<String> players() {
        return game.players();
    }

    public String gameType() {
        return game.gameType();
    }
}
