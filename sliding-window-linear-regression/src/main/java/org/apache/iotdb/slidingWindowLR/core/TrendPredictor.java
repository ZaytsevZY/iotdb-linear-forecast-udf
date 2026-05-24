package org.apache.iotdb.slidingWindowLR.core;

import java.util.ArrayList;
import java.util.List;

public class TrendPredictor {

    private final PredictorConfig config;
    private final SlidingWindow window;
    private final OutlierFilter outlierFilter;

    public TrendPredictor(PredictorConfig config) {
        this.config = config;
        this.window = new SlidingWindow(config.getWindowSize());
        this.outlierFilter = config.isEnableOutlierFilter()
                ? new OutlierFilter(config.getIqrMultiplier()) : null;
    }

    public PredictorConfig getConfig() { return config; }
    public SlidingWindow getWindow() { return window; }

    /**
     * Process a single data point. Returns null if not enough data yet.
     */
    public PredictionResult addPoint(long timestamp, double value) {
        window.add(timestamp, value);

        if (window.size() < config.getMinValidPoints()) {
            return null;
        }

        double[] y = window.getValues();

        double[] regInput = y;
        if (outlierFilter != null && y.length >= 4) {
            double[] filtered = outlierFilter.filter(y);
            if (filtered.length >= config.getMinValidPoints()) {
                regInput = filtered;
            }
        }

        RegressionResult regression = LinearRegression.compute(regInput);
        double xPredict = regInput.length - 1 + config.getHorizon();
        double predictedValue = regression.predict(xPredict);

        double ciLower = Double.NaN;
        double ciUpper = Double.NaN;
        if (config.isEnableConfidenceInterval()) {
            double[] interval = regression.predictWithInterval(xPredict, config.getConfidenceLevel());
            ciLower = interval[0];
            ciUpper = interval[2];
        }

        return new PredictionResult(
                timestamp, predictedValue, regression.getSlope(),
                regression.getIntercept(), regression.getRSquared(),
                ciLower, ciUpper
        );
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
        private final double slope;
        private final double intercept;
        private final double rSquared;
        private final double ciLower;
        private final double ciUpper;

        public PredictionResult(long timestamp, double predictedValue,
                                double slope, double intercept, double rSquared,
                                double ciLower, double ciUpper) {
            this.timestamp = timestamp;
            this.predictedValue = predictedValue;
            this.slope = slope;
            this.intercept = intercept;
            this.rSquared = rSquared;
            this.ciLower = ciLower;
            this.ciUpper = ciUpper;
        }

        public long getTimestamp() { return timestamp; }
        public double getPredictedValue() { return predictedValue; }
        public double getSlope() { return slope; }
        public double getIntercept() { return intercept; }
        public double getRSquared() { return rSquared; }
        public double getCiLower() { return ciLower; }
        public double getCiUpper() { return ciUpper; }
        public boolean hasConfidenceInterval() { return !Double.isNaN(ciLower); }

        @Override
        public String toString() {
            if (hasConfidenceInterval()) {
                return String.format("PredictionResult{ts=%d, predicted=%.4f [%.4f, %.4f], slope=%.6f}",
                        timestamp, predictedValue, ciLower, ciUpper, slope);
            }
            return String.format("PredictionResult{ts=%d, predicted=%.4f, slope=%.6f, r²=%.6f}",
                    timestamp, predictedValue, slope, rSquared);
        }
    }
}
