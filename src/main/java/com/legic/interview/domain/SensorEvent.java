package com.legic.interview.domain;

import java.time.Instant;

/**
 * KEY CONCEPTS TO EXPLAIN:
 * 1. Sealed interfaces: permits clause → compiler-enforced exhaustive switch.
 *    Add a new permits type without a matching case → compile error.
 * 2. Guarded patterns: case X x when x.field() > N → most specific first,
 *    order matters like CSS specificity rules.
 * 3. Record deconstruction (Java 21 standard, Java 22 refined):
 *    case DoorEvent(_, _, DoorState.FORCED_OPEN, var who) extracts components.
 * 4. Unnamed pattern _ (Java 22): suppresses "variable never used" warning
 *    for components you intentionally ignore.
 */
// ── Sealed event hierarchy ────────────────────────────────────────────────────

public sealed interface SensorEvent
        permits TemperatureEvent, MovementEvent, TamperEvent, DoorEvent {
    String  deviceId();
    Instant timestamp();
}

