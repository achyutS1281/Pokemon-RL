//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package pas.pokemon;

import edu.bu.pas.pokemon.RandomBattle;
import edu.bu.pas.pokemon.core.Agent;
import edu.bu.pas.pokemon.core.Battle;
import edu.bu.pas.pokemon.core.CoreRegistry;
import edu.bu.pas.pokemon.core.Move;
import edu.bu.pas.pokemon.core.Pokemon;
import edu.bu.pas.pokemon.rendering.BattlePanel;
import edu.bu.pas.pokemon.rendering.Sound;
import edu.bu.pas.pokemon.rendering.SoundManager;
import edu.bu.pas.pokemon.rendering.animations.UpdateBattleStateAnimation;
import edu.bu.pas.pokemon.rendering.listeners.AnimationListener;
import edu.bu.pas.pokemon.utils.Pair;
import java.awt.Component;
import java.awt.event.WindowEvent;
import java.lang.reflect.Constructor;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;

public class BattleTester {
    public static final long SEED = 12345L;

    public static Agent getAgent(String var0) {
        Agent var1 = null;

        try {
            Class var2 = Class.forName(var0);
            Constructor var3 = var2.getConstructor();
            var1 = (Agent)var3.newInstance();
        } catch (Exception var4) {
            System.err.println("[ERROR] Main.main: error when instantiating " + var0);
            var4.printStackTrace();
            System.exit(-1);
        }

        return var1;
    }

    public static Pokemon getPokemon(CoreRegistry var0, int var1, Move[] var2) throws Exception {
        return var0.spawnPokemon(var1, 100, new int[]{15, 15, 15, 15, 15, 15, 1, 1}, new int[]{65535, 65535, 65535, 65535, 65535, 65535, 1, 1}, var2);
    }

    public static void main(String[] var0) throws Exception {
        ArgumentParser var1 = ArgumentParsers.newFor("Main").build().defaultHelp(true).description("Play a Pokemon battle where the two teams (and agents) are configurable!");
        var1.addArgument(new String[]{"t1FilePath"}).type(String.class).help("path to team1's .csv file (including filename)");
        var1.addArgument(new String[]{"t2FilePath"}).type(String.class).help("path to team2's .csv file (including filename)");
        var1.addArgument(new String[]{"--t1Agent"}).type(String.class).setDefault("edu.bu.pas.pokemon.agents.RandomAgent").help("Specify fully-qualified class for team1's agent");
        var1.addArgument(new String[]{"--t2Agent"}).type(String.class).setDefault("edu.bu.pas.pokemon.agents.RandomAgent").help("Specify fully-qualified class for team2's agent");
        var1.addArgument(new String[]{"-s", "--silent"}).action(Arguments.storeTrue());
        var1.addArgument(new String[]{"-v", "--volume"}).type(Float.class).setDefault(0.5F).help("0.0 = no sound, 0.5 = half, 1.0 = full volume");
        var1.addArgument(new String[]{"-t", "--turnDelayMS"}).type(Long.class).setDefault(0L).help("delay between each turn of the game in milliseconds");
        var1.addArgument(new String[]{"-i1"}).type(String.class).setDefault("").help("Input file for agent 1");
        var1.addArgument(new String[]{"-i2"}).type(String.class).setDefault("").help("Input file for agent 2");
        Namespace var2 = var1.parseArgsOrFail(var0);
        Agent var3 = getAgent((String)var2.get("t1Agent"));
        Agent var4 = getAgent((String)var2.get("t2Agent"));
        if (var3 instanceof src.pas.pokemon.agents.PolicyAgent) {
            ((src.pas.pokemon.agents.PolicyAgent)var3).initialize(var2);
            ((src.pas.pokemon.agents.PolicyAgent)var3).getModel().load((String)var2.get("i1"));
        }
        if (var4 instanceof src.pas.pokemon.agents.PolicyAgent) {
            ((src.pas.pokemon.agents.PolicyAgent)var4).initialize(var2);
            ((src.pas.pokemon.agents.PolicyAgent)var4).getModel().load((String)var2.get("i2"));
        }
        String var5 = (String)var2.get("t1FilePath");
        String var6 = (String)var2.get("t2FilePath");
        boolean var7 = (Boolean)var2.get("silent");
        long var8 = (Long)var2.get("turnDelayMS");

        try {
            CoreRegistry var10 = new CoreRegistry();
            Battle var11 = Battle.fromFiles(var10, var5, var6, var3, var4);
            System.out.println("loaded t1=" + var3.getClass() + " w/ config=" + var5);
            System.out.println("loaded t2=" + var4.getClass() + " w/ config=" + var6);
            System.out.println();
            System.out.println("t1's pokemon:");

            for(Pokemon var13 : var11.getTeam(0).getPokemon()) {
                System.out.println("\t-" + var13.getName());

                for(Move var17 : var13.getMoves()) {
                    System.out.println("\t\t-" + (var17 == null ? "null" : var17.getName()));
                }
            }

            System.out.println();
            System.out.println("t2's pokemon:");

            for(Pokemon var24 : var11.getTeam(1).getPokemon()) {
                System.out.println("\t-" + var24.getName());

                for(Move var33 : var24.getMoves()) {
                    System.out.println("\t\t-" + (var33 == null ? "null" : var33.getName()));
                }
            }

            SoundManager var23 = var7 ? null : new SoundManager((Float)var2.get("volume"));
            JFrame var25 = var7 ? null : new JFrame("Pokemon");
            BattlePanel var27 = var7 ? null : new BattlePanel(var11, var10, var23);
            if (!var7) {
                var11.setListener(new AnimationListener(var27));
                var27.setBattle(var11.copy());
                var23.setMusic(() -> ((Sound)var27.getSoundEffects().getByName("battle")).getAudioStream());
                var23.playMusic();
                var27.start();
            }

            if (var25 != null) {
                var25.setDefaultCloseOperation(3);
                var25.setIconImage(ImageIO.read(RandomBattle.class.getResourceAsStream("/resources/sprites/icon.png")));
                var25.add(var27);
                var25.pack();
                var25.setLocationRelativeTo((Component)null);
                var25.setVisible(true);
            }

            boolean var29 = false;

            while(!var29) {
                var11.nextTurn();
                System.out.println("turn=" + var11.getTurnNumber());
                var11.applyPreTurnConditions();
                Pair var31 = var11.getMoves();
                if (var27 != null) {
                    var27.queueAnimation(new UpdateBattleStateAnimation(var11));
                    var27.join();
                }

                var11.applyMoves(var31);
                var11.applyPostTurnConditions();
                if (var27 != null && var8 > 0L) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(var8);
                    } catch (Exception var20) {
                    }
                }

                var29 = var11.isOver();
                System.out.println();
            }

            if (var27 != null) {
                var27.queueAnimation(new UpdateBattleStateAnimation(var11));
                var27.join();
            }

            boolean var32 = false;

            for(Pokemon var18 : var11.getTeam1().getPokemon()) {
                if (var18 != null) {
                    var32 = var32 || !var18.hasFainted();
                }
            }

            boolean var35 = false;

            for(Pokemon var19 : var11.getTeam2().getPokemon()) {
                if (var19 != null) {
                    var35 = var35 || !var19.hasFainted();
                }
            }

            if (!var32 && !var35) {
                System.out.println("[Pool.RESULT] tie game!");
            } else if (var32) {
                if (!var7) {
                    var23.stopMusic();
                    var23.playSound(((Sound)var27.getSoundEffects().getByName("victory")).getAudioStream());
                    TimeUnit.MILLISECONDS.sleep(8500L);
                }

                System.out.println("[Pool.RESULT] team 1 wins!");
            } else if (var35) {
                if (!var7) {
                    var23.stopMusic();
                    var23.playSound(((Sound)var27.getSoundEffects().getByName("victory")).getAudioStream());
                    TimeUnit.MILLISECONDS.sleep(8500L);
                }

                System.out.println("[Pool.RESULT] team 2 wins!");
            }

            if (var25 != null) {
                var25.dispatchEvent(new WindowEvent(var25, 201));
            }

            System.exit(0);
        } catch (Exception var21) {
            var21.printStackTrace();
        }

    }
}
