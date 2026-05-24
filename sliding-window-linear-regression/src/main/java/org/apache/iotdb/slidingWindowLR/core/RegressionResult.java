package org.apache.iotdb.slidingWindowLR.core;

public class RegressionResult {
    private final double slope;
    private final double intercept;
    private final double rSquared;
    private final int dataPoints;
    private final double standardError;
    private final double meanX;
    private final double sumX2Centered;

    public RegressionResult(double slope, double intercept, double rSquared, int dataPoints) {
        this(slope, intercept, rSquared, dataPoints, 0, 0, 0);
    }

    public RegressionResult(double slope, double intercept, double rSquared, int dataPoints,
                            double standardError, double meanX, double sumX2Centered) {
        this.slope = slope;
        this.intercept = intercept;
        this.rSquared = rSquared;
        this.dataPoints = dataPoints;
        this.standardError = standardError;
        this.meanX = meanX;
        this.sumX2Centered = sumX2Centered;
    }

    public double getSlope() { return slope; }
    public double getIntercept() { return intercept; }
    public double getRSquared() { return rSquared; }
    public int getDataPoints() { return dataPoints; }
    public double getStandardError() { return standardError; }

    public double predict(double x) {
        return intercept + slope * x;
    }

    /**
     * Prediction interval: predicted ± margin.
     * Uses t-distribution approximation with t ≈ z for simplicity.
     * @param x the x value to predict at
     * @param confidenceLevel e.g. 0.95 for 95% confidence
     * @return [lower, predicted, upper]
     */
    public double[] predictWithInterval(double x, double confidenceLevel) {
        if (dataPoints < 3 || standardError < 1e-15 || sumX2Centered < 1e-15) {
            double pv = predict(x);
            return new double[]{pv, pv, pv};
        }
        double z = normalQuantile(confidenceLevel);
        double deviation = x - meanX;
        double se = standardError * Math.sqrt(1.0 + 1.0 / dataPoints + deviation * deviation / sumX2Centered);
        double pv = predict(x);
        double margin = z * se;
        return new double[]{pv - margin, pv, pv + margin};
    }

    /**
     * Rough t-distribution critical value approximated as normal quantile.
     * For n >= 10 this is quite accurate.
     */
    private static double normalQuantile(double confidenceLevel) {
        double p = (1 + confidenceLevel) / 2;
        return ApproxMath.inverseNormalCDF(p);
    }

    @Override
    public String toString() {
        return String.format("RegressionResult{slope=%.6f, intercept=%.6f, r²=%.6f, se=%.6f, n=%d}",
                slope, intercept, rSquared, standardError, dataPoints);
    }
}
