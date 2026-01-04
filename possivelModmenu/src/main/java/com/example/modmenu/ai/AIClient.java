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
        heartbeat.addProperty("protocol_version", 1);

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
                if (response != null) {
                    JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
                    if (jsonResponse.has("intent")) {
                        try {
                            IntentType intent = IntentType.valueOf(jsonResponse.get("intent").getAsString().toUpperCase());
                            return new Response(intent, null);
                        } catch (IllegalArgumentException e) {
                            String unknownIntent = jsonResponse.get("intent").getAsString();
                            LOGGER.error("Received unknown intent from Python: {}", unknownIntent);
                            return new Response(null, "UNKNOWN_INTENT: " + unknownIntent);
                        }
                    }
                    return new Response(null, "MALFORMED_RESPONSE");
                }
            }
        } catch (IOException e) {
            LOGGER.debug("AI Server communication failure: {}", e.getMessage());
            return new Response(null, "COMMUNICATION_FAILURE: " + e.getMessage());
        }
        return new Response(null, "EMPTY_RESPONSE");
    }

    /**
     * Opaque response container to allow AIController to handle fallbacks.
     */
    public static class Response {
        public final IntentType intent;
        public final String errorMessage;

        public Response(IntentType intent, String errorMessage) {
            this.intent = intent;
            this.errorMessage = errorMessage;
        }

        public boolean isSuccess() {
            return intent != null && errorMessage == null;
        }
    }
}
