package org.apache.iotdb.udf;

import org.apache.iotdb.slidingWindowLR.core.OutputMode;
import org.apache.iotdb.slidingWindowLR.core.PredictorConfig;
import org.apache.iotdb.slidingWindowLR.core.TrendPredictor;

import org.apache.iotdb.udf.api.UDTF;
import org.apache.iotdb.udf.api.access.Row;
import org.apache.iotdb.udf.api.collector.PointCollector;
import org.apache.iotdb.udf.api.customizer.config.UDTFConfigurations;
import org.apache.iotdb.udf.api.customizer.parameter.UDFParameterValidator;
import org.apache.iotdb.udf.api.customizer.parameter.UDFParameters;
import org.apache.iotdb.udf.api.customizer.strategy.RowByRowAccessStrategy;
import org.apache.iotdb.udf.api.type.Type;

/**
 * IoTDB UDF: Sliding Window Linear Regression for short-term trend prediction.
 *
 * Usage:
 *   SELECT UDTFSlidingWindowLinearRegression(s0, 'window'='10', 'horizon'='1',
 *          'minValidPoints'='3', 'output'='predict') FROM root.device;
 *
 * Parameters:
 *   window         - sliding window size (default: 10)
 *   horizon        - prediction step ahead (default: 1)
 *   minValidPoints - minimum points before producing output (default: 3)
 *   output         - "predict", "slope", or "both" (default: "predict")
 */
public class UDTFSlidingWindowLinearRegression implements UDTF {

    private TrendPredictor predictor;
    private PredictorConfig config;

    @Override
    public void validate(UDFParameterValidator validator) throws Exception {
        validator.validateInputSeriesDataType(0, Type.DOUBLE);
    }

    @Override
    public void beforeStart(UDFParameters parameters, UDTFConfigurations configurations)
            throws Exception {
        int window = parameters.getIntOrDefault("window", 10);
        int horizon = parameters.getIntOrDefault("horizon", 1);
        int minValidPoints = parameters.getIntOrDefault("minValidPoints", 3);
        String outputStr = parameters.getStringOrDefault("output", "predict");

        OutputMode outputMode;
        switch (outputStr.toLowerCase()) {
            case "slope":
                outputMode = OutputMode.SLOPE;
                break;
            case "both":
                outputMode = OutputMode.BOTH;
                break;
            default:
                outputMode = OutputMode.PREDICT;
        }

        this.config = PredictorConfig.builder()
                .windowSize(window)
                .horizon(horizon)
                .minValidPoints(minValidPoints)
                .outputMode(outputMode)
                .build();

        this.predictor = new TrendPredictor(config);

        configurations
                .setAccessStrategy(new RowByRowAccessStrategy())
                .setOutputDataType(Type.DOUBLE);
    }

    @Override
    public void transform(Row row, PointCollector collector) throws Exception {
        double value = row.getDouble(0);
        long timestamp = row.getTime();

        TrendPredictor.PredictionResult result = predictor.addPoint(timestamp, value);
        if (result == null) {
            return;
        }

        switch (config.getOutputMode()) {
            case PREDICT:
                collector.putDouble(timestamp, result.getPredictedValue());
                break;
            case SLOPE:
                collector.putDouble(timestamp, result.getSlope());
                break;
            case BOTH:
                collector.putDouble(timestamp, result.getPredictedValue());
                break;
        }
    }

    @Override
    public void terminate(PointCollector collector) throws Exception {
        // No additional batch output needed for streaming mode
    }
}
