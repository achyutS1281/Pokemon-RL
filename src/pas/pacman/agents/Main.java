//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package src.pas.pacman.agents;

import edu.bu.pas.pacman.agents.Agent;
import edu.bu.pas.pacman.agents.GhostAgent;
import edu.bu.pas.pacman.game.Constants;
import edu.bu.pas.pacman.game.Difficulty;
import edu.bu.pas.pacman.game.Game;
import edu.bu.pas.pacman.game.entity.Entity;
import edu.bu.pas.pacman.game.entity.Ghost;
import edu.bu.pas.pacman.graph.Path;
import edu.bu.pas.pacman.graph.PelletGraph;
import edu.bu.pas.pacman.rendering.GamePanel;
import edu.bu.pas.pacman.utils.Coordinate;
import java.awt.Component;
import java.lang.reflect.Constructor;
import java.util.concurrent.TimeUnit;
import javax.swing.JFrame;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;

public class Main {
    public static Agent getAgent(String var0, int var1, int var2) {
        Agent var3 = null;

        try {
            Class var4 = Class.forName(var0);
            Constructor var5 = var4.getConstructor(Integer.TYPE, Integer.TYPE, Integer.TYPE);
            var3 = (Agent)var5.newInstance(var1, var1, var2);
        } catch (Exception var6) {
            System.err.println("[ERROR] Main.main: error when instantiating " + var0);
            var6.printStackTrace();
            System.exit(-1);
        }

        return var3;
    }

    public static void main(String[] var0) {
        ArgumentParser var1 = ArgumentParsers.newFor("Main").build().defaultHelp(true).description("Pacman");
        var1.addArgument(new String[]{"-a", "--agent"}).type(String.class).setDefault("src.pas.pacman.agents.PacmanAgent").help("Specify fully-qualified class for the agent to pacman");
        var1.addArgument(new String[]{"-e", "--numEpisodes"}).type(Integer.class).setDefault(10).help("the number of games to play at once");
        var1.addArgument(new String[]{"-x", "--xCoordSize"}).type(Integer.class).setDefault(6).help("the size of the map in the horizontal dimension");
        var1.addArgument(new String[]{"-y", "--yCoordSize"}).type(Integer.class).setDefault(9).help("the size of the map in the vertical dimension");
        var1.addArgument(new String[]{"-d", "--difficulty"}).choices(new String[]{"EASY", "MEDIUM", "HARD", "INSANE"}).setDefault("EASY").type(String.class).help("difficulty of the game");
        var1.addArgument(new String[]{"-p", "--numPacmanLives"}).type(Integer.class).setDefault(1).help("the number of lives pacman has in a single game");
        var1.addArgument(new String[]{"-g", "--numGhosts"}).type(Integer.class).setDefault(0).help("the number of ghosts in a single game");
        var1.addArgument(new String[]{"-r", "--ghostChaseRadius"}).type(Integer.class).setDefault(2).help("the chebyshev distance <= which ghosts start chasing pacman");
        var1.addArgument(new String[]{"-b", "--ghostBackoffProb"}).type(Double.class).setDefault((double)0.5F).help("probability ghosts 'forget' to keep chasing pacman (when they're chasing him)");
        var1.addArgument(new String[]{"-s", "--silent"}).action(Arguments.storeTrue()).help("if specified, run the game without the GUI.");
        var1.addArgument(new String[]{"--seed"}).type(Long.class).setDefault(12345L).help("random seed to make successive runs repeatable.");
        var1.addArgument(new String[]{"-t", "--maxTurnsPerGame"}).type(Integer.class).setDefault(10000).help("the maximum number of turns allowed in a single game");
        var1.addArgument(new String[]{"--hz"}).type(Integer.class).setDefault(5).help("Frame rate for rendering");
        Namespace var2 = var1.parseArgsOrFail(var0);
        new Constants(Difficulty.valueOf((String)var2.get("difficulty")));
        int var4 = (Integer)var2.get("numEpisodes");
        int var5 = (Integer)var2.get("xCoordSize");
        int var6 = (Integer)var2.get("yCoordSize");
        int var7 = (Integer)var2.get("numPacmanLives");
        int var8 = (Integer)var2.get("numGhosts");
        int var9 = (Integer)var2.get("ghostChaseRadius");
        double var10 = (Double)var2.get("ghostBackoffProb");
        boolean var12 = (Boolean)var2.get("silent");
        long var13 = (Long)var2.get("seed");
        int var15 = (Integer)var2.get("maxTurnsPerGame");
        int var16 = (Integer)var2.get("hz");
        int var17 = (int)((double)1000.0F / (double)var16) / 2;
        Coordinate var18 = new Coordinate(3, 5);
        Coordinate var19 = new Coordinate(5, 1);

        for(int var20 = 0; var20 < var4; ++var20) {
            Game var21 = new Game(var18, var19, var8, var7, var13);
            Agent var22 = getAgent((String)var2.get("agent"), var21.getPacman().getId(), var9);
            var21.setPacmanAgent(var22);

            for(Entity var24 : var21.getBoard().getEntities().values()) {
                if (var24 instanceof Ghost) {
                    var21.addGhostAgent(new GhostAgent(var24.getId(), var21.getPacman().getId(), var9, var21.getBoard().getRandom(), var10));
                }
            }

            if (var12) {
                while(!var21.isGameOver()) {
                    try {
                        var21.playTurn();
                    } catch (Exception var26) {
                        System.err.print(var26);
                        var26.printStackTrace();
                        System.exit(1);
                    }
                }
            } else {
                JFrame var30 = new JFrame("Pacman");
                var30.setDefaultCloseOperation(3);
                var30.setResizable(false);
                var30.setLocationRelativeTo((Component)null);
                var30.setVisible(true);
                GamePanel var32 = new GamePanel(var21);
                var30.add(var32);
                var30.pack();
                var30.repaint();
                Path<PelletGraph.PelletVertex> var34 = null;
                PacmanAgent var33 = ((PacmanAgent) (var21.getPacmanAgent()));
                while(!var21.isGameOver()) {
                    var30.repaint();

                    try {
                        TimeUnit.MILLISECONDS.sleep((long)var17);
                    } catch (Exception var29) {
                    }

                    try {
                        if (var34 == null && var33.getPlanToGetToTarget() == null) {
                            var34 = var33.findPathToEatAllPelletsTheFastest(new Game.GameView(var21));
                            var33.setTargetCoordinate(var34.getDestination().getPacmanCoordinate());
                            var34 = var34.getParentPath();
                        }
                        if(var34 != null && var33.getPlanToGetToTarget() != null) {
                            var33.setTargetCoordinate(var34.getDestination().getPacmanCoordinate());
                            var34 = var34.getParentPath();
                        }
                        var21.playTurn();

                    } catch (Exception var28) {
                        System.err.print(var28);
                        var28.printStackTrace();
                        System.exit(1);
                    }

                    var30.repaint();

                    try {
                        TimeUnit.MILLISECONDS.sleep((long)var17);
                    } catch (Exception var27) {
                    }
                }

                var30.dispose();
            }

            var21.getPacmanAgent().afterGameEnds(new Game.GameView(var21));

            for(Agent var33 : var21.getGhostAgents()) {
                var33.afterGameEnds(new Game.GameView(var21));
            }

            if (var21.getTurnNumber() >= var21.getMaxNumTurns()) {
                System.out.println("[OUTCOME] Main.main: TIE! game ran out of turns!");
            } else if (!var21.getPacman().getIsAlive()) {
                System.out.println("[OUTCOME] Main.main: PACMAN LOSES!");
            } else if (var21.getNumPelletsRemaining() <= 0) {
                System.out.println("[OUTCOME] Main.main: PACMAN WINS!");
            } else {
                System.out.println("[OUTCOME] Main.main: UNKNOWN OUTCOME");
            }
        }

        System.exit(0);
    }
}
