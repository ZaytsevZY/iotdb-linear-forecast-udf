# 开发过程文档

## 第一阶段：核心算法实现（zy 负责，截止 5.31）

### 设计决策

1. **算法与 IoTDB 解耦**：核心算法放在 `org.apache.iotdb.slidingWindowLR.core` 包下，不依赖 IoTDB UDF API。UDF 封装层单独放在 `org.apache.iotdb.udf` 包下。这样 lhx 在接入 IoTDB 时只需关注 UDF 层，核心算法可以直接通过单元测试验证。

2. **直接计算优先**：当前 `TrendPredictor` 使用直接遍历窗口的方式计算回归（O(w) per point）。`LinearRegression.IncrementalStats` 类实现了增量统计维护（理论 O(1)），但由于滑动窗口滑动时 x 坐标需要重新映射，增量方案的集成需要额外处理。作为后续优化方向保留。

3. **Builder 模式配置**：使用 `PredictorConfig.Builder` 构建配置对象，参数校验在 build 时完成，避免运行时出现非法状态。

4. **无外部依赖**：逆正态 CDF 使用 Abramowitz-Stegun 近似实现（`ApproxMath.java`），避免引入 commons-math 等额外依赖。

### 已完成的模块

| 模块 | 文件 | 说明 |
|------|------|------|
| 输出模式 | `OutputMode.java` | 枚举：PREDICT、SLOPE、BOTH |
| 配置 | `PredictorConfig.java` | 窗口大小、预测步长、最小有效点数、输出模式、异常值过滤、置信区间 |
| 窗口 | `SlidingWindow.java` | 基于 ArrayDeque 的定长滑动窗口 |
| 回归 | `LinearRegression.java` | OLS 线性回归，含标准误差和置信区间计算 |
| 回归结果 | `RegressionResult.java` | 斜率、截距、R²、标准误差，支持预测置信区间 |
| 预测器 | `TrendPredictor.java` | 串联窗口+回归+异常值过滤，支持置信区间输出 |
| 异常值过滤 | `OutlierFilter.java` | 基于 IQR 的窗口内异常点过滤 |
| 移动平均 | `MovingAveragePredictor.java` | 滑动窗口移动平均预测器（对比实验基准） |
| 数学工具 | `ApproxMath.java` | 逆正态 CDF 近似（Abramowitz-Stegun） |
| UDF 封装 | `UDTFSlidingWindowLinearRegression.java` | IoTDB UDTF 封装（lhx 可在此基础上完善） |

### 新增功能说明

#### 1. IQR 异常值过滤

在回归计算前，对窗口内数据做 IQR（四分位距）过滤：
- 计算窗口内数据的 Q1、Q3、IQR = Q3 - Q1
- 移除超出 [Q1 - k*IQR, Q3 + k*IQR] 范围的值
- k 默认 1.5（标准 IQR 规则），可配置
- 过滤后数据不足 minValidPoints 时，退回使用原始数据

使用场景：传感器数据中偶尔出现极端异常值（如传感器故障导致跳变），过滤后拟合更稳定。

#### 2. 预测置信区间

基于 OLS 回归的预测区间公式：
```
ŷ ± z · se · √(1 + 1/n + (x_h - x̄)² / Σ(x_i - x̄)²)
```
- `se` 为回归标准误差 = √(SSR / (n-2))
- `z` 为正态分布分位数（使用 Abramowitz-Stegun 近似）
- 距窗口中心越远的预测点，置信区间越宽

#### 3. 移动平均对比

提供 `MovingAveragePredictor` 作为基准对比方法：
- 相同窗口大小和预测步长
- 直接对窗口内值取均值作为预测
- 可与线性回归在相同数据上比较 MAE、趋势准确率等

#### 4. 对比实验框架

`ComparisonExperimentTest.java` 提供了两类对比实验的代码框架：
- **LR vs MA 对比**：在带噪声的线性趋势数据上，验证线性回归的 MAE 低于移动平均
- **窗口长度对比**：相同数据、不同窗口大小（5/10/20/50），观察 MAE 和斜率估计的变化趋势

### 测试覆盖

| 测试文件 | 测试数 | 覆盖内容 |
|----------|--------|----------|
| `LinearRegressionTest.java` | 8 | 完美线性、负斜率、水平线、预测、边界输入、带时间戳计算 |
| `SlidingWindowTest.java` | 5 | 正常添加、溢出淘汰、清空、非法容量、空窗口 |
| `TrendPredictorTest.java` | 8 | 完美序列、负趋势、重置、噪声数据、多步预测、配置校验、增量统计 |
| `MovingAveragePredictorTest.java` | 4 | 常量序列、线性序列、最小点数、重置 |
| `OutlierFilterTest.java` | 5 | 无异常值、移除极端值、保序、小数组、自定义倍数 |
| `ConfidenceIntervalTest.java` | 5 | 完美线性窄区间、噪声数据宽区间、TrendPredictor 集成、异常值过滤、过滤 vs 不过滤对比 |
| `ComparisonExperimentTest.java` | 3 | LR vs MA 线性趋势对比、不同窗口大小噪声数据、窗口对比摘要输出 |

**合计：38 个测试，全部通过。**

---

## 第二阶段：IoTDB 接入与实验（lhx 负责，截止 6.4）

### 接入要点

1. **UDF 注册**：将打包好的 jar 注册到 IoTDB
2. **参数传递**：UDTFSlidingWindowLinearRegression 已实现参数解析，需验证与 IoTDB 参数传递机制兼容
3. **输出类型**：当前使用 DOUBLE 输出，如果 output=both 需要考虑多列输出的方案
4. **新参数**：`enableOutlierFilter`、`iqrMultiplier`、`enableConfidenceInterval`、`confidenceLevel` 需要在 UDF 层解析

### 实验设计

| 实验 | 说明 | 数据 | 已有工具 |
|------|------|------|----------|
| 正确性 | 已知 y=2x+3 序列 | 人工构造 | TrendPredictor |
| 趋势识别 | 上升、下降、平稳 | 人工构造 | TrendPredictor |
| 抗噪声 | 线性趋势 + 随机扰动 | 人工构造 | TrendPredictor |
| 异常值过滤效果 | 带尖峰的序列，对比过滤前后 | 人工构造 | enableOutlierFilter=true |
| 窗口长度影响 | 不同窗口下的 MAE、斜率估计 | 人工+真实数据 | PredictorConfig |
| LR vs MA 对比 | MAE、趋势方向准确率 | 人工构造 | MovingAveragePredictor |
| IoTDB 集成 | 注册 UDF 后 SQL 调用 | 样例数据 | UDF 封装 |
| 性能 | 不同数据量和窗口下的查询耗时 | 不同规模数据集 | IoTDB 环境 |

---

## 第三阶段：报告与展示（zlb 负责，截止 6.8）

待前两阶段完成后推进。

---

## 更新日志

### 2026-05-23 (v2)

- 新增 `OutlierFilter.java`：IQR 异常值过滤器
- 新增 `MovingAveragePredictor.java`：移动平均预测器（对比实验基准）
- 新增 `ApproxMath.java`：逆正态 CDF 近似计算
- `RegressionResult.java` 新增标准误差、置信区间计算
- `LinearRegression.compute()` 返回增强的 RegressionResult（含 se、meanX、sumX2Centered）
- `PredictorConfig.java` 新增异常值过滤和置信区间配置项
- `TrendPredictor.java` 集成异常值过滤和置信区间输出
- 新增 17 个测试用例，总测试数从 21 增加到 38
- 更新 README 和本开发文档

### 2026-05-23 (v1)

- 完成项目 Maven 框架搭建
- 实现 5 个核心类和 1 个 UDF 封装类
- 编写 21 个单元测试，全部通过
- 编写 README 和本开发文档
