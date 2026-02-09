# 附录 A：配置参数参考

本附录提供 Gluten 所有配置参数的完整参考，包括核心配置、Velox 特定配置、ClickHouse 特定配置以及性能调优参数。

## A.1 核心配置

### A.1.1 基础配置

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `spark.plugins` | - | 插件类名，必须设置为 `org.apache.gluten.GlutenPlugin` |
| `spark.gluten.enabled` | `true` | 是否启用 Gluten |
| `spark.gluten.sql.columnar.backend.lib` | `velox` | 后端引擎：`velox` 或 `clickhouse` |
| `spark.gluten.sql.columnar.forceShuffledHashJoin` | `true` | 强制使用 Shuffled Hash Join（Velox 性能更好） |
| `spark.shuffle.manager` | - | 必须设置为 `org.apache.spark.shuffle.sort.ColumnarShuffleManager` |
| `spark.sql.adaptive.enabled` | `true` | 启用 Adaptive Query Execution |
| `spark.sql.adaptive.coalescePartitions.enabled` | `true` | 启用分区合并 |

**示例配置**：

```bash
spark-submit \
  --conf spark.plugins=org.apache.gluten.GlutenPlugin \
  --conf spark.gluten.enabled=true \
  --conf spark.gluten.sql.columnar.backend.lib=velox \
  --conf spark.shuffle.manager=org.apache.spark.shuffle.sort.ColumnarShuffleManager \
  myapp.jar
```

### A.1.2 内存配置

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `spark.memory.offHeap.enabled` | `true` | 启用堆外内存（Gluten 必需） |
| `spark.memory.offHeap.size` | `20g` | 堆外内存大小，建议为 executor 内存的 60-70% |
| `spark.gluten.memory.isolation` | `false` | 内存隔离模式：`true` (隔离) 或 `false` (共享) |
| `spark.gluten.memory.dynamic.offHeap.sizing.enabled` | `false` | 动态调整堆外内存 |
| `spark.gluten.memory.dynamic.offHeap.sizing.memory.fraction` | `0.6` | 动态内存比例 |
| `spark.gluten.memory.reservation.block.size` | `8388608` | 内存预留块大小（8MB） |
| `spark.gluten.memory.overAcquire.enabled` | `false` | 允许过度申请内存 |

**推荐配置（100G executor）**：

```properties
spark.executor.memory=100g
spark.memory.offHeap.enabled=true
spark.memory.offHeap.size=70g
spark.executor.memoryOverhead=10g
spark.gluten.memory.isolation=false
```

### A.1.3 Shuffle 配置

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `spark.shuffle.manager` | - | Columnar Shuffle Manager |
| `spark.gluten.sql.columnar.shuffle.codec` | `lz4` | Shuffle 压缩算法：`lz4`, `zstd`, `snappy` |
| `spark.gluten.sql.columnar.shuffle.codecBackend` | - | 压缩后端：`qat`, `iaa` |
| `spark.gluten.sql.columnar.shuffle.customizedCompression.enabled` | `false` | 启用自定义压缩 |
| `spark.gluten.sql.columnar.shuffle.writeBufferSize` | `4194304` | Shuffle 写缓冲区大小（4MB） |
| `spark.gluten.sql.columnar.shuffle.realloc.threshold` | `0.25` | 缓冲区重新分配阈值 |
| `spark.gluten.shuffle.split.into.batches` | `true` | 将 Shuffle 数据分批处理 |

**高性能配置**：

```properties
spark.shuffle.manager=org.apache.spark.shuffle.sort.ColumnarShuffleManager
spark.gluten.sql.columnar.shuffle.codec=lz4
spark.gluten.sql.columnar.shuffle.writeBufferSize=8388608
spark.gluten.shuffle.split.into.batches=true
```

### A.1.4 Fallback 配置

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `spark.gluten.sql.columnar.forceToFallback` | `false` | 强制 Fallback（用于调试） |
| `spark.gluten.sql.columnar.fallback.enabled` | `true` | 启用自动 Fallback |
| `spark.gluten.sql.columnar.fallback.preferColumnar` | `true` | 优先使用 Columnar 格式 |
| `spark.gluten.sql.columnar.fallback.expressions.whitelist` | - | Fallback 白名单表达式 |
| `spark.gluten.sql.columnar.fallback.expressions.blacklist` | - | Fallback 黑名单表达式 |
| `spark.gluten.sql.columnar.fallback.ignoreCorrupted` | `false` | 忽略损坏数据 |

**调试配置**：

```properties
# 查看 Fallback 原因
spark.gluten.sql.columnar.fallback.enabled=true
spark.gluten.sql.columnar.fallback.reporter.enabled=true

# 禁用特定算子的 Native 执行
spark.gluten.sql.columnar.fallback.expressions.blacklist=SortMergeJoin
```

## A.2 Velox 特定配置

### A.2.1 基础配置

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `spark.gluten.sql.columnar.backend.velox.driver` | `true` | 在 Driver 端启用 Velox |
| `spark.gluten.sql.columnar.backend.velox.glog.severity` | `1` | 日志级别：0=INFO, 1=WARNING, 2=ERROR |
| `spark.gluten.sql.columnar.backend.velox.glog.v` | `0` | 详细日志级别（0-3） |
| `spark.gluten.sql.columnar.backend.velox.listenerBus.threadCount` | `10` | 监听器总线线程数 |

### A.2.2 内存管理

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `spark.gluten.sql.columnar.backend.velox.memoryUseHugePages` | `false` | 使用大页内存 |
| `spark.gluten.sql.columnar.backend.velox.cacheEnabled` | `false` | 启用 Velox 缓存 |
| `spark.gluten.sql.columnar.backend.velox.cacheSizeInMB` | `1024` | 缓存大小（MB） |
| `spark.gluten.sql.columnar.backend.velox.IOThreads` | `10` | I/O 线程数 |
| `spark.gluten.sql.columnar.backend.velox.spillStrategy` | `auto` | Spill 策略：`auto`, `orderBySpill`, `rowNumber` |
| `spark.gluten.sql.columnar.backend.velox.maxSpillFileSize` | `1073741824` | 最大 Spill 文件大小（1GB） |
| `spark.gluten.sql.columnar.backend.velox.spillFilePrefix` | `/tmp` | Spill 文件前缀 |

**推荐配置**：

```properties
spark.gluten.sql.columnar.backend.velox.memoryUseHugePages=true
spark.gluten.sql.columnar.backend.velox.cacheEnabled=true
spark.gluten.sql.columnar.backend.velox.cacheSizeInMB=4096
spark.gluten.sql.columnar.backend.velox.IOThreads=20
spark.gluten.sql.columnar.backend.velox.spillStrategy=auto
```

### A.2.3 数据源配置

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `spark.gluten.sql.columnar.backend.velox.parquet.enableBatchRead` | `true` | 启用批量读取 Parquet |
| `spark.gluten.sql.columnar.backend.velox.parquet.batchSize` | `8192` | Parquet 批大小 |
| `spark.gluten.sql.columnar.backend.velox.orc.enableBatchRead` | `true` | 启用批量读取 ORC |
| `spark.gluten.sql.columnar.backend.velox.uriSchemeFsCacheEnabled` | `true` | 启用文件系统缓存 |

### A.2.4 Join 配置

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `spark.gluten.sql.columnar.backend.velox.maxHashBuildTableSize` | `1073741824` | Hash Join Build 表最大大小（1GB） |
| `spark.gluten.sql.columnar.backend.velox.minHashProbeThreads` | `1` | Hash Join Probe 最小线程数 |
| `spark.gluten.sql.columnar.backend.velox.minHashBuildThreads` | `1` | Hash Join Build 最小线程数 |

## A.3 ClickHouse 特定配置

### A.3.1 基础配置

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `spark.gluten.sql.columnar.backend.ch.worker.id` | `0` | ClickHouse Worker ID |
| `spark.gluten.sql.columnar.backend.ch.runtime_settings.local_engine.settings` | - | ClickHouse 设置 JSON |
| `spark.gluten.sql.columnar.backend.ch.runtime_config.logger.level` | `error` | 日志级别：`trace`, `debug`, `information`, `warning`, `error` |

### A.3.2 内存配置

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `spark.gluten.sql.columnar.backend.ch.runtime_settings.max_bytes_before_external_group_by` | `0` | Group By 外部排序阈值（字节） |
| `spark.gluten.sql.columnar.backend.ch.runtime_settings.max_bytes_before_external_sort` | `0` | Sort 外部排序阈值（字节） |
| `spark.gluten.sql.columnar.backend.ch.runtime_settings.spill_to_disk` | `false` | 启用 Spill 到磁盘 |

### A.3.3 读取优化

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `spark.gluten.sql.columnar.backend.ch.runtime_settings.max_block_size` | `8192` | 最大块大小 |
| `spark.gluten.sql.columnar.backend.ch.runtime_settings.max_threads` | `0` | 最大线程数（0=自动） |
| `spark.gluten.sql.columnar.backend.ch.runtime_settings.max_insert_threads` | `0` | 最大插入线程数 |

### A.3.4 Join 配置

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `spark.gluten.sql.columnar.backend.ch.runtime_settings.join_algorithm` | `hash` | Join 算法：`hash`, `partial_merge`, `direct` |
| `spark.gluten.sql.columnar.backend.ch.runtime_settings.max_bytes_in_join` | `0` | Join 最大内存（字节） |
| `spark.gluten.sql.columnar.backend.ch.runtime_settings.partial_merge_join_rows_in_right_blocks` | `65536` | Partial Merge Join 右表行数 |

**示例配置**：

```properties
spark.gluten.sql.columnar.backend.ch.runtime_settings.max_block_size=16384
spark.gluten.sql.columnar.backend.ch.runtime_settings.max_threads=32
spark.gluten.sql.columnar.backend.ch.runtime_settings.join_algorithm=hash
spark.gluten.sql.columnar.backend.ch.runtime_config.logger.level=information
```

## A.4 性能调优配置

### A.4.1 Executor 配置

| 参数 | 推荐值 | 说明 |
|------|--------|------|
| `spark.executor.cores` | `8-16` | Executor 核心数 |
| `spark.executor.memory` | `32g-128g` | Executor 内存 |
| `spark.executor.memoryOverhead` | `0.1 * memory` | 内存开销 |
| `spark.task.cpus` | `1` | 每个任务的 CPU 数 |
| `spark.executor.instances` | 动态 | Executor 实例数 |

**小集群（10 节点）**：

```properties
spark.executor.cores=8
spark.executor.memory=32g
spark.executor.memoryOverhead=4g
spark.memory.offHeap.size=20g
spark.executor.instances=20
```

**大集群（100 节点）**：

```properties
spark.executor.cores=16
spark.executor.memory=128g
spark.executor.memoryOverhead=16g
spark.memory.offHeap.size=90g
spark.executor.instances=200
```

### A.4.2 并行度配置

| 参数 | 推荐值 | 说明 |
|------|--------|------|
| `spark.sql.shuffle.partitions` | `200-2000` | Shuffle 分区数 |
| `spark.default.parallelism` | `cores * executors * 2` | 默认并行度 |
| `spark.sql.adaptive.coalescePartitions.initialPartitionNum` | `1000` | AQE 初始分区数 |
| `spark.sql.adaptive.advisoryPartitionSizeInBytes` | `64MB-256MB` | AQE 建议分区大小 |

**计算公式**：

```bash
# Shuffle 分区数
总核心数 = executor数 × 每个executor的核心数
shuffle_partitions = 总核心数 × 2 到 4

# 示例：100 executors × 16 cores = 1600 cores
spark.sql.shuffle.partitions=3200  # 1600 × 2
```

### A.4.3 文件读取优化

| 参数 | 推荐值 | 说明 |
|------|--------|------|
| `spark.sql.files.maxPartitionBytes` | `128MB-512MB` | 最大分区字节数 |
| `spark.sql.files.openCostInBytes` | `4194304` | 文件打开成本（4MB） |
| `spark.sql.parquet.columnarReaderBatchSize` | `4096-8192` | Parquet 批大小 |
| `spark.sql.inMemoryColumnarStorage.batchSize` | `10000` | 内存列存储批大小 |

### A.4.4 压缩配置

| 参数 | 推荐值 | 说明 |
|------|--------|------|
| `spark.sql.parquet.compression.codec` | `snappy` | Parquet 压缩：`snappy`, `gzip`, `lz4`, `zstd` |
| `spark.sql.orc.compression.codec` | `snappy` | ORC 压缩 |
| `spark.io.compression.codec` | `lz4` | 通用压缩 |
| `spark.gluten.sql.columnar.shuffle.codec` | `lz4` | Shuffle 压缩 |

**压缩算法对比**：

| 算法 | 压缩比 | 速度 | CPU 使用 | 推荐场景 |
|------|--------|------|---------|---------|
| `snappy` | 中 | 很快 | 低 | 默认选择 |
| `lz4` | 中 | 最快 | 很低 | Shuffle, 实时场景 |
| `zstd` | 高 | 快 | 中 | 存储优先 |
| `gzip` | 很高 | 慢 | 高 | 归档数据 |

## A.5 监控配置

### A.5.1 Metrics 配置

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `spark.gluten.sql.columnar.metrics.enabled` | `true` | 启用 Gluten Metrics |
| `spark.gluten.sql.columnar.metrics.level` | `1` | Metrics 级别（0-2） |
| `spark.metrics.conf` | - | Metrics 配置文件路径 |
| `spark.metrics.namespace` | `${spark.app.id}` | Metrics 命名空间 |

**Prometheus 配置**：

```properties
spark.metrics.conf=/path/to/metrics.properties

# metrics.properties 内容：
*.sink.prometheus.class=org.apache.spark.metrics.sink.PrometheusSink
*.sink.prometheus.port=9091
*.source.jvm.class=org.apache.spark.metrics.source.JvmSource
```

### A.5.2 日志配置

| 参数 | 推荐值 | 说明 |
|------|--------|------|
| `spark.gluten.sql.columnar.logLevel` | `WARN` | Gluten 日志级别 |
| `spark.sql.planChangeLog.level` | `WARN` | 执行计划变更日志 |
| `spark.eventLog.enabled` | `true` | 启用事件日志 |
| `spark.eventLog.dir` | `hdfs://...` | 事件日志目录 |

## A.6 高级配置

### A.6.1 代码生成

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `spark.sql.codegen.wholeStage` | `true` | 启用全阶段代码生成 |
| `spark.sql.codegen.maxFields` | `100` | 代码生成最大字段数 |
| `spark.sql.codegen.fallback` | `true` | 代码生成失败时 Fallback |

### A.6.2 Broadcast 配置

| 参数 | 推荐值 | 说明 |
|------|--------|------|
| `spark.sql.autoBroadcastJoinThreshold` | `10MB-50MB` | Broadcast Join 阈值 |
| `spark.sql.broadcastTimeout` | `300s` | Broadcast 超时时间 |

### A.6.3 动态资源分配

| 参数 | 推荐值 | 说明 |
|------|--------|------|
| `spark.dynamicAllocation.enabled` | `true` | 启用动态分配 |
| `spark.dynamicAllocation.minExecutors` | `1` | 最小 Executor 数 |
| `spark.dynamicAllocation.maxExecutors` | `100` | 最大 Executor 数 |
| `spark.dynamicAllocation.initialExecutors` | `10` | 初始 Executor 数 |
| `spark.dynamicAllocation.executorIdleTimeout` | `60s` | Executor 空闲超时 |

## A.7 完整配置示例

### A.7.1 生产环境配置（Velox）

```properties
# 基础配置
spark.plugins=org.apache.gluten.GlutenPlugin
spark.gluten.enabled=true
spark.gluten.sql.columnar.backend.lib=velox
spark.shuffle.manager=org.apache.spark.shuffle.sort.ColumnarShuffleManager

# 内存配置
spark.executor.memory=128g
spark.executor.memoryOverhead=16g
spark.memory.offHeap.enabled=true
spark.memory.offHeap.size=90g
spark.gluten.memory.isolation=false

# Executor 配置
spark.executor.cores=16
spark.executor.instances=100
spark.task.cpus=1

# 并行度配置
spark.sql.shuffle.partitions=3200
spark.default.parallelism=3200
spark.sql.adaptive.enabled=true
spark.sql.adaptive.coalescePartitions.enabled=true
spark.sql.adaptive.advisoryPartitionSizeInBytes=128MB

# Shuffle 配置
spark.gluten.sql.columnar.shuffle.codec=lz4
spark.gluten.sql.columnar.shuffle.writeBufferSize=8388608

# Velox 配置
spark.gluten.sql.columnar.backend.velox.memoryUseHugePages=true
spark.gluten.sql.columnar.backend.velox.cacheEnabled=true
spark.gluten.sql.columnar.backend.velox.cacheSizeInMB=8192
spark.gluten.sql.columnar.backend.velox.IOThreads=32
spark.gluten.sql.columnar.backend.velox.spillStrategy=auto

# 文件读取优化
spark.sql.files.maxPartitionBytes=256MB
spark.sql.parquet.compression.codec=snappy

# 监控配置
spark.eventLog.enabled=true
spark.eventLog.dir=hdfs://namenode:8020/spark-events
spark.metrics.conf=/opt/spark/conf/metrics.properties
```

### A.7.2 开发环境配置

```properties
# 基础配置
spark.plugins=org.apache.gluten.GlutenPlugin
spark.gluten.enabled=true
spark.gluten.sql.columnar.backend.lib=velox
spark.shuffle.manager=org.apache.spark.shuffle.sort.ColumnarShuffleManager

# 小规模配置
spark.executor.memory=8g
spark.memory.offHeap.enabled=true
spark.memory.offHeap.size=5g
spark.executor.cores=4
spark.executor.instances=2

# 调试配置
spark.gluten.sql.columnar.logLevel=INFO
spark.gluten.sql.columnar.backend.velox.glog.severity=0
spark.sql.planChangeLog.level=INFO

# 简化配置
spark.sql.shuffle.partitions=20
spark.default.parallelism=8
```

## A.8 配置优先级

配置优先级从高到低：

1. **代码中设置**：`spark.conf.set(...)`
2. **spark-submit 命令行**：`--conf key=value`
3. **spark-defaults.conf**：配置文件
4. **系统默认值**：Spark 和 Gluten 默认值

**示例**：

```scala
// 优先级最高
spark.conf.set("spark.gluten.enabled", "true")

// 优先级次之
// spark-submit --conf spark.gluten.enabled=true

// 优先级再次
// spark-defaults.conf: spark.gluten.enabled true

// 优先级最低
// 默认值
```

## A.9 配置验证

### A.9.1 检查配置

```scala
// Scala 代码检查
val glutenEnabled = spark.conf.get("spark.gluten.enabled")
val backend = spark.conf.get("spark.gluten.sql.columnar.backend.lib")
val offHeapSize = spark.conf.get("spark.memory.offHeap.size")

println(s"Gluten Enabled: $glutenEnabled")
println(s"Backend: $backend")
println(s"Off-Heap Size: $offHeapSize")
```

### A.9.2 常见配置错误

| 错误 | 原因 | 解决方案 |
|------|------|---------|
| `OutOfMemoryError` | 堆外内存不足 | 增加 `spark.memory.offHeap.size` |
| `Too many small files` | 分区数过多 | 调整 `spark.sql.shuffle.partitions` |
| `Task not serializable` | 闭包问题 | 检查 UDF 和自定义函数 |
| `Fallback too often` | 功能不支持 | 检查 Gluten 版本和后端支持 |

---

**本附录完**。下一个附录将介绍 Gluten 支持的函数列表。
