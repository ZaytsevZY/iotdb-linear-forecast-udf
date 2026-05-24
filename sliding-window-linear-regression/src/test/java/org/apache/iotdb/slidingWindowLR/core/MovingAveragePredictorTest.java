package org.apache.iotdb.slidingWindowLR.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MovingAveragePredictorTest {

    @Test
    void testConstantSequence() {
        MovingAveragePredictor ma = new MovingAveragePredictor(5, 1, 1);
        long[] ts = {1, 2, 3, 4, 5};
        double[] values = {10, 10, 10, 10, 10};

        var results = ma.processBatch(ts, values);
        for (var r : results) {
            if (r != null) assertEquals(10.0, r.getPredictedValue(), 1e-9);
        }
    }

    @Test
    void testLinearSequence_predictionLags() {
        MovingAveragePredictor ma = new MovingAveragePredictor(5, 1, 5);
        long[] ts = {1, 2, 3, 4, 5};
        double[] values = {2, 4, 6, 8, 10}; // mean = 6

        var results = ma.processBatch(ts, values);
        assertEquals(6.0, results.get(4).getPredictedValue(), 1e-9);
    }

    @Test
    void testNullBeforeMinPoints() {
        MovingAveragePredictor ma = new MovingAveragePredictor(5, 1, 3);
        assertNull(ma.addPoint(1, 10.0));
        assertNull(ma.addPoint(2, 20.0));
        assertNotNull(ma.addPoint(3, 30.0));
    }

    @Test
    void testReset() {
        MovingAveragePredictor ma = new MovingAveragePredictor(3, 1, 2);
        ma.addPoint(1, 10.0);
        ma.reset();
        assertNull(ma.addPoint(2, 20.0));
        assertNotNull(ma.addPoint(3, 30.0));
    }
}
