package src.pas.othello.heuristics;// java
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import edu.bu.pas.othello.Main;
import java.lang.reflect.Constructor;

public class Evaluator {
    public static void main(String[] args) throws Exception {

        String weightsPath = "config.txt";
        int games = 5;

        // load weights into Heuristics

        int wins = 0;
        for (int i = 0; i < games; i++) {
            boolean tunedPlaysFirst = (i % 2 == 0);
            String searchText = tunedPlaysFirst ? "BLACK player wins (" : "WHITE player wins (";
            PrintStream oldout = new PrintStream(System.out);
            PrintStream out = new PrintStream(new ByteArrayOutputStream());
            System.setOut(out);
            Main.main(tunedPlaysFirst ? new String[]{"--silent", "-b", "src.pas.othello.agents.OthelloAgent", "-w",  "edu.bu.pas.othello.agents.MediumAgent"} : new String[]{"--silent  -b src.pas.othello.agents.MediumAgent -w edu.bu.pas.othello.agents.OthelloAgent"} );
            String regex = Pattern.quote(searchText) + "\\s*(\\d+)";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(out.toString());
            int number;
            if (matcher.find()) {
                String numberString = matcher.group(1);
                number = Integer.parseInt(numberString);
                System.out.println("Found number: " + number);
            } else {
                System.out.println("Number next to '" + searchText + "' not found.");
            }
            double result = out.toString().contains(tunedPlaysFirst ? "BLACK player wins" : "WHITE player wins") ? 1.0 : -1.0;
            // result: +1 tuned win, 0 draw, -1 tuned loss (adapt as needed)
            if (result > 0.0) wins++;
            System.setOut(oldout);
        }

        double winRate = (double) wins / games;
        // print a single numeric metric (Optuna expects a single numeric stdout line)
        System.out.println(winRate);
    }
}