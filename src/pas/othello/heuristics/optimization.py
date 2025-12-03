import optuna
import subprocess
import os
import re
import sys
from pathlib import Path
def run_java_main(args, project_root=None, timeout=5000):
    """
    Ensure project_root contains compiled classes (use `javac -d . src\\**\\*.java`)
    and any jars under `lib/` if present.
    """
    project_root = Path(project_root or "C:/Users/achyu/CS440")
    # Use "." (the cwd) plus lib/* if a lib folder exists
    cp_parts = ["."]
    if (project_root / "lib").exists():
        cp_parts.append("lib/*")
    classpath = os.pathsep.join(cp_parts)  # uses ';' on Windows, ':' on Unix

    cmd = ["java", "-cp", classpath, "edu.bu.pas.othello.Main"] + args
    proc = subprocess.run(cmd, capture_output=True, text=True, cwd=str(project_root), timeout=timeout)
    return proc.returncode, proc.stdout, proc.stderr
def evaluate():
    project_root = "C:/Users/achyu/CS440"  # adjust if you need a specific project root
    # If you need to load weights via a Java Heuristics class, do that before running games
    # e.g. subprocess.run(["java", "-cp", ".", "src.pas.othello.heuristics.Heuristics", "loadWeights", "config.txt"])

    weights_path = "config.txt"
    games = 3

    wins = 0
    for i in range(games):
        tuned_plays_first = (i % 2 == 0)
        search_text = "BLACK player wins (" if tuned_plays_first else "WHITE player wins ("

        if tuned_plays_first:
            agent_args = ["--hz", "360", "-b", "src.pas.othello.agents.OthelloAgent", "-w",
                          "edu.bu.pas.othello.agents.HardAgent"]
            print("Tuned agent plays BLACK")
        else:
            agent_args = ["--hz", "360", "-b", "edu.bu.pas.othello.agents.HardAgent", "-w",
                          "src.pas.othello.agents.OthelloAgent"]
            print("Tuned agent plays WHITE")

        try:
            rc, stdout, stderr = run_java_main(agent_args)
        except subprocess.TimeoutExpired:
            print(f"Run {i}: java process timed out", file=sys.stderr)
            stdout = ""
            rc = 1

        if rc != 0:
            print(f"Run {i}: java returned code {rc}", file=sys.stderr)
            if stderr:
                print(stderr, file=sys.stderr)

        # extract the number next to the search text
        pattern = re.compile(r'(?<=' + re.escape(search_text) + r')\d+')
        match = pattern.search(stdout)
        number = 0
        won = False
        if match:
            won = True
            number = int(match.group(0))
            print(f"Found number: {number}")
        else:
            search_text = "WHITE player wins (" if tuned_plays_first else "BLACK player wins ("
            pattern = re.compile(r'(?<=' + re.escape(search_text) + r')\d+')
            match = pattern.search(stdout)
            if match:
                number = int(match.group(0))
                print(f"Found number: {number}")
            else:
                print(f"Run {i}: could not find win information in output", file=sys.stderr)

        # determine win for tuned agent
        ratio = number / 64
        if won:
            wins += ratio
        else:
            wins -= ratio
    win_rate = wins / games if games > 0 else 0.0
    result = win_rate
    return result
def objective(trial):
    vals = {
        'W_CORNER': trial.suggest_float('W_CORNER', 100.0, 200.0),
        "W_CORNER_PENALTY": trial.suggest_float("W_CORNER_PENALTY", -150.0, -50.0),
        "W_STABLE": trial.suggest_float("W_STABLE", 0.0, 50.0),
        "W_MOBILITY": trial.suggest_float("W_MOBILITY", 0.0, 50.0),
        "W_FRONTIER": trial.suggest_float("W_FRONTIER", -10.0, 10.0),
        "W_DISC_COUNT": trial.suggest_float("W_DISC_COUNT", 0.0, 10.0),
        "W_EDGE_CONTROL": trial.suggest_float("W_EDGE_CONTROL", -50.0, 50.0),
        "W_DISC_COUNT_END": trial.suggest_float("W_DISC_COUNT_END", 0.0, 50.0),
        "ENDGAME_THRESHOLD": int(trial.suggest_int("ENDGAME_THRESHOLD", 0, 64))
    }
    with open("optimization/config.txt", "w") as f:
        for k, v in vals.items():
            f.write(f"{k}={v}\n")
    print("Trial parameters:", vals)
    score = evaluate()
    return score
if __name__ == "__main__":
    study = optuna.create_study(direction="maximize", sampler=optuna.samplers.TPESampler())
    study.optimize(objective, n_trials=50, timeout=50400, n_jobs=1, show_progress_bar=True)

    print("Best trial:")
    print(study.best_trial.params)
    print("Value:", study.best_trial.value)