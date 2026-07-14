package com.boardgame.client;

import com.boardgame.network.LanDiscovery;
import com.boardgame.protocol.Protocol;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JLayeredPane;
import javax.swing.JTextArea;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JCheckBox;
import javax.swing.SpinnerNumberModel;
import javax.swing.JSpinner;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.SourceDataLine;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private static final Font FONT_SMALL = new Font("SansSerif", Font.PLAIN, 11);

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel mainPanel = new JPanel(cardLayout);
    /** Emote definitions: id -> {emoji, phrase, comma-separated tone frequencies}. */
    private static final Map<String, String[]> EMOTE_DEFS = new LinkedHashMap<>();

    static {
        EMOTE_DEFS.put("WAVE", new String[]{"\uD83D\uDC4B", "waves hello", "660,880"});
        EMOTE_DEFS.put("LAUGH", new String[]{"\uD83D\uDE02", "laughs out loud", "523,659,784"});
        EMOTE_DEFS.put("CRY", new String[]{"\uD83D\uDE2D", "bursts into tears", "440,349"});
        EMOTE_DEFS.put("ANGRY", new String[]{"\uD83D\uDE20", "is fuming!", "220,196"});
        EMOTE_DEFS.put("SHOCK", new String[]{"\uD83D\uDE31", "is shocked!", "880,1046"});
        EMOTE_DEFS.put("GG", new String[]{"\uD83E\uDD1D", "says: good game!", "523,784"});
        EMOTE_DEFS.put("TAUNT", new String[]{"\uD83D\uDE1C",
                "taunts: is that all you've got?", "784,659,784,659"});
        EMOTE_DEFS.put("BOAST", new String[]{"\uD83D\uDE0E", "boasts: too easy!", "659,784,988"});
        EMOTE_DEFS.put("HORN", new String[]{"\uD83D\uDCEF", "sounds the air horn!", "466,466,622"});
    }

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
    private String selectedTitle = "";
    private int myLevel = 1;
    private JComboBox<String> titleBox;
    private JLabel charPreview;

    // Lobby screen
    private JPanel roomsPanel;
    private JPanel playersPanel;
    private JLabel lobbyStatus;
    private JComboBox<String> gameTypeBox;

    // Game screen
    private JPanel gamePanel;
    private JLabel gameStatus;
    private JTextArea chatArea;
    private Timer waitingTimer;
    private float waitingPhase;

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

        // Title picker (character building)
        gbc.gridy = 2;
        JPanel titleRow = darkPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        titleRow.add(styledLabel("Title:", FONT_BODY, TEXT_PRIMARY));
        titleBox = styledComboBox(new String[]{"(no title)", "Novice", "Apprentice",
                "Strategist", "Tactician", "Trickster", "Card Shark", "Grandmaster",
                "Champion", "Legend"});
        titleBox.addActionListener(e -> {
            String value = (String) titleBox.getSelectedItem();
            selectedTitle = "(no title)".equals(value) ? "" : value;
            updateCharPreview();
        });
        titleRow.add(titleBox);
        center.add(titleRow, gbc);

        // Preview
        gbc.gridy = 3;
        charPreview = new JLabel(selectedSymbol) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_CARD);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.setColor(Color.decode("#" + selectedColor));
                g2.setFont(new Font("SansSerif", Font.PLAIN, 56));
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(selectedSymbol)) / 2;
                int y = (getHeight() - 16 + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(selectedSymbol, x, y);
                g2.setFont(FONT_BODY);
                g2.setColor(TEXT_SECONDARY);
                String caption = (selectedTitle.isEmpty() ? "" : selectedTitle + " \u2022 ")
                        + "Lv " + myLevel;
                FontMetrics fm2 = g2.getFontMetrics();
                g2.drawString(caption, (getWidth() - fm2.stringWidth(caption)) / 2,
                        getHeight() - 12);
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
        JButton customizeBtn = accentButton("Customize");
        customizeBtn.addActionListener(e -> cardLayout.show(mainPanel, "character"));
        JButton leaderboardBtn = accentButton("\uD83C\uDFC6 Leaderboard");
        leaderboardBtn.addActionListener(e -> sendCommand("LEADERBOARD"));
        JPanel headerRight = darkPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        headerRight.add(lobbyStatus);
        headerRight.add(leaderboardBtn);
        headerRight.add(customizeBtn);
        JPanel header = darkPanel(new BorderLayout());
        header.add(title, BorderLayout.WEST);
        header.add(headerRight, BorderLayout.EAST);
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
        gameTypeBox = styledComboBox(new String[]{
                "UNO", "TICTACTOE", "CONNECTFOUR", "CHECKERS", "REVERSI", "DOTSANDBOXES",
                "GOMOKU", "RPS", "PUTTPUTT"});
        createPanel.add(gameTypeBox, gc);
        gc.gridy = 1;
        JTextField roomNameField = styledTextField(12);
        createPanel.add(roomNameField, gc);
        gc.gridy = 2;
        JButton createBtn = accentButton("Create");
        createBtn.addActionListener(e -> {
            String name = roomNameField.getText().trim();
            if (!name.isEmpty()) {
                String type = String.valueOf(gameTypeBox.getSelectedItem());
                String options = "UNO".equals(type) ? promptUnoRules() : null;
                if ("UNO".equals(type) && options == null) {
                    return; // dialog cancelled
                }
                String cmd = "CREATE|" + type + "|" + Protocol.encode(name);
                if (options != null && !options.isEmpty()) {
                    cmd += "|" + Protocol.encode(options);
                }
                sendCommand(cmd);
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
            stopWaitingAnimation();
            if (chatArea != null) {
                chatArea.setText("");
            }
            cardLayout.show(mainPanel, "lobby");
            sendCommand("LIST");
        });
        JPanel top = darkPanel(new BorderLayout());
        top.add(gameStatus, BorderLayout.CENTER);
        top.add(leaveBtn, BorderLayout.EAST);
        panel.add(top, BorderLayout.NORTH);

        gamePanel = darkPanel(new BorderLayout());
        panel.add(gamePanel, BorderLayout.CENTER);

        panel.add(buildChatSidebar(), BorderLayout.EAST);

        return panel;
    }

    private JPanel buildChatSidebar() {
        JPanel side = darkPanel(new BorderLayout(8, 8));
        side.setPreferredSize(new Dimension(260, 0));

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setBackground(BG_CARD);
        chatArea.setForeground(TEXT_PRIMARY);
        chatArea.setFont(FONT_BODY);
        chatArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatScroll.setBackground(BG_DARK);
        chatScroll.getViewport().setBackground(BG_CARD);
        chatScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(BG_INPUT), "Room Chat",
                0, 0, FONT_BODY, TEXT_SECONDARY));
        side.add(chatScroll, BorderLayout.CENTER);

        JPanel bottom = darkPanel(new BorderLayout(4, 4));

        // Emote / taunt buttons
        JPanel emotes = darkPanel(new GridLayout(3, 3, 4, 4));
        for (Map.Entry<String, String[]> entry : EMOTE_DEFS.entrySet()) {
            String id = entry.getKey();
            JButton btn = accentButton(entry.getValue()[0]);
            btn.setToolTipText(id.charAt(0) + id.substring(1).toLowerCase());
            btn.setPreferredSize(new Dimension(48, 32));
            btn.addActionListener(e -> sendCommand("EMOTE|" + id));
            emotes.add(btn);
        }
        bottom.add(emotes, BorderLayout.NORTH);

        JTextField chatInput = styledTextField(12);
        JButton sendBtn = accentButton("Send");
        Runnable sendChat = () -> {
            String text = chatInput.getText().trim();
            if (!text.isEmpty()) {
                sendCommand("CHAT|" + Protocol.encode(text));
                chatInput.setText("");
            }
        };
        chatInput.addActionListener(e -> sendChat.run());
        sendBtn.addActionListener(e -> sendChat.run());
        JPanel inputRow = darkPanel(new BorderLayout(4, 0));
        inputRow.add(chatInput, BorderLayout.CENTER);
        inputRow.add(sendBtn, BorderLayout.EAST);
        bottom.add(inputRow, BorderLayout.SOUTH);

        side.add(bottom, BorderLayout.SOUTH);
        return side;
    }

    private void appendChat(String line) {
        if (chatArea != null) {
            chatArea.append(line + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        }
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
        try {
            handleMessage(line);
        } catch (RuntimeException exception) {
            lobbyStatus.setText("Received invalid data from server");
        }
    }

    private void handleMessage(String line) {
        String[] parts = line.split("\\|", -1);
        switch (parts[0]) {
            case "WELCOME" -> {
                username = Protocol.decode(parts[1]);
                role = Protocol.decode(parts[2]);
                selectedSymbol = Protocol.decode(parts[3]);
                selectedColor = parts[4];
                if (parts.length > 6) {
                    selectedTitle = Protocol.decode(parts[5]);
                    try {
                        myLevel = Integer.parseInt(parts[6]);
                    } catch (NumberFormatException ignored) {
                        myLevel = 1;
                    }
                    if (titleBox != null) {
                        titleBox.setSelectedItem(selectedTitle.isEmpty()
                                ? "(no title)" : selectedTitle);
                    }
                }
                updateCharPreview();
                cardLayout.show(mainPanel, "character");
                sendCommand("LIST");
            }
            case "OK" -> {
                String msg = parts.length > 1 ? Protocol.decode(parts[1]) : "OK";
                if (msg.startsWith("room-")) {
                    // Room created — join it right away
                    sendCommand("JOINROOM|" + msg);
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
            case "ROOMCLOSED" -> {
                currentRoomId = null;
                stopWaitingAnimation();
                if (chatArea != null) {
                    chatArea.setText("");
                }
                lobbyStatus.setText(parts.length > 1 ? Protocol.decode(parts[1]) : "Room closed");
                cardLayout.show(mainPanel, "lobby");
                sendCommand("LIST");
            }
            case "GAMESTATE" -> renderGame(parts);
            case "GAMES" -> {
                if (parts.length > 1 && !parts[1].isEmpty() && gameTypeBox != null) {
                    gameTypeBox.setModel(new DefaultComboBoxModel<>(parts[1].split(",")));
                }
            }
            case "CHAT" -> appendChat(Protocol.decode(parts[1]) + ": " + Protocol.decode(parts[2]));
            case "EMOTE" -> showEmote(Protocol.decode(parts[1]), parts[2]);
            case "LEADERBOARD" -> showLeaderboard(parts.length > 1 ? parts[1] : "");
        }
    }

    private void showLeaderboard(String data) {
        StringBuilder sb = new StringBuilder(String.format("%-4s %-20s %5s %5s %5s %5s%n",
                "#", "Player", "W", "L", "D", "Lv"));
        int rank = 1;
        if (!data.isEmpty()) {
            for (String entry : data.split(",")) {
                String[] f = entry.split(":", 5);
                if (f.length == 5) {
                    sb.append(String.format("%-4d %-20s %5s %5s %5s %5s%n",
                            rank++, Protocol.decode(f[0]), f[1], f[2], f[3], f[4]));
                }
            }
        }
        if (rank == 1) {
            sb.append("No games played yet. Go make history!");
        }
        JTextArea area = new JTextArea(sb.toString());
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        area.setBackground(BG_CARD);
        area.setForeground(TEXT_PRIMARY);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(440, 320));
        JOptionPane.showMessageDialog(this, scroll, "\uD83C\uDFC6 Leaderboard",
                JOptionPane.PLAIN_MESSAGE);
    }

    // --- Emotes, noises and animations ---

    private void showEmote(String user, String emoteId) {
        String[] def = EMOTE_DEFS.get(emoteId);
        if (def == null) {
            return;
        }
        appendChat(def[0] + " " + user + " " + def[1]);
        playNoise(def[2]);
        animateToast(def[0] + " " + user + " " + def[1]);
    }

    /** Plays a short sequence of sine-wave beeps; silently no-ops without audio. */
    private static void playNoise(String frequencies) {
        Thread thread = new Thread(() -> {
            try {
                AudioFormat format = new AudioFormat(22050f, 8, 1, true, false);
                try (SourceDataLine line = javax.sound.sampled.AudioSystem
                        .getSourceDataLine(format)) {
                    line.open(format);
                    line.start();
                    for (String freq : frequencies.split(",")) {
                        double f = Double.parseDouble(freq);
                        byte[] buffer = new byte[2205]; // 100 ms per tone
                        for (int i = 0; i < buffer.length; i++) {
                            double envelope = 1.0 - (double) i / buffer.length;
                            buffer[i] = (byte) (Math.sin(2 * Math.PI * f * i / 22050) * 70 * envelope);
                        }
                        line.write(buffer, 0, buffer.length);
                    }
                    line.drain();
                }
            } catch (Exception ignored) {
                // No audio device (e.g. headless) — emotes stay visual only
            }
        }, "emote-noise");
        thread.setDaemon(true);
        thread.start();
    }

    /** Slides an emote toast up from the bottom of the window while fading it out. */
    private void animateToast(String text) {
        JLayeredPane layers = getLayeredPane();
        JLabel toast = new JLabel(text, SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                float alpha = Math.max(0f, Math.min(1f, ((Number) getClientProperty("alpha")).floatValue()));
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                g2.setColor(ACCENT);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.setColor(Color.WHITE);
                g2.setFont(FONT_BUTTON);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2,
                        (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        toast.putClientProperty("alpha", 1f);
        int width = Math.max(220, toast.getFontMetrics(FONT_BUTTON).stringWidth(text) + 48);
        int startY = getHeight() - 90;
        toast.setBounds((getWidth() - width) / 2, startY, width, 44);
        toast.setOpaque(false);
        layers.add(toast, JLayeredPane.POPUP_LAYER);
        Timer timer = new Timer(30, null);
        long start = System.currentTimeMillis();
        timer.addActionListener(e -> {
            float progress = (System.currentTimeMillis() - start) / 1800f;
            if (progress >= 1f) {
                timer.stop();
                layers.remove(toast);
                layers.repaint();
                return;
            }
            toast.putClientProperty("alpha", 1f - progress);
            toast.setLocation(toast.getX(), startY - (int) (progress * 60));
            toast.repaint();
        });
        timer.start();
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
                String[] fields = entry.split(":", 5);
                if (fields.length >= 3) {
                    String name = Protocol.decode(fields[0]);
                    String sym = Protocol.decode(fields[1]);
                    String col = fields[2];
                    String text = sym + " " + name;
                    if (fields.length >= 5) {
                        String userTitle = Protocol.decode(fields[3]);
                        text += (userTitle.isEmpty() ? "" : " \u201C" + userTitle + "\u201D")
                                + "  Lv " + fields[4];
                    }
                    JLabel lbl = styledLabel(text, FONT_BODY, Color.decode("#" + col));
                    playersPanel.add(lbl);
                }
            }
        }
        playersPanel.revalidate();
        playersPanel.repaint();
    }

    private void renderGame(String[] parts) {
        // parts[0]="GAMESTATE", parts[1]=gameType, rest is game-specific snapshot
        if (parts.length < 3) return;
        String gameType = parts[1];
        String snapshotData = String.join("|", java.util.Arrays.copyOfRange(parts, 2, parts.length));
        String[] fields = snapshotData.split("\\|", -1);
        if (fields.length < 3) return;

        boolean started = Boolean.parseBoolean(fields[0]);
        boolean finished = Boolean.parseBoolean(fields[1]);
        String currentPlayer = Protocol.decode(fields[2]);
        String statusText = fields.length > 3 ? Protocol.decode(fields[fields.length - 1]) : "";
        gameStatus.setText(statusText);

        stopWaitingAnimation();
        gamePanel.removeAll();

        if (!started) {
            gamePanel.add(buildWaitingPanel(statusText), BorderLayout.CENTER);
            gamePanel.revalidate();
            gamePanel.repaint();
            return;
        }

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

        switch (gameType) {
            case "TICTACTOE" -> renderTicTacToe(board, fields, myTurn);
            case "CONNECTFOUR" -> renderConnectFour(board, fields, myTurn);
            case "CHECKERS" -> renderCheckers(board, fields, myTurn);
            case "REVERSI" -> renderReversi(board, fields, myTurn);
            case "GOMOKU" -> renderGomoku(board, fields, myTurn);
            case "RPS" -> renderRps(board, fields, myTurn);
            case "PUTTPUTT" -> renderPuttPutt(board, fields, myTurn);
            case "UNO" -> renderUno(board, fields, myTurn);
            default -> renderGeneric(board, fields, myTurn);
        }

        gamePanel.add(board, BorderLayout.CENTER);
        if (finished) {
            String outcome;
            Color outcomeColor;
            String lower = statusText.toLowerCase();
            if (lower.startsWith(username.toLowerCase()) && lower.contains("win")) {
                outcome = "\uD83C\uDFC6 YOU WIN!";
                outcomeColor = new Color(0xFF, 0xD7, 0x00);
            } else if (lower.contains("wins")) {
                outcome = "\uD83D\uDC80 YOU LOSE";
                outcomeColor = new Color(0xFF, 0x6B, 0x6B);
            } else {
                outcome = "\uD83C\uDFC1 GAME OVER";
                outcomeColor = TEXT_PRIMARY;
            }
            JPanel bannerPanel = darkPanel(new GridLayout(0, 1, 0, 2));
            JLabel banner = new JLabel(outcome, SwingConstants.CENTER);
            banner.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 42));
            banner.setForeground(outcomeColor);
            JLabel detail = new JLabel(statusText, SwingConstants.CENTER);
            detail.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
            detail.setForeground(TEXT_PRIMARY);
            bannerPanel.add(banner);
            bannerPanel.add(detail);
            gamePanel.add(bannerPanel, BorderLayout.NORTH);

            JButton againBtn = accentButton("\uD83D\uDD01 Play Again");
            againBtn.addActionListener(e -> sendCommand("PLAYAGAIN"));
            JPanel againRow = darkPanel(new FlowLayout(FlowLayout.CENTER, 8, 8));
            againRow.add(againBtn);
            if ("UNO".equals(gameType)) {
                JButton rulesBtn = accentButton("\uD83D\uDD01 Play Again (change rules)");
                rulesBtn.addActionListener(e -> {
                    String options = promptUnoRules();
                    if (options != null) {
                        sendCommand("PLAYAGAIN|" + Protocol.encode(options));
                    }
                });
                againRow.add(rulesBtn);
            }
            againRow.add(styledLabel("or leave the room \u2014 it closes automatically after a while",
                    FONT_BODY, TEXT_SECONDARY));
            gamePanel.add(againRow, BorderLayout.SOUTH);
        }
        gamePanel.revalidate();
        gamePanel.repaint();
    }

    /** Full-panel "waiting for players" screen with a gently pulsing hourglass. */
    private JPanel buildWaitingPanel(String statusText) {
        JPanel waiting = darkPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.gridx = 0;
        gbc.gridy = 0;
        JLabel hourglass = new JLabel("\u23F3", SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                float alpha = 0.55f + 0.45f * (float) Math.abs(Math.sin(waitingPhase));
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                super.paintComponent(g2);
                g2.dispose();
            }
        };
        hourglass.setFont(new Font("SansSerif", Font.PLAIN, 72));
        hourglass.setForeground(ACCENT);
        waiting.add(hourglass, gbc);

        gbc.gridy = 1;
        waiting.add(styledLabel("Waiting for players\u2026", FONT_HEADER, TEXT_PRIMARY), gbc);
        gbc.gridy = 2;
        waiting.add(styledLabel(statusText, FONT_BODY, TEXT_SECONDARY), gbc);
        gbc.gridy = 3;
        waiting.add(styledLabel("Say hi in the chat while you wait!", FONT_BODY, TEXT_SECONDARY), gbc);

        waitingTimer = new Timer(80, e -> {
            waitingPhase += 0.15f;
            hourglass.repaint();
        });
        waitingTimer.start();
        return waiting;
    }

    private void stopWaitingAnimation() {
        if (waitingTimer != null) {
            waitingTimer.stop();
            waitingTimer = null;
        }
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

    private void renderGomoku(JPanel board, String[] fields, boolean myTurn) {
        renderGridGame(board, fields, myTurn, 15, "GOMOKU");
    }

    /**
     * Renders the Putt Putt course with an animated shot. The server sends the
     * sampled path of the most recent shot; the shooter's ball rolls along it.
     */
    private void renderPuttPutt(JPanel board, String[] fields, boolean myTurn) {
        // fields: started|finished|current|w,h|hx,hy,hr|walls|balls|shooter|path|message
        String[] dims = fields[3].split(",");
        double courseW = Double.parseDouble(dims[0]);
        double courseH = Double.parseDouble(dims[1]);
        String[] hole = fields[4].split(",");
        double holeX = Double.parseDouble(hole[0]);
        double holeY = Double.parseDouble(hole[1]);
        double holeR = Double.parseDouble(hole[2]);
        String wallsStr = fields[5];
        String ballsStr = fields[6];
        String shooter = Protocol.decode(fields[7]);
        String pathStr = fields.length > 9 ? fields[8] : "";

        java.util.List<double[]> path = new java.util.ArrayList<>();
        if (!pathStr.isEmpty()) {
            for (String point : pathStr.split(";")) {
                String[] xy = point.split(":");
                path.add(new double[]{Double.parseDouble(xy[0]), Double.parseDouble(xy[1])});
            }
        }
        java.util.List<String[]> ballRows = new java.util.ArrayList<>();
        if (!ballsStr.isEmpty()) {
            for (String entry : ballsStr.split(",")) {
                ballRows.add(entry.split(":"));
            }
        }
        Color[] ballColors = {Color.WHITE, new Color(0x4F, 0xC3, 0xF7),
                new Color(0xFF, 0xD5, 0x4F), new Color(0xFF, 0x6B, 0x6B)};

        final int[] animIndex = {path.size() - 1};
        JPanel course = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                double sx = getWidth() / courseW;
                double sy = getHeight() / courseH;
                // Green with light stripes
                g2.setColor(new Color(0x2E, 0x7D, 0x32));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setColor(new Color(0x38, 0x8E, 0x3C));
                for (int i = 0; i < courseW; i += 10) {
                    g2.fillRect((int) (i * sx), 0, (int) (5 * sx), getHeight());
                }
                // Walls
                g2.setColor(new Color(0x6D, 0x4C, 0x41));
                if (!wallsStr.isEmpty()) {
                    for (String wall : wallsStr.split(";")) {
                        String[] r = wall.split(":");
                        g2.fillRect((int) (Double.parseDouble(r[0]) * sx),
                                (int) (Double.parseDouble(r[1]) * sy),
                                (int) (Double.parseDouble(r[2]) * sx),
                                (int) (Double.parseDouble(r[3]) * sy));
                    }
                }
                // Hole
                int hr = (int) (holeR * sx);
                g2.setColor(Color.BLACK);
                g2.fillOval((int) (holeX * sx) - hr, (int) (holeY * sy) - hr, hr * 2, hr * 2);
                g2.setColor(Color.LIGHT_GRAY);
                g2.drawLine((int) (holeX * sx), (int) (holeY * sy) - hr,
                        (int) (holeX * sx), (int) (holeY * sy) - hr * 4);
                g2.setColor(Color.RED);
                g2.fillPolygon(new int[]{(int) (holeX * sx), (int) (holeX * sx) + hr * 2,
                        (int) (holeX * sx)},
                        new int[]{(int) (holeY * sy) - hr * 4, (int) (holeY * sy) - hr * 3,
                                (int) (holeY * sy) - hr * 2}, 3);
                // Balls (the animating shooter ball uses the path position)
                int ballIndex = 0;
                for (String[] row : ballRows) {
                    String name = Protocol.decode(row[0]);
                    double bx = Double.parseDouble(row[1]);
                    double by = Double.parseDouble(row[2]);
                    boolean holed = Boolean.parseBoolean(row[4]);
                    if (name.equals(shooter) && animIndex[0] < path.size() - 1) {
                        bx = path.get(animIndex[0])[0];
                        by = path.get(animIndex[0])[1];
                        holed = false;
                    }
                    if (!holed) {
                        int r = Math.max(4, (int) (1.4 * sx));
                        g2.setColor(ballColors[ballIndex % ballColors.length]);
                        g2.fillOval((int) (bx * sx) - r, (int) (by * sy) - r, r * 2, r * 2);
                        g2.setColor(Color.DARK_GRAY);
                        g2.drawOval((int) (bx * sx) - r, (int) (by * sy) - r, r * 2, r * 2);
                        g2.setColor(TEXT_PRIMARY);
                        g2.setFont(FONT_SMALL);
                        g2.drawString(name, (int) (bx * sx) - r, (int) (by * sy) - r - 3);
                    }
                    ballIndex++;
                }
                g2.dispose();
            }
        };
        course.setOpaque(false);
        course.setPreferredSize(new Dimension(640, 384));
        if (path.size() > 1) {
            animIndex[0] = 0;
            Timer rollTimer = new Timer(25, null);
            rollTimer.addActionListener(e -> {
                animIndex[0]++;
                if (animIndex[0] >= path.size() - 1 || !course.isShowing()) {
                    rollTimer.stop();
                }
                course.repaint();
            });
            rollTimer.start();
        }

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 6;
        board.add(course, gbc);

        // Scorecard
        gbc.gridy = 1;
        StringBuilder score = new StringBuilder("<html>");
        for (String[] row : ballRows) {
            score.append(Protocol.decode(row[0])).append(": ").append(row[3])
                    .append(Boolean.parseBoolean(row[4]) ? " \u26F3" : "").append("&nbsp;&nbsp;&nbsp;");
        }
        score.append("</html>");
        board.add(styledLabel(score.toString(), FONT_BODY, TEXT_SECONDARY), gbc);

        // Shot controls
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        board.add(styledLabel("Angle\u00B0", FONT_BODY, TEXT_SECONDARY), gbc);
        gbc.gridx = 1;
        JSpinner angle = new JSpinner(new SpinnerNumberModel(0, -180, 360, 5));
        board.add(angle, gbc);
        gbc.gridx = 2;
        board.add(styledLabel("Power", FONT_BODY, TEXT_SECONDARY), gbc);
        gbc.gridx = 3;
        JSpinner power = new JSpinner(new SpinnerNumberModel(50, 1, 100, 5));
        board.add(power, gbc);
        gbc.gridx = 4;
        JButton puttBtn = accentButton("\u26F3 Putt!");
        puttBtn.setEnabled(myTurn);
        puttBtn.addActionListener(e ->
                sendCommand("MOVE|SHOT|" + angle.getValue() + "|" + power.getValue()));
        board.add(puttBtn, gbc);
        gbc.gridx = 5;
        board.add(styledLabel("0\u00B0 = right, 90\u00B0 = up", FONT_SMALL, TEXT_SECONDARY), gbc);
    }

    private void renderRps(JPanel board, String[] fields, boolean myTurn) {
        // fields: started|finished|pendingPlayer|scores|round|myChoice|message
        String scores = fields[3];
        String round = fields.length > 4 ? fields[4] : "";
        String myChoice = fields.length > 5 ? fields[5] : "";

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(12, 12, 12, 12);
        gbc.gridy = 0;
        gbc.gridx = 0;
        gbc.gridwidth = 3;
        StringBuilder scoreText = new StringBuilder("Round " + round + "   ");
        for (String entry : scores.split(",")) {
            String[] kv = entry.split(":", 2);
            if (kv.length == 2) {
                scoreText.append(Protocol.decode(kv[0])).append(": ").append(kv[1]).append("   ");
            }
        }
        board.add(styledLabel(scoreText.toString().trim(), FONT_HEADER, TEXT_PRIMARY), gbc);

        gbc.gridy = 1;
        gbc.gridwidth = 1;
        String[][] options = {{"ROCK", "\u270A"}, {"PAPER", "\u270B"}, {"SCISSORS", "\u270C"}};
        for (int i = 0; i < options.length; i++) {
            String choice = options[i][0];
            JButton btn = accentButton(options[i][1] + " " + choice);
            btn.setPreferredSize(new Dimension(160, 60));
            btn.setEnabled(myTurn);
            btn.addActionListener(e -> sendCommand("MOVE|" + choice));
            gbc.gridx = i;
            board.add(btn, gbc);
        }

        gbc.gridy = 2;
        gbc.gridx = 0;
        gbc.gridwidth = 3;
        String hint = !myChoice.isEmpty() ? "You chose " + myChoice + " — waiting for opponent\u2026"
                : myTurn ? "Make your choice!" : "";
        board.add(styledLabel(hint, FONT_BODY, TEXT_SECONDARY), gbc);
    }

    private void renderGridGame(JPanel board, String[] fields, boolean myTurn, int size, String type) {
        String[] rows = fields[3].split(",");
        String myPiece = fields[4];
        int cellSize = size > 10 ? 32 : 48;
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
                        } else if (type.equals("GOMOKU")) {
                            g2.setColor(new Color(0xC8, 0x9B, 0x5C));
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
                cell.setPreferredSize(new Dimension(cellSize, cellSize));
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
        if (type.equals("REVERSI") || type.equals("GOMOKU")) {
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

    /** Last UNO rule selections, reused to prefill the rules dialog. */
    private String lastUnoOptions = "";

    /**
     * Shows the UNO house-rules dialog and returns the chosen options as a
     * {@code key=value;key=value} string, or null if the dialog was cancelled.
     */
    private String promptUnoRules() {
        java.util.Map<String, String> last = new java.util.HashMap<>();
        for (String pair : lastUnoOptions.split(";")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                last.put(pair.substring(0, eq), pair.substring(eq + 1));
            }
        }
        JSpinner handSize = new JSpinner(new SpinnerNumberModel(
                Integer.parseInt(last.getOrDefault("handSize", "7")), 3, 10, 1));
        JSpinner maxPlayers = new JSpinner(new SpinnerNumberModel(
                Integer.parseInt(last.getOrDefault("maxPlayers", "4")), 2, 8, 1));
        JCheckBox drawToMatch = new JCheckBox("Draw to match: keep drawing until you get a playable card",
                Boolean.parseBoolean(last.getOrDefault("drawToMatch", "false")));
        JCheckBox playDrawn = new JCheckBox("Play drawn card: place a playable drawn card or keep it",
                Boolean.parseBoolean(last.getOrDefault("playDrawn", "false")));
        JCheckBox callUno = new JCheckBox("Call UNO: forget to call UNO at 2 cards and draw 2",
                Boolean.parseBoolean(last.getOrDefault("callUno", "false")));
        JCheckBox stackDraws = new JCheckBox("Stacking: stack +2/+4 cards to pass the penalty on",
                Boolean.parseBoolean(last.getOrDefault("stackDraws", "false")));
        JCheckBox sevenZero = new JCheckBox("Seven-Zero: 7 swaps hands, 0 rotates all hands",
                Boolean.parseBoolean(last.getOrDefault("sevenZero", "false")));

        JPanel form = new JPanel(new GridLayout(0, 1, 4, 4));
        JPanel handRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        handRow.add(new JLabel("Starting hand size:"));
        handRow.add(handSize);
        JPanel playersRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        playersRow.add(new JLabel("Max players:"));
        playersRow.add(maxPlayers);
        form.add(handRow);
        form.add(playersRow);
        form.add(drawToMatch);
        form.add(playDrawn);
        form.add(callUno);
        form.add(stackDraws);
        form.add(sevenZero);

        int result = JOptionPane.showConfirmDialog(this, form, "UNO House Rules",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return null;
        }
        String options = "handSize=" + handSize.getValue()
                + ";maxPlayers=" + maxPlayers.getValue()
                + ";drawToMatch=" + drawToMatch.isSelected()
                + ";playDrawn=" + playDrawn.isSelected()
                + ";callUno=" + callUno.isSelected()
                + ";stackDraws=" + stackDraws.isSelected()
                + ";sevenZero=" + sevenZero.isSelected();
        lastUnoOptions = options;
        return options;
    }

    private void renderUno(JPanel board, String[] fields, boolean myTurn) {
        // fields: started|finished|currentPlayer|topCard|activeColor|hand|players|drawPileSize|flags|message
        String topCardToken = fields[3];
        String activeColorStr = fields[4];
        String handStr = fields[5];
        String playersStr = fields[6];
        String flagsStr = fields.length > 9 ? fields[8] : "";
        java.util.Set<String> flags = new java.util.HashSet<>(
                java.util.Arrays.asList(flagsStr.split(",")));
        boolean pendingDrawn = flags.contains("PLAYDRAWN") && myTurn;
        String stackFlag = flags.stream().filter(f -> f.startsWith("STACK:"))
                .findFirst().orElse(null);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 5;

        // Players
        JLabel playersLabel = styledLabel(formatUnoPlayers(playersStr), FONT_BODY, TEXT_SECONDARY);
        board.add(playersLabel, gbc);

        // Pending stacked draw penalty
        if (stackFlag != null) {
            gbc.gridy++;
            JLabel stackLabel = styledLabel("\u26A0 +" + stackFlag.substring(6)
                    + " cards pending \u2014 stack a draw card or draw them!",
                    FONT_BODY, new Color(0xFF, 0x6B, 0x6B));
            board.add(stackLabel, gbc);
        }

        // Top card
        gbc.gridy++;
        if (!topCardToken.isEmpty()) {
            com.boardgame.model.Card topCard = com.boardgame.model.Card.fromToken(topCardToken);
            JPanel cardPanel = createUnoCardPanel(topCard, activeColorStr, true);
            board.add(cardPanel, gbc);
        }

        // Action row: draw / keep / play drawn / call UNO
        gbc.gridy++;
        gbc.gridwidth = 1;
        JComboBox<String> colorBox = styledComboBox(
                new String[]{"RED", "YELLOW", "GREEN", "BLUE"});
        if (pendingDrawn) {
            JButton playDrawnBtn = accentButton("Play Drawn Card");
            playDrawnBtn.addActionListener(e -> sendCommand(
                    "MOVE|PLAYDRAWN|" + colorBox.getSelectedItem()));
            board.add(playDrawnBtn, gbc);
            gbc.gridx = 1;
            JButton keepBtn = accentButton("Keep It");
            keepBtn.addActionListener(e -> sendCommand("MOVE|KEEP"));
            board.add(keepBtn, gbc);
            gbc.gridx = 2;
        } else {
            JButton drawBtn = accentButton("Draw Card");
            drawBtn.setEnabled(myTurn);
            drawBtn.addActionListener(e -> sendCommand("MOVE|DRAW"));
            board.add(drawBtn, gbc);
            gbc.gridx = 1;
        }
        if (flags.contains("CANCALLUNO")) {
            JButton unoBtn = accentButton("\uD83D\uDCE2 Call UNO!");
            unoBtn.addActionListener(e -> sendCommand("MOVE|CALLUNO"));
            board.add(unoBtn, gbc);
            gbc.gridx++;
        } else if (flags.contains("UNOCALLED")) {
            board.add(styledLabel("UNO called \u2714", FONT_BODY,
                    new Color(0x7A, 0xE5, 0x82)), gbc);
            gbc.gridx++;
        }

        // Wild color selector
        board.add(colorBox, gbc);

        // Hand
        gbc.gridy++;
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
        sendCommand("CHARACTER|" + selectedSymbol + "|" + selectedColor
                + "|" + Protocol.encode(selectedTitle));
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

    private static JComboBox<String> styledComboBox(String[] items) {
        JComboBox<String> box = new JComboBox<>(items);
        box.setBackground(BG_INPUT);
        box.setForeground(TEXT_PRIMARY);
        box.setFont(FONT_BODY);
        box.setBorder(BorderFactory.createLineBorder(BG_INPUT.brighter(), 1));
        box.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        box.setUI(new javax.swing.plaf.basic.BasicComboBoxUI() {
            @Override
            protected JButton createArrowButton() {
                JButton arrow = new JButton() {
                    @Override
                    protected void paintComponent(Graphics g) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setColor(BG_INPUT);
                        g2.fillRect(0, 0, getWidth(), getHeight());
                        g2.setColor(ACCENT);
                        int cx = getWidth() / 2;
                        int cy = getHeight() / 2;
                        g2.fillPolygon(new int[]{cx - 4, cx + 4, cx},
                                new int[]{cy - 2, cy - 2, cy + 4}, 3);
                        g2.dispose();
                    }
                };
                arrow.setBorder(BorderFactory.createEmptyBorder());
                arrow.setContentAreaFilled(false);
                arrow.setFocusPainted(false);
                return arrow;
            }
        });
        box.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                label.setBackground(isSelected ? ACCENT : BG_INPUT);
                label.setForeground(isSelected ? Color.WHITE : TEXT_PRIMARY);
                label.setFont(FONT_BODY);
                label.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
                return label;
            }
        });
        return box;
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
