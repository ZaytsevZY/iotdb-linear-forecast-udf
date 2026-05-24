package org.apache.iotdb.slidingWindowLR.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OutlierFilterTest {

    @Test
    void testNoOutliers() {
        OutlierFilter filter = new OutlierFilter();
        double[] data = {1, 2, 3, 4, 5};
        double[] result = filter.filter(data);
        assertEquals(5, result.length);
    }

    @Test
    void testRemovesExtremeOutlier() {
        OutlierFilter filter = new OutlierFilter();
        double[] data = {1, 2, 3, 4, 100}; // 100 is extreme
        double[] result = filter.filter(data);
        assertEquals(4, result.length);
        // 100 should be removed
        for (double v : result) assertNotEquals(100.0, v, 1e-9);
    }

    @Test
    void testPreservesOrder() {
        OutlierFilter filter = new OutlierFilter();
        double[] data = {1, 2, 3, 4, 100, 6};
        double[] result = filter.filter(data);
        for (int i = 1; i < result.length; i++) {
            assertTrue(result[i] > result[i - 1]);
        }
    }

    @Test
    void testSmallArrayPassthrough() {
        OutlierFilter filter = new OutlierFilter();
        double[] data = {1, 2};
        double[] result = filter.filter(data);
        assertEquals(2, result.length);
    }

    @Test
    void testCustomMultiplier() {
        OutlierFilter filter = new OutlierFilter(2.0); // wider range, fewer removals
        double[] data = {10, 11, 12, 13, 50};
        double[] result = filter.filter(data);
        // With IQR=2, bounds = [7, 17], 50 removed
        assertEquals(4, result.length);
    }
}
