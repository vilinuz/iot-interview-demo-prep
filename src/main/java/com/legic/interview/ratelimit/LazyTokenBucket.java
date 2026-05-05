package com.legic.interview.ratelimit;

import java.util.concurrent.atomic.AtomicLong;

public class LazyTokenBucket {
    private final long capacity;
    private final AtomicLong tokens;
    private volatile long lastRefillTimestamp;

    public LazyTokenBucket(long capacity) {
        this.capacity = capacity;
        this.tokens = new AtomicLong(capacity);
        this.lastRefillTimestamp = System.nanoTime();
    }

    public boolean tryConsume() {
        refill();
        long current = tokens.get();
        if (current > 0 && tokens.compareAndSet(current, current - 1)) {
            return true;
        }
        return false;
    }

    private void refill() {
        long now = System.nanoTime();
        // Logical refill based on time elapsed...
        if (now - lastRefillTimestamp > 1_000_000_000L) { // 1 second
            tokens.set(capacity);
            lastRefillTimestamp = now;
        }
    }
}
