package com.legic.interview.concurrency;

import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.List;
import java.util.concurrent.*;

/**
 * KEY CONCEPTS TO EXPLAIN:
 * 1. StructuredTaskScope: forked threads are CHILDREN of the enclosing block.
 *    scope.close() only returns after ALL children finish — no thread leaks.
 * 2. ShutdownOnFailure: first subtask to throw cancels all siblings.
 *    CompletableFuture.allOf() cannot do this — siblings keep running.
 * 3. ShutdownOnSuccess: first subtask to succeed cancels siblings.
 *    Useful for active-passive sensor pairs.
 * 4. ScopedValue (Java 22 standard): immutable, automatically inherited by
 *    child virtual threads. ThreadLocal is mutable and NOT inherited.
 */

public record SensorReading(String sensorId, double value, Instant timestamp) {}

