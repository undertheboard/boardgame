package com.boardgame.uno.network;

import org.junit.jupiter.api.Test;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LanDiscoveryTest {
    @Test
    void discoversAdvertisedServer() throws Exception {
        int discoveryPort;
        try (DatagramSocket socket = new DatagramSocket(0)) {
            discoveryPort = socket.getLocalPort();
        }

        try (LanDiscovery ignored = LanDiscovery.advertise(45678, discoveryPort)) {
            List<LanDiscovery.Server> servers = LanDiscovery.discover(
                    discoveryPort, 250, List.of(InetAddress.getLoopbackAddress()));

            assertEquals(List.of(new LanDiscovery.Server(
                    InetAddress.getLoopbackAddress().getHostAddress(), 45678)), servers);
        }
    }
}
