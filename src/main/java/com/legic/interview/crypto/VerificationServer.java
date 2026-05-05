package com.legic.interview.crypto;

import com.sun.net.httpserver.HttpServer;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public class VerificationServer {

    private final ChallengeVerifier verifier;
    private final HexFormat hex = HexFormat.of();

    public VerificationServer(ChallengeVerifier verifier) {
        this.verifier = verifier;
    }

    public void start() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        server.createContext("/verify", ex -> {
            if (!ex.getRequestMethod().equals("POST")) {
                ex.sendResponseHeaders(405, -1); return;
            }
            try {
                String body = new String(ex.getRequestBody().readAllBytes());
                VerificationRequest req = parseRequest(body);

                // Java 22 exhaustive switch on sealed type — no default branch
                String responseJson = switch (verifier.verify(req)) {
                    case VerificationResult.Valid   v ->
                            "{\"status\":\"ok\",\"cardId\":\"" + v.cardId() + "\"}";
                    case VerificationResult.Invalid i ->
                            "{\"status\":\"denied\",\"reason\":\"" + i.reason() + "\"}";
                };
                int status = responseJson.contains("\"ok\"") ? 200 : 401;
                byte[] bytes = responseJson.getBytes();
                ex.getResponseHeaders().set("Content-Type", "application/json");
                ex.sendResponseHeaders(status, bytes.length);
                ex.getResponseBody().write(bytes);

            } catch (IllegalArgumentException e) {
                byte[] msg = e.getMessage().getBytes();
                ex.sendResponseHeaders(400, msg.length);
                ex.getResponseBody().write(msg);
            }
        });
        server.start();
    }

    private VerificationRequest parseRequest(String json) {
        return new VerificationRequest(
                extract(json, "cardId"),
                hex.parseHex(extract(json, "challenge")),
                hex.parseHex(extract(json, "response")));
    }

    private String extract(String json, String field) {
        int s = json.indexOf("\"" + field + "\":\"") + field.length() + 4;
        if (s < field.length() + 4) throw new IllegalArgumentException("Missing: " + field);
        int e = json.indexOf("\"", s);
        return json.substring(s, e);
    }
}
