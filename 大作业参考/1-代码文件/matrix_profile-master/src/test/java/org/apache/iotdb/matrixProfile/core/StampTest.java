package org.apache.iotdb.matrixProfile.core;

import org.apache.commons.math3.util.FastMath;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*; // 导入 JUnit 5 的断言方法

class StampTest {

    // 测试 zScoreNormalize 的正常归一化情况 (基于总体标准差)
    @Test
    void testZScoreNormalize_NormalData_PopulationStdDev() {
        List<Double> rawData = Arrays.asList(1.0, 2.0, 3.0, 4.0, 5.0);
        double varianceThreshold = 0.001; // 一个较低的阈值

        // 预期结果，基于总体标准差 sqrt(2.0) ≈ 1.4142135623730951
        // (x - mean) / stdDev
        // (1.0 - 3.0) / 1.4142135623730951 ≈ -1.414213562373095
        // (2.0 - 3.0) / 1.4142135623730951 ≈ -0.7071067811865475
        // (3.0 - 3.0) / 1.4142135623730951 ≈ 0.0
        // (4.0 - 3.0) / 1.4142135623730951 ≈ 0.7071067811865475
        // (5.0 - 3.0) / 1.4142135623730951 ≈ 1.414213562373095
        List<Double> expectedNormalizedData = Arrays.asList(
                -1.414213562373095,
                -0.7071067811865475,
                0.0,
                0.7071067811865475,
                1.414213562373095
        );

        List<Double> actualNormalizedData = Stamp.zScoreNormalize(rawData, varianceThreshold);

        assertEquals(expectedNormalizedData.size(), actualNormalizedData.size(), "Normalized list size should match raw list size");

        for (int i = 0; i < expectedNormalizedData.size(); i++) {
            assertEquals(expectedNormalizedData.get(i), actualNormalizedData.get(i), 1e-9, // 使用一个小的 delta
                    "Element at index " + i + " does not match expected value");
        }
    }

    // 测试 zScoreNormalize 的输入列表为空的情况
    @Test
    void testZScoreNormalize_EmptyList() {
        List<Double> emptyList = new ArrayList<>();
        double varianceThreshold = 0.01;

        assertThrows(IllegalArgumentException.class, () -> {
            Stamp.zScoreNormalize(emptyList, varianceThreshold);
        }, "Should throw IllegalArgumentException for empty list in zScoreNormalize");
    }

    // 测试 zScoreNormalize 的输入列表为 null 的情况
    @Test
    void testZScoreNormalize_NullList() {
        List<Double> nullList = null;
        double varianceThreshold = 0.01;

        assertThrows(IllegalArgumentException.class, () -> {
            Stamp.zScoreNormalize(nullList, varianceThreshold);
        }, "Should throw IllegalArgumentException for null list in zScoreNormalize");
    }

    // 测试 zScoreNormalize 的方差小于阈值的情况
    @Test
    void testZScoreNormalize_SmallVariance() {
        List<Double> smallVarianceData = Arrays.asList(5.0, 5.0, 5.1, 5.0, 5.0);
        double varianceThreshold = 0.01; // 设置一个阈值，使得这个数据的方差小于它 (方差约 0.008)

        assertThrows(IllegalArgumentException.class, () -> {
            Stamp.zScoreNormalize(smallVarianceData, varianceThreshold);
        }, "Should throw IllegalArgumentException for data with small variance in zScoreNormalize");
    }

    // 测试 zScoreNormalize 的方差等于 0 的情况（所有元素相同）
    @Test
    void testZScoreNormalize_ZeroVariance() {
        List<Double> zeroVarianceData = Arrays.asList(10.0, 10.0, 10.0, 10.0);
        double varianceThreshold = 0.00001; // 任何大于 0 的阈值都会触发异常

        assertThrows(IllegalArgumentException.class, () -> {
            Stamp.zScoreNormalize(zeroVarianceData, varianceThreshold);
        }, "Should throw IllegalArgumentException for data with zero variance in zScoreNormalize");
    }

    // --- Tests for euclideanDistance method ---

    // 测试 euclideanDistance 的正常计算情况
    @Test
    void testEuclideanDistance_Normal() {
        // 示例归一化序列
        List<Double> seq1 = Arrays.asList(-1.414, -0.707, 0.0, 0.707, 1.414); // 简化值
        List<Double> seq2 = Arrays.asList(-1.3, -0.6, 0.1, 0.8, 1.5); // 稍微不同

        // 手动计算预期的欧氏距离
        // diffs: (-1.414 - (-1.3)), (-0.707 - (-0.6)), (0.0 - 0.1), (0.707 - 0.8), (1.414 - 1.5)
        // diffs: -0.114, -0.107, -0.1, -0.093, -0.086
        // squared diffs: 0.012996, 0.011449, 0.01, 0.008649, 0.007396
        // sum of squared diffs: 0.012996 + 0.011449 + 0.01 + 0.008649 + 0.007396 = 0.05049
        // sqrt(0.05049) ≈ 0.224699799...
        // 注意：使用更精确的归一化值进行计算会得到更精确的预期结果
        List<Double> preciseSeq1 = Arrays.asList(
                -1.414213562373095,
                -0.7071067811865475,
                0.0,
                0.7071067811865475,
                1.414213562373095
        ); // 来自 zScoreNormalize(Arrays.asList(1.0, 2.0, 3.0, 4.0, 5.0))

        List<Double> rawData2 = Arrays.asList(1.1, 2.1, 3.1, 4.1, 5.1);
        List<Double> preciseSeq2 = Stamp.zScoreNormalize(rawData2, 0.001); // 归一化 data2

        // 计算 preciseSeq1 和 preciseSeq2 之间的欧氏距离
        // 你可以通过运行主函数并观察输出，或者手动精确计算来确定这里的预期值
        // 例如，根据上面的 zScoreNormalize 输出和欧氏距离计算逻辑，
        // 对于 data1 和 data2，他们的归一化结果之间的欧氏距离应该接近 0.0
        // 因为 data2 只是 data1 整体上加了一个常数，Z-score 归一化应该消除这种平移。
        // 让我们计算一下精确值：
        // preciseSeq1: [-1.4142..., -0.7071..., 0.0, 0.7071..., 1.4142...]
        // preciseSeq2: [-1.4142..., -0.7071..., 0.0, 0.7071..., 1.4142...]  (因为 data2 只是 data1 平移)
        // 欧氏距离应该非常接近 0

        double expectedDistance = 0.0; // 对于平移后的数据，归一化后欧氏距离应为 0

        double actualDistance = Stamp.euclideanDistance(preciseSeq1, preciseSeq2);

        assertEquals(expectedDistance, actualDistance, 1e-9, "Euclidean distance should be close to 0 for parallel sequences");

        // 测试两个完全不同的序列
        List<Double> rawData3 = Arrays.asList(5.0, 4.0, 3.0, 2.0, 1.0); // data1 的反序
        List<Double> preciseSeq3 = Stamp.zScoreNormalize(rawData3, 0.001);

        // 计算 preciseSeq1 和 preciseSeq3 之间的欧氏距离
        // preciseSeq1: [-1.4142..., -0.7071..., 0.0, 0.7071..., 1.4142...]
        // preciseSeq3: [ 1.4142...,  0.7071..., 0.0, -0.7071..., -1.4142...] (反序归一化)
        // diffs:约 (-1.414 - 1.414), (-0.707 - 0.707), (0 - 0), (0.707 - (-0.707)), (1.414 - (-1.414))
        // diffs:约 -2.828, -1.414, 0.0, 1.414, 2.828
        // squared diffs:约 8, 2, 0, 2, 8
        // sum of squared diffs:约 8+2+0+2+8 = 20
        // sqrt(20) ≈ 4.47213595...
        double expectedDistance1_3 = FastMath.sqrt(20.0); // 精确值

        double actualDistance1_3 = Stamp.euclideanDistance(preciseSeq1, preciseSeq3);

        assertEquals(expectedDistance1_3, actualDistance1_3, 1e-9, "Euclidean distance should be correct for inverted sequence");
    }

    // 测试 euclideanDistance 的输入列表为空的情况
    @Test
    void testEuclideanDistance_EmptyList() {
        List<Double> emptyList = new ArrayList<>();
        List<Double> normalList = Arrays.asList(1.0, 2.0, 3.0);

        assertThrows(IllegalArgumentException.class, () -> {
            Stamp.euclideanDistance(emptyList, normalList);
        }, "Should throw IllegalArgumentException for empty list in euclideanDistance (seqA)");

        assertThrows(IllegalArgumentException.class, () -> {
            Stamp.euclideanDistance(normalList, emptyList);
        }, "Should throw IllegalArgumentException for empty list in euclideanDistance (seqB)");
    }

    // 测试 euclideanDistance 的输入列表为 null 的情况
    @Test
    void testEuclideanDistance_NullList() {
        List<Double> nullList = null;
        List<Double> normalList = Arrays.asList(1.0, 2.0, 3.0);

        assertThrows(IllegalArgumentException.class, () -> {
            Stamp.euclideanDistance(nullList, normalList);
        }, "Should throw IllegalArgumentException for null list in euclideanDistance (seqA)");

        assertThrows(IllegalArgumentException.class, () -> {
            Stamp.euclideanDistance(normalList, nullList);
        }, "Should throw IllegalArgumentException for null list in euclideanDistance (seqB)");
        assertThrows(IllegalArgumentException.class, () -> {
            Stamp.euclideanDistance(nullList, nullList);
        }, "Should throw IllegalArgumentException for both null lists in euclideanDistance");
    }


    // 测试 euclideanDistance 的输入列表长度不一致的情况
    @Test
    void testEuclideanDistance_DifferentLengths() {
        List<Double> list1 = Arrays.asList(1.0, 2.0, 3.0);
        List<Double> list2 = Arrays.asList(1.0, 2.0);

        assertThrows(IllegalArgumentException.class, () -> {
            Stamp.euclideanDistance(list1, list2);
        }, "Should throw IllegalArgumentException for lists of different lengths");
    }

    // 测试 euclideanDistance 的两个序列完全相同的情况
    @Test
    void testEuclideanDistance_IdenticalSequences() {
        List<Double> seq = Arrays.asList(1.0, 2.0, 3.0, 4.0, 5.0);
        double expectedDistance = 0.0; // 相同序列的欧氏距离为 0

        double actualDistance = Stamp.euclideanDistance(seq, seq);

        assertEquals(expectedDistance, actualDistance, 1e-9, "Euclidean distance of identical sequences should be 0");
    }


    // 可以添加更多测试用例，例如包含负数、小数、长度更长的序列等

}