# HUMAN & OPERATIONAL PROTOCOL (v1.0.0)

This document defines the formal requirements for human interaction with the AI system, including management commands, guardrails against operator error, and emergency runbooks.

## 1. DANGEROUS MANUAL ACTIONS

The following actions are classified by their risk to system stability:

| Action | Risk Level | Potential Impact | Mitigation |
| :--- | :--- | :--- | :--- |
| `PROMOTE_CANDIDATE` | CRITICAL | Deploying a regressive or unsafe model. | Automatic Gatekeeping + Confirmation. |
| `ACTIVATE_MODEL` | HIGH | Skipping the Shadow/Canary phases. | Version validation + Confirmation. |
| `SET_CANARY_RATIO` | HIGH | Overloading an untested model. | Ratio capping (Max 10%) + Override. |
| `RESET_FAILURE_STATUS`| HIGH | Hiding a persistent system failure. | Requirement for incident review. |
| `SET_ML_ENABLED` | MEDIUM | Accidentally disabling learning/inference. | Logged attribution. |

## 2. COMMAND GUARDRAILS & CONFIRMATIONS

To prevent "fat-finger" errors and accidental deployments, the following guardrails MUST be enforced:

### A) Two-Step Confirmation
Commands with risk level HIGH or CRITICAL require a `confirmation_token`.
1. Operator sends command without token -> System returns a `409 Conflict` or `REQUIRED_CONFIRMATION` with a generated token.
2. Operator sends command WITH token -> System executes and logs the event.

### B) Promotion Gatekeeping (Shadow Gate)
The `PROMOTE_CANDIDATE` command MUST fail if the following conditions are not met:
- **Sample Size**: Candidate has at least 1000 shadow cycles recorded.
- **Survival Invariant**: Candidate shadow survival rate >= 0.95 of Production survival rate.
- **Reward Invariant**: Candidate shadow reward >= 0.90 of Production reward.
- **Latency Check**: Candidate 99th percentile latency < 50ms.

### C) Canary Ratio Cap
The `canary_ratio` is capped at **0.1 (10%)** for all manual commands. 
- Setting a ratio > 0.1 requires an explicit `bypass_safety_cap: true` flag in the request.

## 3. OPERATOR ATTRIBUTION

Every management command MUST include an `operator_id` (string).
- The system MUST log the `operator_id` in the `GOVERNANCE_EVENT` or incident report.
- Commands without an `operator_id` are logged as `UNATTRIBUTED_ACTION` (Severity: MEDIUM).

## 4. EMERGENCY RUNBOOKS (PANIC BUTTONS)

### A) LOCKDOWN MODE (`EMERGENCY_LOCKDOWN`)
- **Trigger**: Human operator detects emergent unsafe behavior not caught by the Sentinel.
- **Action**: Immediately sets `DisasterMode.READ_ONLY` and `DisasterMode.FREEZE`.
- **Effect**: Entity stops moving (`STOP`), and all learning/persistence is suspended.
- **Exit**: Requires a specific `UNRELEASE_LOCKDOWN` command with confirmation.

### B) INSTANT ROLLBACK
- **Trigger**: Regression detected after promotion.
- **Action**: Immediate execution of `ROLLBACK_MODEL`.
- **Effect**: Reverts to `last_good_version` and demotes the failed model to `CANDIDATE`.

## 5. FORBIDDEN HUMAN ACTIONS
### Things humans must NEVER be allowed to do:

1. **NEVER** promote a model that has not completed the Shadow Evaluation period.
2. **NEVER** reset a failure status without first inspecting the `failure_report.json`.
3. **NEVER** use the same `operator_id` for automated scripts and human actors.
4. **NEVER** bypass the `CANARY_RATIO` cap in production without a written justification in the `metadata`.
5. **NEVER** delete the audit trail to "save space" without a verified off-site backup.

## 6. NON-NEGOTIABLE OPERATIONAL SUMMARY
- Humans are **ADMINISTRATORS**, not direct controllers.
- Every manual change must be **ATTRIBUTED** and **CONFIRMED**.
- Safety gates protect the system from **INTERNAL** (human) failure just as much as **EXTERNAL** (model) failure.
