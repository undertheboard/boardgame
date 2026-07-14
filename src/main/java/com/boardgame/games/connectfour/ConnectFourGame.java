package com.boardgame.games.connectfour;

import com.boardgame.games.BoardGame;
import com.boardgame.protocol.Protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class ConnectFourGame implements BoardGame {
    public static final int ROWS = 6;
    public static final int COLS = 7;

    private final char[][] grid = new char[ROWS][COLS];
    private final List<String> playerList = new ArrayList<>(2);
    private int currentIndex;
    private boolean started;
    private boolean finished;
    private String message = "Waiting for players";

    public ConnectFourGame() {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                grid[r][c] = '.';
            }
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
            message = playerList.get(0) + "'s turn (R)";
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
        int col = Integer.parseInt(moveData.trim());
        if (col < 0 || col >= COLS) {
            throw new IllegalArgumentException("Invalid column (0-6)");
        }
        int row = dropPiece(col);
        if (row < 0) {
            throw new IllegalArgumentException("Column is full");
        }
        char piece = currentIndex == 0 ? 'R' : 'Y';
        grid[row][col] = piece;
        if (checkWin(row, col, piece)) {
            finished = true;
            message = playerId + " wins!";
        } else if (isFull()) {
            finished = true;
            message = "Draw!";
        } else {
            currentIndex = 1 - currentIndex;
            message = playerList.get(currentIndex) + "'s turn (" + (currentIndex == 0 ? "R" : "Y") + ")";
        }
    }

    @Override
    public synchronized String snapshot(String playerId) {
        StringBuilder sb = new StringBuilder();
        sb.append(started).append("|");
        sb.append(finished).append("|");
        sb.append(Protocol.encode(started ? playerList.get(currentIndex) : "")).append("|");
        String boardStr = IntStream.range(0, ROWS)
                .mapToObj(r -> new String(grid[r]))
                .collect(Collectors.joining(","));
        sb.append(boardStr).append("|");
        String piece = playerList.indexOf(playerId) == 0 ? "R" : "Y";
        sb.append(piece).append("|");
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
    public synchronized List<String> players() {
        return new ArrayList<>(playerList);
    }

    @Override
    public String gameType() {
        return "CONNECTFOUR";
    }

    private void requireTurn(String playerId) {
        if (!started || finished) {
            throw new IllegalStateException("Game is not active");
        }
        if (!playerList.get(currentIndex).equals(playerId)) {
            throw new IllegalStateException("It is not your turn");
        }
    }

    private int dropPiece(int col) {
        for (int r = ROWS - 1; r >= 0; r--) {
            if (grid[r][col] == '.') {
                return r;
            }
        }
        return -1;
    }

    private boolean checkWin(int row, int col, char piece) {
        int[][] directions = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};
        for (int[] dir : directions) {
            int count = 1;
            count += countDirection(row, col, dir[0], dir[1], piece);
            count += countDirection(row, col, -dir[0], -dir[1], piece);
            if (count >= 4) return true;
        }
        return false;
    }

    private int countDirection(int row, int col, int dr, int dc, char piece) {
        int count = 0;
        int r = row + dr;
        int c = col + dc;
        while (r >= 0 && r < ROWS && c >= 0 && c < COLS && grid[r][c] == piece) {
            count++;
            r += dr;
            c += dc;
        }
        return count;
    }

    private boolean isFull() {
        for (int c = 0; c < COLS; c++) {
            if (grid[0][c] == '.') return false;
        }
        return true;
    }

    public synchronized char[][] getGrid() {
        char[][] copy = new char[ROWS][COLS];
        for (int r = 0; r < ROWS; r++) {
            System.arraycopy(grid[r], 0, copy[r], 0, COLS);
        }
        return copy;
    }
}
