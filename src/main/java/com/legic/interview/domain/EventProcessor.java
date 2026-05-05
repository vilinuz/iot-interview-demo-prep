package com.legic.interview.domain;

import java.time.Instant;

public final class EventProcessor {

    public RoutingDecision classify(SensorEvent event) {
        // NO default branch — sealed contract makes switch exhaustive.
        // Removing a permits type or adding one without a case = compile error.
        return switch (event) {

            // Guarded patterns: most specific guard must come first
            case TemperatureEvent te when te.celsius() > 85.0 ->
                    new RoutingDecision("alarms", "temp.critical", 1, true);

            case TemperatureEvent te when te.celsius() > 60.0 ->
                    new RoutingDecision("alarms", "temp.warning",  2, false);

            case TemperatureEvent te ->
                    new RoutingDecision("data",   "temp.normal",   5, false);

            case MovementEvent me when me.magnitudeG() > 3.0 ->
                    new RoutingDecision("alarms", "movement.shock", 1, true);

            case MovementEvent me ->
                    new RoutingDecision("data",   "movement.normal", 5, false);

            case TamperEvent te when te.resetRequired() ->
                    new RoutingDecision("alarms",
                            "tamper.critical." + te.type().name().toLowerCase(), 1, true);

            case TamperEvent te ->
                    new RoutingDecision("alarms",
                            "tamper." + te.type().name().toLowerCase(), 2, true);

            // Java 22 record deconstruction: _ ignores deviceId and timestamp
            // var who binds the triggeredBy component inline — no extra variable
            case DoorEvent(_, _, DoorState state, var who) when state == DoorState.FORCED_OPEN ->
                    new RoutingDecision("alarms", "door.forced." + who, 1, true);

            case DoorEvent de ->
                    new RoutingDecision("data",
                            "door." + de.state().name().toLowerCase(), 4, false);
        };
    }
}

// ── V1 → V2 migration (anti-corruption layer) ────────────────────────────────
/**
 * V1 used a flat JSON blob with a "type" string discriminator.
 * Map at the ingestion boundary — domain logic never sees V1 types.
 */
