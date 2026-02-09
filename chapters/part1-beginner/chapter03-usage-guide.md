# 第3章：Gluten 使用指南

> **本章要点**：
> - 掌握 Gluten 支持的 Spark 版本和功能特性
> - 深入理解核心配置参数及其影响
> - 学习内存管理和 Columnar Shuffle 的配置技巧
> - 理解 Fallback 机制并学会优化
> - 掌握性能调优的最佳实践
> - 学会使用监控指标和 Gluten UI

## 引言

在上一章我们成功运行了第一个 Gluten 程序，本章将深入学习如何在实际项目中使用 Gluten。我们将从配置、调优到监控，全方位掌握 Gluten 的使用方法，让你能够在生产环境中充分发挥 Gluten 的性能优势。

## 3.1 Gluten 支持的 Spark 版本

### 3.1.1 官方支持的版本

Gluten 积极跟进 Spark 社区，目前支持以下版本：

| Spark 版本 | Scala 版本 | 支持状态 | 推荐场景 |
|-----------|-----------|---------|---------|
| 3.2.2 | 2.12 | ✅ 稳定支持 | 稳定性优先的生产环境 |
| 3.3.1 | 2.12 | ✅ 稳定支持 | 平衡性能和稳定性 |
| 3.4.4 | 2.12 | ✅ 稳定支持 | 推荐使用（最佳性能） |
| 3.5.5 | 2.12 | ✅ 稳定支持 | 最新特性 |

### 3.1.2 版本选择建议

**新项目推荐**：Spark 3.4.4 或 3.5.5
- 性能最优
- 支持最新 Spark 特性
- Gluten 适配最完善

**已有项目迁移**：
- 如果使用 Spark 3.2.x → 继续使用，无需升级
- 如果使用 Spark 3.3.x → 考虑升级到 3.4.x 以获得更好性能
- 如果使用 Spark 3.4.x+ → 直接启用 Gluten

### 3.1.3 Shim Layer 机制

Gluten 通过 Shim Layer 支持多个 Spark 版本：

```
Gluten Core
    ↓
Shim Layer (版本适配层)
    ├── Spark 3.2 Shim
    ├── Spark 3.3 Shim
    ├── Spark 3.4 Shim
    └── Spark 3.5 Shim
```

**优势**：
- 单一代码库支持多版本
- 平滑迁移
- 向后兼容

### 3.1.4 后端支持情况

#### Velox 后端

| 功能类别 | 支持程度 |
|---------|---------|
| 基础算子 (Filter, Project, Aggregate) | 100% |
| Join 算子 (Hash, Sort-Merge, Broadcast) | 100% |
| Scalar Functions | ~80% |
| Aggregate Functions | ~90% |
| Window Functions | ~70% |
| 文件格式 (Parquet, ORC) | 100% |
| 数据源 (HDFS, S3, ABFS, GCS) | 100% |

完整列表：[Velox 函数支持列表](https://github.com/apache/incubator-gluten/blob/main/docs/velox-backend-scalar-function-support.md)

#### ClickHouse 后端

| 功能类别 | 支持程度 |
|---------|---------|
| 基础算子 | ~95% |
| Join 算子 | ~90% |
| Functions | ~85% |
| 文件格式 | Parquet 优先 |

## 3.2 核心配置参数详解

### 3.2.1 必需配置

以下配置是启用 Gluten 的最小集合：

```properties
# 1. 插件加载（必需）
spark.plugins=org.apache.gluten.GlutenPlugin

# 2. Off-Heap 内存（必需）
spark.memory.offHeap.enabled=true
spark.memory.offHeap.size=20g

# 3. Columnar Shuffle（强烈推荐）
spark.shuffle.manager=org.apache.spark.shuffle.sort.ColumnarShuffleManager

# 4. ClassPath 配置（必需）
spark.driver.extraClassPath=/path/to/gluten-jar
spark.executor.extraClassPath=/path/to/gluten-jar
```

### 3.2.2 Gluten 核心参数

#### 启用/禁用控制

```properties
# 全局开关（默认 true）
spark.gluten.enabled=true

# 是否从 JAR 加载动态库（默认 false）
spark.gluten.loadLibFromJar=false

# 后端类型（自动识别，无需配置）
# 由 JAR 文件名决定：velox 或 clickhouse
```

#### 表达式黑名单

```properties
# 跳过转换的表达式（逗号分隔）
spark.gluten.expression.blacklist=regexp_extract,from_json

# 示例：禁用某些不稳定的函数
spark.gluten.expression.blacklist=get_json_object,regexp_replace
```

### 3.2.3 算子启用开关

Gluten 允许精细控制每个算子的启用：

```properties
# 启用/禁用各类算子（默认都是 true）

# 扫描算子
spark.gluten.sql.columnar.filescan=true
spark.gluten.sql.columnar.batchscan=true
spark.gluten.sql.columnar.hivetablescan=true

# 计算算子
spark.gluten.sql.columnar.filter=true
spark.gluten.sql.columnar.project=true
spark.gluten.sql.columnar.hashagg=true
spark.gluten.sql.columnar.sortagg=true

# Join 算子
spark.gluten.sql.columnar.broadcastJoin=true
spark.gluten.sql.columnar.sortMergeJoin=true
spark.gluten.sql.columnar.shuffledHashJoin=true

# Shuffle 相关
spark.gluten.sql.columnar.broadcastExchange=true
spark.gluten.sql.columnar.shuffledColumnarExchange=true

# 其他算子
spark.gluten.sql.columnar.expand=true
spark.gluten.sql.columnar.generate=true
spark.gluten.sql.columnar.window=true
spark.gluten.sql.columnar.limit=true
spark.gluten.sql.columnar.sort=true
```

**使用场景**：
- 调试特定算子问题
- 逐步验证功能
- 回避已知 Bug

**示例：仅启用 Filter 和 Project**
```properties
spark.gluten.sql.columnar.filter=true
spark.gluten.sql.columnar.project=true
spark.gluten.sql.columnar.hashagg=false
spark.gluten.sql.columnar.broadcastJoin=false
# ... 其他设为 false
```

## 3.3 内存管理配置

内存配置是 Gluten 性能的关键。

### 3.3.1 Off-Heap 内存基础

```properties
# 启用 Off-Heap（必需）
spark.memory.offHeap.enabled=true

# Off-Heap 内存大小（根据实际调整）
spark.memory.offHeap.size=20g
```

#### 容量规划

**推荐公式**：

```
Off-Heap Size = Executor Memory × (60% ~ 70%)

示例：
- Executor Memory = 32G
- Off-Heap Size = 20G (62.5%)
```

**规划表**：

| Executor Memory | Off-Heap Size (60%) | Off-Heap Size (70%) |
|----------------|---------------------|---------------------|
| 16 GB | 10 GB | 11 GB |
| 32 GB | 19 GB | 22 GB |
| 64 GB | 38 GB | 45 GB |
| 128 GB | 77 GB | 90 GB |

### 3.3.2 内存隔离模式

```properties
# 启用内存隔离（多查询并发时推荐）
spark.gluten.memory.isolation=true
```

**工作原理**：
```
不启用隔离：
所有任务共享 Off-Heap 内存，可能导致某个任务 OOM

启用隔离：
每个任务最大内存 = Off-Heap Size / Task Slots
例如：20GB / 4 cores = 5GB per task
```

**何时启用**：
- ✅ 多查询并发执行
- ✅ 存在长尾任务
- ✅ 经常出现 OOM
- ❌ 单查询独占资源（浪费内存）

### 3.3.3 内存预留和过度分配

```properties
# 内存预留块大小（默认 8MB）
spark.gluten.memory.reservationBlockSize=8MB

# 内存过度分配比例（默认 0.3）
spark.gluten.memory.overAcquiredMemoryRatio=0.3
```

**过度分配机制**：
```
实际可用 = Off-Heap Size × (1 + overAcquiredMemoryRatio)
例如：20GB × 1.3 = 26GB

目的：避免轻微超限导致的 OOM
```

### 3.3.4 Velox 内存配置

Velox 后端的高级内存参数：

```properties
# 初始容量（每个 Query Pool）
spark.gluten.sql.columnar.backend.velox.memInitCapacity=8MB

# 是否允许跨任务内存转移
spark.gluten.sql.columnar.backend.velox.memoryPoolCapacityTransferAcrossTasks=true

# 使用 Huge Pages（高级特性）
spark.gluten.sql.columnar.backend.velox.memoryUseHugePages=false
```

#### Spill 配置

当内存不足时，Velox 会 spill 到磁盘：

```properties
# Spill 策略（auto: 自动，none: 禁用）
spark.gluten.sql.columnar.backend.velox.spillStrategy=auto

# Spill 文件系统（local 或 heap-over-local）
spark.gluten.sql.columnar.backend.velox.spillFileSystem=local

# 单个 Spill 文件最大大小
spark.gluten.sql.columnar.backend.velox.maxSpillFileSize=1GB

# 查询最大 Spill 总量
spark.gluten.sql.columnar.backend.velox.maxSpillBytes=100GB

# Spill 层级（嵌套 Spill 的次数）
spark.gluten.sql.columnar.backend.velox.maxSpillLevel=4
```

**Spill 调优建议**：
1. 优先增大内存，减少 Spill
2. 使用 SSD 存储 Spill 文件
3. 监控 Spill 指标，调整分区数

### 3.3.5 内存监控

检查内存使用情况：

```scala
// 在 Spark Shell 中
spark.sparkContext.statusTracker.getExecutorInfos.foreach { info =>
  println(s"Executor ${info.host}: Max Memory = ${info.totalOffHeapStorageMemory}")
}

// 查看任务级别内存
val metrics = spark.sparkContext.statusTracker
  .getStageInfo(stageId)
  .get
  .taskMetrics
println(s"Peak Memory: ${metrics.peakExecutionMemory}")
```

## 3.4 Columnar Shuffle 配置

### 3.4.1 启用 Columnar Shuffle

```properties
# Shuffle Manager（必需）
spark.shuffle.manager=org.apache.spark.shuffle.sort.ColumnarShuffleManager

# Shuffle 写缓冲区大小（可选）
spark.gluten.shuffleWriter.bufferSize=4m
```

### 3.4.2 Shuffle 优化参数

```properties
# Shuffle 分区数（影响并行度）
spark.sql.shuffle.partitions=200

# AQE 自动分区合并
spark.sql.adaptive.enabled=true
spark.sql.adaptive.coalescePartitions.enabled=true
spark.sql.adaptive.coalescePartitions.minPartitionSize=1MB
spark.sql.adaptive.coalescePartitions.initialPartitionNum=200

# Shuffle 压缩（默认启用）
spark.sql.shuffle.enableColumnarCompression=true
```

### 3.4.3 Batch 大小调整

```properties
# Shuffle 输入 Batch 合并
spark.gluten.sql.columnar.backend.velox.resizeBatches.shuffleInput=true
spark.gluten.sql.columnar.backend.velox.resizeBatches.shuffleInput.minSize=1024

# Shuffle 输出 Batch 合并
spark.gluten.sql.columnar.backend.velox.resizeBatches.shuffleOutput=false
```

**效果**：
- 减少小 Batch 数量
- 降低网络传输开销
- 提升 Shuffle 性能

### 3.4.4 远程 Shuffle 服务

Gluten 支持 Celeborn 和 Uniffle 等远程 Shuffle 服务：

#### Celeborn 配置

```properties
# 1. 编译时启用 Celeborn
# mvn clean package -Pbackends-velox -Pspark-3.3 -Pceleborn

# 2. 配置 Celeborn 客户端
spark.shuffle.manager=org.apache.spark.shuffle.celeborn.SparkShuffleManager
spark.celeborn.master.endpoints=clb-master:9097
spark.celeborn.shuffle.enabled=true

# 3. 性能优化
spark.celeborn.push.replicate.enabled=true
spark.celeborn.push.buffer.size=256k
```

#### Uniffle 配置

```properties
spark.shuffle.manager=org.apache.spark.shuffle.RssShuffleManager
spark.rss.coordinator.quorum=uniffle-coordinator:19999
```

**远程 Shuffle 的优势**：
- 解耦计算和 Shuffle
- 提高稳定性
- 减少磁盘 IO
- 支持动态资源调整

## 3.5 Fallback 机制理解

### 3.5.1 什么是 Fallback

Fallback 是 Gluten 的重要机制：当某个算子无法在原生引擎执行时，自动回退到 Spark 执行。

```
查询计划：
Filter (支持) → Project (支持) → Aggregate (不支持)
                                        ↓
                                    Fallback
                                        ↓
                         ColumnarToRow → Spark Aggregate
```

### 3.5.2 Fallback 触发条件

以下情况会触发 Fallback：

1. **不支持的函数**：
   ```sql
   SELECT regexp_replace(name, 'pattern', 'replacement') FROM table
   -- 如果 regexp_replace 不支持，会 Fallback
   ```

2. **不支持的数据类型**：
   ```sql
   SELECT map_keys(col) FROM table
   -- 某些复杂类型可能不支持
   ```

3. **不支持的算子组合**：
   ```sql
   SELECT * FROM table1
   FULL OUTER JOIN table2 ON ...
   -- 某些 Join 类型可能不支持
   ```

4. **超过资源限制**：
   ```sql
   -- 聚合键数量过多
   GROUP BY col1, col2, ..., col50
   ```

### 3.5.3 Fallback 的影响

**性能影响**：

```mermaid
graph LR
    A[Velox Data] -->|ColumnarToRow| B[Row Format]
    B -->|Spark Operator| C[Row Result]
    C -->|RowToColumnar| D[Columnar Data]
```

每次 Fallback 涉及：
- ColumnarToRow (C2R) 转换
- RowToColumnar (R2C) 转换
- 数据序列化/反序列化开销

**开销示例**：
- 单次转换：5-10% 性能损失
- 多次转换：累积可达 30-50%

### 3.5.4 Fallback 配置

```properties
# 是否偏好 Columnar 计划（推荐 true）
spark.gluten.sql.columnar.fallback.preferColumnar=true

# 计算 Fallback 时忽略 RowToColumnar
spark.gluten.sql.columnar.fallback.ignoreRowToColumnar=true

# 查询级别 Fallback 阈值（-1 表示不限制）
spark.gluten.sql.columnar.query.fallback.threshold=-1

# 表达式 Fallback 阈值（嵌套表达式数量）
spark.gluten.sql.columnar.fallback.expressions.threshold=50
```

### 3.5.5 查看 Fallback 信息

**方法 1：查看执行计划**

```scala
df.explain(mode = "formatted")

// 查找 ColumnarToRow 和 RowToColumnar
// 它们标志着 Fallback 点
```

**方法 2：使用 Gluten UI**

访问 Spark UI → Gluten SQL 标签页：
- 查看 Fallback Summary
- 查看具体的 Fallback 算子

![Gluten UI Fallback](../../images/gluten-ui-fallback.png)

**方法 3：启用事件日志**

```properties
spark.gluten.ui.enabled=true
```

Gluten 会发送 `GlutenPlanFallbackEvent`，包含：
- Fallback 的算子类型
- Fallback 原因
- 涉及的表达式

### 3.5.6 减少 Fallback 的策略

#### 策略 1：使用支持的函数

```sql
-- ❌ 不支持的函数
SELECT from_json(json_col, schema) FROM table

-- ✅ 使用支持的替代方案
SELECT get_json_object(json_col, '$.field') FROM table
```

查看支持列表：
- [Velox Scalar Functions](https://github.com/apache/incubator-gluten/blob/main/docs/velox-backend-scalar-function-support.md)
- [Velox Aggregate Functions](https://github.com/apache/incubator-gluten/blob/main/docs/velox-backend-aggregate-function-support.md)

#### 策略 2：简化查询

```sql
-- ❌ 复杂的嵌套表达式
SELECT 
    CASE 
        WHEN condition1 THEN expression1
        WHEN condition2 THEN expression2
        ... (50+ branches)
    END
FROM table

-- ✅ 拆分为多个步骤
WITH step1 AS (
    SELECT *, simple_expression1 AS result1 FROM table
),
step2 AS (
    SELECT *, simple_expression2 AS result2 FROM step1
)
SELECT * FROM step2
```

#### 策略 3：调整数据类型

```sql
-- ❌ 使用复杂类型
SELECT map_values(complex_map) FROM table

-- ✅ 使用基础类型
SELECT array_col[0], array_col[1] FROM table
```

#### 策略 4：启用部分投影

```properties
# 将不支持的表达式单独处理
spark.gluten.sql.columnar.partial.project=true
```

**效果**：
```
原计划（全部 Fallback）：
Project[expr1, expr2_unsupported, expr3]
  → Fallback 全部

优化后（部分 Fallback）：
Project[expr2_unsupported]  ← Fallback
  ↓
Project[expr1, expr3]  ← Gluten
```

## 3.6 性能调优最佳实践

### 3.6.1 基础调优

#### 1. 合理设置并行度

```properties
# Executor 配置
spark.executor.instances=10
spark.executor.cores=8
spark.executor.memory=32g

# 动态资源分配（推荐）
spark.dynamicAllocation.enabled=true
spark.dynamicAllocation.minExecutors=5
spark.dynamicAllocation.maxExecutors=50
```

#### 2. 启用 AQE

```properties
# Adaptive Query Execution
spark.sql.adaptive.enabled=true
spark.sql.adaptive.coalescePartitions.enabled=true
spark.sql.adaptive.skewJoin.enabled=true
spark.sql.adaptive.localShuffleReader.enabled=true
```

#### 3. 合理设置 Batch 大小

```properties
# Columnar Batch 大小（默认 4096）
spark.gluten.sql.columnar.maxBatchSize=4096

# 根据数据特点调整：
# - 宽表（列多）：减小 batch size（2048）
# - 窄表（列少）：增大 batch size（8192）
```

### 3.6.2 文件读取优化

#### Parquet 优化

```properties
# Parquet 向量化读取
spark.sql.parquet.enableVectorizedReader=true

# 列式批次大小
spark.sql.parquet.columnarReaderBatchSize=4096

# Velox 特定配置
spark.gluten.sql.columnar.backend.velox.maxCoalescedBytes=64MB
spark.gluten.sql.columnar.backend.velox.maxCoalescedDistance=512KB
spark.gluten.sql.columnar.backend.velox.prefetchRowGroups=1
```

#### 文件合并

```properties
# 小文件合并
spark.sql.files.maxPartitionBytes=128MB
spark.sql.files.openCostInBytes=4MB

# Velox 加载量子
spark.gluten.sql.columnar.backend.velox.loadQuantum=256MB
```

### 3.6.3 Join 优化

```properties
# Broadcast Join 阈值
spark.sql.autoBroadcastJoinThreshold=10MB

# Sort-Merge Join 缓冲区
spark.sql.sortMergeJoin.buffer.size=4096

# 强制使用 Shuffled Hash Join
spark.gluten.sql.columnar.forceShuffledHashJoin=true
```

**Join 策略选择**：

| Join 类型 | 适用场景 | 配置 |
|----------|---------|------|
| Broadcast Join | 小表 (< 10MB) | 自动触发 |
| Shuffled Hash Join | 一侧数据倾斜 | forceShuffledHashJoin=true |
| Sort-Merge Join | 两侧都很大 | 默认 |

### 3.6.4 聚合优化

```properties
# 使用 Hash Aggregation
spark.gluten.sql.columnar.force.hashagg=true

# Flushable Partial Aggregation（Velox）
spark.gluten.sql.columnar.backend.velox.flushablePartialAggregation=true
spark.gluten.sql.columnar.backend.velox.maxPartialAggregationMemoryRatio=0.1
spark.gluten.sql.columnar.backend.velox.abandonPartialAggregationMinRows=100000
```

**Flushable Aggregation 原理**：
```
传统聚合：
- 内存中保持所有分组
- 内存不足时失败

Flushable 聚合：
- 内存满时输出中间结果
- 避免 OOM
- 支持超大基数聚合
```

### 3.6.5 缓存策略

#### Velox Local Cache

```properties
# 启用缓存
spark.gluten.sql.columnar.backend.velox.cacheEnabled=true

# 内存缓存大小
spark.gluten.sql.columnar.backend.velox.memCacheSize=1GB

# SSD 缓存配置
spark.gluten.sql.columnar.backend.velox.ssdCacheSize=100GB
spark.gluten.sql.columnar.backend.velox.ssdCachePath=/mnt/ssd/cache
spark.gluten.sql.columnar.backend.velox.ssdCacheShards=4
```

**缓存适用场景**：
- 重复查询相同数据
- 数据热点明显
- 远程存储延迟高

#### Soft Affinity

```properties
# 启用 Soft Affinity
spark.gluten.soft-affinity.enabled=true
spark.gluten.soft-affinity.replications.num=2

# 重复读取检测
spark.gluten.soft-affinity.duplicateReadingDetect.enabled=true
```

**工作原理**：
- 记录文件分片的处理位置
- 后续查询优先调度到相同 Executor
- 利用 Velox Cache 提升性能

### 3.6.6 调优检查清单

**内存**：
- [ ] Off-Heap 大小合理（60-70% of executor memory）
- [ ] 启用内存隔离（多查询并发）
- [ ] 监控 Spill 情况

**Shuffle**：
- [ ] 使用 Columnar Shuffle
- [ ] 分区数合理（200-2000）
- [ ] 启用 AQE

**Fallback**：
- [ ] Fallback 比例 < 10%
- [ ] 关键路径无 Fallback
- [ ] 使用 Gluten UI 监控

**文件**：
- [ ] 文件大小合理（128MB-1GB）
- [ ] 使用 Parquet 格式
- [ ] 列裁剪和谓词下推

## 3.7 监控和指标（Gluten UI）

### 3.7.1 启用 Gluten UI

```properties
# 启用 Gluten UI（默认启用）
spark.gluten.ui.enabled=true

# 禁用（如果不需要）
spark.gluten.ui.enabled=false
```

### 3.7.2 Gluten UI 功能

访问 Spark UI → **Gluten SQL / DataFrame** 标签页

#### 功能 1：构建信息

显示 Gluten 的构建和运行环境：

```
Gluten Version: 1.2.0
Backend: velox
Backend Revision: abc123
Spark Version: 3.3.1
Scala Version: 2.12
Java Version: 1.8.0_XXX
GCC Version: 9.4.0
Hadoop Version: 3.3.1
```

**用途**：
- 确认版本匹配
- 排查兼容性问题
- 记录环境信息

#### 功能 2：Fallback 信息

每个查询的 Fallback 统计：

```
Query ID: 0
Duration: 12.5s
Status: Success
Fallback Nodes: 2
Fallback Reasons:
  - UnsupportedExpression: regexp_extract
  - UnsupportedDataType: map<string,int>
```

**信息包括**：
- Fallback 算子数量
- 具体原因
- 涉及的表达式/数据类型

### 3.7.3 History Server 支持

Gluten UI 也支持 History Server：

```bash
# 1. 复制 gluten-ui JAR 到 History Server
cp gluten-ui*.jar $SPARK_HOME/jars/

# 2. 重启 History Server
$SPARK_HOME/sbin/stop-history-server.sh
$SPARK_HOME/sbin/start-history-server.sh

# 3. 访问历史任务，Gluten 标签页仍然可用
```

### 3.7.4 自定义事件监听

开发者可以注册 SparkListener 处理 Gluten 事件：

```scala
import org.apache.spark.scheduler.{SparkListener, SparkListenerEvent}
import org.apache.gluten.events.{GlutenBuildInfoEvent, GlutenPlanFallbackEvent}

class MyGlutenListener extends SparkListener {
  override def onOtherEvent(event: SparkListenerEvent): Unit = {
    event match {
      case e: GlutenBuildInfoEvent =>
        println(s"Gluten Version: ${e.glutenVersion}")
        println(s"Backend: ${e.backend}")
        
      case e: GlutenPlanFallbackEvent =>
        println(s"Query ${e.queryId} has ${e.fallbackNodeCount} fallback nodes")
        e.fallbackSummary.foreach { summary =>
          println(s"  - ${summary.node}: ${summary.reason}")
        }
        
      case _ => // 其他事件
    }
  }
}

// 注册监听器
spark.sparkContext.addSparkListener(new MyGlutenListener())
```

### 3.7.5 指标收集

**重要指标**：

1. **执行时间**：
   ```scala
   val df = spark.sql("SELECT ...")
   val start = System.currentTimeMillis()
   df.count()
   val duration = System.currentTimeMillis() - start
   println(s"Duration: $duration ms")
   ```

2. **Fallback 比例**：
   ```
   Fallback Rate = Fallback Nodes / Total Nodes
   目标: < 10%
   ```

3. **内存使用**：
   ```
   Peak Memory Usage per Task
   Spill To Disk (次数和大小)
   ```

4. **Shuffle 数据量**：
   ```
   Shuffle Read/Write Bytes
   Shuffle Read/Write Records
   ```

### 3.7.6 监控最佳实践

1. **定期检查 Gluten UI**：
   - 每周查看 Fallback 趋势
   - 识别高 Fallback 查询

2. **设置告警**：
   ```scala
   if (fallbackRate > 0.2) {
     // 发送告警
     sendAlert(s"High fallback rate: $fallbackRate")
   }
   ```

3. **性能基线**：
   ```
   建立性能基线（无 Gluten）
   对比 Gluten 性能
   持续跟踪改进
   ```

4. **A/B 测试**：
   ```properties
   # 组 A：使用 Gluten
   spark.plugins=org.apache.gluten.GlutenPlugin
   
   # 组 B：不使用 Gluten
   spark.plugins=
   ```

## 本章小结

通过本章的学习，你应该已经：

1. ✅ **版本支持**：了解 Gluten 支持的 Spark 版本和功能特性
2. ✅ **核心配置**：掌握必需配置和可选配置的使用
3. ✅ **内存管理**：理解 Off-Heap 内存配置和优化技巧
4. ✅ **Columnar Shuffle**：学会配置和优化 Shuffle 性能
5. ✅ **Fallback 机制**：理解 Fallback 原理和减少策略
6. ✅ **性能调优**：掌握多个维度的调优最佳实践
7. ✅ **监控指标**：学会使用 Gluten UI 和自定义监控

**第一部分（入门篇）总结**：

至此，第一部分的三章内容已全部完成：
- **第1章**：理解了 Gluten 是什么以及为什么需要它
- **第2章**：成功搭建环境并运行了第一个示例
- **第3章**：深入学习了配置、调优和监控

现在你已经具备了在生产环境使用 Gluten 的基础知识。下一部分（架构篇）将深入 Gluten 的内部机制，帮助你理解其工作原理，以便进行更高级的优化和问题排查。

## 参考资料

- [Gluten Configuration](https://github.com/apache/incubator-gluten/blob/main/docs/Configuration.md)
- [Velox Configuration](https://github.com/apache/incubator-gluten/blob/main/docs/velox-configuration.md)
- [Velox Function Support](https://github.com/apache/incubator-gluten/blob/main/docs/velox-backend-scalar-function-support.md)
- [Gluten UI](https://github.com/apache/incubator-gluten/blob/main/docs/get-started/GlutenUI.md)
- [Spark Performance Tuning](https://spark.apache.org/docs/latest/tuning.html)

---

**下一章预告**：[第4章：Gluten 整体架构](../part2-architecture/chapter04-overall-architecture.md) - 深入理解 Gluten 的设计原理和核心组件
