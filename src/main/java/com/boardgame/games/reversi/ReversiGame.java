package com.boardgame.games.reversi;

import com.boardgame.games.BoardGame;
import com.boardgame.protocol.Protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class ReversiGame implements BoardGame {
    public static final int SIZE = 8;
    private final char[][] board = new char[SIZE][SIZE];
    private final List<String> playerList = new ArrayList<>(2);
    private int currentIndex; // 0=Black, 1=White
    private boolean started;
    private boolean finished;
    private String message = "Waiting for players";
    private int consecutivePasses;

    public ReversiGame() {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                board[r][c] = '.';
            }
        }
        board[3][3] = 'W';
        board[3][4] = 'B';
        board[4][3] = 'B';
        board[4][4] = 'W';
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
        char disc = currentIndex == 0 ? 'B' : 'W';

        if (moveData.equals("PASS")) {
            if (hasValidMove(disc)) {
                throw new IllegalArgumentException("Cannot pass when valid moves exist");
            }
            consecutivePasses++;
            if (consecutivePasses >= 2) {
                endGame();
            } else {
                currentIndex = 1 - currentIndex;
                message = playerList.get(currentIndex) + "'s turn";
            }
            return;
        }

        String[] parts = moveData.split(",");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid move format (row,col or PASS)");
        }
        int row = Integer.parseInt(parts[0]);
        int col = Integer.parseInt(parts[1]);
        if (row < 0 || row >= SIZE || col < 0 || col >= SIZE) {
            throw new IllegalArgumentException("Out of bounds");
        }
        if (board[row][col] != '.') {
            throw new IllegalArgumentException("Cell is occupied");
        }
        List<int[]> flips = getFlips(row, col, disc);
        if (flips.isEmpty()) {
            throw new IllegalArgumentException("Invalid move: no captures");
        }
        board[row][col] = disc;
        for (int[] pos : flips) {
            board[pos[0]][pos[1]] = disc;
        }
        consecutivePasses = 0;

        // Check if opponent can move; if not, check if we can; if neither, game over
        char opp = currentIndex == 0 ? 'W' : 'B';
        if (hasValidMove(opp)) {
            currentIndex = 1 - currentIndex;
            message = playerList.get(currentIndex) + "'s turn";
        } else if (hasValidMove(disc)) {
            message = playerList.get(currentIndex) + " goes again (opponent has no moves)";
        } else {
            endGame();
        }
    }

    private void endGame() {
        finished = true;
        int black = 0;
        int white = 0;
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (board[r][c] == 'B') black++;
                else if (board[r][c] == 'W') white++;
            }
        }
        if (black > white) {
            message = playerList.get(0) + " wins! (Black " + black + "-" + white + ")";
        } else if (white > black) {
            message = playerList.get(1) + " wins! (White " + white + "-" + black + ")";
        } else {
            message = "Draw! (" + black + "-" + white + ")";
        }
    }

    private List<int[]> getFlips(int row, int col, char disc) {
        List<int[]> allFlips = new ArrayList<>();
        char opp = disc == 'B' ? 'W' : 'B';
        int[][] dirs = {{-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}};
        for (int[] dir : dirs) {
            List<int[]> lineFlips = new ArrayList<>();
            int r = row + dir[0];
            int c = col + dir[1];
            while (r >= 0 && r < SIZE && c >= 0 && c < SIZE && board[r][c] == opp) {
                lineFlips.add(new int[]{r, c});
                r += dir[0];
                c += dir[1];
            }
            if (!lineFlips.isEmpty() && r >= 0 && r < SIZE && c >= 0 && c < SIZE && board[r][c] == disc) {
                allFlips.addAll(lineFlips);
            }
        }
        return allFlips;
    }

    boolean hasValidMove(char disc) {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (board[r][c] == '.' && !getFlips(r, c, disc).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public synchronized String snapshot(String playerId) {
        StringBuilder sb = new StringBuilder();
        sb.append(started).append("|");
        sb.append(finished).append("|");
        sb.append(Protocol.encode(started ? playerList.get(currentIndex) : "")).append("|");
        String boardStr = IntStream.range(0, SIZE)
                .mapToObj(r -> new String(board[r]))
                .collect(Collectors.joining(","));
        sb.append(boardStr).append("|");
        String disc = playerList.indexOf(playerId) == 0 ? "B" : "W";
        sb.append(disc).append("|");
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
        return "REVERSI";
    }

    public synchronized char[][] getBoard() {
        char[][] copy = new char[SIZE][SIZE];
        for (int r = 0; r < SIZE; r++) {
            System.arraycopy(board[r], 0, copy[r], 0, SIZE);
        }
        return copy;
    }

    private void requireTurn(String playerId) {
        if (!started || finished) {
            throw new IllegalStateException("Game is not active");
        }
        if (!playerList.get(currentIndex).equals(playerId)) {
            throw new IllegalStateException("It is not your turn");
        }
    }
}
