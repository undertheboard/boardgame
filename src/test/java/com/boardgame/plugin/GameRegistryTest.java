package com.boardgame.plugin;

import com.boardgame.games.BoardGame;
import com.boardgame.games.tictactoe.TicTacToeGame;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameRegistryTest {
    private static GamePlugin plugin(String type) {
        return new GamePlugin() {
            @Override
            public String gameType() {
                return type;
            }

            @Override
            public BoardGame createGame() {
                return new TicTacToeGame();
            }
        };
    }

    @Test
    void builtinsAreRegistered() {
        List<String> types = GameRegistry.types();
        assertTrue(types.containsAll(List.of("UNO", "TICTACTOE", "CONNECTFOUR", "CHECKERS",
                "REVERSI", "DOTSANDBOXES", "GOMOKU", "RPS")));
    }

    @Test
    void createIsCaseInsensitiveAndFresh() {
        BoardGame first = GameRegistry.create("gomoku");
        BoardGame second = GameRegistry.create("GOMOKU");
        assertEquals("GOMOKU", first.gameType());
        assertNotSame(first, second);
    }

    @Test
    void unknownTypeThrows() {
        assertThrows(IllegalArgumentException.class, () -> GameRegistry.create("NOPE"));
    }

    @Test
    void registersCustomPluginAndListsIt() {
        assertTrue(GameRegistry.register(plugin("TESTGAME_A")));
        assertTrue(GameRegistry.types().contains("TESTGAME_A"));
        assertEquals("TICTACTOE", GameRegistry.create("TESTGAME_A").gameType());
    }

    @Test
    void rejectsDuplicateType() {
        assertTrue(GameRegistry.register(plugin("TESTGAME_B")));
        assertFalse(GameRegistry.register(plugin("testgame_b")));
        assertFalse(GameRegistry.register(plugin("TICTACTOE")));
    }

    @Test
    void rejectsInvalidTypeNames() {
        assertFalse(GameRegistry.register(plugin("has space")));
        assertFalse(GameRegistry.register(plugin("pipe|char")));
        assertFalse(GameRegistry.register(plugin("")));
        assertFalse(GameRegistry.register(plugin("WAY_TOO_LONG_GAME_TYPE_NAME")));
    }
}
