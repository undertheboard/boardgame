package com.boardgame.server;

import com.boardgame.games.BoardGame;
import com.boardgame.games.checkers.CheckersGame;
import com.boardgame.games.connectfour.ConnectFourGame;
import com.boardgame.games.dotsandboxes.DotsAndBoxesGame;
import com.boardgame.games.reversi.ReversiGame;
import com.boardgame.games.tictactoe.TicTacToeGame;
import com.boardgame.games.uno.UnoGame;

import java.util.List;

public final class GameFactory {
    private GameFactory() {
    }

    public static final List<String> GAME_TYPES = List.of(
            "UNO", "TICTACTOE", "CONNECTFOUR", "CHECKERS", "REVERSI", "DOTSANDBOXES");

    public static BoardGame create(String gameType) {
        return switch (gameType.toUpperCase()) {
            case "UNO" -> new UnoGame();
            case "TICTACTOE" -> new TicTacToeGame();
            case "CONNECTFOUR" -> new ConnectFourGame();
            case "CHECKERS" -> new CheckersGame();
            case "REVERSI" -> new ReversiGame();
            case "DOTSANDBOXES" -> new DotsAndBoxesGame();
            default -> throw new IllegalArgumentException("Unknown game type: " + gameType);
        };
    }
}
