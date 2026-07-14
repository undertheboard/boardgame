package com.boardgame.games.uno;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnoGameTest {
    @Test
    void createsStandardDeck() {
        assertEquals(108, UnoGame.createDeck().size());
    }

    @Test
    void startsAtConfiguredMinimumAndDealsCards() {
        UnoGame game = new UnoGame(2, 4, 7, new Random(2));
        game.addPlayer("Alice");
        assertFalse(game.snapshotRecord("Alice").started());
        game.addPlayer("Bob");
        assertTrue(game.snapshotRecord("Alice").started());
        assertEquals(7, game.snapshotRecord("Alice").hand().size());
        assertEquals(7, game.snapshotRecord("Bob").hand().size());
        assertEquals("Alice", game.snapshotRecord("Alice").currentPlayer());
    }

    @Test
    void rejectsActionsFromPlayerOutOfTurn() {
        UnoGame game = new UnoGame(2, 4, 7, new Random(2));
        game.addPlayer("Alice");
        game.addPlayer("Bob");
        assertThrows(IllegalStateException.class, () -> game.drawForTurn("Bob"));
    }

    @Test
    void drawingAddsCardAndAdvancesTurn() {
        UnoGame game = new UnoGame(2, 4, 7, new Random(2));
        game.addPlayer("Alice");
        game.addPlayer("Bob");
        game.drawForTurn("Alice");
        assertEquals(8, game.snapshotRecord("Alice").hand().size());
        assertEquals("Bob", game.snapshotRecord("Bob").currentPlayer());
    }

    @Test
    void implementsBoardGameInterface() {
        UnoGame game = new UnoGame();
        assertEquals("UNO", game.gameType());
        assertEquals(2, game.minPlayers());
        assertEquals(4, game.maxPlayers());
        assertFalse(game.isStarted());
    }
}
