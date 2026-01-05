# SYSTEM FAILURE PROTOCOL (v1.0.0)

This document defines how the AI system survives environmental and infrastructure failures, ensuring data preservation and safety.

## 1. ENVIRONMENTAL FAILURE TAXONOMY

| Category | Type | Description | Detection | Severity |
| :--- | :--- | :--- | :--- | :--- |
| **Infrastructure** | `DISK_FULL` | Filesystem cannot accept new logs or models. | `IOError` during write | CRITICAL |
| **Infrastructure** | `CLOCK_JUMP` | System clock moves backwards or jumps > 1 hour. | Monotonic vs Wall clock drift | HIGH |
| **Connectivity** | `NETWORK_DROP` | Connection between Java and Python is lost. | Socket timeout / Heartbeat miss | MEDIUM |
| **Persistence** | `STATE_CORRUPTION` | Saved memory or model files are unreadable. | `JSONDecodeError` on load | HIGH |
| **Runtime** | `CRASH_RECOVERY` | System restarting after ungraceful shutdown. | Absence of clean exit flag | MEDIUM |

## 2. DISASTER MODES (SYSTEM LEVEL)

| Mode | Description | Action Taken | Entry Condition |
| :--- | :--- | :--- | :--- |
| **READ_ONLY_DISK** | Disk is full. | Stop all logging and memory storage. Inference only. | `DISK_FULL` |
| **TEMPORAL_LOCK** | Clock is unstable. | Suspend all time-dependent logic (TTL, timeouts). | `CLOCK_JUMP` |
| **ISOLATED** | Network lost. | System enters standby. Resume only after handshake. | `NETWORK_DROP` |

## 3. INCIDENT PLAYBOOKS

### A) DISK FULL
1. **Detection**: Any `write` operation fails with `No space left on device`.
2. **Containment**: Immediately stop appending to `MemoryBuffer` and `Auditor`.
3. **Mitigation**: Switch to `READ_ONLY_DISK` mode. Log ONE final warning to console.
4. **Recovery**: Manually clear disk. System resumes after successful test write.

### B) CLOCK JUMP
1. **Detection**: Compare `time.time()` (wall clock) with `time.monotonic()`. If delta changes > 1s, jump detected.
2. **Containment**: Invalidate current `episode_id`. 
3. **Mitigation**: Switch to `TEMPORAL_LOCK`. All TTL checks are bypassed (assume STALE).
4. **Recovery**: Stabilize clock. Reset monotonic baseline.

### C) NETWORK DROP (Python Side)
1. **Detection**: Socket `recv` or `accept` returns error or timeout > 10s.
2. **Containment**: Clear active decision state.
3. **Mitigation**: Enter `ISOLATED` standby. Return `STOP` intent if any further requests arrive.
4. **Recovery**: Wait for new successful `HEARTBEAT` from Java.

### D) PYTHON CRASH (Mid-decision)
1. **Detection**: On startup, check for `active_decision.tmp` file.
2. **Containment**: If exists, discard it (potentially corrupted).
3. **Mitigation**: Log "RECOVERY FROM UNGRACEFUL SHUTDOWN".
4. **Recovery**: Normal startup, but increment `episode_id` immediately.

## 4. "SYSTEM MUST SURVIVE THIS" CHECKLIST

- [ ] System remains operational (Inference only) if Disk fills.
- [ ] Memory buffer does not corrupt if crash happens during `push`.
- [ ] Learning does not poison if clock jumps (Episode boundary forced).
- [ ] System does not crash if `failures/` directory is unwritable.
- [ ] No duplicated experiences enter training after network reconnection.

## 5. RECOVERY FLOWS

### Level 1: Automatic
- **Network**: Python socket enters `LISTEN` mode again.
- **Clock**: Resynchronize monotonic offset.

### Level 2: Human Required
- **Disk**: Clear space and restart server.
- **Persistence**: Restore from backup if `memory.json` is corrupted.

## 6. FORBIDDEN BEHAVIORS UNDER FAILURE
1. **NEVER** retry a write to a full disk more than once per minute.
2. **NEVER** trust `java_timestamp` if local clock has jumped.
3. **NEVER** resume a training session if the previous one crashed mid-save.
4. **NEVER** allow a crash in the failure manager itself to take down the server.
