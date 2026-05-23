package org.apache.iotdb.matrixProfile.core;

import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.stat.descriptive.rank.Percentile; // 引入 Percentile

import java.util.stream.Collectors; // 引入 Collectors

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

public class Stamp {

    public static final double DEFAULT_VARIANCE_THRESHOLD = 1e-6; // 默认方差阈值

    /**
     * 对 List<Double> 进行 Z-score 归一化。
     *
     * @param dataList          需要归一化的 List<Double>。
     * @param varianceThreshold 如果方差小于此阈值，则抛出 IllegalArgumentException。
     * @return 归一化后的 List<Double>。
     * @throws IllegalArgumentException 如果输入列表为空或方差小于给定的阈值。
     */
    public static List<Double> zScoreNormalize(List<Double> dataList, double varianceThreshold) throws IllegalArgumentException {
        if (dataList == null || dataList.isEmpty()) {
            throw new IllegalArgumentException("Input data list cannot be null or empty.");
        }

        // 将 List<Double> 转换为 double[] 以便使用 Apache Commons Math
        double[] dataArray = dataList.stream()
                .mapToDouble(Double::doubleValue)
                .toArray();

        // 计算均值
        Mean meanCalculator = new Mean();
        double mean = meanCalculator.evaluate(dataArray);

        // 计算标准差
        // 注意：这里的构造函数参数 false 表示计算总体标准差 (分母为 n)
        StandardDeviation standardDeviationCalculator = new StandardDeviation(false);
        double stdDev = standardDeviationCalculator.evaluate(dataArray);

        // 检查方差（标准差的平方）是否小于阈值
        if (stdDev * stdDev < varianceThreshold) {
            throw new IllegalArgumentException("Variance is too small (" + (stdDev * stdDev) + "), cannot perform Z-score normalization.");
        }

        // 进行 Z-score 归一化
        List<Double> normalizedDataList = new ArrayList<>(dataList.size());
        for (Double value : dataList) {
            normalizedDataList.add((value - mean) / stdDev);
        }

        return normalizedDataList;
    }

    /**
     * 计算两个 Z-score 归一化后的序列之间的欧氏距离。
     *
     * @param sequenceA 第一个 Z-score 归一化后的序列。
     * @param sequenceB 第二个 Z-score 归一化后的序列。
     * @return 两个序列之间的欧氏距离。
     * @throws IllegalArgumentException 如果输入的序列为空或长度不一致。
     */
    public static double euclideanDistance(List<Double> sequenceA, List<Double> sequenceB) throws IllegalArgumentException {
        if (sequenceA == null || sequenceA.isEmpty() || sequenceB == null || sequenceB.isEmpty()) {
            throw new IllegalArgumentException("Input sequences cannot be null or empty.");
        }
        if (sequenceA.size() != sequenceB.size()) {
            throw new IllegalArgumentException("Input sequences must have the same length.");
        }

        int n = sequenceA.size();
        double sumOfSquaredDifferences = 0.0;

        for (int i = 0; i < n; i++) {
            double diff = sequenceA.get(i) - sequenceB.get(i);
            sumOfSquaredDifferences += diff * diff; // 计算差的平方并累加
        }

        return FastMath.sqrt(sumOfSquaredDifferences);
    }

    /**
     * 计算时间序列的 Matrix Profile 和 Profile Index (基本 STAMP 算法)。
     *
     * @param timeSeries        原始时间序列数据。
     * @param m                 子序列的窗口长度。
     * @param k                 最小不计算距离，用于排除平凡匹配。
     * @param varianceThreshold Z-score 归一化时的方差阈值。
     * @return 一个包含两个 List 的 List：第一个 List 是 Matrix Profile 值，第二个 List 是对应的 Profile Index。
     * @throws IllegalArgumentException 如果输入参数无效 (例如，时间序列太短，m 或 k 不合法)。
     */
    public static List<List<Double>> computeMatrixProfile(List<Double> timeSeries, int m, int k, double varianceThreshold) throws IllegalArgumentException {
        if (timeSeries == null || timeSeries.isEmpty()) {
            throw new IllegalArgumentException("Time series cannot be null or empty.");
        }
        int n = timeSeries.size();
        if (m <= 0) {
            throw new IllegalArgumentException("Window length m must be positive.");
        }
        if (m > n) {
            throw new IllegalArgumentException("Window length m cannot be greater than time series length.");
        }
        if (k < 0) {
            throw new IllegalArgumentException("Exclusion zone k must be non-negative.");
        }
        if (n < m + 2) { // 至少需要两个长度为 m 的子序列才能计算距离
            throw new IllegalArgumentException("Time series is too short for the given window length m.");
        }


        // 计算 Matrix Profile 的长度 (可能子序列的数量)
        int profileLength = n - m + 1;
        if (profileLength <= 1) {
            throw new IllegalArgumentException("Profile length is too short, cannot compute Matrix Profile.");
        }

        // 初始化 Matrix Profile 和 Profile Index
        List<Double> matrixProfile = new ArrayList<>(profileLength);
        List<Double> profileIndex = new ArrayList<>(profileLength);

        for (int i = 0; i < profileLength; i++) {
            matrixProfile.add(Double.POSITIVE_INFINITY);
            profileIndex.add(-1.0);
        }

        for (int i = 0; i < profileLength; i++) { // 遍历所有可能的子序列 P (起始索引 i)
            List<Double> subseriesP = timeSeries.subList(i, i + m);

            List<Double> normalizedP;
            try {
                normalizedP = zScoreNormalize(subseriesP, varianceThreshold);
            } catch (IllegalArgumentException e) {
                // 如果子序列的方差太小，其距离其他序列的 Z-normalized 距离将不稳定或无穷大。
                // 在 Matrix Profile 中，通常会将这些子序列的 Profile 值设为无穷大。
                // 这里我们继续循环，保持其初始的无穷大值。
                // System.err.println("Warning: Subseries at index " + i + " has small variance. Skipping distance calculations for this subseries.");
                continue; // 跳过当前子序列的距离计算
            }

            for (int j = 0; j < profileLength; j++) { // 遍历所有其他可能的子序列 Q (起始索引 j)
                // 排除平凡匹配
                if (FastMath.abs(i - j) <= k) {
                    continue;
                }

                List<Double> subseriesQ = timeSeries.subList(j, j + m);

                List<Double> normalizedQ;
                try {
                    normalizedQ = zScoreNormalize(subseriesQ, varianceThreshold);
                } catch (IllegalArgumentException e) {
                    // 如果子序列 Q 的方差太小，跳过这个距离计算
                    // System.err.println("Warning: Subseries at index " + j + " has small variance. Skipping distance calculation with subseries at index " + i + ".");
                    continue;
                }

                // 计算归一化后的子序列 P 和 Q 之间的欧氏距离
                double distance = euclideanDistance(normalizedP, normalizedQ);

                // 更新 Matrix Profile 和 Profile Index
                if (distance < matrixProfile.get(i)) {
                    matrixProfile.set(i, distance);
                    profileIndex.set(i, (double) j);
                }
                // 利用对称性更新 j
                if (distance < matrixProfile.get(j)) {
                    matrixProfile.set(j, distance);
                    profileIndex.set(j, (double) i);
                }
            }
        }

        List<List<Double>> result = new ArrayList<>(2);
        result.add(matrixProfile);
        result.add(profileIndex);
        return result;
    }

    /**
     * 基于 Matrix Profile 检测异常点 (基于阈值法)。
     * 阈值通过计算 Matrix Profile 中**非无穷大值**的百分位数确定。
     *
     * @param matrixProfile       Matrix Profile 值序列。
     * @param percentileThreshold 用于确定阈值的百分位数 (例如 95 表示使用 95th 百分位数作为阈值)。
     * @return 异常点的索引列表 (对应于原始时间序列中异常子序列的起始索引)。
     * @throws IllegalArgumentException 如果输入 Matrix Profile 序列为空或百分位数无效。
     */
    public static List<Integer> detectAnomalies(List<Double> matrixProfile, double percentileThreshold) throws IllegalArgumentException {
        if (matrixProfile == null || matrixProfile.isEmpty()) {
            throw new IllegalArgumentException("Matrix Profile cannot be null or empty for anomaly detection.");
        }
        if (percentileThreshold < 0 || percentileThreshold > 100) {
            throw new IllegalArgumentException("Percentile threshold must be between 0 and 100.");
        }

        // 过滤掉无穷大值和 NaN 值，只保留有限的 Matrix Profile 值
        List<Double> finiteMatrixProfile = matrixProfile.stream()
                .filter(value -> value != null && !Double.isInfinite(value) && !Double.isNaN(value))
                .collect(Collectors.toList());

        // 如果过滤后没有有限值，无法计算百分位数
        if (finiteMatrixProfile.isEmpty()) {
            System.err.println("Warning: No finite Matrix Profile values found to calculate percentile threshold. No anomalies detected by thresholding.");
            return new ArrayList<>(); // 返回空列表表示没有检测到异常
        }

        // 将有限 Matrix Profile 值转换为 double[] 以便使用 Apache Commons Math 的 Percentile
        double[] finiteMpArray = finiteMatrixProfile.stream()
                .mapToDouble(Double::doubleValue)
                .toArray();

        // 计算 Matrix Profile 值的百分位数 (基于有限值)
        Percentile percentileCalculator = new Percentile();
        double threshold = percentileCalculator.evaluate(finiteMpArray, percentileThreshold);

        System.out.println("Calculated Anomaly Threshold (based on " + percentileThreshold + " percentile of finite MP values): " + threshold);

        // 找到原始 Matrix Profile 中值大于阈值的索引 (仍然检查原始列表，但排除无穷大)
        List<Integer> anomalyIndices = new ArrayList<>();
        for (int i = 0; i < matrixProfile.size(); i++) {
            // 只有当 Matrix Profile 值是有限的并且大于阈值时才认为是异常
            if (!Double.isInfinite(matrixProfile.get(i)) && matrixProfile.get(i) > threshold) {
                anomalyIndices.add(i);
            }
        }

        return anomalyIndices;
    }


    // 添加 main 方法用于简单的测试
    public static void main(String[] args) {
        // 示例时间序列数据 (来自 Matrix Profile 论文的示例数据)
        // 引入一个明显的异常点 (例如在索引 35 开始的一个尖峰)
        // 并引入一个方差非常小的平坦段
        List<Double> timeSeries = new ArrayList<>(Arrays.asList(
                0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 4.0, 3.0, 2.0, 1.0, // 波峰 1 (0-9)
                0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 4.0, 3.0, 2.0, 1.0, // 波峰 2 (10-19)
                0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 4.0, 3.0, 2.0, 1.0, // 波峰 3 (20-29)
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, // 平坦段 (索引 30-39)
                // 异常点开始 (索引 40)
                -5.0, -4.0, -3.0, -2.0, -1.0, // 索引 40-44 的异常子序列
                // 异常点结束
                10.0, 10.0, 10.0, 10.0 // 另一个平坦段 (索引 45-48)
        ));
        int m = 5; // 窗口长度
        int k = m / 4; // 最小不计算距离，通常取 m/4 (这里 k=1)
        double varianceThreshold = 1e-9; // Z-score 归一化时的方差阈值
        double anomalyPercentileThreshold = 90.0; // 使用 95th 百分位数作为异常阈值

        try {
            List<List<Double>> matrixProfileResult = computeMatrixProfile(timeSeries, m, k, varianceThreshold);
            List<Double> mp = matrixProfileResult.get(0);
            List<Double> pi = matrixProfileResult.get(1);

            System.out.println("Time Series Length: " + timeSeries.size());
            System.out.println("Window Length (m): " + m);
            System.out.println("Exclusion Zone (k): " + k);
            System.out.println("Profile Length (N-m+1): " + mp.size());

            System.out.println("\nMatrix Profile:");
            System.out.println(mp);

            System.out.println("\nProfile Index:");
            System.out.println(pi);

            // 找到最小 Matrix Profile 值及其对应的位置 (可能表示模体)
            double minMpValue = Double.POSITIVE_INFINITY;
            int minMpIndex = -1;
            for (int i = 0; i < mp.size(); i++) {
                if (mp.get(i) < minMpValue) {
                    minMpValue = mp.get(i);
                    minMpIndex = i;
                }
            }
            System.out.println("\nMinimum Matrix Profile value: " + minMpValue);
            System.out.println("Index of minimum Matrix Profile value: " + minMpIndex);
            if (minMpIndex != -1 && minMpIndex < pi.size()) { // 确保索引在范围内
                System.out.println("Corresponding nearest neighbor index: " + pi.get(minMpIndex).intValue());
            }


            // --- 异常检测 ---
            System.out.println("\n--- Anomaly Detection ---");
            List<Integer> anomalyIndices = detectAnomalies(mp, anomalyPercentileThreshold);

            System.out.println("\nAnomaly Indices (using " + anomalyPercentileThreshold + " percentile threshold on finite MP values):");
            System.out.println(anomalyIndices);

            // 输出异常点对应的子序列在原始时间序列中的值
            if (!anomalyIndices.isEmpty()) {
                System.out.println("\nAnomaly Subsequences (starting indices):");
                for (int index : anomalyIndices) {
                    if (index + m <= timeSeries.size()) { // 确保子序列在时间序列范围内
                        System.out.println("Index " + index + ": " + timeSeries.subList(index, index + m));
                    } else {
                        System.out.println("Index " + index + ": Subsequence out of bounds."); // 额外的安全检查
                    }
                }
            }


        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
        }

        // 示例：输入参数无效的情况 (可以保留或根据需要调整)
        System.out.println("\n--- Testing invalid input parameters ---");
        try {
            computeMatrixProfile(timeSeries, timeSeries.size() + 1, k, varianceThreshold); // m > n
        } catch (IllegalArgumentException e) {
            System.err.println("Testing invalid m: " + e.getMessage());
        }

        try {
            computeMatrixProfile(timeSeries, m, -1, varianceThreshold); // k < 0
        } catch (IllegalArgumentException e) {
            System.err.println("Testing invalid k: " + e.getMessage());
        }

        try {
            List<Double> shortTimeSeries = Arrays.asList(1.0, 2.0, 3.0, 4.0, 5.0, 6.0); // n=6, m=5, profileLength = 2 (n < m+2 will fail)
            computeMatrixProfile(shortTimeSeries, 5, 0, varianceThreshold);
        } catch (IllegalArgumentException e) {
            System.err.println("Testing short time series: " + e.getMessage()); // Should pass now with n < m+2 check
        }

        try {
            List<Double> veryShortTimeSeries = Arrays.asList(1.0, 2.0, 3.0, 4.0); // n=4, m=5 -> m > n fails
            computeMatrixProfile(veryShortTimeSeries, 5, 0, varianceThreshold);
        } catch (IllegalArgumentException e) {
            System.err.println("Testing very short time series (m > n): " + e.getMessage());
        }

        try {
            List<Double> minLengthTimeSeries = Arrays.asList(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0); // n=7, m=5, profileLength=3 (n >= m+2 passes)
            computeMatrixProfile(minLengthTimeSeries, 5, 0, varianceThreshold);
            System.out.println("Testing min length time series: Passed.");
        } catch (IllegalArgumentException e) {
            System.err.println("Testing min length time series: Failed unexpectedly: " + e.getMessage());
        }


    }
}