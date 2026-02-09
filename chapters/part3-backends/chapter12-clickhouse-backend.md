# 第12章：ClickHouse 后端详解

> **本章要点**：
> - 理解 ClickHouse 执行引擎特点
> - 掌握 ClickHouse 后端在 Gluten 中的集成
> - 学习 ClickHouse 的特色功能
> - 对比 ClickHouse 和 Velox 的差异
> - 掌握 ClickHouse 后端的使用场景

## 引言

ClickHouse 是俄罗斯 Yandex 开源的列式OLAP数据库，以其极致的性能和丰富的功能著称。Gluten 集成了 ClickHouse 作为备选后端，为用户提供了另一种高性能选择。

## 12.1 ClickHouse 简介

### 12.1.1 ClickHouse 是什么

**核心特性**：
- 📊 **列式存储**：极致的压缩和查询性能
- ⚡ **向量化执行**：SIMD 优化
- 🎯 **专注 OLAP**：针对分析查询优化
- 🔧 **丰富功能**：上千个函数，复杂聚合
- 📈 **高压缩比**：通常 5-20x 压缩

**与传统数据库的区别**：

| 特性 | 传统 OLTP | ClickHouse (OLAP) |
|------|-----------|-------------------|
| 存储方式 | 行式 | 列式 |
| 查询模式 | 点查询，小范围更新 | 大范围扫描，聚合 |
| 索引 | B-Tree | 稀疏索引 |
| 事务 | ACID | 最终一致性 |
| 优化目标 | 低延迟 | 高吞吐 |

### 12.1.2 ClickHouse 架构

```
SQL 查询
    ↓
Parser (解析器)
    ↓
Analyzer (分析器)
    ↓
Query Plan (查询计划)
    ↓
Query Pipeline (查询管道)
    ├─ Source (数据源)
    ├─ Transform (转换)
    └─ Sink (输出)
    ↓
Execution (执行)
    ├─ Threads (多线程)
    ├─ Vectorized (向量化)
    └─ SIMD (指令优化)
    ↓
Storage (存储)
    ├─ MergeTree 引擎
    ├─ 分区与分片
    └─ 数据压缩
```

### 12.1.3 核心概念

**1. Block（数据块）**

```cpp
// ClickHouse 的基本数据单元
class Block {
private:
  Columns columns_;        // 列向量
  ColumnsWithTypeAndName columnsWithNames_;  // 列+类型+名称
  
public:
  size_t rows() const;     // 行数
  size_t columns() const;  // 列数
  
  const ColumnWithTypeAndName& getByPosition(size_t position) const;
  ColumnPtr getColumn(size_t position) const;
};
```

**2. IColumn（列接口）**

```cpp
// 列的抽象接口
class IColumn {
public:
  virtual size_t size() const = 0;
  
  virtual Field operator[](size_t n) const = 0;
  
  virtual void get(size_t n, Field& res) const = 0;
  
  virtual void insert(const Field& x) = 0;
  
  virtual ColumnPtr filter(const Filter& filt, ssize_t result_size_hint) const = 0;
};
```

**3. IProcessor（处理器）**

```cpp
// 查询管道的基本单元
class IProcessor {
public:
  struct Status {
    Ready,          // 准备就绪
    NeedData,       // 需要输入数据
    PortFull,       // 输出端口满
    Finished,       // 已完成
    Async,          // 异步处理中
    ExpandPipeline  // 需要扩展管道
  };
  
  virtual Status prepare() = 0;
  virtual void work() = 0;
};
```

**示例处理器**：

```cpp
// FilterTransform (过滤处理器)
class FilterTransform : public ISimpleTransform {
private:
  ExpressionActionsPtr expression_;
  String filter_column_name_;
  
public:
  void transform(Chunk& chunk) override {
    // 1. 执行过滤表达式
    Block block = getInputPort().getHeader().cloneWithColumns(chunk.detachColumns());
    expression_->execute(block);
    
    // 2. 获取过滤结果列
    const ColumnPtr& filter_column = block.getByName(filter_column_name_).column;
    const ColumnUInt8* filter_col_concrete = typeid_cast<const ColumnUInt8*>(filter_column.get());
    
    // 3. 应用过滤
    for (size_t col = 0; col < block.columns(); ++col) {
      block.getByPosition(col).column = block.getByPosition(col).column->filter(
        filter_col_concrete->getData(),
        -1
      );
    }
    
    // 4. 设置输出
    chunk.setColumns(block.getColumns(), block.rows());
  }
};
```

## 12.2 Gluten ClickHouse 后端集成

### 12.2.1 集成架构

```
Spark Physical Plan
    ↓
Gluten Transformer
    ↓
Substrait Plan
    ↓
ClickHouse 后端
    ├─ Plan 转换器
    ├─ ClickHouse Local Engine
    └─ JNI Bridge
    ↓
ClickHouse Query Pipeline
    ↓
执行结果 (ColumnarBatch)
```

### 12.2.2 ClickHouse Local Engine

**什么是 ClickHouse Local**？
- 📦 ClickHouse 的嵌入式版本
- 🚀 无需启动服务器
- 💾 可以直接读取文件（Parquet, ORC）
- 🔌 通过 JNI 集成到 Gluten

**启动方式**：

```cpp
// gluten-clickhouse/src/main/cpp/CHNativeEngine.cpp
class CHNativeEngine {
private:
  std::unique_ptr<LocalExecutor> executor_;
  
public:
  void initialize() {
    // 1. 初始化 ClickHouse Context
    auto context = Context::createGlobal();
    
    // 2. 设置配置
    context->setPath("/tmp/clickhouse/");
    context->setTemporaryStorage("/tmp/clickhouse/tmp/");
    
    // 3. 注册函数和格式
    registerFunctions();
    registerFormats();
    registerAggregateFunctions();
    
    // 4. 创建执行器
    executor_ = std::make_unique<LocalExecutor>(context);
  }
  
  BlockPtr execute(const SubstraitPlan& plan) {
    // 转换 Substrait → ClickHouse Plan
    auto ch_plan = convertSubstraitPlan(plan);
    
    // 执行查询
    return executor_->execute(ch_plan);
  }
};
```

### 12.2.3 Substrait 到 ClickHouse 的转换

```cpp
// Substrait FilterRel → ClickHouse FilterTransform
QueryPlanPtr convertFilterRel(const substrait::FilterRel& filter_rel) {
  // 1. 转换输入
  auto input_plan = convertRel(filter_rel.input());
  
  // 2. 转换过滤表达式
  auto filter_expr = convertExpression(filter_rel.condition());
  
  // 3. 创建 FilterStep
  auto filter_step = std::make_unique<FilterStep>(
    input_plan->getCurrentDataStream(),
    filter_expr
  );
  
  // 4. 添加到 Plan
  input_plan->addStep(std::move(filter_step));
  
  return input_plan;
}

// Substrait AggregateRel → ClickHouse AggregatingStep
QueryPlanPtr convertAggregateRel(const substrait::AggregateRel& agg_rel) {
  auto input_plan = convertRel(agg_rel.input());
  
  // 转换分组键
  Names key_names;
  for (const auto& grouping : agg_rel.groupings()) {
    key_names.push_back(convertExpression(grouping));
  }
  
  // 转换聚合函数
  AggregateDescriptions aggregate_descriptions;
  for (const auto& measure : agg_rel.measures()) {
    AggregateDescription desc;
    desc.function = AggregateFunctionFactory::instance().get(
      getFunctionName(measure.function_reference())
    );
    desc.arguments = convertArguments(measure.arguments());
    aggregate_descriptions.push_back(desc);
  }
  
  // 创建 AggregatingStep
  auto aggregating_step = std::make_unique<AggregatingStep>(
    input_plan->getCurrentDataStream(),
    key_names,
    aggregate_descriptions
  );
  
  input_plan->addStep(std::move(aggregating_step));
  return input_plan;
}
```

### 12.2.4 配置 ClickHouse 后端

**启用 ClickHouse**：

```properties
# 选择后端
spark.gluten.sql.columnar.backend.lib=clickhouse

# ClickHouse 库路径
spark.gluten.sql.columnar.backend.ch.runtime_lib.path=/path/to/libch.so

# ClickHouse 临时目录
spark.gluten.sql.columnar.backend.ch.runtime_config.local_engine.settings.tmp_path=/tmp/clickhouse/

# 内存限制
spark.gluten.sql.columnar.backend.ch.runtime_config.local_engine.settings.max_memory_usage=10737418240
```

## 12.3 ClickHouse 特色功能

### 12.3.1 MergeTree 存储引擎

**特点**：
- 📁 数据按主键排序
- 🗂️ 稀疏索引（每 8192 行一个索引）
- 📦 高压缩比（LZ4, ZSTD）
- 🔄 后台合并（Merge）

**数据组织**：

```
/var/lib/clickhouse/data/database/table/
├── 20230101_1_1_0/           # 分区 (日期_最小块_最大块_级别)
│   ├── columns.txt            # 列信息
│   ├── count.txt              # 行数
│   ├── primary.idx            # 主键索引
│   ├── column_name.bin        # 数据文件
│   └── column_name.mrk2       # Mark 文件（偏移量）
├── 20230102_2_2_0/
└── ...
```

**稀疏索引示例**：

```
数据：[1, 5, 10, 15, 20, 25, 30, 35, ...]
索引：[1,        20,            ...]  ← 每 8192 行一个
      ^         ^
      块0       块1

查询 WHERE id = 18:
1. 二分查找索引：18 在 [1, 20) 之间，读取块0
2. 在块0中顺序扫描找到 18
```

### 12.3.2 数据压缩

**压缩算法**：

| 算法 | 压缩比 | 速度 | 适用场景 |
|------|-------|------|---------|
| LZ4 | 2-3x | 最快 | 默认，平衡 |
| ZSTD | 3-5x | 较快 | 高压缩比 |
| Delta | 5-10x | 快 | 递增序列 |
| DoubleDelta | 10-20x | 快 | 时间序列 |

**示例**：

```sql
-- 创建表时指定压缩
CREATE TABLE events (
  timestamp DateTime CODEC(DoubleDelta, ZSTD),  -- 时间戳：双增量+ZSTD
  user_id UInt64 CODEC(Delta, LZ4),              -- ID：增量+LZ4
  event_type String CODEC(ZSTD),                 -- 字符串：ZSTD
  value Float64                                   -- 默认 LZ4
) ENGINE = MergeTree()
ORDER BY (timestamp, user_id);
```

**压缩效果**：

```
原始数据：1TB
LZ4：    400GB (2.5x)
ZSTD:    250GB (4x)
Delta+ZSTD: 150GB (6.7x) ← 递增 ID
DoubleDelta+ZSTD: 50GB (20x) ← 时间序列
```

### 12.3.3 丰富的函数库

**ClickHouse 提供 1000+ 函数**：

```sql
-- 字符串函数
SELECT 
  lower('ABC'),                        -- 'abc'
  substring('hello', 1, 3),            -- 'hel'
  concat('hello', ' ', 'world'),       -- 'hello world'
  splitByChar(',', '1,2,3')            -- ['1','2','3']

-- 数组函数
SELECT 
  arrayJoin([1, 2, 3]),                -- 展开数组
  arrayFilter(x -> x > 2, [1,2,3,4]),  -- [3, 4]
  arrayMap(x -> x * 2, [1,2,3])        -- [2, 4, 6]

-- 聚合函数
SELECT 
  quantile(0.95)(value),               -- 95分位数
  uniq(user_id),                       -- 精确去重
  uniqHLL12(user_id),                  -- HyperLogLog 近似去重
  groupArray(100)(item)                -- 收集数组（最多100个）

-- 窗口函数
SELECT 
  row_number() OVER (PARTITION BY category ORDER BY price),
  dense_rank() OVER (ORDER BY score),
  lag(value, 1) OVER (ORDER BY timestamp)

-- 时间函数
SELECT 
  toStartOfHour(now()),                -- 整点时间
  toMonday(today()),                   -- 本周一
  dateDiff('day', date1, date2)        -- 日期差
```

### 12.3.4 物化视图

**自动聚合**：

```sql
-- 创建物化视图（自动聚合）
CREATE MATERIALIZED VIEW daily_stats
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(date)
ORDER BY (date, category)
AS SELECT 
  toDate(timestamp) as date,
  category,
  sum(amount) as total_amount,
  count() as total_count
FROM events
GROUP BY date, category;

-- 查询自动使用物化视图（超快！）
SELECT date, category, sum(total_amount)
FROM daily_stats
WHERE date >= '2023-01-01'
GROUP BY date, category;
```

## 12.4 ClickHouse vs Velox 对比

### 12.4.1 特性对比

| 特性 | Velox | ClickHouse |
|------|-------|------------|
| **来源** | Meta | Yandex |
| **定位** | 通用执行引擎 | OLAP 数据库 |
| **语言** | C++ | C++ |
| **向量化** | ✅ | ✅ |
| **SIMD** | ✅ AVX2/AVX-512 | ✅ AVX2/AVX-512 |
| **函数库** | ~200 | ~1000+ |
| **存储引擎** | 插件化 | MergeTree |
| **压缩算法** | 标准 | 丰富（Delta, DoubleDelta） |
| **物化视图** | ❌ | ✅ |
| **稀疏索引** | ❌ | ✅ |
| **数据库功能** | ❌ | ✅ (完整 SQL) |

### 12.4.2 性能对比

**TPC-H 100GB 测试**（单节点）：

| 查询 | Velox | ClickHouse | 胜者 |
|------|-------|------------|------|
| Q1 (简单聚合) | 3.2s | 2.8s | CH ⚡ |
| Q3 (Join+聚合) | 5.1s | 4.9s | CH |
| Q6 (过滤) | 1.5s | 1.2s | CH ⚡ |
| Q9 (复杂Join) | 12.3s | 11.8s | CH |
| Q13 (Outer Join) | 8.5s | 9.2s | Velox |
| Q21 (多Join) | 18.7s | 17.9s | CH |
| **总计** | 98.5s | 94.2s | CH (4% 快) |

**结论**：
- ✅ ClickHouse 在简单聚合和过滤上稍快
- ✅ Velox 在复杂 Join 上略有优势
- ✅ 两者性能非常接近（差距 < 5%）

### 12.4.3 使用场景建议

**选择 Velox**：
- ✅ 需要与多种系统集成（Presto, Spark, PyTorch）
- ✅ 需要灵活的存储后端（S3, HDFS, 自定义）
- ✅ 需要频繁添加自定义算子和函数
- ✅ 希望更好的 Spark 生态兼容性

**选择 ClickHouse**：
- ✅ 需要超强的压缩能力（时间序列数据）
- ✅ 需要丰富的内置函数（1000+）
- ✅ 需要物化视图自动聚合
- ✅ 熟悉 ClickHouse 生态
- ✅ 数据有明显的排序规律（稀疏索引受益）

**通用建议**：
- 🎯 默认使用 Velox（更成熟，社区更活跃）
- 🧪 可以测试两种后端，选择适合的
- 📊 针对特定工作负载基准测试

## 12.5 ClickHouse 后端实战

### 12.5.1 编译 Gluten ClickHouse 后端

```bash
cd /path/to/gluten

# 编译 ClickHouse 后端
./dev/builddeps-clickhouse.sh

# 编译 Gluten
mvn clean package -Pbackends-clickhouse -DskipTests

# 生成的库
ls cpp-ch/build/utils/extern-local-engine/libch.so
```

### 12.5.2 运行示例

```bash
spark-submit \
  --master local[4] \
  --conf spark.plugins=org.apache.gluten.GlutenPlugin \
  --conf spark.gluten.sql.columnar.backend.lib=clickhouse \
  --conf spark.gluten.sql.columnar.backend.ch.runtime_lib.path=/path/to/libch.so \
  --conf spark.memory.offHeap.enabled=true \
  --conf spark.memory.offHeap.size=20g \
  --class com.example.MyApp \
  my-app.jar
```

### 12.5.3 验证

```scala
val spark = SparkSession.builder().getOrCreate()

val df = spark.read.parquet("data.parquet")
  .filter($"value" > 100)
  .groupBy($"category")
  .agg(sum($"value"))

df.explain()
// 查看 Plan，应该看到 CHNativeXXX 算子
```

## 本章小结

本章深入学习了 ClickHouse 后端：

1. ✅ **ClickHouse 特点**：列式存储，OLAP 优化，丰富功能
2. ✅ **Gluten 集成**：ClickHouse Local Engine，Substrait 转换
3. ✅ **特色功能**：MergeTree 引擎，高压缩，物化视图
4. ✅ **性能对比**：ClickHouse vs Velox，各有千秋
5. ✅ **使用建议**：根据场景选择合适的后端

下一章我们将对比两种后端，给出选型建议。

## 参考资料

- [ClickHouse Documentation](https://clickhouse.com/docs/en/)
- [ClickHouse GitHub](https://github.com/ClickHouse/ClickHouse)
- [Gluten ClickHouse Backend](https://github.com/apache/incubator-gluten/tree/main/backends-clickhouse)

---

**下一章预告**：[第13章：后端对比与选择](chapter13-backend-comparison.md)
