package com.boardgame.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void registerAndLoginRoundTrip() throws Exception {
        UserStore store = new UserStore(tempDir.resolve("users.properties"));
        store.load();
        store.register("testuser", "secret123");
        UserStore.UserRecord record = store.login("testuser", "secret123");
        assertEquals("testuser", record.username());
        assertEquals("user", record.role());
    }

    @Test
    void rejectsDuplicateUsername() throws Exception {
        UserStore store = new UserStore(tempDir.resolve("users.properties"));
        store.load();
        store.register("alice", "pass1");
        assertThrows(IllegalArgumentException.class, () -> store.register("alice", "pass2"));
    }

    @Test
    void rejectsBlankUsername() throws Exception {
        UserStore store = new UserStore(tempDir.resolve("users.properties"));
        store.load();
        assertThrows(IllegalArgumentException.class, () -> store.register("", "pass"));
    }

    @Test
    void rejectsLongUsername() throws Exception {
        UserStore store = new UserStore(tempDir.resolve("users.properties"));
        store.load();
        assertThrows(IllegalArgumentException.class,
                () -> store.register("a".repeat(25), "pass"));
    }

    @Test
    void rejectsInvalidCredentials() throws Exception {
        UserStore store = new UserStore(tempDir.resolve("users.properties"));
        store.load();
        store.register("bob", "correct");
        assertThrows(IllegalArgumentException.class, () -> store.login("bob", "wrong"));
    }

    @Test
    void bootstrapsAdmin() throws Exception {
        UserStore store = new UserStore(tempDir.resolve("users.properties"));
        store.load();
        store.bootstrapAdmin("adminpass");
        assertTrue(store.hasAdmin());
        UserStore.UserRecord admin = store.login("admin", "adminpass");
        assertEquals("admin", admin.role());
    }

    @Test
    void hashVerificationWorks() {
        byte[] salt = new byte[16];
        String hash = UserStore.hashPassword("testpass", salt);
        assertNotNull(hash);
        assertTrue(UserStore.verifyPassword("testpass", java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(salt), hash));
    }

    @Test
    void persistsAndReloads() throws Exception {
        Path file = tempDir.resolve("users.properties");
        UserStore store1 = new UserStore(file);
        store1.load();
        store1.register("persist", "mypass");

        UserStore store2 = new UserStore(file);
        store2.load();
        UserStore.UserRecord record = store2.login("persist", "mypass");
        assertEquals("persist", record.username());
    }

    @Test
    void updateCharacter() throws Exception {
        UserStore store = new UserStore(tempDir.resolve("users.properties"));
        store.load();
        store.register("charuser", "pass");
        store.updateCharacter("charuser", "\u2665", "FF0000");
        UserStore.UserRecord record = store.getUser("charuser");
        assertEquals("\u2665", record.avatarSymbol());
        assertEquals("FF0000", record.avatarColor());
    }
}
