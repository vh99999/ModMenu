import socket
import json
import time

def test_request(payload):
    HOST = '127.0.0.1'
    PORT = 5001
    
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.connect((HOST, PORT))
        s.sendall(json.dumps(payload).encode('utf-8'))
        data = s.recv(1024)
        return json.loads(data.decode('utf-8'))

if __name__ == "__main__":
    print("Testing Generic AI Server v1.1...")
    
    # 0. Test Heartbeat
    print("\nSending HEARTBEAT request...")
    response = test_request({"type": "HEARTBEAT"})
    print("Response:", json.dumps(response, indent=2))

    # 1. Simulate AI in control with HEURISTIC policy
    payload_ai = {
        "state": {
            "health": 0.4, # Threatened
            "target_distance": 2.5, # Close
            "is_colliding": False,
            "energy": 0.9
        },
        "intent_taken": "MOVE",
        "last_confidence": 0.7,
        "controller": "AI",
        "policy_override": "HEURISTIC",
        "result": {
            "damage_dealt": 0.0,
            "damage_received": 0.1,
            "is_alive": True,
            "action_wasted": False
        }
    }
    
    print("\nSending AI-controlled request (Heuristic)...")
    response = test_request(payload_ai)
    print("Response:", json.dumps(response, indent=2))
    # Should expect EVADE because health < 0.5 and distance < 3.0
    
    # 2. Simulate HUMAN in control (Shadow Learning)
    payload_human = {
        "state": {
            "health": 0.7,
            "target_distance": 1.2,
            "is_colliding": True,
            "energy": 0.5
        },
        "intent_taken": "PRIMARY_ATTACK",
        "controller": "HUMAN",
        "result": {
            "damage_dealt": 5.0,
            "damage_received": 0.0,
            "is_alive": True,
            "action_wasted": False
        }
    }
    
    print("\nSending HUMAN-controlled request (Shadow Learning)...")
    response = test_request(payload_human)
    print("Response:", json.dumps(response, indent=2))
    
    # 3. Simulate malformed request
    payload_bad = {
        "state": "not a dictionary"
    }
    
    print("\nSending malformed request...")
    response = test_request(payload_bad)
    print("Response:", json.dumps(response, indent=2))
