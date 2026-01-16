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
    private static int connectTimeoutMs = 5000;
    private static int readTimeoutMs = 0;

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
     * Sends the full AI payload (state, intent_taken, result, etc.) to the server.
     * @param payload The complete JSON structure required by the Python server.
     * @return A Response object containing the intent or error information.
     */
    public synchronized Response getNextIntent(JsonObject payload) {
        JsonObject jsonResponse = sendCommand(payload);
        
        if (jsonResponse == null) {
            return new Response(IntentType.STOP, 0.0, new JsonObject(), "NULL_JSON_RESPONSE", -1);
        }

        if (jsonResponse.has("status") && "ERROR".equals(jsonResponse.get("status").getAsString())) {
            String error = jsonResponse.has("error") ? jsonResponse.get("error").getAsString() : "Unknown Python error";
            return new Response(IntentType.STOP, 0.0, new JsonObject(), "SERVER_ERROR: " + error, -1);
        }

        if (jsonResponse.has("errorMessage")) {
            return new Response(IntentType.STOP, 0.0, new JsonObject(), jsonResponse.get("errorMessage").getAsString(), -1);
        }

        if (!jsonResponse.has("intent") || jsonResponse.get("intent").isJsonNull()) {
            return new Response(IntentType.STOP, 0.0, new JsonObject(), "MALFORMED_RESPONSE: Missing 'intent'", -1);
        }

        try {
            String intentStr = jsonResponse.get("intent").getAsString();
            IntentType intent = IntentType.valueOf(intentStr.toUpperCase());
            
            if (intent == IntentType.NO_OP) {
                intent = IntentType.STOP;
            }

            double confidence = (jsonResponse.has("confidence") && !jsonResponse.get("confidence").isJsonNull()) 
                ? jsonResponse.get("confidence").getAsDouble() : 1.0;

            JsonObject params = (jsonResponse.has("intent_params") && !jsonResponse.get("intent_params").isJsonNull())
                ? jsonResponse.getAsJsonObject("intent_params") : new JsonObject();

            int targetId = (jsonResponse.has("target_id") && !jsonResponse.get("target_id").isJsonNull())
                ? jsonResponse.get("target_id").getAsInt() : -1;

            return new Response(intent, confidence, params, null, targetId);
        } catch (Exception e) {
            LOGGER.error("[AI_NET] Unknown intent received: {}", jsonResponse.get("intent"));
            return new Response(IntentType.STOP, 0.0, new JsonObject(), "INTENT_PARSE_FAILURE", -1);
        }
    }

    /**
     * Sends a generic command to the Python server and returns the raw JSON response.
     */
    public synchronized JsonObject sendCommand(JsonObject payload) {
        try {
            ensureConnected();

            String json = gson.toJson(payload) + "\n";
            byte[] payloadBytes = json.getBytes(StandardCharsets.UTF_8);
            
            LOGGER.info("[AI_NET] Outbound JSON: {}", json.trim());
            out.write(payloadBytes);
            out.flush();

            String response = reader.readLine();
            
            if (response == null) {
                LOGGER.warn("[AI_NET] Connection closed by Python server (EOF).");
                closeQuietly();
                JsonObject err = new JsonObject();
                err.addProperty("errorMessage", "CONNECTION_CLOSED");
                return err;
            }

            LOGGER.info("[AI_NET] Inbound JSON: {}", response.trim());

            try {
                return gson.fromJson(response, JsonObject.class);
            } catch (Exception e) {
                LOGGER.error("[AI_NET] Parse failure for response: {}", response);
                JsonObject err = new JsonObject();
                err.addProperty("errorMessage", "INVALID_JSON_RESPONSE");
                return err;
            }
        } catch (IOException e) {
            String errorMsg = e.getMessage();
            LOGGER.debug("[AI_NET] Communication failure: {}", errorMsg);
            closeQuietly();
            JsonObject err = new JsonObject();
            err.addProperty("errorMessage", "COMMUNICATION_FAILURE: " + errorMsg);
            return err;
        } catch (Throwable t) {
            LOGGER.error("[AI_NET] Critical client error: {}", t.getMessage());
            JsonObject err = new JsonObject();
            err.addProperty("errorMessage", "CRITICAL_CLIENT_ERROR: " + t.getMessage());
            return err;
        }
    }

    /**
     * Opaque response container to allow AIController to handle fallbacks.
     */
    public static class Response {
        public final IntentType intent;
        public final double confidence;
        public final JsonObject params;
        public final String errorMessage;
        public final int targetId;

        public Response(IntentType intent, double confidence, JsonObject params, String errorMessage, int targetId) {
            this.intent = intent;
            this.confidence = confidence;
            this.params = params;
            this.errorMessage = errorMessage;
            this.targetId = targetId;
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
