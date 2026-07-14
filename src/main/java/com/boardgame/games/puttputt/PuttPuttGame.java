package com.boardgame.games.puttputt;

import com.boardgame.games.BoardGame;
import com.boardgame.protocol.Protocol;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Putt Putt (mini golf) — a physics based game with animated shots.
 *
 * <p>The course is a {@value #WIDTH}x{@value #HEIGHT} unit green with wall
 * obstacles and one hole. Players take turns hitting their ball with an angle
 * (degrees, 0 = right, counter-clockwise) and power (1-100). The server
 * simulates the shot — friction, wall bounces, course boundary bounces — and
 * publishes the sampled ball path in the snapshot so clients can animate the
 * roll. First everyone sinks the ball; fewest strokes wins.
 *
 * <p>Moves: {@code SHOT|angle|power}
 *
 * <p>Snapshot (pipe-delimited, after the standard started/finished/current
 * fields): course dimensions, hole, walls, balls (with stroke counts), the
 * shooter and sampled path of the most recent shot, and a status message.
 */
public final class PuttPuttGame implements BoardGame {

    public static final double WIDTH = 100;
    public static final double HEIGHT = 60;
    public static final double HOLE_X = 85;
    public static final double HOLE_Y = 30;
    public static final double HOLE_RADIUS = 2.2;
    public static final int MAX_STROKES = 10;

    /** Axis-aligned wall obstacle. */
    public record Wall(double x, double y, double w, double h) {
    }

    /** Rectangular obstacles the ball bounces off. */
    public static final List<Wall> WALLS = List.of(
            new Wall(30, 0, 4, 38),
            new Wall(55, 22, 4, 38),
            new Wall(70, 0, 3, 14));

    private static final double FRICTION = 4.0;      // units/s^2 deceleration
    private static final double DT = 1.0 / 60;       // simulation step
    private static final double MAX_SPEED = 60;      // speed at power 100
    private static final double SINK_SPEED = 18;     // max speed at which the ball drops

    private static final class Ball {
        double x = 8;
        double y = 30;
        int strokes;
        boolean holed;
    }

    private final LinkedHashMap<String, Ball> balls = new LinkedHashMap<>();
    private final List<double[]> lastPath = new ArrayList<>();
    private String lastShooter = "";
    private int currentIndex;
    private boolean started;
    private boolean finished;
    private String winner;
    private String message = "Waiting for players";

    @Override
    public synchronized void addPlayer(String playerId) {
        if (started || balls.size() >= maxPlayers()) {
            throw new IllegalStateException("Game is full or already started");
        }
        if (balls.containsKey(playerId)) {
            throw new IllegalArgumentException("Player is already connected");
        }
        Ball ball = new Ball();
        ball.y = 20 + balls.size() * 7;
        balls.put(playerId, ball);
        message = playerId + " joined";
        if (balls.size() >= minPlayers()) {
            started = true;
            message = new ArrayList<>(balls.keySet()).get(0) + " tees off";
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
        simulateShot(playerId, Math.toRadians(angle), power);
        advanceTurn();
    }

    private void simulateShot(String playerId, double angleRad, double power) {
        Ball ball = balls.get(playerId);
        ball.strokes++;
        lastShooter = playerId;
        lastPath.clear();
        double speed = MAX_SPEED * power / 100;
        double vx = Math.cos(angleRad) * speed;
        double vy = -Math.sin(angleRad) * speed;
        lastPath.add(new double[]{ball.x, ball.y});
        int steps = 0;
        while ((vx * vx + vy * vy) > 0.04 && steps < 3000) {
            double nx = ball.x + vx * DT;
            double ny = ball.y + vy * DT;
            // Boundary bounces
            if (nx < 1 || nx > WIDTH - 1) {
                vx = -vx * 0.8;
                nx = clamp(nx, 1, WIDTH - 1);
            }
            if (ny < 1 || ny > HEIGHT - 1) {
                vy = -vy * 0.8;
                ny = clamp(ny, 1, HEIGHT - 1);
            }
            // Wall bounces
            for (Wall wall : WALLS) {
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
            // Hole capture
            double dx = ball.x - HOLE_X;
            double dy = ball.y - HOLE_Y;
            double curSpeed = Math.sqrt(vx * vx + vy * vy);
            if (dx * dx + dy * dy <= HOLE_RADIUS * HOLE_RADIUS && curSpeed <= SINK_SPEED) {
                ball.x = HOLE_X;
                ball.y = HOLE_Y;
                ball.holed = true;
                lastPath.add(new double[]{ball.x, ball.y});
                message = playerId + " sinks it in " + ball.strokes + "!";
                return;
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
        lastPath.add(new double[]{ball.x, ball.y});
        if (ball.strokes >= MAX_STROKES) {
            ball.holed = true;
            message = playerId + " maxed out at " + MAX_STROKES + " strokes";
        } else {
            message = playerId + " rolls to a stop (" + ball.strokes
                    + (ball.strokes == 1 ? " stroke)" : " strokes)");
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void advanceTurn() {
        if (balls.values().stream().allMatch(b -> b.holed)) {
            finished = true;
            winner = balls.entrySet().stream()
                    .min((a, b) -> Integer.compare(a.getValue().strokes, b.getValue().strokes))
                    .map(Map.Entry::getKey)
                    .orElse(null);
            message = message + " \u2014 " + winner + " wins the round!";
            return;
        }
        advance();
        skipHoledPlayers();
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
        sb.append(fmt(WIDTH)).append(",").append(fmt(HEIGHT)).append("|");
        sb.append(fmt(HOLE_X)).append(",").append(fmt(HOLE_Y)).append(",")
                .append(fmt(HOLE_RADIUS)).append("|");
        sb.append(WALLS.stream()
                .map(w -> fmt(w.x()) + ":" + fmt(w.y()) + ":" + fmt(w.w()) + ":" + fmt(w.h()))
                .reduce((a, b) -> a + ";" + b).orElse("")).append("|");
        StringBuilder ballsField = new StringBuilder();
        balls.forEach((name, ball) -> {
            if (ballsField.length() > 0) {
                ballsField.append(",");
            }
            ballsField.append(Protocol.encode(name)).append(":").append(fmt(ball.x)).append(":")
                    .append(fmt(ball.y)).append(":").append(ball.strokes).append(":")
                    .append(ball.holed);
        });
        sb.append(ballsField).append("|");
        sb.append(Protocol.encode(lastShooter)).append("|");
        sb.append(lastPath.stream()
                .map(p -> fmt(p[0]) + ":" + fmt(p[1]))
                .reduce((a, b) -> a + ";" + b).orElse("")).append("|");
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
        return 4;
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
