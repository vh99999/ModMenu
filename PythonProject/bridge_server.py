import socket
import json
import threading

HOST = "127.0.0.1"
PORT = 5000

def handle_client(conn, addr):
    with conn:
        data = conn.recv(8192)
        if not data:
            return

        msg = json.loads(data.decode("utf-8"))

        print("Recebido do Minecraft:", msg)

        response = {
            "type": "ACTION",
            "action": 0,
            "confidence": 1.0
        }

        conn.sendall(json.dumps(response).encode("utf-8"))

def start_server():
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.bind((HOST, PORT))
        s.listen()
        print("AI Server rodando em localhost:5000")

        while True:
            conn, addr = s.accept()
            threading.Thread(
                target=handle_client,
                args=(conn, addr),
                daemon=True
            ).start()

if __name__ == "__main__":
    start_server()
