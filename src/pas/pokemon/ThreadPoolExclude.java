//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package src.pas.pokemon;

import edu.bu.pas.pokemon.execution.BattleDriver;
import edu.bu.pas.pokemon.execution.BattlePool;
import edu.bu.pas.pokemon.execution.Result;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ThreadPoolExclude {
    private final BattlePoolExclude battlePool;
    private final Random random;
    private long numTotalBattles;
    private long numBattlesProcessed;
    private long numBattlesLaunched;
    private final int numWorkers;
    private ExecutorService threadPool;
    private List<Future<Result>> futures;

    public ThreadPoolExclude(BattlePoolExclude var1, int var2, Random var3) {
        this.battlePool = var1;
        this.random = var3;
        this.numTotalBattles = var1.size();
        this.numBattlesProcessed = 0L;
        this.numBattlesLaunched = 0L;
        this.numWorkers = var2;
        this.threadPool = Executors.newFixedThreadPool(this.getNumWorkers());
        this.futures = new ArrayList(this.getNumWorkers());
        this.getBattlePool().reset();
    }

    public final BattlePoolExclude getBattlePool() {
        return this.battlePool;
    }

    public final Random getRandom() {
        return this.random;
    }

    public final long getNumTotalBattles() {
        return this.numTotalBattles;
    }

    public final long getNumBattlesProcessed() {
        return this.numBattlesProcessed;
    }

    public final long getNumBattlesLaunched() {
        return this.numBattlesLaunched;
    }

    public final int getNumWorkers() {
        return this.numWorkers;
    }

    private final ExecutorService getThreadPool() {
        return this.threadPool;
    }

    private final List<Future<Result>> getFutures() {
        return this.futures;
    }

    private void setNumBattlesProcessed(long var1) {
        this.numBattlesProcessed = var1;
    }

    private void setNumBattlesLaunched(long var1) {
        this.numBattlesLaunched = var1;
    }

    public void reset() {
        this.getBattlePool().reset();
        this.setNumBattlesProcessed(0L);
        this.setNumBattlesLaunched(0L);
    }

    public void start() {
        for(int var1 = 0; var1 < this.getNumWorkers(); ++var1) {
            BattleDriver var2 = this.getBattlePool().getNextBattle();
            Future var3 = this.getThreadPool().submit(var2);
            this.getFutures().add(var3);
            this.setNumBattlesLaunched(this.getNumBattlesLaunched() + 1L);
        }

    }

    public void stopNow() {
        this.getThreadPool().shutdownNow();
    }

    public void stop() {
        this.getThreadPool().shutdown();
    }

    public void interrupt() {
        this.stopNow();
    }

    private void launchNextBattle(int var1) {
        if (this.getNumBattlesLaunched() < this.getNumTotalBattles()) {
            BattleDriver var2 = this.getBattlePool().getNextBattle();
            Future var3 = this.getThreadPool().submit(var2);
            this.getFutures().set(var1, var3);
            this.setNumBattlesLaunched(this.getNumBattlesLaunched() + 1L);
        }

    }

    public List<Result> update(long var1, TimeUnit var3) {
        LinkedList var4 = new LinkedList();

        for(int var5 = 0; var5 < this.getNumWorkers(); ++var5) {
            if (this.getFutures().get(var5) == null && this.getNumBattlesLaunched() < this.getNumTotalBattles()) {
                this.launchNextBattle(var5);
            }

            Future var6 = (Future)this.getFutures().get(var5);
            if (var6 != null) {
                try {
                    Result var7 = (Result)var6.get(var1, var3);
                    this.setNumBattlesProcessed(this.getNumBattlesProcessed() + 1L);
                    var4.add(var7);
                    if (this.getNumBattlesLaunched() < this.getNumTotalBattles()) {
                        this.launchNextBattle(var5);
                    } else {
                        this.getFutures().set(var5, (Future<Result>) null);
                    }
                } catch (TimeoutException var8) {
                } catch (Exception var9) {
                    var9.printStackTrace();
                    this.stopNow();
                }
            }
        }

        return var4;
    }

    public List<Result> update() {
        return this.update(1000L, TimeUnit.MILLISECONDS);
    }
}
