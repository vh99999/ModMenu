import json
import sys
import os

PATH = os.path.join("../shadow", "passive_store.json")

print("=== SHADOW DATA INSPECTION ===")

if not os.path.exists(PATH):
    print("[ERROR] passive_store.json not found")
    sys.exit(1)

try:
    with open(PATH, "r", encoding="utf-8") as f:
        data = json.load(f)
except Exception as e:
    print("[ERROR] Failed to load JSON:", e)
    sys.exit(1)

visits = data.get("visitation_counts", {})
actions = data.get("valid_actions", {})
transitions = data.get("transitions", {})

num_states = len(visits)
num_valid_actions = sum(len(v) for v in actions.values())
num_transitions = sum(
    len(t)
    for state in transitions.values()
    for t in state.values()
)

print(f"States: {num_states}")
print(f"Valid actions (total): {num_valid_actions}")
print(f"Transitions (total): {num_transitions}")

if num_states == 0:
    print("[FAIL] No states recorded")

if num_valid_actions == 0:
    print("[FAIL] No valid actions recorded")

if num_transitions == 0:
    print("[FAIL] No transitions recorded — PROMOTION WILL FAIL")

if num_states > 0 and num_valid_actions > 0 and num_transitions > 0:
    print("[OK] Shadow data is PROMOTABLE")

print("=== END ===")
