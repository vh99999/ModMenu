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
        try {
            ensureConnected();

            // Send complete payload
            String json = gson.toJson(payload) + "\n";
            byte[] payloadBytes = json.getBytes(StandardCharsets.UTF_8);
            
            // LOGGING: EXACT JSON sent (Required)
            LOGGER.info("[AI_NET] Outbound JSON: {}", json.trim());
            out.write(payloadBytes);
            out.flush();

            // Read intent response - STRICT ONE LINE - Block until response
            String response = reader.readLine();
            
            if (response == null) {
                LOGGER.warn("[AI_NET] Connection closed by Python server (EOF).");
                closeQuietly();
                return new Response(IntentType.STOP, 0.0, "CONNECTION_CLOSED");
            }

            // LOGGING: Response received (Required)
            LOGGER.info("[AI_NET] Inbound JSON: {}", response.trim());

            JsonObject jsonResponse;
            try {
                jsonResponse = gson.fromJson(response, JsonObject.class);
            } catch (Exception e) {
                LOGGER.error("[AI_NET] Parse failure for response: {}", response);
                return new Response(IntentType.STOP, 0.0, "INVALID_JSON_RESPONSE");
            }

            if (jsonResponse == null) {
                return new Response(IntentType.STOP, 0.0, "NULL_JSON_RESPONSE");
            }
            
            // 1. Check for explicit error fields from Python
            if (jsonResponse.has("status") && "ERROR".equals(jsonResponse.get("status").getAsString())) {
                String error = jsonResponse.has("error") ? jsonResponse.get("error").getAsString() : "Unknown Python error";
                return new Response(IntentType.STOP, 0.0, "SERVER_ERROR: " + error);
            }

            // 2. Validate successful response schema
            if (!jsonResponse.has("intent") || jsonResponse.get("intent").isJsonNull()) {
                return new Response(IntentType.STOP, 0.0, "MALFORMED_RESPONSE: Missing 'intent'");
            }

            try {
                String intentStr = jsonResponse.get("intent").getAsString();
                IntentType intent = IntentType.valueOf(intentStr.toUpperCase());
                
                // Map NO_OP to STOP to comply with Requirement 8 (No NO_OP sent by client)
                if (intent == IntentType.NO_OP) {
                    intent = IntentType.STOP;
                }

                double confidence = (jsonResponse.has("confidence") && !jsonResponse.get("confidence").isJsonNull()) 
                    ? jsonResponse.get("confidence").getAsDouble() : 1.0;

                return new Response(intent, confidence, null);
            } catch (Exception e) {
                LOGGER.error("[AI_NET] Unknown intent received: {}", jsonResponse.get("intent"));
                return new Response(IntentType.STOP, 0.0, "INTENT_PARSE_FAILURE");
            }
        } catch (IOException e) {
            String errorMsg = e.getMessage();
            LOGGER.debug("[AI_NET] Communication failure: {}", errorMsg);
            closeQuietly();
            return new Response(IntentType.STOP, 0.0, "COMMUNICATION_FAILURE: " + errorMsg);
        } catch (Throwable t) {
            LOGGER.error("[AI_NET] Critical client error: {}", t.getMessage());
            return new Response(IntentType.STOP, 0.0, "CRITICAL_CLIENT_ERROR: " + t.getMessage());
        }
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
