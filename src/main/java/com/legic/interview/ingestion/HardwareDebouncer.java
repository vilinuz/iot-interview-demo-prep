package com.legic.interview.ingestion;

import reactor.core.publisher.Flux;
import java.time.Duration;

public class HardwareDebouncer {
    
    // Approach 1: Project Reactor (Reactive)
    public Flux<Signal> debounce(Flux<Signal> signals) {
        return signals
            .sample(Duration.ofMillis(50))
            .filter(Signal::isValid);
    }

    // Approach 2: Java 21 Virtual Threads
    private long lastSignalTime = 0;

    public void handleSignal(Signal signal) {
        Thread.startVirtualThread(() -> {
            if (isLastSignalWithinThreshold()) return;
            process(signal);
        });
    }

    private synchronized boolean isLastSignalWithinThreshold() {
        long now = System.currentTimeMillis();
        if (now - lastSignalTime < 50) return true;
        lastSignalTime = now;
        return false;
    }

    private void process(Signal signal) {
        // stub
    }
}
