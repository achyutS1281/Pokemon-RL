package src.pas.pokemon.agents;


// SYSTEM IMPORTS
import edu.bu.pas.pokemon.core.Pokemon;
import edu.bu.pas.pokemon.core.enums.Stat;
import edu.bu.pas.pokemon.core.enums.Type;
import net.sourceforge.argparse4j.inf.Namespace;

import edu.bu.pas.pokemon.agents.NeuralQAgent;
import edu.bu.pas.pokemon.agents.senses.SensorArray;
import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Move.MoveView;
import edu.bu.pas.pokemon.linalg.Matrix;
import edu.bu.pas.pokemon.nn.Model;
import edu.bu.pas.pokemon.nn.models.Sequential;
import edu.bu.pas.pokemon.nn.layers.Dense; // fully connected layer
import edu.bu.pas.pokemon.nn.layers.ReLU;  // some activations (below too)
import edu.bu.pas.pokemon.nn.layers.Tanh;
import edu.bu.pas.pokemon.nn.layers.Sigmoid;


// JAVA PROJECT IMPORTS
import src.pas.pokemon.senses.CustomSensorArray;

import java.util.List;


public class PolicyAgent
    extends NeuralQAgent
{

    public PolicyAgent()
    {
        super();
    }

    public void initializeSenses(Namespace args)
    {
        SensorArray modelSenses = new CustomSensorArray();

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
        // TODO: create your neural network

        // currently this creates a one-hidden-layer network
        Sequential qFunction = new Sequential();
        qFunction.add(new Dense(64, 32)); // input layer (64 inputs, 32 outputs)
        qFunction.add(new ReLU());        // activation
        qFunction.add(new Dense(32, 16)); // hidden layer (32 inputs
        qFunction.add(new ReLU());        // activation
        qFunction.add(new Dense(16, 4));  // output layer (16
        qFunction.add(new Tanh());       // activation


        return qFunction;
    }

    @Override
    public Integer chooseNextPokemon(BattleView view)
    {
        // TODO: change this to something more intelligent!

        //Choose a pokemon that is resistant to the opponent's active pokemon type and has high HP. If none, choose the one with lowest hp to sacrifice.
        for (int i = 0; i < view.getTeam1View().size(); i++) {
            Pokemon.PokemonView pokemon = view.getTeam1View().getPokemonView(i);
            if (!pokemon.hasFainted()) {
                Pokemon.PokemonView oppActive = view.getTeam2View().getActivePokemonView();
                List<Type> oppTypes = List.of(new Type[]{oppActive.getCurrentType1(), oppActive.getCurrentType2()});
                List<Type> myTypes = List.of(new Type[]{pokemon.getCurrentType1(), pokemon.getCurrentType2()});
                boolean resistant = false;
                for (Type myType : myTypes) {
                    for (Type oppType : oppTypes) {
                        if (Type.getEffectivenessModifier(myType, oppType) < 1.0) {
                            resistant = true;
                            break;
                        }
                    }
                    if (resistant) break;
                }
                if (resistant && pokemon.getCurrentStat(Stat.HP) > pokemon.getBaseStat(Stat.HP) * 0.3) {
                    return i;
                }
            }
        }
        return null;
    }

    @Override
    public MoveView getMove(BattleView view)
    {
        // TODO: change this to include random exploration during training and maybe use the transition model to make
        // good predictions?
        // if you choose to use the transition model you might want to also override the makeGroundTruth(...) method
        // to not use temporal difference learning

        // currently always tries to argmax the learned model
        // this is not a good idea to always do when training. When playing evaluation games you *do* want to always
        // argmax your model, but when training our model may not know anything yet! So, its a good idea to sometime
        // during training choose *not* to argmax the model and instead choose something new at random.

        // HOW that randomness works and how often you do it are up to you, but it *will* affect the quality of your
        // learned model whether you do it or not!

        double epsilon = 0.1; // exploration rate
        if (Math.random() < epsilon) {
            // choose a random move
            List<MoveView> moves = this.getPotentialMoves(view);
            epsilon *= 0.99; // decay epsilon
            return moves.get((int) (Math.random() * moves.size()));
        } else {
            // choose the best move according to the model
            return this.argmax(view);
        }
    }

    @Override
    public void afterGameEnds(BattleView view)
    {

    }

}

