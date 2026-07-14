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
import javax.swing.JProgressBar;
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
    /** Drives all in-game animations (turn glow, confetti, card lifts). */
    private Timer boardAnimTimer;
    private float boardAnimPhase;
    private static final int ANIMATION_TICK_MS = 30;
    private static final float ANIMATION_PHASE_INCREMENT = 0.09f;
    private static final int CONFETTI_PIECE_COUNT = 90;
    private static final float CARD_LIFT_EASING = 0.3f;
    private String currentGameType = "";

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
                String options = switch (type) {
                    case "UNO" -> promptUnoRules();
                    case "PUTTPUTT" -> promptPuttPuttRules();
                    default -> null;
                };
                if (("UNO".equals(type) || "PUTTPUTT".equals(type)) && options == null) {
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
        JButton helpBtn = accentButton("?");
        helpBtn.setToolTipText("How to play");
        helpBtn.setPreferredSize(new Dimension(40, 32));
        helpBtn.addActionListener(e -> showHowToPlay());
        JPanel top = darkPanel(new BorderLayout());
        top.add(gameStatus, BorderLayout.CENTER);
        JPanel topRight = darkPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        topRight.add(helpBtn);
        topRight.add(leaveBtn);
        top.add(topRight, BorderLayout.EAST);
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
        currentGameType = gameType;
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
        if (!finished) {
            gamePanel.add(buildTurnBanner(myTurn, currentPlayer), BorderLayout.NORTH);
        }
        if (finished) {
            String outcome;
            Color outcomeColor;
            String lower = statusText.toLowerCase();
            boolean won = lower.startsWith(username.toLowerCase()) && lower.contains("win");
            if (won) {
                outcome = "\uD83C\uDFC6 YOU WIN!";
                outcomeColor = new Color(0xFF, 0xD7, 0x00);
            } else if (lower.contains("wins")) {
                outcome = "\uD83D\uDC80 YOU LOSE";
                outcomeColor = new Color(0xFF, 0x6B, 0x6B);
            } else {
                outcome = "\uD83C\uDFC1 GAME OVER";
                outcomeColor = TEXT_PRIMARY;
            }
            gamePanel.add(buildOutcomeBanner(outcome, outcomeColor, statusText, won),
                    BorderLayout.NORTH);

            JButton againBtn = accentButton("\uD83D\uDD01 Play Again");
            againBtn.addActionListener(e -> sendCommand("PLAYAGAIN"));
            JPanel againRow = darkPanel(new FlowLayout(FlowLayout.CENTER, 8, 8));
            againRow.add(againBtn);
            if ("UNO".equals(gameType) || "PUTTPUTT".equals(gameType)) {
                JButton rulesBtn = accentButton("\uD83D\uDD01 Play Again (change rules)");
                rulesBtn.addActionListener(e -> {
                    String options = "UNO".equals(gameType) ? promptUnoRules()
                            : promptPuttPuttRules();
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
        startBoardAnimation();
        gamePanel.revalidate();
        gamePanel.repaint();
    }

    /** Restarts the shared ~33fps animation clock that drives in-game effects. */
    private void startBoardAnimation() {
        boardAnimTimer = new Timer(ANIMATION_TICK_MS, e -> {
            boardAnimPhase += ANIMATION_PHASE_INCREMENT;
            gamePanel.repaint();
        });
        boardAnimTimer.start();
    }

    /**
     * Banner above the board making the turn unmissable: a pulsing golden
     * pill when it is your turn, a subdued "waiting" pill otherwise.
     */
    private JPanel buildTurnBanner(boolean myTurn, String currentPlayer) {
        String text = myTurn ? "\uD83D\uDD25 YOUR TURN \u2014 make your move!"
                : "\u23F3 Waiting for " + currentPlayer + "\u2026";
        JPanel banner = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setFont(new Font("SansSerif", Font.BOLD, myTurn ? 22 : 16));
                FontMetrics fm = g2.getFontMetrics();
                int pillW = fm.stringWidth(text) + 48;
                int pillH = getHeight() - 12;
                int x = (getWidth() - pillW) / 2;
                int y = 6;
                float pulse = 0.5f + 0.5f * (float) Math.sin(boardAnimPhase * 2);
                if (myTurn) {
                    // Soft expanding glow behind the pill
                    for (int i = 4; i >= 1; i--) {
                        g2.setColor(new Color(255, 200, 0, (int) (12 + pulse * 12)));
                        g2.fillRoundRect(x - i * 3, y - i * 2, pillW + i * 6,
                                pillH + i * 4, pillH, pillH);
                    }
                    g2.setPaint(new GradientPaint(x, y, new Color(0xFF, 0xB3, 0x00),
                            x, y + pillH, new Color(0xFF, 0x6F, 0x00)));
                    g2.fillRoundRect(x, y, pillW, pillH, pillH, pillH);
                    g2.setColor(new Color(255, 255, 255, (int) (120 + pulse * 135)));
                    g2.setStroke(new BasicStroke(2.5f));
                    g2.drawRoundRect(x, y, pillW, pillH, pillH, pillH);
                    g2.setColor(new Color(0x33, 0x1A, 0x00));
                } else {
                    g2.setColor(BG_INPUT);
                    g2.fillRoundRect(x, y, pillW, pillH, pillH, pillH);
                    g2.setColor(TEXT_SECONDARY);
                }
                g2.drawString(text, x + 24,
                        y + (pillH + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        banner.setOpaque(false);
        banner.setPreferredSize(new Dimension(0, myTurn ? 56 : 44));
        return banner;
    }

    /** End-of-game banner; on a win it rains animated confetti behind the text. */
    private JPanel buildOutcomeBanner(String outcome, Color outcomeColor,
                                      String statusText, boolean won) {
        final int pieces = CONFETTI_PIECE_COUNT;
        final float[][] confetti = new float[pieces][4]; // x%, speed, drift, size
        final Color[] confettiColors = {new Color(0xFF, 0x52, 0x52), new Color(0xFF, 0xD7, 0x00),
                new Color(0x69, 0xF0, 0xAE), new Color(0x40, 0xC4, 0xFF), ACCENT};
        java.util.Random rnd = new java.util.Random();
        for (float[] p : confetti) {
            p[0] = rnd.nextFloat();
            p[1] = 0.4f + rnd.nextFloat() * 0.9f;
            p[2] = rnd.nextFloat() * 6.28f;
            p[3] = 4 + rnd.nextFloat() * 5;
        }
        JPanel bannerPanel = new JPanel(new GridLayout(0, 1, 0, 2)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (!won) {
                    return;
                }
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                for (int i = 0; i < pieces; i++) {
                    float[] p = confetti[i];
                    float fall = (boardAnimPhase * p[1] * 18 + i * 13) % (getHeight() + 20);
                    int px = (int) (p[0] * getWidth()
                            + Math.sin(boardAnimPhase * 1.5 + p[2]) * 14);
                    g2.setColor(confettiColors[i % confettiColors.length]);
                    g2.rotate(boardAnimPhase * p[1] * 2 + p[2], px, fall - 10);
                    g2.fillRect(px - (int) (p[3] / 2), (int) (fall - 10 - p[3] / 2),
                            (int) p[3], (int) (p[3] * 0.6f));
                    g2.rotate(-(boardAnimPhase * p[1] * 2 + p[2]), px, fall - 10);
                }
                g2.dispose();
            }
        };
        bannerPanel.setBackground(BG_DARK);
        bannerPanel.setPreferredSize(new Dimension(0, 100));
        JLabel banner = new JLabel(outcome, SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                float pulse = won ? 1f + 0.05f * (float) Math.sin(boardAnimPhase * 2) : 1f;
                setFont(getFont().deriveFont(42f * pulse));
                super.paintComponent(g2);
                g2.dispose();
            }
        };
        banner.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 42));
        banner.setForeground(outcomeColor);
        banner.setOpaque(false);
        JLabel detail = new JLabel(statusText, SwingConstants.CENTER);
        detail.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        detail.setForeground(TEXT_PRIMARY);
        detail.setOpaque(false);
        bannerPanel.add(banner);
        bannerPanel.add(detail);
        return bannerPanel;
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
        if (boardAnimTimer != null) {
            boardAnimTimer.stop();
            boardAnimTimer = null;
        }
    }

    /** Shows game rules for the current game in a scrollable dialog. */
    private void showHowToPlay() {
        String rules = switch (currentGameType) {
            case "UNO" -> """
                    Match the top card of the discard pile by color or value.
                    \u2022 Click a card in your hand to play it (bright cards are playable).
                    \u2022 Wild cards: click the card, then pick a color from the popup.
                    \u2022 No playable card? Click the draw pile (or the Draw Card button).
                    \u2022 Skip / Reverse / +2 / +4 change the flow \u2014 watch the spinning
                      direction arrow and the glowing ring showing whose turn it is.
                    \u2022 First player to empty their hand wins!
                    House rules (chosen when the room was created) may add: stacking
                    +2/+4 penalties, playing a just-drawn card, calling UNO at 2 cards,
                    drawing until you can play, and 7/0 hand swapping.""";
            case "TICTACTOE" -> """
                    Take turns placing your mark on the 3\u00D73 grid.
                    Get three in a row (across, down or diagonal) to win.""";
            case "CONNECTFOUR" -> """
                    Take turns dropping discs into a column (click \u25BC).
                    Connect four of your discs in a row \u2014 horizontally,
                    vertically or diagonally \u2014 to win.""";
            case "CHECKERS" -> """
                    Move your pieces diagonally forward one square.
                    Jump over an opponent's piece to capture it.
                    Reach the far side to crown a king, which moves both ways.
                    Capture all opposing pieces to win.""";
            case "REVERSI" -> """
                    Place a disc so it brackets a line of opposing discs;
                    everything in between flips to your color.
                    When no one can move, the player with more discs wins.""";
            case "GOMOKU" -> """
                    Take turns placing stones on the board.
                    The first player to line up five stones in a row wins.""";
            case "DOTSANDBOXES" -> """
                    Take turns drawing one line between two adjacent dots.
                    Complete the fourth side of a box to claim it and move again.
                    Most boxes when the grid is full wins.""";
            case "RPS" -> """
                    Pick Rock, Paper or Scissors each round.
                    Rock beats Scissors, Scissors beats Paper, Paper beats Rock.""";
            case "PUTTPUTT" -> """
                    Play a round of mini golf (nine holes by default).
                    \u2022 Aim: click or drag on the green \u2014 the dashed line shows
                      your shot direction. Fine-tune with the \u21BA / \u21BB buttons.
                    \u2022 Power: the power bar sweeps up and down; press Putt! to
                      lock it in and take the shot.
                    \u2022 Power-ups on the course are collected by rolling over them:
                      B = power boost (you), M = bigger cup (you),
                      S = sand trap (rivals), W = wobbly aim (rivals).
                    \u2022 Sink everyone's ball to move to the next hole.
                    Fewest total strokes after the last hole wins!
                    Course difficulty, size, hole count and power-ups are chosen
                    when the room is created.""";
            default -> "Take turns making moves. The status bar at the top tells\n"
                    + "you whose turn it is and what happened last.";
        };
        JTextArea area = new JTextArea(rules);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(FONT_BODY);
        area.setBackground(BG_CARD);
        area.setForeground(TEXT_PRIMARY);
        area.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(460, 300));
        scroll.setBorder(BorderFactory.createEmptyBorder());
        JOptionPane.showMessageDialog(this, scroll,
                "\u2753 How to Play" + (currentGameType.isEmpty() ? "" : " \u2014 " + currentGameType),
                JOptionPane.PLAIN_MESSAGE);
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

    /** Aim direction in degrees (0 = right, CCW positive), kept across snapshots. */
    private double puttAimAngle;
    /** Drives the oscillating putt power bar. */
    private Timer puttPowerTimer;

    /**
     * Renders the Putt Putt course with an animated shot. Aim by clicking or
     * dragging on the green (fine-tune with the arrow buttons), then press
     * Putt! to lock the oscillating power bar and take the shot.
     */
    private void renderPuttPutt(JPanel board, String[] fields, boolean myTurn) {
        // fields: started|finished|current|w,h|hx,hy,hr|walls|balls|shooter|path|
        //         holeNum/holes|powerups|effects|message
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
        String holeInfo = fields.length > 10 ? fields[9] : "";
        String powerUpsStr = fields.length > 11 ? fields[10] : "";
        String effectsStr = fields.length > 12 ? fields[11] : "";

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
        java.util.List<String[]> powerUps = new java.util.ArrayList<>();
        if (!powerUpsStr.isEmpty()) {
            for (String entry : powerUpsStr.split(";")) {
                powerUps.add(entry.split(":"));
            }
        }
        java.util.Map<String, String> effects = new java.util.HashMap<>();
        if (!effectsStr.isEmpty()) {
            for (String entry : effectsStr.split(",")) {
                String[] kv = entry.split(":");
                if (kv.length == 2) {
                    effects.put(Protocol.decode(kv[0]), kv[1]);
                }
            }
        }
        Color[] ballColors = {Color.WHITE, new Color(0x4F, 0xC3, 0xF7),
                new Color(0xFF, 0xD5, 0x4F), new Color(0xFF, 0x6B, 0x6B)};

        double[] myBall = null;
        for (String[] row : ballRows) {
            if (Protocol.decode(row[0]).equals(username)) {
                myBall = new double[]{Double.parseDouble(row[1]), Double.parseDouble(row[2])};
            }
        }
        final double[] myBallPos = myBall;

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
                // Power-ups
                for (String[] powerUp : powerUps) {
                    double px = Double.parseDouble(powerUp[1]) * sx;
                    double py = Double.parseDouble(powerUp[2]) * sy;
                    int pr = Math.max(6, (int) (2.0 * sx));
                    g2.setColor(puttPowerUpColor(powerUp[0]));
                    g2.fillOval((int) px - pr, (int) py - pr, pr * 2, pr * 2);
                    g2.setColor(Color.WHITE);
                    g2.drawOval((int) px - pr, (int) py - pr, pr * 2, pr * 2);
                    g2.setFont(new Font("SansSerif", Font.BOLD, Math.max(9, pr)));
                    FontMetrics fm = g2.getFontMetrics();
                    String letter = powerUp[0].substring(0, 1);
                    g2.drawString(letter, (int) px - fm.stringWidth(letter) / 2,
                            (int) py + fm.getAscent() / 2 - 1);
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
                // Aim indicator: dashed line from my ball in the aim direction
                if (myTurn && myBallPos != null) {
                    double rad = Math.toRadians(puttAimAngle);
                    int ax = (int) (myBallPos[0] * sx);
                    int ay = (int) (myBallPos[1] * sy);
                    int ex = ax + (int) (Math.cos(rad) * 60);
                    int ey = ay - (int) (Math.sin(rad) * 60);
                    g2.setColor(new Color(255, 255, 255, 200));
                    g2.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                            0, new float[]{6, 6}, 0));
                    g2.drawLine(ax, ay, ex, ey);
                    g2.fillOval(ex - 4, ey - 4, 8, 8);
                }
                g2.dispose();
            }
        };
        course.setOpaque(false);
        course.setPreferredSize(new Dimension(640, 384));
        if (myTurn && myBallPos != null) {
            course.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            java.awt.event.MouseAdapter aimer = new java.awt.event.MouseAdapter() {
                private void aim(java.awt.event.MouseEvent e) {
                    double sx = course.getWidth() / courseW;
                    double sy = course.getHeight() / courseH;
                    double dx = e.getX() / sx - myBallPos[0];
                    double dy = e.getY() / sy - myBallPos[1];
                    if (dx != 0 || dy != 0) {
                        puttAimAngle = Math.toDegrees(Math.atan2(-dy, dx));
                        course.repaint();
                    }
                }

                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    aim(e);
                }

                @Override
                public void mouseDragged(java.awt.event.MouseEvent e) {
                    aim(e);
                }
            };
            course.addMouseListener(aimer);
            course.addMouseMotionListener(aimer);
        }
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

        // Scorecard with hole progress and pending effects
        gbc.gridy = 1;
        StringBuilder score = new StringBuilder("<html>\u26F3 Hole " + holeInfo
                + "&nbsp;&nbsp;&nbsp;");
        for (String[] row : ballRows) {
            String name = Protocol.decode(row[0]);
            score.append(name).append(": ").append(row[3]);
            if (row.length > 5) {
                score.append(" (").append(row[5]).append(" this hole)");
            }
            score.append(Boolean.parseBoolean(row[4]) ? " \u26F3" : "");
            String effect = effects.get(name);
            if (effect != null) {
                score.append(" \u2728").append(effect);
            }
            score.append("&nbsp;&nbsp;&nbsp;");
        }
        score.append("</html>");
        board.add(styledLabel(score.toString(), FONT_BODY, TEXT_SECONDARY), gbc);

        gbc.gridy = 2;
        board.add(styledLabel("Power-ups: B power boost \u00B7 M bigger cup \u00B7 "
                        + "S sands your rivals \u00B7 W wobbles their aim",
                FONT_SMALL, TEXT_SECONDARY), gbc);

        // Shot controls: aim adjustment + oscillating power bar
        if (puttPowerTimer != null) {
            puttPowerTimer.stop();
            puttPowerTimer = null;
        }
        gbc.gridy = 3;
        gbc.gridwidth = 1;
        JButton aimLeft = accentButton("\u21BA");
        aimLeft.setToolTipText("Rotate aim counter-clockwise");
        aimLeft.setPreferredSize(new Dimension(48, 32));
        aimLeft.setEnabled(myTurn);
        JButton aimRight = accentButton("\u21BB");
        aimRight.setToolTipText("Rotate aim clockwise");
        aimRight.setPreferredSize(new Dimension(48, 32));
        aimRight.setEnabled(myTurn);
        aimLeft.addActionListener(e -> {
            puttAimAngle += 3;
            course.repaint();
        });
        aimRight.addActionListener(e -> {
            puttAimAngle -= 3;
            course.repaint();
        });
        gbc.gridx = 0;
        board.add(aimLeft, gbc);
        gbc.gridx = 1;
        board.add(aimRight, gbc);

        JProgressBar powerBar = new JProgressBar(1, 100);
        powerBar.setValue(50);
        powerBar.setStringPainted(true);
        powerBar.setString("Power");
        powerBar.setPreferredSize(new Dimension(220, 28));
        powerBar.setForeground(ACCENT);
        powerBar.setBackground(BG_INPUT);
        gbc.gridx = 2;
        gbc.gridwidth = 2;
        board.add(powerBar, gbc);

        JButton puttBtn = accentButton("\u26F3 Putt!");
        puttBtn.setEnabled(myTurn);
        gbc.gridx = 4;
        gbc.gridwidth = 1;
        board.add(puttBtn, gbc);
        gbc.gridx = 5;
        board.add(styledLabel("Click the green to aim, Putt! locks the power",
                FONT_SMALL, TEXT_SECONDARY), gbc);

        if (myTurn) {
            final int[] tick = {0};
            puttPowerTimer = new Timer(30, e -> {
                if (!powerBar.isShowing()) {
                    ((Timer) e.getSource()).stop();
                    return;
                }
                tick[0]++;
                // Triangle wave sweeping 1..100 and back
                int phase = tick[0] % 100;
                int value = phase <= 50 ? 1 + phase * 2 : 1 + (100 - phase) * 2;
                powerBar.setValue(Math.min(100, value));
                powerBar.setString("Power " + powerBar.getValue());
            });
            puttPowerTimer.start();
            puttBtn.addActionListener(e -> {
                if (puttPowerTimer != null) {
                    puttPowerTimer.stop();
                    puttPowerTimer = null;
                }
                puttBtn.setEnabled(false);
                sendCommand("MOVE|SHOT|" + Math.round(puttAimAngle * 100) / 100.0
                        + "|" + powerBar.getValue());
            });
        }
    }

    private static Color puttPowerUpColor(String type) {
        return switch (type) {
            case "BLAST" -> new Color(0xFF, 0x8F, 0x00);
            case "MAGNET" -> new Color(0x00, 0xBC, 0xD4);
            case "SAND" -> new Color(0xD7, 0xB3, 0x77);
            case "WOBBLE" -> new Color(0xAB, 0x47, 0xBC);
            default -> Color.GRAY;
        };
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

    /** Last Putt Putt rule selections, reused to prefill the rules dialog. */
    private String lastPuttOptions = "";

    /**
     * Shows the Putt Putt course-setup dialog and returns the chosen options
     * as a {@code key=value;key=value} string, or null if cancelled.
     */
    private String promptPuttPuttRules() {
        java.util.Map<String, String> last = new java.util.HashMap<>();
        for (String pair : lastPuttOptions.split(";")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                last.put(pair.substring(0, eq), pair.substring(eq + 1));
            }
        }
        JSpinner holes = new JSpinner(new SpinnerNumberModel(
                Integer.parseInt(last.getOrDefault("holes", "9")), 1, 18, 1));
        JComboBox<String> difficulty = new JComboBox<>(new String[]{"EASY", "MEDIUM", "HARD"});
        difficulty.setSelectedItem(last.getOrDefault("difficulty", "MEDIUM"));
        JComboBox<String> courseSize = new JComboBox<>(new String[]{"SMALL", "MEDIUM", "LARGE"});
        courseSize.setSelectedItem(last.getOrDefault("courseSize", "MEDIUM"));
        JSpinner maxPlayers = new JSpinner(new SpinnerNumberModel(
                Integer.parseInt(last.getOrDefault("maxPlayers", "4")), 2, 4, 1));
        JCheckBox powerUps = new JCheckBox(
                "Power-ups: collect boosts on the green (bad ones hit your rivals)",
                Boolean.parseBoolean(last.getOrDefault("powerUps", "true")));

        JPanel form = new JPanel(new GridLayout(0, 1, 4, 4));
        JPanel holesRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        holesRow.add(new JLabel("Holes:"));
        holesRow.add(holes);
        JPanel difficultyRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        difficultyRow.add(new JLabel("Course difficulty:"));
        difficultyRow.add(difficulty);
        JPanel sizeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        sizeRow.add(new JLabel("Course size:"));
        sizeRow.add(courseSize);
        JPanel playersRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        playersRow.add(new JLabel("Max players:"));
        playersRow.add(maxPlayers);
        form.add(holesRow);
        form.add(difficultyRow);
        form.add(sizeRow);
        form.add(playersRow);
        form.add(powerUps);

        int result = JOptionPane.showConfirmDialog(this, form, "Putt Putt Course Setup",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return null;
        }
        String options = "holes=" + holes.getValue()
                + ";difficulty=" + difficulty.getSelectedItem()
                + ";courseSize=" + courseSize.getSelectedItem()
                + ";maxPlayers=" + maxPlayers.getValue()
                + ";powerUps=" + powerUps.isSelected();
        lastPuttOptions = options;
        return options;
    }

    private void renderUno(JPanel board, String[] fields, boolean myTurn) {
        // fields: started|finished|currentPlayer|topCard|activeColor|hand|players|drawPileSize|flags|message
        String currentPlayer = Protocol.decode(fields[2]);
        String topCardToken = fields[3];
        String activeColorStr = fields[4];
        String handStr = fields[5];
        String playersStr = fields[6];
        int drawPileSize = 0;
        try {
            drawPileSize = Integer.parseInt(fields[7]);
        } catch (NumberFormatException ignored) {
            // Older servers may not send the pile size
        }
        String flagsStr = fields.length > 9 ? fields[8] : "";
        java.util.Set<String> flags = new java.util.HashSet<>(
                java.util.Arrays.asList(flagsStr.split(",")));
        boolean pendingDrawn = flags.contains("PLAYDRAWN") && myTurn;
        String stackFlag = flags.stream().filter(f -> f.startsWith("STACK:"))
                .findFirst().orElse(null);
        boolean clockwise = !flags.contains("DIR:CCW");
        com.boardgame.model.Card topCard = topCardToken.isEmpty()
                ? null : com.boardgame.model.Card.fromToken(topCardToken);
        com.boardgame.model.Card.Color activeColor = activeColorStr.isEmpty()
                ? null : com.boardgame.model.Card.Color.valueOf(activeColorStr);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 5;

        // Every player's card stack, in seat order, with direction indicator
        JPanel seats = darkPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        seats.setOpaque(false);
        seats.add(createUnoDirectionIndicator(clockwise));
        if (!playersStr.isEmpty()) {
            for (String p : playersStr.split(",")) {
                String[] kv = p.split(":", 2);
                if (kv.length == 2) {
                    String name = Protocol.decode(kv[0]);
                    int count;
                    try {
                        count = Integer.parseInt(kv[1]);
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    seats.add(createUnoSeatPanel(name, count, name.equals(currentPlayer),
                            name.equals(username)));
                }
            }
        }
        board.add(seats, gbc);

        // Pending stacked draw penalty
        if (stackFlag != null) {
            gbc.gridy++;
            JLabel stackLabel = styledLabel("\u26A0 +" + stackFlag.substring(6)
                    + " cards pending \u2014 stack a draw card or draw them!",
                    FONT_BODY, new Color(0xFF, 0x6B, 0x6B));
            board.add(stackLabel, gbc);
        }

        // Table center: draw pile next to the discard top card
        gbc.gridy++;
        JPanel table = darkPanel(new FlowLayout(FlowLayout.CENTER, 24, 0));
        table.setOpaque(false);
        JPanel pilePanel = createUnoDrawPilePanel(drawPileSize, myTurn && !pendingDrawn);
        table.add(pilePanel);
        if (topCard != null) {
            table.add(createUnoCardPanel(topCard, activeColorStr, true, false));
        }
        board.add(table, gbc);

        // Action row: draw / keep / play drawn / call UNO
        gbc.gridy++;
        gbc.gridwidth = 1;
        if (pendingDrawn) {
            boolean drawnIsWild = !handStr.isEmpty() && com.boardgame.model.Card
                    .fromToken(handStr.split(",")[handStr.split(",").length - 1])
                    .color() == com.boardgame.model.Card.Color.WILD;
            JButton playDrawnBtn = accentButton("Play Drawn Card");
            playDrawnBtn.addActionListener(e -> {
                String color = drawnIsWild ? promptWildColor() : "";
                if (color != null) {
                    sendCommand("MOVE|PLAYDRAWN|" + color);
                }
            });
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

        // Hand: playable cards glow and lift on hover
        gbc.gridy++;
        gbc.gridx = 0;
        gbc.gridwidth = 5;
        JPanel handPanel = darkPanel(new FlowLayout(FlowLayout.CENTER, 4, 4));
        if (!handStr.isEmpty()) {
            String[] cardTokens = handStr.split(",");
            for (int i = 0; i < cardTokens.length; i++) {
                int idx = i;
                com.boardgame.model.Card card = com.boardgame.model.Card.fromToken(cardTokens[i]);
                boolean playable = myTurn && topCard != null
                        && isUnoCardPlayable(card, idx, cardTokens.length,
                                topCard, activeColor, pendingDrawn, stackFlag != null);
                JPanel cardP = createUnoCardPanel(card, null, false, playable);
                cardP.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                cardP.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        if (myTurn) {
                            String color = "";
                            if (card.color() == com.boardgame.model.Card.Color.WILD) {
                                color = promptWildColor();
                                if (color == null) {
                                    return;
                                }
                            }
                            sendCommand("MOVE|" + idx + "|" + color);
                        }
                    }

                    @Override
                    public void mouseEntered(MouseEvent e) {
                        cardP.putClientProperty("liftTarget", 12f);
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        cardP.putClientProperty("liftTarget", 0f);
                    }
                });
                handPanel.add(cardP);
            }
        }
        JScrollPane scroll = new JScrollPane(handPanel);
        scroll.setBackground(BG_DARK);
        scroll.getViewport().setBackground(BG_DARK);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setPreferredSize(new Dimension(700, 165));
        board.add(scroll, gbc);
    }

    /** Client-side mirror of the server's playability rules, used for the card glow. */
    private static boolean isUnoCardPlayable(com.boardgame.model.Card card, int idx, int handSize,
                                             com.boardgame.model.Card topCard,
                                             com.boardgame.model.Card.Color activeColor,
                                             boolean pendingDrawn, boolean stackPending) {
        if (pendingDrawn && idx != handSize - 1) {
            return false;
        }
        if (stackPending) {
            return card.value() == topCard.value()
                    && (card.value() == com.boardgame.model.Card.Value.DRAW_TWO
                    || card.value() == com.boardgame.model.Card.Value.WILD_DRAW_FOUR);
        }
        return card.matches(topCard, activeColor);
    }

    /** A player's seat: fanned face-down card stack, name, count, turn glow, UNO badge. */
    private JPanel createUnoSeatPanel(String name, int count, boolean isTurn, boolean isMe) {
        JPanel seat = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                float pulse = 0.5f + 0.5f * (float) Math.sin(boardAnimPhase * 2);
                // Pulsing golden ring when it is this player's turn
                if (isTurn) {
                    for (int i = 3; i >= 1; i--) {
                        g2.setColor(new Color(255, 200, 0, (int) (20 + pulse * 25)));
                        g2.setStroke(new BasicStroke(i * 2f));
                        g2.drawRoundRect(i, i, getWidth() - 1 - 2 * i,
                                getHeight() - 1 - 2 * i, 18, 18);
                    }
                    g2.setColor(new Color(255, 200, 0, (int) (140 + pulse * 115)));
                    g2.setStroke(new BasicStroke(2.5f));
                    g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 18, 18);
                }
                g2.setColor(isTurn ? new Color(0x3A, 0x36, 0x2A) : BG_INPUT);
                g2.fillRoundRect(4, 4, getWidth() - 8, getHeight() - 8, 14, 14);
                // Fanned face-down stack (bobbing gently on the current turn)
                int shown = Math.max(1, Math.min(count, 7));
                int cw = 26;
                int chh = 38;
                int span = (shown - 1) * 10;
                int startX = (getWidth() - cw - span) / 2;
                int baseY = 12 + (isTurn ? (int) (Math.sin(boardAnimPhase * 2) * 2) : 0);
                for (int i = 0; i < shown; i++) {
                    double rot = Math.toRadians((i - (shown - 1) / 2.0) * 7);
                    int cx = startX + i * 10;
                    g2.rotate(rot, cx + cw / 2.0, baseY + chh);
                    paintUnoCardBack(g2, cx, baseY, cw, chh);
                    g2.rotate(-rot, cx + cw / 2.0, baseY + chh);
                }
                // Name + count
                g2.setFont(FONT_SMALL.deriveFont(Font.BOLD));
                g2.setColor(isMe ? ACCENT_HOVER : TEXT_PRIMARY);
                String label = (isMe ? "\u2B50 " : "") + name;
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(label, (getWidth() - fm.stringWidth(label)) / 2, getHeight() - 22);
                g2.setColor(TEXT_SECONDARY);
                String cards = count + (count == 1 ? " card" : " cards");
                g2.drawString(cards, (getWidth() - fm.stringWidth(cards)) / 2, getHeight() - 9);
                // Pulsing UNO! badge at one card
                if (count == 1) {
                    g2.setFont(new Font("SansSerif", Font.BOLD, 11));
                    String unoTxt = "UNO!";
                    FontMetrics ufm = g2.getFontMetrics();
                    int bw = ufm.stringWidth(unoTxt) + 10;
                    int bx = getWidth() - bw - 6;
                    g2.setColor(new Color(0xD3, 0x2F, 0x2F, (int) (170 + pulse * 85)));
                    g2.fillRoundRect(bx, 6, bw, 16, 10, 10);
                    g2.setColor(Color.WHITE);
                    g2.drawString(unoTxt, bx + 5, 18);
                }
                g2.dispose();
            }
        };
        seat.setOpaque(false);
        seat.setPreferredSize(new Dimension(118, 104));
        seat.setToolTipText(name + " holds " + count + (count == 1 ? " card" : " cards"));
        return seat;
    }

    /** Spinning circular arrow showing the direction of play. */
    private JPanel createUnoDirectionIndicator(boolean clockwise) {
        JPanel indicator = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                int cx = getWidth() / 2;
                int cy = getHeight() / 2 - 8;
                int r = 16;
                double spin = boardAnimPhase * (clockwise ? 1 : -1);
                g2.setColor(ACCENT);
                g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.rotate(spin, cx, cy);
                g2.drawArc(cx - r, cy - r, 2 * r, 2 * r, 20, 140);
                g2.drawArc(cx - r, cy - r, 2 * r, 2 * r, 200, 140);
                // Arrowheads
                for (int a : new int[]{20, 200}) {
                    double rad = Math.toRadians(-a);
                    int ax = cx + (int) (r * Math.cos(rad));
                    int ay = cy + (int) (r * Math.sin(rad));
                    int dir = clockwise ? 1 : -1;
                    g2.fillPolygon(new int[]{ax - 5 * dir, ax + 5 * dir, ax},
                            new int[]{ay - 2, ay - 2, ay + 7}, 3);
                }
                g2.rotate(-spin, cx, cy);
                g2.setFont(FONT_SMALL);
                g2.setColor(TEXT_SECONDARY);
                String lbl = clockwise ? "clockwise" : "reversed!";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(lbl, (getWidth() - fm.stringWidth(lbl)) / 2, getHeight() - 6);
                g2.dispose();
            }
        };
        indicator.setOpaque(false);
        indicator.setPreferredSize(new Dimension(72, 104));
        indicator.setToolTipText("Direction of play");
        return indicator;
    }

    /** The draw pile: a stack of card backs with the remaining count; click to draw. */
    private JPanel createUnoDrawPilePanel(int drawPileSize, boolean canDraw) {
        JPanel pile = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                int layers = Math.max(1, Math.min(drawPileSize, 4));
                int cw = getWidth() - 14;
                int chh = getHeight() - 34;
                for (int i = layers - 1; i >= 0; i--) {
                    paintUnoCardBack(g2, 4 + i * 3, 4 + i * 3, cw, chh);
                }
                if (canDraw) {
                    float pulse = 0.5f + 0.5f * (float) Math.sin(boardAnimPhase * 2);
                    g2.setColor(new Color(255, 255, 255, (int) (60 + pulse * 90)));
                    g2.setStroke(new BasicStroke(2.5f));
                    g2.drawRoundRect(4, 4, cw, chh, 14, 14);
                }
                g2.setFont(FONT_SMALL);
                g2.setColor(TEXT_SECONDARY);
                String label = drawPileSize + " left" + (canDraw ? " \u2014 click to draw" : "");
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(label, (getWidth() - fm.stringWidth(label)) / 2, getHeight() - 6);
                g2.dispose();
            }
        };
        pile.setOpaque(false);
        pile.setPreferredSize(new Dimension(110, 172));
        pile.setToolTipText("Draw pile");
        if (canDraw) {
            pile.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            pile.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    sendCommand("MOVE|DRAW");
                }
            });
        }
        return pile;
    }

    /** Paints a single face-down UNO card back. */
    private static void paintUnoCardBack(Graphics2D g2, int x, int y, int w, int h) {
        g2.setColor(new Color(0, 0, 0, 60));
        g2.fillRoundRect(x + 2, y + 2, w, h, 12, 12);
        g2.setPaint(new GradientPaint(x, y, new Color(0x1A, 0x1A, 0x2E),
                x, y + h, new Color(0x0F, 0x0F, 0x1E)));
        g2.fillRoundRect(x, y, w, h, 12, 12);
        g2.setColor(new Color(0xD3, 0x2F, 0x2F));
        g2.fillOval(x + w / 6, y + h / 4, w * 2 / 3, h / 2);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, Math.max(8, h / 5)));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString("UNO", x + (w - fm.stringWidth("UNO")) / 2,
                y + (h + fm.getAscent() - fm.getDescent()) / 2);
        g2.setColor(new Color(255, 255, 255, 90));
        g2.drawRoundRect(x + 2, y + 2, w - 4, h - 4, 10, 10);
    }

    private JPanel createUnoCardPanel(com.boardgame.model.Card card, String activeColor,
                                      boolean large, boolean playable) {
        int w = large ? 100 : 70;
        int h = large ? 150 : 105;
        int headroom = large ? 0 : 14;
        JPanel panel = new JPanel() {
            private float lift;

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Object targetProp = getClientProperty("liftTarget");
                float target = targetProp instanceof Number n ? n.floatValue() : 0f;
                lift += (target - lift) * CARD_LIFT_EASING; // eased by the shared animation clock
                g2.translate(0, headroom - lift);
                Color cardColor = unoAwtColor(card.color());
                if (card.color() == com.boardgame.model.Card.Color.WILD && activeColor != null && !activeColor.isEmpty()) {
                    cardColor = unoAwtColor(com.boardgame.model.Card.Color.valueOf(activeColor));
                }
                // Playable glow
                if (playable) {
                    float pulse = 0.5f + 0.5f * (float) Math.sin(boardAnimPhase * 2);
                    g2.setColor(new Color(255, 255, 255, (int) (70 + pulse * 110)));
                    g2.setStroke(new BasicStroke(3f));
                    g2.drawRoundRect(-1, -1, getWidth() - 2, getHeight() - headroom - 2, 18, 18);
                }
                // Shadow (deepens as the card lifts)
                g2.setColor(new Color(0, 0, 0, 60 + (int) (lift * 5)));
                g2.fillRoundRect(3, 3 + (int) lift, getWidth() - 3, getHeight() - headroom - 3, 16, 16);
                // Card body
                g2.setColor(playable ? cardColor : dimmed(cardColor, playableDimming()));
                g2.fillRoundRect(0, 0, getWidth() - 4, getHeight() - headroom - 4, 16, 16);
                // White ellipse
                g2.setColor(new Color(255, 255, 255, 100));
                g2.fillOval(getWidth() / 6, (getHeight() - headroom) / 4,
                        getWidth() * 2 / 3, (getHeight() - headroom) / 2);
                // Label
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.BOLD, large ? 22 : 16));
                String label = card.value().label();
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - 4 - fm.stringWidth(label)) / 2;
                int y = (getHeight() - headroom - 4 + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(label, x, y);
                // Corner label
                g2.setFont(new Font("SansSerif", Font.BOLD, large ? 12 : 9));
                g2.drawString(label, 6, 16);
                g2.dispose();
            }

            private float playableDimming() {
                return large ? 0f : 0.35f;
            }
        };
        panel.setPreferredSize(new Dimension(w, h + headroom));
        panel.setOpaque(false);
        return panel;
    }

    private static Color dimmed(Color c, float amount) {
        return new Color((int) (c.getRed() * (1 - amount)),
                (int) (c.getGreen() * (1 - amount)),
                (int) (c.getBlue() * (1 - amount)));
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

    /**
     * Modal color picker for wild cards: four big glossy swatches that light
     * up on hover. Returns "RED"/"YELLOW"/"GREEN"/"BLUE", or null if dismissed.
     */
    private String promptWildColor() {
        final String[] chosen = {null};
        javax.swing.JDialog dialog = new javax.swing.JDialog(this, "Pick a color", true);
        dialog.setUndecorated(true);
        JPanel content = new JPanel(new BorderLayout(0, 8)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_CARD);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
                g2.setColor(ACCENT);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 24, 24);
                g2.dispose();
            }
        };
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        JLabel title = styledLabel("\uD83C\uDF08 Choose a color", FONT_HEADER, TEXT_PRIMARY);
        title.setHorizontalAlignment(SwingConstants.CENTER);
        content.add(title, BorderLayout.NORTH);

        JPanel swatches = new JPanel(new GridLayout(2, 2, 10, 10));
        swatches.setOpaque(false);
        for (com.boardgame.model.Card.Color c : List.of(
                com.boardgame.model.Card.Color.RED, com.boardgame.model.Card.Color.YELLOW,
                com.boardgame.model.Card.Color.GREEN, com.boardgame.model.Card.Color.BLUE)) {
            Color awt = unoAwtColor(c);
            final boolean[] hover = {false};
            JPanel swatch = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
                    int inset = hover[0] ? 0 : 4;
                    g2.setColor(new Color(0, 0, 0, 70));
                    g2.fillRoundRect(inset + 3, inset + 3, getWidth() - 2 * inset - 3,
                            getHeight() - 2 * inset - 3, 18, 18);
                    g2.setPaint(new GradientPaint(0, inset, awt.brighter(),
                            0, getHeight() - inset, awt));
                    g2.fillRoundRect(inset, inset, getWidth() - 2 * inset,
                            getHeight() - 2 * inset, 18, 18);
                    if (hover[0]) {
                        g2.setColor(new Color(255, 255, 255, 200));
                        g2.setStroke(new BasicStroke(3f));
                        g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 18, 18);
                    }
                    g2.setColor(Color.WHITE);
                    g2.setFont(FONT_BUTTON);
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(c.name(), (getWidth() - fm.stringWidth(c.name())) / 2,
                            (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                    g2.dispose();
                }
            };
            swatch.setOpaque(false);
            swatch.setPreferredSize(new Dimension(110, 80));
            swatch.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            swatch.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    chosen[0] = c.name();
                    dialog.dispose();
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    hover[0] = true;
                    swatch.repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover[0] = false;
                    swatch.repaint();
                }
            });
            swatches.add(swatch);
        }
        content.add(swatches, BorderLayout.CENTER);

        JButton cancel = accentButton("Cancel");
        cancel.addActionListener(e -> dialog.dispose());
        JPanel cancelRow = new JPanel(new FlowLayout(FlowLayout.CENTER));
        cancelRow.setOpaque(false);
        cancelRow.add(cancel);
        content.add(cancelRow, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.setBackground(new Color(0, 0, 0, 0));
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
        return chosen[0];
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
