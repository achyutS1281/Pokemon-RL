package src.pas.pokemon.senses;


// SYSTEM IMPORTS


// JAVA PROJECT IMPORTS
import edu.bu.pas.pokemon.agents.senses.SensorArray;
import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Move.MoveView;
import edu.bu.pas.pokemon.core.Pokemon.PokemonView;
import edu.bu.pas.pokemon.core.Team.TeamView;
import edu.bu.pas.pokemon.linalg.Matrix;


public class CustomSensorArray
    extends SensorArray
{

    // TODO: make fields if you want!

    public CustomSensorArray()
    {
        // TODO: intialize those fields if you make any!
    }

    public Matrix getSensorValues(final BattleView state, final MoveView action)
    {
        // TODO: Convert a BattleView and a MoveView into a row-vector containing measurements for every sense
        // you want your neural network to have. This method should be called if your model is a q-based model

        // currently returns 64 random numbers
        return Matrix.randn(1, 64);
    }

}
