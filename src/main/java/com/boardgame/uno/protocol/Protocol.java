package com.boardgame.uno.protocol;

import com.boardgame.uno.game.UnoGame.Snapshot;
import com.boardgame.uno.model.Card;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class Protocol {
    private Protocol() {
    }

    public static String state(Snapshot state) {
        return String.join("|", "STATE", Boolean.toString(state.started()),
                Boolean.toString(state.finished()), encode(state.currentPlayer()),
                state.topCard() == null ? "" : state.topCard().token(),
                state.activeColor() == null ? "" : state.activeColor().name(),
                cards(state.hand()), players(state.handSizes()),
                Integer.toString(state.drawPileSize()), encode(state.message()));
    }

    public static String encode(String text) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    public static String decode(String text) {
        return new String(Base64.getUrlDecoder().decode(text), StandardCharsets.UTF_8);
    }

    private static String cards(List<Card> cards) {
        return cards.stream().map(Card::token).collect(Collectors.joining(","));
    }

    private static String players(Map<String, Integer> players) {
        return players.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + ":" + entry.getValue())
                .collect(Collectors.joining(","));
    }
}
