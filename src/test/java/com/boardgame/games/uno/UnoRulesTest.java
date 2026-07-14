package com.boardgame.games.uno;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnoRulesTest {
    @Test
    void defaultsAreClassicRules() {
        UnoRules rules = UnoRules.fromOptions(Map.of());
        assertEquals(UnoRules.DEFAULT, rules);
        assertEquals(7, rules.handSize());
        assertEquals(4, rules.maxPlayers());
        assertFalse(rules.drawToMatch());
        assertFalse(rules.playDrawn());
        assertFalse(rules.callUno());
        assertFalse(rules.stackDraws());
        assertFalse(rules.sevenZero());
    }

    @Test
    void nullOptionsGiveDefaults() {
        assertEquals(UnoRules.DEFAULT, UnoRules.fromOptions(null));
    }

    @Test
    void parsesAllOptions() {
        UnoRules rules = UnoRules.fromOptions(Map.of(
                "handSize", "5",
                "maxPlayers", "6",
                "drawToMatch", "true",
                "playDrawn", "TRUE",
                "callUno", "true",
                "stackDraws", "true",
                "sevenZero", "true"));
        assertEquals(5, rules.handSize());
        assertEquals(6, rules.maxPlayers());
        assertTrue(rules.drawToMatch());
        assertTrue(rules.playDrawn());
        assertTrue(rules.callUno());
        assertTrue(rules.stackDraws());
        assertTrue(rules.sevenZero());
    }

    @Test
    void ignoresUnknownKeys() {
        UnoRules rules = UnoRules.fromOptions(Map.of("bogus", "42", "handSize", "9"));
        assertEquals(9, rules.handSize());
    }

    @Test
    void rejectsOutOfRangeValues() {
        assertThrows(IllegalArgumentException.class,
                () -> UnoRules.fromOptions(Map.of("handSize", "2")));
        assertThrows(IllegalArgumentException.class,
                () -> UnoRules.fromOptions(Map.of("handSize", "11")));
        assertThrows(IllegalArgumentException.class,
                () -> UnoRules.fromOptions(Map.of("maxPlayers", "1")));
        assertThrows(IllegalArgumentException.class,
                () -> UnoRules.fromOptions(Map.of("maxPlayers", "9")));
        assertThrows(IllegalArgumentException.class,
                () -> UnoRules.fromOptions(Map.of("handSize", "seven")));
    }

    @Test
    void roundTripsThroughOptions() {
        UnoRules rules = new UnoRules(5, 6, true, false, true, false, true);
        assertEquals(rules, UnoRules.fromOptions(rules.toOptions()));
    }
}
