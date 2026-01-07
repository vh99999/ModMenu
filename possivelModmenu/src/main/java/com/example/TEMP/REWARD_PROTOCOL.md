# REWARD PROTOCOL SPECIFICATION (v1.0.0)

This document defines the formal invariants, component definitions, and failure modes for the AI Reward System.

## 1. FORMAL REWARD INVARIANTS

| Invariant | Definition | Counterexample | Severity |
| :--- | :--- | :--- | :--- |
| **Strict Boundedness** | Total reward $R$ MUST be in range $[-2.0, 2.0]$. | $R = 2.1$ due to floating point error. | CRITICAL |
| **Monotonicity (Aggression)** | $R(damage\_dealt=x) < R(damage\_dealt=y)$ if $x < y$, all else equal. | $5.0$ damage yields less reward than $2.0$. | MAJOR |
| **Dominance (Death)** | If `is_alive` is `false`, $R$ MUST be $\leq -1.0$ regardless of other metrics. | Dying while dealing massive damage yields $+0.5$. | CRITICAL |
| **Zero-Impact Condition** | If no damage is dealt/received and action is not wasted, $R$ MUST be exactly `survival_reward`. | Small noise in floating point math. | MINOR |
| **Non-Negativity (Aggression Component)** | `damage_dealt_reward` MUST be $\geq 0$. | Java sends negative damage. | MAJOR |

## 2. REWARD COMPONENT ISOLATION

Each component represents a specific semantic goal and must be independent of others.

| Component | Semantic Meaning | Zero-Condition | Dominance |
| :--- | :--- | :--- | :--- |
| `damage_dealt_reward` | Incentive for successful offensive actions. | `damage_dealt` $\leq 0$ | None. |
| `damage_received_penalty` | Disincentive for taking damage. | `damage_received` $\leq 0$ | None. |
| `survival_reward` | Baseline for staying alive. | N/A (Constant while alive) | Overridden by `death_penalty`. |
| `death_penalty` | Massive terminal penalty for death. | `is_alive` is `true` | Overrides all positive components. |
| `efficiency_penalty` | Cost of ineffective action (e.g. attacking empty air). | `action_wasted` is `false` | None. |

## 3. ADVERSARIAL TEST MATRIX

| Scenario | Input Payload | Expected Reward Behavior |
| :--- | :--- | :--- |
| **Zero Damage Loop** | `damage_dealt=0`, `damage_received=0` | Return constant `survival_reward`. |
| **Kamikaze** | `damage_dealt=100`, `is_alive=false` | Return `death_penalty` (Dominance). |
| **Damage Overflow** | `damage_dealt=1e10` | Clamp total reward to $+2.0$. |
| **Negative Damage** | `damage_dealt=-5.0` | Treat as $0.0$ (Invariant: Non-negativity). |
| **NaN Injection** | `damage_received=NaN` | Fallback to default penalty ($0.0$) or safe default. |
| **Partial Execution Abuse** | `partial_execution=true` | No extra reward/penalty; components handle it. |

## 4. TEMPORAL CONSISTENCY

- **Delta Stability**: A change in state $\Delta S$ should result in a bounded change in reward $\Delta R$. 
- **Oscillation Mitigation**: No alternating positive/negative components for the same outcome.
- **Terminal Decay**: Terminal penalties are applied only once at the moment of death to prevent gradient explosion.

## 5. FAILURE HANDLING

- **Malformed Result**: If the `result` dictionary is missing mandatory fields or has invalid types, `RewardCalculator` MUST return $0.0$ and log a CRITICAL violation.
- **Unknown Controller**: Reward calculation is independent of the controller (HUMAN/AI), ensuring unbiased evaluation.
- **Data Gap**: Missing metrics default to $0.0$ (Neutrality).

## 6. FORBIDDEN PRACTICES
### Things Reward Logic is NEVER allowed to do:

1. **NEVER assume intent preference**: Do not reward `PRIMARY_ATTACK` more than `MOVE` if outcomes are identical.
2. **NEVER use hidden state**: Reward must only use data provided in the current `result` payload.
3. **NEVER allow Infinity**: All calculations must be guarded against `inf` and `nan`.
4. **NEVER encode mechanics**: Reward components must use semantic names (`damage_dealt`), not engine names (`health_delta`).
5. **NEVER ignore the Result Contract**: If Java violates the result schema, reward is $0.0$.
