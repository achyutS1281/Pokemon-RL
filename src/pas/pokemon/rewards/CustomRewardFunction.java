package src.pas.pokemon.rewards;


// SYSTEM IMPORTS


// JAVA PROJECT IMPORTS
import edu.bu.pas.pokemon.agents.rewards.RewardFunction;
import edu.bu.pas.pokemon.agents.rewards.RewardFunction.RewardType;
import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Move.MoveView;
import edu.bu.pas.pokemon.core.Pokemon.PokemonView;
import edu.bu.pas.pokemon.core.Team.TeamView;


public class CustomRewardFunction
    extends RewardFunction
{

    public CustomRewardFunction()
    {
        super(RewardType.STATE); // currently configured to produce rewards as a function of the state
    }

    public double getLowerBound()
    {
        // TODO: change this. Reward values must be finite!
        return Double.POSITIVE_INFINITY;
    }

    public double getUpperBound()
    {
        // TODO: change this. Reward values must be finite!
        return Double.NEGATIVE_INFINITY;
    }

    public double getStateReward(final BattleView state)
    {
        return 0d;
    }

    public double getStateActionReward(final BattleView state,
                                       final MoveView action)
    {
        return 0d;
    }

    public double getStateActionStateReward(final BattleView state,
                                            final MoveView action,
                                            final BattleView nextState)
    {
        return 0d;
    }

}
