package org.apache.iotdb.slidingWindowLR.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Sliding window moving average predictor for comparison experiments.
 * Predicts future values by averaging the current window.
 */
public class MovingAveragePredictor {

    private final int windowSize;
    private final int horizon;
    private final int minValidPoints;
    private final SlidingWindow window;

    public MovingAveragePredictor(int windowSize, int horizon, int minValidPoints) {
        if (windowSize <= 0) throw new IllegalArgumentException("windowSize must be positive");
        if (horizon <= 0) throw new IllegalArgumentException("horizon must be positive");
        if (minValidPoints < 1) throw new IllegalArgumentException("minValidPoints must be at least 1");
        this.windowSize = windowSize;
        this.horizon = horizon;
        this.minValidPoints = minValidPoints;
        this.window = new SlidingWindow(windowSize);
    }

    public int getWindowSize() { return windowSize; }
    public int getHorizon() { return horizon; }

    public PredictionResult addPoint(long timestamp, double value) {
        window.add(timestamp, value);

        if (window.size() < minValidPoints) {
            return null;
        }

        double[] values = window.getValues();
        double sum = 0;
        for (double v : values) sum += v;
        double mean = sum / values.length;

        return new PredictionResult(timestamp, mean, values.length);
    }

    public List<PredictionResult> processBatch(long[] timestamps, double[] values) {
        if (timestamps == null || values == null || timestamps.length != values.length) {
            throw new IllegalArgumentException("timestamps and values must be non-null and of equal length");
        }
        List<PredictionResult> results = new ArrayList<>(timestamps.length);
        for (int i = 0; i < timestamps.length; i++) {
            results.add(addPoint(timestamps[i], values[i]));
        }
        return results;
    }

    public void reset() { window.clear(); }

    public static class PredictionResult {
        private final long timestamp;
        private final double predictedValue;
        private final int windowPoints;

        public PredictionResult(long timestamp, double predictedValue, int windowPoints) {
            this.timestamp = timestamp;
            this.predictedValue = predictedValue;
            this.windowPoints = windowPoints;
        }

        public long getTimestamp() { return timestamp; }
        public double getPredictedValue() { return predictedValue; }
        public int getWindowPoints() { return windowPoints; }

        @Override
        public String toString() {
            return String.format("MA{ts=%d, predicted=%.4f, n=%d}", timestamp, predictedValue, windowPoints);
        }
    }
}
