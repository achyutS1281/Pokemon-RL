package src.pas.pokemon.senses;

// SYSTEM IMPORTS

// JAVA PROJECT IMPORTS
import edu.bu.pas.pokemon.agents.senses.SensorArray;
import edu.bu.pas.pokemon.core.DamageEquation;
import edu.bu.pas.pokemon.core.Move;
import edu.bu.pas.pokemon.core.Pokemon;
import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Move.MoveView;
import edu.bu.pas.pokemon.core.Move.Category;
import edu.bu.pas.pokemon.core.Pokemon.PokemonView;
import edu.bu.pas.pokemon.core.Team.TeamView;
import edu.bu.pas.pokemon.core.enums.Stat;
import edu.bu.pas.pokemon.core.enums.Type;
import edu.bu.pas.pokemon.linalg.Matrix;
import src.pas.pokemon.agents.PolicyAgent;

public class CustomSensorArray
        extends SensorArray {

    private PolicyAgent agent;

    public CustomSensorArray(PolicyAgent agent) {
        this.agent = agent;
    }

    public static int getInputSize() {
        // Condensed Features:
        // 1. My HP Ratio
        // 2. Opp HP Ratio
        // 3. Speed Advantage
        // 4. Defensive Matchup
        // 5. Offensive Matchup
        // 6. Action - Expected Damage %
        // 7. Action - Accuracy
        // 8. Action - Priority
        // 9. Action - Is Status
        // 10. Action - Effectiveness
        // 11. Action - STAB
        return 11;
    }

    @Override
    public Matrix getSensorValues(final BattleView state, final MoveView action) {
        int size = getInputSize();
        Matrix sensors = Matrix.zeros(1, size);
        int idx = 0;

        // Determine team indices
        int myTeamIdx = this.agent.getMyTeamIdx();
        int oppTeamIdx = (myTeamIdx == 0) ? 1 : 0;

        TeamView myTeam = state.getTeamView(myTeamIdx);
        TeamView oppTeam = state.getTeamView(oppTeamIdx);

        PokemonView myPokemonView = myTeam.getActivePokemonView();
        PokemonView oppPokemonView = oppTeam.getActivePokemonView();

        // 1. My HP Ratio
        double myHpRatio = (double) myPokemonView.getCurrentStat(Stat.HP) / myPokemonView.getInitialStat(Stat.HP);
        sensors.set(0, idx++, myHpRatio);

        // 2. Opp HP Ratio
        double oppHpRatio = (double) oppPokemonView.getCurrentStat(Stat.HP) / oppPokemonView.getInitialStat(Stat.HP);
        sensors.set(0, idx++, oppHpRatio);

        // 3. Speed Advantage ((MySpeed - OppSpeed) / 200.0) -> Scaled
        double mySpeed = (double) myPokemonView.getCurrentStat(Stat.SPD);
        double oppSpeed = (double) oppPokemonView.getCurrentStat(Stat.SPD);
        double speedAdvantage = (mySpeed - oppSpeed) / 200.0;
        // Clamp between -1 and 1
        speedAdvantage = Math.max(-1.0, Math.min(1.0, speedAdvantage));
        sensors.set(0, idx++, speedAdvantage);

        // Prepare Type Variables
        Type myType1 = myPokemonView.getCurrentType1();
        Type myType2 = myPokemonView.getCurrentType2();
        Type oppType1 = oppPokemonView.getCurrentType1();
        Type oppType2 = oppPokemonView.getCurrentType2();

        // 4. Defensive Matchup (How weak am I to opponent's types?)
        // Max effectiveness of Opponent Types attacking Me
        double maxDefWeakness = 0.0;

        // Check Opp Type 1 vs Me
        if (oppType1 != null) {
            double eff = Type.getEffectivenessModifier(oppType1, myType1);
            if (myType2 != null)
                eff *= Type.getEffectivenessModifier(oppType1, myType2);
            maxDefWeakness = Math.max(maxDefWeakness, eff);
        }
        // Check Opp Type 2 vs Me
        if (oppType2 != null) {
            double eff = Type.getEffectivenessModifier(oppType2, myType1);
            if (myType2 != null)
                eff *= Type.getEffectivenessModifier(oppType2, myType2);
            maxDefWeakness = Math.max(maxDefWeakness, eff);
        }
        // Normalize: Max normal weakness is 4.0.
        sensors.set(0, idx++, maxDefWeakness / 4.0);

        // 5. Offensive Matchup (How strong am I against opponent?)
        // Max effectiveness of My Types attacking Opponent
        double maxOffStrength = 0.0;

        // Check My Type 1 vs Opp
        if (myType1 != null) {
            double eff = Type.getEffectivenessModifier(myType1, oppType1);
            if (oppType2 != null)
                eff *= Type.getEffectivenessModifier(myType1, oppType2);
            maxOffStrength = Math.max(maxOffStrength, eff);
        }
        // Check My Type 2 vs Opp
        if (myType2 != null) {
            double eff = Type.getEffectivenessModifier(myType2, oppType1);
            if (oppType2 != null)
                eff *= Type.getEffectivenessModifier(myType2, oppType2);
            maxOffStrength = Math.max(maxOffStrength, eff);
        }
        sensors.set(0, idx++, maxOffStrength / 4.0);

        // Action Features
        if (action != null) {
            // Reconstruct Core Objects for Calculate Damage
            // NOTE: We assume these factory/constructor methods exist based on
            // documentation.
            Pokemon myPokemonCore = Pokemon.fromView(myPokemonView);
            Pokemon oppPokemonCore = Pokemon.fromView(oppPokemonView);
            Move moveCore = new Move(action);

            // 6. Expected Damage % (Damage / OppCurrentHP)
            double expectedDamage = 0.0;
            if (action.getCategory() != Category.STATUS) {
                // Use DamageEquation
                // calculateDamage(Move move, Pokemon caster, Pokemon target, int critical,
                // double randomScaling)
                // Critical = 1 (Normal), Random = 1.0 (Max damage potential) or 0.85 (Min) ->
                // Using 1.0
                int dmg = DamageEquation.calculateDamage(moveCore, myPokemonCore, oppPokemonCore, 1, 1.0);

                double oppCurrentHP = (double) oppPokemonView.getCurrentStat(Stat.HP);
                if (oppCurrentHP > 0) {
                    expectedDamage = (double) dmg / oppCurrentHP;
                    expectedDamage = Math.min(1.0, expectedDamage); // Cap at 100% kill
                } else {
                    expectedDamage = 1.0; // Already dead?
                }
            }
            sensors.set(0, idx++, expectedDamage);

            // 7. Accuracy
            Integer acc = action.getAccuracy();
            if (acc != null) {
                sensors.set(0, idx++, (double) acc / 100.0);
            } else {
                sensors.set(0, idx++, 1.0); // Infinite accuracy
            }

            // 8. Priority
            sensors.set(0, idx++, (double) action.getPriority());

            // 9. Is Status Move
            sensors.set(0, idx++, (action.getCategory() == Category.STATUS) ? 1.0 : 0.0);

            // 10. Effectiveness
            double eff = Type.getEffectivenessModifier(action.getType(), oppType1);
            if (oppType2 != null)
                eff *= Type.getEffectivenessModifier(action.getType(), oppType2);
            sensors.set(0, idx++, eff / 4.0); // Normalize

            // 11. STAB
            boolean stab = (action.getType() == myType1) || (action.getType() == myType2);
            sensors.set(0, idx++, stab ? 1.0 : 0.0);

        } else {
            // Null action (e.g. at start of turn or error), fill 0s
            for (int i = 0; i < 6; i++) {
                sensors.set(0, idx++, 0.0);
            }
        }

        return sensors;
    }
}
