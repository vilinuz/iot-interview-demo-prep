package com.legic.interview.k8s;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * KEY CONCEPTS TO EXPLAIN:
 * 1. Liveness probe: is the JVM alive? Fail → K8s RESTARTS the pod.
 *    Only set live=false in truly unrecoverable states (OOM, deadlock).
 * 2. Readiness probe: can this pod serve traffic? Fail → K8s removes
 *    pod from Service endpoints WITHOUT restarting it.
 * 3. Graceful shutdown: set ready=false first (stop new traffic),
 *    then drain in-flight requests, then stop the server.
 * 4. preStop sleep: kube-proxy takes ~2s to remove the endpoint — sleep
 *    in preStop gives it time before SIGTERM triggers the JVM hook.
 *
 * Kubernetes pod spec:
 *   livenessProbe:
 *     httpGet: { path: /health/live, port: 8080 }
 *     failureThreshold: 3
 *     periodSeconds: 10
 *   readinessProbe:
 *     httpGet: { path: /health/ready, port: 8080 }
 *     failureThreshold: 1
 *     periodSeconds: 5
 *   lifecycle:
 *     preStop:
 *       exec: { command: ["sleep", "5"] }   # drain kube-proxy endpoint table
 */

public class HealthAwareServer {

    private final AtomicBoolean ready      = new AtomicBoolean(false);
    private final AtomicBoolean live       = new AtomicBoolean(true);
    private final AtomicInteger inflight   = new AtomicInteger(0);
    private final int           maxInflight;
    private HttpServer          server;

    public HealthAwareServer(int maxInflight) {
        this.maxInflight = maxInflight;
    }

    public void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        // ── Liveness: only fails on unrecoverable state ───────────────
        server.createContext("/health/live", ex -> {
            boolean up  = live.get();
            byte[] body = (up ? "UP" : "DOWN").getBytes();
            ex.sendResponseHeaders(up ? 200 : 500, body.length);
            ex.getResponseBody().write(body);
        });

        // ── Readiness: fails during startup, shutdown, or overload ────
        server.createContext("/health/ready", ex -> {
            boolean ok   = ready.get() && inflight.get() < maxInflight;
            String  text = (ok ? "READY" : "NOT_READY") + " inflight=" + inflight.get();
            byte[]  body = text.getBytes();
            ex.sendResponseHeaders(ok ? 200 : 503, body.length);
            ex.getResponseBody().write(body);
        });

        // ── Business endpoint: track in-flight with try/finally ───────
        server.createContext("/api/data", ex -> {
            if (!ready.get()) { ex.sendResponseHeaders(503, -1); return; }

            inflight.incrementAndGet();
            try {
                try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } // simulate work
                byte[] ok = "processed".getBytes();
                ex.sendResponseHeaders(200, ok.length);
                ex.getResponseBody().write(ok);
            } finally {
                inflight.decrementAndGet();     // ALWAYS decrement — even on exception
            }
        });

        // ── Graceful shutdown hook ────────────────────────────────────
        Runtime.getRuntime().addShutdownHook(Thread.ofVirtual().unstarted(() -> {
            System.out.println("[shutdown] Draining in-flight requests...");
            ready.set(false);  // fail readiness → K8s stops routing here

            long deadline = System.currentTimeMillis() + 30_000;
            while (inflight.get() > 0 && System.currentTimeMillis() < deadline) {
                try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            }

            System.out.printf("[shutdown] Drain complete. Remaining in-flight: %d%n",
                    inflight.get());
            server.stop(0);
        }));

        server.start();

        // ── Startup warmup: mark ready after DB pool / cache warms up ─
        Executors.newSingleThreadScheduledExecutor()
                .schedule(() -> {
                    ready.set(true);
                    System.out.println("[startup] READY");
                }, 3, TimeUnit.SECONDS);

        System.out.println("[startup] Listening on :8080 (readiness pending warmup)");
    }

    public static void main(String[] args) throws Exception {
        new HealthAwareServer(10_000).start();
    }
}
