package com.boardgame.server;

import com.boardgame.auth.UserStore;
import com.boardgame.auth.UserStore.UserRecord;
import com.boardgame.games.BoardGame;
import com.boardgame.network.LanDiscovery;
import com.boardgame.protocol.Protocol;

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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public final class HubServer implements AutoCloseable {
    private final ServerConfig config;
    private final UserStore userStore;
    private final Map<String, ClientSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final AtomicInteger roomCounter = new AtomicInteger();
    private final ExecutorService pool;
    private ServerSocket serverSocket;

    public HubServer(ServerConfig config) throws IOException {
        this.config = config;
        this.userStore = new UserStore(Path.of(config.usersFile()));
        userStore.load();
        userStore.bootstrapAdmin(config.adminPassword());
        pool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "hub-client");
            t.setDaemon(true);
            return t;
        });
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
                session.send("GAMESTATE|" + game.snapshot(player));
            }
        }
    }

    private String buildLobbyMessage() {
        String roomData = rooms.values().stream()
                .map(r -> Protocol.encode(r.id()) + ":" + Protocol.encode(r.name()) + ":"
                        + r.gameType() + ":" + r.players().size() + ":"
                        + r.game().maxPlayers() + ":" + (r.game().isStarted() ? "1" : "0")
                        + ":" + (r.game().isFinished() ? "1" : "0"))
                .collect(Collectors.joining(","));
        String onlineUsers = sessions.values().stream()
                .filter(s -> s.authenticated)
                .map(s -> Protocol.encode(s.username) + ":" + Protocol.encode(s.avatarSymbol)
                        + ":" + s.avatarColor)
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
        private boolean authenticated;
        private Room currentRoom;

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
            send("OK|Registered successfully");
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
            authenticated = true;
            sessions.put(username, this);
            send("WELCOME|" + Protocol.encode(username) + "|" + Protocol.encode(role)
                    + "|" + Protocol.encode(avatarSymbol) + "|" + avatarColor);
            broadcastLobby();
        }

        private void handleCharacter(String[] parts) throws IOException {
            requireAuth();
            if (parts.length != 3) {
                throw new IllegalArgumentException("Usage: CHARACTER|symbol|hexcolor");
            }
            String symbol = parts[1];
            String color = parts[2];
            if (symbol.isEmpty() || symbol.length() > 2) {
                throw new IllegalArgumentException("Symbol must be 1-2 characters");
            }
            if (!color.matches("[0-9a-fA-F]{6}")) {
                throw new IllegalArgumentException("Color must be 6-digit hex");
            }
            userStore.updateCharacter(username, symbol, color);
            avatarSymbol = symbol;
            avatarColor = color;
            send("OK|Character updated");
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
            send("OK|" + roomId);
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
            send("OK|Joined " + room.name());
            broadcastRoom(room);
            broadcastLobby();
        }

        private void handleLeaveRoom() {
            requireAuth();
            if (currentRoom == null) {
                throw new IllegalStateException("Not in a room");
            }
            leaveCurrentRoom();
            send("OK|Left room");
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
                broadcastLobby();
            }
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
            send("OK|Kicked " + target);
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
            send("OK|Deleted " + target);
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
