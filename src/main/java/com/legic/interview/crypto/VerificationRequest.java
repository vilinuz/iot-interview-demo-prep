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

/**
 * KEY CONCEPTS TO EXPLAIN:
 * 1. Constant-time comparison: MessageDigest.isEqual() never short-circuits.
 *    Arrays.equals() returns on first mismatch → leaks prefix length via timing.
 * 2. HKDF (RFC 5869): deterministic key derivation — no DB of per-card keys,
 *    just re-derive on demand from the master key.
 * 3. Version-scoped cache keys ("2:card-007"): old entries become unreachable
 *    after rotation without an explicit clear-all (grace period for in-flight).
 * 4. AtomicReference<MasterKeyEntry>: safe publication across virtual threads
 *    without locks on the read path.
 */
// ── Domain types (Java 22 records + sealed) ───────────────────────────────────

public record VerificationRequest(String cardId, byte[] challenge, byte[] response) {
    public VerificationRequest {                       // compact constructor = validation
        if (cardId   == null || cardId.isBlank())      throw new IllegalArgumentException("cardId required");
        if (challenge == null || challenge.length < 16) throw new IllegalArgumentException("challenge ≥ 16 bytes");
        if (response  == null || response.length  != 32) throw new IllegalArgumentException("response must be 32 bytes");
    }
}

