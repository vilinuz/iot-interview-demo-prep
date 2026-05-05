package com.legic.interview.domain;

import java.time.Instant;

public record MovementEvent(String deviceId, Instant timestamp, double magnitudeG)
        implements SensorEvent {}

