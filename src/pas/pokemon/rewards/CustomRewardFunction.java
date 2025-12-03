package src.pas.pokemon.rewards;

import edu.bu.pas.pokemon.agents.rewards.RewardFunction;
import edu.bu.pas.pokemon.agents.rewards.RewardFunction.RewardType;
import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Move.MoveView;
import edu.bu.pas.pokemon.core.Pokemon.PokemonView;
import edu.bu.pas.pokemon.core.Team.TeamView;
import edu.bu.pas.pokemon.core.enums.NonVolatileStatus;
import edu.bu.pas.pokemon.core.enums.Stat;

public class CustomRewardFunction extends RewardFunction
{
    private static final double LOWER = -1.0;
    private static final double UPPER = 1.0;
    // Normalizer controls sensitivity before tanh; adjust to tune mapping
    private static final double NORMALIZER = 12.0;

    public CustomRewardFunction()
    {
        // produce R(s,a,s') for Q-learning
        super(RewardType.STATE_ACTION_STATE);
    }

    public double getLowerBound()
    {
        return LOWER;
    }

    public double getUpperBound()
    {
        return UPPER;
    }

    public double getStateReward(final BattleView state)
    {
        try {
            TeamView my = state.getTeam1View();
            TeamView opp = state.getTeam2View();
            double myAvg = avgHpRatio(my);
            double oppAvg = avgHpRatio(opp);
            double raw = (oppAvg - myAvg) * 5.0;
            return normalize(raw);
        } catch (Exception e) {
            return 0d;
        }
    }

    public double getStateActionReward(final BattleView state,
                                       final MoveView action)
    {
        try {
            double power = safeMovePower(action);
            double raw = 0.01 * power;
            return normalize(raw);
        } catch (Exception e) {
            return 0d;
        }
    }

    public double getStateActionStateReward(final BattleView state,
                                            final MoveView action,
                                            final BattleView nextState)
    {
        try {
            TeamView myBefore = state.getTeam1View();
            TeamView oppBefore = state.getTeam2View();
            TeamView myAfter = nextState.getTeam1View();
            TeamView oppAfter = nextState.getTeam2View();

            double raw = 0.0;

            // 1) Damage to opponent active (scaled)
            double oppHpBefore = activeHp(oppBefore);
            double oppHpAfter = activeHp(oppAfter);
            double dmgToOpp = Math.max(0.0, oppHpBefore - oppHpAfter);
            if (oppHpBefore > 0.0) raw += 6.0 * (dmgToOpp / Math.max(1.0, oppHpBefore));

            // 2) Damage taken by our active (penalize)
            double myHpBefore = activeHp(myBefore);
            double myHpAfter = activeHp(myAfter);
            double dmgTaken = Math.max(0.0, myHpBefore - myHpAfter);
            if (myHpBefore > 0.0) raw -= 8.0 * (dmgTaken / Math.max(1.0, myHpBefore));

            // 3) Faint bonuses
            if (activeFainted(oppAfter) && !activeFainted(oppBefore)) raw += 12.0;
            if (activeFainted(myAfter) && !activeFainted(myBefore)) raw -= 12.0;

            // 4) Team-level relative avg HP progress
            double oppAvgBefore = avgHpRatio(oppBefore);
            double oppAvgAfter = avgHpRatio(oppAfter);
            double myAvgBefore = avgHpRatio(myBefore);
            double myAvgAfter = avgHpRatio(myAfter);
            double teamDelta = (oppAvgBefore - oppAvgAfter) - (myAvgBefore - myAvgAfter);
            raw += 10.0 * teamDelta;

            // 5) Status effects: + for inflicting, - for receiving
            int oppStatusesBefore = statusCount(oppBefore);
            int oppStatusesAfter = statusCount(oppAfter);
            int myStatusesBefore = statusCount(myBefore);
            int myStatusesAfter = statusCount(myAfter);
            raw += 3.0 * (oppStatusesAfter - oppStatusesBefore);
            raw -= 3.0 * (myStatusesAfter - myStatusesBefore);

            // 6) Move-specific shaping
            if (action != null) {
                // small bias toward higher-power moves
                raw += 0.01 * Math.max(0.0, safeMovePower(action));

                // heuristic: reward if significant fraction of opponent HP removed
                if (oppHpBefore > 0.0 && (dmgToOpp / Math.max(1.0, oppHpBefore)) > 0.25) {
                    raw += 2.0;
                } else if (dmgToOpp <= 0.0) {
                    raw -= 0.5; // wasted move
                }

                // PP penalty if API available
                try {
                    int beforePP = action.getPP();
                    int afterPP = action.getPP() - 1;
                    int used = Math.max(0, beforePP - afterPP);
                    raw -= 0.2 * used;
                } catch (Exception ignored) {}

                // Accuracy penalty if API available
                try {
                    double accuracy = action.getAccuracy(); // e.g., 0.8 for 80%
                    raw -= 0.05 * (1.0 - accuracy);
                }catch (Exception ignored) {}

                //Light Screen or Protect move bonus
                try {
                    String moveName = action.getName().toLowerCase();
                    if (moveName.contains("protect") || moveName.contains("light screen") || moveName.contains("reflect")) {
                        raw += 1.0;
                    }else{
                        raw += 0.5*(nextState.getTeam1View().getNumLightScreenTurnsRemaining() + nextState.getTeam1View().getNumReflectTurnsRemaining());
                    }
                }catch (Exception ignored) {}

            }

            // normalize to [-1,1]
            return normalize(raw);

        } catch (Exception e) {
            return 0d;
        }
    }

    private double normalize(double raw) {
        if (Double.isNaN(raw) || Double.isInfinite(raw)) return 0.0;
        // tanh maps to (-1,1); clamp just in case
        double n = Math.tanh(raw / NORMALIZER);
        if (n != n) return 0.0;
        return Math.max(LOWER, Math.min(UPPER, n));
    }

    private double activeHp(TeamView team) {
        try {
            if (team == null) return 0.0;
            PokemonView p = team.getActivePokemonView();
            if (p == null) return 0.0;
            return Math.max(0.0, p.getCurrentStat(Stat.HP));
        } catch (Exception e) {
            return 0.0;
        }
    }

    private boolean activeFainted(TeamView team) {
        try {
            if (team == null) return true;
            PokemonView p = team.getActivePokemonView();
            return p == null || p.hasFainted();
        } catch (Exception e) {
            return true;
        }
    }

    private double avgHpRatio(TeamView team) {
        try {
            if (team == null) return 0.0;
            double sum = 0.0;
            int cnt = 0;
            for (int idx = 0; idx < team.size(); idx++) {
                PokemonView p = team.getPokemonView(idx);
                if (p == null) continue;
                double cur = (double) p.getCurrentStat(Stat.HP);
                double max = Math.max(1.0, (double) p.getBaseStat(Stat.HP));
                sum += (cur / max);
                cnt++;
            }
            return cnt == 0 ? 0.0 : sum / cnt;
        } catch (Exception e) {
            return 0.0;
        }
    }

    private int statusCount(TeamView team) {
        try {
            if (team == null) return 0;
            int c = 0;
            for (int idx = 0; idx < team.size(); idx++) {
                PokemonView p = team.getPokemonView(idx);
                if (p == null) continue;
                try {
                    if (p.getNonVolatileStatus() != null) c++;
                } catch (Exception ignored) {}
            }
            return c;
        } catch (Exception e) {
            return 0;
        }
    }

    private double safeMovePower(MoveView m) {
        if (m == null) return 0.0;
        try { return m.getPower(); } catch (Exception e) { return 0.0; }
    }
}
