package com.boardgame.games;

import java.util.List;

public interface BoardGame {
    void addPlayer(String playerId);

    void removePlayer(String playerId);

    void move(String playerId, String moveData);

    String snapshot(String playerId);

    int minPlayers();

    int maxPlayers();

    boolean isStarted();

    boolean isFinished();

    List<String> players();

    String gameType();
}
