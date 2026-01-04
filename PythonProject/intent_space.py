from enum import Enum
from typing import Dict, Any

class Intent(str, Enum):
    """
    Fixed set of high-level semantic intents.
    These are independent of specific game mechanics or keybinds.
    """
    PRIMARY_ATTACK = "PRIMARY_ATTACK"
    EVADE = "EVADE"
    MOVE = "MOVE"
    HOLD = "HOLD"
    RELEASE = "RELEASE"
    STOP = "STOP"
    JUMP = "JUMP"

    @property
    def metadata(self) -> Dict[str, Any]:
        """
        Returns architectural metadata for each intent.
        This is engine-agnostic and helps the policy or bridge 
        understand the 'nature' of the intent.
        """
        registry = {
            Intent.PRIMARY_ATTACK: {
                "risk_level": "MEDIUM",
                "cooldown_sensitive": True,
                "priority": 3,
                "is_combat": True,
                "is_atomic": True
            },
            Intent.EVADE: {
                "risk_level": "LOW",
                "cooldown_sensitive": True,
                "priority": 4,
                "is_combat": False,
                "is_atomic": True
            },
            Intent.MOVE: {
                "risk_level": "LOW",
                "cooldown_sensitive": False,
                "priority": 1,
                "is_combat": False,
                "is_atomic": True
            },
            Intent.HOLD: {
                "risk_level": "LOW",
                "cooldown_sensitive": False,
                "priority": 2,
                "is_combat": False,
                "is_atomic": True
            },
            Intent.RELEASE: {
                "risk_level": "LOW",
                "cooldown_sensitive": False,
                "priority": 2,
                "is_combat": False,
                "is_atomic": True
            },
            Intent.STOP: {
                "risk_level": "NONE",
                "cooldown_sensitive": False,
                "priority": 5,
                "is_combat": False,
                "is_atomic": True
            },
            Intent.JUMP: {
                "risk_level": "LOW",
                "cooldown_sensitive": True,
                "priority": 2,
                "is_combat": False,
                "is_atomic": True
            }
        }
        return registry.get(self, {})

    @classmethod
    def has_value(cls, value):
        return value in cls._value2member_map_
