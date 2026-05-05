package com.legic.interview.ingestion;

import com.rabbitmq.client.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * KEY CONCEPTS TO EXPLAIN:
 * 1. Virtual threads (Java 21+): one per request, blocking I/O is fine —
 *    JVM unmounts the carrier thread while waiting, not the OS thread
 * 2. LinkedBlockingDeque bounded capacity = natural backpressure:
 *    offer() returns false immediately when full
 * 3. Dedicated flusher virtual thread: drainTo() collects up to BATCH_SIZE
 *    messages without busy-waiting
 * 4. RabbitMQ publisher confirms: waitForConfirms() blocks until broker
 *    acks — safe on a virtual thread, fatal on a platform thread pool
 */

public class SensorIngestionServer {

    private static final int BATCH_SIZE     = 200;
    private static final int QUEUE_CAPACITY = 10_000; // backpressure threshold
    private static final int FLUSH_MS       = 200;    // max latency before flush

    // Bounded blocking deque — offer() is the backpressure gate
    private final LinkedBlockingDeque<Map<String, Object>> buffer =
            new LinkedBlockingDeque<>(QUEUE_CAPACITY);

    private Channel rabbitChannel;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public void start() throws Exception {
        setupRabbitMQ();

        // Java 22 built-in HTTPS server — one virtual thread per HTTP request
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/ingest", this::handleIngest);
        server.start();

        // Dedicated flusher: also a virtual thread — blocking poll() is fine
        Thread.ofVirtual().name("batch-flusher").start(this::flushLoop);

        Runtime.getRuntime().addShutdownHook(Thread.ofVirtual().unstarted(() -> {
            running.set(false);
            server.stop(3);
        }));
    }

    private void handleIngest(com.sun.net.httpserver.HttpExchange ex) throws java.io.IOException {
        if (!ex.getRequestMethod().equals("POST")) {
            ex.sendResponseHeaders(405, -1); return;
        }

        byte[] body = ex.getRequestBody().readAllBytes();
        String json = new String(body, StandardCharsets.UTF_8);

        // Basic validation — use Jackson in production
        if (!json.contains("\"sensorId\"")) {
            byte[] msg = "Missing sensorId".getBytes();
            ex.sendResponseHeaders(400, msg.length);
            ex.getResponseBody().write(msg);
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("raw",        json);
        payload.put("ingestedAt", System.currentTimeMillis());

        // offer() is non-blocking and returns false when deque is full
        // → HTTP 503 signals the caller to slow down (backpressure contract)
        if (!buffer.offer(payload)) {
            byte[] msg = "Broker congested — retry later".getBytes();
            ex.getResponseHeaders().set("Retry-After", "1");
            ex.sendResponseHeaders(503, msg.length);
            ex.getResponseBody().write(msg);
            return;
        }
        ex.sendResponseHeaders(202, -1);
    }

    private void flushLoop() {
        List<Map<String, Object>> batch = new ArrayList<>(BATCH_SIZE);
        while (running.get() || !buffer.isEmpty()) {
            try {
                // Block up to FLUSH_MS waiting for the first element
                Map<String, Object> head = buffer.poll(FLUSH_MS, TimeUnit.MILLISECONDS);
                if (head != null) {
                    batch.add(head);
                    // Drain remaining up to BATCH_SIZE without blocking
                    buffer.drainTo(batch, BATCH_SIZE - 1);
                }
                if (!batch.isEmpty()) {
                    publishBatch(batch);
                    batch.clear();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("Publish error: " + e.getMessage());
            }
        }
    }

    private void publishBatch(List<Map<String, Object>> batch) throws Exception {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < batch.size(); i++) {
            sb.append(batch.get(i).get("raw"));
            if (i < batch.size() - 1) sb.append(",");
        }
        sb.append("]");
        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);

        // RabbitMQ channel is NOT thread-safe: synchronize or use per-thread channels
        synchronized (rabbitChannel) {
            rabbitChannel.basicPublish("sensors", "raw.data",
                    MessageProperties.PERSISTENT_TEXT_PLAIN, body);
            // Publisher confirm: blocks virtual thread (not a carrier thread)
            rabbitChannel.waitForConfirms(5_000);
        }
    }

    private void setupRabbitMQ() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUri("amqp://localhost");
        Connection conn = factory.newConnection();
        rabbitChannel   = conn.createChannel();
        rabbitChannel.exchangeDeclare("sensors", "topic", true);
        rabbitChannel.confirmSelect(); // enable publisher confirms
    }

    public static void main(String[] args) throws Exception {
        new SensorIngestionServer().start();
    }
}
