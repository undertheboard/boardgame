package com.boardgame.auth;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class UserStore {
    private static final int ITERATIONS = 100_000;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Path filePath;
    private final Map<String, UserRecord> users = new LinkedHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public record UserRecord(String username, String salt, String hash,
                             String role, String avatarSymbol, String avatarColor,
                             String title) {
        public String serialize() {
            return salt + ":" + hash + ":" + role + ":" + avatarSymbol + ":" + avatarColor
                    + ":" + title;
        }

        public static UserRecord deserialize(String username, String value) {
            String[] parts = value.split(":", 6);
            if (parts.length < 5) {
                throw new IllegalArgumentException("Invalid user record for: " + username);
            }
            String title = parts.length >= 6 ? parts[5] : "";
            return new UserRecord(username, parts[0], parts[1], parts[2], parts[3], parts[4],
                    title);
        }
    }

    public UserStore(Path filePath) {
        this.filePath = filePath;
    }

    public void load() throws IOException {
        lock.writeLock().lock();
        try {
            users.clear();
            if (Files.exists(filePath)) {
                Properties props = new Properties();
                try (Reader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
                    props.load(reader);
                }
                for (String key : props.stringPropertyNames()) {
                    users.put(key, UserRecord.deserialize(key, props.getProperty(key)));
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void save() throws IOException {
        lock.readLock().lock();
        try {
            Properties props = new Properties();
            users.forEach((name, record) -> props.setProperty(name, record.serialize()));
            try (Writer writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
                props.store(writer, "Board Game Hub Users");
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    public UserRecord register(String username, String password) throws IOException {
        validateUsername(username);
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password cannot be blank");
        }
        lock.writeLock().lock();
        try {
            if (users.containsKey(username)) {
                throw new IllegalArgumentException("Username already exists");
            }
            UserRecord record = createRecord(username, password, "user", "\u2605", "4FC3F7");
            users.put(username, record);
            save();
            return record;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public UserRecord login(String username, String password) {
        if (username == null || password == null) {
            throw new IllegalArgumentException("Invalid credentials");
        }
        lock.readLock().lock();
        try {
            UserRecord record = users.get(username);
            if (record == null || !verifyPassword(password, record.salt(), record.hash())) {
                throw new IllegalArgumentException("Invalid credentials");
            }
            return record;
        } finally {
            lock.readLock().unlock();
        }
    }

    public UserRecord updateCharacter(String username, String symbol, String color,
                                      String title) throws IOException {
        lock.writeLock().lock();
        try {
            UserRecord record = users.get(username);
            if (record == null) {
                throw new IllegalArgumentException("User not found");
            }
            UserRecord updated = new UserRecord(username, record.salt(), record.hash(),
                    record.role(), symbol, color, title);
            users.put(username, updated);
            save();
            return updated;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean deleteUser(String username) throws IOException {
        lock.writeLock().lock();
        try {
            if (users.remove(username) != null) {
                save();
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean hasAdmin() {
        lock.readLock().lock();
        try {
            return users.values().stream().anyMatch(r -> "admin".equals(r.role()));
        } finally {
            lock.readLock().unlock();
        }
    }

    public void bootstrapAdmin(String password) throws IOException {
        lock.writeLock().lock();
        try {
            if (!hasAdmin()) {
                UserRecord record = createRecord("admin", password, "admin", "\u265B", "FFD700");
                users.put("admin", record);
                save();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public UserRecord getUser(String username) {
        lock.readLock().lock();
        try {
            return users.get(username);
        } finally {
            lock.readLock().unlock();
        }
    }

    private static void validateUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be blank");
        }
        if (username.length() > 24) {
            throw new IllegalArgumentException("Username must be 24 characters or fewer");
        }
        if (!username.matches("[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException("Username must be alphanumeric or underscore");
        }
    }

    private static UserRecord createRecord(String username, String password,
                                           String role, String symbol, String color) {
        byte[] saltBytes = new byte[SALT_LENGTH];
        RANDOM.nextBytes(saltBytes);
        String salt = Base64.getUrlEncoder().withoutPadding().encodeToString(saltBytes);
        String hash = hashPassword(password, saltBytes);
        return new UserRecord(username, salt, hash, role, symbol, color, "");
    }

    static String hashPassword(String password, byte[] saltBytes) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), saltBytes, ITERATIONS, KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hashBytes = factory.generateSecret(spec).getEncoded();
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("PBKDF2 not available", e);
        }
    }

    static boolean verifyPassword(String password, String salt, String hash) {
        byte[] saltBytes = Base64.getUrlDecoder().decode(salt);
        String computed = hashPassword(password, saltBytes);
        return computed.equals(hash);
    }
}
