package src.pas.pokemon.agents;

// SYSTEM IMPORTS
import net.sourceforge.argparse4j.inf.Namespace;

import edu.bu.pas.pokemon.agents.NeuralQAgent;
import edu.bu.pas.pokemon.agents.senses.SensorArray;
import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Move.MoveView;
import edu.bu.pas.pokemon.core.Pokemon.PokemonView;
import edu.bu.pas.pokemon.core.Team.TeamView;
import edu.bu.pas.pokemon.core.enums.Type;
import edu.bu.pas.pokemon.nn.Model;
import edu.bu.pas.pokemon.nn.models.Sequential;
import edu.bu.pas.pokemon.nn.layers.Dense;
import edu.bu.pas.pokemon.nn.layers.ReLU;

import java.util.List;
import java.util.Random;

// JAVA PROJECT IMPORTS
import src.pas.pokemon.senses.CustomSensorArray;

public class PolicyAgent
        extends NeuralQAgent {
    private double epsilon = 0.1;
    private Random random;

    public PolicyAgent() {
        super();
        this.random = new Random();
    }

    public void initializeSenses(Namespace args) {
        SensorArray modelSenses = new CustomSensorArray(this);

        this.setSensorArray(modelSenses);
    }

    @Override
    public void initialize(Namespace args) {
        // make sure you call this, this will call your initModel() and set a field
        // AND if the command line argument "inFile" is present will attempt to set
        // your model with the contents of that file.
        super.initialize(args);

        // what senses will your neural network have?
        this.initializeSenses(args);

        // do what you want just don't expect custom command line options to be
        // available
        // when I'm testing your code
    }

    @Override
    public Model initModel() {
        int inputSize = CustomSensorArray.getInputSize();
        Sequential qFunction = new Sequential();
        qFunction.add(new Dense(inputSize, 128));
        qFunction.add(new ReLU());
        qFunction.add(new Dense(128, 64));
        qFunction.add(new ReLU());
        qFunction.add(new Dense(64, 1));

        return qFunction;
    }

    @Override
    public Integer chooseNextPokemon(BattleView view) {
        TeamView myTeam = this.getMyTeamView(view);
        TeamView oppTeam = this.getOpponentTeamView(view);
        PokemonView oppPokemon = oppTeam.getActivePokemonView();

        int bestIdx = -1;
        double bestScore = -Double.MAX_VALUE;

        // If opponent is alive, try to pick a counter
        boolean oppAlive = !oppPokemon.hasFainted();

        for (int idx = 0; idx < myTeam.size(); ++idx) {
            PokemonView myPokemon = myTeam.getPokemonView(idx);
            if (!myPokemon.hasFainted()) {
                if (bestIdx == -1)
                    bestIdx = idx; // Default to first alive

                double score = 0.0;

                // Prefer higher HP ratio
                score += (double) myPokemon.getCurrentStat(edu.bu.pas.pokemon.core.enums.Stat.HP)
                        / myPokemon.getInitialStat(edu.bu.pas.pokemon.core.enums.Stat.HP) * 10.0;

                if (oppAlive) {
                    // Check defensive advantage: Does opponent type hit me effectively?
                    Type oppType1 = oppPokemon.getCurrentType1();
                    Type oppType2 = oppPokemon.getCurrentType2();
                    Type myType1 = myPokemon.getCurrentType1();
                    Type myType2 = myPokemon.getCurrentType2();

                    double damageTakenMult = 1.0;
                    if (oppType1 != null) {
                        damageTakenMult *= Type.getEffectivenessModifier(oppType1, myType1);
                        if (myType2 != null)
                            damageTakenMult *= Type.getEffectivenessModifier(oppType1, myType2);
                    }
                    if (oppType2 != null) {
                        double mult2 = Type.getEffectivenessModifier(oppType2, myType1);
                        if (myType2 != null)
                            mult2 *= Type.getEffectivenessModifier(oppType2, myType2);
                        damageTakenMult = Math.max(damageTakenMult, mult2); // Assume they use best type
                    }

                    score -= damageTakenMult * 20.0; // Penalize taking more damage
                }

                if (score > bestScore) {
                    bestScore = score;
                    bestIdx = idx;
                }
            }
        }
        if (bestIdx != -1) {
            return bestIdx;
        } else {
            return null;
        }
    }

    @Override
    public MoveView getMove(BattleView view) {
        // Epsilon-greedy exploration
        if (this.random.nextDouble() < this.epsilon) {
            List<MoveView> moves = this.getPotentialMoves(view);
            if (moves.isEmpty())
                return null;
            return moves.get(this.random.nextInt(moves.size()));
        }

        return this.argmax(view);
    }

    @Override
    public void afterGameEnds(BattleView view) {
        // Decay epsilon
        if (this.epsilon > 0.01) {
            this.epsilon *= 0.99;
        }
    }

}
