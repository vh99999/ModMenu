# AI Authority Bridge: Control Protocol

## Overview
The AI Authority Bridge provides a strict, exclusive control mechanism between the human player and the Python AI Server. It ensures that at any given time, only one entity has authority over the player's actions.

## Control Modes
The system supports two mutually exclusive control modes:

1. **HUMAN (Default)**
   - The human player has full control over the character.
   - The AI system operates in "Shadow Learning" mode, observing human actions and reporting them to the Python server for training purposes.
   - AI intents are ignored for execution but recorded for data collection.

2. **AI**
   - The Python AI Server has exclusive authority over the character.
   - All human keyboard and mouse inputs are blocked and released.
   - The AI's intents are executed exactly as received.
   - The system reverts to HUMAN mode immediately upon any communication failure or exception.

## GUI Integration
Control mode can only be toggled via the **System Upgrades** GUI.
- **AI: OFF**: System is in HUMAN mode (Default).
- **AI: ON**: System is in AI mode.

Toggling is a local authority switch. The Python AI Server is informed of the current authority in every state payload.

## Fail-Safe Mechanisms
To ensure player safety and system stability, the bridge will force-revert to HUMAN mode under the following conditions:
- Python AI Server becomes unreachable.
- Malformed or invalid data is received from the server.
- Any exception occurs in the AI execution path.

## Protocol Specification
Authority state is included in every payload sent to the Python server:
```json
{
  "state": { ... },
  "intent_taken": "...",
  "controller": "AI" | "HUMAN",
  "protocol_version": 1
}
```
Python is the sole authority for learning decisions based on the `controller` field. Java never sends explicit learning flags.
