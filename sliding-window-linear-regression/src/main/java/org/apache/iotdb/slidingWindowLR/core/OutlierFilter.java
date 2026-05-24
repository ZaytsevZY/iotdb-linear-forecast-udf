package org.apache.iotdb.slidingWindowLR.core;

import java.util.Arrays;

/**
 * IQR-based outlier filter. Removes values outside [Q1 - k*IQR, Q3 + k*IQR].
 */
public class OutlierFilter {

    private final double iqrMultiplier;

    public OutlierFilter() {
        this(1.5);
    }

    public OutlierFilter(double iqrMultiplier) {
        this.iqrMultiplier = iqrMultiplier;
    }

    /**
     * Filter outliers from y array. Returns a new array with outliers removed,
     * preserving order of valid points. Returns the original array if no outliers found.
     */
    public double[] filter(double[] y) {
        if (y == null || y.length < 4) return y.clone();

        double[] sorted = y.clone();
        Arrays.sort(sorted);

        double q1 = percentile(sorted, 25);
        double q3 = percentile(sorted, 75);
        double iqr = q3 - q1;
        double lower = q1 - iqrMultiplier * iqr;
        double upper = q3 + iqrMultiplier * iqr;

        int validCount = 0;
        for (double v : y) {
            if (v >= lower && v <= upper) validCount++;
        }

        if (validCount == y.length) return y.clone();

        double[] filtered = new double[validCount];
        int idx = 0;
        for (double v : y) {
            if (v >= lower && v <= upper) filtered[idx++] = v;
        }
        return filtered;
    }

    private static double percentile(double[] sorted, double p) {
        double pos = (p / 100.0) * (sorted.length - 1);
        int lo = (int) Math.floor(pos);
        int hi = (int) Math.ceil(pos);
        if (lo == hi) return sorted[lo];
        return sorted[lo] + (pos - lo) * (sorted[hi] - sorted[lo]);
    }
}
