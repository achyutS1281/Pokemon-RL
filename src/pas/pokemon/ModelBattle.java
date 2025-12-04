package src.pas.pokemon;


// SYSTEM IMPORTS
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import javax.swing.JFrame;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;


// JAVA PROJECT IMPORTS
import edu.bu.pas.pokemon.core.Agent;
import edu.bu.pas.pokemon.core.Battle;
import edu.bu.pas.pokemon.core.Team;
import edu.bu.pas.pokemon.generators.BattleCreator;
import edu.bu.pas.pokemon.rendering.BattlePanel;
import edu.bu.pas.pokemon.rendering.SpriteRegistry;
import edu.bu.pas.pokemon.core.CoreRegistry;
import net.sourceforge.argparse4j.inf.Namespace;

import src.pas.pokemon.agents.PolicyAgent;


/**
 * Minimal driver to play a random battle using a saved PolicyAgent model.
 * Args (all optional, in order):
 * 0: model file path (default params/qFunction.model)
 * 1: opponent agent class (default edu.bu.pas.pokemon.agents.RandomAgent)
 * 2: numPokemon per team (default 6, capped at Team.MAX_TEAMSIZE)
 * 3: numMoves per pokemon (default 4)
 * 4: seed (-1 for random, default -1)
 * 5: silent (true/false, default false)
 * 6: turnDelayMS (default 0)
 */
public class ModelBattle
{
    public static void main(String[] args) throws Exception
    {
        final String modelFileArg = (args.length > 0) ? args[0] : "src/pas/pokemon/params/qFunction.model";
        final String oppClass = (args.length > 1) ? args[1] : "edu.bu.pas.pokemon.agents.RandomAgent";
        int numMons = (args.length > 2) ? Integer.parseInt(args[2]) : 6;
        int numMoves = (args.length > 3) ? Integer.parseInt(args[3]) : 4;
        final long seed = (args.length > 4) ? Long.parseLong(args[4]) : -1L;
        final boolean silent = (args.length > 5) && Boolean.parseBoolean(args[5]);
        final long turnDelayMS = (args.length > 6) ? Long.parseLong(args[6]) : 0L;

        numMons = Math.max(1, Math.min(numMons, Team.MAX_TEAMSIZE));
        numMoves = Math.max(1, Math.min(numMoves, 4));

        CoreRegistry cr = new CoreRegistry();
        Random rng = (seed == -1L) ? new Random() : new Random(seed);

        // Initialize our policy agent with the model file
        PolicyAgent myAgent = new PolicyAgent();
        final String modelFile = resolveModelPath(modelFileArg);
        Map<String, Object> map = new HashMap<>();
        map.put("inFile", modelFile);
        myAgent.initialize(new Namespace(map));

        // Instantiate opponent
        Agent oppAgent = (Agent)Class.forName(oppClass).getConstructor().newInstance();
        oppAgent.initialize(null);

        // Build battle with random teams
        Battle battle = BattleCreator.makeRandomTeams(numMons, numMons, numMoves, rng, myAgent, oppAgent);

        SpriteRegistry sr = silent ? null : new SpriteRegistry(cr);
        JFrame frame = silent ? null : new JFrame("Pokemon");
        BattlePanel panel = silent ? null : new BattlePanel(battle, cr, sr);

        if(frame != null)
        {
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.add(panel);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        }

        boolean done = false;
        while(!done)
        {
            battle.nextTurn();
            battle.applyPreTurnConditions();
            battle.getAndApplyMoves();
            if(panel != null)
            {
                panel.repaint();
            }
            battle.applyPostTurnConditions();
            if(panel != null)
            {
                panel.repaint();
            }
            if(turnDelayMS > 0 && panel != null)
            {
                Thread.sleep(turnDelayMS);
            }
            done = battle.isOver();
        }

        if(frame != null)
        {
            frame.dispose();
        }

        // Report winner
        boolean team1Alive = battle.getTeam1().getPokemon().stream().anyMatch(p -> p != null && !p.hasFainted());
        boolean team2Alive = battle.getTeam2().getPokemon().stream().anyMatch(p -> p != null && !p.hasFainted());
        if(team1Alive && !team2Alive)
        {
            System.out.println("[ModelBattle.RESULT] team 1 wins!");
        }
        else if(!team1Alive && team2Alive)
        {
            System.out.println("[ModelBattle.RESULT] team 2 wins!");
        }
        else
        {
            System.out.println("[ModelBattle.RESULT] tie game!");
        }

        System.exit(0);
    }

    private static String resolveModelPath(final String arg)
    {
        // Try as-is
        if(new File(arg).exists())
        {
            return arg;
        }
        // Try params/ relative to project root
        Path p1 = Paths.get("params", arg);
        if(p1.toFile().exists())
        {
            return p1.toString();
        }
        // Try src/pas/pokemon/params/
        Path p2 = Paths.get("src", "pas", "pokemon", "params", arg);
        if(p2.toFile().exists())
        {
            return p2.toString();
        }
        System.err.println("[ERROR] ModelBattle: model file not found with tried paths: " + arg + ", " + p1 + ", " + p2);
        System.exit(1);
        return arg; // unreachable
    }
}
