package com.boardgame.uno.client;

import com.boardgame.uno.protocol.Protocol;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

final class ServerConnection implements Closeable {
    private final Socket socket;
    private final PrintWriter output;
    private final BufferedReader input;

    ServerConnection(String host, int port) throws IOException {
        socket = new Socket(host, port);
        output = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
        input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    }

    void join(String name) {
        send("JOIN|" + Protocol.encode(name));
    }

    void play(int index, String color) {
        send("PLAY|" + index + "|" + (color == null ? "" : color));
    }

    void draw() {
        send("DRAW");
    }

    void listen(Consumer<String> listener) {
        Thread thread = new Thread(() -> {
            try {
                String line;
                while ((line = input.readLine()) != null) {
                    listener.accept(line);
                }
            } catch (IOException exception) {
                listener.accept("ERROR|" + Protocol.encode("Disconnected from server"));
            }
        }, "uno-server-listener");
        thread.setDaemon(true);
        thread.start();
    }

    private synchronized void send(String message) {
        output.println(message);
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
