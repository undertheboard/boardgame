package com.boardgame.games.tictactoe;

import com.boardgame.games.BoardGame;
import com.boardgame.protocol.Protocol;

import java.util.ArrayList;
import java.util.List;

public final class TicTacToeGame implements BoardGame {
    private final char[] board = new char[9];
    private final List<String> playerList = new ArrayList<>(2);
    private int currentIndex;
    private boolean started;
    private boolean finished;
    private String winner;
    private String message = "Waiting for players";

    public TicTacToeGame() {
        for (int i = 0; i < 9; i++) {
            board[i] = '.';
        }
    }

    @Override
    public synchronized void addPlayer(String playerId) {
        if (playerList.size() >= 2) {
            throw new IllegalStateException("Game is full");
        }
        if (playerList.contains(playerId)) {
            throw new IllegalArgumentException("Already joined");
        }
        playerList.add(playerId);
        if (playerList.size() == 2) {
            started = true;
            message = playerList.get(0) + "'s turn (X)";
        } else {
            message = playerId + " joined, waiting for opponent";
        }
    }

    @Override
    public synchronized void removePlayer(String playerId) {
        if (playerList.remove(playerId)) {
            finished = true;
            message = "Game ended: player left";
        }
    }

    @Override
    public synchronized void move(String playerId, String moveData) {
        requireTurn(playerId);
        int cell = Integer.parseInt(moveData.trim());
        if (cell < 0 || cell > 8) {
            throw new IllegalArgumentException("Invalid cell (0-8)");
        }
        if (board[cell] != '.') {
            throw new IllegalArgumentException("Cell is occupied");
        }
        char mark = currentIndex == 0 ? 'X' : 'O';
        board[cell] = mark;
        if (checkWin(mark)) {
            finished = true;
            winner = playerId;
            message = playerId + " wins!";
        } else if (isFull()) {
            finished = true;
            message = "Draw!";
        } else {
            currentIndex = 1 - currentIndex;
            message = playerList.get(currentIndex) + "'s turn (" + (currentIndex == 0 ? "X" : "O") + ")";
        }
    }

    @Override
    public synchronized String snapshot(String playerId) {
        StringBuilder sb = new StringBuilder();
        sb.append(started).append("|");
        sb.append(finished).append("|");
        sb.append(Protocol.encode(started ? playerList.get(currentIndex) : "")).append("|");
        sb.append(new String(board)).append("|");
        sb.append(currentIndex == 0 ? "X" : "O").append("|");
        String mark = playerList.indexOf(playerId) == 0 ? "X" : "O";
        sb.append(mark).append("|");
        sb.append(Protocol.encode(message));
        return sb.toString();
    }

    @Override
    public int minPlayers() {
        return 2;
    }

    @Override
    public int maxPlayers() {
        return 2;
    }

    @Override
    public synchronized boolean isStarted() {
        return started;
    }

    @Override
    public synchronized boolean isFinished() {
        return finished;
    }

    @Override
    public synchronized String winner() {
        return winner;
    }

    @Override
    public synchronized List<String> players() {
        return new ArrayList<>(playerList);
    }

    @Override
    public String gameType() {
        return "TICTACTOE";
    }

    private void requireTurn(String playerId) {
        if (!started || finished) {
            throw new IllegalStateException("Game is not active");
        }
        if (!playerList.get(currentIndex).equals(playerId)) {
            throw new IllegalStateException("It is not your turn");
        }
    }

    private boolean checkWin(char mark) {
        int[][] lines = {
            {0, 1, 2}, {3, 4, 5}, {6, 7, 8},
            {0, 3, 6}, {1, 4, 7}, {2, 5, 8},
            {0, 4, 8}, {2, 4, 6}
        };
        for (int[] line : lines) {
            if (board[line[0]] == mark && board[line[1]] == mark && board[line[2]] == mark) {
                return true;
            }
        }
        return false;
    }

    private boolean isFull() {
        for (char c : board) {
            if (c == '.') return false;
        }
        return true;
    }

    public synchronized char[] getBoard() {
        return board.clone();
    }
}
