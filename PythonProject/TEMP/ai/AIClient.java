package com.example.modmenu.ai;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Responsible for low-level socket communication with the Python AI server.
 * 
 * ARCHITECTURE ROLE:
 * This is a stateless transport layer (bridge).
 * - MUST NOT interpret responses beyond basic JSON/Enum mapping.
 * - MUST NOT validate rewards or logic.
 * - MUST NOT "fix" or "normalize" intents.
 */
public class AIClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final String host;
    private final int port;
    private final Gson gson = new Gson();
    private static int connectTimeoutMs = 250;
    private static int readTimeoutMs = 250;

    private Socket socket;
    private OutputStream out;
    private BufferedReader reader;

    public static void setTimeouts(int connect, int read) {
        connectTimeoutMs = connect;
        readTimeoutMs = read;
    }

    private static final int PROTOCOL_VERSION = 1;

    public AIClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    private synchronized void ensureConnected() throws IOException {
        if (socket == null || socket.isClosed() || !socket.isConnected()) {
            closeQuietly();
            LOGGER.info("[AI_NET] [DEBUG] Connecting to {}:{}", host, port);
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
            socket.setSoTimeout(readTimeoutMs);
            out = socket.getOutputStream();
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            
            // PROBE HANDSHAKE - Send once per connection
            String probe = "{\"_probe\":\"java_handshake\",\"ts\":" + System.currentTimeMillis() + "}\n";
            byte[] probeBytes = probe.getBytes(StandardCharsets.UTF_8);
            out.write(probeBytes);
            out.flush();
            LOGGER.info("[AI_NET] [DEBUG] Handshake sent.");
        }
    }

    private void closeQuietly() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {}
        socket = null;
        out = null;
        reader = null;
    }

    /**
     * Sends a heartbeat request to verify server health.
     * @return true if the server is alive and responding correctly.
     */
    public synchronized boolean sendHeartbeat() {
        JsonObject heartbeat = new JsonObject();
        heartbeat.addProperty("type", "HEARTBEAT");
        heartbeat.addProperty("protocol_version", PROTOCOL_VERSION);

        try {
            ensureConnected();
            
            String json = gson.toJson(heartbeat) + "\n";
            byte[] payloadBytes = json.getBytes(StandardCharsets.UTF_8);
            out.write(payloadBytes);
            out.flush();
            
            String response = reader.readLine();
            if (response != null) {
                JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
                return jsonResponse.has("status") && "OK".equals(jsonResponse.get("status").getAsString());
            } else {
                closeQuietly();
            }
        } catch (IOException e) {
            LOGGER.debug("Heartbeat failed: {}", e.getMessage());
            closeQuietly();
        }
        return false;
    }

    /**
     * Sends the full AI payload (state, intent_taken, result, etc.) to the server.
     * @param payload The complete JSON structure required by the Python server.
     * @return A Response object containing the intent or error information.
     */
    public synchronized Response getNextIntent(JsonObject payload) {
        try {
            // Enforce protocol version in all outgoing requests
            payload.addProperty("protocol_version", PROTOCOL_VERSION);

            ensureConnected();

            // Send complete payload
            String json = gson.toJson(payload) + "\n";
            byte[] payloadBytes = json.getBytes(StandardCharsets.UTF_8);
            
            LOGGER.info("[AI_NET] Sending payload: {}", json.trim());
            out.write(payloadBytes);
            out.flush();

            // Read intent response - STRICT ONE LINE
            String response = reader.readLine();
            LOGGER.info("[AI_NET] Raw response received: {}", response);

            if (response == null) {
                LOGGER.info("[AI_NET] Fallback used: EMPTY_RESPONSE (Connection closed by peer) -> NO_OP");
                closeQuietly();
                return new Response(IntentType.NO_OP, 0.0, "EMPTY_RESPONSE");
            }

            JsonObject jsonResponse;
            try {
                jsonResponse = gson.fromJson(response, JsonObject.class);
            } catch (Exception e) {
                LOGGER.debug("[AI_NET] Parse failure: {}", e.getMessage());
                return new Response(IntentType.NO_OP, 0.0, "INVALID_JSON_RESPONSE");
            }

            if (jsonResponse == null) {
                return new Response(IntentType.NO_OP, 0.0, "NULL_JSON_RESPONSE");
            }
            
            // 1. Check for explicit error fields from Python
            if (jsonResponse.has("status") && "ERROR".equals(jsonResponse.get("status").getAsString())) {
                String error = jsonResponse.has("error") ? jsonResponse.get("error").getAsString() : "Unknown Python error";
                return new Response(IntentType.NO_OP, 0.0, "SERVER_ERROR: " + error);
            }

            // 2. Validate successful response schema
            if (!jsonResponse.has("intent") || jsonResponse.get("intent").isJsonNull()) {
                return new Response(IntentType.NO_OP, 0.0, "MALFORMED_RESPONSE: Missing 'intent'");
            }

            try {
                String intentStr = jsonResponse.get("intent").getAsString();
                IntentType intent = IntentType.valueOf(intentStr.toUpperCase());
                
                double confidence = (jsonResponse.has("confidence") && !jsonResponse.get("confidence").isJsonNull()) 
                    ? jsonResponse.get("confidence").getAsDouble() : 1.0;

                return new Response(intent, confidence, null);
            } catch (Exception e) {
                return new Response(IntentType.NO_OP, 0.0, "INTENT_PARSE_FAILURE");
            }
        } catch (IOException e) {
            String errorMsg = (e instanceof java.net.SocketTimeoutException) ? "TIMEOUT" : e.getMessage();
            LOGGER.debug("[AI_NET] Communication failure: {} -> NO_OP", errorMsg);
            closeQuietly();
            return new Response(IntentType.NO_OP, 0.0, "COMMUNICATION_FAILURE: " + errorMsg);
        } catch (Throwable t) {
            LOGGER.debug("[AI_NET] Critical client error: {} -> NO_OP", t.getMessage());
            return new Response(IntentType.NO_OP, 0.0, "CRITICAL_CLIENT_ERROR: " + t.getMessage());
        }
    }

    /**
     * Sends the CONTROL_MODE message to the server.
     * @param mode The target control mode.
     * @return true if the server acknowledged the mode change.
     */
    public synchronized boolean sendControlMode(ControlMode mode) {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "CONTROL_MODE");
        payload.addProperty("mode", mode.name());
        payload.addProperty("source", "GUI");
        payload.addProperty("protocol_version", PROTOCOL_VERSION);

        try {
            ensureConnected();

            String json = gson.toJson(payload) + "\n";
            byte[] payloadBytes = json.getBytes(StandardCharsets.UTF_8);
            out.write(payloadBytes);
            out.flush();

            String response = reader.readLine();
            if (response != null) {
                JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
                return jsonResponse.has("status") && "SUCCESS".equals(jsonResponse.get("status").getAsString());
            } else {
                closeQuietly();
            }
        } catch (IOException e) {
            LOGGER.warn("Control mode update failed: {}", e.getMessage());
            closeQuietly();
        }
        return false;
    }

    /**
     * Opaque response container to allow AIController to handle fallbacks.
     */
    public static class Response {
        public final IntentType intent;
        public final double confidence;
        public final String errorMessage;

        public Response(IntentType intent, double confidence, String errorMessage) {
            this.intent = intent;
            this.confidence = confidence;
            this.errorMessage = errorMessage;
        }

        public boolean isSuccess() {
            return intent != null && errorMessage == null;
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x ", b));
        }
        return sb.toString().trim();
    }
}
