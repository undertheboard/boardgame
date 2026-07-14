# Animated UNO

A playable UNO game with a configurable Java TCP server and animated Swing clients.

## Requirements

- JDK 17+
- Maven 3.8+

## Build and test

```bash
mvn clean verify
```

## Run

Start the server:

```bash
mvn exec:java -Dexec.mainClass=com.boardgame.uno.server.UnoServer
```

Then start at least two clients in separate terminals:

```bash
mvn exec:java -Dexec.mainClass=com.boardgame.uno.client.UnoClient
```

The client accepts optional host and port arguments:

```bash
mvn exec:java -Dexec.mainClass=com.boardgame.uno.client.UnoClient \
  -Dexec.args="server.example.com 8888"
```

Cards animate onto the discard pile. Select a color before playing a wild card.

## Server configuration

Defaults are in `src/main/resources/server.properties`:

```properties
host=0.0.0.0
port=8888
minPlayers=2
maxPlayers=4
startingHandSize=7
```

Pass a properties file to override any defaults:

```bash
mvn exec:java -Dexec.mainClass=com.boardgame.uno.server.UnoServer \
  -Dexec.args="/path/to/server.properties"
```

The server is authoritative for turn order, card validation, drawing, action cards,
winning, and player limits. Clients communicate using a small line-based TCP protocol.