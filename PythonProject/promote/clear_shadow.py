import json
import os
import sys

# Absolute path to this file
THIS_FILE = os.path.abspath(__file__)

# promote/clear_shadow.py -> project root is ONE level up
PROJECT_ROOT = os.path.dirname(os.path.dirname(THIS_FILE))
STORE_PATH = os.path.join(PROJECT_ROOT, "shadow", "passive_store.json")

def clear_shadow():
    if not os.path.exists(STORE_PATH):
        print(f"[OK] Shadow store does not exist at {STORE_PATH}. Nothing to clear.")
        return

    try:
        # Load existing data to preserve session_id or other metadata if needed, 
        # but the request asks to clear what shadow learned.
        # Following the pattern in run.py (reset_data)
        reset_data = {
            "session_id": 0,
            "visitation_counts": {},
            "valid_actions": {},
            "transitions": {},
            "invariant_violations": [],
            "blocked_resolutions": {},
            "combat_movement_patterns": {}
        }
        
        with open(STORE_PATH, 'w') as f:
            json.dump(reset_data, f, indent=2)
        
        print(f"[OK] Shadow data cleared at {STORE_PATH}")
    except Exception as e:
        print(f"[ERROR] Failed to clear shadow data: {e}")
        sys.exit(1)

if __name__ == "__main__":
    clear_shadow()
