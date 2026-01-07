# CONTROL AUTHORITY PROTOCOL (Phase 10B)

## 1. OBJECTIVE
Establish a deterministic and auditable mechanism for exclusive control authority between HUMAN and AI, while enabling safe shadow influence under controlled conditions.

## 2. CONTROL MODES
### 2.1 HUMAN (Default)
- **Authority**: All outbound actions are gated and replaced with `STOP`.
- **Learning**: Continuous shadow learning remains active.
- **Auditing**: Full auditing and logging of state and potential AI intents.
- **Fallback**: Inference always returns `Intent.STOP`.

### 2.2 AI
- **Authority**: AI policies (Active/Shadow) are authorized to emit actions.
- **Arbitration**: Decisions are governed by the `PolicyArbitrator`.
- **Influence**: `ShadowPolicy` may influence actions up to a maximum weight of 0.3.

## 3. CONTROL TRANSITIONS
- **Endpoint**: `POST /control/mode`
- **Authorization**: Only `GUI` source is allowed.
- **Validation**: Redundant transitions and unknown modes must be rejected.
- **Audit**: Every change triggers a `CONTROL_MODE_CHANGED` incident.
- **Fail-Safe**: Any invalid transition triggers a `CONTROL_MODE_REJECTED` incident.

## 4. SHADOW INFLUENCE (Phase 10B)
### 4.1 Influence Controller
- **Range**: `influence_weight` ∈ [0.0, 0.3].
- **Reset**: Resets to 0.0 on:
    - Control mode switch.
    - Any system error.
    - Safety violation.
    - Reward regression.
    - Entropy spike.

### 4.2 Arbitration Mix
- **Formula**: `final = mix(ActivePolicy, ShadowPolicy, influence_weight)`
- **Safety**: ShadowPolicy MUST NOT bypass inference gating.
- **Fallback**: If ShadowPolicy errors, the system must fallback to ActivePolicy.

## 5. SAFETY & MONITORING
- **Rolling Windows**: Active and Shadow rewards are tracked in 100-cycle rolling windows.
- **Adjustment Rules**:
    - **Increase**: Only if Shadow > Active reward AND zero HIGH/CRITICAL incidents AND no drift AND stable health.
    - **Decrease**: Immediate reset to 0.0 on any violation or regression.
- **Hard Fail**: The system must fail-stop if:
    - ActivePolicy is mutated.
    - ShadowPolicy emits actions in HUMAN mode.
    - CONTROL_MODE is bypassed.

## 6. OBSERVABILITY
The following metrics must be exposed via heartbeat:
- `control_mode`
- `influence_weight`
- `shadow_reward_avg`
- `active_reward_avg`
- `learning_active`
- `last_control_switch`
