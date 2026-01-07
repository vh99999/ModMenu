# DATA & STATE INTEGRITY PROTOCOL (v1.0.0)

This document defines the formal requirements for ensuring the integrity, freshness, and uniqueness of data flowing between Java and Python.

## 1. STATE DEDUPLICATION

To prevent over-sampling and biased training, duplicate states MUST be detected and handled.

### Deduplication Rules:
- **Strict Uniqueness**: A state is considered a duplicate if its SHA-256 hash matches the hash of the immediately preceding state in the same episode.
- **Action on Duplicate**:
    - **Inference**: Python SHOULD still return a decision (to maintain low latency and heartbeat), but SHOULD log it as a `DUPLICATE_STATE_DECISION`.
    - **Learning**: Duplicate states MUST be BLOCKED from the `MemoryBuffer` and `Trainer`.
    - **Audit**: Duplicate cycles MUST be flagged with a `DUPLICATE_STATE` violation (LOW severity).

---

## 2. TIME-TO-LIVE (TTL) & STALENESS

States arriving late due to network congestion or Java-side lag MUST be rejected if they are too old to be relevant.

### TTL Rules:
- **Max Age**: 100ms. If `(Python_Current_Time - Java_Timestamp) > 100ms`, the state is considered STALE.
- **Action on Stale**:
    - **Inference**: Fallback to `SAFE_MODE` (Heuristic) or `STOP`.
    - **Learning**: Stale experiences MUST NOT enter the training pipeline.
    - **Violation**: `STALE_STATE` (MEDIUM severity).

---

## 3. MEMORY PROTECTION & QUARANTINE

### Buffer Overflow:
- All buffers (`MemoryBuffer`, `Auditor.history`) MUST be bounded.
- Deletion of oldest records is acceptable to prevent system-wide memory exhaustion.

### Contamination Paths:
- **Partially Corrupted State**: If more than 50% of the `SCHEMA` fields in `StateParser` are missing and replaced by defaults, the state MUST be rejected for learning.
- **Inconsistent Transitions**: If `is_alive` was `false` in the previous cycle but `true` in the current cycle without a new `episode_id`, it is a CRITICAL integrity failure.

---

## 4. INTEGRITY REJECTION CRITERIA

An experience is REJECTED from the `MemoryBuffer` if:
1. It is a `DUPLICATE_STATE`.
2. It is a `STALE_STATE`.
3. It contains `REWARD_INVARIANT` violations.
4. It contains `CONTRACT_VIOLATION` with severity `HIGH` or `CRITICAL`.

---

## 5. FORBIDDEN DATA BEHAVIORS

### "Data must never do this":
1. **Never go backwards in time**: Java timestamps MUST be monotonic within an episode.
2. **Never skip episodes**: State transitions must be consistent with `episode_id` increments.
3. **Never allow NaN features**: Even if the parser injects defaults, the record MUST be flagged as corrupted if the original value was non-numeric.
4. **Never reuse state hashes**: Two different game situations must not produce the same hash (SHA-256 is the standard).
