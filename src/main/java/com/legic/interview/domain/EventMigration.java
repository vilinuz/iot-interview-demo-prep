package com.legic.interview.domain;

import java.time.Instant;

class EventMigration {

    public record SensorEventV1(String type, String deviceId,
                                 long epochMs, Double value, String subType) {}

    public static SensorEvent toV2(SensorEventV1 v1) {
        Instant ts = Instant.ofEpochMilli(v1.epochMs());
        return switch (v1.type()) {
            case "TEMP"     -> new TemperatureEvent(v1.deviceId(), ts, v1.value());
            case "MOVEMENT" -> new MovementEvent(v1.deviceId(), ts, v1.value());
            case "TAMPER"   -> new TamperEvent(v1.deviceId(), ts,
                    TamperType.valueOf(v1.subType()), false);
            case "DOOR"     -> new DoorEvent(v1.deviceId(), ts,
                    DoorState.valueOf(v1.subType()), "legacy-v1");
            default -> throw new IllegalArgumentException("Unknown V1 type: " + v1.type());
        };
    }
}

// ── Demo ──────────────────────────────────────────────────────────────────────
