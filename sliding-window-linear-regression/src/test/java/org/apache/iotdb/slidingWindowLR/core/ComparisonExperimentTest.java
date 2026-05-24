package org.apache.iotdb.slidingWindowLR.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comparison experiments: LR vs Moving Average, different window sizes.
 */
class ComparisonExperimentTest {

    @Test
    void testLinearRegression_vs_MovingAverage_linearTrend() {
        // y = 2x + 3 + slight noise
        Random rng = new Random(42);
        int n = 50;
        long[] ts = new long[n];
        double[] values = new double[n];
        for (int i = 0; i < n; i++) {
            ts[i] = (i + 1) * 1000L;
            values[i] = 2 * i + 3 + rng.nextGaussian() * 0.3;
        }

        // Linear regression
        TrendPredictor lr = new TrendPredictor(PredictorConfig.builder()
                .windowSize(10).horizon(1).minValidPoints(3).build());
        List<TrendPredictor.PredictionResult> lrResults = lr.processBatch(ts, values);

        // Moving average
        MovingAveragePredictor ma = new MovingAveragePredictor(10, 1, 3);
        List<MovingAveragePredictor.PredictionResult> maResults = ma.processBatch(ts, values);

        // Compute MAE for last 20 predictions
        double lrMae = 0, maMae = 0;
        int count = 0;
        for (int i = 30; i < n; i++) {
            double actual = 2 * (i + 1) + 3; // true value at next step
            if (lrResults.get(i) != null) lrMae += Math.abs(lrResults.get(i).getPredictedValue() - actual);
            if (maResults.get(i) != null) maMae += Math.abs(maResults.get(i).getPredictedValue() - actual);
            count++;
        }
        lrMae /= count;
        maMae /= count;

        // LR should have lower MAE than MA for linear trend (MA always lags)
        assertTrue(lrMae < maMae,
                String.format("LR MAE (%.4f) should be < MA MAE (%.4f) for linear trend", lrMae, maMae));
    }

    @Test
    void testDifferentWindowSizes_noiseData() {
        Random rng = new Random(42);
        int n = 100;
        long[] ts = new long[n];
        double[] values = new double[n];
        for (int i = 0; i < n; i++) {
            ts[i] = (i + 1) * 1000L;
            // Trend with change: up then flat
            if (i < 50) {
                values[i] = 2 * i + 3 + rng.nextGaussian() * 3;
            } else {
                values[i] = 103 + rng.nextGaussian() * 3; // flat
            }
        }

        int[] windowSizes = {5, 10, 20, 50};
        double[] maes = new double[4];

        for (int w = 0; w < windowSizes.length; w++) {
            TrendPredictor predictor = new TrendPredictor(PredictorConfig.builder()
                    .windowSize(windowSizes[w]).horizon(1).minValidPoints(3).build());
            List<TrendPredictor.PredictionResult> results = predictor.processBatch(ts, values);

            double mae = 0;
            int count = 0;
            for (int i = windowSizes[w]; i < n; i++) {
                if (results.get(i) != null) {
                    double actual = values[i]; // compare with current actual
                    mae += Math.abs(results.get(i).getPredictedValue() - actual);
                    count++;
                }
            }
            maes[w] = mae / count;
        }

        // Just verify they produce different results (no assertion on which is best,
        // since that depends on the data pattern)
        boolean allSame = true;
        for (int w = 1; w < maes.length; w++) {
            if (Math.abs(maes[w] - maes[0]) > 0.01) allSame = false;
        }
        assertFalse(allSame, "Different window sizes should produce different MAEs");
    }

    @Test
    void testWindowComparison_printSummary() {
        Random rng = new Random(42);
        int n = 100;
        long[] ts = new long[n];
        double[] values = new double[n];
        for (int i = 0; i < n; i++) {
            ts[i] = (i + 1) * 1000L;
            values[i] = 1.5 * i + 5 + rng.nextGaussian() * 2;
        }

        int[] windowSizes = {5, 10, 20, 50};
        System.out.println("=== Window Size Comparison (y = 1.5x + 5 + noise) ===");
        System.out.printf("%-12s %-12s %-12s %-12s%n", "Window", "MAE", "Slope(avg)", "R²(avg)");
        System.out.println("-".repeat(48));

        for (int ws : windowSizes) {
            TrendPredictor p = new TrendPredictor(PredictorConfig.builder()
                    .windowSize(ws).horizon(1).minValidPoints(3).build());
            List<TrendPredictor.PredictionResult> results = p.processBatch(ts, values);

            double mae = 0, slopeSum = 0, r2Sum = 0;
            int count = 0;
            for (int i = ws; i < n; i++) {
                if (results.get(i) != null) {
                    mae += Math.abs(results.get(i).getPredictedValue() - values[i]);
                    slopeSum += results.get(i).getSlope();
                    r2Sum += results.get(i).getRSquared();
                    count++;
                }
            }
            System.out.printf("%-12d %-12.4f %-12.4f %-12.6f%n",
                    ws, mae / count, slopeSum / count, r2Sum / count);
        }
    }
}
