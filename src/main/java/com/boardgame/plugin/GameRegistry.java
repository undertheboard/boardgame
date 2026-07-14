package com.boardgame.plugin;

import com.boardgame.games.BoardGame;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Central registry of every game type the hub can host.
 *
 * <p>The registry aggregates games from three sources, in order:
 * <ol>
 *   <li><b>Built-in games</b> — registered automatically when the class loads.</li>
 *   <li><b>Classpath plugins</b> — {@link GamePlugin} implementations declared in
 *       {@code META-INF/services/com.boardgame.plugin.GamePlugin} on the server
 *       classpath, discovered with {@link ServiceLoader}.</li>
 *   <li><b>Plugin jars</b> — jars dropped into the server's plugin directory
 *       (default {@code plugins/}) and loaded via
 *       {@link #loadPluginsDirectory(Path)} at server startup.</li>
 * </ol>
 *
 * <p>Game types are case-insensitive and normalized to upper case. The first
 * plugin to claim a type wins; duplicates are logged and ignored, so a plugin
 * can never silently replace a built-in game.
 *
 * <p><b>Security note:</b> plugin jars run with full server privileges. Only
 * install plugins from sources you trust.
 *
 * <p>All methods are thread-safe.
 *
 * @see GamePlugin
 */
public final class GameRegistry {
    private static final Map<String, GamePlugin> PLUGINS = new LinkedHashMap<>();

    static {
        registerBuiltins();
        loadFromClassLoader(GameRegistry.class.getClassLoader());
    }

    private GameRegistry() {
    }

    /**
     * Registers a plugin programmatically. Useful for tests and for embedders
     * that construct the hub in code rather than dropping jars in a directory.
     *
     * @param plugin the plugin to register
     * @return {@code true} if registered, {@code false} if the game type was
     *         invalid or already taken
     */
    public static synchronized boolean register(GamePlugin plugin) {
        String type = plugin.gameType() == null ? "" : plugin.gameType().toUpperCase();
        if (!type.matches("[A-Z0-9_]{1,24}")) {
            System.err.println("GameRegistry: rejected plugin with invalid game type '"
                    + plugin.gameType() + "'");
            return false;
        }
        if (PLUGINS.containsKey(type)) {
            System.err.println("GameRegistry: game type '" + type
                    + "' already registered; ignoring duplicate");
            return false;
        }
        PLUGINS.put(type, plugin);
        return true;
    }

    /**
     * Loads every {@code *.jar} in {@code dir} and registers all
     * {@link GamePlugin} services found inside them. Missing or empty
     * directories are silently ignored so the feature is zero-configuration.
     *
     * @param dir the plugin directory (e.g. {@code plugins/})
     */
    public static void loadPluginsDirectory(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        List<URL> urls = new ArrayList<>();
        try (Stream<Path> jars = Files.list(dir)) {
            jars.filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .sorted()
                    .forEach(p -> {
                        try {
                            urls.add(p.toUri().toURL());
                        } catch (IOException e) {
                            System.err.println("GameRegistry: cannot read plugin jar " + p + ": " + e);
                        }
                    });
        } catch (IOException e) {
            System.err.println("GameRegistry: cannot list plugin directory " + dir + ": " + e);
            return;
        }
        if (urls.isEmpty()) {
            return;
        }
        // The class loader intentionally stays open for the lifetime of the
        // server: plugin classes are used for as long as rooms exist.
        URLClassLoader loader = new URLClassLoader(
                urls.toArray(new URL[0]), GameRegistry.class.getClassLoader());
        loadFromClassLoader(loader);
    }

    /**
     * Creates a fresh game instance for the given type.
     *
     * @param gameType the (case-insensitive) game type
     * @return a new {@link BoardGame}
     * @throws IllegalArgumentException if no plugin provides the type
     */
    public static synchronized BoardGame create(String gameType) {
        return create(gameType, Map.of());
    }

    /**
     * Creates a fresh game instance for the given type, configured with the
     * given options (see {@link GamePlugin#createGame(Map)}).
     *
     * @param gameType the (case-insensitive) game type
     * @param options  option map for configurable rules; may be empty
     * @return a new {@link BoardGame}
     * @throws IllegalArgumentException if no plugin provides the type or an
     *                                  option value is invalid
     */
    public static synchronized BoardGame create(String gameType, Map<String, String> options) {
        GamePlugin plugin = PLUGINS.get(gameType.toUpperCase());
        if (plugin == null) {
            throw new IllegalArgumentException("Unknown game type: " + gameType);
        }
        return plugin.createGame(options == null ? Map.of() : options);
    }

    /**
     * @return all registered game types, in registration order (built-ins first)
     */
    public static synchronized List<String> types() {
        return List.copyOf(PLUGINS.keySet());
    }

    /**
     * @param gameType the (case-insensitive) game type
     * @return the plugin registered for the type, or {@code null}
     */
    public static synchronized GamePlugin plugin(String gameType) {
        return PLUGINS.get(gameType.toUpperCase());
    }

    private static void loadFromClassLoader(ClassLoader loader) {
        try {
            for (GamePlugin plugin : ServiceLoader.load(GamePlugin.class, loader)) {
                if (register(plugin)) {
                    System.out.println("GameRegistry: loaded plugin game '"
                            + plugin.gameType() + "' (" + plugin.displayName() + ")");
                }
            }
        } catch (java.util.ServiceConfigurationError error) {
            System.err.println("GameRegistry: failed to load plugins: " + error);
        }
    }

    private static void registerBuiltins() {
        register(new GamePlugin() {
            @Override
            public String gameType() {
                return "UNO";
            }

            @Override
            public String displayName() {
                return "UNO";
            }

            @Override
            public String description() {
                return "Classic card game with skips, reverses, draws, wilds and house rules.";
            }

            @Override
            public BoardGame createGame() {
                return new com.boardgame.games.uno.UnoGame();
            }

            @Override
            public BoardGame createGame(Map<String, String> options) {
                return new com.boardgame.games.uno.UnoGame(
                        com.boardgame.games.uno.UnoRules.fromOptions(options),
                        new java.util.Random());
            }
        });
        registerBuiltin("TICTACTOE", "Tic-Tac-Toe", "3x3 grid, first to three in a row wins.",
                com.boardgame.games.tictactoe.TicTacToeGame::new);
        registerBuiltin("CONNECTFOUR", "Connect Four", "7x6 gravity drop, first to four in a line wins.",
                com.boardgame.games.connectfour.ConnectFourGame::new);
        registerBuiltin("CHECKERS", "Checkers", "8x8 diagonal moves, jumps and king promotion.",
                com.boardgame.games.checkers.CheckersGame::new);
        registerBuiltin("REVERSI", "Reversi", "8x8 flanking capture, most discs wins.",
                com.boardgame.games.reversi.ReversiGame::new);
        registerBuiltin("DOTSANDBOXES", "Dots and Boxes", "Complete boxes to score, extra turn on completion.",
                com.boardgame.games.dotsandboxes.DotsAndBoxesGame::new);
        registerBuiltin("GOMOKU", "Gomoku", "15x15 board, first to five stones in a row wins.",
                com.boardgame.games.gomoku.GomokuGame::new);
        registerBuiltin("RPS", "Rock Paper Scissors", "Best of five: first player to three round wins.",
                com.boardgame.games.rps.RockPaperScissorsGame::new);
        registerBuiltin("PUTTPUTT", "Putt Putt", "Animated mini golf: bounce off walls, fewest strokes wins.",
                com.boardgame.games.puttputt.PuttPuttGame::new);
    }

    private static void registerBuiltin(String type, String name, String description,
                                        Supplier<BoardGame> factory) {
        register(new GamePlugin() {
            @Override
            public String gameType() {
                return type;
            }

            @Override
            public String displayName() {
                return name;
            }

            @Override
            public String description() {
                return description;
            }

            @Override
            public BoardGame createGame() {
                return factory.get();
            }
        });
    }
}
