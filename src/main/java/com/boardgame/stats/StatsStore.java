package com.boardgame.stats;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Persistent per-player win/loss/draw statistics backing the hub leaderboard
 * and character levels.
 *
 * <p>Stats are stored in a properties file ({@code stats.properties} by
 * default) with entries of the form {@code username=wins:losses:draws}.
 *
 * <p>Players earn experience from every finished game and level up over time:
 * a win grants 10 XP, a draw 5 XP and a loss 2 XP. The level is
 * {@code 1 + floor(sqrt(xp / 10))}, so early levels come quickly and later
 * levels require sustained play.
 */
public final class StatsStore {
    private final Path filePath;
    private final Map<String, PlayerStats> stats = new LinkedHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /** Immutable stats snapshot for one player. */
    public record PlayerStats(String username, int wins, int losses, int draws) {
        /** @return total experience points: 10 per win, 5 per draw, 2 per loss */
        public int xp() {
            return wins * 10 + draws * 5 + losses * 2;
        }

        /** @return character level derived from {@link #xp()} */
        public int level() {
            return 1 + (int) Math.floor(Math.sqrt(xp() / 10.0));
        }

        /** @return total games played */
        public int games() {
            return wins + losses + draws;
        }
    }

    public StatsStore(Path filePath) {
        this.filePath = filePath;
    }

    /** Loads stats from disk, replacing any in-memory state. */
    public void load() throws IOException {
        lock.writeLock().lock();
        try {
            stats.clear();
            if (Files.exists(filePath)) {
                Properties props = new Properties();
                try (Reader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
                    props.load(reader);
                }
                for (String user : props.stringPropertyNames()) {
                    String[] parts = props.getProperty(user).split(":", 3);
                    if (parts.length == 3) {
                        try {
                            stats.put(user, new PlayerStats(user,
                                    Integer.parseInt(parts[0]),
                                    Integer.parseInt(parts[1]),
                                    Integer.parseInt(parts[2])));
                        } catch (NumberFormatException ignored) {
                            // Skip corrupt entries rather than failing startup
                        }
                    }
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Writes all stats to disk. */
    public void save() throws IOException {
        lock.readLock().lock();
        try {
            Properties props = new Properties();
            stats.forEach((user, s) -> props.setProperty(user,
                    s.wins() + ":" + s.losses() + ":" + s.draws()));
            try (Writer writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
                props.store(writer, "Board Game Hub Stats (wins:losses:draws)");
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Records the outcome of a finished game and persists it.
     *
     * @param winner  the winning player, or {@code null} for a draw
     * @param players every player who took part
     */
    public void recordResult(String winner, List<String> players) throws IOException {
        lock.writeLock().lock();
        try {
            for (String player : players) {
                PlayerStats current = stats.getOrDefault(player, new PlayerStats(player, 0, 0, 0));
                PlayerStats updated;
                if (winner == null) {
                    updated = new PlayerStats(player, current.wins(), current.losses(),
                            current.draws() + 1);
                } else if (winner.equals(player)) {
                    updated = new PlayerStats(player, current.wins() + 1, current.losses(),
                            current.draws());
                } else {
                    updated = new PlayerStats(player, current.wins(), current.losses() + 1,
                            current.draws());
                }
                stats.put(player, updated);
            }
            save();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** @return stats for the player, or a zeroed record if they never played */
    public PlayerStats get(String username) {
        lock.readLock().lock();
        try {
            return stats.getOrDefault(username, new PlayerStats(username, 0, 0, 0));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * @param limit maximum number of entries to return
     * @return players ordered by wins, then XP, then name
     */
    public List<PlayerStats> top(int limit) {
        lock.readLock().lock();
        try {
            List<PlayerStats> ordered = new ArrayList<>(stats.values());
            ordered.sort(Comparator.comparingInt(PlayerStats::wins).reversed()
                    .thenComparing(Comparator.comparingInt(PlayerStats::xp).reversed())
                    .thenComparing(PlayerStats::username));
            return ordered.subList(0, Math.min(limit, ordered.size()));
        } finally {
            lock.readLock().unlock();
        }
    }
}
