package com.boardgame.games.puttputt;

import com.boardgame.games.BoardGame;
import com.boardgame.protocol.Protocol;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Putt Putt (mini golf) — a physics based game with animated shots, played
 * over a configurable round of holes (nine by default).
 *
 * <p>Each hole is a procedurally generated green with wall obstacles, one cup
 * and (optionally) collectible power-ups. Players take turns hitting their
 * ball with an angle (degrees, 0 = right, counter-clockwise) and power
 * (1-100). The server simulates the shot — friction, wall bounces, course
 * boundary bounces, power-up pickups — and publishes the sampled ball path in
 * the snapshot so clients can animate the roll. When everyone sinks the ball
 * the round moves to the next hole; fewest total strokes after the last hole
 * wins.
 *
 * <p>Power-ups are collected by rolling the ball over them:
 * <ul>
 *   <li>{@code BLAST} — your next shot gets +50% power.</li>
 *   <li>{@code MAGNET} — the cup doubles in size for your next shot.</li>
 *   <li>{@code SAND} — every opponent's next shot loses half its power.</li>
 *   <li>{@code WOBBLE} — every opponent's next shot veers off randomly.</li>
 * </ul>
 *
 * <p>Rules (hole count, difficulty, course size, power-ups, max players) are
 * configured at room creation, see {@link PuttPuttRules}.
 *
 * <p>Moves: {@code SHOT|angle|power}
 *
 * <p>Snapshot (pipe-delimited, after the standard started/finished/current
 * fields): course dimensions, hole, walls, balls (with stroke counts), the
 * shooter and sampled path of the most recent shot, hole progress
 * ({@code current/total}), power-ups on the course, pending player effects,
 * and a status message.
 */
public final class PuttPuttGame implements BoardGame {

    public static final int MAX_STROKES = 10;

    private static final double FRICTION = 4.0;      // units/s^2 deceleration
    private static final double DT = 1.0 / 60;       // simulation step
    private static final double MAX_SPEED = 60;      // speed at power 100
    private static final double SINK_SPEED = 18;     // max speed at which the ball drops
    private static final double PICKUP_RADIUS = 2.5; // power-up collection radius
    private static final int POWER_UPS_PER_HOLE = 3;
    private static final double TEE_X = 8;

    /** Axis-aligned wall obstacle. */
    public record Wall(double x, double y, double w, double h) {
    }

    /** Collectible power-up types. */
    public enum PowerUpType {
        /** Beneficial: your next shot gets +50% power. */
        BLAST,
        /** Beneficial: the cup doubles in size for your next shot. */
        MAGNET,
        /** Negative: every opponent's next shot loses half its power. */
        SAND,
        /** Negative: every opponent's next shot veers off randomly. */
        WOBBLE
    }

    private record PowerUp(PowerUpType type, double x, double y) {
    }

    private static final class Ball {
        double x = TEE_X;
        double y;
        int holeStrokes;
        int totalStrokes;
        boolean holed;
        PowerUpType pendingEffect;
    }

    private final PuttPuttRules rules;
    private final Random random;
    private final double width;
    private final double height;

    private final LinkedHashMap<String, Ball> balls = new LinkedHashMap<>();
    private final List<Wall> walls = new ArrayList<>();
    private final List<PowerUp> powerUps = new ArrayList<>();
    private final List<double[]> lastPath = new ArrayList<>();
    private double holeX;
    private double holeY;
    private double holeRadius;
    private int currentHole = 1;
    private String lastShooter = "";
    private int currentIndex;
    private boolean started;
    private boolean finished;
    private String winner;
    private String message = "Waiting for players";

    public PuttPuttGame() {
        this(PuttPuttRules.defaults());
    }

    public PuttPuttGame(PuttPuttRules rules) {
        this(rules, new Random());
    }

    PuttPuttGame(PuttPuttRules rules, Random random) {
        this.rules = rules;
        this.random = random;
        this.width = rules.courseSize().width();
        this.height = rules.courseSize().height();
        layoutHole();
    }

    public double width() {
        return width;
    }

    public double height() {
        return height;
    }

    public PuttPuttRules rules() {
        return rules;
    }

    @Override
    public synchronized void addPlayer(String playerId) {
        if (started || balls.size() >= maxPlayers()) {
            throw new IllegalStateException("Game is full or already started");
        }
        if (balls.containsKey(playerId)) {
            throw new IllegalArgumentException("Player is already connected");
        }
        Ball ball = new Ball();
        ball.y = teeY(balls.size());
        balls.put(playerId, ball);
        message = playerId + " joined";
        if (balls.size() >= minPlayers()) {
            started = true;
            message = "Hole 1 of " + rules.holes() + " \u2014 "
                    + new ArrayList<>(balls.keySet()).get(0) + " tees off";
        }
    }

    @Override
    public synchronized void removePlayer(String playerId) {
        List<String> order = new ArrayList<>(balls.keySet());
        int removedIndex = order.indexOf(playerId);
        if (removedIndex < 0) {
            return;
        }
        balls.remove(playerId);
        if (started && balls.size() < minPlayers()) {
            finished = true;
            message = "Game ended: not enough players";
            return;
        }
        if (!balls.isEmpty()
                && (removedIndex < currentIndex || currentIndex >= balls.size())) {
            currentIndex = Math.floorMod(currentIndex - 1, balls.size());
        }
        skipHoledPlayers();
    }

    @Override
    public synchronized void move(String playerId, String moveData) {
        if (!started || finished) {
            throw new IllegalStateException("Game is not active");
        }
        if (!currentPlayer().equals(playerId)) {
            throw new IllegalStateException("It is not your turn");
        }
        String[] parts = moveData.split("\\|");
        if (parts.length != 3 || !parts[0].equals("SHOT")) {
            throw new IllegalArgumentException("Usage: SHOT|angle|power");
        }
        double angle;
        double power;
        try {
            angle = Double.parseDouble(parts[1]);
            power = Double.parseDouble(parts[2]);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Angle and power must be numbers");
        }
        if (power < 1 || power > 100) {
            throw new IllegalArgumentException("Power must be 1-100");
        }
        simulateShot(playerId, angle, power);
        advanceTurn();
    }

    private void simulateShot(String playerId, double angleDeg, double power) {
        Ball ball = balls.get(playerId);
        ball.holeStrokes++;
        ball.totalStrokes++;
        lastShooter = playerId;
        lastPath.clear();
        String effectNote = "";
        double effectiveHoleRadius = holeRadius;
        PowerUpType effect = ball.pendingEffect;
        ball.pendingEffect = null;
        if (effect != null) {
            switch (effect) {
                case BLAST -> {
                    power = Math.min(150, power * 1.5);
                    effectNote = " [BLAST boost!]";
                }
                case MAGNET -> {
                    effectiveHoleRadius = holeRadius * 2;
                    effectNote = " [MAGNET cup!]";
                }
                case SAND -> {
                    power = Math.max(1, power * 0.5);
                    effectNote = " [stuck in SAND]";
                }
                case WOBBLE -> {
                    angleDeg += (random.nextDouble() * 2 - 1) * 20;
                    effectNote = " [WOBBLE veer!]";
                }
                default -> {
                }
            }
        }
        double angleRad = Math.toRadians(angleDeg);
        double speed = MAX_SPEED * power / 100;
        double vx = Math.cos(angleRad) * speed;
        double vy = -Math.sin(angleRad) * speed;
        lastPath.add(new double[]{ball.x, ball.y});
        StringBuilder pickups = new StringBuilder();
        int steps = 0;
        boolean sunk = false;
        while ((vx * vx + vy * vy) > 0.04 && steps < 3000) {
            double nx = ball.x + vx * DT;
            double ny = ball.y + vy * DT;
            // Boundary bounces
            if (nx < 1 || nx > width - 1) {
                vx = -vx * 0.8;
                nx = clamp(nx, 1, width - 1);
            }
            if (ny < 1 || ny > height - 1) {
                vy = -vy * 0.8;
                ny = clamp(ny, 1, height - 1);
            }
            // Wall bounces
            for (Wall wall : walls) {
                if (nx >= wall.x() && nx <= wall.x() + wall.w()
                        && ny >= wall.y() && ny <= wall.y() + wall.h()) {
                    boolean fromLeft = ball.x < wall.x();
                    boolean fromRight = ball.x > wall.x() + wall.w();
                    if (fromLeft || fromRight) {
                        vx = -vx * 0.8;
                        nx = fromLeft ? wall.x() - 0.01 : wall.x() + wall.w() + 0.01;
                    } else {
                        vy = -vy * 0.8;
                        ny = ball.y < wall.y() ? wall.y() - 0.01 : wall.y() + wall.h() + 0.01;
                    }
                }
            }
            ball.x = nx;
            ball.y = ny;
            // Power-up pickups
            for (int i = powerUps.size() - 1; i >= 0; i--) {
                PowerUp powerUp = powerUps.get(i);
                double px = ball.x - powerUp.x();
                double py = ball.y - powerUp.y();
                if (px * px + py * py <= PICKUP_RADIUS * PICKUP_RADIUS) {
                    powerUps.remove(i);
                    collectPowerUp(playerId, powerUp.type());
                    pickups.append(" +").append(powerUp.type());
                }
            }
            // Hole capture
            double dx = ball.x - holeX;
            double dy = ball.y - holeY;
            double curSpeed = Math.sqrt(vx * vx + vy * vy);
            if (dx * dx + dy * dy <= effectiveHoleRadius * effectiveHoleRadius
                    && curSpeed <= SINK_SPEED) {
                ball.x = holeX;
                ball.y = holeY;
                ball.holed = true;
                lastPath.add(new double[]{ball.x, ball.y});
                message = playerId + " sinks it in " + ball.holeStrokes + "!"
                        + effectNote + pickups;
                sunk = true;
                break;
            }
            // Friction
            if (curSpeed > 0) {
                double decel = FRICTION * DT;
                double factor = Math.max(0, (curSpeed - decel) / curSpeed);
                vx *= factor;
                vy *= factor;
            }
            if (steps % 3 == 0) {
                lastPath.add(new double[]{ball.x, ball.y});
            }
            steps++;
        }
        if (sunk) {
            return;
        }
        lastPath.add(new double[]{ball.x, ball.y});
        if (ball.holeStrokes >= MAX_STROKES) {
            ball.holed = true;
            message = playerId + " maxed out at " + MAX_STROKES + " strokes" + effectNote + pickups;
        } else {
            message = playerId + " rolls to a stop (" + ball.holeStrokes
                    + (ball.holeStrokes == 1 ? " stroke)" : " strokes)") + effectNote + pickups;
        }
    }

    /** Applies a collected power-up: good ones stick to the collector, bad ones to everyone else. */
    private void collectPowerUp(String playerId, PowerUpType type) {
        switch (type) {
            case BLAST, MAGNET -> balls.get(playerId).pendingEffect = type;
            case SAND, WOBBLE -> balls.forEach((name, other) -> {
                if (!name.equals(playerId)) {
                    other.pendingEffect = type;
                }
            });
            default -> {
            }
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void advanceTurn() {
        if (balls.values().stream().allMatch(b -> b.holed)) {
            if (currentHole < rules.holes()) {
                currentHole++;
                layoutHole();
                int seat = 0;
                for (Ball ball : balls.values()) {
                    ball.x = TEE_X;
                    ball.y = teeY(seat++);
                    ball.holeStrokes = 0;
                    ball.holed = false;
                }
                currentIndex = 0;
                lastPath.clear();
                lastShooter = "";
                message = message + " \u2014 Hole " + currentHole + " of " + rules.holes() + "!";
                return;
            }
            finished = true;
            winner = balls.entrySet().stream()
                    .min((a, b) -> Integer.compare(a.getValue().totalStrokes,
                            b.getValue().totalStrokes))
                    .map(Map.Entry::getKey)
                    .orElse(null);
            message = message + " \u2014 " + winner + " wins the round!";
            return;
        }
        advance();
        skipHoledPlayers();
    }

    /** Generates the layout (cup, walls, power-ups) for the current hole. */
    private void layoutHole() {
        walls.clear();
        powerUps.clear();
        holeRadius = rules.difficulty().holeRadius();
        holeX = width * (0.72 + random.nextDouble() * 0.18);
        holeY = height * (0.2 + random.nextDouble() * 0.6);
        int wallCount = rules.difficulty().wallCount();
        for (int i = 0; i < wallCount; i++) {
            Wall wall = randomWall();
            if (wall != null) {
                walls.add(wall);
            }
        }
        if (rules.powerUps()) {
            for (int i = 0; i < POWER_UPS_PER_HOLE; i++) {
                double[] spot = randomFreeSpot();
                if (spot != null) {
                    PowerUpType type = PowerUpType.values()[
                            random.nextInt(PowerUpType.values().length)];
                    powerUps.add(new PowerUp(type, spot[0], spot[1]));
                }
            }
        }
    }

    /** Picks a wall that blocks neither the tee area nor the cup. */
    private Wall randomWall() {
        for (int attempt = 0; attempt < 20; attempt++) {
            boolean vertical = random.nextBoolean();
            double thickness = 3 + random.nextDouble() * 2;
            double length = height * (0.3 + random.nextDouble() * 0.35);
            double x;
            double y;
            double w;
            double h;
            if (vertical) {
                x = width * (0.2 + random.nextDouble() * 0.5);
                y = random.nextBoolean() ? 0 : height - length;
                w = thickness;
                h = length;
            } else {
                x = width * (0.2 + random.nextDouble() * 0.4);
                y = height * (0.15 + random.nextDouble() * 0.6);
                w = length;
                h = thickness;
            }
            Wall wall = new Wall(x, y, w, h);
            if (!intersectsCircle(wall, holeX, holeY, holeRadius + 4)
                    && wall.x() > TEE_X + 8) {
                return wall;
            }
        }
        return null;
    }

    /** Picks a spot in the fairway that is clear of walls, the tee and the cup. */
    private double[] randomFreeSpot() {
        for (int attempt = 0; attempt < 20; attempt++) {
            double x = width * (0.2 + random.nextDouble() * 0.55);
            double y = height * (0.1 + random.nextDouble() * 0.8);
            double dx = x - holeX;
            double dy = y - holeY;
            if (dx * dx + dy * dy < 64) {
                continue;
            }
            boolean clear = true;
            for (Wall wall : walls) {
                if (x >= wall.x() - PICKUP_RADIUS && x <= wall.x() + wall.w() + PICKUP_RADIUS
                        && y >= wall.y() - PICKUP_RADIUS && y <= wall.y() + wall.h() + PICKUP_RADIUS) {
                    clear = false;
                    break;
                }
            }
            if (clear) {
                return new double[]{x, y};
            }
        }
        return null;
    }

    private static boolean intersectsCircle(Wall wall, double cx, double cy, double radius) {
        double nearestX = clamp(cx, wall.x(), wall.x() + wall.w());
        double nearestY = clamp(cy, wall.y(), wall.y() + wall.h());
        double dx = cx - nearestX;
        double dy = cy - nearestY;
        return dx * dx + dy * dy <= radius * radius;
    }

    private double teeY(int seat) {
        return height / 3 + seat * height / 9;
    }

    private void skipHoledPlayers() {
        if (balls.isEmpty() || finished) {
            return;
        }
        int guard = 0;
        while (balls.get(currentPlayer()).holed && guard++ < balls.size()) {
            advance();
        }
    }

    private void advance() {
        currentIndex = Math.floorMod(currentIndex + 1, balls.size());
    }

    private String currentPlayer() {
        return new ArrayList<>(balls.keySet()).get(currentIndex);
    }

    @Override
    public synchronized String snapshot(String playerId) {
        StringBuilder sb = new StringBuilder();
        sb.append(started).append("|");
        sb.append(finished).append("|");
        sb.append(Protocol.encode(started && !finished ? currentPlayer() : "")).append("|");
        sb.append(fmt(width)).append(",").append(fmt(height)).append("|");
        sb.append(fmt(holeX)).append(",").append(fmt(holeY)).append(",")
                .append(fmt(holeRadius)).append("|");
        sb.append(walls.stream()
                .map(w -> fmt(w.x()) + ":" + fmt(w.y()) + ":" + fmt(w.w()) + ":" + fmt(w.h()))
                .reduce((a, b) -> a + ";" + b).orElse("")).append("|");
        StringBuilder ballsField = new StringBuilder();
        balls.forEach((name, ball) -> {
            if (ballsField.length() > 0) {
                ballsField.append(",");
            }
            ballsField.append(Protocol.encode(name)).append(":").append(fmt(ball.x)).append(":")
                    .append(fmt(ball.y)).append(":").append(ball.totalStrokes).append(":")
                    .append(ball.holed).append(":").append(ball.holeStrokes);
        });
        sb.append(ballsField).append("|");
        sb.append(Protocol.encode(lastShooter)).append("|");
        sb.append(lastPath.stream()
                .map(p -> fmt(p[0]) + ":" + fmt(p[1]))
                .reduce((a, b) -> a + ";" + b).orElse("")).append("|");
        sb.append(currentHole).append("/").append(rules.holes()).append("|");
        sb.append(powerUps.stream()
                .map(p -> p.type() + ":" + fmt(p.x()) + ":" + fmt(p.y()))
                .reduce((a, b) -> a + ";" + b).orElse("")).append("|");
        StringBuilder effectsField = new StringBuilder();
        balls.forEach((name, ball) -> {
            if (ball.pendingEffect != null) {
                if (effectsField.length() > 0) {
                    effectsField.append(",");
                }
                effectsField.append(Protocol.encode(name)).append(":").append(ball.pendingEffect);
            }
        });
        sb.append(effectsField).append("|");
        sb.append(Protocol.encode(message));
        return sb.toString();
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    @Override
    public int minPlayers() {
        return 2;
    }

    @Override
    public int maxPlayers() {
        return rules.maxPlayers();
    }

    @Override
    public synchronized boolean isStarted() {
        return started;
    }

    @Override
    public synchronized boolean isFinished() {
        return finished;
    }

    @Override
    public synchronized String winner() {
        return winner;
    }

    @Override
    public synchronized List<String> players() {
        return new ArrayList<>(balls.keySet());
    }

    @Override
    public String gameType() {
        return "PUTTPUTT";
    }
}
