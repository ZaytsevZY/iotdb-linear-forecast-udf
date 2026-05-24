package org.apache.iotdb.slidingWindowLR.core;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A fixed-size sliding window that stores timestamp-value pairs.
 * When the window exceeds its capacity, the oldest entry is evicted.
 */
public class SlidingWindow {
    private final int capacity;
    private final Deque<Long> timestamps;
    private final Deque<Double> values;

    public SlidingWindow(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive, got: " + capacity);
        }
        this.capacity = capacity;
        this.timestamps = new ArrayDeque<>(capacity);
        this.values = new ArrayDeque<>(capacity);
    }

    public void add(long timestamp, double value) {
        timestamps.addLast(timestamp);
        values.addLast(value);
        if (timestamps.size() > capacity) {
            timestamps.pollFirst();
            values.pollFirst();
        }
    }

    public int size() {
        return timestamps.size();
    }

    public int getCapacity() {
        return capacity;
    }

    public boolean isEmpty() {
        return timestamps.isEmpty();
    }

    public long[] getTimestamps() {
        long[] result = new long[timestamps.size()];
        int i = 0;
        for (Long ts : timestamps) {
            result[i++] = ts;
        }
        return result;
    }

    public double[] getValues() {
        double[] result = new double[values.size()];
        int i = 0;
        for (Double v : values) {
            result[i++] = v;
        }
        return result;
    }

    public void clear() {
        timestamps.clear();
        values.clear();
    }
}
