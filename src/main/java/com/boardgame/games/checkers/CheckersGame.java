package com.boardgame.games.checkers;

import com.boardgame.games.BoardGame;
import com.boardgame.protocol.Protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class CheckersGame implements BoardGame {
    public static final int SIZE = 8;
    // Pieces: 'b' = black, 'B' = black king, 'w' = white, 'W' = white king, '.' = empty
    private final char[][] board = new char[SIZE][SIZE];
    private final List<String> playerList = new ArrayList<>(2);
    private int currentIndex; // 0 = black (top), 1 = white (bottom)
    private boolean started;
    private boolean finished;
    private String message = "Waiting for players";

    public CheckersGame() {
        initBoard();
    }

    private void initBoard() {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                board[r][c] = '.';
            }
        }
        // Black pieces on top 3 rows (dark squares)
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < SIZE; c++) {
                if ((r + c) % 2 == 1) {
                    board[r][c] = 'b';
                }
            }
        }
        // White pieces on bottom 3 rows
        for (int r = 5; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if ((r + c) % 2 == 1) {
                    board[r][c] = 'w';
                }
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
        // moveData format: "fromRow,fromCol,toRow,toCol" or chain jumps "r1,c1,r2,c2,r3,c3..."
        String[] parts = moveData.split(",");
        if (parts.length < 4 || parts.length % 2 != 0) {
            throw new IllegalArgumentException("Invalid move format");
        }
        int fromRow = Integer.parseInt(parts[0]);
        int fromCol = Integer.parseInt(parts[1]);

        char piece = board[fromRow][fromCol];
        if (!isOwnPiece(piece)) {
            throw new IllegalArgumentException("Not your piece");
        }

        // Execute move steps
        for (int i = 2; i < parts.length; i += 2) {
            int toRow = Integer.parseInt(parts[i]);
            int toCol = Integer.parseInt(parts[i + 1]);
            executeStep(fromRow, fromCol, toRow, toCol, piece);
            fromRow = toRow;
            fromCol = toCol;
            piece = board[fromRow][fromCol]; // might be promoted
        }

        // Promote to king
        if (board[fromRow][fromCol] == 'b' && fromRow == SIZE - 1) {
            board[fromRow][fromCol] = 'B';
        } else if (board[fromRow][fromCol] == 'w' && fromRow == 0) {
            board[fromRow][fromCol] = 'W';
        }

        // Check win
        if (!hasAnyPieces(1 - currentIndex) || !hasAnyMoves(1 - currentIndex)) {
            finished = true;
            message = playerId + " wins!";
        } else {
            currentIndex = 1 - currentIndex;
            String color = currentIndex == 0 ? "Black" : "White";
            message = playerList.get(currentIndex) + "'s turn (" + color + ")";
        }
    }

    private void executeStep(int fromRow, int fromCol, int toRow, int toCol, char piece) {
        if (toRow < 0 || toRow >= SIZE || toCol < 0 || toCol >= SIZE) {
            throw new IllegalArgumentException("Move out of bounds");
        }
        if (board[toRow][toCol] != '.') {
            throw new IllegalArgumentException("Destination not empty");
        }
        int dr = toRow - fromRow;
        int dc = toCol - fromCol;
        if (Math.abs(dr) != Math.abs(dc)) {
            throw new IllegalArgumentException("Must move diagonally");
        }
        boolean isKing = piece == 'B' || piece == 'W';
        if (Math.abs(dr) == 1) {
            // Simple move
            if (!isKing) {
                int forwardDir = (piece == 'b' || piece == 'B') ? 1 : -1;
                if (dr != forwardDir) {
                    throw new IllegalArgumentException("Non-kings can only move forward");
                }
            }
        } else if (Math.abs(dr) == 2) {
            // Jump
            int midRow = fromRow + dr / 2;
            int midCol = fromCol + dc / 2;
            char midPiece = board[midRow][midCol];
            if (midPiece == '.' || isSameColor(piece, midPiece)) {
                throw new IllegalArgumentException("Must jump over opponent piece");
            }
            board[midRow][midCol] = '.';
        } else {
            throw new IllegalArgumentException("Invalid move distance");
        }
        board[toRow][toCol] = piece;
        board[fromRow][fromCol] = '.';
    }

    private boolean isOwnPiece(char piece) {
        if (currentIndex == 0) return piece == 'b' || piece == 'B';
        return piece == 'w' || piece == 'W';
    }

    private boolean isSameColor(char a, char b) {
        boolean aBlack = a == 'b' || a == 'B';
        boolean bBlack = b == 'b' || b == 'B';
        return aBlack == bBlack;
    }

    private boolean hasAnyPieces(int playerIdx) {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                char p = board[r][c];
                if (playerIdx == 0 && (p == 'b' || p == 'B')) return true;
                if (playerIdx == 1 && (p == 'w' || p == 'W')) return true;
            }
        }
        return false;
    }

    private boolean hasAnyMoves(int playerIdx) {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                char p = board[r][c];
                if (playerIdx == 0 && (p == 'b' || p == 'B') && canMove(r, c, p)) return true;
                if (playerIdx == 1 && (p == 'w' || p == 'W') && canMove(r, c, p)) return true;
            }
        }
        return false;
    }

    private boolean canMove(int r, int c, char piece) {
        boolean isKing = piece == 'B' || piece == 'W';
        int forwardDir = (piece == 'b' || piece == 'B') ? 1 : -1;
        int[] drs = isKing ? new int[]{1, -1} : new int[]{forwardDir};
        for (int dr : drs) {
            for (int dc : new int[]{-1, 1}) {
                int nr = r + dr;
                int nc = c + dc;
                if (nr >= 0 && nr < SIZE && nc >= 0 && nc < SIZE && board[nr][nc] == '.') {
                    return true;
                }
                // Jump
                int jr = r + 2 * dr;
                int jc = c + 2 * dc;
                if (jr >= 0 && jr < SIZE && jc >= 0 && jc < SIZE && board[jr][jc] == '.'
                        && board[nr][nc] != '.' && !isSameColor(piece, board[nr][nc])) {
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
        String color = playerList.indexOf(playerId) == 0 ? "B" : "W";
        sb.append(color).append("|");
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
        return "CHECKERS";
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
