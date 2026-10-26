"""Synthetic UI review fixture; not user workout data."""
import copy
import datetime
import json
from pathlib import Path

root = Path(__file__).parent
files = root.parent
envelope = json.loads((files / "delivery-0.1.3/evidence/upgrade/rebuild-0.1.3-roundtrip.json").read_text())
state = envelope["state"]
old = json.loads((files / "delivery-0.1.1/evidence/rebuild-upgrade-baseline.json").read_text())["state"]
state.update(phase="INTRO_TWO", activeWorkoutId=None, restTimer=None, overrides=[],
             revision=1, lastBackupRevision=1, unbackedUpSince=None)
state["exercises"] = [e for e in state["exercises"] if not e["custom"]]
state["workouts"] = []
for index, (date, kg, reps) in enumerate((("2026-09-19T15:00:00+03:00", 40.0, 10),
                                        ("2026-09-26T15:00:00+03:00", 42.5, 10))):
    started = int(datetime.datetime.fromisoformat(date).timestamp() * 1000)
    workout = copy.deepcopy(old["workouts"][0])
    workout.update(id=f"review-upper-{index}", phase="INTRO_TWO", startedAt=started,
                   finishedAt=started + 2400000, notes="")
    for j, exercise in enumerate(workout["exercises"]):
        exercise.update(id=f"review-exercise-{index}-{j}", notes="",
                        targetSets=2, targetRir=3, sets=[])
        if j == 0:
            exercise["sets"] = [
                dict(id=f"review-set-{index}-{k}", kg=kg, reps=reps-k, rir=2.0,
                     warmup=False, completedAt=started+120000+k*180000)
                for k in range(2)
            ]
    state["workouts"].append(workout)
(root / "synthetic-baseline.json").write_text(json.dumps(envelope, separators=(",", ":")) + "\n")
