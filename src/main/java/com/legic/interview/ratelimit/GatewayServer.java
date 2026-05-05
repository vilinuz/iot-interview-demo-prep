package com.legic.interview.ratelimit;

import com.sun.net.httpserver.*;
import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

class GatewayServer {
    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        RateLimitFilter rateLimiter = new RateLimitFilter();
        var ctx = server.createContext("/api", ex -> {
            byte[] ok = "OK".getBytes();
            ex.sendResponseHeaders(200, ok.length);
            ex.getResponseBody().write(ok);
        });
        ctx.getFilters().add(rateLimiter);

        // Evict stale connection state every 60 seconds
        Executors.newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(
                        () -> rateLimiter.evict(Set.of(/* active connection IDs */)),
                        60, 60, TimeUnit.SECONDS);
        server.start();
    }
}
