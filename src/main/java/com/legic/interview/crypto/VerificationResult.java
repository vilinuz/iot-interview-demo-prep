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

public sealed interface VerificationResult
        permits VerificationResult.Valid, VerificationResult.Invalid {
    record Valid(String cardId)   implements VerificationResult {}
    record Invalid(String reason) implements VerificationResult {}
}

// ── Core verifier ─────────────────────────────────────────────────────────────
