package com.legic.interview.domain;

import java.time.Instant;

public record DoorEvent(String deviceId, Instant timestamp, DoorState state, String triggeredBy)
        implements SensorEvent {}

