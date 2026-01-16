import json
import os
import sys
import time
import hashlib
import math

# Absolute path to this file
THIS_FILE = os.path.abspath(__file__)

# promote/run.py  → project root is ONE level up
PROJECT_ROOT = os.path.dirname(os.path.dirname(THIS_FILE))

STORE_PATH = os.path.join(PROJECT_ROOT, "shadow", "passive_store.json")
SNAPSHOT_BASE_DIR = os.path.join(PROJECT_ROOT, "snapshots", "promoted")

print("CWD:", os.getcwd())
print("STORE_PATH:", os.path.abspath(STORE_PATH))
print("STORE EXISTS:", os.path.exists(STORE_PATH))

def log_ok(msg):
    print(f"[OK] {msg}")

def log_warn(msg):
    print(f"[WARN] {msg}")

def log_error(msg):
    print(f"[ERROR] {msg}")

def validate_finite(obj):
    stack = [obj]
    while stack:
        current = stack.pop()
        if isinstance(current, dict):
            stack.extend(current.values())
        elif isinstance(current, list):
            stack.extend(current)
        elif isinstance(current, (float, int)):
            if isinstance(current, float) and (math.isnan(current) or math.isinf(current)):
                raise ValueError(f"Non-finite value detected: {current}")


def merge_knowledge(new_data, old_data):
    if not old_data: return new_data
    merged = old_data.copy()
    for k in ["visitation_counts", "blocked_resolutions", "combat_movement_patterns"]:
        new_v = new_data.get(k, {}); old_v = merged.get(k, {})
        for key, val in new_v.items():
            if isinstance(val, dict):
                if key not in old_v: old_v[key] = val
                else: 
                    for sk, sv in val.items(): old_v[key][sk] = old_v[key].get(sk, 0) + sv
            else: old_v[key] = old_v.get(key, 0) + val
        merged[k] = old_v
    new_va = new_data.get("valid_actions", {}); old_va = merged.get("valid_actions", {})
    for state, actions in new_va.items():
        if state not in old_va: old_va[state] = actions
        else:
            existing = old_va[state]
            for a in actions:
                name = a.get("action_name"); found = False
                for e in existing:
                    if e.get("action_name") == name:
                        e["hold_duration_ticks"] = max(e.get("hold_duration_ticks", 1), a.get("hold_duration_ticks", 1))
                        e["cooldown_ticks_observed"] = max(e.get("cooldown_ticks_observed", 0), a.get("cooldown_ticks_observed", 0))
                        if "params" in a: e["params"] = a["params"]
                        found = True; break
                if not found: existing.append(a)
    merged["valid_actions"] = old_va
    new_t = new_data.get("transitions", {}); old_t = merged.get("transitions", {})
    for s, acts in new_t.items():
        if s not in old_t: old_t[s] = acts
        else:
            for a, ns in acts.items():
                if a not in old_t[s]: old_t[s][a] = ns
                else:
                    for next_s, c in ns.items(): old_t[s][a][next_s] = old_t[s][a].get(next_s, 0) + c
    merged["transitions"] = old_t
    return merged


def run_promotion():
    # 1) PRE-FLIGHT CHECKS
    if not os.path.exists(STORE_PATH):
        log_warn("No shadow data to promote")
        sys.exit(1)
    
    try:
        with open(STORE_PATH, 'r') as f:
            content = f.read()
            if not content.strip():
                log_warn("No shadow data to promote")
                sys.exit(1)
            data = json.loads(content)
    except json.JSONDecodeError as e:
        log_error(f"Failed to parse JSON from {STORE_PATH}: {e}")
        sys.exit(1)
    except Exception as e:
        log_error(f"Error reading {STORE_PATH}: {e}")
        sys.exit(1)

    # 2) LOAD & MERGE DATA
    latest_p = os.path.join(SNAPSHOT_BASE_DIR, "latest", "passive_knowledge.json")
    old_data = {}
    if os.path.exists(latest_p):
        try:
            with open(latest_p, "r") as f: old_data = json.load(f)
            log_ok("Found existing knowledge, merging accumulatively...")
        except Exception as e: log_warn(f"Failed to load existing knowledge for merging: {e}")
    data = merge_knowledge(data, old_data)
    v_counts = data.get("visitation_counts", {})
    transitions = data.get("transitions", {})
    valid_actions = data.get("valid_actions", {})
    
    num_states = len(v_counts)
    num_transitions = 0
    for state_trans in transitions.values():
        for action_trans in state_trans.values():
            num_transitions += len(action_trans)
            
    observation_count = sum(v_counts.values())
    
    log_ok(f"Loaded {num_states} states, {num_transitions} transitions")
    print(f"Observations: {observation_count}")

    # 3) SANITY VALIDATION
    try:
        if num_states == 0:
            raise ValueError("Zero states")
        if num_transitions == 0:
            raise ValueError("Zero transitions")
        
        validate_finite(data)
        
        for state in v_counts:
            if not valid_actions.get(state):
                log_warn(f"State {state} has no valid actions - skipping")
                continue
        
        # Check for corrupt or recursive structures
        json.dumps(data)

    except Exception as e:
        log_error(f"Validation failed: {e}")
        sys.exit(1)

    # 4) SNAPSHOT CREATION
    version_id = f"v_{int(time.time())}"
    version_dir = os.path.join(SNAPSHOT_BASE_DIR, version_id)
    latest_dir = os.path.join(SNAPSHOT_BASE_DIR, "latest")

    if not os.path.exists(version_dir):
        os.makedirs(version_dir)
    
    passive_path = os.path.join(version_dir, "passive_knowledge.json")
    manifest_path = os.path.join(version_dir, "manifest.json")
    
    data_content = json.dumps(data, indent=2)
    data_hash = hashlib.sha256(data_content.encode('utf-8')).hexdigest()

    manifest = {
        "version_id": version_id,
        "source": "shadow",
        "promotion_timestamp": time.time(),
        "schema_version": "1.0.0",
        "hash": data_hash,
        "validation_status": "PASS",
        "stats": {
            "states": num_states,
            "transitions": num_transitions,
            "observations": observation_count
        }
    }

    try:
        # Write passive knowledge
        with open(passive_path, 'w') as f:
            f.write(data_content)
        
        # Write manifest
        with open(manifest_path, 'w') as f:
            json.dump(manifest, f, indent=2)
            
        # Update 'latest' pointer (copy for compatibility)
        if not os.path.exists(latest_dir):
            os.makedirs(latest_dir)
        
        with open(os.path.join(latest_dir, "passive_knowledge.json"), 'w') as f:
            f.write(data_content)
        with open(os.path.join(latest_dir, "manifest.json"), 'w') as f:
            json.dump(manifest, f, indent=2)

        log_ok(f"Snapshot written: {version_dir}")
        log_ok("Latest pointer updated: snapshots/promoted/latest")
    except Exception as e:
        log_error(f"Snapshot write failed: {e}")
        sys.exit(1)

    # 5) PROMOTION SUCCESS CONFIRMATION
    print(f"PROMOTION READY — ACTIVE WILL LOAD {version_id} ON NEXT START")

    # 6) RESET SHADOW (ONLY AFTER SUCCESS)
    try:
        new_session_id = data.get("session_id", 0) + 1
        reset_data = {
            "session_id": new_session_id,
            "visitation_counts": {},
            "valid_actions": {},
            "transitions": {},
            "invariant_violations": [],
            "blocked_resolutions": {},
            "combat_movement_patterns": {}
        }
        
        temp_store = STORE_PATH + ".tmp"
        with open(temp_store, 'w') as f:
            json.dump(reset_data, f, indent=2)
        os.replace(temp_store, STORE_PATH)
        log_ok("SHADOW: State reset after promotion")
        print(f"Shadow reset complete — ready for next learning run (New Session: {new_session_id})")
    except Exception as e:
        log_error(f"Shadow reset failed: {e}")
        sys.exit(1)

if __name__ == "__main__":
    run_promotion()
