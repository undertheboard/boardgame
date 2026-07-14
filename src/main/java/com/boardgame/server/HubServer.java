package com.boardgame.server;

import com.boardgame.auth.UserStore;
import com.boardgame.auth.UserStore.UserRecord;
import com.boardgame.games.BoardGame;
import com.boardgame.network.LanDiscovery;
import com.boardgame.plugin.GameRegistry;
import com.boardgame.protocol.Protocol;
import com.boardgame.stats.StatsStore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public final class HubServer implements AutoCloseable {
    /** Emote/taunt identifiers clients may broadcast to their room. */
    static final Set<String> EMOTES = Set.of(
            "WAVE", "LAUGH", "CRY", "ANGRY", "SHOCK", "GG", "TAUNT", "BOAST", "HORN");

    private final ServerConfig config;
    private final UserStore userStore;
    private final StatsStore statsStore;
    private final Map<String, ClientSession> sessions = new ConcurrentHashMap<>();
    private final Set<ClientSession> spectators = ConcurrentHashMap.newKeySet();
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final AtomicInteger roomCounter = new AtomicInteger();
    private final ExecutorService pool;
    private final ScheduledExecutorService cleaner;
    /** Finished rooms are swept from the lobby after this long. */
    private static final long STALE_ROOM_MILLIS = TimeUnit.MINUTES.toMillis(5);
    private ServerSocket serverSocket;

    public HubServer(ServerConfig config) throws IOException {
        this.config = config;
        this.userStore = new UserStore(Path.of(config.usersFile()));
        userStore.load();
        userStore.bootstrapAdmin(config.adminPassword());
        this.statsStore = new StatsStore(Path.of(config.statsFile()));
        statsStore.load();
        GameRegistry.loadPluginsDirectory(Path.of(config.pluginsDir()));
        pool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "hub-client");
            t.setDaemon(true);
            return t;
        });
        cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "hub-room-cleaner");
            t.setDaemon(true);
            return t;
        });
        cleaner.scheduleAtFixedRate(this::sweepStaleRooms, 1, 1, TimeUnit.MINUTES);
    }

    /** Removes rooms whose game finished a while ago, returning players to the lobby. */
    private void sweepStaleRooms() {
        boolean removedAny = false;
        for (Room room : rooms.values()) {
            if (room.game().isFinished() && room.finishedSinceMillis() >= STALE_ROOM_MILLIS) {
                rooms.remove(room.id());
                removedAny = true;
                for (ClientSession session : sessions.values()) {
                    if (session.currentRoom == room) {
                        session.currentRoom = null;
                        session.send("ROOMCLOSED|" + Protocol.encode(
                                "Room \"" + room.name() + "\" was closed (game over)"));
                    }
                }
            }
        }
        if (removedAny) {
            broadcastLobby();
        }
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(config.port(), 50,
                InetAddress.getByName(config.host()));
        System.out.printf("Board Game Hub listening on %s:%d%n", config.host(), config.port());
        try (LanDiscovery ignored = LanDiscovery.advertise(
                config.port(), config.discoveryPort())) {
            System.out.printf("LAN discovery on UDP port %d%n", config.discoveryPort());
            while (!serverSocket.isClosed()) {
                Socket socket = serverSocket.accept();
                socket.setSoTimeout(300_000);
                pool.submit(new ClientSession(socket));
            }
        }
    }

    @Override
    public void close() throws IOException {
        if (serverSocket != null) {
            serverSocket.close();
        }
        pool.shutdownNow();
        cleaner.shutdownNow();
    }

    private void broadcastLobby() {
        String lobbyMsg = buildLobbyMessage();
        sessions.values().stream()
                .filter(s -> s.authenticated && s.currentRoom == null)
                .forEach(s -> s.send(lobbyMsg));
    }

    private void broadcastRoom(Room room) {
        BoardGame game = room.game();
        for (String player : game.players()) {
            ClientSession session = sessions.get(player);
            if (session != null) {
                session.send("GAMESTATE|" + game.gameType() + "|" + game.snapshot(player));
            }
        }
        // Spectators get a neutral snapshot that never contains private state
        // (e.g. UNO hands) because the spectator id is not a player.
        String spectatorState = "GAMESTATE|" + game.gameType() + "|" + game.snapshot("");
        for (ClientSession spectator : spectators) {
            if (spectator.spectatingRoom == room) {
                spectator.send(spectatorState);
            }
        }
    }

    private String buildRoomData() {
        return rooms.values().stream()
                .map(r -> Protocol.encode(r.id()) + ":" + Protocol.encode(r.name()) + ":"
                        + r.gameType() + ":" + r.players().size() + ":"
                        + r.game().maxPlayers() + ":" + (r.game().isStarted() ? "1" : "0")
                        + ":" + (r.game().isFinished() ? "1" : "0"))
                .collect(Collectors.joining(","));
    }

    private String buildLobbyMessage() {
        String roomData = buildRoomData();
        String onlineUsers = sessions.values().stream()
                .filter(s -> s.authenticated)
                .map(s -> Protocol.encode(s.username) + ":" + Protocol.encode(s.avatarSymbol)
                        + ":" + s.avatarColor + ":" + Protocol.encode(s.title)
                        + ":" + statsStore.get(s.username).level())
                .collect(Collectors.joining(","));
        return "LOBBY|" + roomData + "|" + onlineUsers;
    }

    private final class ClientSession implements Runnable {
        private final Socket socket;
        private PrintWriter output;
        private String username;
        private String role;
        private String avatarSymbol = "\u2605";
        private String avatarColor = "4FC3F7";
        private String title = "";
        private boolean authenticated;
        private Room currentRoom;
        private Room spectatingRoom;

        private ClientSession(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (socket;
                 BufferedReader input = new BufferedReader(new InputStreamReader(
                         socket.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter writer = new PrintWriter(socket.getOutputStream(), true,
                         StandardCharsets.UTF_8)) {
                output = writer;
                String line;
                while ((line = input.readLine()) != null) {
                    handle(line);
                }
            } catch (IOException | RuntimeException exception) {
                // Connection closed
            } finally {
                cleanup();
            }
        }

        private void cleanup() {
            spectators.remove(this);
            if (currentRoom != null) {
                leaveCurrentRoom();
            }
            if (username != null) {
                sessions.remove(username);
                broadcastLobby();
            }
        }

        private void handle(String line) {
            if (line.length() > 1024) {
                send("ERROR|" + Protocol.encode("Command too long"));
                return;
            }
            String[] parts = line.split("\\|", -1);
            try {
                switch (parts[0]) {
                    case "REGISTER" -> handleRegister(parts);
                    case "LOGIN" -> handleLogin(parts);
                    case "CHARACTER" -> handleCharacter(parts);
                    case "LIST" -> handleList();
                    case "CREATE" -> handleCreate(parts);
                    case "JOINROOM" -> handleJoinRoom(parts);
                    case "LEAVEROOM" -> handleLeaveRoom();
                    case "MOVE" -> handleMove(parts);
                    case "PLAYAGAIN" -> handlePlayAgain();
                    case "CHAT" -> handleChat(parts);
                    case "EMOTE" -> handleEmote(parts);
                    case "LEADERBOARD" -> handleLeaderboard();
                    case "ROOMS" -> send("ROOMS|" + buildRoomData());
                    case "SPECTATE" -> handleSpectate(parts);
                    case "UNSPECTATE" -> handleUnspectate();
                    case "KICK" -> handleKick(parts);
                    case "DELETEUSER" -> handleDeleteUser(parts);
                    default -> send("ERROR|" + Protocol.encode("Unknown command"));
                }
            } catch (IllegalArgumentException | IllegalStateException exception) {
                send("ERROR|" + Protocol.encode(exception.getMessage()));
            } catch (IOException exception) {
                send("ERROR|" + Protocol.encode("Server error"));
            }
        }

        private void handleRegister(String[] parts) throws IOException {
            if (parts.length != 3) {
                throw new IllegalArgumentException("Usage: REGISTER|username|password");
            }
            String user = Protocol.decode(parts[1]);
            String pass = Protocol.decode(parts[2]);
            userStore.register(user, pass);
            send("OK|" + Protocol.encode("Registered successfully"));
        }

        private void handleLogin(String[] parts) {
            if (parts.length != 3) {
                throw new IllegalArgumentException("Usage: LOGIN|username|password");
            }
            String user = Protocol.decode(parts[1]);
            String pass = Protocol.decode(parts[2]);
            UserRecord record = userStore.login(user, pass);
            if (sessions.containsKey(user)) {
                throw new IllegalArgumentException("User already logged in");
            }
            username = record.username();
            role = record.role();
            avatarSymbol = record.avatarSymbol();
            avatarColor = record.avatarColor();
            title = record.title();
            authenticated = true;
            sessions.put(username, this);
            send("WELCOME|" + Protocol.encode(username) + "|" + Protocol.encode(role)
                    + "|" + Protocol.encode(avatarSymbol) + "|" + avatarColor
                    + "|" + Protocol.encode(title) + "|" + statsStore.get(username).level());
            send("GAMES|" + String.join(",", GameRegistry.types()));
            broadcastLobby();
        }

        private void handleCharacter(String[] parts) throws IOException {
            requireAuth();
            if (parts.length != 3 && parts.length != 4) {
                throw new IllegalArgumentException("Usage: CHARACTER|symbol|hexcolor[|title]");
            }
            String symbol = parts[1];
            String color = parts[2];
            String newTitle = parts.length == 4 ? Protocol.decode(parts[3]) : title;
            if (symbol.isEmpty() || symbol.length() > 2) {
                throw new IllegalArgumentException("Symbol must be 1-2 characters");
            }
            if (!color.matches("[0-9a-fA-F]{6}")) {
                throw new IllegalArgumentException("Color must be 6-digit hex");
            }
            if (newTitle.length() > 24 || !newTitle.matches("[a-zA-Z0-9 _]*")) {
                throw new IllegalArgumentException(
                        "Title must be at most 24 letters, digits or spaces");
            }
            userStore.updateCharacter(username, symbol, color, newTitle);
            avatarSymbol = symbol;
            avatarColor = color;
            title = newTitle;
            send("OK|" + Protocol.encode("Character updated"));
            broadcastLobby();
        }

        private void handleList() {
            requireAuth();
            send(buildLobbyMessage());
        }

        private void handleCreate(String[] parts) {
            requireAuth();
            if (parts.length != 3) {
                throw new IllegalArgumentException("Usage: CREATE|gameType|roomName");
            }
            if (rooms.size() >= config.maxRooms()) {
                throw new IllegalStateException("Maximum rooms reached");
            }
            String gameType = parts[1].toUpperCase();
            String roomName = Protocol.decode(parts[2]);
            if (roomName.isBlank() || roomName.length() > 32) {
                throw new IllegalArgumentException("Room name must be 1-32 characters");
            }
            BoardGame game = GameFactory.create(gameType);
            String roomId = "room-" + roomCounter.incrementAndGet();
            Room room = new Room(roomId, roomName, game);
            rooms.put(roomId, room);
            send("OK|" + Protocol.encode(roomId));
            broadcastLobby();
        }

        private void handleJoinRoom(String[] parts) {
            requireAuth();
            if (parts.length != 2) {
                throw new IllegalArgumentException("Usage: JOINROOM|roomId");
            }
            if (currentRoom != null) {
                throw new IllegalStateException("Already in a room");
            }
            String roomId = parts[1];
            Room room = rooms.get(roomId);
            if (room == null) {
                throw new IllegalArgumentException("Room not found");
            }
            room.game().addPlayer(username);
            currentRoom = room;
            send("OK|" + Protocol.encode("Joined " + room.name()));
            broadcastRoom(room);
            broadcastLobby();
        }

        private void handleLeaveRoom() {
            requireAuth();
            if (currentRoom == null) {
                throw new IllegalStateException("Not in a room");
            }
            leaveCurrentRoom();
            send("OK|" + Protocol.encode("Left room"));
            broadcastLobby();
        }

        private void leaveCurrentRoom() {
            if (currentRoom != null) {
                Room room = currentRoom;
                room.game().removePlayer(username);
                currentRoom = null;
                if (room.game().players().isEmpty()) {
                    rooms.remove(room.id());
                } else {
                    broadcastRoom(room);
                }
            }
        }

        private void handleMove(String[] parts) {
            requireAuth();
            if (currentRoom == null) {
                throw new IllegalStateException("Not in a room");
            }
            String moveData = parts.length > 1
                    ? String.join("|", java.util.Arrays.copyOfRange(parts, 1, parts.length))
                    : "";
            currentRoom.game().move(username, moveData);
            broadcastRoom(currentRoom);
            if (currentRoom.game().isFinished()) {
                currentRoom.noteFinished();
                recordResult(currentRoom);
                broadcastLobby();
            }
        }

        /** Restarts a finished game in the same room with the same players. */
        private void handlePlayAgain() {
            requireAuth();
            if (currentRoom == null) {
                throw new IllegalStateException("Not in a room");
            }
            Room room = currentRoom;
            synchronized (room) {
                if (!room.game().isFinished()) {
                    throw new IllegalStateException("Game is still running");
                }
                var players = room.game().players();
                var freshGame = GameRegistry.create(room.gameType());
                for (String player : players) {
                    freshGame.addPlayer(player);
                }
                room.resetGame(freshGame);
            }
            broadcastToRoom(room, "CHAT|" + Protocol.encode(username)
                    + "|" + Protocol.encode("started a rematch!"));
            broadcastRoom(room);
            broadcastLobby();
        }

        private void recordResult(Room room) {
            if (!room.markResultRecorded()) {
                return;
            }
            try {
                statsStore.recordResult(room.game().winner(), room.game().players());
            } catch (IOException exception) {
                System.err.println("Failed to save stats: " + exception);
            }
        }

        private void handleChat(String[] parts) {
            requireAuth();
            if (currentRoom == null) {
                throw new IllegalStateException("Not in a room");
            }
            if (parts.length != 2) {
                throw new IllegalArgumentException("Usage: CHAT|message");
            }
            String text = Protocol.decode(parts[1]).strip();
            if (text.isEmpty() || text.length() > 200) {
                throw new IllegalArgumentException("Message must be 1-200 characters");
            }
            broadcastToRoom(currentRoom,
                    "CHAT|" + Protocol.encode(username) + "|" + Protocol.encode(text));
        }

        private void handleEmote(String[] parts) {
            requireAuth();
            if (currentRoom == null) {
                throw new IllegalStateException("Not in a room");
            }
            if (parts.length != 2) {
                throw new IllegalArgumentException("Usage: EMOTE|emoteId");
            }
            String emote = parts[1].toUpperCase();
            if (!EMOTES.contains(emote)) {
                throw new IllegalArgumentException("Unknown emote");
            }
            broadcastToRoom(currentRoom,
                    "EMOTE|" + Protocol.encode(username) + "|" + emote);
        }

        private void broadcastToRoom(Room room, String message) {
            for (String player : room.players()) {
                ClientSession session = sessions.get(player);
                if (session != null) {
                    session.send(message);
                }
            }
            for (ClientSession spectator : spectators) {
                if (spectator.spectatingRoom == room) {
                    spectator.send(message);
                }
            }
        }

        /**
         * Starts watching a room. Deliberately available without login so a
         * read-only spectator/projection client can cast games; spectators
         * only ever receive the neutral public snapshot.
         */
        private void handleSpectate(String[] parts) {
            if (parts.length != 2) {
                throw new IllegalArgumentException("Usage: SPECTATE|roomId");
            }
            Room room = rooms.get(parts[1]);
            if (room == null) {
                throw new IllegalArgumentException("Room not found");
            }
            spectatingRoom = room;
            spectators.add(this);
            send("OK|" + Protocol.encode("Watching " + room.name()));
            send("GAMESTATE|" + room.gameType() + "|" + room.game().snapshot(""));
        }

        private void handleUnspectate() {
            spectatingRoom = null;
            spectators.remove(this);
            send("OK|" + Protocol.encode("Stopped watching"));
        }

        private void handleLeaderboard() {
            requireAuth();
            String data = statsStore.top(20).stream()
                    .map(stat -> Protocol.encode(stat.username()) + ":" + stat.wins()
                            + ":" + stat.losses() + ":" + stat.draws() + ":" + stat.level())
                    .collect(Collectors.joining(","));
            send("LEADERBOARD|" + data);
        }

        private void handleKick(String[] parts) {
            requireAuth();
            requireAdmin();
            if (parts.length != 2) {
                throw new IllegalArgumentException("Usage: KICK|username");
            }
            String target = Protocol.decode(parts[1]);
            ClientSession targetSession = sessions.get(target);
            if (targetSession == null) {
                throw new IllegalArgumentException("User not online");
            }
            targetSession.send("ERROR|" + Protocol.encode("You have been kicked"));
            try {
                targetSession.socket.close();
            } catch (IOException ignored) {
                // already closing
            }
            send("OK|" + Protocol.encode("Kicked " + target));
        }

        private void handleDeleteUser(String[] parts) throws IOException {
            requireAuth();
            requireAdmin();
            if (parts.length != 2) {
                throw new IllegalArgumentException("Usage: DELETEUSER|username");
            }
            String target = Protocol.decode(parts[1]);
            if (target.equals(username)) {
                throw new IllegalArgumentException("Cannot delete yourself");
            }
            if (!userStore.deleteUser(target)) {
                throw new IllegalArgumentException("User not found");
            }
            // Kick if online
            ClientSession targetSession = sessions.get(target);
            if (targetSession != null) {
                targetSession.send("ERROR|" + Protocol.encode("Your account has been deleted"));
                try {
                    targetSession.socket.close();
                } catch (IOException ignored) {
                    // closing
                }
            }
            send("OK|" + Protocol.encode("Deleted " + target));
        }

        private void requireAuth() {
            if (!authenticated) {
                throw new IllegalStateException("Login required");
            }
        }

        private void requireAdmin() {
            if (!"admin".equals(role)) {
                throw new IllegalStateException("Admin privileges required");
            }
        }

        private synchronized void send(String message) {
            if (output != null) {
                output.println(message);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Path configPath = args.length == 0 ? null : Path.of(args[0]);
        try (HubServer server = new HubServer(ServerConfig.load(configPath))) {
            server.start();
        }
    }
}
