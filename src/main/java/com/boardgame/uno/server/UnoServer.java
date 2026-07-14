package com.boardgame.uno.server;

import com.boardgame.uno.game.UnoGame;
import com.boardgame.uno.model.Card.Color;
import com.boardgame.uno.network.LanDiscovery;
import com.boardgame.uno.protocol.Protocol;

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
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class UnoServer implements AutoCloseable {
    private final ServerConfig config;
    private final UnoGame game;
    private final Map<String, ClientSession> clients = new ConcurrentHashMap<>();
    private final ExecutorService pool;
    private ServerSocket serverSocket;

    public UnoServer(ServerConfig config) {
        this.config = config;
        game = new UnoGame(config.minPlayers(), config.maxPlayers(),
                config.startingHandSize(), new Random());
        pool = Executors.newFixedThreadPool(config.maxPlayers());
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(config.port(), config.maxPlayers(),
                InetAddress.getByName(config.host()));
        System.out.printf("Uno server listening on %s:%d%n", config.host(), config.port());
        try (LanDiscovery ignored = LanDiscovery.advertise(
                config.port(), config.discoveryPort())) {
            System.out.printf("LAN discovery listening on UDP port %d%n", config.discoveryPort());
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

    private void broadcast() {
        clients.forEach((name, client) -> client.send(Protocol.state(game.snapshot(name))));
    }

    private final class ClientSession implements Runnable {
        private final Socket socket;
        private PrintWriter output;
        private String name;

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
                send("ERROR|" + Protocol.encode(exception.getMessage() == null
                        ? "Connection failed" : exception.getMessage()));
            } finally {
                if (name != null) {
                    clients.remove(name);
                    game.removePlayer(name);
                    broadcast();
                }
            }
        }

        private void handle(String line) {
            if (line.length() > 512) {
                send("ERROR|" + Protocol.encode("Command is too long"));
                return;
            }
            String[] parts = line.split("\\|", -1);
            try {
                switch (parts[0]) {
                    case "JOIN" -> join(parts);
                    case "PLAY" -> play(parts);
                    case "DRAW" -> {
                        requireJoined();
                        game.drawForTurn(name);
                        broadcast();
                    }
                    default -> throw new IllegalArgumentException("Unknown command");
                }
            } catch (IllegalArgumentException | IllegalStateException exception) {
                send("ERROR|" + Protocol.encode(exception.getMessage()));
            }
        }

        private void join(String[] parts) {
            if (name != null || parts.length != 2) {
                throw new IllegalArgumentException("Invalid JOIN command");
            }
            String requested = Protocol.decode(parts[1]).strip();
            if (requested.isEmpty() || requested.length() > 24
                    || clients.putIfAbsent(requested, this) != null) {
                throw new IllegalArgumentException("Name must be unique and 1-24 characters");
            }
            name = requested;
            try {
                game.addPlayer(name);
            } catch (RuntimeException exception) {
                clients.remove(name);
                name = null;
                throw exception;
            }
            send("WELCOME|" + Protocol.encode(name));
            broadcast();
        }

        private void play(String[] parts) {
            requireJoined();
            if (parts.length < 2 || parts.length > 3) {
                throw new IllegalArgumentException("Invalid PLAY command");
            }
            int index = Integer.parseInt(parts[1]);
            Color color = parts.length == 3 && !parts[2].isEmpty()
                    ? Color.valueOf(parts[2]) : null;
            game.play(name, index, color);
            broadcast();
        }

        private void requireJoined() {
            if (name == null) {
                throw new IllegalStateException("Join before playing");
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
        try (UnoServer server = new UnoServer(ServerConfig.load(configPath))) {
            server.start();
        }
    }
}
