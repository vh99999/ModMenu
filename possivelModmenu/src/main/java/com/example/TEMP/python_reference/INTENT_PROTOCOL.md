# INTENT PROTOCOL SPECIFICATION (v1.0.0)

This document defines the authoritative contract for INTENTS sent from Python to Java, and the EXECUTION RESULTS sent from Java back to Python.

## 1. INTENT SPECIFICATION

Intents are high-level semantic directives. Python is the ONLY decision-maker. Java is a "dumb" executor that attempts to translate these intents into engine-specific actions.

| Canonical Name | Parameters | Semantic Meaning | Expected Java Attempt | Failure Criteria |
| :--- | :--- | :--- | :--- | :--- |
| `PRIMARY_ATTACK` | None | Use the primary offensive tool/action. | Trigger the 'attack' mechanic once. | No action taken; Target out of range; Cooldown active. |
| `EVADE` | None | Perform a defensive/avoidance maneuver. | Execute a dash, roll, or rapid movement away from current threat. | No movement; Blocked by geometry. |
| `MOVE` | `{"vector": [x, y, z]}` | Move towards a relative or absolute position. | Initiate movement towards the specified vector. | No movement; Obstruction; Invalid coordinates. |
| `HOLD` | None | Maintain a state (e.g., blocking, charging). | Keep the action active until `RELEASE` or `STOP`. | Action not supported; Interrupted. |
| `RELEASE` | None | End a sustained state. | Stop the action initiated by `HOLD`. | Action was not active. |
| `STOP` | None | Cease all active movement and actions. | Cancel current velocities and clear action queue. | Entity continues to slide/move significantly. |
| `JUMP` | None | Perform a vertical movement. | Trigger the jump mechanic. | Entity is grounded and didn't move; Entity is in mid-air (if double jump not supported). |

### Intent Constraints:
- **No Logic:** Intents MUST NOT contain conditional logic (e.g., "Attack if health > 10").
- **No Mechanics:** Intents MUST NOT reference specific keybinds or internal engine functions.
- **No Cooldowns:** Python ignores cooldowns; Java MUST report if an intent failed due to a cooldown.

---

## 2. EXECUTION RESULT CONTRACT

Every intent execution attempt by Java MUST return a result payload with the following structure.

### Result Schema
```json
{
  "status": "SUCCESS",
  "failure_reason": "NONE",
  "partial_execution": false,
  "safety_flags": {
    "is_blocked": false,
    "on_cooldown": false,
    "invalid_environment": false
  },
  "outcomes": {
    "damage_dealt": 0.0,
    "damage_received": 0.0,
    "is_alive": true,
    "action_wasted": false
  },
  "metadata": {
    "engine_timestamp": 123456789,
    "execution_time_ms": 1.5
  }
}
```

### Status Definitions:
- `SUCCESS`: The intent was fully translated and triggered in the engine.
- `FAILURE`: The intent could not be started or was instantly aborted.
- `PARTIAL`: The intent started but was interrupted or only partially completed (e.g., moved half-way).

### Python Decision Flow for Results:
1. **On `SUCCESS`**: Continue policy execution normally. Log as positive experience.
2. **On `FAILURE` (COOLDOWN/BLOCKED)**: Policy may choose an alternative intent or wait. Log as "Environmental Constraint".
3. **On `FAILURE` (INVALID_STATE)**: Python detects out-of-sync state. Force a state refresh.
4. **On `MALFORMED` response**: Assume `FAILURE/UNKNOWN`. Log a CONTRACT VIOLATION. Fallback to `STOP`.

---

## 3. FAILURE & FALLBACK POLICY (PYTHON SIDE)

- **Invalid Intent Detection**: If Python generates an intent not in the canonical list, the Bridge MUST intercept and replace it with `STOP`.
- **Java Response Validation**: If Java returns a non-compliant JSON or missing mandatory fields (`status`), Python treats the action as `FAILURE` with reason `UNKNOWN`.
- **Infinite Retry Prevention**: Python-side policies MUST NOT retry the same failing intent more than 3 consecutive times if the `failure_reason` remains the same (e.g., `BLOCKED`).
- **Shadow Learning**: Even if an intent fails, the `(state, intent, result)` tuple is still recorded. Failure is a valid learning signal (negative reward).

---

## 4. FORBIDDEN ASSUMPTIONS
### Things Python is NEVER allowed to assume about Java:

1. **Python MUST NOT assume** Java successfully executed the last intent.
2. **Python MUST NOT assume** Java is running at the same frequency as Python.
3. **Python MUST NOT assume** Java has the same "view" of the world (Latencies exist).
4. **Python MUST NOT assume** Java will handle safety (e.g., Python must not send "MOVE" into a lava pit and expect Java to stop it).
5. **Python MUST NOT assume** Java's `SUCCESS` means the goal was achieved (e.g., `PRIMARY_ATTACK` can be a `SUCCESS` even if it missed the target).
6. **Python MUST NOT assume** Java version is compatible without checking the handshake.

---

## 5. NON-NEGOTIABLE CONTRACT SUMMARY
- Java is a **SLAVE** to the Intent Specification.
- Every intent **MUST** produce a Result Schema.
- Python **ALWAYS** has the final word on what counts as a valid state/intent.
- Any deviation from the schema is a **CRITICAL LOG EVENT** and triggers immediate safe-fallback.
