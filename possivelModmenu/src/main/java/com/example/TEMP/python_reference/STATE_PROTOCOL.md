# STATE Protocol Specification (Strict & Versioned)

This document defines the formal protocol for state transmission between the Java Data Source (Game/Mod) and the Python AI Server.

## 1. Design Philosophy
- **Java is Dumb**: Java sends raw observations with no semantic understanding.
- **Python is the Interpreter**: Only Python assigns meaning or performs derivation.
- **Fail-Safe**: Protocol errors must not crash the system. Recoverable errors use safe defaults.
- **Hermetic**: No future leakage, no reward calculation, no side effects.

## 2. STATE Versioning Rules
- `state_version`: Mandatory integer.
- **Older Versions**: Python MUST support at least one previous version if possible.
- **Newer Versions**: Python MUST ignore unknown fields from newer versions and attempt to parse known fields.
- **Incompatible Versions**: If a version mismatch is non-recoverable, the server MUST log a CRITICAL error and reject the state, but continue execution (stateless).

## 3. Formal Schema Definition

### 3.1 Metadata
| Field | Type | Unit | Range | Meaning | If Missing | If Invalid |
|-------|------|------|-------|---------|------------|------------|
| `state_version` | int | N/A | 1 - 255 | Protocol version | Treat as 0 (Legacy) | Reject/Log |

### 3.2 Raw Observations
| Field | Type | Unit | Range | Meaning | If Missing | If Invalid |
|-------|------|------|-------|---------|------------|------------|
| `health` | float | % | 0.0 - 1.0 | Current entity health | 1.0 | Clamp to [0,1] |
| `energy` | float | % | 0.0 - 1.0 | Current entity energy | 1.0 | Clamp to [0,1] |
| `target_distance`| float | Units | 0.0 - 1000.0| Distance to target | 1000.0 | Clamp to [0,1000] |
| `is_colliding` | bool | N/A | True/False | Collision status | False | False |

## 4. Validation Flow (Step-by-Step)
1. **Reception**: Receive raw TCP bytes.
2. **Decoding**: UTF-8 decoding of the payload.
3. **JSON Deserialization**: Parse string to dictionary. Catch `JSONDecodeError`.
4. **Root Validation**: Check for mandatory `state` and `state_version` keys.
5. **Sanity Scan**:
    - Iterate all numeric values.
    - Check for `NaN` or `Infinity`. Replace with field default immediately.
6. **Field Extraction & Type Casting**:
    - Extract fields according to the schema for the detected `state_version`.
    - Explicitly cast to `float`, `int`, or `bool`.
7. **Range Enforcement**:
    - Apply `clamp(min, max)` to all numeric fields.
8. **Default Injection**:
    - If a field is missing, inject the defined "If Missing" value.
9. **Categorization**:
    - Separate into `raw`, `normalized`, and `derived` layers.
10. **Logging**:
    - Log any deviations (missing fields, clamped values, type conversions) at `WARNING` level.

## 5. Failure Modes
- **Recoverable**: Missing optional fields, values out of range, malformed numeric strings.
    - *Action*: Use default/clamped value, log `WARNING`.
- **Non-Recoverable**: Malformed JSON, missing root `state` key, incompatible `state_version`.
    - *Action*: Reject packet, log `ERROR`, return `None` to prevent policy execution.

## 6. Security & Sanity Guarantees
- **No NaN/Inf Propagation**: All numbers are clamped and checked before reaching any logic.
- **No Future Leakage**: State only contains observations from the current or past ticks.
- **No Hidden Coupling**: State does not contain intended actions or reward signals.
- **No Reward Calculation**: State Parser purely formats and validates data.

## 7. Forbidden Practices
- Do NOT use `eval()` or generic `pickle` for state data.
- Do NOT assume units are normalized by the source.
- Do NOT allow unknown fields to pass into the `normalized` feature vector.
- Do NOT crash on a missing field.
- Do NOT perform ML inference or reward math within the `StateParser`.

## 8. Compliance Checklist
- [ ] `state_version` is included in every packet.
- [ ] No `NaN` or `Infinity` can survive the `parse()` call.
- [ ] Every field in the schema has a corresponding clamp/default logic.
- [ ] All errors are logged via standard `logging`.
- [ ] Python's `derived` fields do not leak into the `raw` data storage.
- [ ] Backward compatibility for `state_version - 1` is implemented.
