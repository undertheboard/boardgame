package com.boardgame.uno.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardTest {
    @Test
    void tokensRoundTrip() {
        Card card = new Card(Card.Color.BLUE, Card.Value.REVERSE);
        assertEquals(card, Card.fromToken(card.token()));
    }

    @Test
    void matchesColorValueAndWild() {
        Card top = new Card(Card.Color.RED, Card.Value.FIVE);
        assertTrue(new Card(Card.Color.RED, Card.Value.TWO).matches(top, Card.Color.RED));
        assertTrue(new Card(Card.Color.BLUE, Card.Value.FIVE).matches(top, Card.Color.RED));
        assertTrue(new Card(Card.Color.WILD, Card.Value.WILD).matches(top, Card.Color.RED));
    }

    @Test
    void rejectsMismatchedWildCard() {
        assertThrows(IllegalArgumentException.class,
                () -> new Card(Card.Color.RED, Card.Value.WILD));
    }
}
