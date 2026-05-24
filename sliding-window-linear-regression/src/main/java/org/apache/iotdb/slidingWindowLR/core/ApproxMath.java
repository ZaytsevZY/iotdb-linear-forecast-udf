package org.apache.iotdb.slidingWindowLR.core;

/**
 * Simple math approximations to avoid external dependencies.
 */
final class ApproxMath {

    private ApproxMath() {}

    /**
     * Abramowitz and Stegun approximation of the inverse normal CDF.
     * Maximum absolute error ~ 4.5e-4. Good enough for prediction intervals.
     */
    static double inverseNormalCDF(double p) {
        if (p <= 0 || p >= 1) throw new IllegalArgumentException("p must be in (0, 1)");
        if (p < 0.5) return -inverseNormalCDF(1 - p);

        // For p >= 0.5, use the AS26.2.23 rational approximation
        double t = Math.sqrt(-2.0 * Math.log(1.0 - p));
        double c0 = 2.515517;
        double c1 = 0.802853;
        double c2 = 0.010328;
        double d1 = 1.432788;
        double d2 = 0.189269;
        double d3 = 0.001308;

        return t - (c0 + c1 * t + c2 * t * t) / (1.0 + d1 * t + d2 * t * t + d3 * t * t * t);
    }
}
