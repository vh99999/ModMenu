import enum
import logging
from typing import List, Callable, Dict, Any, Optional

logger = logging.getLogger(__name__)

class NodeStatus(enum.Enum):
    SUCCESS = enum.auto()
    FAILURE = enum.auto()
    RUNNING = enum.auto()

class Node:
    def __init__(self, name: str):
        self.name = name

    def tick(self, state: Dict[str, Any]) -> NodeStatus:
        raise NotImplementedError

class Composite(Node):
    def __init__(self, name: str, children: List[Node] = None):
        super().__init__(name)
        self.children = children or []

    def add_child(self, child: Node):
        self.children.append(child)

class Selector(Composite):
    """
    Ticks children until one succeeds.
    """
    def tick(self, state: Dict[str, Any]) -> NodeStatus:
        for child in self.children:
            status = child.tick(state)
            if status != NodeStatus.FAILURE:
                return status
        return NodeStatus.FAILURE

class Sequence(Composite):
    """
    Ticks children until one fails.
    """
    def tick(self, state: Dict[str, Any]) -> NodeStatus:
        for child in self.children:
            status = child.tick(state)
            if status != NodeStatus.SUCCESS:
                return status
        return NodeStatus.SUCCESS

class Leaf(Node):
    def __init__(self, name: str, action: Callable[[Dict[str, Any]], NodeStatus]):
        super().__init__(name)
        self.action = action

    def tick(self, state: Dict[str, Any]) -> NodeStatus:
        return self.action(state)

class Condition(Node):
    def __init__(self, name: str, check: Callable[[Dict[str, Any]], bool]):
        super().__init__(name)
        self.check = check

    def tick(self, state: Dict[str, Any]) -> NodeStatus:
        return NodeStatus.SUCCESS if self.check(state) else NodeStatus.FAILURE

class BehaviorTree:
    def __init__(self, root: Node):
        self.root = root

    def tick(self, state: Dict[str, Any]) -> NodeStatus:
        return self.root.tick(state)
