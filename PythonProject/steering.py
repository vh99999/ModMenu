import math
import numpy as np
from typing import List, Tuple, Optional

class SteeringBehaviors:
    """
    Implements steering forces for smoother movement.
    """
    @staticmethod
    def seek(current_pos: Tuple[float, float, float], target_pos: Tuple[float, float, float], max_speed: float = 1.0) -> np.ndarray:
        desired = np.array(target_pos) - np.array(current_pos)
        dist = np.linalg.norm(desired)
        if dist > 0:
            desired = (desired / dist) * max_speed
        return desired

    @staticmethod
    def flee(current_pos: Tuple[float, float, float], threat_pos: Tuple[float, float, float], max_speed: float = 1.0) -> np.ndarray:
        return -SteeringBehaviors.seek(current_pos, threat_pos, max_speed)

    @staticmethod
    def arrive(current_pos: Tuple[float, float, float], target_pos: Tuple[float, float, float], slowing_radius: float = 2.0, max_speed: float = 1.0) -> np.ndarray:
        desired = np.array(target_pos) - np.array(current_pos)
        dist = np.linalg.norm(desired)
        if dist > 0:
            if dist < slowing_radius:
                desired = (desired / dist) * max_speed * (dist / slowing_radius)
            else:
                desired = (desired / dist) * max_speed
        return desired

    @staticmethod
    def avoid_obstacles(current_pos: Tuple[float, float, float], obstacles: List[Tuple[float, float, float, float]], avoid_distance: float = 2.0, max_speed: float = 1.0) -> np.ndarray:
        """
        obstacles: list of (x, y, z, radius)
        """
        steering = np.zeros(3)
        for ox, oy, oz, orad in obstacles:
            obs_pos = np.array([ox, oy, oz])
            dist = np.linalg.norm(obs_pos - np.array(current_pos))
            if dist < avoid_distance + orad:
                # Force away from obstacle
                force = np.array(current_pos) - obs_pos
                force_dist = np.linalg.norm(force)
                if force_dist > 0:
                    steering += (force / force_dist) * max_speed * (1.0 - dist / (avoid_distance + orad))
        return steering

    @staticmethod
    def compute_combined_velocity(current_pos: Tuple[float, float, float], 
                                  behaviors: List[Tuple[np.ndarray, float]], 
                                  max_speed: float = 1.0) -> Tuple[float, float, float]:
        """
        behaviors: list of (force, weight)
        """
        total_force = np.zeros(3)
        for force, weight in behaviors:
            total_force += force * weight
            
        force_mag = np.linalg.norm(total_force)
        if force_mag > max_speed:
            total_force = (total_force / force_mag) * max_speed
            
        return tuple(total_force.tolist())
