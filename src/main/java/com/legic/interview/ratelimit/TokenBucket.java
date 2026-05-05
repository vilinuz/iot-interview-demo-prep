package com.legic.interview.ratelimit;

import com.sun.net.httpserver.*;
import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * KEY CONCEPTS TO EXPLAIN:
 * 1. CAS loop (compareAndSet): lock-free token consumption — retries only on
 *    contention, no OS context switch, scales linearly with core count
 * 2. Lazy refill: compute elapsed time inside tryConsume() — no background
 *    thread needed, but slightly uneven distribution at period boundaries
 * 3. Ring buffer sliding window (AtomicLongArray): O(1) per check, no sorted
 *    structure, cache-friendly sequential access
 * 4. Java HttpServer Filter: middleware chain without Vert.x handlers
 */
// ── Global token bucket ───────────────────────────────────────────────────────

public final class TokenBucket {

    private final long capacity;
    private final long ratePerSec;          // tokens/s used to compute refill
    private final AtomicLong tokens;
    private final AtomicLong lastRefillNs;

    public TokenBucket(long capacity, long ratePerSec) {
        this.capacity    = capacity;
        this.ratePerSec  = ratePerSec;
        this.tokens       = new AtomicLong(capacity);
        this.lastRefillNs = new AtomicLong(System.nanoTime());
    }

    public boolean tryConsume(int n) {
        refillLazy();
        long cur;
        do {
            cur = tokens.get();
            if (cur < n) return false;
        } while (!tokens.compareAndSet(cur, cur - n)); // retry loop on CAS miss
        return true;
    }

    /** Compute elapsed time and add tokens — CAS on lastRefillNs prevents double-refill */
    private void refillLazy() {
        long now     = System.nanoTime();
        long lastRef = lastRefillNs.get();
        long elapsed = now - lastRef;
        if (elapsed < 1_000_000L) return;               // < 1ms — skip

        long toAdd = (elapsed * ratePerSec) / 1_000_000_000L;
        if (toAdd == 0) return;

        // Only one thread wins the CAS and refills — others skip
        if (lastRefillNs.compareAndSet(lastRef, now)) {
            tokens.getAndUpdate(cur -> Math.min(capacity, cur + toAdd));
        }
    }
}

// ── Per-connection sliding window ─────────────────────────────────────────────
