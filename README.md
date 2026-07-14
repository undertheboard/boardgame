# Animated UNO

A playable UNO game with a configurable Java TCP server and animated Swing clients.

## Requirements

- JDK 17+
- Maven 3.8+

## Build and test

```bash
mvn clean verify
```

This creates runnable `target/boardgame-1.0-SNAPSHOT-server.jar` and
`target/boardgame-1.0-SNAPSHOT-client.jar` files. Prebuilt copies are committed as
`dist/uno-server.jar` and `dist/uno-client.jar`.

## Run

Start the server:

```bash
java -jar dist/uno-server.jar
```

Then start at least two clients in separate terminals:

```bash
java -jar dist/uno-client.jar
```

Without arguments, clients discover UNO servers on the local network over UDP port
8889. If multiple servers respond, the client prompts you to choose one. If no server
responds, it tries `localhost:8888`. You can also provide an explicit host and port:

```bash
java -jar dist/uno-client.jar server.example.com 8888
```

Cards animate onto the discard pile. Select a color before playing a wild card.

## Server configuration

Defaults are in `src/main/resources/server.properties`:

```properties
host=0.0.0.0
port=8888
discoveryPort=8889
minPlayers=2
maxPlayers=4
startingHandSize=7
```

Pass a properties file to override any defaults:

```bash
java -jar dist/uno-server.jar /path/to/server.properties
```

The server is authoritative for turn order, card validation, drawing, action cards,
winning, and player limits. Clients communicate using a small line-based TCP protocol.