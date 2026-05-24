package org.apache.iotdb.slidingWindowLR.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SlidingWindowTest {

    @Test
    void testAddWithinCapacity() {
        SlidingWindow w = new SlidingWindow(5);
        w.add(1, 10.0);
        w.add(2, 20.0);
        w.add(3, 30.0);

        assertEquals(3, w.size());
        assertArrayEquals(new double[]{10.0, 20.0, 30.0}, w.getValues(), 1e-9);
    }

    @Test
    void testEviction() {
        SlidingWindow w = new SlidingWindow(3);
        w.add(1, 10.0);
        w.add(2, 20.0);
        w.add(3, 30.0);
        w.add(4, 40.0);

        assertEquals(3, w.size());
        assertArrayEquals(new double[]{20.0, 30.0, 40.0}, w.getValues(), 1e-9);
    }

    @Test
    void testClear() {
        SlidingWindow w = new SlidingWindow(5);
        w.add(1, 10.0);
        w.add(2, 20.0);
        w.clear();

        assertTrue(w.isEmpty());
        assertEquals(0, w.size());
    }

    @Test
    void testInvalidCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new SlidingWindow(0));
        assertThrows(IllegalArgumentException.class, () -> new SlidingWindow(-1));
    }

    @Test
    void testEmptyWindowValues() {
        SlidingWindow w = new SlidingWindow(5);
        assertEquals(0, w.getValues().length);
        assertEquals(0, w.getTimestamps().length);
    }
}
