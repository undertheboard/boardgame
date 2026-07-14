package com.boardgame.client;

import com.boardgame.network.LanDiscovery;
import com.boardgame.protocol.Protocol;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Read-only spectator client. Connects to a hub <b>without logging in</b>,
 * lists the active rooms and casts any of them to the screen.
 *
 * <p>Designed for projection: everything is drawn with very large fonts, the
 * player whose turn it is shown in a huge pulsing banner, and {@code F11}
 * toggles borderless full-screen so the window can be thrown onto a beamer,
 * TV or casting device.
 *
 * <p>Run with {@code java -jar dist/hub-spectator.jar [host [port]]}.
 */
public final class SpectatorClient extends JFrame {
    private static final Color BG_DARK = new Color(0x12, 0x13, 0x1C);
    private static final Color BG_CARD = new Color(0x2A, 0x2B, 0x38);
    private static final Color ACCENT = new Color(0x6C, 0x63, 0xFF);
    private static final Color TEXT_PRIMARY = new Color(0xE8, 0xE8, 0xF0);
    private static final Color TEXT_SECONDARY = new Color(0xA0, 0xA0, 0xB0);
    private static final Font FONT_BANNER = new Font("SansSerif", Font.BOLD, 54);
    private static final Font FONT_BIG = new Font("SansSerif", Font.BOLD, 30);
    private static final Font FONT_BODY = new Font("SansSerif", Font.PLAIN, 18);

    private final CardLayout cards = new CardLayout();
    private final JPanel main = new JPanel(cards);
    private Socket socket;
    private PrintWriter output;

    // Rooms screen
    private JPanel roomsPanel;
    private Timer refreshTimer;

    // Watch screen
    private final JLabel turnBanner = big(" ", FONT_BANNER, ACCENT);
    private final JLabel statusLabel = big(" ", FONT_BIG, TEXT_PRIMARY);
    private final JLabel tickerLabel = big(" ", FONT_BODY, TEXT_SECONDARY);
    private final BoardView boardView = new BoardView();
    private String watchedGameType = "";
    private float bannerPulse;
    private boolean fullScreen;

    private SpectatorClient(String host, int port) {
        super("Board Game Hub — Spectator");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1024, 720));
        setLocationByPlatform(true);
        getContentPane().setBackground(BG_DARK);

        main.setBackground(BG_DARK);
        main.add(buildRoomsScreen(), "rooms");
        main.add(buildWatchScreen(), "watch");
        add(main);

        // F11 toggles borderless full-screen for projection / casting
        getRootPane().registerKeyboardAction(e -> toggleFullScreen(),
                KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        Timer pulse = new Timer(70, e -> {
            bannerPulse += 0.12f;
            float shade = 0.75f + 0.25f * (float) Math.abs(Math.sin(bannerPulse));
            turnBanner.setForeground(new Color(
                    (int) (ACCENT.getRed() * shade),
                    (int) (ACCENT.getGreen() * shade),
                    Math.min(255, (int) (ACCENT.getBlue() * shade))));
        });
        pulse.start();

        connect(host, port);
    }

    private JPanel buildRoomsScreen() {
        JPanel panel = darkPanel(new BorderLayout(16, 16));
        panel.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        JLabel title = big("\uD83D\uDCFA Watch a game — no login needed", FONT_BIG, TEXT_PRIMARY);
        JLabel hint = big("Press F11 for full screen. Rooms refresh automatically.",
                FONT_BODY, TEXT_SECONDARY);
        JPanel north = darkPanel(new BorderLayout());
        north.add(title, BorderLayout.NORTH);
        north.add(hint, BorderLayout.SOUTH);
        panel.add(north, BorderLayout.NORTH);

        roomsPanel = darkPanel(null);
        roomsPanel.setLayout(new BoxLayout(roomsPanel, BoxLayout.Y_AXIS));
        JScrollPane scroll = new JScrollPane(roomsPanel);
        scroll.setBackground(BG_DARK);
        scroll.getViewport().setBackground(BG_DARK);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildWatchScreen() {
        JPanel panel = darkPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        turnBanner.setHorizontalAlignment(SwingConstants.CENTER);
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        JPanel north = darkPanel(new BorderLayout(4, 4));
        north.add(turnBanner, BorderLayout.NORTH);
        north.add(statusLabel, BorderLayout.SOUTH);
        panel.add(north, BorderLayout.NORTH);

        panel.add(boardView, BorderLayout.CENTER);

        JButton back = new JButton("\u2B05 Back to rooms");
        back.setFont(FONT_BODY);
        back.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        back.addActionListener(e -> {
            send("UNSPECTATE");
            tickerLabel.setText(" ");
            cards.show(main, "rooms");
        });
        tickerLabel.setHorizontalAlignment(SwingConstants.CENTER);
        JPanel south = darkPanel(new BorderLayout(8, 0));
        south.add(back, BorderLayout.WEST);
        south.add(tickerLabel, BorderLayout.CENTER);
        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    private void toggleFullScreen() {
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice();
        fullScreen = !fullScreen;
        dispose();
        setUndecorated(fullScreen);
        setVisible(true);
        device.setFullScreenWindow(fullScreen ? this : null);
    }

    // --- Network ---

    private void connect(String host, int port) {
        try {
            socket = new Socket(host, port);
            output = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
            BufferedReader input = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            Thread listener = new Thread(() -> {
                try {
                    String line;
                    while ((line = input.readLine()) != null) {
                        String received = line;
                        SwingUtilities.invokeLater(() -> handleMessage(received));
                    }
                } catch (IOException e) {
                    SwingUtilities.invokeLater(() ->
                            statusLabel.setText("Disconnected from server"));
                }
            }, "spectator-listener");
            listener.setDaemon(true);
            listener.start();
            send("ROOMS");
            refreshTimer = new Timer(3000, e -> send("ROOMS"));
            refreshTimer.start();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Connection failed: " + e.getMessage(),
                    "Spectator", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void send(String command) {
        if (output != null) {
            output.println(command);
        }
    }

    private void handleMessage(String line) {
        String[] parts = line.split("\\|", -1);
        switch (parts[0]) {
            case "ROOMS" -> renderRooms(parts.length > 1 ? parts[1] : "");
            case "GAMESTATE" -> renderState(parts);
            case "CHAT" -> tickerLabel.setText(
                    "\uD83D\uDCAC " + Protocol.decode(parts[1]) + ": " + Protocol.decode(parts[2]));
            case "EMOTE" -> tickerLabel.setText(
                    "\u2728 " + Protocol.decode(parts[1]) + " sends " + parts[2]);
            case "ERROR" -> statusLabel.setText(
                    parts.length > 1 ? Protocol.decode(parts[1]) : "Error");
            default -> {
                // OK and other messages need no spectator handling
            }
        }
    }

    private void renderRooms(String roomData) {
        roomsPanel.removeAll();
        if (!roomData.isEmpty()) {
            for (String entry : roomData.split(",")) {
                String[] f = entry.split(":", 7);
                if (f.length < 7) {
                    continue;
                }
                String roomId = Protocol.decode(f[0]);
                String name = Protocol.decode(f[1]);
                String status = f[6].equals("1") ? "Finished"
                        : f[5].equals("1") ? "In Progress" : "Waiting";
                JPanel card = darkPanel(new BorderLayout(12, 4));
                card.setBackground(BG_CARD);
                card.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
                card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 84));
                JLabel label = big(name + "  [" + f[2] + "]  " + f[3] + "/" + f[4]
                        + " players — " + status, FONT_BIG, TEXT_PRIMARY);
                card.add(label, BorderLayout.CENTER);
                JButton watch = new JButton("\uD83D\uDC41 Watch");
                watch.setFont(FONT_BODY);
                watch.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                watch.addActionListener(e -> {
                    watchedGameType = f[2];
                    send("SPECTATE|" + roomId);
                    cards.show(main, "watch");
                });
                card.add(watch, BorderLayout.EAST);
                roomsPanel.add(card);
                roomsPanel.add(Box.createVerticalStrut(10));
            }
        }
        if (roomsPanel.getComponentCount() == 0) {
            roomsPanel.add(big("  No rooms are open right now\u2026", FONT_BIG, TEXT_SECONDARY));
        }
        roomsPanel.revalidate();
        roomsPanel.repaint();
    }

    private void renderState(String[] parts) {
        if (parts.length < 3) {
            return;
        }
        String gameType = parts[1];
        watchedGameType = gameType;
        String snapshot = String.join("|", java.util.Arrays.copyOfRange(parts, 2, parts.length));
        String[] fields = snapshot.split("\\|", -1);
        if (fields.length < 3) {
            return;
        }
        boolean started = Boolean.parseBoolean(fields[0]);
        boolean finished = Boolean.parseBoolean(fields[1]);
        String currentPlayer = Protocol.decode(fields[2]);
        String message = Protocol.decode(fields[fields.length - 1]);

        if (finished) {
            turnBanner.setText("\uD83C\uDFC1 " + message);
            statusLabel.setText("Game over");
        } else if (!started) {
            turnBanner.setText("\u23F3 Waiting for players\u2026");
            statusLabel.setText(message);
        } else {
            turnBanner.setText("\u25B6 " + (currentPlayer.isEmpty()
                    ? "Both players choosing\u2026" : currentPlayer + "'s turn"));
            statusLabel.setText(message);
        }
        boardView.update(gameType, fields);
    }

    /** Generic large-scale board painter for the grid-based games. */
    private static final class BoardView extends JComponent {
        private String gameType = "";
        private String[] rows = new String[0];
        private String info = "";

        void update(String gameType, String[] fields) {
            this.gameType = gameType;
            this.rows = new String[0];
            this.info = "";
            String data = fields.length > 3 ? fields[3] : "";
            switch (gameType) {
                case "TICTACTOE" -> {
                    if (data.length() == 9) {
                        rows = new String[]{data.substring(0, 3), data.substring(3, 6),
                                data.substring(6, 9)};
                    }
                }
                case "CONNECTFOUR", "CHECKERS", "REVERSI", "GOMOKU" -> rows = data.split(",");
                case "UNO" -> info = fields.length > 6 ? "Hands: " + decodePairs(fields[6]) : "";
                case "RPS" -> info = "Score: " + decodePairs(data);
                case "DOTSANDBOXES" -> info = fields.length > 5
                        ? "Boxes: " + decodePairs(fields[5]) : "";
                default -> info = "";
            }
            repaint();
        }

        private static String decodePairs(String encoded) {
            StringBuilder sb = new StringBuilder();
            for (String pair : encoded.split(",")) {
                String[] kv = pair.split(":", 2);
                if (kv.length == 2) {
                    if (sb.length() > 0) {
                        sb.append("    ");
                    }
                    sb.append(Protocol.decode(kv[0])).append(": ").append(kv[1]);
                }
            }
            return sb.toString();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(BG_DARK);
            g2.fillRect(0, 0, getWidth(), getHeight());
            if (rows.length > 0) {
                paintGrid(g2);
            } else if (!info.isEmpty()) {
                g2.setColor(TEXT_PRIMARY);
                g2.setFont(FONT_BIG);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(info, (getWidth() - fm.stringWidth(info)) / 2, getHeight() / 2);
            }
            g2.dispose();
        }

        private void paintGrid(Graphics2D g2) {
            int size = rows.length;
            int cols = rows[0].length();
            int cell = Math.max(12, Math.min((getWidth() - 60) / cols, (getHeight() - 60) / size));
            int x0 = (getWidth() - cell * cols) / 2;
            int y0 = (getHeight() - cell * size) / 2;
            for (int r = 0; r < size; r++) {
                for (int c = 0; c < Math.min(cols, rows[r].length()); c++) {
                    char ch = rows[r].charAt(c);
                    int x = x0 + c * cell;
                    int y = y0 + r * cell;
                    g2.setColor(cellColor(r, c));
                    g2.fillRect(x + 1, y + 1, cell - 2, cell - 2);
                    if (ch != '.' && ch != ' ') {
                        drawPiece(g2, ch, x, y, cell);
                    }
                }
            }
        }

        private Color cellColor(int r, int c) {
            return switch (gameType) {
                case "CHECKERS" -> (r + c) % 2 == 0
                        ? new Color(0xD4, 0xA0, 0x60) : new Color(0x5C, 0x3A, 0x21);
                case "GOMOKU" -> new Color(0xC8, 0x9B, 0x5C);
                case "CONNECTFOUR" -> new Color(0x1A, 0x23, 0x7E);
                case "TICTACTOE" -> BG_CARD;
                default -> new Color(0x1B, 0x5E, 0x20);
            };
        }

        private void drawPiece(Graphics2D g2, char ch, int x, int y, int cell) {
            if (gameType.equals("TICTACTOE")) {
                g2.setColor(ch == 'X' ? ACCENT : new Color(0xEF, 0x53, 0x50));
                g2.setFont(new Font("SansSerif", Font.BOLD, cell - 8));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(String.valueOf(ch),
                        x + (cell - fm.stringWidth(String.valueOf(ch))) / 2,
                        y + (cell + fm.getAscent() - fm.getDescent()) / 2);
                return;
            }
            Color color = switch (ch) {
                case 'R' -> new Color(0xEF, 0x53, 0x50);
                case 'Y' -> new Color(0xFF, 0xD7, 0x00);
                case 'b', 'B' -> new Color(0x21, 0x21, 0x21);
                default -> new Color(0xFA, 0xFA, 0xFA);
            };
            g2.setColor(color);
            g2.fillOval(x + 3, y + 3, cell - 6, cell - 6);
            if (ch == 'B' || ch == 'W') {
                g2.setColor(Color.YELLOW);
                g2.setFont(new Font("SansSerif", Font.BOLD, cell / 3));
                g2.drawString("K", x + cell / 2 - cell / 8, y + cell / 2 + cell / 8);
            }
        }
    }

    // --- Helpers ---

    private static JPanel darkPanel(java.awt.LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setBackground(BG_DARK);
        return panel;
    }

    private static JLabel big(String text, Font font, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        return label;
    }

    public static void main(String[] args) {
        String host;
        int port;
        if (args.length > 0) {
            host = args[0];
            port = args.length > 1 ? Integer.parseInt(args[1]) : 8888;
        } else {
            List<LanDiscovery.Server> servers = discover();
            host = servers.get(0).host();
            port = servers.get(0).port();
        }
        SwingUtilities.invokeLater(() -> new SpectatorClient(host, port).setVisible(true));
    }

    private static List<LanDiscovery.Server> discover() {
        try {
            List<LanDiscovery.Server> servers =
                    LanDiscovery.discover(LanDiscovery.DEFAULT_PORT, 750);
            if (!servers.isEmpty()) {
                return servers;
            }
        } catch (IOException e) {
            System.err.println("LAN discovery failed: " + e);
        }
        return List.of(new LanDiscovery.Server("localhost", 8888));
    }
}
