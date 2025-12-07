package src.pas.pokemon.rewards;

// SYSTEM IMPORTS

// JAVA PROJECT IMPORTS
import edu.bu.pas.pokemon.agents.rewards.RewardFunction;
import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Move.MoveView;
import edu.bu.pas.pokemon.core.Pokemon.PokemonView;
import edu.bu.pas.pokemon.core.Team.TeamView;
import edu.bu.pas.pokemon.core.enums.Stat;
import edu.bu.pas.pokemon.core.enums.Flag;
import edu.bu.pas.pokemon.core.enums.NonVolatileStatus;

public class CustomRewardFunction
        extends RewardFunction {

    private int teamIdx = 0;

    public CustomRewardFunction() {
        super(RewardType.STATE_ACTION_STATE);
    }

    public CustomRewardFunction(int teamIdx) {
        super(RewardType.STATE_ACTION_STATE);
        this.teamIdx = teamIdx;
    }

    @Override
    public double getLowerBound() {
        return -100.0;
    }

    @Override
    public double getUpperBound() {
        return 100.0;
    }

    @Override
    public double getStateReward(BattleView state) {
        return 0;
    }

    @Override
    public double getStateActionReward(BattleView state, MoveView action) {
        return 0;
    }

    @Override
    public double getStateActionStateReward(BattleView state, MoveView action, BattleView nextState) {
        double reward = 0.0;

        // Determine team indices
        int myTeamIdx = this.teamIdx;
        int oppTeamIdx = (this.teamIdx == 0) ? 1 : 0;

        TeamView myTeam = state.getTeamView(myTeamIdx);
        TeamView oppTeam = state.getTeamView(oppTeamIdx);
        TeamView nextMyTeam = nextState.getTeamView(myTeamIdx);
        TeamView nextOppTeam = nextState.getTeamView(oppTeamIdx);

        PokemonView myPoke = myTeam.getActivePokemonView();
        PokemonView oppPoke = oppTeam.getActivePokemonView();
        PokemonView nextMyPoke = nextMyTeam.getActivePokemonView();
        PokemonView nextOppPoke = nextOppTeam.getActivePokemonView();

        // 1. Damage Dealt / KO
        int oppIdx = oppTeam.getActivePokemonIdx();
        int nextOppIdx = nextOppTeam.getActivePokemonIdx();

        if (oppIdx == nextOppIdx) {
            // Same pokemon slot active
            if (nextOppPoke.hasFainted() && !oppPoke.hasFainted()) {
                reward += 10.0; // KO Bonus
            } else {
                double dmg = oppPoke.getCurrentStat(Stat.HP) - nextOppPoke.getCurrentStat(Stat.HP);
                if (dmg > 0) {
                    reward += 10.0 * (dmg / oppPoke.getInitialStat(Stat.HP));
                }
            }
        } else {
            // Opponent switched
            PokemonView prevOppInNext = nextOppTeam.getPokemonView(oppIdx);
            if (prevOppInNext.hasFainted() && !oppPoke.hasFainted()) {
                reward += 10.0; // KO Bonus (caused switch by faint)
            }
        }

        // 2. Damage Taken / Faint
        int myIdx = myTeam.getActivePokemonIdx();
        int nextMyIdx = nextMyTeam.getActivePokemonIdx();

        if (myIdx == nextMyIdx) {
            if (nextMyPoke.hasFainted() && !myPoke.hasFainted()) {
                reward -= 10.0; // Faint Penalty
            } else {
                double dmg = myPoke.getCurrentStat(Stat.HP) - nextMyPoke.getCurrentStat(Stat.HP);
                if (dmg > 0) {
                    reward -= 10.0 * (dmg / myPoke.getInitialStat(Stat.HP));
                }
            }
        } else {
            PokemonView prevMyInNext = nextMyTeam.getPokemonView(myIdx);
            if (prevMyInNext.hasFainted() && !myPoke.hasFainted()) {
                reward -= 10.0; // Faint Penalty
            }
        }

        // 3. Win/Loss
        if (nextState.isOver()) {
            boolean myAlive = false;
            for (int i = 0; i < nextMyTeam.size(); i++) {
                if (!nextMyTeam.getPokemonView(i).hasFainted())
                    myAlive = true;
            }
            boolean oppAlive = false;
            for (int i = 0; i < nextOppTeam.size(); i++) {
                if (!nextOppTeam.getPokemonView(i).hasFainted())
                    oppAlive = true;
            }

            if (myAlive && !oppAlive) {
                reward += 50.0; // Win Bonus
            } else if (!myAlive && oppAlive) {
                reward -= 50.0; // Loss Penalty
            }
        }

        // 5. Status Infliction Reward
        if (oppIdx == nextOppIdx) {
            // Non-Volatile Status (Sleep, Burn, etc.)
            if (oppPoke.getNonVolatileStatus() == NonVolatileStatus.NONE &&
                    nextOppPoke.getNonVolatileStatus() != NonVolatileStatus.NONE) {
                reward += 5.0;
            }

            // Volatile Status (Confusion, Flinch, etc.)
            for (Flag f : Flag.values()) {
                if (!oppPoke.getFlag(f) && nextOppPoke.getFlag(f)) {
                    reward += 5.0;
                }
            }
        }

        // 6. Stat Change Reward
        // Self Buffs
        if (myIdx == nextMyIdx) {
            for (Stat s : Stat.values()) {
                if (s != Stat.HP) {
                    double change = nextMyPoke.getStatMultiplier(s) - myPoke.getStatMultiplier(s);
                    if (change > 0) {
                        reward += 2.5;
                    }
                }
            }
        }
        // Opponent Debuffs
        if (oppIdx == nextOppIdx) {
            for (Stat s : Stat.values()) {
                if (s != Stat.HP) {
                    double change = nextOppPoke.getStatMultiplier(s) - oppPoke.getStatMultiplier(s);
                    if (change < 0) {
                        reward += 2.5;
                    }
                }
            }
        }

        return reward;
    }
}
