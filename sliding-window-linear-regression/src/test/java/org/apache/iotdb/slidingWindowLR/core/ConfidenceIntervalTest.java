package org.apache.iotdb.slidingWindowLR.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConfidenceIntervalTest {

    @Test
    void testPerfectLinear_narrowInterval() {
        double[] y = {0, 2, 4, 6, 8}; // y = 2x, perfect fit
        RegressionResult result = LinearRegression.compute(y);

        double[] interval = result.predictWithInterval(5, 0.95);
        // Perfect fit -> very narrow interval
        assertEquals(10.0, interval[1], 1e-9); // predicted
        assertTrue(interval[2] - interval[0] < 0.01); // narrow
    }

    @Test
    void testNoisyData_widerInterval() {
        java.util.Random rng = new java.util.Random(42);
        double[] y = new double[20];
        for (int i = 0; i < 20; i++) {
            y[i] = 2 * i + 3 + rng.nextGaussian() * 5;
        }
        RegressionResult result = LinearRegression.compute(y);

        double[] interval = result.predictWithInterval(20, 0.95);
        assertTrue(interval[0] < interval[1]);
        assertTrue(interval[1] < interval[2]);
        double width = interval[2] - interval[0];
        assertTrue(width > 0.01);
    }

    @Test
    void testTrendPredictorWithCI() {
        PredictorConfig config = PredictorConfig.builder()
                .windowSize(10)
                .horizon(1)
                .minValidPoints(3)
                .enableConfidenceInterval(true)
                .confidenceLevel(0.95)
                .build();

        TrendPredictor predictor = new TrendPredictor(config);
        long[] ts = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        double[] values = {3, 5, 7, 9, 11, 13, 15, 17, 19, 21};

        var results = predictor.processBatch(ts, values);
        TrendPredictor.PredictionResult last = results.get(9);
        assertTrue(last.hasConfidenceInterval());
        assertTrue(last.getCiLower() <= last.getPredictedValue());
        assertTrue(last.getCiUpper() >= last.getPredictedValue());
    }

    @Test
    void testTrendPredictorWithOutlierFilter() {
        PredictorConfig config = PredictorConfig.builder()
                .windowSize(10)
                .horizon(1)
                .minValidPoints(3)
                .enableOutlierFilter(true)
                .iqrMultiplier(1.5)
                .build();

        TrendPredictor predictor = new TrendPredictor(config);

        // Mostly y=2x+3, but with a spike at position 4
        long[] ts = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        double[] values = {3, 5, 7, 9, 999, 13, 15, 17, 19, 21};

        var results = predictor.processBatch(ts, values);

        // With filter, slope should still be close to 2
        TrendPredictor.PredictionResult last = results.get(9);
        assertEquals(2.0, last.getSlope(), 1.0);
    }

    @Test
    void testFilterVsNoFilterComparison() {
        // Without filter
        PredictorConfig configNoFilter = PredictorConfig.builder()
                .windowSize(10).horizon(1).minValidPoints(3).build();
        TrendPredictor noFilter = new TrendPredictor(configNoFilter);

        // With filter
        PredictorConfig configFilter = PredictorConfig.builder()
                .windowSize(10).horizon(1).minValidPoints(3)
                .enableOutlierFilter(true).build();
        TrendPredictor withFilter = new TrendPredictor(configFilter);

        long[] ts = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        double[] values = {3, 5, 7, 9, 999, 13, 15, 17, 19, 21};

        var resultsNoFilter = noFilter.processBatch(ts, values);
        var resultsWithFilter = withFilter.processBatch(ts, values);

        // Filtered slope should be closer to 2.0 than unfiltered
        double slopeNoFilter = resultsNoFilter.get(9).getSlope();
        double slopeWithFilter = resultsWithFilter.get(9).getSlope();
        assertTrue(Math.abs(slopeWithFilter - 2.0) < Math.abs(slopeNoFilter - 2.0));
    }
}
