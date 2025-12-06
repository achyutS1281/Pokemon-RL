package pas.pokemon.senses;

// SYSTEM IMPORTS

// JAVA PROJECT IMPORTS
import edu.bu.pas.pokemon.agents.senses.SensorArray;
import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Move;
import edu.bu.pas.pokemon.core.Move.MoveView;
import edu.bu.pas.pokemon.core.Pokemon;
import edu.bu.pas.pokemon.core.Pokemon.PokemonView;
import edu.bu.pas.pokemon.core.Team.TeamView;
import edu.bu.pas.pokemon.core.DamageEquation;
import edu.bu.pas.pokemon.core.enums.Flag;
import edu.bu.pas.pokemon.core.enums.NonVolatileStatus;
import edu.bu.pas.pokemon.core.enums.Stat;
import edu.bu.pas.pokemon.core.enums.Type;
import pas.pokemon.agents.PolicyAgent;
import edu.bu.pas.pokemon.linalg.Matrix;

public class CustomSensorArray
        extends SensorArray {

    private PolicyAgent agent;

    public CustomSensorArray(PolicyAgent agent) {
        this.agent = agent;
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

    private int addPokemonFeatures(Matrix sensors, int idx, PokemonView pokemon) {
        // HP Ratio
        double hpRatio = (double) pokemon.getCurrentStat(Stat.HP) / pokemon.getInitialStat(Stat.HP);
        sensors.set(0, idx++, hpRatio);

        // Type 1 (One-hot)
        Type t1 = pokemon.getCurrentType1();
        for (Type t : Type.values()) {
            sensors.set(0, idx++, (t == t1) ? 1.0 : 0.0);
        }

        // Type 2 (One-hot)
        Type t2 = pokemon.getCurrentType2();
        for (Type t : Type.values()) {
            sensors.set(0, idx++, (t == t2) ? 1.0 : 0.0);
        }

        // Stats
        for (Stat s : Stat.values()) {
            if (s == Stat.HP) {
                sensors.set(0, idx++, 0.0); // Skip HP as we used ratio, or just set 0 placeholder
            } else {
                // Use current stat value normalized
                sensors.set(0, idx++, pokemon.getCurrentStat(s) / 1000.0);
            }
        }

        // Non-Volatile Status (One-hot)
        NonVolatileStatus status = pokemon.getNonVolatileStatus();
        for (NonVolatileStatus s : NonVolatileStatus.values()) {
            sensors.set(0, idx++, (s == status) ? 1.0 : 0.0);
        }

        // Volatile Status Flags
        for (Flag f : Flag.values()) {
            sensors.set(0, idx++, pokemon.getFlag(f) ? 1.0 : 0.0);
        }

        return idx;
    }

    @Override
    public Matrix getSensorValues(final BattleView state, final MoveView action) {
        int size = getInputSize();
        Matrix sensors = Matrix.zeros(1, size);
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
        idx = addPokemonFeatures(sensors, idx, myPokemon);

        // Opponent Active Pokemon Features
        idx = addPokemonFeatures(sensors, idx, oppPokemon);

        // Action Features
        if (action != null) {
            Move moveCore = new Move(action);
            Pokemon myPokemonCore = Pokemon.fromView(myPokemon);
            Pokemon oppPokemonCore = Pokemon.fromView(oppPokemon);

            // Expected Damage
            double effectiveness = 1.0;
            Type moveType = moveCore.getType();
            Type oppType1 = oppPokemonCore.getCurrentType1();
            Type oppType2 = oppPokemonCore.getCurrentType2();

            effectiveness *= Type.getEffectivenessModifier(moveType, oppType1);
            if (oppType2 != null) {
                effectiveness *= Type.getEffectivenessModifier(moveType, oppType2);
            }

            // calculateDamage(Move move, int casterIdx, Pokemon caster, Pokemon target,
            // boolean isCrit, boolean isRandom, int randomSeed, double effectiveness)
            double damage = DamageEquation.calculateDamage(moveCore, myTeamIdx, myPokemonCore, oppPokemonCore, false,
                    false, 0, effectiveness);

            // Normalize damage.
            sensors.set(0, idx++, Math.min(damage / 1000.0, 1.0));

            // Expected Status
            boolean isStatus = moveCore.getCategory() == Move.Category.STATUS;
            sensors.set(0, idx++, isStatus ? 1.0 : 0.0);

            // Priority
            sensors.set(0, idx++, (action.getPriority() + 1.0) / 3.0);
        } else {
            // No action
            sensors.set(0, idx++, 0.0);
            sensors.set(0, idx++, 0.0);
            sensors.set(0, idx++, 0.0);
        }

        return sensors;
    }
}
