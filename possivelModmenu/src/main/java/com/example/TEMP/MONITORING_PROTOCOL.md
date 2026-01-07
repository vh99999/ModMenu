# CONTINUOUS MONITORING PROTOCOL (v1.0.0)

This document defines the formal requirements for live monitoring, anomaly detection, and auto-protection of the AI system.

## 1. CORE MONITORING METRICS

Metrics are calculated over a sliding window (default: 1000 cycles).

| Metric | Code Name | Description | Formula / Source |
| :--- | :--- | :--- | :--- |
| **Survival Rate** | `survival_rate` | Ratio of cycles where `is_alive` is true. | `count(is_alive) / total_cycles` |
| **Reward Trend** | `reward_mean` | Average reward over the window. | `sum(reward) / total_cycles` |
| **Intent Drift** | `intent_drift` | Max deviation from baseline intent distribution. | `max(abs(current_freq - baseline_freq))` |
| **Policy Divergence**| `ml_divergence` | Frequency of ML differing from Heuristic. | `divergence_count / ml_calls` |
| **Fallback Rate** | `fallback_rate` | Frequency of non-ML suggestions when ML is active. | `fallback_count / total_cycles` |
| **Failure Rate** | `ml_failure_rate`| Frequency of ML timeouts or invalid outputs. | `(timeouts + errors) / ml_calls` |

## 2. ALERT MATRIX & THRESHOLDS

| Alert Level | Condition | Trigger Event | Metric Threshold |
| :--- | :--- | :--- | :--- |
| **WARNING** | `REWARD_DEGRADATION` | Reward trend dropping significantly. | `reward_mean < 0.05` |
| **WARNING** | `HIGH_DIVERGENCE` | ML diverging from Heuristics often. | `ml_divergence > 0.4` |
| **CRITICAL** | `SURVIVAL_CRASH` | AI is dying repeatedly. | `survival_rate < 0.5` |
| **CRITICAL** | `INTENT_COLLAPSE` | AI only choosing one or two intents. | `max(freq) > 0.9` or `any(freq) < 0.01` |
| **CRITICAL** | `CONTRACT_BREACH` | High rate of Java/Python violations. | `violation_rate > 0.1` |
| **CRITICAL** | `LATENCY_SPIKE` | Inference consistently slow. | `avg_inference_ms > 40` |

## 3. AUTO-PROTECTION FLOW (THE SENTINEL)

Monitoring MUST trigger automatic mitigations when CRITICAL thresholds are breached.

### A) Degradation Levels:
1. **NONE**: ML operates normally.
2. **SOFT_DEGRADE**: ML bypassed for 100 cycles (Cooldown).
3. **HARD_DISABLE**: ML kill-switch engaged until manual reset.

### B) Trigger Logic:
- **IF** `ml_failure_rate > 0.05` **OR** `ml_timeout_rate > 0.02`: Trigger **SOFT_DEGRADE**.
- **IF** `survival_rate < 0.3` **OR** `violation_rate > 0.15`: Trigger **HARD_DISABLE**.
- **IF** `REWARD_DEGRADATION` persists for 3 windows: Trigger **SOFT_DEGRADE**.

## 4. DASHBOARD CONTRACT (VISUALIZATION)

| View | Requirement | Aggregation | Retention |
| :--- | :--- | :--- | :--- |
| **Health Overview** | Status (OK/WARN/CRIT), ML state, Active Model. | Real-time | 24h |
| **Performance** | Reward & Survival Trend lines. | 100-cycle bins | 7 days |
| **Intent Map** | Radar chart of intent frequencies. | 1000-cycle window | 7 days |
| **Audit Logs** | Filterable list of CRITICAL violations. | Per-event | 30 days |

## 5. FORBIDDEN MONITORING SHORTCUTS

1. **NEVER** ignore survival crashes, even if reward is high.
2. **NEVER** trust ML self-reported confidence during a degradation.
3. **NEVER** reset auto-protection cooldowns automatically (requires manual or time-based expiry).
4. **NEVER** disable monitoring logic while the system is live.

## 6. NON-NEGOTIABLE MONITORING SUMMARY
- Monitoring is the **IMMUNE SYSTEM** of the AI.
- Alerts must be **ACTIONABLE** (linked to a mitigation).
- Safety is prioritized over model performance.
- False positives are preferred over missed failures.
