package com.boardgame.games.dotsandboxes;

import com.boardgame.games.BoardGame;
import com.boardgame.protocol.Protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class DotsAndBoxesGame implements BoardGame {
    public static final int DOTS = 5; // 5x5 grid of dots = 4x4 boxes
    private static final int BOXES = DOTS - 1;

    // hLines[r][c]: horizontal line from dot (r,c) to (r,c+1). r in [0,DOTS), c in [0,BOXES)
    // vLines[r][c]: vertical line from dot (r,c) to (r+1,c). r in [0,BOXES), c in [0,DOTS)
    // Box (r,c) needs: hLines[r][c], hLines[r+1][c], vLines[r][c], vLines[r][c+1]
    private final boolean[][] hLines = new boolean[DOTS][BOXES];
    private final boolean[][] vLines = new boolean[BOXES][DOTS];

    private final int[] scores;
    private final int[][] boxOwner = new int[BOXES][BOXES]; // -1 = unclaimed
    private final List<String> playerList = new ArrayList<>(4);
    private int currentIndex;
    private boolean started;
    private boolean finished;
    private String message = "Waiting for players";
    private int totalLinesDrawn;
    private final int totalLines;

    public DotsAndBoxesGame() {
        scores = new int[4];
        for (int r = 0; r < BOXES; r++) {
            for (int c = 0; c < BOXES; c++) {
                boxOwner[r][c] = -1;
            }
        }
        // Total lines = horizontal lines + vertical lines
        // Horizontal: DOTS rows * BOXES cols = 5*4 = 20
        // Vertical: BOXES rows * DOTS cols = 4*5 = 20
        totalLines = DOTS * BOXES + BOXES * DOTS;
    }

    @Override
    public synchronized void addPlayer(String playerId) {
        if (started) {
            throw new IllegalStateException("Game already started");
        }
        if (playerList.size() >= 4) {
            throw new IllegalStateException("Game is full");
        }
        if (playerList.contains(playerId)) {
            throw new IllegalArgumentException("Already joined");
        }
        playerList.add(playerId);
        if (playerList.size() >= 2) {
            started = true;
            message = playerList.get(0) + "'s turn";
        } else {
            message = playerId + " joined, waiting for more players";
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
        // moveData format: "H|row|col" for horizontal line, "V|row|col" for vertical line
        String[] parts = moveData.split("\\|");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid move format (H|row|col or V|row|col)");
        }
        String type = parts[0];
        int row = Integer.parseInt(parts[1]);
        int col = Integer.parseInt(parts[2]);

        boolean completed = false;
        if (type.equals("H")) {
            if (row < 0 || row >= DOTS || col < 0 || col >= BOXES) {
                throw new IllegalArgumentException("Invalid horizontal line position");
            }
            if (hLines[row][col]) {
                throw new IllegalArgumentException("Line already drawn");
            }
            hLines[row][col] = true;
            totalLinesDrawn++;
            completed = checkBoxCompletion(playerId);
        } else if (type.equals("V")) {
            if (row < 0 || row >= BOXES || col < 0 || col >= DOTS) {
                throw new IllegalArgumentException("Invalid vertical line position");
            }
            if (vLines[row][col]) {
                throw new IllegalArgumentException("Line already drawn");
            }
            vLines[row][col] = true;
            totalLinesDrawn++;
            completed = checkBoxCompletion(playerId);
        } else {
            throw new IllegalArgumentException("Line type must be H or V");
        }

        if (totalLinesDrawn >= totalLines) {
            finished = true;
            message = determineWinner();
        } else if (!completed) {
            currentIndex = (currentIndex + 1) % playerList.size();
            message = playerList.get(currentIndex) + "'s turn";
        } else {
            // Player completed a box, gets another turn
            message = playerId + " completed a box! Goes again.";
        }
    }

    private boolean checkBoxCompletion(String playerId) {
        int playerIdx = playerList.indexOf(playerId);
        boolean completed = false;
        for (int r = 0; r < BOXES; r++) {
            for (int c = 0; c < BOXES; c++) {
                if (boxOwner[r][c] == -1 && isBoxComplete(r, c)) {
                    boxOwner[r][c] = playerIdx;
                    scores[playerIdx]++;
                    completed = true;
                }
            }
        }
        return completed;
    }

    private boolean isBoxComplete(int r, int c) {
        // Box (r,c) needs:
        // Top: hLines[r][c]
        // Bottom: hLines[r+1][c]
        // Left: vLines[r][c]
        // Right: vLines[r][c+1]
        return hLines[r][c] && hLines[r + 1][c] && vLines[r][c] && vLines[r][c + 1];
    }

    private String determineWinner() {
        int maxScore = -1;
        String winner = null;
        boolean tie = false;
        for (int i = 0; i < playerList.size(); i++) {
            if (scores[i] > maxScore) {
                maxScore = scores[i];
                winner = playerList.get(i);
                tie = false;
            } else if (scores[i] == maxScore) {
                tie = true;
            }
        }
        if (tie) {
            return "Draw!";
        }
        return winner + " wins with " + maxScore + " boxes!";
    }

    @Override
    public synchronized String snapshot(String playerId) {
        StringBuilder sb = new StringBuilder();
        sb.append(started).append("|");
        sb.append(finished).append("|");
        sb.append(Protocol.encode(started ? playerList.get(currentIndex) : "")).append("|");
        // Encode lines
        StringBuilder lines = new StringBuilder();
        for (int r = 0; r < DOTS; r++) {
            for (int c = 0; c < BOXES; c++) {
                lines.append(hLines[r][c] ? '1' : '0');
            }
        }
        lines.append(';');
        for (int r = 0; r < BOXES; r++) {
            for (int c = 0; c < DOTS; c++) {
                lines.append(vLines[r][c] ? '1' : '0');
            }
        }
        sb.append(lines).append("|");
        // Box owners
        StringBuilder owners = new StringBuilder();
        for (int r = 0; r < BOXES; r++) {
            for (int c = 0; c < BOXES; c++) {
                owners.append(boxOwner[r][c] == -1 ? "." : String.valueOf(boxOwner[r][c]));
            }
        }
        sb.append(owners).append("|");
        // Scores
        String scoreStr = playerList.stream()
                .map(p -> Protocol.encode(p) + ":" + scores[playerList.indexOf(p)])
                .collect(Collectors.joining(","));
        sb.append(scoreStr).append("|");
        sb.append(Protocol.encode(message));
        return sb.toString();
    }

    @Override
    public int minPlayers() {
        return 2;
    }

    @Override
    public int maxPlayers() {
        return 4;
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
        return "DOTSANDBOXES";
    }

    private void requireTurn(String playerId) {
        if (!started || finished) {
            throw new IllegalStateException("Game is not active");
        }
        if (!playerList.get(currentIndex).equals(playerId)) {
            throw new IllegalStateException("It is not your turn");
        }
    }

    public synchronized int getScore(int playerIdx) {
        return scores[playerIdx];
    }
}
