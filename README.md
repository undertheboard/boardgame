# Board Game Hub

A multiplayer board game platform with LAN discovery, user authentication, a lobby system, and six games — all in a single Java 17 application with a modern dark-themed Swing UI.

## Games

| Game | Players | Description |
|------|---------|-------------|
| **UNO** | 2–4 | Classic card game with full rules (skip, reverse, draw, wilds) |
| **Tic-Tac-Toe** | 2 | 3×3 grid, first to three in a row wins |
| **Connect Four** | 2 | 7×6 gravity-drop, first to four in a line wins |
| **Checkers** | 2 | 8×8, diagonal moves, jumps, king promotion |
| **Reversi** | 2 | 8×8 flanking capture (Othello rules), most discs wins |
| **Dots and Boxes** | 2–4 | 5×5 dot grid, complete boxes to score, extra turn on completion |

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

## Run

### Server

```bash
java -jar target/boardgame-1.0-SNAPSHOT-server.jar [path/to/override.properties]
```

### Client

```bash
java -jar target/boardgame-1.0-SNAPSHOT-client.jar [host [port]]
```

The client auto-discovers servers on the LAN via UDP broadcast (port 8889). If no server is found, it falls back to `localhost:8888`.

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
| `CHARACTER\|symbol\|hexcolor` | Set avatar |

### Lobby
| Command | Description |
|---------|-------------|
| `LIST` | Request room list |
| `CREATE\|gameType\|base64(name)` | Create room |
| `JOINROOM\|roomId` | Join a game room |
| `LEAVEROOM` | Leave current room |

### In-Game
| Command | Description |
|---------|-------------|
| `MOVE\|...` | Game-specific move data |

### Admin
| Command | Description |
|---------|-------------|
| `KICK\|base64(user)` | Kick a player |
| `DELETEUSER\|base64(user)` | Delete a user account |

### Server Responses
| Message | Description |
|---------|-------------|
| `WELCOME\|user\|role\|symbol\|color` | Login success |
| `OK\|message` | Command success |
| `ERROR\|base64(message)` | Error |
| `LOBBY\|rooms\|players` | Lobby state |
| `GAMESTATE\|...` | Game-specific state |

## LAN Discovery

The server advertises on UDP port 8889 using the same `UNO_DISCOVER_V1` / `UNO_SERVER_V1|port` protocol for backward compatibility. Clients broadcast a discovery packet and receive server addresses.

## Architecture

```
com.boardgame.auth       — UserStore (PBKDF2 password hashing, properties file persistence)
com.boardgame.client     — HubClient (Swing UI with dark theme, CardLayout screens)
com.boardgame.games      — BoardGame interface + GameFactory
com.boardgame.games.uno  — UNO engine
com.boardgame.games.tictactoe    — Tic-Tac-Toe engine
com.boardgame.games.connectfour  — Connect Four engine
com.boardgame.games.checkers     — Checkers engine
com.boardgame.games.reversi      — Reversi/Othello engine
com.boardgame.games.dotsandboxes — Dots and Boxes engine
com.boardgame.model      — Card record (shared UNO model)
com.boardgame.network    — LanDiscovery (UDP broadcast)
com.boardgame.protocol   — Protocol (Base64-URL encoding)
com.boardgame.server     — HubServer, ServerConfig, Room, GameFactory
```

## Security

- Passwords hashed with PBKDF2WithHmacSHA256 (100,000 iterations, 16-byte random salt)
- Passwords never logged or transmitted in plaintext after initial TLS-less TCP send
- Thread-safe game engines (synchronized), ConcurrentHashMap for sessions
- Input validation on all commands (length limits, format checks)
