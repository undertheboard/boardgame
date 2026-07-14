package com.boardgame.games.uno;

import com.boardgame.games.BoardGame;
import com.boardgame.model.Card;
import com.boardgame.model.Card.Color;
import com.boardgame.model.Card.Value;
import com.boardgame.protocol.Protocol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

public final class UnoGame implements BoardGame {
    public record Snapshot(boolean started, boolean finished, String currentPlayer, Card topCard,
                           Color activeColor, List<Card> hand, Map<String, Integer> handSizes,
                           int drawPileSize, String message) {
    }

    private final int minPlayersCount;
    private final int maxPlayersCount;
    private final int startingHandSize;
    private final Random random;
    private final LinkedHashMap<String, List<Card>> hands = new LinkedHashMap<>();
    private final List<Card> drawPile = new ArrayList<>();
    private final List<Card> discardPile = new ArrayList<>();
    private int currentIndex;
    private int direction = 1;
    private Color activeColor;
    private boolean started;
    private boolean finished;
    private String message = "Waiting for players";

    public UnoGame(int minPlayers, int maxPlayers, int startingHandSize, Random random) {
        if (minPlayers < 2 || maxPlayers < minPlayers || startingHandSize < 1) {
            throw new IllegalArgumentException("Invalid game limits");
        }
        this.minPlayersCount = minPlayers;
        this.maxPlayersCount = maxPlayers;
        this.startingHandSize = startingHandSize;
        this.random = random;
    }

    public UnoGame() {
        this(2, 4, 7, new Random());
    }

    @Override
    public synchronized void addPlayer(String playerId) {
        if (started || hands.size() >= maxPlayersCount) {
            throw new IllegalStateException("Game is full or already started");
        }
        if (hands.containsKey(playerId)) {
            throw new IllegalArgumentException("Player is already connected");
        }
        hands.put(playerId, new ArrayList<>());
        message = playerId + " joined";
        if (hands.size() >= minPlayersCount) {
            start();
        }
    }

    @Override
    public synchronized void removePlayer(String playerId) {
        int removedIndex = new ArrayList<>(hands.keySet()).indexOf(playerId);
        if (removedIndex < 0) {
            return;
        }
        hands.remove(playerId);
        if (hands.size() < minPlayersCount) {
            finished = true;
            message = "Game ended: not enough players";
        } else if (removedIndex < currentIndex || currentIndex >= hands.size()) {
            currentIndex = Math.floorMod(currentIndex - 1, hands.size());
        }
    }

    @Override
    public synchronized void move(String playerId, String moveData) {
        if (moveData.equals("DRAW")) {
            drawForTurn(playerId);
        } else {
            String[] parts = moveData.split("\\|", 2);
            int index = Integer.parseInt(parts[0]);
            Color color = parts.length > 1 && !parts[1].isEmpty() ? Color.valueOf(parts[1]) : null;
            play(playerId, index, color);
        }
    }

    @Override
    public synchronized String snapshot(String playerId) {
        Snapshot snap = snapshotRecord(playerId);
        StringBuilder sb = new StringBuilder();
        sb.append(snap.started()).append("|");
        sb.append(snap.finished()).append("|");
        sb.append(Protocol.encode(snap.currentPlayer())).append("|");
        sb.append(snap.topCard() == null ? "" : snap.topCard().token()).append("|");
        sb.append(snap.activeColor() == null ? "" : snap.activeColor().name()).append("|");
        sb.append(snap.hand().stream().map(Card::token).collect(Collectors.joining(","))).append("|");
        sb.append(snap.handSizes().entrySet().stream()
                .map(e -> Protocol.encode(e.getKey()) + ":" + e.getValue())
                .collect(Collectors.joining(","))).append("|");
        sb.append(snap.drawPileSize()).append("|");
        sb.append(Protocol.encode(snap.message()));
        return sb.toString();
    }

    @Override
    public int minPlayers() {
        return minPlayersCount;
    }

    @Override
    public int maxPlayers() {
        return maxPlayersCount;
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
        return new ArrayList<>(hands.keySet());
    }

    @Override
    public String gameType() {
        return "UNO";
    }

    public synchronized Snapshot snapshotRecord(String playerId) {
        Map<String, Integer> sizes = new LinkedHashMap<>();
        hands.forEach((name, hand) -> sizes.put(name, hand.size()));
        return new Snapshot(started, finished, started ? currentPlayer() : "",
                started ? topCard() : null, activeColor,
                List.copyOf(hands.getOrDefault(playerId, List.of())), sizes,
                drawPile.size(), message);
    }

    public synchronized void play(String playerId, int handIndex, Color chosenColor) {
        requireTurn(playerId);
        List<Card> hand = hands.get(playerId);
        if (handIndex < 0 || handIndex >= hand.size()) {
            throw new IllegalArgumentException("Card does not exist");
        }
        Card card = hand.get(handIndex);
        if (!card.matches(topCard(), activeColor)) {
            throw new IllegalArgumentException("That card cannot be played");
        }
        if (card.color() == Color.WILD && (chosenColor == null || chosenColor == Color.WILD)) {
            throw new IllegalArgumentException("Choose a color for a wild card");
        }
        hand.remove(handIndex);
        discardPile.add(card);
        activeColor = card.color() == Color.WILD ? chosenColor : card.color();
        if (hand.isEmpty()) {
            finished = true;
            message = playerId + " wins!";
            return;
        }
        applyAction(card);
        advance();
        message = playerId + " played " + card.value().label();
    }

    public synchronized void drawForTurn(String playerId) {
        requireTurn(playerId);
        hands.get(playerId).add(draw());
        message = playerId + " drew a card";
        advance();
    }

    private void start() {
        drawPile.addAll(createDeck());
        Collections.shuffle(drawPile, random);
        for (int i = 0; i < startingHandSize; i++) {
            for (List<Card> hand : hands.values()) {
                hand.add(draw());
            }
        }
        Card first;
        do {
            first = draw();
            if (first.color() == Color.WILD) {
                drawPile.add(0, first);
            }
        } while (first.color() == Color.WILD);
        discardPile.add(first);
        activeColor = first.color();
        started = true;
        message = new ArrayList<>(hands.keySet()).get(0) + "'s turn";
    }

    private void applyAction(Card card) {
        if (card.value() == Value.REVERSE) {
            direction *= -1;
        }
        if (card.value() == Value.SKIP) {
            advance();
        } else if (card.value() == Value.DRAW_TWO) {
            advance();
            drawCards(currentPlayer(), 2);
        } else if (card.value() == Value.WILD_DRAW_FOUR) {
            advance();
            drawCards(currentPlayer(), 4);
        }
    }

    private void drawCards(String playerId, int count) {
        for (int i = 0; i < count; i++) {
            hands.get(playerId).add(draw());
        }
    }

    private void requireTurn(String playerId) {
        if (!started || finished) {
            throw new IllegalStateException("Game is not active");
        }
        if (!currentPlayer().equals(playerId)) {
            throw new IllegalStateException("It is not your turn");
        }
    }

    private void advance() {
        currentIndex = Math.floorMod(currentIndex + direction, hands.size());
    }

    private Card draw() {
        if (drawPile.isEmpty()) {
            Card top = discardPile.remove(discardPile.size() - 1);
            drawPile.addAll(discardPile);
            discardPile.clear();
            discardPile.add(top);
            Collections.shuffle(drawPile, random);
        }
        return drawPile.remove(drawPile.size() - 1);
    }

    private String currentPlayer() {
        return new ArrayList<>(hands.keySet()).get(currentIndex);
    }

    private Card topCard() {
        return discardPile.get(discardPile.size() - 1);
    }

    public static List<Card> createDeck() {
        List<Card> cards = new ArrayList<>(108);
        for (Color color : List.of(Color.RED, Color.YELLOW, Color.GREEN, Color.BLUE)) {
            cards.add(new Card(color, Value.ZERO));
            for (Value value : List.of(Value.ONE, Value.TWO, Value.THREE, Value.FOUR, Value.FIVE,
                    Value.SIX, Value.SEVEN, Value.EIGHT, Value.NINE, Value.SKIP, Value.REVERSE,
                    Value.DRAW_TWO)) {
                cards.add(new Card(color, value));
                cards.add(new Card(color, value));
            }
        }
        for (int i = 0; i < 4; i++) {
            cards.add(new Card(Color.WILD, Value.WILD));
            cards.add(new Card(Color.WILD, Value.WILD_DRAW_FOUR));
        }
        return cards;
    }
}
