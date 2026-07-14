package com.boardgame.games.uno;

import com.boardgame.games.BoardGame;
import com.boardgame.model.Card;
import com.boardgame.model.Card.Color;
import com.boardgame.model.Card.Value;
import com.boardgame.protocol.Protocol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public final class UnoGame implements BoardGame {
    /**
     * Immutable view of the game for one player.
     *
     * @param flags machine-readable state hints, comma separated. Possible
     *              tokens: {@code PLAYDRAWN} (the requesting player just drew a
     *              playable card and must play or keep it), {@code STACK:n}
     *              (a stacked draw penalty of n cards is pending),
     *              {@code CANCALLUNO} (the requesting player may call UNO now),
     *              {@code UNOCALLED} (the requesting player has called UNO).
     */
    public record Snapshot(boolean started, boolean finished, String currentPlayer, Card topCard,
                           Color activeColor, List<Card> hand, Map<String, Integer> handSizes,
                           int drawPileSize, String flags, String message) {
    }

    private final int minPlayersCount;
    private final UnoRules rules;
    private final Random random;
    private final LinkedHashMap<String, List<Card>> hands = new LinkedHashMap<>();
    private final List<Card> drawPile = new ArrayList<>();
    private final List<Card> discardPile = new ArrayList<>();
    private final Set<String> unoCalled = new HashSet<>();
    private int currentIndex;
    private int direction = 1;
    private Color activeColor;
    private boolean started;
    private boolean finished;
    private String winner;
    private int pendingDraw;
    private int pendingDrawnIndex = -1;
    private String message = "Waiting for players";

    public UnoGame(UnoRules rules, Random random) {
        this.minPlayersCount = 2;
        this.rules = rules;
        this.random = random;
    }

    public UnoGame(int minPlayers, int maxPlayers, int startingHandSize, Random random) {
        if (minPlayers < 2 || maxPlayers < minPlayers || startingHandSize < 1) {
            throw new IllegalArgumentException("Invalid game limits");
        }
        this.minPlayersCount = minPlayers;
        this.rules = new UnoRules(startingHandSize, maxPlayers, false, false, false, false, false);
        this.random = random;
    }

    public UnoGame() {
        this(UnoRules.DEFAULT, new Random());
    }

    /** @return the house rules this game was created with */
    public UnoRules rules() {
        return rules;
    }

    @Override
    public synchronized void addPlayer(String playerId) {
        if (started || hands.size() >= rules.maxPlayers()) {
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
        unoCalled.remove(playerId);
        if (hands.size() < minPlayersCount) {
            finished = true;
            message = "Game ended: not enough players";
        } else if (removedIndex < currentIndex || currentIndex >= hands.size()) {
            currentIndex = Math.floorMod(currentIndex - 1, hands.size());
        }
    }

    @Override
    public synchronized void move(String playerId, String moveData) {
        switch (moveData) {
            case "DRAW" -> drawForTurn(playerId);
            case "KEEP" -> keepDrawn(playerId);
            case "CALLUNO" -> callUno(playerId);
            default -> {
                if (moveData.startsWith("PLAYDRAWN")) {
                    String[] parts = moveData.split("\\|", 2);
                    Color color = parts.length > 1 && !parts[1].isEmpty()
                            ? Color.valueOf(parts[1]) : null;
                    playDrawn(playerId, color);
                } else {
                    String[] parts = moveData.split("\\|", 2);
                    int index = Integer.parseInt(parts[0]);
                    Color color = parts.length > 1 && !parts[1].isEmpty()
                            ? Color.valueOf(parts[1]) : null;
                    play(playerId, index, color);
                }
            }
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
        sb.append(snap.flags()).append("|");
        sb.append(Protocol.encode(snap.message()));
        return sb.toString();
    }

    @Override
    public int minPlayers() {
        return minPlayersCount;
    }

    @Override
    public int maxPlayers() {
        return rules.maxPlayers();
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
                drawPile.size(), buildFlags(playerId), message);
    }

    private String buildFlags(String playerId) {
        List<String> flags = new ArrayList<>();
        if (!started || finished) {
            return "";
        }
        if (pendingDrawnIndex >= 0 && currentPlayer().equals(playerId)) {
            flags.add("PLAYDRAWN");
        }
        if (pendingDraw > 0) {
            flags.add("STACK:" + pendingDraw);
        }
        if (rules.callUno() && hands.containsKey(playerId)) {
            if (unoCalled.contains(playerId)) {
                flags.add("UNOCALLED");
            } else if (hands.get(playerId).size() == 2) {
                flags.add("CANCALLUNO");
            }
        }
        return String.join(",", flags);
    }

    public synchronized void play(String playerId, int handIndex, Color chosenColor) {
        requireTurn(playerId);
        List<Card> hand = hands.get(playerId);
        if (pendingDrawnIndex >= 0 && handIndex != pendingDrawnIndex) {
            throw new IllegalStateException("Play or keep the card you just drew first");
        }
        if (handIndex < 0 || handIndex >= hand.size()) {
            throw new IllegalArgumentException("Card does not exist");
        }
        Card card = hand.get(handIndex);
        if (pendingDraw > 0 && !stacksOnPending(card)) {
            throw new IllegalArgumentException(
                    "You must stack a matching draw card or draw " + pendingDraw + " cards");
        }
        if (!card.matches(topCard(), activeColor)) {
            throw new IllegalArgumentException("That card cannot be played");
        }
        if (card.color() == Color.WILD && (chosenColor == null || chosenColor == Color.WILD)) {
            throw new IllegalArgumentException("Choose a color for a wild card");
        }
        boolean missedUnoCall = rules.callUno() && hand.size() == 2
                && !unoCalled.contains(playerId);
        hand.remove(handIndex);
        pendingDrawnIndex = -1;
        discardPile.add(card);
        activeColor = card.color() == Color.WILD ? chosenColor : card.color();
        if (hand.isEmpty()) {
            finished = true;
            winner = playerId;
            message = playerId + " wins!";
            return;
        }
        String penaltyNote = "";
        if (missedUnoCall) {
            drawCards(playerId, 2);
            penaltyNote = " (forgot to call UNO, +2!)";
        }
        if (hand.size() != 1) {
            unoCalled.remove(playerId);
        }
        applyAction(playerId, card);
        advance();
        message = playerId + " played " + card.value().label() + penaltyNote;
    }

    public synchronized void drawForTurn(String playerId) {
        requireTurn(playerId);
        if (pendingDrawnIndex >= 0) {
            throw new IllegalStateException("Play or keep the card you just drew first");
        }
        List<Card> hand = hands.get(playerId);
        unoCalled.remove(playerId);
        if (pendingDraw > 0) {
            int count = pendingDraw;
            drawCards(playerId, count);
            pendingDraw = 0;
            advance();
            message = playerId + " picked up " + count + " cards";
            return;
        }
        int drawn = 0;
        Card last;
        do {
            last = draw();
            if (last == null) {
                break;
            }
            hand.add(last);
            drawn++;
        } while (rules.drawToMatch() && !last.matches(topCard(), activeColor));
        boolean playable = last != null && last.matches(topCard(), activeColor);
        if (rules.playDrawn() && playable) {
            pendingDrawnIndex = hand.size() - 1;
            message = playerId + " drew " + drawn + (drawn == 1 ? " card" : " cards")
                    + " - play it or keep it";
            return;
        }
        advance();
        message = playerId + " drew " + drawn + (drawn == 1 ? " card" : " cards");
    }

    /** Keeps the drawn card in hand and passes the turn ({@code playDrawn} rule). */
    public synchronized void keepDrawn(String playerId) {
        requireTurn(playerId);
        if (pendingDrawnIndex < 0) {
            throw new IllegalStateException("There is no drawn card to keep");
        }
        pendingDrawnIndex = -1;
        advance();
        message = playerId + " kept the drawn card";
    }

    /** Plays the card that was just drawn ({@code playDrawn} rule). */
    public synchronized void playDrawn(String playerId, Color chosenColor) {
        requireTurn(playerId);
        if (pendingDrawnIndex < 0) {
            throw new IllegalStateException("There is no drawn card to play");
        }
        play(playerId, pendingDrawnIndex, chosenColor);
    }

    /** Calls UNO for a player holding exactly two cards ({@code callUno} rule). */
    public synchronized void callUno(String playerId) {
        if (!rules.callUno()) {
            throw new IllegalStateException("The call-UNO rule is not enabled");
        }
        if (!started || finished) {
            throw new IllegalStateException("Game is not active");
        }
        List<Card> hand = hands.get(playerId);
        if (hand == null) {
            throw new IllegalArgumentException("Not a player in this game");
        }
        if (hand.size() != 2) {
            throw new IllegalStateException("You can only call UNO with two cards left");
        }
        unoCalled.add(playerId);
        message = playerId + " called UNO!";
    }

    private boolean stacksOnPending(Card card) {
        Value topValue = topCard().value();
        return card.value() == topValue
                && (topValue == Value.DRAW_TWO || topValue == Value.WILD_DRAW_FOUR);
    }

    private void start() {
        drawPile.addAll(createDeck());
        Collections.shuffle(drawPile, random);
        for (int i = 0; i < rules.handSize(); i++) {
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

    private void applyAction(String playerId, Card card) {
        if (card.value() == Value.REVERSE) {
            direction *= -1;
        }
        if (rules.sevenZero() && card.value() == Value.SEVEN) {
            swapWithNext(playerId);
        } else if (rules.sevenZero() && card.value() == Value.ZERO) {
            rotateHands();
        }
        if (card.value() == Value.SKIP) {
            advance();
        } else if (card.value() == Value.DRAW_TWO) {
            if (rules.stackDraws()) {
                pendingDraw += 2;
            } else {
                advance();
                drawCards(currentPlayer(), 2);
            }
        } else if (card.value() == Value.WILD_DRAW_FOUR) {
            if (rules.stackDraws()) {
                pendingDraw += 4;
            } else {
                advance();
                drawCards(currentPlayer(), 4);
            }
        }
    }

    /** Seven-zero rule: swap the player's hand with the next player's hand. */
    private void swapWithNext(String playerId) {
        List<String> order = new ArrayList<>(hands.keySet());
        int playerIndex = order.indexOf(playerId);
        String next = order.get(Math.floorMod(playerIndex + direction, order.size()));
        List<Card> mine = hands.get(playerId);
        hands.put(playerId, hands.get(next));
        hands.put(next, mine);
        unoCalled.remove(playerId);
        unoCalled.remove(next);
    }

    /** Seven-zero rule: every hand moves to the next player in play direction. */
    private void rotateHands() {
        List<String> order = new ArrayList<>(hands.keySet());
        List<List<Card>> current = new ArrayList<>();
        for (String player : order) {
            current.add(hands.get(player));
        }
        for (int i = 0; i < order.size(); i++) {
            int from = Math.floorMod(i - direction, order.size());
            hands.put(order.get(i), current.get(from));
        }
        unoCalled.clear();
    }

    private void drawCards(String playerId, int count) {
        for (int i = 0; i < count; i++) {
            Card card = draw();
            if (card == null) {
                return;
            }
            hands.get(playerId).add(card);
        }
        unoCalled.remove(playerId);
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

    /** @return the next card, or null if every card is in a player's hand */
    private Card draw() {
        if (drawPile.isEmpty()) {
            if (discardPile.size() <= 1) {
                return null;
            }
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
