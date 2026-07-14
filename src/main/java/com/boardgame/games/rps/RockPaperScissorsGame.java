package com.boardgame.games.rps;

import com.boardgame.games.BoardGame;
import com.boardgame.protocol.Protocol;

import java.util.ArrayList;
import java.util.List;

/**
 * Rock Paper Scissors, first to three round wins. Both players pick
 * simultaneously; a round resolves once both choices are in. Moves are
 * {@code ROCK}, {@code PAPER} or {@code SCISSORS}.
 *
 * <p>The snapshot's "current player" field returns the requesting player's own
 * name while their choice for the round is still pending, so the client
 * enables the choice buttons for each player independently.
 */
public final class RockPaperScissorsGame implements BoardGame {
    private static final int TARGET_WINS = 3;
    private static final List<String> CHOICES = List.of("ROCK", "PAPER", "SCISSORS");

    private final List<String> playerList = new ArrayList<>(2);
    private final String[] choices = new String[2];
    private final int[] scores = new int[2];
    private int round = 1;
    private boolean started;
    private boolean finished;
    private String winner;
    private String message = "Waiting for players";
    private String lastRoundSummary = "";

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
            message = "Round 1 — make your choice!";
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
        if (!started || finished) {
            throw new IllegalStateException("Game is not active");
        }
        int index = playerList.indexOf(playerId);
        if (index < 0) {
            throw new IllegalArgumentException("Not a player in this game");
        }
        String choice = moveData.trim().toUpperCase();
        if (!CHOICES.contains(choice)) {
            throw new IllegalArgumentException("Choose ROCK, PAPER or SCISSORS");
        }
        if (choices[index] != null) {
            throw new IllegalStateException("You already chose this round");
        }
        choices[index] = choice;
        if (choices[0] != null && choices[1] != null) {
            resolveRound();
        } else {
            message = "Round " + round + " — waiting for "
                    + playerList.get(choices[0] == null ? 0 : 1);
        }
    }

    private void resolveRound() {
        int result = beats(choices[0], choices[1]);
        if (result == 0) {
            lastRoundSummary = "Round " + round + ": " + choices[0] + " vs " + choices[1] + " — tie!";
        } else {
            int winnerIndex = result > 0 ? 0 : 1;
            scores[winnerIndex]++;
            lastRoundSummary = "Round " + round + ": " + choices[0] + " vs " + choices[1]
                    + " — " + playerList.get(winnerIndex) + " takes the round!";
        }
        choices[0] = null;
        choices[1] = null;
        if (scores[0] >= TARGET_WINS || scores[1] >= TARGET_WINS) {
            finished = true;
            winner = scores[0] >= TARGET_WINS ? playerList.get(0) : playerList.get(1);
            message = lastRoundSummary + " " + winner + " wins the match "
                    + scores[0] + "-" + scores[1] + "!";
        } else {
            round++;
            message = lastRoundSummary + " Round " + round + " — make your choice!";
        }
    }

    /** @return positive if a beats b, negative if b beats a, 0 on tie. */
    private static int beats(String a, String b) {
        if (a.equals(b)) {
            return 0;
        }
        boolean aWins = (a.equals("ROCK") && b.equals("SCISSORS"))
                || (a.equals("PAPER") && b.equals("ROCK"))
                || (a.equals("SCISSORS") && b.equals("PAPER"));
        return aWins ? 1 : -1;
    }

    @Override
    public synchronized String snapshot(String playerId) {
        int index = playerList.indexOf(playerId);
        boolean pending = started && !finished && index >= 0 && choices[index] == null;
        StringBuilder scoreLine = new StringBuilder();
        for (int i = 0; i < playerList.size(); i++) {
            if (i > 0) {
                scoreLine.append(',');
            }
            scoreLine.append(Protocol.encode(playerList.get(i))).append(':').append(scores[i]);
        }
        String myChoice = index >= 0 && choices[index] != null ? choices[index] : "";
        return started + "|" + finished + "|"
                + Protocol.encode(pending ? playerId : "") + "|"
                + scoreLine + "|" + round + "|" + myChoice + "|"
                + Protocol.encode(message);
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
        return "RPS";
    }
}
