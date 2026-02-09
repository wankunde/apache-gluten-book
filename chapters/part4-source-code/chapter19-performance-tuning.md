# 第19章：性能分析与调优

> 本章要点：
> - 性能分析工具全景（perf, Gperftools, VTune）
> - Query Trace 功能使用
> - 内存 Profiling 实战
> - CPU 火焰图分析
> - 瓶颈定位方法论
> - 实战案例：性能问题诊断

## 引言

性能调优是 Gluten 使用中的重要环节。本章将介绍一系列性能分析工具和方法，帮助你识别性能瓶颈并进行针对性优化。无论是 CPU 密集型还是内存密集型的性能问题，本章都将提供系统化的分析手段和解决方案。

## 19.1 性能分析工具概览

### 19.1.1 工具分类

| 工具类别 | 工具名称 | 用途 | 适用场景 |
|---------|---------|------|---------|
| **系统级** | perf | CPU profiling, 事件采样 | CPU 瓶颈分析 |
| | vmstat | 系统资源监控 | 整体性能监控 |
| | iostat | I/O 性能监控 | 磁盘瓶颈分析 |
| **进程级** | Gperftools | CPU/Heap profiling | C++ 性能分析 |
| | Valgrind | 内存泄漏检测 | 内存问题诊断 |
| | Intel VTune | 全方位性能分析 | Intel CPU 优化 |
| **JVM** | JProfiler | Java profiling | JVM 层性能 |
| | VisualVM | JVM 监控 | 实时监控 |
| | Async-profiler | 低开销 profiling | 生产环境分析 |
| **Spark** | Spark UI | 任务监控 | 作业级分析 |
| | Spark Metrics | 指标采集 | 指标监控 |
| **Gluten** | Gluten UI | Native 执行监控 | Gluten 特定分析 |
| | Query Trace | 查询执行追踪 | 查询级分析 |

### 19.1.2 安装性能分析工具

```bash
# 安装 perf
sudo apt install -y linux-tools-common linux-tools-generic linux-tools-`uname -r`

# 安装 Gperftools
sudo apt install -y google-perftools libgoogle-perftools-dev

# 安装 FlameGraph
cd /opt
sudo git clone https://github.com/brendangregg/FlameGraph.git
echo 'export PATH=/opt/FlameGraph:$PATH' >> ~/.bashrc

# 安装 Valgrind
sudo apt install -y valgrind

# 安装 async-profiler
cd /opt
wget https://github.com/jvm-profiling-tools/async-profiler/releases/download/v2.9/async-profiler-2.9-linux-x64.tar.gz
sudo tar -xzf async-profiler-2.9-linux-x64.tar.gz
```

## 19.2 Query Trace 功能

### 19.2.1 启用 Query Trace

Gluten 提供了查询级别的追踪功能，可以记录每个算子的执行时间和资源使用。

```scala
// 配置 Query Trace
spark.conf.set("spark.gluten.sql.columnar.queryTrace.enabled", "true")
spark.conf.set("spark.gluten.sql.columnar.queryTrace.outputPath", "/tmp/gluten-trace")

// 执行查询
val df = spark.sql("""
  SELECT 
    category,
    COUNT(*) as cnt,
    AVG(price) as avg_price
  FROM products
  WHERE price > 100
  GROUP BY category
  ORDER BY cnt DESC
  LIMIT 10
""")

df.collect()
```

### 19.2.2 Trace 输出格式

```json
// /tmp/gluten-trace/query_1234567890.json
{
  "query_id": "1234567890",
  "start_time": "2024-01-01T10:00:00.000Z",
  "end_time": "2024-01-01T10:00:05.234Z",
  "duration_ms": 5234,
  "stages": [
    {
      "stage_id": 0,
      "stage_name": "Scan + Filter",
      "operators": [
        {
          "operator": "TableScan",
          "duration_ms": 1200,
          "input_rows": 0,
          "output_rows": 1000000,
          "native_time_ms": 1150,
          "jvm_time_ms": 50
        },
        {
          "operator": "FilterExec",
          "duration_ms": 800,
          "input_rows": 1000000,
          "output_rows": 500000,
          "selectivity": 0.5,
          "native_time_ms": 780,
          "jvm_time_ms": 20
        }
      ]
    },
    {
      "stage_id": 1,
      "stage_name": "HashAggregate",
      "operators": [
        {
          "operator": "HashAggregate",
          "duration_ms": 2500,
          "input_rows": 500000,
          "output_rows": 100,
          "hash_table_size_mb": 45,
          "spill_count": 0,
          "native_time_ms": 2480,
          "jvm_time_ms": 20
        }
      ]
    },
    {
      "stage_id": 2,
      "stage_name": "Sort",
      "operators": [
        {
          "operator": "SortExec",
          "duration_ms": 734,
          "input_rows": 100,
          "output_rows": 10,
          "native_time_ms": 720,
          "jvm_time_ms": 14
        }
      ]
    }
  ],
  "total_input_rows": 1000000,
  "total_output_rows": 10,
  "native_time_ms": 5130,
  "jvm_time_ms": 104,
  "shuffle_bytes_written": 0,
  "shuffle_bytes_read": 0
}
```

### 19.2.3 Trace 分析工具

```python
#!/usr/bin/env python3
# trace_analyzer.py

import json
import sys
from collections import defaultdict

def analyze_trace(trace_file):
    with open(trace_file) as f:
        trace = json.load(f)
    
    print(f"Query ID: {trace['query_id']}")
    print(f"Total Duration: {trace['duration_ms']} ms")
    print(f"Native Time: {trace['native_time_ms']} ms ({trace['native_time_ms']/trace['duration_ms']*100:.1f}%)")
    print(f"JVM Time: {trace['jvm_time_ms']} ms ({trace['jvm_time_ms']/trace['duration_ms']*100:.1f}%)")
    print()
    
    # 按算子统计
    operator_stats = defaultdict(lambda: {"count": 0, "total_time": 0, "total_rows": 0})
    
    for stage in trace['stages']:
        for op in stage['operators']:
            op_name = op['operator']
            operator_stats[op_name]['count'] += 1
            operator_stats[op_name]['total_time'] += op['duration_ms']
            operator_stats[op_name]['total_rows'] += op['output_rows']
    
    print("Operator Statistics:")
    print(f"{'Operator':<20} {'Count':<8} {'Total Time (ms)':<18} {'Avg Time (ms)':<15} {'Throughput (rows/s)'}")
    print("-" * 85)
    
    for op_name, stats in sorted(operator_stats.items(), key=lambda x: x[1]['total_time'], reverse=True):
        avg_time = stats['total_time'] / stats['count']
        throughput = stats['total_rows'] / (stats['total_time'] / 1000) if stats['total_time'] > 0 else 0
        print(f"{op_name:<20} {stats['count']:<8} {stats['total_time']:<18} {avg_time:<15.2f} {throughput:,.0f}")
    
    # 识别瓶颈
    print("\nBottlenecks:")
    for stage in trace['stages']:
        stage_time = sum(op['duration_ms'] for op in stage['operators'])
        if stage_time > trace['duration_ms'] * 0.3:  # 超过 30% 的时间
            print(f"  - Stage {stage['stage_id']} ({stage['stage_name']}): {stage_time} ms")
            for op in sorted(stage['operators'], key=lambda x: x['duration_ms'], reverse=True):
                if op['duration_ms'] > stage_time * 0.5:
                    print(f"    * {op['operator']}: {op['duration_ms']} ms")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: python trace_analyzer.py <trace_file.json>")
        sys.exit(1)
    
    analyze_trace(sys.argv[1])
```

使用示例：

```bash
python trace_analyzer.py /tmp/gluten-trace/query_1234567890.json
```

输出：

```
Query ID: 1234567890
Total Duration: 5234 ms
Native Time: 5130 ms (98.0%)
JVM Time: 104 ms (2.0%)

Operator Statistics:
Operator             Count    Total Time (ms)    Avg Time (ms)   Throughput (rows/s)
-------------------------------------------------------------------------------------
HashAggregate        1        2500               2500.00         40,000
TableScan            1        1200               1200.00         833,333
FilterExec           1        800                800.00          625,000
SortExec             1        734                734.00          13

Bottlenecks:
  - Stage 1 (HashAggregate): 2500 ms
    * HashAggregate: 2500 ms
```

## 19.3 CPU 性能分析

### 19.3.1 使用 perf 进行采样

**步骤1：运行 Spark 任务并记录**

```bash
# 启动 Spark 任务
spark-submit \
  --master local[*] \
  --driver-memory 4g \
  --conf spark.plugins=org.apache.gluten.GlutenPlugin \
  --conf spark.gluten.sql.columnar.backend.lib=velox \
  --jars /path/to/gluten-velox-bundle.jar \
  your_app.py &

SPARK_PID=$!

# 等待任务开始执行
sleep 10

# 使用 perf 记录（记录 60 秒）
sudo perf record -F 99 -p $SPARK_PID -g -- sleep 60

# 任务完成后生成报告
sudo perf report
```

**步骤2：生成火焰图**

```bash
# 转换 perf 数据为火焰图格式
sudo perf script > out.perf

# 折叠调用栈
/opt/FlameGraph/stackcollapse-perf.pl out.perf > out.folded

# 生成火焰图
/opt/FlameGraph/flamegraph.pl out.folded > flamegraph.svg

# 在浏览器中打开
firefox flamegraph.svg
```

### 19.3.2 解读火焰图

火焰图的特征：

1. **宽度**：表示该函数占用的 CPU 时间比例
2. **高度**：表示调用栈深度
3. **颜色**：随机生成，无特殊含义

**典型性能问题识别**：

```
宽平台（Wide Plateau）：
┌─────────────────────────────────────────────────────┐
│              sortData() - 40%                       │ ← CPU 热点！
└─────────────────────────────────────────────────────┘
  表示该函数占用大量 CPU 时间，可能是瓶颈

高塔（Tall Tower）：
      ┌──┐
      │  │
      │  │
      │  │
      │  │
      │hashKey()│ ← 深调用栈
      └──┘
  表示深度递归或层层函数调用，可能需要优化

锯齿状（Jagged）：
┌┐ ┌┐ ┌┐ ┌┐
││ ││ ││ ││
││ ││ ││ ││
└┘ └┘ └┘ └┘
  表示多个小函数被频繁调用，可能需要内联优化
```

### 19.3.3 使用 Gperftools

**编译时启用 Gperftools**：

```bash
# 重新编译 Gluten，链接 profiler
cd /home/kunwan/ws/incubator-gluten/cpp
mkdir -p build-profile && cd build-profile

cmake -DBUILD_VELOX_BACKEND=ON \
      -DCMAKE_BUILD_TYPE=RelWithDebInfo \
      -DCMAKE_CXX_FLAGS="-lprofiler" \
      -GNinja \
      ..
ninja
```

**运行时启用 profiling**：

```bash
# 设置环境变量
export CPUPROFILE=/tmp/gluten_profile.prof
export CPUPROFILE_FREQUENCY=1000  # 每秒采样 1000 次

# 运行 Spark 任务
spark-submit \
  --conf spark.executorEnv.LD_PRELOAD=/usr/lib/x86_64-linux-gnu/libprofiler.so \
  --conf spark.executorEnv.CPUPROFILE=/tmp/gluten_profile.prof \
  --jars /path/to/gluten-velox-bundle.jar \
  your_app.py

# 生成报告
pprof --text /path/to/libgluten.so /tmp/gluten_profile.prof

# 生成火焰图
pprof --svg /path/to/libgluten.so /tmp/gluten_profile.prof > profile.svg
```

**Gperftools 输出示例**：

```
Total: 5000 samples
    1200  24.0%  24.0%     1200  24.0% velox::exec::HashAggregation::addInput
     800  16.0%  40.0%      800  16.0% velox::exec::Filter::getOutput
     600  12.0%  52.0%      600  12.0% facebook::velox::BaseVector::copyRanges
     500  10.0%  62.0%      500  10.0% std::sort
     400   8.0%  70.0%      400   8.0% velox::memory::MemoryPool::allocate
     ...
```

### 19.3.4 Intel VTune（Intel CPU 用户）

```bash
# 安装 VTune
# 下载：https://software.intel.com/content/www/us/en/develop/tools/oneapi/components/vtune-profiler.html

# 使用 VTune 采集数据
vtune -collect hotspots -app-working-dir . -- spark-submit ...

# 打开图形界面查看结果
vtune-gui
```

## 19.4 内存性能分析

### 19.4.1 使用 Valgrind Massif（内存使用分析）

```bash
# 使用 Massif 记录内存使用
valgrind --tool=massif \
         --massif-out-file=massif.out \
         spark-submit --jars /path/to/gluten-velox-bundle.jar your_app.py

# 生成图形报告
ms_print massif.out > massif_report.txt
cat massif_report.txt
```

**Massif 输出示例**：

```
    GB
12.00 ^                                                      #
      |                                                    @@#
      |                                                  @@@@#
      |                                                @@@@@@#
      |                                              @@@@@@@@#
      |                                            @@@@@@@@@@#
      |                                          @@@@@@@@@@@@#
      |                                        @@@@@@@@@@@@@@#
      |                                      @@@@@@@@@@@@@@@@#
      |                                    @@@@@@@@@@@@@@@@@@#
      |                                  @@@@@@@@@@@@@@@@@@@@#
 0.00 +----------------------------------------------------------------------->s
      0                                                                   60.00

Peak: 11.2 GB at 45.3s

Detailed snapshot at peak:
  48.2% (5.4 GB) velox::memory::MemoryPool (HashTable)
  32.1% (3.6 GB) arrow::Buffer (Columnar data)
  15.7% (1.8 GB) std::vector (Temporary buffers)
   4.0% (0.4 GB) Other
```

### 19.4.2 使用 Gperftools Heap Profiler

```bash
# 启用 Heap Profiling
export HEAPPROFILE=/tmp/gluten_heap.prof
export HEAP_PROFILE_ALLOCATION_INTERVAL=1073741824  # 每 1GB 分配采样一次

# 运行任务
spark-submit \
  --conf spark.executorEnv.LD_PRELOAD=/usr/lib/x86_64-linux-gnu/libtcmalloc.so \
  --conf spark.executorEnv.HEAPPROFILE=/tmp/gluten_heap.prof \
  ...

# 分析堆使用
pprof --text /path/to/libgluten.so /tmp/gluten_heap.prof.0001.heap
pprof --svg /path/to/libgluten.so /tmp/gluten_heap.prof.0001.heap > heap.svg
```

### 19.4.3 Gluten 内存追踪

启用 Gluten 内存追踪：

```scala
spark.conf.set("spark.gluten.memory.debug.enabled", "true")
spark.conf.set("spark.gluten.memory.debug.leakCheck", "true")

// 执行查询后，检查日志
```

**日志输出示例**：

```
[INFO] MemoryPool: task_0 allocated 524288000 bytes (500 MB)
[INFO] MemoryPool: task_0 current usage: 1073741824 bytes (1 GB)
[INFO] MemoryPool: task_0 peak usage: 2147483648 bytes (2 GB)
[INFO] MemoryPool: task_0 allocation count: 1024
[INFO] MemoryPool: task_0 freed 524288000 bytes (500 MB)
[WARN] MemoryPool: task_0 still has 549755813888 bytes (512 MB) allocated at shutdown
[ERROR] MemoryPool: Memory leak detected! 512 MB not freed
```

### 19.4.4 检测内存泄漏

使用 Valgrind Memcheck：

```bash
# 检测内存泄漏（运行时间较长）
valgrind --tool=memcheck \
         --leak-check=full \
         --show-leak-kinds=all \
         --track-origins=yes \
         --log-file=valgrind.log \
         spark-submit ...

# 查看泄漏报告
grep "definitely lost" valgrind.log
```

## 19.5 I/O 性能分析

### 19.5.1 使用 iostat 监控

```bash
# 实时监控 I/O（每 2 秒更新）
iostat -x 2

# 输出示例
Device            r/s     w/s     rMB/s   wMB/s   await  aqu-sz  %util
nvme0n1         1200.0  300.0     180.0    45.0    2.5    3.0   85.0
```

**关键指标**：
- **r/s, w/s**：每秒读写次数
- **rMB/s, wMB/s**：读写带宽
- **await**：平均等待时间（ms），<10ms 正常，>50ms 有问题
- **%util**：设备使用率，>80% 表示接近饱和

### 19.5.2 Parquet 读取性能优化

```scala
// 启用列裁剪和谓词下推
spark.conf.set("spark.sql.parquet.enableVectorizedReader", "false")  // 使用 Gluten
spark.conf.set("spark.sql.parquet.filterPushdown", "true")
spark.conf.set("spark.sql.parquet.aggregatePushdown", "true")

// 调整读取缓冲区
spark.conf.set("spark.sql.files.maxPartitionBytes", "134217728")  // 128 MB

// 使用 Gluten 的 Native Parquet Reader
spark.conf.set("spark.gluten.sql.columnar.enableNativeReader", "true")

// 性能对比
val df = spark.read.parquet("/data/large_table")

// 不推送谓词
df.filter("age > 18").count()  // 读取全量数据，耗时 60s

// 推送谓词
df.filter("age > 18").count()  // 只读取过滤后的数据，耗时 15s（4x 加速）
```

### 19.5.3 Shuffle 性能分析

```scala
// 启用 Shuffle 监控
spark.conf.set("spark.shuffle.service.enabled", "true")
spark.conf.set("spark.shuffle.registration.timeout", "120000")

// 执行 Shuffle 密集型查询
val df1 = spark.range(1000000).selectExpr("id as key", "id % 100 as value")
val df2 = spark.range(1000000).selectExpr("id as key", "id * 2 as value2")
val result = df1.join(df2, "key").count()

// 查看 Spark UI > Stages > Shuffle Read/Write
```

**优化 Shuffle**：

```scala
// 使用 Columnar Shuffle
spark.conf.set("spark.shuffle.manager", "org.apache.spark.shuffle.sort.ColumnarShuffleManager")

// 调整 Shuffle 分区数
spark.conf.set("spark.sql.shuffle.partitions", "200")  // 根据数据量调整

// 使用 Celeborn（远程 Shuffle 服务）
spark.conf.set("spark.shuffle.manager", "org.apache.spark.shuffle.celeborn.CelebornShuffleManager")
spark.conf.set("spark.celeborn.master.endpoints", "host1:9097,host2:9097")
```

## 19.6 瓶颈定位方法论

### 19.6.1 自顶向下分析法

```
1. 整体视图（Spark UI）
   └─> 识别慢 Stage
       └─> 查看 Stage 的 Tasks
           └─> 识别慢 Task
               └─> 分析 Task 的算子
                   └─> Profiling 具体算子

示例：
  总耗时: 300s
  ├─ Stage 0 (Scan): 20s (7%)
  ├─ Stage 1 (Shuffle): 50s (17%)
  └─ Stage 2 (Aggregate): 230s (77%)  ← 瓶颈！
      └─ Task 100: 180s  ← 数据倾斜！
          └─ HashAggregate: 175s
              └─ CPU perf: hash() 函数占 80%  ← 根因！
```

### 19.6.2 性能问题分类

| 问题类别 | 典型症状 | 分析工具 | 解决方案 |
|---------|---------|---------|---------|
| **CPU 密集** | CPU 使用率 100%，内存正常 | perf, VTune | 算法优化，SIMD，并行化 |
| **内存密集** | 频繁 GC，OOM | Massif, JProfiler | 增加内存，减少数据量，启用 Spill |
| **I/O 密集** | await 高，CPU 低 | iostat, iotop | 使用 SSD，增加并发，压缩 |
| **数据倾斜** | 部分 Task 特别慢 | Spark UI | 重新分区，加盐，广播 Join |
| **网络瓶颈** | Shuffle 慢 | netstat, iftop | 增加带宽，本地化，Celeborn |
| **JNI 开销** | JVM↔Native 转换多 | Query Trace | 减少 Fallback，批量传输 |

### 19.6.3 典型性能问题诊断

**案例1：Aggregate 慢**

```bash
# 1. 查看 Spark UI
#    发现 HashAggregate Stage 耗时 80%

# 2. 使用 Query Trace
spark.conf.set("spark.gluten.sql.columnar.queryTrace.enabled", "true")
# 发现 HashAggregate 算子 hash_table_size_mb = 8000 (8GB)

# 3. CPU Profiling
sudo perf record -p $PID -g -- sleep 30
sudo perf report
# 发现 hash() 函数占 60% CPU

# 4. 根因：Hash 冲突导致链表过长
# 解决方案：
spark.conf.set("spark.gluten.sql.columnar.hashAgg.enableAdaptive", "true")
# 或增加 Group 数量：
spark.sql.shuffle.partitions = 400  # 从 200 增加到 400
```

**案例2：内存溢出**

```bash
# 1. 日志显示 OOM
java.lang.OutOfMemoryError: Direct buffer memory

# 2. 检查配置
spark.memory.offHeap.size = 2g  # 太小！

# 3. 使用 Heap Profiler
export HEAPPROFILE=/tmp/heap.prof
# 发现 Arrow Buffer 占用 5GB

# 4. 解决方案
spark.memory.offHeap.size = 10g  # 增加 Off-Heap 内存
spark.gluten.memory.isolation = true  # 启用内存隔离
```

**案例3：Fallback 过多**

```bash
# 1. Query Trace 显示
#    native_time_ms = 1000ms (20%)
#    jvm_time_ms = 4000ms (80%)  ← Fallback 导致！

# 2. 检查日志
grep "Fallback" spark-executor.log
# 发现：Unsupported function: collect_list

# 3. 解决方案
# 方案A：使用支持的函数替代
SELECT array_agg(col) ...  # 而不是 collect_list

# 方案B：升级 Gluten 版本（新版本支持更多函数）

# 方案C：自定义 UDF（见第 17 章）
```

## 19.7 实战：端到端性能调优

### 19.7.1 基线测试

```scala
// TPC-H Q1 查询
val q1 = spark.sql("""
  SELECT
    l_returnflag,
    l_linestatus,
    SUM(l_quantity) AS sum_qty,
    SUM(l_extendedprice) AS sum_base_price,
    SUM(l_extendedprice * (1 - l_discount)) AS sum_disc_price,
    SUM(l_extendedprice * (1 - l_discount) * (1 + l_tax)) AS sum_charge,
    AVG(l_quantity) AS avg_qty,
    AVG(l_extendedprice) AS avg_price,
    AVG(l_discount) AS avg_disc,
    COUNT(*) AS count_order
  FROM lineitem
  WHERE l_shipdate <= date '1998-12-01' - INTERVAL '90' DAY
  GROUP BY l_returnflag, l_linestatus
  ORDER BY l_returnflag, l_linestatus
""")

// 基线测试（无 Gluten）
spark.conf.set("spark.plugins", "")
val baseline_time = time { q1.collect() }
println(s"Baseline: ${baseline_time}ms")

// 启用 Gluten
spark.conf.set("spark.plugins", "org.apache.gluten.GlutenPlugin")
spark.conf.set("spark.gluten.sql.columnar.backend.lib", "velox")
val gluten_time = time { q1.collect() }
println(s"Gluten: ${gluten_time}ms")
println(s"Speedup: ${baseline_time / gluten_time}x")
```

### 19.7.2 优化迭代

```scala
// 迭代1：启用基本 Gluten
spark.conf.set("spark.memory.offHeap.enabled", "true")
spark.conf.set("spark.memory.offHeap.size", "20g")
// 结果：60s → 30s (2x 加速)

// 迭代2：启用 Columnar Shuffle
spark.conf.set("spark.shuffle.manager", "org.apache.spark.shuffle.sort.ColumnarShuffleManager")
// 结果：30s → 25s (1.2x 加速)

// 迭代3：优化内存配置
spark.conf.set("spark.memory.offHeap.size", "30g")
spark.conf.set("spark.gluten.memory.isolation", "true")
// 结果：25s → 22s (1.14x 加速)

// 迭代4：启用 S3 Direct I/O
spark.conf.set("spark.gluten.sql.columnar.enableDirectRead", "true")
// 结果：22s → 18s (1.22x 加速)

// 总计：60s → 18s (3.3x 加速)
```

### 19.7.3 性能报告模板

```markdown
## 性能优化报告

### 测试环境
- **集群规模**: 10 nodes (64 cores, 256GB RAM each)
- **数据规模**: TPC-H 1TB
- **Spark 版本**: 3.3.1
- **Gluten 版本**: 1.2.0
- **后端**: Velox

### 基线性能
| 查询 | 原生 Spark | Gluten | 加速比 |
|------|-----------|--------|--------|
| Q1   | 60s       | 18s    | 3.3x   |
| Q3   | 45s       | 15s    | 3.0x   |
| Q6   | 30s       | 8s     | 3.8x   |
| Q13  | 120s      | 40s    | 3.0x   |
| **平均** | **63.8s** | **20.3s** | **3.1x** |

### 优化措施
1. ✅ 启用 Off-Heap 内存 30GB
2. ✅ 启用 Columnar Shuffle
3. ✅ 启用内存隔离
4. ✅ 使用 S3 Direct Read

### 瓶颈分析
- **主要瓶颈**: HashAggregate 占 40% 时间
- **次要瓶颈**: Shuffle 占 25% 时间
- **I/O**: 占 20% 时间

### 下一步优化方向
- [ ] 测试 ClickHouse 后端（可能对聚合更优）
- [ ] 启用 Runtime Filter
- [ ] 使用 Celeborn 远程 Shuffle
```

## 本章小结

本章介绍了 Gluten 性能分析和调优的完整体系：

1. **工具链**：perf, Gperftools, VTune, Valgrind, Query Trace
2. **CPU 分析**：火焰图生成和解读，热点函数识别
3. **内存分析**：内存使用追踪，泄漏检测
4. **I/O 分析**：iostat 监控，Parquet 优化，Shuffle 优化
5. **方法论**：自顶向下分析，问题分类，案例诊断
6. **实战**：端到端调优流程，性能报告模板

通过本章学习，你应该能够：
- ✅ 使用各种工具分析 Gluten 性能
- ✅ 生成和解读 CPU 火焰图
- ✅ 检测和解决内存问题
- ✅ 识别和定位性能瓶颈
- ✅ 进行系统化的性能调优

至此，Part 4（源码剖析篇）全部完成！

## 参考资料

1. Linux perf 教程：https://perf.wiki.kernel.org/index.php/Tutorial
2. Brendan Gregg's Blog：https://www.brendangregg.com/flamegraphs.html
3. Gperftools 文档：https://gperftools.github.io/gperftools/
4. Valgrind 手册：https://valgrind.org/docs/manual/manual.html
5. Intel VTune：https://software.intel.com/content/www/us/en/develop/tools/oneapi/components/vtune-profiler.html
