package com.legic.interview.domain;

import java.time.Instant;

public record TemperatureEvent(String deviceId, Instant timestamp, double celsius)
        implements SensorEvent {
    public TemperatureEvent {    // compact constructor for validation
        if (celsius < -273.15) throw new IllegalArgumentException("Below absolute zero");
    }
}

