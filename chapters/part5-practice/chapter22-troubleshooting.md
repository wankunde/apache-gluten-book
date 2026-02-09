# 第22章：故障排查实战

> 本章要点：
> - 常见错误类型和诊断方法
> - 性能下降问题排查
> - 内存溢出（OOM）解决方案
> - 兼容性问题处理
> - Fallback 问题分析
> - 故障排查工具箱

## 引言

即使精心配置，生产环境中仍可能遇到各种问题。本章将分享常见故障的诊断和解决方法，提供系统化的排查思路和实用工具，帮助你快速定位和解决问题，确保系统稳定运行。

## 22.1 常见错误类型

### 22.1.1 启动失败

**错误1：Native 库加载失败**

```
java.lang.UnsatisfiedLinkError: no gluten_velox in java.library.path
```

**诊断步骤**：

```bash
# 1. 检查 JAR 包是否包含 Native 库
jar tf gluten-velox-bundle.jar | grep libgluten

# 应该看到：
# native/libgluten_velox.so
# native/libvelox.so

# 2. 检查 LD_LIBRARY_PATH
echo $LD_LIBRARY_PATH

# 3. 手动加载测试
java -Djava.library.path=/path/to/gluten/lib \
     -cp gluten-velox-bundle.jar \
     org.apache.gluten.GlutenPlugin

# 4. 检查库依赖
ldd /path/to/libgluten_velox.so
```

**解决方案**：

```scala
// 方案1：设置环境变量
spark.conf.set("spark.executorEnv.LD_LIBRARY_PATH", 
    "/opt/gluten/lib:/usr/local/lib")

// 方案2：使用 --driver-library-path 和 --executor-library-path
spark-submit \
  --driver-library-path /opt/gluten/lib \
  --executor-library-path /opt/gluten/lib \
  --jars gluten-velox-bundle.jar \
  your_app.py

// 方案3：检查编译时依赖版本
// 如果是 glibc 版本不匹配：
ldd --version  # 检查系统 glibc 版本
# 重新在目标系统上编译 Gluten
```

**错误2：Spark 版本不匹配**

```
java.lang.NoSuchMethodError: org.apache.spark.sql.execution.SparkPlan.executeColumnar()
```

**原因**：Gluten 编译时的 Spark 版本与运行时不一致

**解决方案**：

```bash
# 检查 Spark 版本
spark-submit --version

# 重新编译匹配的 Gluten 版本
cd /path/to/gluten
mvn clean package -Pspark-3.3 -Pbackends-velox -DskipTests

# 或下载对应版本的预编译包
wget https://github.com/apache/incubator-gluten/releases/download/v1.2.0/gluten-velox-bundle-spark3.3_2.12-1.2.0.jar
```

### 22.1.2 运行时错误

**错误3：ClassCastException**

```
java.lang.ClassCastException: 
org.apache.spark.sql.execution.ColumnarToRowExec 
cannot be cast to org.apache.gluten.execution.ColumnarToRowExecBase
```

**原因**：类加载器冲突（多个 Gluten JAR 或版本冲突）

**诊断**：

```bash
# 检查是否有多个 Gluten JAR
find $SPARK_HOME -name "gluten*.jar"

# 检查 classpath
spark-submit --verbose your_app.py 2>&1 | grep gluten
```

**解决方案**：

```bash
# 1. 移除旧版本
rm $SPARK_HOME/jars/gluten-*.jar

# 2. 只保留一个版本
cp gluten-velox-bundle-spark3.3_2.12-1.2.0.jar $SPARK_HOME/jars/

# 3. 或使用 --jars 而不是放在 jars/ 目录
spark-submit --jars gluten-velox-bundle.jar your_app.py
```

**错误4：Substrait 转换失败**

```
org.apache.gluten.exception.GlutenException: 
Failed to convert Spark plan to Substrait: Unsupported expression type: ScalaUDF
```

**原因**：使用了 Gluten 不支持的 UDF

**解决方案**：

```scala
// 方案1：替换为内置函数
// 不要用：udf((x: String) => x.toUpperCase)
// 改用：upper(col("name"))

// 方案2：禁用该算子的 Native 执行
spark.conf.set("spark.gluten.sql.columnar.force.fallback", "true")

// 方案3：实现 Velox UDF（见第 17 章）
```

## 22.2 性能下降排查

### 22.2.1 诊断流程

```
1. 收集信息
   ├─ Spark UI（Stage/Task 时间）
   ├─ 日志（ERROR/WARN）
   └─ 监控指标（CPU/内存/I/O）
   
2. 定位瓶颈
   ├─ 慢 Stage 识别
   ├─ 慢 Task 识别
   └─ 算子级分析
   
3. 根因分析
   ├─ 数据倾斜？
   ├─ Fallback 过多？
   ├─ 配置不当？
   └─ 资源不足？
   
4. 实施解决方案
5. 验证效果
```

### 22.2.2 案例：查询突然变慢

**症状**：
- 原本 5 分钟的查询变成 30 分钟
- Spark UI 显示大量时间在 Shuffle

**诊断步骤**：

```bash
# 1. 检查数据量是否增长
spark.sql("SELECT COUNT(*) FROM my_table").show()
# 从 1亿行 → 10亿行（10x 增长）

# 2. 检查 Shuffle 分区数
spark.conf.get("spark.sql.shuffle.partitions")
# 200（默认值，不够了）

# 3. 查看 Spark UI > Stages
# 发现：Shuffle Read Size 平均 5GB per task（太大！）
```

**解决方案**：

```scala
// 增加 Shuffle 分区数
val dataSize = spark.sql("SELECT SUM(size_bytes) FROM table_metadata").collect()(0)(0).asInstanceOf[Long]
val optimalPartitions = (dataSize / (128 * 1024 * 1024)).toInt  // 128MB per partition

spark.conf.set("spark.sql.shuffle.partitions", optimalPartitions)

// 或启用 AQE（Adaptive Query Execution）
spark.conf.set("spark.sql.adaptive.enabled", "true")
spark.conf.set("spark.sql.adaptive.coalescePartitions.enabled", "true")
spark.conf.set("spark.sql.adaptive.advisoryPartitionSizeInBytes", "134217728")  // 128MB
```

### 22.2.3 案例：Fallback 导致性能下降

**症状**：
- Query Trace 显示 Native 时间只占 20%
- 大量 ColumnarToRow 和 RowToColumnar 转换

**诊断**：

```bash
# 1. 启用 Fallback 日志
spark.conf.set("spark.gluten.sql.columnar.logLevel", "DEBUG")

# 2. 查看日志
grep "Fallback" spark-executor.log

# 输出示例：
# [WARN] Fallback to Vanilla Spark for operator: Window (unsupported window function: lag)
# [WARN] Fallback to Vanilla Spark for operator: Generate (explode not supported)
```

**解决方案**：

```scala
// 1. 替换不支持的函数
// 问题：使用了 lag() 窗口函数
val df = data.withColumn("prev_value", lag("value", 1).over(window))

// 解决：使用 Gluten 支持的方式
// 方案A：升级 Gluten 版本（新版本可能支持）
// 方案B：重写逻辑
val df = data
  .withColumn("rn", row_number().over(window))
  .as("a")
  .join(
    data.withColumn("rn", row_number().over(window) + 1).as("b"),
    $"a.rn" === $"b.rn" && $"a.partition_key" === $"b.partition_key",
    "left"
  )
  .select($"a.*", $"b.value".as("prev_value"))

// 2. 检查是否启用了不必要的功能
spark.conf.set("spark.sql.codegen.wholeStage", "false")  // 可能导致 Fallback
```

### 22.2.4 数据倾斜诊断

**症状**：
- 大部分 Task 很快完成，少数 Task 特别慢
- Spark UI 显示某些 Task 的数据量远超平均值

**诊断工具**：

```python
# 数据倾斜检测脚本
from pyspark.sql import functions as F

def detect_skew(df, key_column):
    # 统计每个 Key 的数据量
    key_dist = df.groupBy(key_column) \
        .agg(F.count("*").alias("count")) \
        .orderBy(F.desc("count"))
    
    key_dist.show(20)
    
    # 计算偏度
    stats = key_dist.agg(
        F.avg("count").alias("avg"),
        F.stddev("count").alias("stddev"),
        F.max("count").alias("max"),
        F.min("count").alias("min")
    ).collect()[0]
    
    skewness = (stats["max"] - stats["avg"]) / stats["stddev"] if stats["stddev"] > 0 else 0
    
    print(f"Skewness: {skewness:.2f}")
    if skewness > 3:
        print("⚠️ Severe data skew detected!")
        # 找出热点 Key
        hot_keys = key_dist.filter(F.col("count") > stats["avg"] + 3 * stats["stddev"])
        print("Hot keys:")
        hot_keys.show()
    
    return skewness

# 使用
skew = detect_skew(orders_df, "customer_id")
```

**解决方案**：

```scala
// 方案1：加盐（Salting）
val saltedDF = df.withColumn("salted_key", 
    concat(col("customer_id"), lit("_"), (rand() * 10).cast("int")))

val result = saltedDF
    .groupBy("salted_key")
    .agg(sum("amount").as("total"))
    .withColumn("customer_id", substring_index(col("salted_key"), "_", 1))
    .groupBy("customer_id")
    .agg(sum("total").as("final_total"))

// 方案2：分离热点数据
val hotKeys = Array("customer_123", "customer_456")  // 热点客户

val hotDF = df.filter(col("customer_id").isin(hotKeys: _*))
val normalDF = df.filter(!col("customer_id").isin(hotKeys: _*))

val hotResult = hotDF.groupBy("customer_id").agg(sum("amount"))  // 单独处理
val normalResult = normalDF.groupBy("customer_id").agg(sum("amount"))  // 正常处理

val result = hotResult.union(normalResult)

// 方案3：使用 AQE 自动处理
spark.conf.set("spark.sql.adaptive.enabled", "true")
spark.conf.set("spark.sql.adaptive.skewJoin.enabled", "true")
spark.conf.set("spark.sql.adaptive.skewJoin.skewedPartitionFactor", "5")
spark.conf.set("spark.sql.adaptive.skewJoin.skewedPartitionThresholdInBytes", "256MB")
```

## 22.3 内存问题

### 22.3.1 JVM OOM

**错误信息**：

```
java.lang.OutOfMemoryError: Java heap space
```

**诊断**：

```bash
# 1. 检查 Heap 配置
spark.conf.get("spark.executor.memory")  # 例如：10g

# 2. 查看 GC 日志
# 在 spark-defaults.conf 中启用：
spark.executor.extraJavaOptions -XX:+PrintGCDetails -XX:+PrintGCTimeStamps

# 3. 分析 GC 日志
# 频繁 Full GC → Heap 不够
# Young GC 时间长 → 对象创建速度快
```

**解决方案**：

```scala
// 方案1：增加 Heap 内存
spark.conf.set("spark.executor.memory", "20g")
spark.conf.set("spark.driver.memory", "10g")

// 方案2：优化 GC
spark.conf.set("spark.executor.extraJavaOptions", 
    "-XX:+UseG1GC -XX:InitiatingHeapOccupancyPercent=35 -XX:ConcGCThreads=12")

// 方案3：减少数据缓存
spark.conf.set("spark.memory.storageFraction", "0.3")  // 从 0.5 降低到 0.3
spark.conf.set("spark.memory.fraction", "0.5")  // 减少统一内存池
```

### 22.3.2 Off-Heap OOM

**错误信息**：

```
java.lang.OutOfMemoryError: Direct buffer memory
```

**原因**：Gluten Native 内存不足

**诊断**：

```scala
// 检查配置
spark.conf.get("spark.memory.offHeap.enabled")  // true
spark.conf.get("spark.memory.offHeap.size")  // 例如：10g（可能太小）

// 查看实际使用情况（通过日志或 UI）
// Native memory usage: 12GB / 10GB (120%) ← 超了！
```

**解决方案**：

```scala
// 方案1：增加 Off-Heap 内存
spark.conf.set("spark.memory.offHeap.size", "40g")

// 方案2：启用内存隔离
spark.conf.set("spark.gluten.memory.isolation", "true")
spark.conf.set("spark.gluten.memory.reservationBlockSize", "8388608")  // 8MB

// 方案3：启用 Spill
spark.conf.set("spark.gluten.sql.columnar.backend.velox.enableSpill", "true")
spark.conf.set("spark.gluten.sql.columnar.backend.velox.spillPath", "/mnt/ssd/spill")

// 方案4：调整任务并发度（减少内存压力）
spark.conf.set("spark.executor.cores", "4")  // 从 8 降低到 4
// 每个 Executor 同时运行的 Task 数 = cores
```

### 22.3.3 内存泄漏检测

**症状**：
- 内存使用持续增长
- 即使没有任务运行，内存也不释放

**检测工具**：

```bash
# 1. 使用 Valgrind
valgrind --tool=memcheck \
         --leak-check=full \
         --show-leak-kinds=all \
         --log-file=valgrind.log \
         spark-submit your_app.py

# 2. 查看泄漏报告
grep "definitely lost" valgrind.log

# 3. 使用 Gperftools Heap Profiler
export HEAPPROFILE=/tmp/heap.prof
export HEAP_PROFILE_INUSE_INTERVAL=1073741824  # 1GB

spark-submit \
  --conf spark.executorEnv.LD_PRELOAD=/usr/lib/x86_64-linux-gnu/libtcmalloc.so \
  --conf spark.executorEnv.HEAPPROFILE=/tmp/heap.prof \
  your_app.py

# 4. 分析 Heap Profile
pprof --text /path/to/libgluten.so /tmp/heap.prof.*

# 5. 生成可视化
pprof --svg /path/to/libgluten.so /tmp/heap.prof.* > heap.svg
```

**常见泄漏原因和修复**：

```cpp
// 原因1：ArrowArray 未释放
// 错误代码：
ArrowArray* array = new ArrowArray();
// ... 使用 array
// 忘记释放！

// 正确代码：
std::unique_ptr<ArrowArray> array(new ArrowArray());
// 或使用 shared_ptr

// 原因2：JNI Global Reference 未删除
// 错误代码：
jobject obj = env->NewGlobalRef(localObj);
// ... 使用 obj
// 忘记 DeleteGlobalRef(obj)！

// 正确代码：
class JniGlobalRef {
 public:
  JniGlobalRef(JNIEnv* env, jobject obj) 
      : env_(env), obj_(env->NewGlobalRef(obj)) {}
  ~JniGlobalRef() {
    if (obj_) {
      env_->DeleteGlobalRef(obj_);
    }
  }
 private:
  JNIEnv* env_;
  jobject obj_;
};
```

## 22.4 兼容性问题

### 22.4.1 Spark 版本兼容

**问题**：不同 Spark 小版本 API 差异

**诊断**：

```bash
# 检查 Spark 版本
spark-submit --version
# Spark version 3.3.2

# 检查 Gluten 编译时的 Spark 版本
jar xf gluten-velox-bundle.jar META-INF/MANIFEST.MF
cat META-INF/MANIFEST.MF | grep Spark-Version
# Spark-Version: 3.3.1

# 不匹配可能导致问题
```

**解决方案**：

```bash
# 方案1：重新编译匹配的版本
cd /path/to/gluten
mvn clean package -Pspark-3.3.2 -Pbackends-velox -DskipTests

# 方案2：使用 Shim Layer（Gluten 自带）
# Gluten 已经处理了大部分 API 差异
# 确保使用最新版本的 Gluten

# 方案3：降级/升级 Spark 到 Gluten 支持的版本
# 支持的版本：3.2.2, 3.3.1, 3.4.4, 3.5.5
```

### 22.4.2 Hadoop 版本兼容

**问题**：Hadoop 3.2 vs 3.3 API 差异

```
java.lang.NoSuchMethodError: 
org.apache.hadoop.fs.FileSystem.getFileStatus(Lorg/apache/hadoop/fs/Path;)Lorg/apache/hadoop/fs/FileStatus;
```

**解决方案**：

```xml
<!-- 在 pom.xml 中指定 Hadoop 版本 -->
<properties>
  <hadoop.version>3.3.1</hadoop.version>
</properties>

<!-- 或在编译时指定 -->
mvn clean package -Dhadoop.version=3.3.1
```

### 22.4.3 数据源兼容性

**问题**：某些数据源不支持 Columnar 读取

```scala
// Delta Lake, Hudi, Iceberg 等
val df = spark.read.format("delta").load("/path/to/delta/table")

// 错误：Delta Lake 不支持 Gluten Native Reader
org.apache.gluten.exception.GlutenException: Unsupported data source
```

**解决方案**：

```scala
// 方案1：禁用特定表的 Native 读取
spark.conf.set("spark.gluten.sql.columnar.enableNativeReader.delta", "false")

// 方案2：使用 Row-based 读取 + Columnar 处理
val df = spark.read.format("delta").load("/path")
    .transform(_.selectExpr("*"))  // 触发 RowToColumnar

// 方案3：先物化为 Parquet
df.write.mode("overwrite").parquet("/tmp/materialized")
val parquetDF = spark.read.parquet("/tmp/materialized")  // Gluten 支持
```

## 22.5 Fallback 问题深度分析

### 22.5.1 Fallback 原因分类

| 类别 | 原因 | 解决方案 |
|------|------|---------|
| **不支持的算子** | Window, Generate, SubqueryAlias 等 | 升级 Gluten 或重写查询 |
| **不支持的函数** | 某些 UDF, 特殊函数 | 使用内置函数或实现 Native UDF |
| **不支持的数据类型** | Interval, Map (部分情况) | 转换数据类型 |
| **后端限制** | ClickHouse 不支持某些 Velox 支持的 | 切换后端 |
| **配置问题** | 强制 Fallback 配置 | 检查配置 |

### 22.5.2 Fallback 分析工具

```python
#!/usr/bin/env python3
# fallback_analyzer.py

import json
import sys
from collections import Counter

def analyze_fallback(trace_file):
    with open(trace_file) as f:
        trace = json.load(f)
    
    fallback_ops = []
    total_ops = 0
    
    for stage in trace['stages']:
        for op in stage['operators']:
            total_ops += 1
            if op.get('fallback', False):
                fallback_ops.append({
                    'operator': op['operator'],
                    'reason': op.get('fallback_reason', 'Unknown')
                })
    
    # 统计 Fallback 原因
    reasons = Counter([op['reason'] for op in fallback_ops])
    
    print(f"Total Operators: {total_ops}")
    print(f"Fallback Operators: {len(fallback_ops)} ({len(fallback_ops)/total_ops*100:.1f}%)")
    print("\nFallback Reasons:")
    for reason, count in reasons.most_common():
        print(f"  - {reason}: {count} ({count/len(fallback_ops)*100:.1f}%)")
    
    print("\nFallback Operators:")
    for op in fallback_ops:
        print(f"  - {op['operator']}: {op['reason']}")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: python fallback_analyzer.py <trace_file.json>")
        sys.exit(1)
    
    analyze_fallback(sys.argv[1])
```

### 22.5.3 减少 Fallback 策略

```scala
// 策略1：使用等价的内置函数
// 不要用：
df.withColumn("upper_name", udf((s: String) => s.toUpperCase)(col("name")))
// 改用：
df.withColumn("upper_name", upper(col("name")))

// 策略2：分解复杂查询
// 原始（复杂窗口 + explode 导致 Fallback）：
val result = df
    .withColumn("prev_value", lag("value", 1).over(window))
    .withColumn("items", explode(col("item_array")))

// 分解：
val step1 = df.select("id", "value", "item_array")  // Gluten
val step2 = step1.withColumn("items", explode(col("item_array")))  // Fallback (explode)
val step3 = step2.withColumn("prev_value", lag("value", 1).over(window))  // Fallback (lag)

// 优化：分别处理
val withoutWindow = df.withColumn("items", explode(col("item_array")))  // Fallback 一次
val final = withoutWindow  // 后续都是 Native

// 策略3：使用最新版本 Gluten
// 新版本持续增加算子支持
```

## 22.6 故障排查工具箱

### 22.6.1 诊断脚本

```bash
#!/bin/bash
# gluten_diagnose.sh - Gluten 健康检查脚本

echo "=== Gluten Diagnosis ==="
echo

# 1. 检查环境
echo "1. Environment Check"
echo "Spark Version:"
spark-submit --version | grep "version"
echo "Java Version:"
java -version 2>&1 | grep "version"
echo "OS:"
uname -a
echo

# 2. 检查 Gluten 安装
echo "2. Gluten Installation"
if [ -f "$SPARK_HOME/jars/gluten-velox-bundle.jar" ]; then
    echo "✓ Gluten JAR found"
    jar tf $SPARK_HOME/jars/gluten-velox-bundle.jar | grep "libgluten" | head -3
else
    echo "✗ Gluten JAR not found"
fi
echo

# 3. 检查 Native 库
echo "3. Native Libraries"
find /opt/gluten -name "*.so" 2>/dev/null || echo "No native libs in /opt/gluten"
echo

# 4. 检查配置
echo "4. Configuration"
grep -E "gluten|offHeap" $SPARK_HOME/conf/spark-defaults.conf 2>/dev/null || echo "No Gluten config"
echo

# 5. 检查资源
echo "5. Resource Check"
echo "Memory:"
free -h
echo "Disk:"
df -h | grep -E "/$|/tmp|/mnt"
echo "CPU:"
lscpu | grep -E "^CPU\(s\)|^Model name"
echo

# 6. 检查日志
echo "6. Recent Errors"
tail -100 $SPARK_HOME/logs/spark-executor-*.log 2>/dev/null | grep -i error | tail -5
echo

echo "=== Diagnosis Complete ==="
```

### 22.6.2 性能基准测试

```python
# benchmark.py - 快速性能测试
from pyspark.sql import SparkSession
import time

def benchmark_query(spark, query, iterations=3):
    times = []
    for i in range(iterations):
        start = time.time()
        spark.sql(query).collect()
        elapsed = time.time() - start
        times.append(elapsed)
        print(f"  Iteration {i+1}: {elapsed:.2f}s")
    
    avg = sum(times) / len(times)
    return avg

# 测试 Vanilla Spark
spark_vanilla = SparkSession.builder \
    .appName("Vanilla") \
    .config("spark.plugins", "") \
    .getOrCreate()

# 测试 Gluten
spark_gluten = SparkSession.builder \
    .appName("Gluten") \
    .config("spark.plugins", "org.apache.gluten.GlutenPlugin") \
    .config("spark.gluten.sql.columnar.backend.lib", "velox") \
    .getOrCreate()

# 准备测试数据
spark_vanilla.range(10000000).createOrReplaceTempView("test")

query = """
SELECT 
    id % 100 as key,
    COUNT(*) as cnt,
    AVG(id) as avg_id
FROM test
GROUP BY id % 100
ORDER BY cnt DESC
"""

print("Vanilla Spark:")
vanilla_time = benchmark_query(spark_vanilla, query)

print("\nGluten:")
gluten_time = benchmark_query(spark_gluten, query)

print(f"\nSpeedup: {vanilla_time / gluten_time:.2f}x")
```

### 22.6.3 监控告警配置

```yaml
# prometheus_alerts.yml
groups:
  - name: gluten_alerts
    rules:
      # Fallback 率过高
      - alert: HighFallbackRate
        expr: rate(gluten_fallback_count[5m]) / rate(gluten_total_operators[5m]) > 0.3
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "High Gluten fallback rate detected"
          description: "Fallback rate is {{ $value | humanizePercentage }}"
      
      # Native 内存使用率高
      - alert: HighNativeMemoryUsage
        expr: gluten_native_memory_used / gluten_native_memory_total > 0.9
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "Native memory usage is high"
          description: "Native memory usage: {{ $value | humanizePercentage }}"
      
      # 任务失败率高
      - alert: HighTaskFailureRate
        expr: rate(spark_executor_failed_tasks[5m]) > 0.1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High task failure rate"
          description: "{{ $value }} tasks/sec are failing"
```

## 本章小结

本章介绍了 Gluten 常见故障的诊断和解决方法：

1. **启动失败**：Native 库加载、版本不匹配
2. **性能下降**：数据倾斜、Fallback、配置不当
3. **内存问题**：JVM OOM、Off-Heap OOM、内存泄漏
4. **兼容性**：Spark/Hadoop 版本、数据源兼容
5. **Fallback 分析**：原因分类、减少策略
6. **工具箱**：诊断脚本、基准测试、监控告警

通过本章学习，你应该能够：
- ✅ 快速定位 Gluten 相关问题
- ✅ 使用系统化方法排查故障
- ✅ 解决常见的性能和稳定性问题
- ✅ 使用工具进行诊断和监控

至此，Part 5（实战篇）全部完成！

## 参考资料

1. Gluten Troubleshooting Guide：https://github.com/apache/incubator-gluten/blob/main/docs/TROUBLESHOOTING.md
2. Spark Performance Tuning：https://spark.apache.org/docs/latest/sql-performance-tuning.html
3. Valgrind Manual：https://valgrind.org/docs/manual/manual.html
4. Java Memory Management：https://docs.oracle.com/javase/8/docs/technotes/guides/vm/gctuning/
5. Prometheus Alerting：https://prometheus.io/docs/alerting/latest/
