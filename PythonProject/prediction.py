import numpy as np
from typing import Tuple, Optional

class TargetPredictor:
    """
    Uses a simple Kalman Filter to predict target position.
    State: [x, y, z, vx, vy, vz]
    """
    def __init__(self, dt: float = 0.05): # 1 tick = 0.05s
        self.dt = dt
        # State transition matrix
        self.F = np.array([
            [1, 0, 0, dt, 0, 0],
            [0, 1, 0, 0, dt, 0],
            [0, 0, 1, 0, 0, dt],
            [0, 0, 0, 1, 0, 0],
            [0, 0, 0, 0, 1, 0],
            [0, 0, 0, 0, 0, 1]
        ])
        # Measurement matrix (we only measure x, y, z)
        self.H = np.array([
            [1, 0, 0, 0, 0, 0],
            [0, 1, 0, 0, 0, 0],
            [0, 0, 1, 0, 0, 0]
        ])
        
        self.Q = np.eye(6) * 0.01 # Process noise
        self.R = np.eye(3) * 0.1  # Measurement noise
        self.P = np.eye(6)        # Covariance
        self.x = None             # State estimate

    def update(self, pos: Tuple[float, float, float]):
        z = np.array(pos)
        if self.x is None:
            self.x = np.array([pos[0], pos[1], pos[2], 0, 0, 0])
            return

        # Predict
        x_pred = self.F @ self.x
        P_pred = self.F @ self.P @ self.F.T + self.Q

        # Update
        y = z - self.H @ x_pred
        S = self.H @ P_pred @ self.H.T + self.R
        K = P_pred @ self.H.T @ np.linalg.inv(S)
        self.x = x_pred + K @ y
        self.P = (np.eye(6) - K @ self.H) @ P_pred

    def predict(self, ticks_ahead: int) -> Tuple[float, float, float]:
        if self.x is None:
            return (0.0, 0.0, 0.0)
        
        future_dt = ticks_ahead * self.dt
        F_future = np.array([
            [1, 0, 0, future_dt, 0, 0],
            [0, 1, 0, 0, future_dt, 0],
            [0, 0, 1, 0, 0, future_dt],
            [0, 0, 0, 1, 0, 0],
            [0, 0, 0, 0, 1, 0],
            [0, 0, 0, 0, 0, 1]
        ])
        x_future = F_future @ self.x
        return (float(x_future[0]), float(x_future[1]), float(x_future[2]))

    def get_velocity(self) -> Tuple[float, float, float]:
        if self.x is None:
            return (0.0, 0.0, 0.0)
        return (float(self.x[3]), float(self.x[4]), float(self.x[5]))
