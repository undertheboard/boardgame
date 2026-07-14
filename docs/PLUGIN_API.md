# Board Game Hub Plugin API

The hub ships with a plugin system that lets you add **entire new games**
without touching the hub source code. Plugins are ordinary jars dropped into a
directory; the server discovers them at startup and every hub client can
immediately create rooms, play, chat, emote and spectate them — no client
changes required.

- [How it works](#how-it-works)
- [Quick start: build a game plugin in 5 steps](#quick-start-build-a-game-plugin-in-5-steps)
- [The `BoardGame` interface](#the-boardgame-interface)
- [The snapshot wire format](#the-snapshot-wire-format)
- [The `GamePlugin` interface](#the-gameplugin-interface)
- [Plugin discovery](#plugin-discovery)
- [Client rendering](#client-rendering)
- [Leaderboard integration](#leaderboard-integration)
- [Threading rules](#threading-rules)
- [Validation & wire-safety rules](#validation--wire-safety-rules)
- [Security](#security)
- [Worked example: Nim](#worked-example-nim)

## How it works

```
plugins/my-game.jar
        │  (ServiceLoader: META-INF/services/com.boardgame.plugin.GamePlugin)
        ▼
com.boardgame.plugin.GameRegistry   ← also registers the 8 built-in games
        │
        ▼
HubServer  ──►  CREATE|MYGAME|room name   (players create rooms of your type)
        │
        ▼
GAMESTATE|MYGAME|<your snapshot>          (broadcast to players & spectators)
```

`GameRegistry` aggregates three sources at server startup, in order:

1. **Built-in games** (UNO, TICTACTOE, CONNECTFOUR, CHECKERS, REVERSI,
   DOTSANDBOXES, GOMOKU, RPS).
2. **Classpath plugins** — `GamePlugin` services on the server classpath.
3. **Plugin jars** — every `*.jar` in the server's plugin directory
   (default `plugins/`, configurable with the `pluginsDir` server property).

Game types are case-insensitive and normalized to upper case. The **first**
registration of a type wins; duplicates are logged and ignored, so a plugin
can never replace a built-in game.

After login the server sends the client a `GAMES|TYPE1,TYPE2,...` message, so
plugin game types automatically appear in the "Create Room" picker.

## Quick start: build a game plugin in 5 steps

1. **Create a project** that depends on the hub server jar (it contains the
   `com.boardgame.games`, `com.boardgame.plugin` and `com.boardgame.protocol`
   packages):

   ```xml
   <dependency>
     <groupId>com.boardgame</groupId>
     <artifactId>boardgame</artifactId>
     <version>1.0-SNAPSHOT</version>
     <classifier>server</classifier>
     <scope>provided</scope>
   </dependency>
   ```

2. **Implement `com.boardgame.games.BoardGame`** with your game rules
   (see below).

3. **Implement `com.boardgame.plugin.GamePlugin`**:

   ```java
   public final class NimPlugin implements GamePlugin {
       public String gameType()      { return "NIM"; }
       public String displayName()   { return "Nim"; }
       public String description()   { return "Take 1-3 sticks; last stick loses."; }
       public BoardGame createGame() { return new NimGame(); }
   }
   ```

4. **Register the service.** Create the file
   `src/main/resources/META-INF/services/com.boardgame.plugin.GamePlugin`
   containing one line:

   ```
   com.example.nim.NimPlugin
   ```

5. **Package and deploy.** Build the jar and copy it into the server's
   `plugins/` directory, then restart the server. You should see:

   ```
   GameRegistry: loaded plugin game 'NIM' (Nim)
   ```

## The `BoardGame` interface

```java
public interface BoardGame {
    void addPlayer(String playerId);      // throw if full / duplicate
    void removePlayer(String playerId);   // usually finishes the game
    void move(String playerId, String moveData);
    String snapshot(String playerId);     // see wire format below
    int minPlayers();
    int maxPlayers();
    boolean isStarted();
    boolean isFinished();
    default String winner() { return null; }  // for the leaderboard
    List<String> players();
    String gameType();                    // must equal GamePlugin.gameType()
}
```

Behavioral contract:

| Method | Contract |
|--------|----------|
| `addPlayer` | Called when a player joins the room. Throw `IllegalStateException` when full, `IllegalArgumentException` on duplicates. Start the game (set `isStarted()`) once enough players joined. |
| `removePlayer` | Called when a player leaves or disconnects. The built-in games finish the game when a player leaves mid-match. |
| `move` | Called with the raw move payload from `MOVE\|...`. Validate everything: turn order, format, legality. Throw `IllegalArgumentException`/`IllegalStateException` with a human-readable message — the hub relays it to the offending client as an `ERROR`. |
| `snapshot` | Must return the per-player view of the game (hide private state from other players!). Called once per player after every move, and with an **empty/unknown player id** for spectators — never leak hidden information for unknown ids. |
| `winner` | Return the winning player's id once finished, or `null` for a draw/unfinished game. Used to record leaderboard stats. |

## The snapshot wire format

Snapshots travel to clients as `GAMESTATE|<GAMETYPE>|<snapshot>`. The snapshot
itself is pipe-delimited and must follow this minimum layout:

```
started|finished|currentPlayerEncoded|...game specific fields...|messageEncoded
```

| Field | Type | Notes |
|-------|------|-------|
| `started` | `true`/`false` | game has begun |
| `finished` | `true`/`false` | game is over |
| `currentPlayerEncoded` | Base64-URL | player whose turn it is (`Protocol.encode`). Empty before start. For simultaneous-move games (like the built-in RPS), return the *requesting* player's id while their input is pending. |
| middle fields | free-form | your board state; must not contain raw `\|` |
| `messageEncoded` | Base64-URL | human-readable status line, **always the last field** |

Everything free-text (player names, messages) **must** be encoded with
`com.boardgame.protocol.Protocol.encode(...)` — the protocol is line-based and
pipe/colon/comma-delimited, so raw user text would corrupt framing.

## The `GamePlugin` interface

```java
public interface GamePlugin {
    String gameType();                      // "NIM" — [A-Z0-9_]{1,24}, unique
    default String displayName() {...}      // "Nim"
    default String description() {...}      // one-liner for UIs
    BoardGame createGame();                 // fresh instance per room
}
```

`createGame()` is invoked for every `CREATE` command, and again when players
choose **Play Again** after a finished match — always return a brand-new,
independent instance.

## Plugin discovery

| Mechanism | When | How |
|-----------|------|-----|
| Built-ins | class-load of `GameRegistry` | hard-wired |
| Classpath | class-load of `GameRegistry` | `ServiceLoader.load(GamePlugin.class)` |
| Plugin dir | server startup | `GameRegistry.loadPluginsDirectory(Path)` — loads every `*.jar` (sorted by name) via a `URLClassLoader` and runs `ServiceLoader` over it |
| Programmatic | any time | `GameRegistry.register(plugin)` — handy for tests and embedders |

Server configuration (`server.properties`):

```properties
# Directory scanned for plugin jars at startup (relative to the working dir)
pluginsDir=plugins
```

## Client rendering

The hub client dispatches rendering on the game type in the `GAMESTATE`
message. Game types it does not recognize get the **generic renderer**: the
status message, a free-text move box and a Send Move button — plus chat,
emotes and the waiting-for-players screen, which work for every game. This
means any plugin game is fully playable with zero client work; a custom
board UI in the client is an optional nicety.

The spectator client behaves the same way: recognized grid games get a big
board view; anything else falls back to the status banner, message and score
line, which is driven entirely by your snapshot's `message` field.

## Leaderboard integration

When a game finishes through a move, the hub records one result per player in
the stats store (`statsFile`, default `stats.properties`):

- `winner()` non-null → that player gets a **win**, everyone else a **loss**
- `winner()` null → everyone gets a **draw**

Wins grant 10 XP, draws 5, losses 2; levels are `1 + floor(sqrt(xp/10))` and
are shown next to players in the lobby. Implement `winner()` — otherwise
every finished game of your type counts as a draw.

## Threading rules

The hub calls your game from **multiple client handler threads**
concurrently. The built-in convention (recommended) is to declare every
public method `synchronized` and return defensive copies from `players()`.
Never block inside a game method.

## Validation & wire-safety rules

- Game type: `[A-Z0-9_]{1,24}` — the registry rejects anything else.
- Never emit raw `|`, `:`, `,` or newlines in snapshot fields; encode
  free text with `Protocol.encode`.
- Validate every `move()` input — clients are untrusted.
- Keep snapshots reasonably small; they are sent after every move to every
  player and spectator.

## Security

Plugin jars run **inside the server JVM with full privileges**. There is no
sandbox. Only install plugins from sources you trust, and audit them like any
other server code.

## Worked example: Nim

```java
package com.example.nim;

import com.boardgame.games.BoardGame;
import com.boardgame.protocol.Protocol;
import java.util.ArrayList;
import java.util.List;

public final class NimGame implements BoardGame {
    private final List<String> players = new ArrayList<>(2);
    private int sticks = 21;
    private int current;
    private boolean started;
    private boolean finished;
    private String winner;
    private String message = "Waiting for players";

    @Override public synchronized void addPlayer(String id) {
        if (players.size() >= 2) throw new IllegalStateException("Game is full");
        if (players.contains(id)) throw new IllegalArgumentException("Already joined");
        players.add(id);
        if (players.size() == 2) { started = true; message = players.get(0) + "'s turn"; }
        else message = id + " joined, waiting for opponent";
    }

    @Override public synchronized void removePlayer(String id) {
        if (players.remove(id)) { finished = true; message = "Game ended: player left"; }
    }

    @Override public synchronized void move(String id, String data) {
        if (!started || finished) throw new IllegalStateException("Game is not active");
        if (!players.get(current).equals(id)) throw new IllegalStateException("Not your turn");
        int take = Integer.parseInt(data.trim());
        if (take < 1 || take > 3 || take > sticks)
            throw new IllegalArgumentException("Take 1-3 sticks");
        sticks -= take;
        if (sticks == 0) {           // whoever takes the last stick loses
            finished = true;
            winner = players.get(1 - current);
            message = winner + " wins!";
        } else {
            current = 1 - current;
            message = sticks + " sticks left — " + players.get(current) + "'s turn";
        }
    }

    @Override public synchronized String snapshot(String id) {
        return started + "|" + finished + "|"
                + Protocol.encode(started && !finished ? players.get(current) : "")
                + "|" + sticks + "|" + Protocol.encode(message);
    }

    @Override public int minPlayers() { return 2; }
    @Override public int maxPlayers() { return 2; }
    @Override public synchronized boolean isStarted() { return started; }
    @Override public synchronized boolean isFinished() { return finished; }
    @Override public synchronized String winner() { return winner; }
    @Override public synchronized List<String> players() { return new ArrayList<>(players); }
    @Override public String gameType() { return "NIM"; }
}
```

With the `NimPlugin` from the quick start and the service file in place,
`mvn package`, drop the jar into `plugins/`, restart the server — done.
