package com.boardgame.client;

import com.boardgame.network.LanDiscovery;
import com.boardgame.protocol.Protocol;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class HubClient extends JFrame {
    // Theme colors
    private static final Color BG_DARK = new Color(0x1E, 0x1F, 0x29);
    private static final Color BG_CARD = new Color(0x2A, 0x2B, 0x38);
    private static final Color BG_INPUT = new Color(0x33, 0x34, 0x44);
    private static final Color ACCENT = new Color(0x6C, 0x63, 0xFF);
    private static final Color ACCENT_HOVER = new Color(0x8B, 0x83, 0xFF);
    private static final Color TEXT_PRIMARY = new Color(0xE8, 0xE8, 0xF0);
    private static final Color TEXT_SECONDARY = new Color(0xA0, 0xA0, 0xB0);
    private static final Color SUCCESS = new Color(0x4C, 0xAF, 0x50);
    private static final Color ERROR_COLOR = new Color(0xEF, 0x53, 0x50);
    private static final Font FONT_TITLE = new Font("SansSerif", Font.BOLD, 32);
    private static final Font FONT_HEADER = new Font("SansSerif", Font.BOLD, 20);
    private static final Font FONT_BODY = new Font("SansSerif", Font.PLAIN, 14);
    private static final Font FONT_BUTTON = new Font("SansSerif", Font.BOLD, 14);

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel mainPanel = new JPanel(cardLayout);
    private ServerConnection connection;
    private String username;
    private String role;
    private String currentRoomId;

    // Login screen components
    private JTextField loginUser;
    private JPasswordField loginPass;
    private JLabel loginStatus;

    // Character screen
    private String selectedSymbol = "\u2605";
    private String selectedColor = "6C63FF";
    private JLabel charPreview;

    // Lobby screen
    private JPanel roomsPanel;
    private JPanel playersPanel;
    private JLabel lobbyStatus;

    // Game screen
    private JPanel gamePanel;
    private JLabel gameStatus;

    private HubClient(String host, int port) {
        super("Board Game Hub");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(960, 700));
        setLocationByPlatform(true);
        getContentPane().setBackground(BG_DARK);

        mainPanel.setBackground(BG_DARK);
        mainPanel.add(buildLoginScreen(), "login");
        mainPanel.add(buildCharacterScreen(), "character");
        mainPanel.add(buildLobbyScreen(), "lobby");
        mainPanel.add(buildGameScreen(), "game");
        add(mainPanel);

        connectToServer(host, port);
    }

    // --- UI Builder Methods ---

    private JPanel buildLoginScreen() {
        JPanel panel = darkPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;

        JLabel title = styledLabel("\u265B Board Game Hub", FONT_TITLE, ACCENT);
        gbc.gridy = 0;
        panel.add(title, gbc);

        JLabel subtitle = styledLabel("Login or Register to play", FONT_BODY, TEXT_SECONDARY);
        gbc.gridy = 1;
        panel.add(subtitle, gbc);

        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.gridy = 2;
        gbc.gridx = 0;
        panel.add(styledLabel("Username:", FONT_BODY, TEXT_PRIMARY), gbc);
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        loginUser = styledTextField(18);
        panel.add(loginUser, gbc);

        gbc.gridy = 3;
        gbc.gridx = 0;
        gbc.anchor = GridBagConstraints.EAST;
        panel.add(styledLabel("Password:", FONT_BODY, TEXT_PRIMARY), gbc);
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        loginPass = styledPasswordField(18);
        panel.add(loginPass, gbc);

        gbc.gridy = 4;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        JPanel buttons = darkPanel(new FlowLayout(FlowLayout.CENTER, 16, 0));
        JButton loginBtn = accentButton("Login");
        JButton regBtn = accentButton("Register");
        loginBtn.addActionListener(e -> doLogin());
        regBtn.addActionListener(e -> doRegister());
        buttons.add(loginBtn);
        buttons.add(regBtn);
        panel.add(buttons, gbc);

        loginStatus = styledLabel(" ", FONT_BODY, ERROR_COLOR);
        gbc.gridy = 5;
        panel.add(loginStatus, gbc);

        return panel;
    }

    private JPanel buildCharacterScreen() {
        JPanel panel = darkPanel(new BorderLayout(16, 16));
        panel.setBorder(BorderFactory.createEmptyBorder(32, 32, 32, 32));

        JLabel title = styledLabel("Choose Your Avatar", FONT_HEADER, TEXT_PRIMARY);
        title.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(title, BorderLayout.NORTH);

        JPanel center = darkPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;

        // Symbol grid
        String[] symbols = {"\u265E", "\u265B", "\u2602", "\u2605", "\u2660", "\u2600",
                "\u263E", "\u2663", "\u2666", "\u2665", "\u273F", "\u2744", "\u2618", "\u2693"};
        JPanel symbolGrid = darkPanel(new GridLayout(2, 7, 8, 8));
        for (String sym : symbols) {
            JButton btn = new JButton(sym) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(sym.equals(selectedSymbol) ? ACCENT : BG_INPUT);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                    g2.setColor(TEXT_PRIMARY);
                    g2.setFont(new Font("SansSerif", Font.PLAIN, 28));
                    FontMetrics fm = g2.getFontMetrics();
                    int x = (getWidth() - fm.stringWidth(sym)) / 2;
                    int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                    g2.drawString(sym, x, y);
                    g2.dispose();
                }
            };
            btn.setPreferredSize(new Dimension(50, 50));
            btn.setBorderPainted(false);
            btn.setContentAreaFilled(false);
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btn.addActionListener(e -> {
                selectedSymbol = sym;
                symbolGrid.repaint();
                updateCharPreview();
            });
            symbolGrid.add(btn);
        }
        center.add(symbolGrid, gbc);

        // Color swatches
        gbc.gridy = 1;
        String[] colors = {"6C63FF", "FF6B6B", "4FC3F7", "66BB6A", "FFA726",
                "AB47BC", "EC407A", "26C6DA", "FFD700", "8D6E63", "78909C", "D4E157"};
        JPanel colorGrid = darkPanel(new GridLayout(2, 6, 8, 8));
        for (String hex : colors) {
            JButton btn = new JButton() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(Color.decode("#" + hex));
                    g2.fillRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 10, 10);
                    if (hex.equals(selectedColor)) {
                        g2.setColor(Color.WHITE);
                        g2.setStroke(new BasicStroke(3));
                        g2.drawRoundRect(2, 2, getWidth() - 5, getHeight() - 5, 10, 10);
                    }
                    g2.dispose();
                }
            };
            btn.setPreferredSize(new Dimension(40, 40));
            btn.setBorderPainted(false);
            btn.setContentAreaFilled(false);
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btn.addActionListener(e -> {
                selectedColor = hex;
                colorGrid.repaint();
                updateCharPreview();
            });
            colorGrid.add(btn);
        }
        center.add(colorGrid, gbc);

        // Preview
        gbc.gridy = 2;
        charPreview = new JLabel(selectedSymbol) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_CARD);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.setColor(Color.decode("#" + selectedColor));
                g2.setFont(new Font("SansSerif", Font.PLAIN, 64));
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(selectedSymbol)) / 2;
                int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(selectedSymbol, x, y);
                g2.dispose();
            }
        };
        charPreview.setPreferredSize(new Dimension(120, 120));
        center.add(charPreview, gbc);

        panel.add(center, BorderLayout.CENTER);

        JButton saveBtn = accentButton("Save & Enter Lobby");
        saveBtn.addActionListener(e -> doSaveCharacter());
        JPanel bottom = darkPanel(new FlowLayout(FlowLayout.CENTER));
        bottom.add(saveBtn);
        panel.add(bottom, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildLobbyScreen() {
        JPanel panel = darkPanel(new BorderLayout(16, 16));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JLabel title = styledLabel("\u265B Board Game Hub — Lobby", FONT_HEADER, TEXT_PRIMARY);
        lobbyStatus = styledLabel("Welcome!", FONT_BODY, TEXT_SECONDARY);
        JPanel header = darkPanel(new BorderLayout());
        header.add(title, BorderLayout.WEST);
        header.add(lobbyStatus, BorderLayout.EAST);
        panel.add(header, BorderLayout.NORTH);

        // Rooms list
        roomsPanel = darkPanel(new BoxLayout(null, BoxLayout.Y_AXIS));
        roomsPanel.setLayout(new BoxLayout(roomsPanel, BoxLayout.Y_AXIS));
        JScrollPane roomScroll = new JScrollPane(roomsPanel);
        roomScroll.setBackground(BG_DARK);
        roomScroll.getViewport().setBackground(BG_DARK);
        roomScroll.setBorder(BorderFactory.createEmptyBorder());
        panel.add(roomScroll, BorderLayout.CENTER);

        // Right panel: players + create
        JPanel right = darkPanel(new BorderLayout(8, 8));
        right.setPreferredSize(new Dimension(220, 0));

        playersPanel = darkPanel(new BoxLayout(null, BoxLayout.Y_AXIS));
        playersPanel.setLayout(new BoxLayout(playersPanel, BoxLayout.Y_AXIS));
        JScrollPane playerScroll = new JScrollPane(playersPanel);
        playerScroll.setBackground(BG_DARK);
        playerScroll.getViewport().setBackground(BG_DARK);
        playerScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(BG_INPUT), "Online Players",
                0, 0, FONT_BODY, TEXT_SECONDARY));
        right.add(playerScroll, BorderLayout.CENTER);

        // Create room controls
        JPanel createPanel = darkPanel(new GridBagLayout());
        createPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(BG_INPUT), "Create Room",
                0, 0, FONT_BODY, TEXT_SECONDARY));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 4, 4, 4);
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.gridx = 0;
        gc.gridy = 0;
        JComboBox<String> gameTypeBox = new JComboBox<>(new String[]{
                "UNO", "TICTACTOE", "CONNECTFOUR", "CHECKERS", "REVERSI", "DOTSANDBOXES"});
        gameTypeBox.setBackground(BG_INPUT);
        gameTypeBox.setForeground(TEXT_PRIMARY);
        createPanel.add(gameTypeBox, gc);
        gc.gridy = 1;
        JTextField roomNameField = styledTextField(12);
        createPanel.add(roomNameField, gc);
        gc.gridy = 2;
        JButton createBtn = accentButton("Create");
        createBtn.addActionListener(e -> {
            String name = roomNameField.getText().trim();
            if (!name.isEmpty()) {
                sendCommand("CREATE|" + gameTypeBox.getSelectedItem() + "|" + Protocol.encode(name));
                roomNameField.setText("");
            }
        });
        createPanel.add(createBtn, gc);
        right.add(createPanel, BorderLayout.SOUTH);

        panel.add(right, BorderLayout.EAST);
        return panel;
    }

    private JPanel buildGameScreen() {
        JPanel panel = darkPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        gameStatus = styledLabel("In game...", FONT_HEADER, TEXT_PRIMARY);
        JButton leaveBtn = accentButton("Leave Room");
        leaveBtn.addActionListener(e -> {
            sendCommand("LEAVEROOM");
            currentRoomId = null;
            cardLayout.show(mainPanel, "lobby");
            sendCommand("LIST");
        });
        JPanel top = darkPanel(new BorderLayout());
        top.add(gameStatus, BorderLayout.CENTER);
        top.add(leaveBtn, BorderLayout.EAST);
        panel.add(top, BorderLayout.NORTH);

        gamePanel = darkPanel(new BorderLayout());
        panel.add(gamePanel, BorderLayout.CENTER);

        return panel;
    }

    // --- Network ---

    private void connectToServer(String host, int port) {
        try {
            connection = new ServerConnection(host, port);
            connection.listen(line -> SwingUtilities.invokeLater(() -> receive(line)));
        } catch (IOException e) {
            loginStatus.setText("Connection failed: " + e.getMessage());
        }
    }

    private void sendCommand(String cmd) {
        if (connection != null) {
            connection.send(cmd);
        }
    }

    private void receive(String line) {
        String[] parts = line.split("\\|", -1);
        switch (parts[0]) {
            case "WELCOME" -> {
                username = Protocol.decode(parts[1]);
                role = Protocol.decode(parts[2]);
                selectedSymbol = Protocol.decode(parts[3]);
                selectedColor = parts[4];
                cardLayout.show(mainPanel, "lobby");
                sendCommand("LIST");
            }
            case "OK" -> {
                String msg = parts.length > 1 ? Protocol.decode(parts[1]) : "OK";
                if (msg.startsWith("room-")) {
                    // Room created
                    sendCommand("LIST");
                } else if (msg.startsWith("Joined")) {
                    cardLayout.show(mainPanel, "game");
                } else if (msg.equals("Character updated")) {
                    cardLayout.show(mainPanel, "lobby");
                    sendCommand("LIST");
                } else {
                    lobbyStatus.setText(msg);
                    sendCommand("LIST");
                }
            }
            case "ERROR" -> {
                String msg = parts.length > 1 ? Protocol.decode(parts[1]) : "Error";
                loginStatus.setText(msg);
                lobbyStatus.setText(msg);
                gameStatus.setText(msg);
            }
            case "LOBBY" -> renderLobby(parts);
            case "GAMESTATE" -> renderGame(parts);
        }
    }

    private void renderLobby(String[] parts) {
        String roomData = parts.length > 1 ? parts[1] : "";
        String onlineData = parts.length > 2 ? parts[2] : "";

        roomsPanel.removeAll();
        if (!roomData.isEmpty()) {
            for (String entry : roomData.split(",")) {
                String[] fields = entry.split(":", 7);
                if (fields.length >= 7) {
                    String roomId = Protocol.decode(fields[0]);
                    String roomName = Protocol.decode(fields[1]);
                    String gameType = fields[2];
                    String playerCount = fields[3];
                    String maxP = fields[4];
                    boolean roomStarted = fields[5].equals("1");
                    boolean roomFinished = fields[6].equals("1");

                    JPanel card = new JPanel(new BorderLayout(8, 4)) {
                        @Override
                        protected void paintComponent(Graphics g) {
                            Graphics2D g2 = (Graphics2D) g.create();
                            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                            g2.setColor(BG_CARD);
                            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                            g2.dispose();
                        }
                    };
                    card.setOpaque(false);
                    card.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
                    card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));

                    String status = roomFinished ? "Finished" : roomStarted ? "In Progress" : "Waiting";
                    JLabel nameLabel = styledLabel(roomName + " [" + gameType + "]", FONT_BUTTON, TEXT_PRIMARY);
                    JLabel infoLabel = styledLabel(playerCount + "/" + maxP + " players — " + status,
                            FONT_BODY, TEXT_SECONDARY);
                    JPanel textPanel = darkPanel(new GridLayout(2, 1));
                    textPanel.setOpaque(false);
                    textPanel.add(nameLabel);
                    textPanel.add(infoLabel);
                    card.add(textPanel, BorderLayout.CENTER);

                    JButton joinBtn = accentButton("Join");
                    joinBtn.addActionListener(e -> {
                        currentRoomId = roomId;
                        sendCommand("JOINROOM|" + roomId);
                    });
                    card.add(joinBtn, BorderLayout.EAST);
                    roomsPanel.add(card);
                    roomsPanel.add(Box.createVerticalStrut(8));
                }
            }
        }
        if (roomsPanel.getComponentCount() == 0) {
            roomsPanel.add(styledLabel("  No rooms yet. Create one!", FONT_BODY, TEXT_SECONDARY));
        }
        roomsPanel.revalidate();
        roomsPanel.repaint();

        playersPanel.removeAll();
        if (!onlineData.isEmpty()) {
            for (String entry : onlineData.split(",")) {
                String[] fields = entry.split(":", 3);
                if (fields.length >= 3) {
                    String name = Protocol.decode(fields[0]);
                    String sym = Protocol.decode(fields[1]);
                    String col = fields[2];
                    JLabel lbl = styledLabel(sym + " " + name, FONT_BODY,
                            Color.decode("#" + col));
                    playersPanel.add(lbl);
                }
            }
        }
        playersPanel.revalidate();
        playersPanel.repaint();
    }

    private void renderGame(String[] parts) {
        // parts[0]="GAMESTATE", rest is game-specific snapshot
        String snapshotData = String.join("|", java.util.Arrays.copyOfRange(parts, 1, parts.length));
        String[] fields = snapshotData.split("\\|", -1);
        if (fields.length < 3) return;

        boolean started = Boolean.parseBoolean(fields[0]);
        boolean finished = Boolean.parseBoolean(fields[1]);
        String currentPlayer = Protocol.decode(fields[2]);
        String statusText = fields.length > 3 ? Protocol.decode(fields[fields.length - 1]) : "";
        gameStatus.setText(statusText);

        gamePanel.removeAll();
        // Simple text-based game rendering with styled components
        JPanel board = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_CARD);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.dispose();
            }
        };
        board.setOpaque(false);
        board.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        boolean myTurn = started && !finished && username.equals(currentPlayer);

        // Determine game type from snapshot structure and render
        if (fields.length >= 7 && fields[3].length() == 9 && fields[3].matches("[XO.]{9}")) {
            renderTicTacToe(board, fields, myTurn);
        } else if (fields.length >= 6 && fields[3].contains(",") && fields[3].split(",").length == 6) {
            renderConnectFour(board, fields, myTurn);
        } else if (fields.length >= 6 && fields[3].contains(",") && fields[3].split(",").length == 8
                && fields[3].split(",")[0].length() == 8 && fields[3].contains("b")) {
            renderCheckers(board, fields, myTurn);
        } else if (fields.length >= 6 && fields[3].contains(",") && fields[3].split(",").length == 8
                && fields[3].split(",")[0].length() == 8) {
            renderReversi(board, fields, myTurn);
        } else if (fields.length >= 9) {
            // UNO game
            renderUno(board, fields, myTurn);
        } else {
            // Generic/DotsAndBoxes - show raw data
            renderGeneric(board, fields, myTurn);
        }

        gamePanel.add(board, BorderLayout.CENTER);
        gamePanel.revalidate();
        gamePanel.repaint();
    }

    private void renderTicTacToe(JPanel board, String[] fields, boolean myTurn) {
        String boardStr = fields[3];
        String myMark = fields[5];
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        for (int i = 0; i < 9; i++) {
            int cell = i;
            char ch = boardStr.charAt(i);
            JButton btn = new JButton(ch == '.' ? "" : String.valueOf(ch)) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(BG_INPUT);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                    g2.setFont(new Font("SansSerif", Font.BOLD, 36));
                    g2.setColor(ch == 'X' ? ACCENT : ch == 'O' ? ERROR_COLOR : TEXT_SECONDARY);
                    FontMetrics fm = g2.getFontMetrics();
                    String txt = ch == '.' ? "" : String.valueOf(ch);
                    int x = (getWidth() - fm.stringWidth(txt)) / 2;
                    int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                    g2.drawString(txt, x, y);
                    g2.dispose();
                }
            };
            btn.setPreferredSize(new Dimension(80, 80));
            btn.setBorderPainted(false);
            btn.setContentAreaFilled(false);
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btn.setEnabled(myTurn && ch == '.');
            btn.addActionListener(e -> sendCommand("MOVE|" + cell));
            gbc.gridx = i % 3;
            gbc.gridy = i / 3;
            board.add(btn, gbc);
        }
    }

    private void renderConnectFour(JPanel board, String[] fields, boolean myTurn) {
        String[] rows = fields[3].split(",");
        String myPiece = fields[4];
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);

        // Column buttons
        gbc.gridy = 0;
        for (int c = 0; c < 7; c++) {
            int col = c;
            JButton btn = accentButton("\u25BC");
            btn.setPreferredSize(new Dimension(50, 30));
            btn.setEnabled(myTurn);
            btn.addActionListener(e -> sendCommand("MOVE|" + col));
            gbc.gridx = c;
            board.add(btn, gbc);
        }

        for (int r = 0; r < 6; r++) {
            for (int c = 0; c < 7; c++) {
                char ch = rows[r].charAt(c);
                JPanel cell = new JPanel() {
                    @Override
                    protected void paintComponent(Graphics g) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setColor(new Color(0x1A, 0x23, 0x7E));
                        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                        Color pieceColor = ch == 'R' ? new Color(0xEF, 0x53, 0x50)
                                : ch == 'Y' ? new Color(0xFF, 0xD7, 0x00) : BG_DARK;
                        g2.setColor(pieceColor);
                        g2.fillOval(4, 4, getWidth() - 8, getHeight() - 8);
                        g2.dispose();
                    }
                };
                cell.setPreferredSize(new Dimension(50, 50));
                gbc.gridx = c;
                gbc.gridy = r + 1;
                board.add(cell, gbc);
            }
        }
    }

    private void renderCheckers(JPanel board, String[] fields, boolean myTurn) {
        renderGridGame(board, fields, myTurn, 8, "CHECKERS");
    }

    private void renderReversi(JPanel board, String[] fields, boolean myTurn) {
        renderGridGame(board, fields, myTurn, 8, "REVERSI");
    }

    private void renderGridGame(JPanel board, String[] fields, boolean myTurn, int size, String type) {
        String[] rows = fields[3].split(",");
        String myPiece = fields[4];
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(1, 1, 1, 1);
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                char ch = rows[r].charAt(c);
                int row = r;
                int col = c;
                JPanel cell = new JPanel() {
                    @Override
                    protected void paintComponent(Graphics g) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        if (type.equals("CHECKERS")) {
                            g2.setColor((row + col) % 2 == 0 ? new Color(0xD4, 0xA0, 0x60) : new Color(0x5C, 0x3A, 0x21));
                        } else {
                            g2.setColor(new Color(0x1B, 0x5E, 0x20));
                        }
                        g2.fillRect(0, 0, getWidth(), getHeight());
                        if (ch != '.') {
                            Color pieceColor = (ch == 'b' || ch == 'B') ? new Color(0x21, 0x21, 0x21)
                                    : (ch == 'w' || ch == 'W') ? new Color(0xFA, 0xFA, 0xFA)
                                    : ch == 'B' ? new Color(0x21, 0x21, 0x21) : new Color(0xFA, 0xFA, 0xFA);
                            g2.setColor(pieceColor);
                            g2.fillOval(4, 4, getWidth() - 8, getHeight() - 8);
                            if (ch == 'B' || ch == 'W') {
                                g2.setColor(Color.YELLOW);
                                g2.setFont(new Font("SansSerif", Font.BOLD, 12));
                                g2.drawString("K", getWidth() / 2 - 4, getHeight() / 2 + 4);
                            }
                        }
                        g2.dispose();
                    }
                };
                cell.setPreferredSize(new Dimension(48, 48));
                cell.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                cell.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        if (myTurn) {
                            handleGridClick(row, col, type);
                        }
                    }
                });
                gbc.gridx = c;
                gbc.gridy = r;
                board.add(cell, gbc);
            }
        }
    }

    private int selectedFromRow = -1, selectedFromCol = -1;

    private void handleGridClick(int row, int col, String type) {
        if (type.equals("REVERSI")) {
            sendCommand("MOVE|" + row + "," + col);
        } else if (type.equals("CHECKERS")) {
            if (selectedFromRow < 0) {
                selectedFromRow = row;
                selectedFromCol = col;
            } else {
                sendCommand("MOVE|" + selectedFromRow + "," + selectedFromCol + "," + row + "," + col);
                selectedFromRow = -1;
                selectedFromCol = -1;
            }
        }
    }

    private void renderUno(JPanel board, String[] fields, boolean myTurn) {
        // fields: started|finished|currentPlayer|topCard|activeColor|hand|players|drawPileSize|message
        String topCardToken = fields[3];
        String activeColorStr = fields[4];
        String handStr = fields[5];
        String playersStr = fields[6];

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 5;

        // Players
        JLabel playersLabel = styledLabel(formatUnoPlayers(playersStr), FONT_BODY, TEXT_SECONDARY);
        board.add(playersLabel, gbc);

        // Top card
        gbc.gridy = 1;
        if (!topCardToken.isEmpty()) {
            com.boardgame.model.Card topCard = com.boardgame.model.Card.fromToken(topCardToken);
            JPanel cardPanel = createUnoCardPanel(topCard, activeColorStr, true);
            board.add(cardPanel, gbc);
        }

        // Draw button
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        JButton drawBtn = accentButton("Draw Card");
        drawBtn.setEnabled(myTurn);
        drawBtn.addActionListener(e -> sendCommand("MOVE|DRAW"));
        board.add(drawBtn, gbc);

        // Wild color selector
        gbc.gridx = 1;
        JComboBox<String> colorBox = new JComboBox<>(new String[]{"RED", "YELLOW", "GREEN", "BLUE"});
        colorBox.setBackground(BG_INPUT);
        colorBox.setForeground(TEXT_PRIMARY);
        board.add(colorBox, gbc);

        // Hand
        gbc.gridy = 3;
        gbc.gridx = 0;
        gbc.gridwidth = 5;
        JPanel handPanel = darkPanel(new FlowLayout(FlowLayout.CENTER, 4, 4));
        if (!handStr.isEmpty()) {
            String[] cardTokens = handStr.split(",");
            for (int i = 0; i < cardTokens.length; i++) {
                int idx = i;
                com.boardgame.model.Card card = com.boardgame.model.Card.fromToken(cardTokens[i]);
                JPanel cardP = createUnoCardPanel(card, null, false);
                cardP.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                cardP.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        if (myTurn) {
                            String color = card.color() == com.boardgame.model.Card.Color.WILD
                                    ? (String) colorBox.getSelectedItem() : "";
                            sendCommand("MOVE|" + idx + "|" + color);
                        }
                    }
                });
                handPanel.add(cardP);
            }
        }
        JScrollPane scroll = new JScrollPane(handPanel);
        scroll.setBackground(BG_DARK);
        scroll.getViewport().setBackground(BG_DARK);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setPreferredSize(new Dimension(700, 150));
        board.add(scroll, gbc);
    }

    private JPanel createUnoCardPanel(com.boardgame.model.Card card, String activeColor, boolean large) {
        int w = large ? 100 : 70;
        int h = large ? 150 : 105;
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color cardColor = unoAwtColor(card.color());
                if (card.color() == com.boardgame.model.Card.Color.WILD && activeColor != null && !activeColor.isEmpty()) {
                    cardColor = unoAwtColor(com.boardgame.model.Card.Color.valueOf(activeColor));
                }
                // Shadow
                g2.setColor(new Color(0, 0, 0, 60));
                g2.fillRoundRect(3, 3, getWidth() - 3, getHeight() - 3, 16, 16);
                // Card body
                g2.setColor(cardColor);
                g2.fillRoundRect(0, 0, getWidth() - 4, getHeight() - 4, 16, 16);
                // White ellipse
                g2.setColor(new Color(255, 255, 255, 100));
                g2.fillOval(getWidth() / 6, getHeight() / 4, getWidth() * 2 / 3, getHeight() / 2);
                // Label
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.BOLD, large ? 22 : 16));
                String label = card.value().label();
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - 4 - fm.stringWidth(label)) / 2;
                int y = (getHeight() - 4 + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(label, x, y);
                // Corner label
                g2.setFont(new Font("SansSerif", Font.BOLD, large ? 12 : 9));
                g2.drawString(label, 6, 16);
                g2.dispose();
            }
        };
        panel.setPreferredSize(new Dimension(w, h));
        panel.setOpaque(false);
        return panel;
    }

    private static Color unoAwtColor(com.boardgame.model.Card.Color color) {
        return switch (color) {
            case RED -> new Color(0xD3, 0x2F, 0x2F);
            case YELLOW -> new Color(0xF9, 0xA8, 0x25);
            case GREEN -> new Color(0x2E, 0x7D, 0x32);
            case BLUE -> new Color(0x15, 0x65, 0xC0);
            case WILD -> new Color(0x37, 0x37, 0x37);
        };
    }

    private String formatUnoPlayers(String encoded) {
        if (encoded.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String p : encoded.split(",")) {
            String[] kv = p.split(":", 2);
            if (kv.length == 2) {
                if (sb.length() > 0) sb.append("   ");
                sb.append(Protocol.decode(kv[0])).append(" (").append(kv[1]).append(")");
            }
        }
        return sb.toString();
    }

    private void renderGeneric(JPanel board, String[] fields, boolean myTurn) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.insets = new Insets(4, 4, 4, 4);
        String msg = fields.length > 1 ? Protocol.decode(fields[fields.length - 1]) : "";
        board.add(styledLabel(msg, FONT_BODY, TEXT_PRIMARY), gbc);

        // Show raw state for dots and boxes etc.
        gbc.gridy = 1;
        JLabel rawLabel = styledLabel("Use protocol commands for this game", FONT_BODY, TEXT_SECONDARY);
        board.add(rawLabel, gbc);

        // Move input
        gbc.gridy = 2;
        JTextField moveField = styledTextField(20);
        board.add(moveField, gbc);
        gbc.gridy = 3;
        JButton moveBtn = accentButton("Send Move");
        moveBtn.setEnabled(myTurn);
        moveBtn.addActionListener(e -> {
            String move = moveField.getText().trim();
            if (!move.isEmpty()) {
                sendCommand("MOVE|" + move);
                moveField.setText("");
            }
        });
        board.add(moveBtn, gbc);
    }

    // --- Actions ---

    private void doLogin() {
        String user = loginUser.getText().trim();
        String pass = new String(loginPass.getPassword());
        if (user.isEmpty() || pass.isEmpty()) {
            loginStatus.setText("Enter username and password");
            return;
        }
        sendCommand("LOGIN|" + Protocol.encode(user) + "|" + Protocol.encode(pass));
    }

    private void doRegister() {
        String user = loginUser.getText().trim();
        String pass = new String(loginPass.getPassword());
        if (user.isEmpty() || pass.isEmpty()) {
            loginStatus.setText("Enter username and password");
            return;
        }
        sendCommand("REGISTER|" + Protocol.encode(user) + "|" + Protocol.encode(pass));
        loginStatus.setText("Registered! Now login.");
    }

    private void doSaveCharacter() {
        sendCommand("CHARACTER|" + selectedSymbol + "|" + selectedColor);
    }

    private void updateCharPreview() {
        if (charPreview != null) {
            charPreview.repaint();
        }
    }

    // --- Styled UI Helpers ---

    private static JPanel darkPanel(java.awt.LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setBackground(BG_DARK);
        return panel;
    }

    private static JLabel styledLabel(String text, Font font, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        return label;
    }

    private static JTextField styledTextField(int cols) {
        JTextField field = new JTextField(cols);
        field.setBackground(BG_INPUT);
        field.setForeground(TEXT_PRIMARY);
        field.setCaretColor(TEXT_PRIMARY);
        field.setFont(FONT_BODY);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BG_INPUT.brighter(), 1),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        return field;
    }

    private static JPasswordField styledPasswordField(int cols) {
        JPasswordField field = new JPasswordField(cols);
        field.setBackground(BG_INPUT);
        field.setForeground(TEXT_PRIMARY);
        field.setCaretColor(TEXT_PRIMARY);
        field.setFont(FONT_BODY);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BG_INPUT.brighter(), 1),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        return field;
    }

    private static JButton accentButton(String text) {
        JButton button = new JButton(text) {
            private boolean hover = false;

            {
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                    @Override
                    public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = isEnabled() ? (hover ? ACCENT_HOVER : ACCENT) : BG_INPUT;
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(isEnabled() ? Color.WHITE : TEXT_SECONDARY);
                g2.setFont(FONT_BUTTON);
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(getText())) / 2;
                int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), x, y);
                g2.dispose();
            }
        };
        button.setPreferredSize(new Dimension(
                button.getFontMetrics(FONT_BUTTON).stringWidth(text) + 40, 36));
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    // --- Connection class ---

    private static final class ServerConnection implements Closeable {
        private final Socket socket;
        private final PrintWriter output;
        private final BufferedReader input;

        ServerConnection(String host, int port) throws IOException {
            socket = new Socket(host, port);
            output = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
            input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        }

        void send(String message) {
            synchronized (this) {
                output.println(message);
            }
        }

        void listen(Consumer<String> listener) {
            Thread thread = new Thread(() -> {
                try {
                    String line;
                    while ((line = input.readLine()) != null) {
                        listener.accept(line);
                    }
                } catch (IOException exception) {
                    listener.accept("ERROR|" + Protocol.encode("Disconnected"));
                }
            }, "hub-listener");
            thread.setDaemon(true);
            thread.start();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    // --- Main ---

    public static void main(String[] args) {
        List<LanDiscovery.Server> servers = args.length > 0
                ? List.of(new LanDiscovery.Server(
                        args[0], args.length > 1 ? Integer.parseInt(args[1]) : 8888))
                : discoverServers();
        SwingUtilities.invokeLater(() -> {
            LanDiscovery.Server selected = chooseServer(servers);
            if (selected == null) return;
            new HubClient(selected.host(), selected.port()).setVisible(true);
        });
    }

    private static List<LanDiscovery.Server> discoverServers() {
        try {
            List<LanDiscovery.Server> servers = LanDiscovery.discover(LanDiscovery.DEFAULT_PORT, 750);
            if (!servers.isEmpty()) return servers;
        } catch (IOException exception) {
            System.err.println("LAN discovery failed: " + exception);
        }
        return List.of(new LanDiscovery.Server("localhost", 8888));
    }

    private static LanDiscovery.Server chooseServer(List<LanDiscovery.Server> servers) {
        if (servers.size() == 1) return servers.get(0);
        return (LanDiscovery.Server) JOptionPane.showInputDialog(null,
                "Choose a server:", "Board Game Hub", JOptionPane.QUESTION_MESSAGE,
                null, servers.toArray(), servers.get(0));
    }
}
