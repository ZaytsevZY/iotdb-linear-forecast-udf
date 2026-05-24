package org.apache.iotdb.slidingWindowLR.core;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Ordinary Least Squares (OLS) linear regression calculator.
 * Supports both direct computation and incremental (O(1)) updates via running statistics.
 */
public class LinearRegression {

    /**
     * Direct OLS regression: y = a + b*x, where x = 0, 1, ..., n-1.
     */
    public static RegressionResult compute(double[] y) {
        if (y == null || y.length < 2) {
            throw new IllegalArgumentException("need at least 2 data points, got: " + (y == null ? "null" : y.length));
        }

        int n = y.length;
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;

        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += y[i];
            sumXY += i * y[i];
            sumX2 += (double) i * i;
        }

        double meanX = sumX / n;
        double meanY = sumY / n;

        double denominator = n * sumX2 - sumX * sumX;
        if (Math.abs(denominator) < 1e-15) {
            return new RegressionResult(0.0, meanY, 0.0, n);
        }

        double slope = (n * sumXY - sumX * sumY) / denominator;
        double intercept = meanY - slope * meanX;

        double[] rAndSe = computeRSquaredAndSe(y, slope, intercept, meanX, n);
        double sumX2Centered = n * sumX2 - sumX * sumX; // = n * Σ(x_i - meanX)²

        return new RegressionResult(slope, intercept, rAndSe[0], n,
                rAndSe[1], meanX, sumX2Centered / n);
    }

    /**
     * OLS regression with arbitrary x values (for real-time-interval mode).
     */
    public static RegressionResult compute(long[] xTimestamps, double[] yValues) {
        if (xTimestamps == null || yValues == null || xTimestamps.length != yValues.length) {
            throw new IllegalArgumentException("x and y arrays must be non-null and of equal length");
        }
        if (xTimestamps.length < 2) {
            throw new IllegalArgumentException("need at least 2 data points");
        }

        int n = xTimestamps.length;
        long baseTimestamp = xTimestamps[0];
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;

        for (int i = 0; i < n; i++) {
            double x = (xTimestamps[i] - baseTimestamp) / 1000.0;
            sumX += x;
            sumY += yValues[i];
            sumXY += x * yValues[i];
            sumX2 += x * x;
        }

        double meanX = sumX / n;
        double meanY = sumY / n;

        double denominator = n * sumX2 - sumX * sumX;
        if (Math.abs(denominator) < 1e-15) {
            return new RegressionResult(0.0, meanY, 0.0, n);
        }

        double slope = (n * sumXY - sumX * sumY) / denominator;
        double intercept = meanY - slope * meanX;

        double rSquared = computeRSquared(xTimestamps, yValues, slope, intercept, baseTimestamp);

        return new RegressionResult(slope, intercept, rSquared, n);
    }

    private static double[] computeRSquaredAndSe(double[] y, double slope, double intercept,
                                                    double meanX, int n) {
        double meanY = 0;
        for (double v : y) meanY += v;
        meanY /= n;

        double ssTot = 0, ssRes = 0;
        for (int i = 0; i < n; i++) {
            double predicted = intercept + slope * i;
            ssRes += (y[i] - predicted) * (y[i] - predicted);
            ssTot += (y[i] - meanY) * (y[i] - meanY);
        }

        double rSquared = (ssTot < 1e-15) ? 1.0 : 1.0 - ssRes / ssTot;
        double standardError = (n > 2) ? Math.sqrt(ssRes / (n - 2)) : 0;
        return new double[]{rSquared, standardError};
    }

    private static double computeRSquared(long[] xTimestamps, double[] yValues,
                                           double slope, double intercept, long baseTimestamp) {
        double meanY = 0;
        for (double v : yValues) meanY += v;
        meanY /= yValues.length;

        double ssTot = 0, ssRes = 0;
        for (int i = 0; i < yValues.length; i++) {
            double x = (xTimestamps[i] - baseTimestamp) / 1000.0;
            double predicted = intercept + slope * x;
            ssRes += (yValues[i] - predicted) * (yValues[i] - predicted);
            ssTot += (yValues[i] - meanY) * (yValues[i] - meanY);
        }

        if (ssTot < 1e-15) return 1.0;
        return 1.0 - ssRes / ssTot;
    }

    /**
     * Incremental statistics tracker for O(1) sliding window updates.
     * Maintains running sums: sum(x), sum(y), sum(x*y), sum(x^2).
     */
    public static class IncrementalStats {
        private final ArrayDeque<Entry> window;
        private final int capacity;
        private int count;
        private double sumX;
        private double sumY;
        private double sumXY;
        private double sumX2;

        private static class Entry {
            final double x;
            final double y;

            Entry(double x, double y) {
                this.x = x;
                this.y = y;
            }
        }

        public IncrementalStats(int capacity) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("capacity must be positive");
            }
            this.capacity = capacity;
            this.window = new ArrayDeque<>(capacity);
            this.count = 0;
        }

        /**
         * Add a point (x, y) and evict the oldest if at capacity.
         */
        public void add(double x, double y) {
            if (window.size() >= capacity) {
                Entry oldest = window.pollFirst();
                sumX -= oldest.x;
                sumY -= oldest.y;
                sumXY -= oldest.x * oldest.y;
                sumX2 -= oldest.x * oldest.x;
                count--;
            }
            window.addLast(new Entry(x, y));
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
            count++;
        }

        /**
         * Compute regression from current running statistics.
         * x values should be sequential integers (0, 1, 2, ...).
         */
        public RegressionResult computeRegression() {
            if (count < 2) {
                throw new IllegalArgumentException("need at least 2 points, got: " + count);
            }

            double n = count;
            double denominator = n * sumX2 - sumX * sumX;
            if (Math.abs(denominator) < 1e-15) {
                double meanY = sumY / n;
                return new RegressionResult(0.0, meanY, 0.0, count);
            }

            double slope = (n * sumXY - sumX * sumY) / denominator;
            double meanX = sumX / n;
            double meanY = sumY / n;
            double intercept = meanY - slope * meanX;

            double ssTot = 0, ssRes = 0;
            for (Entry e : window) {
                double predicted = intercept + slope * e.x;
                ssRes += (e.y - predicted) * (e.y - predicted);
                ssTot += (e.y - meanY) * (e.y - meanY);
            }
            double rSquared = (ssTot < 1e-15) ? 1.0 : 1.0 - ssRes / ssTot;

            return new RegressionResult(slope, intercept, rSquared, count);
        }

        public int getCount() {
            return count;
        }

        public void clear() {
            window.clear();
            count = 0;
            sumX = sumY = sumXY = sumX2 = 0;
        }
    }
}
