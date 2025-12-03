package src.pas.othello.ordering;


// SYSTEM IMPORTS
import edu.bu.pas.othello.traversal.Node;
import src.pas.othello.heuristics.Heuristics;

import java.util.*;


// JAVA PROJECT IMPORTS



public class MoveOrderer
    extends Object
{
    public static String hashKey(Node node) {
        StringBuilder sb = new StringBuilder();
        for (int x = 0; x < node.getGameView().getMaxXDimension(); x++) {
            for (int y = 0; y < node.getGameView().getMaxYDimension(); y++) {
                sb.append(node.getGameView().getCell(x, y));
            }
        }
        return sb.toString();

    }
    public static List<Node> orderChildren(List<Node> children)
    {
        // TODO: complete me!

        Map<String, Double> heuristicValues = new HashMap<>();
        for (Node child : children) {
            double heuristicValue = Heuristics.calculateHeuristicValue(child);
            heuristicValues.put(hashKey(child), heuristicValue);
        }
        Collections.sort(children, (a, b) -> {{
            double valueA = heuristicValues.get(hashKey(a));
            double valueB = heuristicValues.get(hashKey(b));
            return Double.compare(valueB, valueA);
        }
        });
        return children;
    }


}
