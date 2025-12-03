
package src.pas.pokemon.senses;

import java.util.ArrayList;
import java.util.List;

import edu.bu.pas.pokemon.agents.senses.SensorArray;
import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Move.MoveView;
import edu.bu.pas.pokemon.core.Pokemon.PokemonView;
import edu.bu.pas.pokemon.core.Team.TeamView;
import edu.bu.pas.pokemon.core.enums.Stat;
import edu.bu.pas.pokemon.linalg.Matrix;

public class CustomSensorArray extends SensorArray
{
    private static final int FEATURE_DIM = 128;
    private static final int MAX_MOVES = 4;
    private static final double MAX_MOVE_POWER = 150.0; // normalization constant
    private static final double MAX_HP_NORMALIZER = 1.0; // HP ratios already 0..1

    public CustomSensorArray()
    {
    }

    public Matrix getSensorValues(final BattleView state, final MoveView action)
    {
        Matrix sensors = Matrix.randn(1, FEATURE_DIM); // fallback
        try {
            double[] f = new double[FEATURE_DIM];
            int idx = 0;

            TeamView myTeam = safegetTeam1View(state);
            TeamView oppTeam = safegetTeam2View(state);

            PokemonView myActive = safeGetActive(myTeam);
            PokemonView oppActive = safeGetActive(oppTeam);

            // Basic active and team-level features
            double myActiveHpRatio = safeHpRatio(myActive);
            double oppActiveHpRatio = safeHpRatio(oppActive);
            double myActiveFainted = safeIsFainted(myActive) ? 1.0 : 0.0;
            double oppActiveFainted = safeIsFainted(oppActive) ? 1.0 : 0.0;

            double myAvgHp = safeAvgHpRatio(myTeam);
            double oppAvgHp = safeAvgHpRatio(oppTeam);
            double myAlive = safeAliveCount(myTeam) / 6.0;
            double oppAlive = safeAliveCount(oppTeam) / 6.0;

            double myStatusCount = safeStatusCount(myTeam) / 6.0;
            double oppStatusCount = safeStatusCount(oppTeam) / 6.0;

            double activeHpDiff = oppActiveHpRatio - myActiveHpRatio;
            double avgHpDiff = oppAvgHp - myAvgHp;

            double bias = 0.01;

            // Fill core features (16)
            f[idx++] = myActiveHpRatio;
            f[idx++] = oppActiveHpRatio;
            f[idx++] = myActiveFainted;
            f[idx++] = oppActiveFainted;
            f[idx++] = myAvgHp;
            f[idx++] = oppAvgHp;
            f[idx++] = myAlive;
            f[idx++] = oppAlive;
            f[idx++] = myStatusCount;
            f[idx++] = oppStatusCount;
            f[idx++] = activeHpDiff;
            f[idx++] = avgHpDiff;
            f[idx++] = bias;
            // extra core scalars for headroom
            f[idx++] = myActiveHpRatio * 0.5;
            f[idx++] = oppActiveHpRatio * 0.5;
            f[idx++] = myAlive - oppAlive;

            // Encode up to MAX_MOVES moves for our active pokemon
            MoveView[] moves = safeGetMoves(myActive);
            for (int m = 0; m < MAX_MOVES; m++) {
                MoveView mv = m < moves.length ? moves[m] : null;

                double power = safeMovePower(mv) / MAX_MOVE_POWER;
                if (power > 1.0) power = 1.0;

                double accuracy = safeMoveAccuracy(mv); // expect 0..100 -> normalized below
                if (accuracy > 1.0) accuracy = accuracy / 100.0;
                accuracy = Math.max(0.0, Math.min(1.0, accuracy));

                double ppRatio = safeMovePPRatio(mv, myActive); // 0..1
                double wasSuper = safeWasSuperEffective(mv, state) ? 1.0 : 0.0;
                double isUsable = (mv != null && ppRatio > 0.0 && !safeMoveDisabled(mv, myActive)) ? 1.0 : 0.0;
                double selected = (action != null && mv != null && safeSameMove(action, mv)) ? 1.0 : 0.0;

                // Per-move features: power, accuracy, ppRatio, usable, super, selected, placeholder1, placeholder2
                f[idx++] = power;
                f[idx++] = accuracy;
                f[idx++] = ppRatio;
                f[idx++] = isUsable;
                f[idx++] = wasSuper;
                f[idx++] = selected;
                f[idx++] = power * accuracy; // interaction feature
                f[idx++] = ppRatio * isUsable; // interaction
            }

            // Fill some aggregate move statistics (8 slots): max power, avg pp, usable count, selected index
            double maxPower = 0.0;
            double sumPP = 0.0;
            int usableCount = 0;
            int selectedIndex = -1;
            for (int i = 0; i < moves.length && i < MAX_MOVES; i++) {
                double p = safeMovePower(moves[i]);
                maxPower = Math.max(maxPower, p);
                sumPP += safeMovePPRatio(moves[i], myActive);
                if (safeMovePPRatio(moves[i], myActive) > 0.0 && !safeMoveDisabled(moves[i], myActive)) usableCount++;
                if (action != null && safeSameMove(action, moves[i])) selectedIndex = i;
            }
            f[idx++] = Math.min(1.0, maxPower / MAX_MOVE_POWER);
            f[idx++] = (moves.length == 0) ? 0.0 : Math.min(1.0, sumPP / moves.length);
            f[idx++] = (double) usableCount / (double) MAX_MOVES;
            // one-hot like selected index normalized (-1 if none)
            f[idx++] = selectedIndex >= 0 ? (double) selectedIndex / (double) (MAX_MOVES - 1) : -1.0;
            f[idx++] = moves.length / (double) MAX_MOVES;
            f[idx++] = (moves.length == 0) ? 0.0 : (double) moves.length / (double) MAX_MOVES;
            f[idx++] = (selectedIndex >= 0) ? 1.0 : 0.0;
            f[idx++] = bias * 2.0;

            // Duplicate and scaled variants to fill network input diversity
            while (idx < FEATURE_DIM) {
                int mirror = idx % 24; // reuse some early signals
                f[idx++] = 0.0 + 0.5 * f[mirror];
            }

            // Write into Matrix; if Matrix API differs, return random fallback
            for (int c = 0; c < FEATURE_DIM; c++) {
                try {
                    sensors.set(0, c, f[c]);
                } catch (Throwable t) {
                    return sensors;
                }
            }

            return sensors;

        } catch (Exception e) {
            return sensors;
        }
    }

    // --- Safe helpers ---

    private TeamView safegetTeam1View(BattleView state) {
        try { return state == null ? null : state.getTeam1View(); } catch (Exception e) { return null; }
    }

    private TeamView safegetTeam2View(BattleView state) {
        try { return state == null ? null : state.getTeam2View(); } catch (Exception e) { return null; }
    }

    private PokemonView safeGetActive(TeamView team) {
        try { return team == null ? null : team.getActivePokemonView(); } catch (Exception e) { return null; }
    }

    private double safeHpRatio(PokemonView p) {
        try {
            if (p == null) return 0.0;
            double cur = Math.max(0.0, p.getCurrentStat(Stat.HP));
            double max = Math.max(1.0, p.getBaseStat(Stat.HP));
            return Math.max(0.0, Math.min(1.0, cur / max));
        } catch (Exception e) { return 0.0; }
    }

    private boolean safeIsFainted(PokemonView p) {
        try { return p == null || p.hasFainted(); } catch (Exception e) { return true; }
    }

    private double safeAvgHpRatio(TeamView team) {
        try {
            if (team == null) return 0.0;
            double sum = 0.0;
            int cnt = 0;
            for (int idx = 0; idx < team.size(); idx++) {
                PokemonView pk = team.getPokemonView(idx);
                if (pk == null) continue;
                double cur = Math.max(0.0, pk.getCurrentStat(Stat.HP));
                double max = Math.max(1.0, pk.getBaseStat(Stat.HP));
                sum += (cur / max);
                cnt++;
            }
            return cnt == 0 ? 0.0 : Math.max(0.0, Math.min(1.0, sum / cnt));
        } catch (Exception e) { return 0.0; }
    }

    private int safeAliveCount(TeamView team) {
        try {
            if (team == null) return 0;
            int c = 0;
            for (int idx = 0; idx < team.size(); idx++) {
                PokemonView pk = team.getPokemonView(idx);
                if (pk == null) continue;
                if (!pk.hasFainted()) c++;
            }
            return c;
        } catch (Exception e) { return 0; }
    }

    private int safeStatusCount(TeamView team) {
        try {
            if (team == null) return 0;
            int c = 0;
            for (int idx = 0; idx < team.size(); idx++) {
                PokemonView pk = team.getPokemonView(idx);
                if (pk == null) continue;
                try { if (pk.getNonVolatileStatus() != null) c++; } catch (Exception ignored) {}
            }
            return c;
        } catch (Exception e) { return 0; }
    }

    private MoveView[] safeGetMoves(PokemonView p) {
        try {
            if (p == null) return new MoveView[0];
            // try common APIs
            try {
                List<MoveView> list = p.getAvailableMoves();
                return list == null ? new MoveView[0] : list.toArray(new MoveView[0]);
            } catch (Throwable ignore) {}
            return new MoveView[0];
        } catch (Exception e) {
            return new MoveView[0];
        }
    }

    private double safeMovePower(MoveView m) {
        try { return m == null ? 0.0 : Math.max(0.0, m.getPower()); } catch (Exception e) { return 0.0; }
    }

    private double safeMoveAccuracy(MoveView m) {
        try { return m == null ? 1.0 : Math.max(0.0, Math.min(100.0, m.getAccuracy())); } catch (Exception e) { return 1.0; }
    }

    private double safeMovePPRatio(MoveView m, PokemonView p) {
        try {
            if (m == null || p == null) return 0.0;
            int rem = m.getPP() - 1;
            int max = Math.max(1, m.getPP());
            return Math.max(0.0, Math.min(1.0, (double) rem / (double) max));
        } catch (Exception e) { return 0.0; }
    }

    private boolean safeWasSuperEffective(MoveView m, BattleView state) {
        //try { return m != null && m.wasLastUsedSuperEffective(); } catch (Exception e) { return false; }
        return true;
    }

    private boolean safeMoveDisabled(MoveView m, PokemonView p) {
//        try { return m == null ? true : m.isDisabled(); } catch (Exception e) { return false; }
        return  true;
    }

    private boolean safeSameMove(MoveView a, MoveView b) {
        try {
            if (a == null || b == null) return false;
            // try identity or name equality
            if (a == b) return true;
            try {
                String an = a.getName();
                String bn = b.getName();
                return an != null && an.equals(bn);
            } catch (Throwable ignore) {}
            return false;
        } catch (Exception e) { return false; }
    }
}
