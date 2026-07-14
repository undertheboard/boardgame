package com.boardgame.stats;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatsStoreTest {
    @TempDir
    Path tempDir;

    private StatsStore newStore() throws Exception {
        StatsStore store = new StatsStore(tempDir.resolve("stats.properties"));
        store.load();
        return store;
    }

    @Test
    void recordsWinAndLoss() throws Exception {
        StatsStore store = newStore();
        store.recordResult("Alice", List.of("Alice", "Bob"));
        assertEquals(1, store.get("Alice").wins());
        assertEquals(0, store.get("Alice").losses());
        assertEquals(1, store.get("Bob").losses());
        assertEquals(0, store.get("Bob").wins());
    }

    @Test
    void recordsDrawForAll() throws Exception {
        StatsStore store = newStore();
        store.recordResult(null, List.of("Alice", "Bob"));
        assertEquals(1, store.get("Alice").draws());
        assertEquals(1, store.get("Bob").draws());
    }

    @Test
    void persistsAcrossReload() throws Exception {
        StatsStore store = newStore();
        store.recordResult("Alice", List.of("Alice", "Bob"));
        StatsStore reloaded = new StatsStore(tempDir.resolve("stats.properties"));
        reloaded.load();
        assertEquals(1, reloaded.get("Alice").wins());
        assertEquals(1, reloaded.get("Bob").losses());
    }

    @Test
    void topOrdersByWins() throws Exception {
        StatsStore store = newStore();
        store.recordResult("Alice", List.of("Alice", "Bob"));
        store.recordResult("Alice", List.of("Alice", "Bob"));
        store.recordResult("Bob", List.of("Alice", "Bob"));
        List<StatsStore.PlayerStats> top = store.top(10);
        assertEquals("Alice", top.get(0).username());
        assertEquals("Bob", top.get(1).username());
    }

    @Test
    void levelGrowsWithXp() {
        StatsStore.PlayerStats fresh = new StatsStore.PlayerStats("x", 0, 0, 0);
        StatsStore.PlayerStats veteran = new StatsStore.PlayerStats("x", 50, 10, 5);
        assertEquals(1, fresh.level());
        assertTrue(veteran.level() > fresh.level());
        assertEquals(50 * 10 + 5 * 5 + 10 * 2, veteran.xp());
    }

    @Test
    void unknownPlayerHasZeroStats() throws Exception {
        StatsStore store = newStore();
        assertEquals(0, store.get("nobody").games());
        assertEquals(1, store.get("nobody").level());
    }
}
