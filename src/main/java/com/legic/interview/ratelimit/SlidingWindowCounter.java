package com.legic.interview.ratelimit;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.concurrent.atomic.LongAdder;

public class SlidingWindowCounter {
    private final LongAdder[] buckets = new LongAdder[60]; // 1 bucket per second
    
    public SlidingWindowCounter() {
        for (int i = 0; i < buckets.length; i++) {
            buckets[i] = new LongAdder();
        }
    }

    public void increment() {
        int secondOfMinute = LocalTime.now().getSecond();
        buckets[secondOfMinute].increment();
    }
    
    public long getSum() {
        return Arrays.stream(buckets).mapToLong(LongAdder::sum).sum();
    }
}
