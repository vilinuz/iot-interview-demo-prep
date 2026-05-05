package com.legic.interview.protocol;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;

public class ZeroAllocationParser {
    private static final ValueLayout.OfInt JAVA_INT_BE = ValueLayout.JAVA_INT.withOrder(ByteOrder.BIG_ENDIAN);
    
    public void parse(MemorySegment segment) {
        // Read header without allocating a byte[] or String
        int version = segment.get(JAVA_INT_BE, 0);
        int payloadSize = segment.get(JAVA_INT_BE, 4);
        
        // Use MemorySegment.asSlice for zero-copy sub-views
        MemorySegment payload = segment.asSlice(8, payloadSize);
        processPayload(payload);
    }

    private void processPayload(MemorySegment payload) {
        // stub
    }
}
