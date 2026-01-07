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
    private static final int CONNECT_TIMEOUT_MS = 1000;
    private static final int READ_TIMEOUT_MS = 1000;

    private static final int PROTOCOL_VERSION = 1;

    public AIClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Sends a heartbeat request to verify server health.
     * @return true if the server is alive and responding correctly.
     */
    public boolean sendHeartbeat() {
        JsonObject heartbeat = new JsonObject();
        heartbeat.addProperty("type", "HEARTBEAT");
        heartbeat.addProperty("protocol_version", PROTOCOL_VERSION);

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);

            try (OutputStream out = socket.getOutputStream();
                 InputStream in = socket.getInputStream();
                 PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), true);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {

                writer.println(gson.toJson(heartbeat));
                String response = reader.readLine();
                if (response != null) {
                    JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
                    // Explicit validation of response structure
                    if (!jsonResponse.has("status")) {
                        LOGGER.error("[CONTRACT_VIOLATION] Heartbeat response missing 'status'");
                        return false;
                    }
                    return "OK".equals(jsonResponse.get("status").getAsString());
                }
            }
        } catch (IOException e) {
            LOGGER.debug("Heartbeat failed: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Sends the full AI payload (state, intent_taken, result, etc.) to the server.
     * @param payload The complete JSON structure required by the Python server.
     * @return A Response object containing the intent or error information.
     */
    public Response getNextIntent(JsonObject payload) {
        // Enforce protocol version in all outgoing requests
        payload.addProperty("protocol_version", PROTOCOL_VERSION);

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);

            try (OutputStream out = socket.getOutputStream();
                 InputStream in = socket.getInputStream();
                 PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), true);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {

                // Send complete payload
                writer.println(gson.toJson(payload));

                // Read intent response
                String response = reader.readLine();
                if (response == null) {
                    return new Response(null, 0.0, "EMPTY_RESPONSE");
                }

                JsonObject jsonResponse;
                try {
                    jsonResponse = gson.fromJson(response, JsonObject.class);
                } catch (com.google.gson.JsonSyntaxException e) {
                    return new Response(null, 0.0, "INVALID_JSON_RESPONSE");
                }
                
                // 1. Check for explicit error fields from Python
                if (jsonResponse.has("status") && "ERROR".equals(jsonResponse.get("status").getAsString())) {
                    String error = jsonResponse.has("error") ? jsonResponse.get("error").getAsString() : "Unknown Python error";
                    return new Response(null, 0.0, "SERVER_ERROR: " + error);
                }

                // 2. Validate successful response schema
                if (!jsonResponse.has("intent")) {
                    return new Response(null, 0.0, "MALFORMED_RESPONSE: Missing 'intent'");
                }

                try {
                    String intentStr = jsonResponse.get("intent").getAsString();
                    IntentType intent = IntentType.valueOf(intentStr.toUpperCase());
                    
                    double confidence = jsonResponse.has("confidence") ? jsonResponse.get("confidence").getAsDouble() : 1.0;

                    return new Response(intent, confidence, null);
                } catch (IllegalArgumentException e) {
                    String unknownIntent = jsonResponse.get("intent").getAsString();
                    LOGGER.error("[CONTRACT_VIOLATION] Received unknown intent from Python: {}", unknownIntent);
                    return new Response(null, 0.0, "UNKNOWN_INTENT: " + unknownIntent);
                }
            }
        } catch (IOException e) {
            LOGGER.debug("AI Server communication failure: {}", e.getMessage());
            return new Response(null, 0.0, "COMMUNICATION_FAILURE: " + e.getMessage());
        }
    }

    /**
     * Sends the CONTROL_MODE message to the server.
     * @param mode The target control mode.
     * @return true if the server acknowledged the mode change.
     */
    public boolean sendControlMode(ControlMode mode) {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "CONTROL_MODE");
        payload.addProperty("mode", mode.name());
        payload.addProperty("source", "GUI");
        payload.addProperty("protocol_version", PROTOCOL_VERSION);

        int retries = 0;
        while (retries <= 1) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
                socket.setSoTimeout(READ_TIMEOUT_MS);

                try (OutputStream out = socket.getOutputStream();
                     InputStream in = socket.getInputStream();
                     PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), true);
                     BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {

                    writer.println(gson.toJson(payload));
                    String response = reader.readLine();
                    if (response != null) {
                        JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
                        if (jsonResponse.has("status") && "OK".equals(jsonResponse.get("status").getAsString())) {
                            return true;
                        }
                    }
                }
            } catch (IOException e) {
                LOGGER.warn("Control mode update failed (attempt {}): {}", retries + 1, e.getMessage());
            }
            retries++;
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
}
