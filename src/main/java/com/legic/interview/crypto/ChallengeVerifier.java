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

public final class ChallengeVerifier {

    private final AtomicReference<MasterKeyEntry> masterKey;
    private final ConcurrentHashMap<String, byte[]> keyCache = new ConcurrentHashMap<>(4096);

    public record MasterKeyEntry(int version, byte[] keyMaterial) {}

    public ChallengeVerifier(byte[] initialKey) {
        this.masterKey = new AtomicReference<>(new MasterKeyEntry(1, initialKey));
    }

    public VerificationResult verify(VerificationRequest req) {
        byte[] key = deriveCardKey(req.cardId());
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] expected = mac.doFinal(req.challenge());
            // CRITICAL: constant-time — timing cannot reveal how many bytes matched
            return MessageDigest.isEqual(expected, req.response())
                    ? new VerificationResult.Valid(req.cardId())
                    : new VerificationResult.Invalid("HMAC mismatch");
        } catch (Exception e) {
            return new VerificationResult.Invalid("Crypto error");
        }
    }

    private byte[] deriveCardKey(String cardId) {
        MasterKeyEntry master = masterKey.get();
        // Version prefix in cache key: old keys auto-expire on rotation
        String cacheKey = master.version() + ":" + cardId;
        return keyCache.computeIfAbsent(cacheKey,
                k -> hkdfExpand(hkdfExtract(master.keyMaterial()), cardId));
    }

    // HKDF-Extract: PRK = HMAC-SHA256(salt=masterKey, IKM=domain-separator)
    private byte[] hkdfExtract(byte[] masterKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(masterKey, "HmacSHA256"));
            return mac.doFinal("legic-card-kdf-v1".getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    // HKDF-Expand: OKM = HMAC(PRK, info || 0x01)
    private byte[] hkdfExpand(byte[] prk, String info) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(prk, "HmacSHA256"));
            mac.update(info.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) 0x01);
            return mac.doFinal();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    /** Key rotation: new version → stale cache entries evicted lazily */
    public void rotateMasterKey(byte[] newKey, int newVersion) {
        masterKey.set(new MasterKeyEntry(newVersion, newKey));
        int prev = newVersion - 1;
        // Remove only previous version — older than that already gone
        keyCache.keySet().removeIf(k -> k.startsWith(prev + ":"));
    }
}

// ── HTTP handler ──────────────────────────────────────────────────────────────
