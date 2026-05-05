package com.legic.interview.concurrency;

import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.List;
import java.util.concurrent.*;

public class SensorAggregator {

    // ScopedValue: set once per request, inherited by all forked virtual threads
    static final ScopedValue<String> CORRELATION_ID = ScopedValue.newInstance();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();

    /**
     * Fan-out: query ALL sensors, fail fast if any one fails.
     * All child virtual threads are guaranteed done when scope closes.
     */
    public List<SensorReading> readAll(List<String> sensorIds, String correlationId)
            throws Exception {

        // ScopedValue.where().call(): binds the value for the lambda + all children
        return ScopedValue.where(CORRELATION_ID, correlationId).call(() -> {
            try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.<SensorReading>allSuccessfulOrThrow())) {

                var subtasks = sensorIds.stream()
                        .map(id -> scope.fork(() -> fetchReading(id)))
                        .toList();

                return scope.join().map(StructuredTaskScope.Subtask::get).toList();
            } // scope.close(): ALL threads guaranteed finished before here
        });
    }

    /**
     * Redundant sensor pair: return FIRST successful reading, cancel the other.
     * Primary sensor fails over to backup with no extra logic needed.
     */
    public SensorReading readFirst(List<String> redundantIds, String correlationId)
            throws Exception {

        return ScopedValue.where(CORRELATION_ID, correlationId).call(() -> {
            try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.<SensorReading>anySuccessfulResultOrThrow())) {
                redundantIds.forEach(id -> scope.fork(() -> fetchReading(id)));
                return scope.join(); // throws if ALL fail
            }
        });
    }

    /**
     * Hard deadline: join config cancels remaining subtasks on timeout.
     * No external cancellation token needed — scope handles it.
     */
    public List<SensorReading> readAllWithTimeout(
            List<String> ids, Duration timeout, String correlationId) throws Exception {

        return ScopedValue.where(CORRELATION_ID, correlationId).call(() -> {
            try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.<SensorReading>allSuccessfulOrThrow(), cf -> cf.withTimeout(timeout))) {
                var subtasks = ids.stream()
                        .map(id -> scope.fork(() -> fetchReading(id)))
                        .toList();

                return scope.join().map(StructuredTaskScope.Subtask::get).toList();
            }
        });
    }

    /** Real HTTP call — blocking send() is fine: virtual thread parks, not OS thread */
    private SensorReading fetchReading(String sensorId) throws Exception {
        String corrId = CORRELATION_ID.get(); // inherited from parent scope

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://sensors.internal/" + sensorId + "/reading"))
                .header("X-Correlation-Id", corrId)
                .timeout(Duration.ofSeconds(5))
                .GET().build();

        HttpResponse<String> resp =
                httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() != 200)
            throw new RuntimeException("Sensor " + sensorId + " HTTP " + resp.statusCode());

        // Minimal parse — use Jackson in production
        double value = Double.parseDouble(
                resp.body().replaceAll(".*\"value\":(\\d+\\.?\\d*).*", "$1"));
        return new SensorReading(sensorId, value, Instant.now());
    }
}

/*
 * WHY NOT CompletableFuture.allOf()?
 *
 * CompletableFuture.allOf(futures).join()
 *
 * Problems:
 *   - When one future fails, the rest KEEP RUNNING (no automatic cancellation)
 *   - Thread lifetimes are opaque — hard to reason about in thread dumps
 *   - Exception chaining through .exceptionally() is callback hell
 *   - ScopedValue is NOT propagated across CF chains
 *   - No structured scope = no observability of child thread lifetimes
 */
