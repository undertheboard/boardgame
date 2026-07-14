package com.boardgame.uno.network;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LanDiscovery implements AutoCloseable {
    public static final int DEFAULT_PORT = 8889;
    private static final byte[] REQUEST = "UNO_DISCOVER_V1".getBytes(StandardCharsets.US_ASCII);
    private static final String RESPONSE_PREFIX = "UNO_SERVER_V1|";
    private static final int MAX_PACKET_SIZE = 64;

    private final DatagramSocket socket;

    private LanDiscovery(int gamePort, int discoveryPort) throws SocketException {
        socket = new DatagramSocket(null);
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(discoveryPort));
        Thread responder = new Thread(() -> respond(gamePort), "uno-lan-discovery");
        responder.setDaemon(true);
        responder.start();
    }

    public static LanDiscovery advertise(int gamePort, int discoveryPort) throws SocketException {
        return new LanDiscovery(gamePort, discoveryPort);
    }

    public static List<Server> discover(int discoveryPort, int timeoutMillis) throws IOException {
        Set<InetAddress> targets = new LinkedHashSet<>();
        targets.add(InetAddress.getLoopbackAddress());
        targets.add(InetAddress.getByName("255.255.255.255"));
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            for (InterfaceAddress address : interfaces.nextElement().getInterfaceAddresses()) {
                if (address.getBroadcast() != null) {
                    targets.add(address.getBroadcast());
                }
            }
        }
        return discover(discoveryPort, timeoutMillis, targets);
    }

    static List<Server> discover(int discoveryPort, int timeoutMillis,
                                 Collection<InetAddress> targets) throws IOException {
        if (timeoutMillis < 1) {
            throw new IllegalArgumentException("timeoutMillis must be positive");
        }
        Map<String, Server> servers = new LinkedHashMap<>();
        try (DatagramSocket client = new DatagramSocket()) {
            client.setBroadcast(true);
            for (InetAddress target : targets) {
                client.send(new DatagramPacket(REQUEST, REQUEST.length, target, discoveryPort));
            }

            long deadline = System.currentTimeMillis() + timeoutMillis;
            byte[] buffer = new byte[MAX_PACKET_SIZE];
            while (System.currentTimeMillis() < deadline) {
                client.setSoTimeout((int) Math.max(1, deadline - System.currentTimeMillis()));
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    client.receive(packet);
                } catch (SocketTimeoutException exception) {
                    break;
                }
                Server server = parse(packet);
                if (server != null) {
                    servers.putIfAbsent(server.host() + ":" + server.port(), server);
                }
            }
        }
        return new ArrayList<>(servers.values());
    }

    private void respond(int gamePort) {
        byte[] buffer = new byte[MAX_PACKET_SIZE];
        while (!socket.isClosed()) {
            DatagramPacket request = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(request);
                if (matches(request, REQUEST)) {
                    byte[] response = (RESPONSE_PREFIX + gamePort).getBytes(StandardCharsets.US_ASCII);
                    socket.send(new DatagramPacket(response, response.length,
                            request.getAddress(), request.getPort()));
                }
            } catch (IOException exception) {
                if (!socket.isClosed()) {
                    System.err.println("LAN discovery stopped: " + exception.getMessage());
                }
                return;
            }
        }
    }

    private static Server parse(DatagramPacket packet) {
        String response = new String(packet.getData(), packet.getOffset(), packet.getLength(),
                StandardCharsets.US_ASCII);
        if (!response.startsWith(RESPONSE_PREFIX)) {
            return null;
        }
        try {
            int port = Integer.parseInt(response.substring(RESPONSE_PREFIX.length()));
            if (port < 1 || port > 65535) {
                return null;
            }
            return new Server(packet.getAddress().getHostAddress(), port);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static boolean matches(DatagramPacket packet, byte[] expected) {
        if (packet.getLength() != expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (packet.getData()[packet.getOffset() + i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void close() {
        socket.close();
    }

    public record Server(String host, int port) {
        @Override
        public String toString() {
            return host + ":" + port;
        }
    }
}
