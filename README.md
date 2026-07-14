# Board Game Hub

A multiplayer board game platform with LAN discovery, user authentication, a lobby system, and eight games — all in a single Java 17 application with a modern dark-themed Swing UI. Features room chat, emotes/taunts with sound effects and animations, a persistent leaderboard with character levels and titles, rematch support, a no-login spectator client for projecting games, and a documented plugin API for adding new games.

## Games

| Game | Players | Description |
|------|---------|-------------|
| **UNO** | 2–4 | Classic card game with full rules (skip, reverse, draw, wilds) |
| **Tic-Tac-Toe** | 2 | 3×3 grid, first to three in a row wins |
| **Connect Four** | 2 | 7×6 gravity-drop, first to four in a line wins |
| **Checkers** | 2 | 8×8, diagonal moves, jumps, king promotion |
| **Reversi** | 2 | 8×8 flanking capture (Othello rules), most discs wins |
| **Dots and Boxes** | 2–4 | 5×5 dot grid, complete boxes to score, extra turn on completion |
| **Gomoku** | 2 | 15×15 board, first to five stones in a row wins |
| **Rock Paper Scissors** | 2 | Simultaneous picks, first to three round wins |

More games can be added without touching this codebase — see [docs/PLUGIN_API.md](docs/PLUGIN_API.md).

## Features

- **Room chat** — talk to your opponents while you play
- **Emotes, taunts & noises** — nine one-click emotes (👋 😂 😭 😠 😱 🤝 😜 😎 📯) broadcast to the room with an animated toast and a short sound effect
- **Waiting screen** — a pulsing waiting-for-players view shows room status before the game starts
- **Leaderboard & levels** — wins/losses/draws are persisted; players earn XP and levels (🏆 button in the lobby)
- **Character building** — pick an avatar symbol, color and a title (Novice → Legend); your title and level are shown in the lobby
- **Play Again** — one click rematch in the same room; stale finished rooms are swept automatically after 5 minutes
- **Spectator mode** — a separate no-login client watches any room with a huge "whose turn" banner, ideal for projectors/casting (F11 = full screen)
- **Plugin API** — drop a jar in `plugins/` to add new games ([docs/PLUGIN_API.md](docs/PLUGIN_API.md))

## Requirements

- JDK 17+
- Maven 3.8+

## Build

```bash
mvn clean verify
```

Produces:
- `target/boardgame-1.0-SNAPSHOT-server.jar` — Hub server
- `target/boardgame-1.0-SNAPSHOT-client.jar` — Hub client
- `target/boardgame-1.0-SNAPSHOT-spectator.jar` — Spectator client (watch-only, no login)

Prebuilt copies are committed as `dist/hub-server.jar`, `dist/hub-client.jar` and `dist/hub-spectator.jar`.

## Run

### Server

```bash
java -jar dist/hub-server.jar [path/to/override.properties]
```

### Client

```bash
java -jar dist/hub-client.jar [host [port]]
```

The client auto-discovers servers on the LAN via UDP broadcast (port 8889). If no server is found, it falls back to `localhost:8888`.

### Spectator

```bash
java -jar dist/hub-spectator.jar [host [port]]
```

No account needed. Pick a room to watch: the current player is shown in a large pulsing banner and the board is drawn full-size — press **F11** to go borderless full screen for a projector, TV or casting device. Chat and emotes from the room scroll across the bottom ticker. Spectators only ever receive public game state (never player hands).

## Configuration (server.properties)

| Key | Default | Description |
|-----|---------|-------------|
| `host` | `0.0.0.0` | Bind address |
| `port` | `8888` | TCP port |
| `discoveryPort` | `8889` | UDP LAN discovery port |
| `minPlayers` | `2` | Minimum players for UNO |
| `maxPlayers` | `4` | Maximum players for UNO |
| `startingHandSize` | `7` | Cards dealt in UNO |
| `adminPassword` | `admin` | **⚠️ Change this!** Default admin password |
| `usersFile` | `users.properties` | Path to user credentials file |
| `maxRooms` | `20` | Maximum concurrent game rooms |
| `statsFile` | `stats.properties` | Path to leaderboard stats file |
| `pluginsDir` | `plugins` | Directory scanned for game plugin jars at startup |

Pass an override properties file as the server's first CLI argument.

## Default Admin Account

On first start, the server creates an `admin` user with the password from `adminPassword` config key (default: `admin`).

**⚠️ Change the default admin password immediately in production.**

The admin can:
- **Kick players** from the server
- **Delete user accounts**

## Protocol

Line-based TCP protocol. Free-text fields are Base64-URL encoded.

### Authentication
| Command | Description |
|---------|-------------|
| `REGISTER\|base64(user)\|base64(pass)` | Create account |
| `LOGIN\|base64(user)\|base64(pass)` | Log in |
| `CHARACTER\|symbol\|hexcolor[\|base64(title)]` | Set avatar and optional title |

### Lobby
| Command | Description |
|---------|-------------|
| `LIST` | Request room list |
| `CREATE\|gameType\|base64(name)` | Create room |
| `JOINROOM\|roomId` | Join a game room |
| `LEAVEROOM` | Leave current room |
| `LEADERBOARD` | Request the top-20 leaderboard |

### In-Game
| Command | Description |
|---------|-------------|
| `MOVE\|...` | Game-specific move data |
| `CHAT\|base64(message)` | Send a chat message to the room |
| `EMOTE\|emoteId` | Broadcast an emote/taunt (`WAVE`, `LAUGH`, `CRY`, `ANGRY`, `SHOCK`, `GG`, `TAUNT`, `BOAST`, `HORN`) |
| `PLAYAGAIN` | Restart a finished game in the same room |

### Spectating (no login required)
| Command | Description |
|---------|-------------|
| `ROOMS` | List rooms (public data only) |
| `SPECTATE\|roomId` | Start watching a room |
| `UNSPECTATE` | Stop watching |

### Admin
| Command | Description |
|---------|-------------|
| `KICK\|base64(user)` | Kick a player |
| `DELETEUSER\|base64(user)` | Delete a user account |

### Server Responses
| Message | Description |
|---------|-------------|
| `WELCOME\|user\|role\|symbol\|color\|base64(title)\|level` | Login success |
| `GAMES\|TYPE1,TYPE2,...` | Available game types (built-in + plugins) |
| `OK\|message` | Command success |
| `ERROR\|base64(message)` | Error |
| `LOBBY\|rooms\|players` | Lobby state (players include title and level) |
| `GAMESTATE\|gameType\|...` | Game-specific state |
| `CHAT\|base64(user)\|base64(message)` | Room chat message |
| `EMOTE\|base64(user)\|emoteId` | Room emote |
| `LEADERBOARD\|user:w:l:d:level,...` | Leaderboard data |
| `ROOMS\|rooms` | Room list for spectators |
| `ROOMCLOSED\|base64(message)` | Your room was swept after the game ended |

## LAN Discovery

The server advertises on UDP port 8889 using the same `UNO_DISCOVER_V1` / `UNO_SERVER_V1|port` protocol for backward compatibility. Clients broadcast a discovery packet and receive server addresses.

## Architecture

```
com.boardgame.auth       — UserStore (PBKDF2 password hashing, properties file persistence)
com.boardgame.client     — HubClient (Swing UI) + SpectatorClient (watch-only projection UI)
com.boardgame.games      — BoardGame interface
com.boardgame.games.uno  — UNO engine
com.boardgame.games.tictactoe    — Tic-Tac-Toe engine
com.boardgame.games.connectfour  — Connect Four engine
com.boardgame.games.checkers     — Checkers engine
com.boardgame.games.reversi      — Reversi/Othello engine
com.boardgame.games.dotsandboxes — Dots and Boxes engine
com.boardgame.games.gomoku       — Gomoku engine
com.boardgame.games.rps          — Rock Paper Scissors engine
com.boardgame.model      — Card record (shared UNO model)
com.boardgame.network    — LanDiscovery (UDP broadcast)
com.boardgame.plugin     — GamePlugin SPI + GameRegistry (plugin discovery)
com.boardgame.protocol   — Protocol (Base64-URL encoding)
com.boardgame.server     — HubServer, ServerConfig, Room, GameFactory
com.boardgame.stats      — StatsStore (leaderboard persistence, XP/levels)
```

## Security

- Passwords hashed with PBKDF2WithHmacSHA256 (100,000 iterations, 16-byte random salt)
- Passwords never logged or transmitted in plaintext after initial TLS-less TCP send
- Thread-safe game engines (synchronized), ConcurrentHashMap for sessions
- Input validation on all commands (length limits, format checks)
- Spectators receive only public snapshots (never player hands)
- ⚠️ Plugin jars run with full server privileges — only install plugins you trust
