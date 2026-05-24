package org.apache.iotdb.slidingWindowLR.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LinearRegressionTest {

    @Test
    void testPerfectLinear_y_equals_2x_plus_3() {
        double[] y = {3.0, 5.0, 7.0, 9.0, 11.0}; // y = 2x + 3, x = 0..4
        RegressionResult result = LinearRegression.compute(y);

        assertEquals(2.0, result.getSlope(), 1e-9);
        assertEquals(3.0, result.getIntercept(), 1e-9);
        assertEquals(1.0, result.getRSquared(), 1e-9);
        assertEquals(5, result.getDataPoints());
    }

    @Test
    void testPerfectLinear_negative_slope() {
        double[] y = {10.0, 7.0, 4.0, 1.0}; // y = -3x + 10
        RegressionResult result = LinearRegression.compute(y);

        assertEquals(-3.0, result.getSlope(), 1e-9);
        assertEquals(10.0, result.getIntercept(), 1e-9);
    }

    @Test
    void testFlatLine() {
        double[] y = {5.0, 5.0, 5.0, 5.0, 5.0};
        RegressionResult result = LinearRegression.compute(y);

        assertEquals(0.0, result.getSlope(), 1e-9);
        assertEquals(5.0, result.getIntercept(), 1e-9);
    }

    @Test
    void testPredictMethod() {
        double[] y = {0.0, 2.0, 4.0}; // y = 2x
        RegressionResult result = LinearRegression.compute(y);

        assertEquals(10.0, result.predict(5), 1e-9);
    }

    @Test
    void testInsufficientPoints() {
        double[] y = {1.0};
        assertThrows(IllegalArgumentException.class, () -> LinearRegression.compute(y));
    }

    @Test
    void testNullInput() {
        assertThrows(IllegalArgumentException.class, () -> LinearRegression.compute(null));
    }

    @Test
    void testTwoPoints() {
        double[] y = {1.0, 3.0}; // y = 2x + 1
        RegressionResult result = LinearRegression.compute(y);

        assertEquals(2.0, result.getSlope(), 1e-9);
        assertEquals(1.0, result.getIntercept(), 1e-9);
        assertEquals(1.0, result.getRSquared(), 1e-9);
    }

    @Test
    void testWithTimestamps() {
        long[] timestamps = {1000, 2000, 3000, 4000, 5000};
        double[] values = {3.0, 5.0, 7.0, 9.0, 11.0}; // y = 2x + 3
        RegressionResult result = LinearRegression.compute(timestamps, values);

        assertEquals(2.0, result.getSlope(), 1e-9);
    }
}
