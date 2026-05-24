package org.apache.iotdb.slidingWindowLR.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

class TrendPredictorTest {

    @Test
    void testPerfectLinearSequence() {
        PredictorConfig config = PredictorConfig.builder()
                .windowSize(10)
                .horizon(1)
                .minValidPoints(3)
                .outputMode(OutputMode.PREDICT)
                .build();

        TrendPredictor predictor = new TrendPredictor(config);

        // y = 2x + 3: values at x=0..9 are 3,5,7,9,11,13,15,17,19,21
        long[] timestamps = {1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000, 9000, 10000};
        double[] values = {3, 5, 7, 9, 11, 13, 15, 17, 19, 21};

        List<TrendPredictor.PredictionResult> results = predictor.processBatch(timestamps, values);

        // First 2 results should be null (need minValidPoints=3)
        assertNull(results.get(0));
        assertNull(results.get(1));

        // From the 3rd point onward, predictions should be available
        // At x=2 (3rd point), with window [3,5,7], regression gives slope=2, intercept=3
        // predict(x=3) = 3 + 2*3 = 9
        assertNotNull(results.get(2));
        assertEquals(9.0, results.get(2).getPredictedValue(), 1e-6);

        // Last point: window [3..21], predict(x=10) = 3 + 2*10 = 23
        assertNotNull(results.get(9));
        assertEquals(23.0, results.get(9).getPredictedValue(), 1e-6);
    }

    @Test
    void testNegativeTrend() {
        PredictorConfig config = PredictorConfig.builder()
                .windowSize(5)
                .horizon(1)
                .minValidPoints(2)
                .outputMode(OutputMode.PREDICT)
                .build();

        TrendPredictor predictor = new TrendPredictor(config);

        // y = -x + 10: 10, 9, 8, 7, 6
        long[] timestamps = {1, 2, 3, 4, 5};
        double[] values = {10, 9, 8, 7, 6};

        List<TrendPredictor.PredictionResult> results = predictor.processBatch(timestamps, values);

        // Slope should be negative
        assertTrue(results.get(4).getSlope() < 0);
        assertEquals(-1.0, results.get(4).getSlope(), 1e-6);
    }

    @Test
    void testReset() {
        PredictorConfig config = PredictorConfig.builder()
                .windowSize(5)
                .horizon(1)
                .minValidPoints(2)
                .build();

        TrendPredictor predictor = new TrendPredictor(config);
        predictor.addPoint(1, 10.0);
        predictor.addPoint(2, 20.0);

        predictor.reset();

        // After reset, first point should not produce a result
        assertNull(predictor.addPoint(3, 30.0));
        assertNotNull(predictor.addPoint(4, 40.0));
    }

    @Test
    void testNoisyData() {
        PredictorConfig config = PredictorConfig.builder()
                .windowSize(20)
                .horizon(1)
                .minValidPoints(5)
                .outputMode(OutputMode.PREDICT)
                .build();

        TrendPredictor predictor = new TrendPredictor(config);

        // Generate y = 2x + 3 + noise
        long[] timestamps = new long[50];
        double[] values = new double[50];
        java.util.Random rng = new java.util.Random(42);
        for (int i = 0; i < 50; i++) {
            timestamps[i] = i * 1000L;
            values[i] = 2 * i + 3 + (rng.nextGaussian() * 0.5);
        }

        List<TrendPredictor.PredictionResult> results = predictor.processBatch(timestamps, values);

        // Last result: slope should be close to 2
        TrendPredictor.PredictionResult last = results.get(49);
        assertEquals(2.0, last.getSlope(), 0.5);
        // Window covers i=30..49, so intercept at x=0 maps to i=30, approximately 63
        assertTrue(Math.abs(last.getIntercept() - 63.0) < 5.0);
    }

    @Test
    void testMultiStepHorizon() {
        PredictorConfig config = PredictorConfig.builder()
                .windowSize(5)
                .horizon(3)
                .minValidPoints(3)
                .outputMode(OutputMode.PREDICT)
                .build();

        TrendPredictor predictor = new TrendPredictor(config);

        // y = x + 1: 1, 2, 3, 4, 5
        long[] timestamps = {1, 2, 3, 4, 5};
        double[] values = {1, 2, 3, 4, 5};

        List<TrendPredictor.PredictionResult> results = predictor.processBatch(timestamps, values);

        // Last point: window [1,2,3,4,5], slope=1, intercept=1
        // predict(x=4+3=7) = 1 + 1*7 = 8
        assertEquals(8.0, results.get(4).getPredictedValue(), 1e-6);
    }

    @Test
    void testConfigValidation() {
        assertThrows(IllegalArgumentException.class, () ->
                PredictorConfig.builder().windowSize(0).build());
        assertThrows(IllegalArgumentException.class, () ->
                PredictorConfig.builder().minValidPoints(1).build());
        assertThrows(IllegalArgumentException.class, () ->
                PredictorConfig.builder().windowSize(5).minValidPoints(10).build());
    }

    @Test
    void testIncrementalStats() {
        LinearRegression.IncrementalStats stats = new LinearRegression.IncrementalStats(5);

        // Add y = 2x + 3 for x = 0,1,2,3,4
        for (int i = 0; i < 5; i++) {
            stats.add(i, 2 * i + 3);
        }

        RegressionResult result = stats.computeRegression();
        assertEquals(2.0, result.getSlope(), 1e-9);
        assertEquals(3.0, result.getIntercept(), 1e-9);
    }

    @Test
    void testIncrementalStatsSliding() {
        LinearRegression.IncrementalStats stats = new LinearRegression.IncrementalStats(3);

        // y = x + 5: x=0->5, x=1->6, x=2->7
        stats.add(0, 5);
        stats.add(1, 6);
        stats.add(2, 7);

        RegressionResult r1 = stats.computeRegression();
        assertEquals(1.0, r1.getSlope(), 1e-9);
        assertEquals(5.0, r1.getIntercept(), 1e-9);

        // Slide: x=3->8 replaces x=0->5
        stats.add(3, 8);

        // Window now has (1,6), (2,7), (3,8) -> still y = x + 5
        RegressionResult r2 = stats.computeRegression();
        assertEquals(1.0, r2.getSlope(), 1e-9);
        assertEquals(5.0, r2.getIntercept(), 1e-9);
    }
}
