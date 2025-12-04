package src.pas.pokemon.rewards;


// SYSTEM IMPORTS


// JAVA PROJECT IMPORTS
import edu.bu.pas.pokemon.agents.rewards.RewardFunction;
import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Move.MoveView;
import edu.bu.pas.pokemon.core.Pokemon.PokemonView;
import edu.bu.pas.pokemon.core.Team.TeamView;
import edu.bu.pas.pokemon.core.enums.NonVolatileStatus;
import edu.bu.pas.pokemon.core.enums.Stat;


public class CustomRewardFunction
    extends RewardFunction
{

    public CustomRewardFunction()
    {
        // Configured to produce rewards as a function of R(s, a, s')
        super(RewardType.STATE_ACTION_STATE);
    }

    public double getLowerBound()
    {
        return -1d;
    }

    public double getUpperBound()
    {
        // Reward values must be finite: [-1, +1]
        return +1d;
    }

    // Unused
    public double getStateReward(final BattleView state)
    {
        // Reward values must be finite: [-1, +1]
        return 0d;
    }

    // Unused
    public double getStateActionReward(final BattleView state,
                                       final MoveView action)
    {
        return 0d;
    }

    // Helper methods for our reward "shaping" components

    // Helper to check if a team has living Pokemon remaining
    private boolean hasLivingPokemon(final TeamView team)
    {
        // Check each Pokemon on the team and see if they are alive
        for(int idx = 0; idx < team.size(); ++idx)
        {
            final PokemonView p = team.getPokemonView(idx);
            if(p != null && !p.hasFainted())
            {
                return true;
            }
        }
        return false;
    }

    // Helper to count number of fainted Pokemon on a team
    private int countFainted(final TeamView team)
    {
        int count = 0;
        for(int idx = 0; idx < team.size(); ++idx)
        {
            final PokemonView p = team.getPokemonView(idx);
            if(p == null || p.hasFainted())
            {
                count += 1;
            }
        }
        return count;
    }

    // Helper to count number of statused Pokemon on a team
    private int countStatused(final TeamView team)
    {
        int count = 0;
        for(int idx = 0; idx < team.size(); ++idx)
        {
            final PokemonView p = team.getPokemonView(idx);
            // Non-Volatile Status includes Burn, Freeze, Paralysis, Poison, Sleep, Toxic
            if(p != null && p.getNonVolatileStatus() != NonVolatileStatus.NONE)
            {
                count += 1;
            }
        }
        return count;
    }

    // Helper to calculate the HP advantage of our team over the opponent's team
    private double hpAdvantage(final TeamView ours, final TeamView opp)
    {
        return averageHpFraction(ours) - averageHpFraction(opp);
    }

    // Helper to calculate the HP advantage of our team's bench over the opponent's bench
    private double benchHpAdvantage(final TeamView ours, final TeamView opp)
    {
        return benchHpFraction(ours) - benchHpFraction(opp);
    }

    // Helper to calculate the average HP fraction of a team
    private double averageHpFraction(final TeamView team)
    {
        double total = 0d;
        int count = 0;
        for(int idx = 0; idx < team.size(); ++idx)
        {
            final PokemonView p = team.getPokemonView(idx);
            if(p != null)
            {
                // Call hpFraction helper to get individual Pokemon HP fraction
                total += hpFraction(p);
                count += 1;
            }
        }
        return (count == 0) ? 0d : total / (double)count;
    }

    // Helper to calculate the average HP fraction of a team's bench (non-active Pokemon)
    private double benchHpFraction(final TeamView team)
    {
        final int activeIdx = team.getActivePokemonIdx();
        double total = 0d;
        int count = 0;
        for(int idx = 0; idx < team.size(); ++idx)
        {
            // Skip the active Pokemon
            if(idx == activeIdx)
            {
                continue;
            }

            final PokemonView p = team.getPokemonView(idx);
            if(p != null)
            {
                // Call hpFraction helper to get individual Pokemon HP fraction
                total += hpFraction(p);
                count += 1;
            }
        }
        return (count == 0) ? 0d : total / (double)count;
    }

    // Helper to calculate the HP fraction of a single Pokemon
    private double hpFraction(final PokemonView pokemon)
    {
        // Max to avoid division by zero
        final double maxHp = Math.max(1d, pokemon.getInitialStat(Stat.HP));
        // Current HP cannot be negative (fainted = 0)
        final double currHp = Math.max(0d, pokemon.getCurrentStat(Stat.HP));
        return clamp(currHp / maxHp, 0d, 1d);
    }

    // Helper to calculate the speed edge of our active Pokemon over the opponent's active Pokemon
    private double speedEdge(final TeamView ours, final TeamView opp)
    {
        final double ourSpd = activeSpeed(ours);
        final double oppSpd = activeSpeed(opp);
        final double denominator = Math.max(1d, ourSpd + oppSpd);
        return clamp((ourSpd - oppSpd) / denominator, -1d, 1d);
    }

    // Helper to get the speed of the active Pokemon on a team
    private double activeSpeed(final TeamView team)
    {
        final PokemonView active = team.getActivePokemonView();
        // If no active Pokemon, speed is 0 (but this basically means you lost)
        if(active == null)
        {
            return 0d;
        }
        return Math.max(0d, active.getCurrentStat(Stat.SPD));
    }

    // Helper to clamp a value within min and max bounds (insurance)
    private double clamp(final double value, final double min, final double max)
    {
        return Math.max(min, Math.min(max, value));
    }

    // Main reward function: R(s, a, s')
    public double getStateActionStateReward(final BattleView state,
                                            final MoveView action,
                                            final BattleView nextState)
    {
        // If the state or nextState is null, return neurtral reward
        if(state == null || nextState == null)
        {
            return 0d;
        }

        // Terminal Rewards (Win/Loss/Draw)
        if(nextState.isOver())
        {
            // Check if either side still has living Pokemon
            final boolean ourTeamAlive = hasLivingPokemon(nextState.getTeam1View());
            final boolean oppTeamAlive = hasLivingPokemon(nextState.getTeam2View());

            // If we are alive and opponent is not, we win
            if(ourTeamAlive && !oppTeamAlive)
            {
                return this.getUpperBound();
            }
            // If opponent is alive and we are not, we lose
            if(!ourTeamAlive && oppTeamAlive)
            {
                return this.getLowerBound();
            }
            // Otherwise, it's a draw
            return 0d; 
        }

        // Extract team views before and after the action
        final TeamView ourTeamBefore = state.getTeam1View();
        final TeamView oppTeamBefore = state.getTeam2View();
        final TeamView ourTeamAfter = nextState.getTeam1View();
        final TeamView oppTeamAfter = nextState.getTeam2View();

        // Initialize cumulative reward
        double reward = 0d;

        // HP swing across full team (includes bench)
        final double hpAdvantageBefore = hpAdvantage(ourTeamBefore, oppTeamBefore);
        final double hpAdvantageAfter = hpAdvantage(ourTeamAfter, oppTeamAfter);
        reward += 0.30d * clamp(hpAdvantageAfter - hpAdvantageBefore, -2d, 2d);

        // KO events this turn (if a knock out happened this turn)
        final int oppNewDeaths = countFainted(oppTeamAfter) - countFainted(oppTeamBefore);
        final int ourNewDeaths = countFainted(ourTeamAfter) - countFainted(ourTeamBefore);
        reward += 0.45d * (oppNewDeaths - ourNewDeaths);

        // Status changes (check if we inflicted new non-volatile statuses)
        final int oppNewStatuses = countStatused(oppTeamAfter) - countStatused(oppTeamBefore);
        final int ourNewStatuses = countStatused(ourTeamAfter) - countStatused(ourTeamBefore);
        reward += 0.08d * (oppNewStatuses - ourNewStatuses);

        // Check if we gained a speed advantage (attacking first)
        final double speedEdgeBefore = speedEdge(ourTeamBefore, oppTeamBefore);
        final double speedEdgeAfter = speedEdge(ourTeamAfter, oppTeamAfter);
        reward += 0.05d * (speedEdgeAfter - speedEdgeBefore);

        // Bench health preservation (exclude current active pokemon)
        final double benchHpAdvantageBefore = benchHpAdvantage(ourTeamBefore, oppTeamBefore);
        final double benchHpAdvantageAfter = benchHpAdvantage(ourTeamAfter, oppTeamAfter);
        reward += 0.12d * (benchHpAdvantageAfter - benchHpAdvantageBefore);

        // Clamp final reward within bounds [-1, +1]
        return clamp(reward, getLowerBound(), getUpperBound());
    }
}
