package com.legic.interview.ratelimit;

import com.sun.net.httpserver.*;
import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class SlidingWindowLimiter {

    private final int             maxRequests;
    private final long            windowNs;
    private final AtomicLongArray ring;   // ring buffer of event timestamps
    private final AtomicLong      head = new AtomicLong(0);

    public SlidingWindowLimiter(int maxRequests, long windowMs) {
        this.maxRequests = maxRequests;
        this.windowNs    = windowMs * 1_000_000L;
        this.ring        = new AtomicLongArray(maxRequests);
    }

    /**
     * Returns true if the request is within the rate limit.
     * Claim a slot, overwrite its old timestamp, and check if
     * the evicted timestamp was still within the window.
     */
    public boolean tryAcquire() {
        long now    = System.nanoTime();
        long cutoff = now - windowNs;
        long slot   = head.getAndIncrement() % maxRequests;
        long evicted = ring.getAndSet((int) slot, now);
        // Evicted timestamp within window → we exceeded maxRequests in windowMs
        return evicted < cutoff;
    }
}

// ── HTTP Filter (middleware) ──────────────────────────────────────────────────
