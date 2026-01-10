import socket
import json
import time

def send_payload(payload):
    HOST = '127.0.0.1'
    PORT = 5001
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.connect((HOST, PORT))
        if isinstance(payload, list):
            for p in payload:
                s.sendall((json.dumps(p) + "\n").encode('utf-8'))
        else:
            s.sendall((json.dumps(payload) + "\n").encode('utf-8'))
        
        # Read all responses
        s.settimeout(1.0)
        responses = []
        try:
            while True:
                data = s.recv(16384)
                if not data: break
                for line in data.decode('utf-8').strip().split('\n'):
                    if line:
                        responses.append(json.loads(line))
        except socket.timeout:
            pass
        return responses

if __name__ == "__main__":
    print("=== Protocol Hardening Validation ===")

    # 1. Test Invalid Authority
    print("\n1. Testing Invalid Authority ('ACTIVE')...")
    payload = {
        "experience_id": "test_auth_invalid",
        "type": "HEARTBEAT",
        "authority": "ACTIVE"
    }
    resp = send_payload(payload)
    print(f"Response: {json.dumps(resp, indent=2)}")

    # 2. Test Missing Required Fields
    print("\n2. Testing Missing Required Fields (missing 'health')...")
    payload = {
        "experience_id": "test_missing_fields",
        "state": {
            "energy": 1.0,
            "target_distance": 10.0,
            "is_colliding": False
        },
        "authority": "AUTHORITATIVE"
    }
    resp = send_payload(payload)
    print(f"Response: {json.dumps(resp, indent=2)}")

    # 3. Test Back-to-back Requests (One tick rule)
    print("\n3. Testing Multiple Requests in one tick...")
    payloads = [
        {"experience_id": "req_1", "state": {"health": 1.0, "energy": 1.0, "target_distance": 10.0, "is_colliding": False}, "authority": "AUTHORITATIVE"},
        {"experience_id": "req_2", "state": {"health": 1.0, "energy": 1.0, "target_distance": 10.0, "is_colliding": False}, "authority": "AUTHORITATIVE"}
    ]
    resp = send_payload(payloads)
    print(f"Responses: {len(resp)}")
    for i, r in enumerate(resp):
        print(f"Resp {i+1}: {r.get('experience_id')} - {r.get('status')} - {r.get('reason')}")

    # 4. Test Valid Request (ACTING)
    print("\n4. Testing Valid Request (AI Acting)...")
    payload = {
        "experience_id": "test_valid_1",
        "state": {
            "health": 1.0,
            "energy": 1.0,
            "target_distance": 10.0,
            "target_yaw": 0.0,
            "is_colliding": False,
            "pos_x": 0.0,
            "pos_y": 0.0,
            "pos_z": 0.0
        },
        "authority": "AUTHORITATIVE"
    }
    resp = send_payload(payload)
    print(f"Response 1: {json.dumps(resp, indent=2)}")

    payload["experience_id"] = "test_valid_2"
    resp = send_payload(payload)
    print(f"Response 2: {json.dumps(resp, indent=2)}")

    print("\n=== Validation Complete ===")
