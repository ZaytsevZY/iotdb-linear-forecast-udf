# 基于滑动窗口线性回归的 IoTDB 短期趋势预测 UDF

面向 IoTDB 时序数据的滑动窗口线性回归预测算子。在查询过程中维护一个固定长度的滑动窗口，对窗口内数据进行局部线性拟合，并输出短期预测值或趋势斜率。

## 项目结构

```
sliding-window-linear-regression/
├── pom.xml                          # Maven 构建配置
├── README.md                        # 本文档
├── docs/
│   └── DEVELOPMENT.md               # 开发过程文档
├── src/
│   ├── main/java/
│   │   ├── org/apache/iotdb/slidingWindowLR/core/   # 核心算法（独立于 IoTDB）
│   │   │   ├── OutputMode.java       # 输出模式枚举：PREDICT / SLOPE / BOTH
│   │   │   ├── PredictorConfig.java  # 预测器配置（窗口大小、预测步长等）
│   │   │   ├── SlidingWindow.java    # 滑动窗口数据结构
│   │   │   ├── LinearRegression.java # OLS 线性回归计算器（含置信区间）
│   │   │   ├── TrendPredictor.java   # 主预测器（串联窗口 + 回归 + 过滤）
│   │   │   ├── MovingAveragePredictor.java  # 移动平均预测器（对比实验用）
│   │   │   ├── OutlierFilter.java    # IQR 异常值过滤器
│   │   │   ├── RegressionResult.java # 回归结果（斜率、截距、R²、标准误差）
│   │   │   └── ApproxMath.java       # 数学近似（逆正态 CDF）
│   │   └── org/apache/iotdb/udf/                   # IoTDB UDF 封装层
│   │       └── UDTFSlidingWindowLinearRegression.java
│   └── test/java/
│       └── org/apache/iotdb/slidingWindowLR/core/
│           ├── LinearRegressionTest.java       # 回归计算测试（8）
│           ├── SlidingWindowTest.java           # 滑动窗口测试（5）
│           ├── TrendPredictorTest.java          # 主预测器测试（8）
│           ├── MovingAveragePredictorTest.java  # 移动平均测试（4）
│           ├── OutlierFilterTest.java           # 异常值过滤测试（5）
│           ├── ConfidenceIntervalTest.java      # 置信区间 + 过滤对比测试（5）
│           └── ComparisonExperimentTest.java     # LR vs MA 对比 + 窗口对比（3）
```

## 功能概述

### 核心预测

对窗口内 n 个数据点使用 OLS 拟合直线 y = a + bx：

- **斜率 b**：反映局部趋势方向（b > 0 上升，b < 0 下降，b ≈ 0 平稳）
- **截距 a**：拟合直线的基准值
- **R²**：拟合优度，衡量线性关系的强度
- **预测值**：向未来第 h 步外推，ŷ = a + b(n-1+h)

### 异常值过滤（IQR）

在回归前对窗口内数据做 IQR 过滤，移除 [Q1 - k*IQR, Q3 + k*IQR] 范围外的极端点，避免异常值拉偏拟合直线。

### 预测置信区间

基于回归标准误差和预测点距窗口中心的距离，计算预测值的置信上下界：
ŷ ± z · se · √(1 + 1/n + (x_h - x̄)² / Σ(x_i - x̄)²)

### 移动平均对比

提供 `MovingAveragePredictor` 作为基准对比方法，可直接与线性回归在相同数据上比较 MAE、MSE 等指标。

## 参数说明

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `window` | int | 10 | 滑动窗口大小 |
| `horizon` | int | 1 | 预测步长 |
| `minValidPoints` | int | 3 | 最小有效点数 |
| `output` | string | "predict" | 输出模式："predict"、"slope"、"both" |
| `enableOutlierFilter` | bool | false | 是否启用 IQR 异常值过滤 |
| `iqrMultiplier` | double | 1.5 | IQR 倍数，控制过滤灵敏度 |
| `enableConfidenceInterval` | bool | false | 是否输出预测置信区间 |
| `confidenceLevel` | double | 0.95 | 置信水平 |

## Java API 使用示例

```java
import org.apache.iotdb.slidingWindowLR.core.*;

// 基础预测
PredictorConfig config = PredictorConfig.builder()
    .windowSize(10).horizon(1).minValidPoints(3)
    .build();
TrendPredictor predictor = new TrendPredictor(config);

// 带异常值过滤 + 置信区间
PredictorConfig advancedConfig = PredictorConfig.builder()
    .windowSize(15).horizon(2).minValidPoints(5)
    .enableOutlierFilter(true).iqrMultiplier(1.5)
    .enableConfidenceInterval(true).confidenceLevel(0.95)
    .build();
TrendPredictor advancedPredictor = new TrendPredictor(advancedConfig);

// 移动平均对比
MovingAveragePredictor ma = new MovingAveragePredictor(10, 1, 3);

// 批量处理
long[] timestamps = {1000, 2000, 3000, 4000, 5000};
double[] values = {10.0, 12.0, 14.0, 16.0, 18.0};
List<TrendPredictor.PredictionResult> results = predictor.processBatch(timestamps, values);

for (TrendPredictor.PredictionResult r : results) {
    if (r != null) {
        if (r.hasConfidenceInterval()) {
            System.out.printf("predicted=%.2f [%.2f, %.2f], slope=%.4f%n",
                r.getPredictedValue(), r.getCiLower(), r.getCiUpper(), r.getSlope());
        } else {
            System.out.printf("predicted=%.2f, slope=%.4f%n",
                r.getPredictedValue(), r.getSlope());
        }
    }
}
```

## IoTDB SQL 使用示例

注册 UDF 后，在 IoTDB 查询中调用：

```sql
-- 输出预测值（默认）
SELECT UDTFSlidingWindowLinearRegression(s0, 'window'='10', 'horizon'='1')
FROM root.device;

-- 输出趋势斜率
SELECT UDTFSlidingWindowLinearRegression(s0, 'window'='20', 'output'='slope')
FROM root.device;

-- 自定义所有参数
SELECT UDTFSlidingWindowLinearRegression(
    s0,
    'window'='15', 'horizon'='3', 'minValidPoints'='5', 'output'='predict'
) FROM root.device;
```

## 构建

```bash
# 编译
mvn compile

# 运行测试（38 个测试用例）
mvn test

# 打包（含依赖）
mvn package -P get-jar-with-dependencies
```

## 分工

| 阶段 | 任务 | 负责人 |
|------|------|--------|
| 1 | 核心算法实现、异常值过滤、置信区间、对比实验框架、文档 | zy |
| 2 | IoTDB 接入、UDF 注册、实验 | lhx |
| 3 | 报告、PPT、展示材料 | zlb |
| 4 | 机动支援 | 天舒 |
