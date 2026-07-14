package com.boardgame.model;

import java.util.Objects;

public record Card(Color color, Value value) {
    public enum Color {
        RED, YELLOW, GREEN, BLUE, WILD
    }

    public enum Value {
        ZERO("0"), ONE("1"), TWO("2"), THREE("3"), FOUR("4"), FIVE("5"),
        SIX("6"), SEVEN("7"), EIGHT("8"), NINE("9"), SKIP("Skip"),
        REVERSE("Reverse"), DRAW_TWO("+2"), WILD("Wild"), WILD_DRAW_FOUR("+4");

        private final String label;

        Value(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public Card {
        Objects.requireNonNull(color);
        Objects.requireNonNull(value);
        if ((value == Value.WILD || value == Value.WILD_DRAW_FOUR) != (color == Color.WILD)) {
            throw new IllegalArgumentException("Wild values and colors must be paired");
        }
    }

    public boolean matches(Card top, Color activeColor) {
        return color == Color.WILD || color == activeColor || value == top.value;
    }

    public String token() {
        return color + ":" + value;
    }

    public static Card fromToken(String token) {
        String[] parts = token.split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid card token");
        }
        return new Card(Color.valueOf(parts[0]), Value.valueOf(parts[1]));
    }
}
