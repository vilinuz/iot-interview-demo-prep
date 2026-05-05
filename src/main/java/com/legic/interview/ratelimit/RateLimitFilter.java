package com.legic.interview.ratelimit;

import com.sun.net.httpserver.*;
import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class RateLimitFilter extends Filter {

    private final TokenBucket globalBucket =
            new TokenBucket(100_000, 100_000); // 100k cap, 100k/s refill

    private final ConcurrentHashMap<String, SlidingWindowLimiter> perConn =
            new ConcurrentHashMap<>();

    @Override
    public void doFilter(HttpExchange ex, Filter.Chain chain) throws java.io.IOException {
        if (!globalBucket.tryConsume(1)) {
            send(ex, 429, "Global rate limit exceeded");
            return;
        }
        String connId = ex.getRequestHeaders().getFirst("X-Connection-Id");
        if (connId != null) {
            SlidingWindowLimiter limiter = perConn.computeIfAbsent(connId,
                    id -> new SlidingWindowLimiter(100, 1_000)); // 100 req/s per conn
            if (!limiter.tryAcquire()) {
                send(ex, 429, "Per-connection rate limit exceeded");
                return;
            }
        }
        chain.doFilter(ex);
    }

    @Override public String description() { return "RateLimitFilter"; }

    /** Call periodically to evict state for closed connections */
    public void evict(Set<String> activeConnectionIds) {
        perConn.keySet().retainAll(activeConnectionIds);
    }

    private void send(HttpExchange ex, int status, String msg) throws java.io.IOException {
        byte[] body = msg.getBytes();
        ex.getResponseHeaders().set("Retry-After", "1");
        ex.sendResponseHeaders(status, body.length);
        ex.getResponseBody().write(body);
    }
}

// ── Wiring ────────────────────────────────────────────────────────────────────
