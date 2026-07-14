package com.boardgame.uno.client;

import com.boardgame.uno.model.Card;
import com.boardgame.uno.model.Card.Color;
import com.boardgame.uno.network.LanDiscovery;
import com.boardgame.uno.protocol.Protocol;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class UnoClient extends JFrame {
    private final JLabel status = new JLabel("Connecting…", JLabel.CENTER);
    private final JLabel players = new JLabel("", JLabel.CENTER);
    private final JPanel hand = new JPanel();
    private final CardCanvas discard = new CardCanvas();
    private final JButton draw = new JButton("Draw card");
    private final JComboBox<Color> wildColor =
            new JComboBox<>(new Color[]{Color.RED, Color.YELLOW, Color.GREEN, Color.BLUE});
    private ServerConnection connection;
    private final String playerName;
    private String topCardToken = "";

    private UnoClient(String host, int port, String name) {
        super("UNO — " + name);
        playerName = name;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(850, 600));
        setLocationByPlatform(true);
        buildUi();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (IOException ignored) {
                        // The socket is already closed.
                    }
                }
            }
        });
        connect(host, port);
    }

    private void buildUi() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        status.setFont(status.getFont().deriveFont(Font.BOLD, 18f));
        header.add(status);
        header.add(players);

        JPanel table = new JPanel(new BorderLayout());
        table.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
        table.add(discard, BorderLayout.CENTER);
        JPanel controls = new JPanel();
        draw.addActionListener(event -> connection.draw());
        controls.add(draw);
        controls.add(new JLabel("Wild color:"));
        controls.add(wildColor);
        table.add(controls, BorderLayout.SOUTH);

        hand.setBorder(BorderFactory.createTitledBorder("Your hand"));
        JScrollPane handScroll = new JScrollPane(hand);
        handScroll.setPreferredSize(new Dimension(800, 190));

        add(header, BorderLayout.NORTH);
        add(table, BorderLayout.CENTER);
        add(handScroll, BorderLayout.SOUTH);
    }

    private void connect(String host, int port) {
        try {
            connection = new ServerConnection(host, port);
            connection.listen(line -> SwingUtilities.invokeLater(() -> receive(line)));
            connection.join(playerName);
        } catch (IOException exception) {
            status.setText("Unable to connect: " + exception.getMessage());
            draw.setEnabled(false);
        }
    }

    private void receive(String line) {
        String[] parts = line.split("\\|", -1);
        try {
            if ("ERROR".equals(parts[0]) && parts.length == 2) {
                status.setText("Server: " + Protocol.decode(parts[1]));
            } else if ("STATE".equals(parts[0]) && parts.length == 10) {
                render(parts);
            }
        } catch (IllegalArgumentException exception) {
            status.setText("Received invalid data from server");
        }
    }

    private void render(String[] parts) {
        boolean started = Boolean.parseBoolean(parts[1]);
        boolean finished = Boolean.parseBoolean(parts[2]);
        String currentPlayer = Protocol.decode(parts[3]);
        status.setText(Protocol.decode(parts[9]));
        players.setText(playerSummary(parts[7], currentPlayer));
        boolean myTurn = started && !finished && playerName.equals(currentPlayer);
        draw.setEnabled(myTurn);
        hand.removeAll();
        List<Card> cards = parseCards(parts[6]);
        for (int i = 0; i < cards.size(); i++) {
            int index = i;
            Card card = cards.get(i);
            JButton button = new JButton("<html><b>" + card.value().label() + "</b><br>"
                    + card.color() + "</html>");
            button.setPreferredSize(new Dimension(86, 120));
            button.setBackground(javaColor(card.color()));
            button.setForeground(card.color() == Color.YELLOW
                    ? java.awt.Color.BLACK : java.awt.Color.WHITE);
            button.setEnabled(myTurn);
            button.addActionListener(event -> connection.play(index,
                    card.color() == Color.WILD ? ((Color) wildColor.getSelectedItem()).name() : null));
            hand.add(button);
        }
        hand.revalidate();
        hand.repaint();
        if (!parts[4].isEmpty() && !parts[4].equals(topCardToken)) {
            topCardToken = parts[4];
            discard.animate(Card.fromToken(parts[4]), Color.valueOf(parts[5]));
        }
    }

    private static List<Card> parseCards(String tokens) {
        List<Card> cards = new ArrayList<>();
        if (!tokens.isEmpty()) {
            for (String token : tokens.split(",")) {
                cards.add(Card.fromToken(token));
            }
        }
        return cards;
    }

    private static String playerSummary(String encoded, String current) {
        List<String> summaries = new ArrayList<>();
        if (!encoded.isEmpty()) {
            for (String player : encoded.split(",")) {
                String[] parts = player.split(":", 2);
                String name = Protocol.decode(parts[0]);
                summaries.add((name.equals(current) ? "▶ " : "") + name + " (" + parts[1] + ")");
            }
        }
        return String.join("     ", summaries);
    }

    private static java.awt.Color javaColor(Color color) {
        return switch (color) {
            case RED -> new java.awt.Color(210, 45, 45);
            case YELLOW -> new java.awt.Color(245, 196, 48);
            case GREEN -> new java.awt.Color(45, 160, 80);
            case BLUE -> new java.awt.Color(45, 105, 200);
            case WILD -> new java.awt.Color(45, 45, 45);
        };
    }

    private static final class CardCanvas extends JPanel {
        private Card card;
        private Color activeColor;
        private double progress = 1;

        private CardCanvas() {
            setPreferredSize(new Dimension(300, 270));
        }

        private void animate(Card nextCard, Color nextColor) {
            card = nextCard;
            activeColor = nextColor;
            progress = 0;
            Timer timer = new Timer(16, null);
            timer.addActionListener(event -> {
                progress = Math.min(1, progress + 0.055);
                repaint();
                if (progress >= 1) {
                    timer.stop();
                }
            });
            timer.start();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (card == null) {
                return;
            }
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double eased = 1 - Math.pow(1 - progress, 3);
            int width = 140;
            int height = 210;
            int x = (int) (-width + eased * (getWidth() + width) / 2);
            int y = (getHeight() - height) / 2;
            g.rotate((1 - eased) * -0.25, x + width / 2.0, y + height / 2.0);
            g.setColor(javaColor(card.color() == Color.WILD ? activeColor : card.color()));
            g.fillRoundRect(x, y, width, height, 24, 24);
            g.setColor(java.awt.Color.WHITE);
            g.setStroke(new BasicStroke(5));
            g.drawRoundRect(x, y, width, height, 24, 24);
            g.setFont(getFont().deriveFont(Font.BOLD, 30f));
            String label = card.value().label();
            int textWidth = g.getFontMetrics().stringWidth(label);
            g.drawString(label, x + (width - textWidth) / 2, y + height / 2 + 10);
            g.dispose();
        }
    }

    public static void main(String[] args) {
        List<LanDiscovery.Server> servers = args.length > 0
                ? List.of(new LanDiscovery.Server(
                        args[0], args.length > 1 ? Integer.parseInt(args[1]) : 8888))
                : discoverServers();
        SwingUtilities.invokeLater(() -> {
            LanDiscovery.Server selected = chooseServer(servers);
            if (selected == null) {
                return;
            }
            String name = JOptionPane.showInputDialog(null, "Player name:", "Join UNO",
                    JOptionPane.QUESTION_MESSAGE);
            if (name != null && !name.isBlank()) {
                new UnoClient(selected.host(), selected.port(), name.strip()).setVisible(true);
            }
        });
    }

    private static List<LanDiscovery.Server> discoverServers() {
        try {
            List<LanDiscovery.Server> servers = LanDiscovery.discover(LanDiscovery.DEFAULT_PORT, 750);
            if (!servers.isEmpty()) {
                return servers;
            }
        } catch (IOException ignored) {
            // Fall back to a server running on this computer.
        }
        return List.of(new LanDiscovery.Server("localhost", 8888));
    }

    private static LanDiscovery.Server chooseServer(List<LanDiscovery.Server> servers) {
        if (servers.size() == 1) {
            return servers.get(0);
        }
        return (LanDiscovery.Server) JOptionPane.showInputDialog(null,
                "Choose a game server:", "Join UNO", JOptionPane.QUESTION_MESSAGE,
                null, servers.toArray(), servers.get(0));
    }
}
