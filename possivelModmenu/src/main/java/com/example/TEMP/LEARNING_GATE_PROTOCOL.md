# Learning Gate Protocol

## Overview
The Learning Gate System is an explicit, deterministic, and auditable evaluation layer designed to inspect experiences and determine if they meet the criteria for learning. 

**IMPORTANT: Learning is OFF by construction. These gates are evaluative only and never trigger learning activation.**

## Gate Purpose
The gates serve as a defensive layer to ensure that even if learning were attempted, it would be blocked by multiple independent checks. They provide a clear audit trail of why learning is disallowed for any given experience.

## Evaluation Order
Gates are evaluated in a fixed, non-bypassable order:

1.  **Gate 1: Authority Gate**
    *   Checks if `learning_allowed` is explicitly set to `True` in the experience lineage.
    *   Result: `BLOCKED` if `False`, `PASS` if `True`.
2.  **Gate 2: Trust Boundary Gate**
    *   Checks if `trust_boundary` is set to `SANDBOX`.
    *   Result: `FAIL` if not `SANDBOX`, `PASS` if `SANDBOX`.
3.  **Gate 3: Integrity Gate**
    *   Inspects the experience for any integrity violations.
    *   Result: `FAIL` if any violation has severity `HIGH` or `CRITICAL`, `PASS` otherwise.
4.  **Gate 4: Governance Gate**
    *   **SHADOW Mode**: Returns `PASS` immediately (Observational Bypass).
    *   **ACTIVE Mode**: Checks for the presence of an `EXPLICIT_LEARNING_PERMIT` in `policy_authority`.
    *   Result: `BLOCKED` if missing or invalid (ACTIVE), `PASS` if present or mode is SHADOW.

## Blocking Semantics
*   Any gate resulting in `FAIL` or `BLOCKED` triggers a system incident of type `LEARNING_GATE_BLOCK`.
*   Severity of incidents:
    *   `BLOCKED` → `HIGH`
    *   `FAIL` → `MEDIUM`
*   All decisions are logged.

## Final Decision
The final system decision is always deterministic:
`LEARNING_BLOCKED_BY_GATE_SYSTEM`

## Proof of Non-Learning
Learning remains impossible due to the following structural guarantees:
1.  **Trainer Unreachability**: The `Trainer.train_on_experience` method contains a mandatory `assert False` statement.
2.  **Explicit Disconnection**: The `AIServer` has been modified to remove all calls to the Trainer, even if all gates pass.
3.  **Read-Only Persistence**: No logic exists to write training datasets or update model weights during server operation.
4.  **No Side Effects**: The gate system is a pure evaluation function with no state mutation or background task spawning capabilities.
