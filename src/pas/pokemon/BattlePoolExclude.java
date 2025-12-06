//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package pas.pokemon;

import edu.bu.pas.pokemon.core.Agent;
import edu.bu.pas.pokemon.execution.BattleDriver;
import edu.bu.pas.pokemon.execution.BattlePool;
import edu.bu.pas.pokemon.generators.BattleCreator;
import java.io.IOException;
import java.util.List;
import java.util.Random;

public class BattlePoolExclude {
    private final Agent submissionAgent;
    private final List<Agent> enemyAgents;
    private final long numBattlesPerMatchup;
    private final Random random;
    private final long numTotalBattles;
    private int currentEnemyAgentIdx;
    private BattleDriver battleDriver;

    public BattlePoolExclude(Agent var1, List<Agent> var2, long var3, Random var5, BattleDriver var6) {
        this.submissionAgent = var1;
        this.enemyAgents = var2;
        this.numBattlesPerMatchup = var3;
        this.random = var5;
        this.numTotalBattles = (long)this.getEnemyAgents().size() * this.getNumBattlesPerMatchup();
        this.currentEnemyAgentIdx = 0;
        this.battleDriver = var6;
    }

    public final Agent getSubmissionAgent() {
        return this.submissionAgent;
    }

    public final List<Agent> getEnemyAgents() {
        return this.enemyAgents;
    }

    public final long getNumBattlesPerMatchup() {
        return this.numBattlesPerMatchup;
    }

    public final Random getRandom() {
        return this.random;
    }

    public final long getNumTotalBattles() {
        return this.numTotalBattles;
    }

    public final int getCurrentEnemyAgentIdx() {
        return this.currentEnemyAgentIdx;
    }

    public final BattleDriver getBattleDriver() {
        return this.battleDriver;
    }

    private final void setCurrentEnemyAgentIdx(int var1) {
        this.currentEnemyAgentIdx = var1;
    }

    public final long size() {
        return this.getNumTotalBattles();
    }

    public final void reset() {
        this.setCurrentEnemyAgentIdx(0);
    }

    public final BattleDriver getNextBattle() {
        Agent var1 = (Agent)this.getEnemyAgents().get(this.getCurrentEnemyAgentIdx());
        BattleDriver var2 = this.getBattleDriver().copy();

        try {
            var2.setBattle(BattleCreatorExclude.makeRandomTeams(6, 6, 4, this.getRandom(), this.getSubmissionAgent(), var1));
        } catch (IOException var4) {
            var4.printStackTrace();
            System.exit(1);
        }

        this.setCurrentEnemyAgentIdx((1 + this.getCurrentEnemyAgentIdx()) % this.getEnemyAgents().size());
        return var2;
    }
}
