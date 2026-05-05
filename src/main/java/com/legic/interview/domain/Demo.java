package com.legic.interview.domain;

import java.time.Instant;

class Demo {
    public static void main(String[] args) {
        var processor = new EventProcessor();
        SensorEvent[] events = {
            new TemperatureEvent("s-01", Instant.now(), 92.0),
            new TamperEvent("s-02", Instant.now(), TamperType.CASING_OPEN, true),
            new DoorEvent("d-03", Instant.now(), DoorState.FORCED_OPEN, "intruder"),
        };
        for (SensorEvent e : events) {
            RoutingDecision d = processor.classify(e);
            System.out.printf("[%s] → %s/%s  priority=%d  alert=%b%n",
                    e.deviceId(), d.exchange(), d.routingKey(), d.priority(), d.alert());
        }
    }
}
