# Gluten 代码示例

本目录包含《Apache Gluten 深入浅出》一书的所有代码示例。

## 📊 统计概览

- **总文件数**: 24 个
- **代码行数**: ~6,000 行
- **涵盖章节**: 第2-13章
- **质量等级**: 生产级（包含完整注释和错误处理）

## 目录结构

```
code-examples/
├── scala/           # Scala 示例 (7 个文件)
├── python/          # Python 工具 (7 个文件)
├── shell/           # Shell 脚本 (3 个文件)
├── configs/         # 配置模板 (4 个文件)
├── cpp/             # C++ UDF 示例 (1 个文件)
└── README.md        # 本文件
```

## 📂 完整示例列表

### 🐍 Python 工具 (7 个)

#### 1. **substrait_plan_viewer.py** (331 行) - 第5章
Substrait 执行计划可视化工具

**功能**：
- 解析 Substrait Protocol Buffer 文件
- 构建和渲染查询计划树
- 导出为 DOT/JSON 格式
- 分析算子统计信息

**用法**：
```bash
python substrait_plan_viewer.py --plan plan.bin --output plan.dot
python substrait_plan_viewer.py --plan plan.bin --format json
```

#### 2. **fallback_analysis.py** (460 行) - 第9章
Fallback 原因分析工具

**功能**：
- 解析 Spark UI 和执行计划日志
- 自动分类 Fallback 原因
- 生成 HTML/JSON/文本报告
- 提供优化建议

**用法**：
```bash
python fallback_analysis.py --log spark-events.log --output report.html
python fallback_analysis.py --plan plan.txt --format json
```

#### 3. **shuffle_compression_benchmark.py** (400 行) - 第8章
Shuffle 压缩算法基准测试

**功能**：
- 对比 LZ4、Zstd、Snappy 压缩算法
- 测试不同数据类型的压缩效果
- 生成性能图表
- 输出 Spark 配置建议

**用法**：
```bash
python shuffle_compression_benchmark.py --data-size 10000000
python shuffle_compression_benchmark.py --plot benchmark.png
```

#### 4. **backend_comparison.py** (340 行) - 第13章
Velox vs ClickHouse 自动化对比

**功能**：
- 自动运行 TPC-H 查询
- 对比两种后端性能
- 生成详细报告
- 推荐最优后端

**用法**：
```bash
python backend_comparison.py --tpch-path /data/tpch --queries Q1,Q3,Q6
python backend_comparison.py --output comparison.json
```

#### 5. **generate_test_data.py** (108 行) - 第2章
生成测试数据

**用法**：
```bash
python generate_test_data.py 10000000 data/test_data.parquet
```

#### 6. **gluten_demo.py** (103 行) - 第2章
Gluten 功能演示

**用法**：
```bash
export GLUTEN_JAR=/path/to/gluten-jar
python gluten_demo.py
```

#### 7. **benchmark.py** (105 行) - 第3章
性能对比测试

**用法**：
```bash
python benchmark.py
```

### 📝 Scala 示例 (7 个)

#### 1. **PlanTransformationDemo.scala** (341 行) - 第5章
查询计划转换演示

**功能**：
- 对比 Gluten vs Vanilla Spark 执行计划
- 分析算子分布和类型
- 检测 Fallback (C2R/R2C)
- 4 个完整示例（简单查询、Join、聚合、复杂查询）

**用法**：
```bash
spark-shell --jars /path/to/gluten-*.jar
:load code-examples/scala/PlanTransformationDemo.scala
PlanTransformationDemo.runAllTests()
```

#### 2. **MemoryMonitoring.scala** (423 行) - 第6章
内存监控和分析工具

**功能**：
- 实时监控 JVM 和 Off-Heap 内存
- 内存泄漏检测
- 分析内存池分配
- 生成优化建议

**用法**：
```bash
spark-shell --jars /path/to/gluten-*.jar
:load code-examples/scala/MemoryMonitoring.scala
MemoryMonitoring.runAllTests()
```

#### 3. **OffHeapDemo.scala** (370 行) - 第6章
堆外内存性能对比

**功能**：
- 对比 On-Heap vs Off-Heap 性能
- 测试不同 Off-Heap 大小影响
- 可扩展性测试
- 配置最佳实践

**用法**：
```bash
spark-shell --jars /path/to/gluten-*.jar
:load code-examples/scala/OffHeapDemo.scala
OffHeapDemo.runAllTests()
```

#### 4. **ColumnarShuffleDemo.scala** (360 行) - 第8章
Columnar Shuffle 性能演示

**功能**：
- Row-based vs Columnar Shuffle 对比
- 测试不同分区数影响
- Shuffle 配置优化建议

**用法**：
```bash
spark-shell --jars /path/to/gluten-*.jar
:load code-examples/scala/ColumnarShuffleDemo.scala
ColumnarShuffleDemo.runAllTests()
```

#### 5. **FallbackDetection.scala** (362 行) - 第9章
Fallback 自动检测工具

**功能**：
- 自动检测查询中的 Fallback
- 统计 C2R/R2C 转换
- 分析 Fallback 原因并提供建议
- 导出 JSON 报告

**用法**：
```bash
val detector = new FallbackDetector(spark)
detector.analyzeQuery("SELECT * FROM table WHERE col > 100")
detector.generateReport()
```

#### 6. **VeloxCacheDemo.scala** (355 行) - 第11章
Velox Cache 使用演示

**功能**：
- 演示 Velox File Cache 使用
- 对比 Cache 启用前后性能
- Cache 命中率监控
- 配置优化建议

**用法**：
```bash
spark-shell --jars /path/to/gluten-*.jar
:load code-examples/scala/VeloxCacheDemo.scala
VeloxCacheDemo.runAllTests()
```

#### 7. **ClickHouseBenchmark.scala** (380 行) - 第12章
ClickHouse 性能基准测试

**功能**：
- ClickHouse 后端功能验证
- 聚合、Join、字符串处理性能测试
- 与标准 Spark 对比
- 特色功能演示

**用法**：
```bash
spark-shell --jars /path/to/gluten-clickhouse-*.jar
:load code-examples/scala/ClickHouseBenchmark.scala
ClickHouseBenchmark.runAllTests()
```

#### 原有示例

**GlutenDemo.scala** (149 行) - 第2章  
Gluten 功能演示（Scala 版本）

**GlutenBenchmark.scala** (143 行) - 第3章  
性能基准测试（Scala 版本）

### 🔧 Shell 脚本 (3 个)

#### 1. **switch-backend.sh** (280 行) - 第13章
后端自动切换脚本

**功能**：
- 一键切换 Velox/ClickHouse 后端
- 自动备份和恢复配置
- 状态检查和验证
- 彩色交互界面

**用法**：
```bash
./switch-backend.sh velox          # 切换到 Velox
./switch-backend.sh clickhouse     # 切换到 ClickHouse
./switch-backend.sh status         # 查看当前状态
./switch-backend.sh backup         # 备份配置
```

#### 2. **check_environment.sh** (80 行) - 第2章
检查 Gluten 运行环境

**用法**：
```bash
./check_environment.sh
```

#### 3. **run_gluten_demo.sh** (79 行) - 第2章
一键运行 Gluten 演示

**用法**：
```bash
export GLUTEN_JAR=/path/to/gluten-jar
./run_gluten_demo.sh
```

### ⚙️ 配置文件 (4 个)

#### 1. **velox-config.conf** (205 行) - 第11章
Velox 后端完整配置模板

**功能**：
- 12 个配置分类（核心、内存、执行、Shuffle、Cache 等）
- 详细参数说明和注释
- 不同规模集群配置建议
- 开箱即用的生产级配置

**用法**：
```bash
spark-submit --properties-file velox-config.conf your_app.py
```

#### 2. **clickhouse-config.conf** (156 行) - 第12章
ClickHouse 后端完整配置

**功能**：
- ClickHouse 特定运行时设置
- 内存限制和性能优化
- 使用场景建议

**用法**：
```bash
spark-submit --properties-file clickhouse-config.conf your_app.py
```

#### 3. **gluten-basic.conf** (61 行) - 第2章
基础配置模板

**用法**：
```bash
cp gluten-basic.conf $SPARK_HOME/conf/spark-defaults.conf
```

#### 4. **gluten-production.conf** (60 行) - 第3章
生产环境配置模板

**用法**：
```bash
spark-submit --properties-file gluten-production.conf your_app.py
```

### 💻 C++ UDF 示例 (1 个)

#### **velox_udf_example.cpp** (51 行) - 第11章
Velox Native UDF 开发示例

**功能**：
- 演示 Velox UDF 编写方法
- 包含字符串处理和数值计算示例
- 编译和集成说明

**编译**：
```bash
g++ -std=c++17 -fPIC -shared \
  -I${VELOX_HOME}/include \
  velox_udf_example.cpp -o libvelox_udf.so
```

## 🚀 快速开始

### 入门示例（第2章）

```bash
# 1. 检查环境
cd shell
./check_environment.sh

# 2. 生成测试数据
cd ../python
python generate_test_data.py 10000000

# 3. 运行演示
export GLUTEN_JAR=/path/to/gluten-jar
python gluten_demo.py

# 4. 性能对比
python benchmark.py
```

### 高级工具（第5-13章）

```bash
# 查询计划分析
spark-shell --jars /path/to/gluten-*.jar
:load code-examples/scala/PlanTransformationDemo.scala
PlanTransformationDemo.runAllTests()

# 内存监控
:load code-examples/scala/MemoryMonitoring.scala
MemoryMonitoring.runAllTests()

# Fallback 检测
val detector = new FallbackDetector(spark)
detector.analyzeQuery("YOUR_SQL")
detector.generateReport()

# Shuffle 压缩测试
python shuffle_compression_benchmark.py --plot benchmark.png

# 后端切换
./switch-backend.sh velox

# 后端性能对比
python backend_comparison.py --tpch-path /data/tpch
```

## 环境变量

需要设置以下环境变量：

| 变量 | 说明 | 示例 |
|------|------|------|
| `GLUTEN_JAR` | Gluten JAR 文件路径 | `/opt/gluten/gluten-velox-bundle-spark3.3_2.12-1.2.0.jar` |
| `SPARK_HOME` | Spark 安装目录 | `~/spark-3.3.1` |
| `DATA_PATH` | 测试数据路径 | `data/test_data.parquet` |
| `JAVA_HOME` | Java 安装目录 | `/usr/lib/jvm/java-8-openjdk-amd64` |

## 常见问题

### Q1: UnsatisfiedLinkError
**问题**：运行时提示找不到动态库

**解决**：
```bash
# 使用官方预编译包（推荐）
# 或设置
spark.gluten.loadLibFromJar=true
```

### Q2: 数据文件不存在
**问题**：找不到 test_data.parquet

**解决**：
```bash
# 先生成数据
python generate_test_data.py
```

### Q3: 内存不足
**问题**：OOM 错误

**解决**：
```bash
# 增加 Off-Heap 内存
spark.memory.offHeap.size=4g  # 或更大
```

## 📚 按章节导航

| 章节 | 示例文件 | 类型 | 功能 |
|-----|---------|------|------|
| 第2章 | generate_test_data.py, gluten_demo.py, GlutenDemo.scala | 入门 | 基础使用 |
| 第3章 | benchmark.py, GlutenBenchmark.scala, gluten-production.conf | 调优 | 性能对比 |
| 第5章 | PlanTransformationDemo.scala, substrait_plan_viewer.py | 计划 | 计划分析 |
| 第6章 | MemoryMonitoring.scala, OffHeapDemo.scala | 内存 | 内存管理 |
| 第8章 | ColumnarShuffleDemo.scala, shuffle_compression_benchmark.py | Shuffle | 性能优化 |
| 第9章 | FallbackDetection.scala, fallback_analysis.py | Fallback | 问题诊断 |
| 第11章 | velox-config.conf, VeloxCacheDemo.scala, velox_udf_example.cpp | Velox | 后端配置 |
| 第12章 | clickhouse-config.conf, ClickHouseBenchmark.scala | ClickHouse | 后端配置 |
| 第13章 | backend_comparison.py, switch-backend.sh | 对比 | 后端选择 |

## 🎯 使用场景推荐

### 场景1：初次接触 Gluten
→ 第2章示例：`gluten_demo.py` 或 `GlutenDemo.scala`

### 场景2：性能问题排查
→ 第6章：`MemoryMonitoring.scala` (内存问题)  
→ 第9章：`FallbackDetection.scala` (Fallback 过多)  
→ 第8章：`ColumnarShuffleDemo.scala` (Shuffle 慢)

### 场景3：后端选择
→ 第13章：`backend_comparison.py` (自动对比)  
→ 第11-12章：配置文件参考

### 场景4：生产环境部署
→ 配置文件：`velox-config.conf` 或 `clickhouse-config.conf`  
→ 工具脚本：`switch-backend.sh`, `check_environment.sh`

### 场景5：性能调优
→ 第8章：`shuffle_compression_benchmark.py` (压缩算法)  
→ 第6章：`OffHeapDemo.scala` (内存配置)  
→ 第11章：`VeloxCacheDemo.scala` (Cache 优化)

## 💡 代码质量标准

所有示例均达到以下标准：
- ✅ **可运行性**：经过测试，可直接运行
- ✅ **完整注释**：中文注释，解释关键逻辑
- ✅ **错误处理**：包含异常处理和错误提示
- ✅ **使用说明**：文件头部有详细使用指南
- ✅ **生产级**：代码质量达到生产环境标准

## 🤝 贡献

欢迎贡献更多示例！请确保：
- 代码可以正常运行
- 添加清晰的中文注释
- 更新本 README
- 遵循现有代码风格

## 📄 许可证

本项目采用 CC BY-NC-SA 4.0 许可证。

## 🔗 相关链接

- [Apache Gluten 官方文档](https://gluten.apache.org/)
- [Gluten GitHub 仓库](https://github.com/apache/incubator-gluten)
- [《Apache Gluten 深入浅出》主页](../../README.md)
- [代码示例进度报告](../../CODE_EXAMPLES_PROGRESS.md)
