package src.pas.pokemon.senses;

// SYSTEM IMPORTS

// JAVA PROJECT IMPORTS
import edu.bu.pas.pokemon.agents.senses.SensorArray;
import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Move.MoveView;
import edu.bu.pas.pokemon.core.Move.Category;
import edu.bu.pas.pokemon.core.Pokemon.PokemonView;
import edu.bu.pas.pokemon.core.Team.TeamView;
import edu.bu.pas.pokemon.core.enums.NonVolatileStatus;
import edu.bu.pas.pokemon.core.enums.Stat;
import edu.bu.pas.pokemon.core.enums.Type;
import edu.bu.pas.pokemon.core.enums.Flag;
import edu.bu.pas.pokemon.linalg.Matrix;
import src.pas.pokemon.agents.PolicyAgent;

public class CustomSensorArray
        extends SensorArray {

    private PolicyAgent agent;

    public CustomSensorArray(PolicyAgent agent) {
        this.agent = agent;
    }

    public static int getInputSize() {
        int numTypes = Type.values().length;
        int numStats = Stat.values().length;
        int numStatus = NonVolatileStatus.values().length;
        int numCategories = Category.values().length;
        int numFlags = Flag.values().length;

        // My Pokemon: HP Ratio (1) + Type1 (N) + Type2 (N) + Stats (S) + Status (St) +
        // Flags (F)
        int pokemonFeatures = 1 + (2 * numTypes) + numStats + numStatus + numFlags;

        // Opponent Pokemon: Same
        int opponentFeatures = pokemonFeatures;

        // Action: Power (1) + Accuracy (1) + Type (N) + Category (C) + Eff1 (1) + Eff2
        // (1) + CombinedEff (1) + STAB (1) + Priority (1)
        int actionFeatures = 1 + 1 + numTypes + numCategories + 4 + 1;

        return pokemonFeatures + opponentFeatures + actionFeatures;
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

        // My Pokemon Features

        // HP Ratio
        double myHpRatio = (double) myPokemon.getCurrentStat(Stat.HP) / myPokemon.getInitialStat(Stat.HP);
        sensors.set(0, idx++, myHpRatio);

        // Type 1 (One-hot)
        Type myType1 = myPokemon.getCurrentType1();
        for (Type t : Type.values()) {
            if (t == myType1) {
                sensors.set(0, idx++, 1.0);
            } else {
                sensors.set(0, idx++, 0.0);
            }
        }

        // Type 2 (One-hot)
        Type myType2 = myPokemon.getCurrentType2();
        for (Type t : Type.values()) {
            if (t == myType2) {
                sensors.set(0, idx++, 1.0);
            } else {
                sensors.set(0, idx++, 0.0);
            }
        }

        for (Stat s : Stat.values()) {
            if (s == Stat.HP) {
                sensors.set(0, idx++, 0.0);
            } else {
                sensors.set(0, idx++, myPokemon.getStatMultiplier(s));
            }
        }

        // Status (One-hot)
        NonVolatileStatus myStatus = myPokemon.getNonVolatileStatus();
        for (NonVolatileStatus s : NonVolatileStatus.values()) {
            if (s == myStatus) {
                sensors.set(0, idx++, 1.0);
            } else {
                sensors.set(0, idx++, 0.0);
            }
        }

        // Volatile Status (Flags)
        for (Flag f : Flag.values()) {
            if (myPokemon.getFlag(f)) {
                sensors.set(0, idx++, 1.0);
            } else {
                sensors.set(0, idx++, 0.0);
            }
        }

        // Opponent Pokemon Features

        // HP Ratio
        double oppHpRatio = (double) oppPokemon.getCurrentStat(Stat.HP) / oppPokemon.getInitialStat(Stat.HP);
        sensors.set(0, idx++, oppHpRatio);

        // Type 1
        Type oppType1 = oppPokemon.getCurrentType1();
        for (Type t : Type.values()) {
            if (t == oppType1) {
                sensors.set(0, idx++, 1.0);
            } else {
                sensors.set(0, idx++, 0.0);
            }
        }

        // Type 2
        Type oppType2 = oppPokemon.getCurrentType2();
        for (Type t : Type.values()) {
            if (t == oppType2) {
                sensors.set(0, idx++, 1.0);
            } else {
                sensors.set(0, idx++, 0.0);
            }
        }

        // Stats
        for (Stat s : Stat.values()) {
            if (s == Stat.HP) {
                sensors.set(0, idx++, 0.0);
            } else {
                sensors.set(0, idx++, oppPokemon.getStatMultiplier(s));
            }
        }

        // Status
        NonVolatileStatus oppStatus = oppPokemon.getNonVolatileStatus();
        for (NonVolatileStatus s : NonVolatileStatus.values()) {
            if (s == oppStatus) {
                sensors.set(0, idx++, 1.0);
            } else {
                sensors.set(0, idx++, 0.0);
            }
        }

        // Volatile Status (Flags)
        for (Flag f : Flag.values()) {
            if (oppPokemon.getFlag(f)) {
                sensors.set(0, idx++, 1.0);
            } else {
                sensors.set(0, idx++, 0.0);
            }
        }

        // Action Features

        if (action != null) {
            // Power (Normalized: Power / 200.0)
            // Max power is usually around 150-250 (Explosion), so dividing by 200 keeps it
            // in [0, 1] range mostly.
            Integer power = action.getPower();
            if (power != null) {
                sensors.set(0, idx++, power / 200.0);
            } else {
                sensors.set(0, idx++, 0.0);
            }

            // Accuracy (Normalized: Accuracy / 100.0)
            // Accuracy is 0-100, so dividing by 100 puts it in [0, 1].
            Integer accuracy = action.getAccuracy();
            if (accuracy != null) {
                sensors.set(0, idx++, accuracy / 100.0);
            } else {
                sensors.set(0, idx++, 1.0); // Null usually means always hits (e.g. Aerial Ace)
            }

            // Type (One-hot)
            Type moveType = action.getType();
            for (Type t : Type.values()) {
                if (t == moveType) {
                    sensors.set(0, idx++, 1.0);
                } else {
                    sensors.set(0, idx++, 0.0);
                }
            }

            // Category (One-hot)
            Category category = action.getCategory();
            for (Category c : Category.values()) {
                if (c == category) {
                    sensors.set(0, idx++, 1.0);
                } else {
                    sensors.set(0, idx++, 0.0);
                }
            }
            // Effectiveness against Opponent Type 1
            double eff1 = Type.getEffectivenessModifier(moveType, oppType1);
            sensors.set(0, idx++, eff1);

            // Effectiveness against Opponent Type 2
            double eff2 = 1.0;
            if (oppType2 != null) {
                eff2 = Type.getEffectivenessModifier(moveType, oppType2);
            }
            sensors.set(0, idx++, eff2);

            // Combined Effectiveness (0.25 to 4.0)
            sensors.set(0, idx++, eff1 * eff2);

            // STAB
            boolean stab = (moveType == myType1) || (moveType == myType2);
            if (stab) {
                sensors.set(0, idx++, 1.5);
            } else {
                sensors.set(0, idx++, 1.0);
            }

            // Priority
            sensors.set(0, idx++, (double) action.getPriority());

        }

        return sensors;
    }
}
