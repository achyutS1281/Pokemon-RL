package src.pas.othello.heuristics;

import edu.bu.pas.othello.game.PlayerType;
import edu.bu.pas.othello.traversal.Node;
import edu.bu.pas.othello.utils.Coordinate;

public class Heuristics {
    private static final double CORNER_WEIGHT = 0.3;
    private static final double BOARD_WEIGHT = 0.5;
    private static final double PIECES_WEIGHT = 0.2;
    private static final double[][] boardWeights = {
        { 20,  -4,  10,  7,  7,  10,  -4,  20},
        { -4, -10,  -5,   1,   1,  -5, -10,  -4},
        { 10,  -5,   1,   2,   2,   1,  -5,  10},
        { 7,   1,   2,  -3,  -3,   2,   1,  7},
        { 7,   1,   2,  -3,  -3,   2,   1,  7},
        { 10,  -5,   1,   2,   2,   1,  -5,  10},
        { -4, -10,  -5,   1,   1,  -5, -10,  -4},
        { 20,  -4,  10,   7,   7,  10,  -4,  20}
    };
    private static final double total = 120.0;
    private static final double W_POSITION = 0.6;
    private static final double W_FRONTIER = 0.4;
    private static final double W_CORNER_CAP = 0.7;
    private static final double W_CORNER_RADIUS = 0.3;
    private static final double W_TRAPS = 0.0;

    public static double calculateHeuristicValue(Node node) {
        PlayerType me = (PlayerType) node.getMaxPlayerType();
        PlayerType opp = me == PlayerType.BLACK ? PlayerType.WHITE : PlayerType.BLACK;

        // positional score: boardWeights is defined as rows (y) first, then columns (x)
        double positionalScore = 0.0;
        for (int x = 0; x < node.getGameView().getMaxXDimension(); x++) {
            for (int y = 0; y < node.getGameView().getMaxYDimension(); y++) {
                if (node.getGameView().getCell(x, y) == me) {
                    positionalScore += boardWeights[y][x] / total;
                } else if (node.getGameView().getCell(x, y) == opp) {
                    positionalScore -= boardWeights[y][x] / total;
                }
            }
        }

        // frontier calculation: keep both frontiers positive, then compare
        double myFrontier = 0.0;
        double oppFrontier = 0.0;
        double totalFrontier = 0.0;
        for (int x = 0; x < node.getGameView().getMaxXDimension(); x++) {
            for (int y = 0; y < node.getGameView().getMaxYDimension(); y++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if (dx == 0 && dy == 0) continue;
                        int nx = x + dx;
                        int ny = y + dy;
                        if (nx < 0 || ny < 0 || nx >= node.getGameView().getMaxXDimension() || ny >= node.getGameView().getMaxYDimension()) {
                            continue;
                        }
                        if (node.getGameView().getCell(x, y) == me &&
                            node.getGameView().getCell(nx, ny) == null) {
                            myFrontier++;
                            totalFrontier++;
                        } else if (node.getGameView().getCell(x, y) == opp &&
                                   node.getGameView().getCell(nx, ny) == null) {
                            oppFrontier++;
                            totalFrontier++;
                        }
                    }
                }
            }
        }
        double frontierScore = (totalFrontier == 0) ? 0.0 : (myFrontier - oppFrontier) / totalFrontier;

        // disc ratio (unchanged)
        double myDiscs = 0.0;
        double oppDiscs = 0.0;
        double totalDiscs = 0.0;
        for (int x = 0; x < node.getGameView().getMaxXDimension(); x++) {
            for (int y = 0; y < node.getGameView().getMaxYDimension(); y++) {
                if (node.getGameView().getCell(x, y) == me) {
                    myDiscs++;
                    totalDiscs++;
                } else if (node.getGameView().getCell(x, y) == opp) {
                    oppDiscs++;
                    totalDiscs++;
                }
            }
        }
        double discRatioScore = (totalDiscs == 0) ? 0.0 : (myDiscs - oppDiscs) / totalDiscs;
        double cornerCaptureScore = 0.0;
        Coordinate[] corners = {
            new Coordinate(0, 0),
            new Coordinate(0, node.getGameView().getMaxYDimension() - 1),
            new Coordinate(node.getGameView().getMaxXDimension() - 1, 0),
            new Coordinate(node.getGameView().getMaxXDimension() - 1, node.getGameView().getMaxYDimension() - 1)
        };
        for (Coordinate corner : corners) {
            PlayerType c = (PlayerType) node.getGameView().getCell(corner.getXCoordinate(), corner.getYCoordinate());
            if (c == me) {
                cornerCaptureScore += 1.0;
            } else if (c == opp) {
                cornerCaptureScore -= 1.0;
            }
        }
        cornerCaptureScore /= 4.0;

        double cornerRadiusPenalty = 0.0;
        double trappingReward = 0.0;
        double cornersUsedForRadius = 0.0;
        for (Coordinate corner : corners) {
            if (node.getGameView().getCell(corner.getXCoordinate(), corner.getYCoordinate()) == null) {
                int myR1 = countSquaresOccupiedNearCornerAtRadius(node, corner, 1, me);
                int oppR1 = countSquaresOccupiedNearCornerAtRadius(node, corner, 1, opp);
                if (myR1 + oppR1 != 0) {
                    cornerRadiusPenalty -= (double)(myR1 - oppR1) / (myR1 + oppR1);
                }
                cornersUsedForRadius++;
            }
        }
        if (cornersUsedForRadius > 0) {
            cornerRadiusPenalty /= cornersUsedForRadius;
            trappingReward /= cornersUsedForRadius;
        }

        double totalScore = BOARD_WEIGHT * (W_POSITION * positionalScore + W_FRONTIER * frontierScore)
                          + PIECES_WEIGHT * discRatioScore
                          + CORNER_WEIGHT * (W_CORNER_CAP * cornerCaptureScore + W_CORNER_RADIUS * cornerRadiusPenalty + W_TRAPS * trappingReward);

        if (totalScore > 1.0) totalScore = 1.0;
        if (totalScore < -1.0) totalScore = -1.0;
        return totalScore;
    }

    public static int countSquaresOccupiedNearCornerAtRadius(Node node, Coordinate corner, int radius, PlayerType playerType) {
        int count = 0;
        int startX = Math.max(0, corner.getXCoordinate() - radius);
        int endX = Math.min(node.getGameView().getMaxXDimension() - 1, corner.getXCoordinate() + radius);
        int startY = Math.max(0, corner.getYCoordinate() - radius);
        int endY = Math.min(node.getGameView().getMaxYDimension() - 1, corner.getYCoordinate() + radius);
        for (int x = startX; x <= endX; x++) {
            for (int y = startY; y <= endY; y++) {
                if (x == corner.getXCoordinate() && y == corner.getYCoordinate()) {
                    continue;
                }
                if (x < 0 || y < 0 || x >= node.getGameView().getMaxXDimension() || y >= node.getGameView().getMaxYDimension()) {
                    continue;
                }
                if (node.getGameView().getCell(x, y) == playerType) {
                    count++;
                }
            }
        }
        return count;
    }
}