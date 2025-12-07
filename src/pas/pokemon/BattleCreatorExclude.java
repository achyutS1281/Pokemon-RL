package src.pas.pokemon;

// SYSTEM IMPORTS

import edu.bu.pas.pokemon.core.*;

import java.io.IOException;
import java.util.*;


// JAVA PROJECT IMPORTS


public class BattleCreatorExclude extends Object {


    public static List<Integer> getRandomDexIdxs(int size, Random rng, CoreRegistry cr) {
        List<Integer> randIdxs = new ArrayList<>(size);
        for (int idx = 0; idx < size; ++idx) {
            int randIdx = rng.nextInt(cr.getNumPokemon()) + 1;
            while (randIdx == 131) {
                randIdx = rng.nextInt(cr.getNumPokemon()) + 1;
            }
            randIdxs.add(randIdx);
        }
        return randIdxs;
    }

    public static Move[] chooseRandomUniqueMoves(int dexIdx, int size, Random rng, CoreRegistry cr) {
        Move moves[] = new Move[size];
        Set<String> moveSet = cr.getMoveSet(dexIdx - 1);
        Set<String> allMoveNames = cr.getAllMoveNames();

        Set<String> availableMoves = new HashSet<String>();
        for (String moveName : moveSet) {
            if (allMoveNames.contains(moveName) && !moveName.equalsIgnoreCase("metronome")) {
                availableMoves.add(moveName);
            }
        }

        List<String> moveSetList = new ArrayList<>(availableMoves);
        int idx = 0;
        while (idx < size && moveSetList.size() > 0) {
            // System.out.println("for pokemon=" + dexIdx + " moveSet=" + moveSet + " availableMoves=" + availableMoves +
            //     " moveSetList=" + moveSetList);
            int randIdx = rng.nextInt(moveSetList.size());
            moves[idx] = cr.getMove(moveSetList.get(randIdx));
            moveSetList.remove(randIdx);
            idx += 1;
        }

        return moves;
    }

    public static Battle makeRandomTeams(int team1Size, int team2Size, int numMovesPerPokemon, Random rng, Agent team1Agent, Agent team2Agent) throws IOException {
        CoreRegistry cr = new CoreRegistry();

        // randomly generate pokemon
        List<Integer> team1DexIdxs = getRandomDexIdxs(team1Size, rng, cr);
        List<Integer> team2DexIdxs = getRandomDexIdxs(team2Size, rng, cr);

        Team teams[] = new Team[2];
        for (int teamIdx = 0; teamIdx < teams.length; ++teamIdx) {
            int teamSize = teamIdx == 0 ? team1Size : team2Size;
            Agent teamAgent = teamIdx == 0 ? team1Agent : team2Agent;
            List<Integer> teamDexIdxs = teamIdx == 0 ? team1DexIdxs : team2DexIdxs;

            Pokemon pokemon[] = new Pokemon[teamSize];
            for (int pokemonIdx = 0; pokemonIdx < pokemon.length; ++pokemonIdx) {
                int dexIdx = teamDexIdxs.get(pokemonIdx);
                Move moves[] = chooseRandomUniqueMoves(dexIdx, numMovesPerPokemon, rng, cr);

                Pokemon p = cr.spawnPokemon(dexIdx, 100, new int[]{15, 15, 15, 15, 15, 15, 1, 1}, new int[]{65535, 65535, 65535, 65535, 65535, 65535, 1, 1}, moves);

                pokemon[pokemonIdx] = p;
            }

            Team team = new Team(pokemon, teamAgent);
            teams[teamIdx] = team;
        }

        Battle battle = new Battle(teams[0], teams[1]);
        return battle;
    }
}
