package com.boardgame.games.gomoku;

import com.boardgame.games.BoardGame;
import com.boardgame.protocol.Protocol;

import java.util.ArrayList;
import java.util.List;

/**
 * Gomoku (Five in a Row) on a 15x15 board. Black moves first; the first
 * player to align five stones horizontally, vertically or diagonally wins.
 * Moves are sent as {@code row,col} (zero-based).
 */
public final class GomokuGame implements BoardGame {
    private static final int SIZE = 15;

    private final char[][] board = new char[SIZE][SIZE];
    private final List<String> playerList = new ArrayList<>(2);
    private int currentIndex;
    private boolean started;
    private boolean finished;
    private String winner;
    private String message = "Waiting for players";

    public GomokuGame() {
        for (char[] row : board) {
            java.util.Arrays.fill(row, '.');
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
            message = playerList.get(0) + "'s turn (Black)";
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
        String[] parts = moveData.trim().split(",");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Move must be row,col");
        }
        int row;
        int col;
        try {
            row = Integer.parseInt(parts[0].trim());
            col = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Move must be row,col");
        }
        if (row < 0 || row >= SIZE || col < 0 || col >= SIZE) {
            throw new IllegalArgumentException("Cell out of range (0-" + (SIZE - 1) + ")");
        }
        if (board[row][col] != '.') {
            throw new IllegalArgumentException("Cell is occupied");
        }
        char stone = currentIndex == 0 ? 'b' : 'w';
        board[row][col] = stone;
        if (hasFive(row, col, stone)) {
            finished = true;
            winner = playerId;
            message = playerId + " wins!";
        } else if (isFull()) {
            finished = true;
            message = "Draw!";
        } else {
            currentIndex = 1 - currentIndex;
            message = playerList.get(currentIndex) + "'s turn ("
                    + (currentIndex == 0 ? "Black" : "White") + ")";
        }
    }

    @Override
    public synchronized String snapshot(String playerId) {
        StringBuilder rows = new StringBuilder();
        for (int r = 0; r < SIZE; r++) {
            if (r > 0) {
                rows.append(',');
            }
            rows.append(board[r]);
        }
        String myStone = playerList.indexOf(playerId) == 0 ? "b" : "w";
        return started + "|" + finished + "|"
                + Protocol.encode(started && !finished ? playerList.get(currentIndex) : "") + "|"
                + rows + "|" + myStone + "|" + Protocol.encode(message);
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
        return "GOMOKU";
    }

    private void requireTurn(String playerId) {
        if (!started || finished) {
            throw new IllegalStateException("Game is not active");
        }
        if (!playerList.get(currentIndex).equals(playerId)) {
            throw new IllegalStateException("It is not your turn");
        }
    }

    private boolean hasFive(int row, int col, char stone) {
        int[][] directions = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};
        for (int[] dir : directions) {
            int count = 1;
            count += countRun(row, col, dir[0], dir[1], stone);
            count += countRun(row, col, -dir[0], -dir[1], stone);
            if (count >= 5) {
                return true;
            }
        }
        return false;
    }

    private int countRun(int row, int col, int dr, int dc, char stone) {
        int count = 0;
        int r = row + dr;
        int c = col + dc;
        while (r >= 0 && r < SIZE && c >= 0 && c < SIZE && board[r][c] == stone) {
            count++;
            r += dr;
            c += dc;
        }
        return count;
    }

    private boolean isFull() {
        for (char[] row : board) {
            for (char c : row) {
                if (c == '.') {
                    return false;
                }
            }
        }
        return true;
    }
}
