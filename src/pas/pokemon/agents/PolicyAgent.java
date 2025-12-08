package src.pas.pokemon.agents;


// SYSTEM IMPORTS
import net.sourceforge.argparse4j.inf.Namespace;

import edu.bu.pas.pokemon.agents.NeuralQAgent;
import edu.bu.pas.pokemon.agents.senses.SensorArray;
import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Move.MoveView;
import edu.bu.pas.pokemon.core.Move.Category;
import edu.bu.pas.pokemon.core.Pokemon.PokemonView;
import edu.bu.pas.pokemon.core.Team.TeamView;
import edu.bu.pas.pokemon.nn.Model;
import edu.bu.pas.pokemon.nn.models.Sequential;
import edu.bu.pas.pokemon.nn.layers.Dense; // fully connected layer
import edu.bu.pas.pokemon.nn.layers.ReLU;  // some activations (below too)


// JAVA PROJECT IMPORTS
import src.pas.pokemon.senses.CustomSensorArray;
import java.util.List;
import java.util.Random;


public class PolicyAgent
    extends NeuralQAgent
{

    public PolicyAgent()
    {
        super();
    }

    // matches CustomSensorArray output length
    private static final int FEATURE_DIM = 172;
    private final Random rng = new Random();

    // Epsilon-greedy Hyperparameters
    private double epsilonStart = 0.30;
    private double epsilonEnd = 0.05;
    private double epsilonDecay = 0.999;
    private double epsilon = epsilonStart;
    public long stepCount = 0;

    public void initializeSenses(Namespace args)
    {
        SensorArray modelSenses = new CustomSensorArray(this);

        this.setSensorArray(modelSenses);
    }

    @Override
    public void initialize(Namespace args)
    {
        // make sure you call this, this will call your initModel() and set a field
        // AND if the command line argument "inFile" is present will attempt to set
        // your model with the contents of that file.
        super.initialize(args);

        // what senses will your neural network have?
        this.initializeSenses(args);

        // do what you want just don't expect custom command line options to be available
        // when I'm testing your code
    }

    @Override
    public Model initModel()
    {
        // Two-hidden-layer NN with ReLU for Q(s,a).
        Sequential qFunction = new Sequential();
        qFunction.add(new Dense(FEATURE_DIM, 256));
        qFunction.add(new ReLU());
        qFunction.add(new Dense(256, 128));
        qFunction.add(new ReLU());
        qFunction.add(new Dense(128, 1));

        return qFunction;
    }

    // Helper method to compute HP fraction of a Pokemon
    private double hpFraction(final PokemonView p)
    {
        if(p == null)
        {
            return 0d;
        }
        double maxHp = Math.max(1d, p.getInitialStat(edu.bu.pas.pokemon.core.enums.Stat.HP));
        double currHp = Math.max(0d, p.getCurrentStat(edu.bu.pas.pokemon.core.enums.Stat.HP));
        return currHp / maxHp;
    }

    // Helper to calculate how advantage our typing would be if switched in
    private double defensiveMultiplier(final MoveView oppLastMove, final PokemonView candidate)
    {
        if(oppLastMove == null || candidate == null)
        {
            return 1d; // 1x effectiveness
        }
        return typeMultiplier(oppLastMove.getType(), candidate);
    }

    // Helper to calculate how well a candidate Pokemon can hit the opponent's active Pokemon
    private double offensiveCoverage(final PokemonView candidate, final PokemonView target)
    {
        // If either is null, return neutral effectiveness
        if(candidate == null || target == null)
        {
            return 1d; // 1x effectiveness
        }

        // Track the best effectiveness among all valid moves (start as low as possible 0.25x)
        double best = 0.25d;
        List<MoveView> moves = candidate.getAvailableMoves();

        // If no moves, return neutral effectiveness
        if(moves == null || moves.isEmpty())
        {
            return best;
        }

        // Check each move for effectiveness
        for(MoveView mv : moves)
        {
            // Skip null moves or status moves
            if(mv == null || mv.getCategory() == Category.STATUS)
            {
                continue;
            }

            // Calculate effectiveness of the move against the target
            double eff = typeMultiplier(mv.getType(), target);
            // Update best effectiveness if this move is better
            if(eff > best)
            {
                best = eff;
            }
        }
        return best;
    }

    // Helper to calculate type effectiveness multiplier
    private double typeMultiplier(final edu.bu.pas.pokemon.core.enums.Type attackType, final PokemonView defender)
    {
        // Return neutral effectiveness if either parameter is null
        if(attackType == null || defender == null)
        {
            return 1d;
        }

        double modifier = 1d;
        // Get the defender's types
        edu.bu.pas.pokemon.core.enums.Type t1 = defender.getCurrentType1();
        edu.bu.pas.pokemon.core.enums.Type t2 = defender.getCurrentType2();

        // Apply effectiveness modifiers for each type
        if(t1 != null)
        {
            modifier *= edu.bu.pas.pokemon.core.enums.Type.getEffectivenessModifier(attackType, t1);
        }
        if(t2 != null)
        {
            modifier *= edu.bu.pas.pokemon.core.enums.Type.getEffectivenessModifier(attackType, t2);
        }
        return modifier;
    }

    // Helper to calculate speed advantage edge
    private double speedEdge(final PokemonView ours, final PokemonView opp)
    {
        // Get both Pokemon's speed stats
        double ourSpd = (ours == null) ? 0d : Math.max(0d, ours.getCurrentStat(edu.bu.pas.pokemon.core.enums.Stat.SPD));
        double oppSpd = (opp == null) ? 0d : Math.max(0d, opp.getCurrentStat(edu.bu.pas.pokemon.core.enums.Stat.SPD));
        double denominator = Math.max(1d, ourSpd + oppSpd);
        return (ourSpd - oppSpd) / denominator;
    }

    @Override
    public Integer chooseNextPokemon(BattleView view)
    {
        // Get the team views and active pokemon views
        final TeamView myTeam = this.getMyTeamView(view);
        final TeamView oppTeam = this.getOpponentTeamView(view);
        final PokemonView oppActive = oppTeam.getActivePokemonView();
        final MoveView oppLastMove = oppTeam.getLastMoveView();

        // Variables to track the best candidate to switch in
        int bestIdx = -1;
        double bestScore = Double.NEGATIVE_INFINITY;

        // if no safe pivot exists (all defensive multiplier > 1.5), sack the lowest-HP alive mon
        int sackIdx = -1;
        double lowestHp = Double.POSITIVE_INFINITY;

        // Check each Pokemon and give it a heuristic score for switching in
        for(int idx = 0; idx < myTeam.size(); ++idx)
        {
            PokemonView candidate = myTeam.getPokemonView(idx);
            // Skip if the Pokemon is null or dead
            if(candidate == null || candidate.hasFainted())
            {
                continue;
            }

            // Calculate the HP fraction for the candidate
            double hpFrac = hpFraction(candidate);
            // Track the lowest HP fraction for potential sack
            if(hpFrac < lowestHp)
            {
                lowestHp = hpFrac;
                sackIdx = idx;
            }

            // Calculate defensive multiplier against opponent's last move
            double defensive = defensiveMultiplier(oppLastMove, candidate);
            // Calculate offensive coverage against opponent's active Pokemon
            double offensive = offensiveCoverage(candidate, oppActive);
            // Calculate speed advantage flag
            double speedFlag = speedEdge(candidate, oppActive) > 0 ? 1d : 0d;

            // Final heuristic score for this Pokemon
            double score = (-1.2 * defensive) + (0.8 * offensive) + (0.5 * hpFrac) + (0.2 * speedFlag);
            // Update best Pokemon if this one is better
            if(score > bestScore)
            {
                bestScore = score;
                bestIdx = idx;
            }
        }

        // Safety: return null is we have no team
        if(bestIdx == -1)
        {
            return null;
        }
//
//        // Sacking Pokemon condition
//        if(defensiveMultiplier(oppLastMove, myTeam.getPokemonView(bestIdx)) > 1.5 && sackIdx != -1)
//        {
//            return sackIdx;
//        }

        return bestIdx;
    }

    @Override
    public MoveView getMove(BattleView view)
    {
        // Epsilon-greedy action selection with decaying epsilon
        this.epsilon = Math.max(epsilonEnd, epsilonStart * Math.pow(epsilonDecay, this.stepCount));

        // Exploration Step (random valid move)
        if(this.rng.nextDouble() < this.epsilon)
        {
            List<MoveView> moves = this.getMyTeamView(view).getActivePokemonView().getAvailableMoves();
            if(moves != null && !moves.isEmpty())
            {
                return moves.get(this.rng.nextInt(moves.size()));
            }
        }

        // Exploitation Step (best Q-value move)
        return this.argmax(view);
    }

    @Override
    public void afterGameEnds(BattleView view)
    {

    }
}
