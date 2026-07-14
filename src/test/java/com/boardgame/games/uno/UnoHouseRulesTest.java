package com.boardgame.games.uno;

import com.boardgame.model.Card;
import com.boardgame.model.Card.Color;
import com.boardgame.model.Card.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for the configurable UNO house rules. */
class UnoHouseRulesTest {

    private static UnoRules rules(int handSize, int maxPlayers, boolean drawToMatch,
                                  boolean playDrawn, boolean callUno, boolean stackDraws,
                                  boolean sevenZero) {
        return new UnoRules(handSize, maxPlayers, drawToMatch, playDrawn, callUno,
                stackDraws, sevenZero);
    }

    private static UnoGame twoPlayerGame(UnoRules rules, long seed) {
        UnoGame game = new UnoGame(rules, new Random(seed));
        game.addPlayer("Alice");
        game.addPlayer("Bob");
        return game;
    }

    private static String current(UnoGame game) {
        return game.snapshotRecord("Alice").currentPlayer();
    }

    @Test
    void customHandSizeIsDealt() {
        UnoGame game = twoPlayerGame(
                rules(5, 4, false, false, false, false, false), 1);
        assertEquals(5, game.snapshotRecord("Alice").hand().size());
        assertEquals(5, game.snapshotRecord("Bob").hand().size());
    }

    @Test
    void maxPlayersRuleIsEnforced() {
        UnoGame game = new UnoGame(rules(7, 2, false, false, false, false, false),
                new Random(1));
        game.addPlayer("Alice");
        game.addPlayer("Bob");
        assertThrows(IllegalStateException.class, () -> game.addPlayer("Cara"));
        assertEquals(2, game.maxPlayers());
    }

    @Test
    void drawToMatchDrawsUntilPlayable() {
        UnoGame game = twoPlayerGame(
                rules(7, 4, true, false, false, false, false), 3);
        UnoGame.Snapshot before = game.snapshotRecord("Alice");
        game.drawForTurn("Alice");
        UnoGame.Snapshot after = game.snapshotRecord("Alice");
        assertTrue(after.hand().size() > before.hand().size());
        Card drawn = after.hand().get(after.hand().size() - 1);
        assertTrue(drawn.matches(after.topCard(), after.activeColor()),
                "last drawn card must be playable");
        assertEquals("Bob", current(game));
    }

    @Test
    void playDrawnLetsPlayerPlaceOrKeepDrawnCard() {
        // drawToMatch guarantees the drawn card is playable so the choice appears
        UnoGame game = twoPlayerGame(
                rules(7, 4, true, true, false, false, false), 3);
        game.drawForTurn("Alice");
        assertEquals("Alice", current(game), "turn must pause for the choice");
        assertTrue(game.snapshotRecord("Alice").flags().contains("PLAYDRAWN"));
        assertFalse(game.snapshotRecord("Bob").flags().contains("PLAYDRAWN"));

        int handBefore = game.snapshotRecord("Alice").hand().size();
        game.playDrawn("Alice", Color.RED);
        assertEquals(handBefore - 1, game.snapshotRecord("Alice").hand().size());
    }

    @Test
    void keepDrawnKeepsCardAndPassesTurn() {
        UnoGame game = twoPlayerGame(
                rules(7, 4, true, true, false, false, false), 3);
        game.drawForTurn("Alice");
        int handSize = game.snapshotRecord("Alice").hand().size();
        game.keepDrawn("Alice");
        assertEquals(handSize, game.snapshotRecord("Alice").hand().size());
        assertEquals("Bob", current(game));
        assertThrows(IllegalStateException.class, () -> game.keepDrawn("Bob"));
    }

    @Test
    void otherMovesAreBlockedWhileDrawnCardPending() {
        UnoGame game = twoPlayerGame(
                rules(7, 4, true, true, false, false, false), 3);
        game.drawForTurn("Alice");
        assertThrows(IllegalStateException.class, () -> game.drawForTurn("Alice"));
        assertThrows(IllegalStateException.class, () -> game.play("Alice", 0, Color.RED));
    }

    @Test
    void callUnoRequiresRuleAndTwoCards() {
        UnoGame off = twoPlayerGame(
                rules(7, 4, false, false, false, false, false), 1);
        assertThrows(IllegalStateException.class, () -> off.callUno("Alice"));

        UnoGame on = twoPlayerGame(
                rules(7, 4, false, false, true, false, false), 1);
        assertThrows(IllegalStateException.class, () -> on.callUno("Alice"));
        assertThrows(IllegalArgumentException.class, () -> on.callUno("Nobody"));
    }

    /**
     * Drives a full game with the call-UNO rule, always calling UNO at two
     * cards: the game must finish normally with a winner.
     */
    @Test
    void gameFinishesWhenPlayersCallUno() {
        UnoGame game = twoPlayerGame(
                rules(5, 4, true, false, true, false, false), 7);
        driveToCompletion(game, true);
        assertTrue(game.isFinished());
        assertNotNull(game.winner());
    }

    /**
     * Drives a game where nobody ever calls UNO: playing the second-to-last
     * card must add a two card penalty (hand becomes 1 + 2 = 3).
     */
    @Test
    void missingUnoCallDrawsPenalty() {
        UnoGame game = twoPlayerGame(
                rules(5, 4, true, false, true, false, false), 7);
        boolean penaltySeen = false;
        for (int i = 0; i < 400 && !game.isFinished(); i++) {
            String player = current(game);
            UnoGame.Snapshot snap = game.snapshotRecord(player);
            boolean atUno = snap.hand().size() == 2;
            boolean played = tryPlayAnyCard(game, player, snap);
            if (played && atUno) {
                assertEquals(3, game.snapshotRecord(player).hand().size(),
                        "missed UNO call must cost two penalty cards");
                penaltySeen = true;
                break;
            }
            if (!played) {
                game.drawForTurn(player);
            }
        }
        assertTrue(penaltySeen, "expected at least one missed UNO call");
    }

    /** Seven-zero rule: playing a 7 swaps hands with the other player. */
    @Test
    void sevenSwapsHands() {
        for (long seed = 0; seed < 40; seed++) {
            UnoGame game = twoPlayerGame(
                    rules(7, 4, true, false, false, false, true), seed);
            for (int i = 0; i < 200 && !game.isFinished(); i++) {
                String player = current(game);
                UnoGame.Snapshot snap = game.snapshotRecord(player);
                String other = player.equals("Alice") ? "Bob" : "Alice";
                int sevenIndex = indexOfPlayable(snap, Value.SEVEN);
                if (sevenIndex >= 0) {
                    List<Card> mineBefore = snap.hand();
                    List<Card> theirsBefore = game.snapshotRecord(other).hand();
                    game.play(player, sevenIndex, null);
                    assertEquals(theirsBefore, game.snapshotRecord(player).hand(),
                            "player must receive the other player's hand");
                    assertEquals(mineBefore.size() - 1,
                            game.snapshotRecord(other).hand().size(),
                            "other player gets the played-from hand minus the 7");
                    return;
                }
                if (!tryPlayAnyCard(game, player, snap)) {
                    game.drawForTurn(player);
                }
            }
        }
        throw new AssertionError("no seed produced a playable 7");
    }

    /** Stacking rule: a Draw Two creates a pending stack the victim picks up. */
    @Test
    void stackedDrawTwoIsPickedUpByOpponent() {
        for (long seed = 0; seed < 60; seed++) {
            UnoGame game = twoPlayerGame(
                    rules(7, 4, true, false, false, true, false), seed);
            for (int i = 0; i < 200 && !game.isFinished(); i++) {
                String player = current(game);
                UnoGame.Snapshot snap = game.snapshotRecord(player);
                String other = player.equals("Alice") ? "Bob" : "Alice";
                if (snap.flags().contains("STACK:")) {
                    break; // only reachable through the branch below
                }
                int drawTwoIndex = indexOfPlayable(snap, Value.DRAW_TWO);
                if (drawTwoIndex >= 0) {
                    game.play(player, drawTwoIndex, null);
                    if (game.isFinished()) {
                        break;
                    }
                    UnoGame.Snapshot victim = game.snapshotRecord(other);
                    assertTrue(victim.flags().contains("STACK:2"),
                            "a pending +2 stack must be flagged");
                    assertEquals(other, victim.currentPlayer());
                    // Victim has no matching draw card? Drawing picks up the stack.
                    int victimHand = victim.hand().size();
                    int stackIndex = indexOfValue(victim.hand(), Value.DRAW_TWO);
                    if (stackIndex >= 0) {
                        game.play(other, stackIndex, null);
                        assertTrue(game.snapshotRecord(player).flags().contains("STACK:4"),
                                "stacking must accumulate to +4");
                    } else {
                        game.drawForTurn(other);
                        assertEquals(victimHand + 2,
                                game.snapshotRecord(other).hand().size(),
                                "victim must pick up the pending two cards");
                        assertEquals(player, current(game));
                    }
                    return;
                }
                if (!tryPlayAnyCard(game, player, snap)) {
                    game.drawForTurn(player);
                }
            }
        }
        throw new AssertionError("no seed produced a playable Draw Two");
    }

    /** Non-stacking cards cannot be played onto a pending stack. */
    @Test
    void pendingStackBlocksNormalCards() {
        for (long seed = 0; seed < 60; seed++) {
            UnoGame game = twoPlayerGame(
                    rules(7, 4, true, false, false, true, false), seed);
            for (int i = 0; i < 200 && !game.isFinished(); i++) {
                String player = current(game);
                UnoGame.Snapshot snap = game.snapshotRecord(player);
                String other = player.equals("Alice") ? "Bob" : "Alice";
                int drawTwoIndex = indexOfPlayable(snap, Value.DRAW_TWO);
                if (drawTwoIndex >= 0) {
                    game.play(player, drawTwoIndex, null);
                    if (game.isFinished()) {
                        break;
                    }
                    UnoGame.Snapshot victim = game.snapshotRecord(other);
                    for (int c = 0; c < victim.hand().size(); c++) {
                        Card card = victim.hand().get(c);
                        if (card.value() != Value.DRAW_TWO
                                && card.matches(victim.topCard(), victim.activeColor())) {
                            int index = c;
                            assertThrows(IllegalArgumentException.class,
                                    () -> game.play(other, index, Color.RED));
                            return;
                        }
                    }
                    break; // victim had no candidate card; try another seed
                }
                if (!tryPlayAnyCard(game, player, snap)) {
                    game.drawForTurn(player);
                }
            }
        }
        throw new AssertionError("no seed produced the blocked-card scenario");
    }

    // --- helpers ---

    private static int indexOfPlayable(UnoGame.Snapshot snap, Value value) {
        for (int i = 0; i < snap.hand().size(); i++) {
            Card card = snap.hand().get(i);
            if (card.value() == value && card.matches(snap.topCard(), snap.activeColor())) {
                return i;
            }
        }
        return -1;
    }

    private static int indexOfValue(List<Card> hand, Value value) {
        for (int i = 0; i < hand.size(); i++) {
            if (hand.get(i).value() == value) {
                return i;
            }
        }
        return -1;
    }

    private static boolean tryPlayAnyCard(UnoGame game, String player, UnoGame.Snapshot snap) {
        for (int i = 0; i < snap.hand().size(); i++) {
            Card card = snap.hand().get(i);
            if (card.matches(snap.topCard(), snap.activeColor())) {
                Color color = card.color() == Color.WILD ? Color.RED : null;
                try {
                    game.play(player, i, color);
                    return true;
                } catch (IllegalArgumentException | IllegalStateException ignored) {
                    // e.g. blocked by a pending stack; treat as unplayable
                }
            }
        }
        return false;
    }

    private static void driveToCompletion(UnoGame game, boolean callUno) {
        for (int i = 0; i < 2000 && !game.isFinished(); i++) {
            String player = game.snapshotRecord("Alice").currentPlayer();
            UnoGame.Snapshot snap = game.snapshotRecord(player);
            if (callUno && snap.hand().size() == 2 && !snap.flags().contains("UNOCALLED")) {
                game.callUno(player);
                snap = game.snapshotRecord(player);
            }
            if (!tryPlayAnyCard(game, player, snap)) {
                game.drawForTurn(player);
            }
        }
    }
}
