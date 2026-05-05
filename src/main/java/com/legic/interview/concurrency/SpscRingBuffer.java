package com.legic.interview.concurrency;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class SpscRingBuffer<E> {
    private final E[] buffer;
    private long head = 0;
    private long tail = 0;
    private final int mask;
    private static final VarHandle QH = MethodHandles.arrayElementVarHandle(Object[].class);

    @SuppressWarnings("unchecked")
    public SpscRingBuffer(int capacity) {
        this.buffer = (E[]) new Object[capacity];
        this.mask = capacity - 1;
    }

    public boolean offer(E item) {
        if (isFull()) return false;
        QH.setRelease(buffer, (int) (tail & mask), item);
        tail++;
        return true;
    }

    @SuppressWarnings("unchecked")
    public E poll() {
        if (isEmpty()) return null;
        E item = (E) QH.getAcquire(buffer, (int) (head & mask));
        head++;
        return item;
    }

    private boolean isFull() {
        return tail - head >= buffer.length;
    }

    private boolean isEmpty() {
        return tail == head;
    }
}
