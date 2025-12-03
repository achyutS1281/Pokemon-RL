package src.pas.othello.agents;


// SYSTEM IMPORTS

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;


// JAVA PROJECT IMPORTS
import edu.bu.pas.othello.agents.TimedTreeSearchAgent;
import edu.bu.pas.othello.game.Game;
import edu.bu.pas.othello.game.Game.GameView;
import edu.bu.pas.othello.game.PlayerType;
import edu.bu.pas.othello.traversal.Node;
import edu.bu.pas.othello.utils.Coordinate;
import src.pas.othello.heuristics.Heuristics;
import src.pas.othello.ordering.MoveOrderer;



public class OthelloAgent
        extends TimedTreeSearchAgent {
    public static class OthelloNode
            extends Node {
        public OthelloNode(final PlayerType maxPlayerType,
                           final GameView gameView,
                           final int depth)
        {
            super(maxPlayerType, gameView, depth);
        }
        private static class TimeoutException extends Exception {
        }

        @Override
        public double getTerminalUtility() {
            if (!getGameView().isGameOver()) {
                return 0.0;
            }

            int myPieces = 0;
            int theirPieces = 0;
            for (int i = 0; i < getGameView().getMaxXDimension(); ++i) {
                for (int j = 0; j < getGameView().getMaxYDimension(); ++j) {
                    if (getGameView().getCell(i, j) != null &&
                            getGameView().getCell(i, j).equals(this.getMaxPlayerType())) {
                        myPieces += 1;
                    } else if (getGameView().getCell(i, j) != null) {
                        theirPieces += 1;
                    }
                }
            }

            return (myPieces - theirPieces) > 0 ?  1.0 : -1.0;
        }


        @Override
        public List<Node> getChildren() {
            if (this.isTerminal()) {
                return new ArrayList<>();
            }

            List<Node> children = new ArrayList<>();
            PlayerType currPlayer = this.getGameView().getCurrentPlayerType();
            java.util.Set<Coordinate> moves = this.getGameView().getFrontier(currPlayer);
            if (!moves.isEmpty()) {
                for (Coordinate move : moves) {
                    Game childGame = new Game(getGameView());
                    childGame.applyMove(move);
                    childGame.setCurrentPlayerType( this.getOtherPlayerType() );
                    OthelloNode node = new OthelloNode(this.getMaxPlayerType(),
                            childGame.getView(),
                            this.getDepth() + 1);
                    node.setLastMove(move);
                    children.add(node);
                }
            } else {
                Game childGame = new Game(this.getGameView());
                childGame.setLastPlayerMove(null, this.getCurrentPlayerType());
                childGame.setCurrentPlayerType( this.getOtherPlayerType() );
                childGame.setTurnNumber(childGame.getTurnNumber() + 1);
                OthelloNode node = new OthelloNode(this.getMaxPlayerType(),
                        childGame.getView(),
                        this.getDepth() + 1);
                node.setLastMove(null);
                children.add(node);
            }
            return children;
        }
    }

    private final Random random;

    public OthelloAgent(final PlayerType myPlayerType,
                        final long maxMoveThinkingTimeInMS) {
        super(myPlayerType,
                maxMoveThinkingTimeInMS);
        this.random = new Random();
    }

    public final Random getRandom() {
        return this.random;
    }

    @Override
    public OthelloNode makeRootNode(final GameView game) {
        // if you change OthelloNode's constructor, you will want to change this!
        // Note: I am starting the initial depth at 0 (because I like to count up)
        //       change this if you want to count depth differently
        return new OthelloNode(this.getMyPlayerType(), game, 0);
    }

    @Override
    public Node treeSearch(Node n) {
        OthelloNode root = (OthelloNode) n;
        Node bestMoveNode;

        long startTime = System.currentTimeMillis();
        long timeLimit = this.getMaxThinkingTimeInMS();

        List<Node> children = root.getChildren();
        if (children.isEmpty()) {
            return root;
        }
        children = MoveOrderer.orderChildren(children);
        bestMoveNode = null;
        for (int depth = 1;depth <= 100; depth++) {
            double bestValue = Double.NEGATIVE_INFINITY;
            System.out.println(depth);
            if (System.currentTimeMillis() - startTime > timeLimit - 50) {
                break;
            }
            Node currentBestNodeForDepth = bestMoveNode;
            boolean timeExceeded = false;
            for (Node child : children) {
                int finalDepth = depth;
                double value;
                try{
                    value = quiescence_alpha_beta(child, 1, finalDepth, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY,
                            false, startTime, timeLimit);
                } catch (OthelloNode.TimeoutException e) {
                    timeExceeded = true;
                    break;
                }
                if (value > bestValue) {
                    bestValue = value;
                    currentBestNodeForDepth = child;
                }
            }
            if (timeExceeded) {
                break;
            }
            bestMoveNode = currentBestNodeForDepth;
        }

        return bestMoveNode;
    }


    private double quiescence_alpha_beta(Node node, int depth, int max_depth, double alpha, double beta, boolean isMax, long startTime, long timeLimit) throws OthelloNode.TimeoutException {
        if (System.currentTimeMillis() - startTime > timeLimit - 50) { // 500ms buffer
            throw new OthelloNode.TimeoutException();
        }
        if (depth >= max_depth) {
//            double utility =  Heuristics.calculateHeuristicValue(node);
//            node.setUtilityValue(utility);
//            return utility;
            return qSearch(node, alpha, beta, isMax, 0, startTime, timeLimit);
        }
        if (node.isTerminal()) {
            return node.getTerminalUtility();
        }
        double origAlpha = alpha;
        double origBeta = beta;
        List<Node> children = MoveOrderer.orderChildren(node.getChildren());
        double val;
        Node bestNode = null;
        if (isMax) {
            val = Double.NEGATIVE_INFINITY;

            for (Node child : children) {
                double currentVal = quiescence_alpha_beta(child, depth + 1, max_depth, alpha, beta, false, startTime, timeLimit);
                if (currentVal > val) {
                    val = currentVal;
                    bestNode = child;
                }
                alpha = Math.max(alpha, val);
                if (alpha >= beta) {
                    break;
                }

            }
        } else {
            val = Double.POSITIVE_INFINITY;
            for (Node child : children) {
                double currentVal = quiescence_alpha_beta(child, depth + 1, max_depth, alpha, beta, true, startTime, timeLimit);
                if (currentVal < val) {
                    val = currentVal;
                    bestNode = child;
                }
                beta = Math.min(beta, val);
                if (beta <= alpha) {
                    break;
                }

            }

        }
        assert bestNode != null;
        node.setUtilityValue(val);
        return val;
    }
    private static double qSearch(Node node, double alpha, double beta, boolean isMax, int depth, long startTime, long timeLimit) throws OthelloNode.TimeoutException {
        if (System.currentTimeMillis() - startTime > timeLimit - 50) {
            throw new OthelloNode.TimeoutException();
        }

        if (node.isTerminal()) {
            return node.getTerminalUtility();
        }
        double standPat = Heuristics.calculateHeuristicValue(node);
        node.setUtilityValue(standPat);
        if (depth >= 5) {
            return standPat;
        }


        if (isMax) {
            if (standPat >= beta) {
                return standPat;
            }
            alpha = Math.max(alpha, standPat);
        }else{
            if (standPat <= alpha) {
                return standPat;
            }
            beta = Math.min(beta, standPat);
        }
        double value;
        List<Node> children = node.getChildren();
        if (isMax) {
            value = Double.NEGATIVE_INFINITY;
            for (Node child : children) {
                double childValue = qSearch(child, alpha, beta, false, depth + 1, startTime, timeLimit);
                value = Math.max(value, childValue);
                alpha = Math.max(alpha, value);
                if (alpha >= beta) {
                    break;
                }
            }
        } else {
            value = Double.POSITIVE_INFINITY;
            for (Node child : children) {
                double childValue = qSearch(child, alpha, beta, true, depth + 1, startTime, timeLimit);
                value = Math.min(value, childValue);
                beta = Math.min(beta, value);
                if (beta <= alpha) {
                    break;
                }
            }
        }
        node.setUtilityValue(value);
        return value;
    }
    @Override
    public Coordinate chooseCoordinateToPlaceTile(final GameView game) {
        // TODO: this move will be called once per turn
        //       you may want to use this method to add to data structures and whatnot
        //       that your algorithm finds useful

        // make the root node
        Node node = this.makeRootNode(game);

        // call tree search
        Node moveNode = this.treeSearch(node);
        System.out.println("-----------------------------------------\n"+Heuristics.calculateHeuristicValue(node)+
                "\n-----------------------------------------");
        System.out.println(moveNode.getGameView().getCell(moveNode.getLastMove()));

        // return the move inside that node
        return moveNode.getLastMove();
    }

    @Override
    public void afterGameEnds(final GameView game) {
    }
}