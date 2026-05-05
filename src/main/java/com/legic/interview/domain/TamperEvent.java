package com.legic.interview.domain;

import java.time.Instant;

public record TamperEvent(String deviceId, Instant timestamp, TamperType type, boolean resetRequired)
        implements SensorEvent {}

