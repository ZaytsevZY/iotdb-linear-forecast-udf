package org.apache.iotdb.slidingWindowLR.core;

/**
 * Configuration for the sliding window linear regression predictor.
 */
public class PredictorConfig {
    private final int windowSize;
    private final int horizon;
    private final int minValidPoints;
    private final OutputMode outputMode;
    private final boolean enableOutlierFilter;
    private final double iqrMultiplier;
    private final boolean enableConfidenceInterval;
    private final double confidenceLevel;

    private PredictorConfig(Builder builder) {
        this.windowSize = builder.windowSize;
        this.horizon = builder.horizon;
        this.minValidPoints = builder.minValidPoints;
        this.outputMode = builder.outputMode;
        this.enableOutlierFilter = builder.enableOutlierFilter;
        this.iqrMultiplier = builder.iqrMultiplier;
        this.enableConfidenceInterval = builder.enableConfidenceInterval;
        this.confidenceLevel = builder.confidenceLevel;
    }

    public int getWindowSize() { return windowSize; }
    public int getHorizon() { return horizon; }
    public int getMinValidPoints() { return minValidPoints; }
    public OutputMode getOutputMode() { return outputMode; }
    public boolean isEnableOutlierFilter() { return enableOutlierFilter; }
    public double getIqrMultiplier() { return iqrMultiplier; }
    public boolean isEnableConfidenceInterval() { return enableConfidenceInterval; }
    public double getConfidenceLevel() { return confidenceLevel; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private int windowSize = 10;
        private int horizon = 1;
        private int minValidPoints = 3;
        private OutputMode outputMode = OutputMode.PREDICT;
        private boolean enableOutlierFilter = false;
        private double iqrMultiplier = 1.5;
        private boolean enableConfidenceInterval = false;
        private double confidenceLevel = 0.95;

        public Builder windowSize(int windowSize) {
            if (windowSize <= 0) throw new IllegalArgumentException("windowSize must be positive");
            this.windowSize = windowSize;
            return this;
        }

        public Builder horizon(int horizon) {
            if (horizon <= 0) throw new IllegalArgumentException("horizon must be positive");
            this.horizon = horizon;
            return this;
        }

        public Builder minValidPoints(int minValidPoints) {
            if (minValidPoints < 2) throw new IllegalArgumentException("minValidPoints must be at least 2");
            this.minValidPoints = minValidPoints;
            return this;
        }

        public Builder outputMode(OutputMode outputMode) {
            this.outputMode = outputMode;
            return this;
        }

        public Builder enableOutlierFilter(boolean enable) {
            this.enableOutlierFilter = enable;
            return this;
        }

        public Builder iqrMultiplier(double multiplier) {
            if (multiplier <= 0) throw new IllegalArgumentException("iqrMultiplier must be positive");
            this.iqrMultiplier = multiplier;
            return this;
        }

        public Builder enableConfidenceInterval(boolean enable) {
            this.enableConfidenceInterval = enable;
            return this;
        }

        public Builder confidenceLevel(double level) {
            if (level <= 0 || level >= 1) throw new IllegalArgumentException("confidenceLevel must be in (0, 1)");
            this.confidenceLevel = level;
            return this;
        }

        public PredictorConfig build() {
            if (minValidPoints > windowSize) {
                throw new IllegalArgumentException(
                        "minValidPoints (" + minValidPoints + ") cannot exceed windowSize (" + windowSize + ")");
            }
            return new PredictorConfig(this);
        }
    }
}
