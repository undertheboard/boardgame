package com.boardgame.server;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public record ServerConfig(String host, int port, int discoveryPort, int minPlayers,
                           int maxPlayers, int startingHandSize, String adminPassword,
                           String usersFile, int maxRooms) {
    public static ServerConfig load(Path override) throws IOException {
        Properties values = new Properties();
        try (InputStream defaults = ServerConfig.class.getResourceAsStream("/server.properties")) {
            if (defaults == null) {
                throw new IOException("Missing server.properties");
            }
            values.load(defaults);
        }
        if (override != null) {
            try (InputStream input = Files.newInputStream(override)) {
                values.load(input);
            }
        }
        return new ServerConfig(values.getProperty("host"),
                integer(values, "port", 1, 65535),
                integer(values, "discoveryPort", 1, 65535),
                integer(values, "minPlayers", 2, 10),
                integer(values, "maxPlayers", 2, 10),
                integer(values, "startingHandSize", 1, 20),
                values.getProperty("adminPassword", "admin"),
                values.getProperty("usersFile", "users.properties"),
                integer(values, "maxRooms", 1, 100));
    }

    private static int integer(Properties values, String key, int min, int max) {
        int value;
        try {
            value = Integer.parseInt(values.getProperty(key, String.valueOf(min)));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid configuration value: " + key, exception);
        }
        if (value < min || value > max) {
            throw new IllegalArgumentException(key + " must be between " + min + " and " + max);
        }
        return value;
    }

    public ServerConfig {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host cannot be blank");
        }
        if (minPlayers > maxPlayers) {
            throw new IllegalArgumentException("minPlayers cannot exceed maxPlayers");
        }
        if (usersFile == null || usersFile.isBlank()) {
            throw new IllegalArgumentException("usersFile cannot be blank");
        }
        if (maxRooms < 1) {
            throw new IllegalArgumentException("maxRooms must be at least 1");
        }
    }
}
