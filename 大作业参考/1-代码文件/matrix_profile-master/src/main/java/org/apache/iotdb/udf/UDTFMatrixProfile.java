package org.apache.iotdb.udf;

import org.apache.iotdb.udf.api.UDTF;
import org.apache.iotdb.udf.api.access.Row;
import org.apache.iotdb.udf.api.collector.PointCollector;
import org.apache.iotdb.udf.api.customizer.config.UDTFConfigurations;
import org.apache.iotdb.udf.api.customizer.parameter.UDFParameterValidator;
import org.apache.iotdb.udf.api.customizer.parameter.UDFParameters;
import org.apache.iotdb.udf.api.customizer.strategy.RowByRowAccessStrategy;
import org.apache.iotdb.udf.api.type.Type;

import org.apache.iotdb.matrixProfile.core.Stamp;

import java.util.ArrayList;
import java.util.List;

public class UDTFMatrixProfile implements UDTF {

    private int m; // 窗口长度
    private int k; // 相似度计算阈值
    private List<Double> values;
    private List<Long> timestamps;

    @Override
    public void validate(UDFParameterValidator validator) throws Exception {
        validator.validateInputSeriesDataType(0, Type.DOUBLE)
                .validate(
                        x -> (int) x > 0,
                        "Window size should be larger than 0.",
                        validator.getParameters().getIntOrDefault("window", 10))
                .validate(
                        x -> (int) x > 0,
                        "Parameter k should be larger than 0.",
                        validator.getParameters().getIntOrDefault("k", 10));
    }

    @Override
    public void beforeStart(UDFParameters parameters, UDTFConfigurations configurations) throws Exception {
        this.m = parameters.getIntOrDefault("window", 10); // 必须参数，默认10
        this.k = parameters.getIntOrDefault("k", 10); // 可选参数，默认10
        configurations
                .setAccessStrategy(new RowByRowAccessStrategy()) // 一次性读取整个序列
                .setOutputDataType(Type.DOUBLE);
        this.values = new ArrayList<>();
        this.timestamps = new ArrayList<>();
    }

    @Override
    public void transform(Row row, PointCollector collector) throws Exception {
        double value = row.getDouble(0);
        long timestamp = row.getTime();
        values.add(value);
        timestamps.add(timestamp);
    }

    @Override
    public void terminate(PointCollector collector) throws Exception {
        List<List<Double>> matrixProfileResult = Stamp.computeMatrixProfile(values, this.m, this.k, Stamp.DEFAULT_VARIANCE_THRESHOLD);
        List<Double> mp = matrixProfileResult.get(0);
        for (int i = 0; i < mp.size(); i++) {
            collector.putDouble(timestamps.get(i), mp.get(i));
        }
    }
}
