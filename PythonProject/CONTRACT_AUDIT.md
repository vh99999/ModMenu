# CONTRACT & INVARIANT AUDIT REPORT

## 1. ASSUMPTION TABLE

| Assumption | Failure Scenario | Fix / Guard |
| :--- | :--- | :--- |
| **Boolean Parsing**: `bool(val)` is sufficient for boolean fields. | Java sends `"false"` or `"0"`, Python treats it as `True`. | Implemented `STRICT` boolean parser in `StateParser` and `IntentValidator`. |
| **Float Parsing**: `float()` in `IntentValidator` is always safe. | Java sends a non-numeric string, crashing the server loop. | Wrapped casts in `safe_float` helpers with `try-except` and NaN/Inf guards. |
| **JSON Serialization**: `json.dumps(raw_state)` always succeeds. | `raw_state` contains non-serializable objects, crashing Auditor. | Added `try-except` around serialization with `str()` fallback for hashing. |
| **Time Monotonicity**: `time.time()` always increases. | Backwards time jump causes negative latency or incorrect logs. | Switched to `time.monotonic()` for intervals and `max(0, ...)` for safety. |
| **Outcome Existence**: `outcomes` is always a valid dictionary. | `result.get("outcomes")` returns `None`, crashing Reward/Intent logic. | Added explicit `isinstance(outcomes, dict)` checks and default to `{}`. |
| **Feature Stability**: ML and Dataset pipelines agree on vector order. | One pipeline changes order independently, causing silent drift. | Centralized vectorization in `StateParser.get_feature_vector`. |

## 2. INVARIANT LIST

1. **State Vector Canonicalization**: State vectors MUST be generated via `StateParser.get_feature_vector` to ensure a fixed length (4) and order (`health`, `energy`, `distance`, `collision`).
2. **Reward Boundedness**: All scalar rewards MUST be clamped to $[-2.0, 2.0]$ at the final stage of calculation.
3. **Strict Boolean Interpretation**: Boolean fields from external sources MUST be parsed case-insensitively (`true`, `false`, `1`, `0`, `yes`, `no`).
4. **Non-Negative Latency**: Inference and execution durations MUST be $\geq 0$.
5. **Death Dominance**: If `is_alive` is false, total reward MUST be $\leq -1.0$.

## 3. GUARDS THAT MUST EXIST

1. **Root Type Guards**: All parsers (`StateParser`, `IntentValidator`, `RewardCalculator`) MUST verify the root input is a `dict`.
2. **Cast Guards**: Every `float()` or `int()` conversion of external data MUST be wrapped in a safety utility.
3. **Null-Object Protection**: Every access to nested dictionary fields (e.g., `result["outcomes"]["is_alive"]`) MUST guard against the parent object being `None`.
4. **Serialization Guards**: Hashing and logging of raw state MUST not crash on non-serializable data.

## 4. THINGS PYTHON MUST NEVER TRUST

1. **NEVER trust Java's type safety**: Always re-validate and cast incoming fields.
2. **NEVER trust the existence of optional fields**: Always provide semantic defaults (e.g., `health=1.0`, `alive=True`).
3. **NEVER trust Java's intent reporting**: Use `INTENT_MISMATCH` audit checks to verify Java did what it was told.
4. **NEVER trust system time for intervals**: Use `time.monotonic()` for all performance measurements.
5. **NEVER trust ML suggestions blindly**: Always pass through the `PolicyArbitrator` safety vetoes.
