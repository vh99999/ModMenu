# LEARNING CONTROL PROTOCOL

## OVERVIEW
This document defines the governance rules for machine learning and data persistence within the AI Server. 
The Learning Control Plane is an explicit architectural layer designed to prevent accidental or unauthorized learning.

## CURRENT STATUS: DISABLED
**Learning is DISABLED by default.**

1.  **No Data Persistence:** No experiences are currently allowed to be pushed to the `MemoryBuffer`.
2.  **No Model Updates:** The `Trainer` is blocked from updating any policy weights or performing optimization.
3.  **No Silent Learning:** Every attempt to learn is audited and must pass through the `can_learn()` check.

## GOVERNANCE RULES
1.  **Explicit Consent:** Learning must remain impossible unless the `learning_control.py` module is explicitly modified to allow it.
2.  **Incident Auditing:** Any attempt to trigger a learning entry point while learning is disabled MUST emit a `LEARNING_ATTEMPT_BLOCKED` incident.
3.  **Audit Trail:** All decisions made by the Learning Control Plane are logged with structured data, including:
    *   Timestamp
    *   Reason for blocking
    *   Hash of the sample being considered
    *   The subsystem that attempted the call

## INCIDENT: LEARNING_ATTEMPT_BLOCKED
This incident is emitted whenever the system identifies a potential learning event that is not authorized.
It serves as a safeguard against:
*   Accidental enabling of training loops.
*   Data leaks from sensitive samples.
*   Unauthorized model drift.

## MODIFICATION POLICY
This protocol and its associated implementation (`learning_control.py`) are strictly for GOVERNANCE. 
They exist to provide a safety gate. Any future machine learning implementation must respect this gate and update this protocol accordingly before being enabled.
