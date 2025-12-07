package src.pas.pokemon.senses;

// SYSTEM IMPORTS

// JAVA PROJECT IMPORTS
import edu.bu.pas.pokemon.agents.senses.SensorArray;
import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Move;
import edu.bu.pas.pokemon.core.Move.MoveView;
import edu.bu.pas.pokemon.core.Pokemon;
import edu.bu.pas.pokemon.core.Pokemon.PokemonView;
import edu.bu.pas.pokemon.core.SwitchMove;
import edu.bu.pas.pokemon.core.Team.TeamView;
import edu.bu.pas.pokemon.core.DamageEquation;
import edu.bu.pas.pokemon.core.enums.*;
import src.pas.pokemon.agents.PolicyAgent;
import edu.bu.pas.pokemon.linalg.Matrix;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class CustomSensorArray
        extends SensorArray {

    private PolicyAgent agent;
    private static final int NUM_TYPES = Type.values().length; // 15 Types
    private static final int NUM_STATS = Stat.values().length; // 8 Stats
    private static final int NUM_NON_VOLATILE = NonVolatileStatus.values().length; // 7 Non-Volatile Statuses
    private static final int NUM_HEIGHTS = Height.values().length; // 3 Heights
    public CustomSensorArray(PolicyAgent agent) {
        this.agent = agent;
    }
    private void addFlags(final List<Double> feats, final PokemonView p)
    {
        // Add binary features for each flag enum
        for(Flag f : Flag.values())
        {
            feats.add((p != null && p.getFlag(f)) ? 1d : 0d);
        }
    }

    // Helper to add status counters for a Pokemon (Dim = 7)
    private void addStatusCounters(final List<Double> feats, final PokemonView p)
    {
        // Normalize per status using mechanic-aware caps.
        for(NonVolatileStatus s : NonVolatileStatus.values())
        {
            final double v = (p == null) ? 0d : p.getNonVolatileStatusCounter(s);
            switch(s)
            {
                case SLEEP:
                case FREEZE:
                    feats.add(normCounter(v, 7d)); // max 7 turns asleep/frozen
                    break;
                case TOXIC:
                    feats.add(normCounter(v, 5d)); // after 5 turns toxic has done 15/16 HP
                    break;
                case PARALYSIS:
                case POISON:
                case BURN:
                case NONE:
                default:
                    feats.add(0d); // counter not meaningful; keep zero
                    break;
            }
        }
    }

    // Helper to add flag counters for a Pokemon (Dim = 3)
    private void addFlagCounters(final List<Double> feats, final PokemonView p)
    {
        // Confusion: 2-5 turns, Trapped: max move does 5, Seeded: binary until switch.
        feats.add(normCounter(p == null ? 0d : p.getFlagCounter(Flag.CONFUSED), 5d));
        feats.add(normCounter(p == null ? 0d : p.getFlagCounter(Flag.TRAPPED), 5d));
        feats.add((p != null && p.getFlag(Flag.SEEDED)) ? 1d : 0d);
    }
    private void addOneHot(final List<Double> feats, final int length, final int idx)
    {
        for(int i = 0; i < length; ++i)
        {
            feats.add(i == idx ? 1d : 0d);
        }
    }

    // Helper to add one-hot encoding for Pokemon types (handles dual-types)
    private void addTypeOneHot(final List<Double> feats, final Type t1, final Type t2)
    {
        addOneHot(feats, NUM_TYPES, t1 == null ? -1 : t1.ordinal());
        addOneHot(feats, NUM_TYPES, t2 == null ? -1 : t2.ordinal());
    }

    // Helper to normalize power to [0, 1]
    private double normPower(final Integer power)
    {
        // If power is null, treat as 0 power move
        if(power == null)
        {
            return 0d;
        }
        // Max power in Pokemon is ~255 (e.g., Explosion 250, realistic second is Hyper Beam 150))
        return clamp(power / 255d, 0d, 1d);
    }

    // Helper to normalize accuracy to [0, 1]
    private double normAccuracy(final Integer acc)
    {
        // If accuaracy is null, it always hits with 100%
        if(acc == null)
        {
            return 1d;
        }
        return clamp(acc / 100d, 0d, 1d);
    }

    // Helper to normalize priority to [-1, 1]
    private double normPriority(final int prio)
    {
        // getPriority returns -1, 0, or +1; clamp directly to keep scale consistent.
        return clamp(prio, -1d, 1d);
    }

    // Helper to normalize index within a size to [0, 1]
    private double normIndex(final int idx, final int size)
    {
        // If size is 1 or less, return 0 since there is no spread
        if(size <= 1)
        {
            return 0d;
        }
        return clamp(idx / (double)(size - 1), 0d, 1d);
    }

    // Helper to normalize counters to [0, 1]
    private double normCounter(final double val, final double max)
    {
        return clamp(val / max, 0d, 1d);
    }

    // Helper to clamp a value within min and max bounds
    private double clamp(final double val, final double low, final double high)
    {
        return Math.max(low, Math.min(high, val));
    }

    // Helper to convert feature list to a row matrix
    private Matrix toRowMatrix(final List<Double> feats)
    {
        final Matrix m = Matrix.zeros(1, feats.size());
        for(int i = 0; i < feats.size(); ++i)
        {
            m.set(0, i, feats.get(i));
        }
        return m;
    }

    // The size of the input vector for the neural network.
    public static int getInputSize() {
        int numTypes = Type.values().length;
        int numStats = Stat.values().length;
        int numStatus = NonVolatileStatus.values().length;
        int numFlags = Flag.values().length;

        // My Pokemon: HP Ratio (1) + Type1 (N) + Type2 (N) + Stats (S) + Status (St) +
        // Flags (F)
        int pokemonFeatures = 1 + (2 * numTypes) + numStats + numStatus + numFlags;

        // Opponent Pokemon: Same
        int opponentFeatures = pokemonFeatures;

        // Features per Move (4 moves):
        // - Expected Damage (1)
        // - Expected Status (1)
        // - Priority (1)
        int moveFeatures = 3;

        return pokemonFeatures + opponentFeatures + (moveFeatures * 4);
    }
    private double hpFraction(final PokemonView p)
    {
        final double maxHp = Math.max(1d, p.getInitialStat(Stat.HP));
        final double currHp = Math.max(0d, p.getCurrentStat(Stat.HP));
        return clamp(currHp / maxHp, 0d, 1d);
    }
    private void addPokemonFeatures(final List<Double> feats, final PokemonView p)
    {
        // If Pokemon is null or fainted, add default values
        if(p == null || p.hasFainted())
        {
            // Add stat ratios placeholders for each stat (Dim = 8)
            for(int i = 0; i < NUM_STATS; ++i)
            {
                feats.add(0d);
            }
            // Add one-hot for non-volatile status: Burn, Freeze, Paralysis, Poison, Sleep, Toxic, None (Dim = 7)
            addOneHot(feats, NUM_NON_VOLATILE, -1);
            // Add flags: Confused, Flinched, Focus_Energy, Seeded, Trapped (Dim = 5)
            addFlags(feats, null);
            // Add one-hot for height: In_Air, None, Underground (Dim = 3)
            addOneHot(feats, NUM_HEIGHTS, -1);
            // Add status counters for non-volatile statuses (Dim = 7)
            addStatusCounters(feats, null);
            // Add flag counters for timed flags: Confused, Seeded, Trapped (Dim = 3)
            addFlagCounters(feats, null);
            // Add type one-hot encodings for both types (includes dual-types) (Dim = 2 x 15 = 30)
            addTypeOneHot(feats, null, null);
            return;
        }

        // Add HP fraction (Dim = 1)
        feats.add(hpFraction(p));

        // Add stat ratios for each stat (Dim = 7)
        for(Stat s : Stat.values())
        {
            // Skip HP since we already added it
            if(s == Stat.HP)
            {
                continue;
            }
            // Prevent division by zero
            final double base = Math.max(1d, p.getInitialStat(s));
            // Clamp to [0, 4] range to avoid outliers for more stability
            final double ratio = clamp(p.getCurrentStat(s) / base, 0d, 4d);
            feats.add(ratio);
        }

        // Add one-hot for non-volatile status: Burn, Freeze, Paralysis, Poison, Sleep, Toxic, None (Dim = 7)
        addOneHot(feats, NUM_NON_VOLATILE, p.getNonVolatileStatus().ordinal());
        // Add flags: Confused, Flinched, Focus_Energy, Seeded, Trapped (Dim = 5)
        addFlags(feats, p);
        // Add one-hot for height: In_Air, None, Underground (Dim = 3)
        addOneHot(feats, NUM_HEIGHTS, p.getHeight().ordinal());
        // Add status counters for non-volatile statuses (Dim = 7)
        addStatusCounters(feats, p);
        // Add flag counters for timed flags: Confused, Seeded, Trapped (Dim = 3)
        addFlagCounters(feats, p);
        // Add type one-hot encodings for both types (includes dual-types) (Dim = 2 x 15 = 30)
        addTypeOneHot(feats, p.getCurrentType1(), p.getCurrentType2());
    }
    private void addActionFeatures(final List<Double> feats,
                                   final TeamView ourTeam,
                                   final PokemonView ourActive,
                                   final PokemonView oppActive,
                                   final MoveView action)
    {
        // If no action, add default values (Dim = 1 + 1 + 1 + 30 + 1 + 1 + 1 + 3 + 30 + 1 + 1 = 71)
        if(action == null)
        {
            feats.add(0d); // isSwitch
            feats.add(0d); // switch slot
            feats.add(0d); // switch hp
            addTypeOneHot(feats, null, null);
            feats.add(0d); // accuracy
            feats.add(0d); // priority
            feats.add(0d);
            return;
        }

        // If action is a switch move, add switch-specific features
        if(action instanceof SwitchMove.SwitchMoveView)
        {
            // If action is a switch move, cast to SwitchMoveView
            final SwitchMove.SwitchMoveView smv = (SwitchMove.SwitchMoveView)action;
            // Get the target index to switch to
            final int targetIdx = smv.getNewActiveIdx();
            // Set the isSwitch flag to 1 (Dim = 1)
            feats.add(1d);
            // Add where the targetIdx is located in the team (Dim = 1)
            feats.add(normIndex(targetIdx, ourTeam == null ? 1 : ourTeam.size()));

            // Get the target Pokemon to switch to
            PokemonView target = null;
            if(ourTeam != null && targetIdx >= 0 && targetIdx < ourTeam.size())
            {
                target = ourTeam.getPokemonView(targetIdx);
            }
            // Add the target's HP fraction and type one-hot encodings (Dim = 1 + 30 = 31)
            feats.add(target == null ? 0d : hpFraction(target));
            feats.add(0d); // accuracy
            feats.add(0d); // priority placeholder (Dim = 1)
            feats.add(0d);
            addTypeOneHot(feats, target == null ? null : target.getCurrentType1(), target == null ? null : target.getCurrentType2());
            return;
        }


        // If it's a regular move, we need to add the move-specific features (Dim = 1 + 1 + 1 + 30 + 1 + 1 + 1 + 3 + 30 + 1 + 1 = 71)
        feats.add(0d); // isSwitch
        feats.add(0d); // switch slot placeholder
        feats.add(0d); // switch hp placeholder
        feats.add(normAccuracy(action.getAccuracy()));
        feats.add(normPriority(action.getPriority()));
        Move moveCore = new Move(action);
        Pokemon myPokemonCore = Pokemon.fromView(ourActive);
        Pokemon oppPokemonCore = Pokemon.fromView(oppActive);

        // Expected Damage
        double effectiveness = 1.0;
        Type moveType = moveCore.getType();
        Type oppType1 = oppPokemonCore.getCurrentType1();
        Type oppType2 = oppPokemonCore.getCurrentType2();

        effectiveness *= Type.getEffectivenessModifier(moveType, oppType1);
        if (oppType2 != null) {
            effectiveness *= Type.getEffectivenessModifier(moveType, oppType2);
        }
        double damage = DamageEquation.calculateDamage(new Move(action), this.agent.getMyTeamIdx(), myPokemonCore, oppPokemonCore, false,
                    false, 0, effectiveness);
        // Normalize damage.
        feats.add(Math.min(damage / 1000.0, 1.0));
        addTypeOneHot(feats, null, null); // switch type placeholder

    }
    private void addSpeedFeatures(final List<Double> feats,
                                  final PokemonView ours,
                                  final PokemonView opp)
    {
        // Get the speed stats for both Pokemon
        final double ourSpd = (ours == null) ? 0d : Math.max(0d, ours.getCurrentStat(Stat.SPD));
        final double oppSpd = (opp == null) ? 0d : Math.max(0d, opp.getCurrentStat(Stat.SPD));
        // Avoid division by zero
        final double denominator = Math.max(1d, ourSpd + oppSpd);
        // Add a flag to represent if we outspeed or not
        feats.add(ourSpd > oppSpd ? 1d : 0d);
        // Add relative speed feature normalized to [-1, 1]
        feats.add(clamp((ourSpd - oppSpd) / denominator, -1d, 1d));
    }
    private void addTeamContext(final List<Double> feats, final TeamView team)
    {
        int living = 0;
        double hpSum = 0d;
        // Count living Pokemon and sum their HP fractions
        for(int idx = 0; idx < team.size(); ++idx)
        {
            final PokemonView p = team.getPokemonView(idx);
            if(p != null && !p.hasFainted())
            {
                living += 1;
            }
            hpSum += (p == null) ? 0d : hpFraction(p);
        }
        // Add fraction of team left alive (alive pokemon %)
        feats.add(team.size() == 0 ? 0d : ((double)living / (double)team.size()));
        // Add average HP fraction across the team
        feats.add(team.size() == 0 ? 0d : hpSum / (double)team.size());
    }
    @Override
    public Matrix getSensorValues(final BattleView state, final MoveView action) {
        int size = getInputSize();
        List<Double> sensors = new LinkedList<>();
        int idx = 0;

        // Determine team indices
        int myTeamIdx = this.agent.getMyTeamIdx();
        int oppTeamIdx;
        if (myTeamIdx == 0) {
            oppTeamIdx = 1;
        } else {
            oppTeamIdx = 0;
        }

        TeamView myTeam = state.getTeamView(myTeamIdx);
        TeamView oppTeam = state.getTeamView(oppTeamIdx);

        PokemonView myPokemon = myTeam.getActivePokemonView();
        PokemonView oppPokemon = oppTeam.getActivePokemonView();

        // My Active Pokemon Features
        addPokemonFeatures(sensors, myPokemon);

        // Opponent Active Pokemon Features
        addPokemonFeatures(sensors, oppPokemon);
        addActionFeatures(sensors, myTeam, myPokemon, oppPokemon, action);
        addSpeedFeatures(sensors, myPokemon, oppPokemon);
        sensors.add(normCounter(myTeam.getNumLightScreenTurnsRemaining(), 5d));
        sensors.add(normCounter(myTeam.getNumReflectTurnsRemaining(), 5d));
        sensors.add(normCounter(oppTeam.getNumLightScreenTurnsRemaining(), 5d));
        sensors.add(normCounter(oppTeam.getNumReflectTurnsRemaining(), 5d));
        addTeamContext(sensors, myTeam);
        addTeamContext(sensors, oppTeam);
        return toRowMatrix(sensors);
    }
}
