# AI SYSTEM OPERATOR RUNBOOK (v1.0.0)

This runbook provides step-by-step instructions for common operational tasks and emergency responses.

## 1. MODEL PROMOTION (The Shadow Path)

Goal: Safely move a model from CANDIDATE to PRODUCTION.

### Step 1: Registration
Register the new model using the `REGISTER_MODEL` command with full metadata.
```json
{
  "type": "REGISTER_MODEL",
  "metadata": { "version": "M-MyModel-V1" }
}
```

### Step 2: Shadow Evaluation
Set the model as a CANDIDATE and wait for 1000 cycles.
```json
{ "type": "SET_CANDIDATE_MODEL", "version": "M-MyModel-V1" }
```
Monitor progress via `HEARTBEAT` (`monitoring` and `online_ml_metrics` fields).

### Step 3: Canary Test (Optional but Recommended)
Set a low canary ratio to test the model on live traffic.
```json
{ "type": "SET_CANARY_RATIO", "ratio": 0.05 }
```

### Step 4: Promotion
Execute promotion. This command requires confirmation.
```json
{ "type": "PROMOTE_CANDIDATE", "operator_id": "Op-01" }
```
You will receive a `confirmation_token`. Re-send the command with the token:
```json
{ "type": "PROMOTE_CANDIDATE", "operator_id": "Op-01", "confirmation_token": "TOKEN_HERE" }
```

---

## 2. EMERGENCY RESPONSES

### A) The "Panic Button" (Lockdown)
**Scenario**: System is behaving erratically or dangerously.
**Command**:
```json
{ "type": "EMERGENCY_LOCKDOWN", "operator_id": "Op-01" }
```
**Effect**: 
- `STOP` intent forced for every cycle.
- Learning is frozen.
- Management APIs are blocked.

### B) Releasing Lockdown
**Scenario**: Root cause identified and resolved.
**Command**:
```json
{ "type": "UNRELEASE_LOCKDOWN", "operator_id": "Op-01" }
```
*(Requires two-step confirmation)*

### C) Instant Rollback
**Scenario**: New model version is performing worse than expected.
**Command**:
```json
{ "type": "ROLLBACK_MODEL", "operator_id": "Op-01" }
```
*(Requires two-step confirmation)*

---

## 3. INCIDENT INVESTIGATION

When the `failure_status` in `HEARTBEAT` reports a non-NORMAL mode:

1. Check the `failures/` directory for the latest `incident_UUID.json`.
2. Inspect the `audit_hash` in the report.
3. Use the `state_snapshot` to reproduce the failure offline.
4. Once resolved, reset the failure status:
```json
{ "type": "RESET_FAILURE_STATUS", "operator_id": "Op-01" }
```
*(Requires two-step confirmation)*

---

## 4. SYSTEM HEALTH CHECKLIST (Daily)

- [ ] Check `Survival Rate` (should be > 0.9).
- [ ] Check `Violation Rate` (should be < 0.01).
- [ ] Verify `Active Model` matches expectations.
- [ ] Inspect `quarantine/` for blocked experiences.
- [ ] Check `Latency 99th` (should be < 50ms).
