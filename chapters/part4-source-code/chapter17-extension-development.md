# 第17章：扩展开发

Apache Gluten提供了灵活的扩展机制，允许开发者通过自定义UDF、数据源集成、Shuffle插件和流处理等方式扩展功能。本章将详细介绍各种扩展开发方法，包含完整的代码示例和部署指南。

## 17.1 自定义UDF开发

### 17.1.1 UDF开发架构概览

Gluten支持在Velox和ClickHouse两种后端上开发自定义UDF。开发流程包括：

1. **JNI层设计**：Java与C++之间的桥接
2. **C++函数实现**：实际的计算逻辑
3. **函数注册**：将UDF注册到执行引擎
4. **编译部署**：生成动态库并加载

```
┌─────────────────┐
│  Spark SQL      │
│  (Scala/Java)   │
└────────┬────────┘
         │ JNI Call
         ▼
┌─────────────────┐
│  Gluten Core    │
│  (JNI Bridge)   │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Velox/CH       │
│  (C++ UDF)      │
└─────────────────┘
```

### 17.1.2 Velox后端简单标量UDF

**步骤1：创建UDF C++文件**

```cpp
// velox-udf/src/MyScalarFunction.cpp
#include "velox/expression/VectorFunction.h"
#include "velox/functions/Registerer.h"
#include "velox/vector/FlatVector.h"

namespace facebook::velox::functions {

// 简单的字符串大写转换UDF
class UpperCaseFunction : public exec::VectorFunction {
 public:
  void apply(
      const SelectivityVector& rows,
      std::vector<VectorPtr>& args,
      const TypePtr& outputType,
      exec::EvalCtx& context,
      VectorPtr& result) const override {
    
    // 获取输入向量
    auto inputVector = args[0]->as<SimpleVector<StringView>>();
    auto* pool = context.pool();
    
    // 准备输出向量
    auto* flatResult = result->as<FlatVector<StringView>>();
    
    rows.applyToSelected([&](vector_size_t row) {
      if (inputVector->isNullAt(row)) {
        flatResult->setNull(row, true);
        return;
      }
      
      // 获取输入字符串
      auto input = inputVector->valueAt(row);
      
      // 转换为大写
      std::string upper(input.size(), '\0');
      std::transform(
          input.begin(), 
          input.end(), 
          upper.begin(), 
          ::toupper);
      
      // 设置结果
      flatResult->set(row, StringView(upper));
    });
  }
  
  static std::vector<std::shared_ptr<exec::FunctionSignature>> signatures() {
    return {
      exec::FunctionSignatureBuilder()
          .returnType("varchar")
          .argumentType("varchar")
          .build()
    };
  }
};

// 数值计算UDF：计算平方根
class SqrtPlusOneFunction : public exec::VectorFunction {
 public:
  void apply(
      const SelectivityVector& rows,
      std::vector<VectorPtr>& args,
      const TypePtr& outputType,
      exec::EvalCtx& context,
      VectorPtr& result) const override {
    
    auto inputVector = args[0]->as<SimpleVector<double>>();
    auto* flatResult = result->as<FlatVector<double>>();
    
    rows.applyToSelected([&](vector_size_t row) {
      if (inputVector->isNullAt(row)) {
        flatResult->setNull(row, true);
        return;
      }
      
      double value = inputVector->valueAt(row);
      if (value < 0) {
        flatResult->setNull(row, true);
        return;
      }
      
      flatResult->set(row, std::sqrt(value) + 1.0);
    });
  }
  
  static std::vector<std::shared_ptr<exec::FunctionSignature>> signatures() {
    return {
      exec::FunctionSignatureBuilder()
          .returnType("double")
          .argumentType("double")
          .build()
    };
  }
};

} // namespace facebook::velox::functions
```

**步骤2：注册函数**

```cpp
// velox-udf/src/Registration.cpp
#include "MyScalarFunction.h"
#include "velox/functions/Registerer.h"

namespace facebook::velox::functions {

void registerMyUDFs() {
  VELOX_REGISTER_VECTOR_FUNCTION(udf_my_upper, "my_upper");
  exec::registerVectorFunction(
      "my_upper",
      UpperCaseFunction::signatures(),
      std::make_unique<UpperCaseFunction>());
  
  VELOX_REGISTER_VECTOR_FUNCTION(udf_sqrt_plus_one, "sqrt_plus_one");
  exec::registerVectorFunction(
      "sqrt_plus_one",
      SqrtPlusOneFunction::signatures(),
      std::make_unique<SqrtPlusOneFunction>());
}

} // namespace facebook::velox::functions

// 导出C接口供JNI调用
extern "C" {
  void register_custom_udfs() {
    facebook::velox::functions::registerMyUDFs();
  }
}
```

### 17.1.3 复杂UDF示例

**聚合函数：加权平均**

```cpp
// velox-udf/src/WeightedAvgAggregate.cpp
#include "velox/exec/Aggregate.h"
#include "velox/functions/prestosql/aggregates/AggregateNames.h"

namespace facebook::velox::aggregate {

struct WeightedAvgAccumulator {
  double sum{0};
  double weightSum{0};
  
  void update(double value, double weight) {
    sum += value * weight;
    weightSum += weight;
  }
  
  void merge(const WeightedAvgAccumulator& other) {
    sum += other.sum;
    weightSum += other.weightSum;
  }
  
  double finalize() const {
    return weightSum > 0 ? sum / weightSum : 0;
  }
};

class WeightedAvgAggregate : public exec::Aggregate {
 public:
  explicit WeightedAvgAggregate(TypePtr resultType)
      : exec::Aggregate(resultType) {}
  
  int32_t accumulatorFixedWidthSize() const override {
    return sizeof(WeightedAvgAccumulator);
  }
  
  void addRawInput(
      char** groups,
      const SelectivityVector& rows,
      const std::vector<VectorPtr>& args,
      bool /*mayPushdown*/) override {
    
    auto valueVector = args[0]->as<SimpleVector<double>>();
    auto weightVector = args[1]->as<SimpleVector<double>>();
    
    rows.applyToSelected([&](vector_size_t row) {
      if (!valueVector->isNullAt(row) && !weightVector->isNullAt(row)) {
        auto* accumulator = value<WeightedAvgAccumulator>(groups[row]);
        accumulator->update(
            valueVector->valueAt(row),
            weightVector->valueAt(row));
      }
    });
  }
  
  void addIntermediateResults(
      char** groups,
      const SelectivityVector& rows,
      const std::vector<VectorPtr>& args,
      bool /*mayPushdown*/) override {
    
    addRawInput(groups, rows, args, false);
  }
  
  void addSingleGroupRawInput(
      char* group,
      const SelectivityVector& rows,
      const std::vector<VectorPtr>& args,
      bool /*mayPushdown*/) override {
    
    auto valueVector = args[0]->as<SimpleVector<double>>();
    auto weightVector = args[1]->as<SimpleVector<double>>();
    auto* accumulator = value<WeightedAvgAccumulator>(group);
    
    rows.applyToSelected([&](vector_size_t row) {
      if (!valueVector->isNullAt(row) && !weightVector->isNullAt(row)) {
        accumulator->update(
            valueVector->valueAt(row),
            weightVector->valueAt(row));
      }
    });
  }
  
  void extractValues(
      char** groups,
      int32_t numGroups,
      VectorPtr* result) override {
    
    auto* flatResult = (*result)->as<FlatVector<double>>();
    
    for (int32_t i = 0; i < numGroups; ++i) {
      auto* accumulator = value<WeightedAvgAccumulator>(groups[i]);
      flatResult->set(i, accumulator->finalize());
    }
  }
};

// 注册聚合函数
bool registerWeightedAvg(const std::string& name) {
  std::vector<std::shared_ptr<exec::AggregateFunctionSignature>> signatures{
      exec::AggregateFunctionSignatureBuilder()
          .returnType("double")
          .intermediateType("row(double,double)")
          .argumentType("double")
          .argumentType("double")
          .build()};
  
  return exec::registerAggregateFunction(
      name,
      std::move(signatures),
      [](core::AggregationNode::Step step,
         const std::vector<TypePtr>& argTypes,
         const TypePtr& resultType) -> std::unique_ptr<exec::Aggregate> {
        return std::make_unique<WeightedAvgAggregate>(resultType);
      });
}

} // namespace facebook::velox::aggregate
```

**字符串处理UDF：正则提取**

```cpp
// velox-udf/src/RegexExtractFunction.cpp
#include <re2/re2.h>
#include "velox/expression/VectorFunction.h"

namespace facebook::velox::functions {

class RegexExtractFunction : public exec::VectorFunction {
 public:
  void apply(
      const SelectivityVector& rows,
      std::vector<VectorPtr>& args,
      const TypePtr& outputType,
      exec::EvalCtx& context,
      VectorPtr& result) const override {
    
    auto inputVector = args[0]->as<SimpleVector<StringView>>();
    auto patternVector = args[1]->as<SimpleVector<StringView>>();
    auto groupVector = args[2]->as<SimpleVector<int32_t>>();
    
    auto* flatResult = result->as<FlatVector<StringView>>();
    
    rows.applyToSelected([&](vector_size_t row) {
      if (inputVector->isNullAt(row) || patternVector->isNullAt(row)) {
        flatResult->setNull(row, true);
        return;
      }
      
      auto input = inputVector->valueAt(row);
      auto pattern = patternVector->valueAt(row);
      int32_t group = groupVector->isNullAt(row) ? 0 : groupVector->valueAt(row);
      
      re2::RE2 regex(re2::StringPiece(pattern.data(), pattern.size()));
      if (!regex.ok()) {
        flatResult->setNull(row, true);
        return;
      }
      
      std::string result_str;
      re2::StringPiece input_piece(input.data(), input.size());
      
      std::vector<re2::StringPiece> groups(regex.NumberOfCapturingGroups() + 1);
      if (re2::RE2::PartialMatchN(input_piece, regex, groups.data(), groups.size())) {
        if (group >= 0 && group < groups.size()) {
          result_str = groups[group].as_string();
          flatResult->set(row, StringView(result_str));
          return;
        }
      }
      
      flatResult->setNull(row, true);
    });
  }
  
  static std::vector<std::shared_ptr<exec::FunctionSignature>> signatures() {
    return {
      exec::FunctionSignatureBuilder()
          .returnType("varchar")
          .argumentType("varchar")
          .argumentType("varchar")
          .argumentType("integer")
          .build()
    };
  }
};

} // namespace facebook::velox::functions
```

### 17.1.4 ClickHouse后端UDF

```cpp
// clickhouse-udf/src/MyClickHouseUDF.cpp
#include <Functions/IFunction.h>
#include <Functions/FunctionFactory.h>
#include <DataTypes/DataTypeString.h>
#include <Columns/ColumnString.h>

namespace DB {

class FunctionCustomHash : public IFunction {
 public:
  static constexpr auto name = "custom_hash";
  static FunctionPtr create(ContextPtr) { return std::make_shared<FunctionCustomHash>(); }
  
  String getName() const override { return name; }
  size_t getNumberOfArguments() const override { return 1; }
  
  DataTypePtr getReturnTypeImpl(const DataTypes&) const override {
    return std::make_shared<DataTypeUInt64>();
  }
  
  ColumnPtr executeImpl(
      const ColumnsWithTypeAndName& arguments,
      const DataTypePtr&,
      size_t input_rows_count) const override {
    
    const auto* col = checkAndGetColumn<ColumnString>(arguments[0].column.get());
    if (!col) {
      throw Exception("Illegal column type", ErrorCodes::ILLEGAL_COLUMN);
    }
    
    auto result_column = ColumnUInt64::create(input_rows_count);
    auto& result_data = result_column->getData();
    
    for (size_t i = 0; i < input_rows_count; ++i) {
      auto str = col->getDataAt(i);
      uint64_t hash = 0;
      
      // 简单的哈希算法
      for (size_t j = 0; j < str.size; ++j) {
        hash = hash * 31 + str.data[j];
      }
      
      result_data[i] = hash;
    }
    
    return result_column;
  }
};

void registerCustomUDFs(FunctionFactory& factory) {
  factory.registerFunction<FunctionCustomHash>();
}

} // namespace DB
```

### 17.1.5 JNI桥接层

```scala
// gluten-core/src/main/scala/org/apache/gluten/udf/UDFLoader.scala
package org.apache.gluten.udf

import org.apache.gluten.backendsapi.BackendsApiManager
import org.apache.spark.internal.Logging

object UDFLoader extends Logging {
  
  private var loaded = false
  
  def loadCustomUDFs(libraryPath: String): Unit = synchronized {
    if (loaded) {
      logInfo("Custom UDFs already loaded")
      return
    }
    
    try {
      // 加载动态库
      System.load(libraryPath)
      
      // 调用C++注册函数
      registerNativeUDFs()
      
      loaded = true
      logInfo(s"Successfully loaded custom UDFs from $libraryPath")
    } catch {
      case e: Exception =>
        logError(s"Failed to load custom UDFs: ${e.getMessage}", e)
        throw e
    }
  }
  
  @native def registerNativeUDFs(): Unit
}
```

### 17.1.6 UDF编译和部署

**CMakeLists.txt配置**

```cmake
# velox-udf/CMakeLists.txt
cmake_minimum_required(VERSION 3.14)
project(gluten-custom-udf)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 查找Velox
find_package(Velox REQUIRED)

# UDF源文件
set(UDF_SOURCES
    src/MyScalarFunction.cpp
    src/WeightedAvgAggregate.cpp
    src/RegexExtractFunction.cpp
    src/Registration.cpp
)

# 创建共享库
add_library(gluten_custom_udf SHARED ${UDF_SOURCES})

target_link_libraries(gluten_custom_udf
    PRIVATE
    velox_expression
    velox_functions_prestosql
    velox_vector
    re2::re2
)

target_include_directories(gluten_custom_udf
    PRIVATE
    ${VELOX_INCLUDE_DIRS}
)

# 安装
install(TARGETS gluten_custom_udf
    LIBRARY DESTINATION lib
)
```

**编译脚本**

```bash
#!/bin/bash
# build-udf.sh

set -e

UDF_DIR=$(cd "$(dirname "$0")" && pwd)
BUILD_DIR="$UDF_DIR/build"
INSTALL_DIR="$UDF_DIR/dist"

echo "Building custom UDFs..."

# 清理旧构建
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

# CMake配置
cd "$BUILD_DIR"
cmake .. \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_INSTALL_PREFIX="$INSTALL_DIR" \
  -DVELOX_ROOT=/path/to/velox/install

# 编译
make -j$(nproc)

# 安装
make install

echo "UDF library installed to: $INSTALL_DIR/lib/libgluten_custom_udf.so"
```

**Spark配置加载UDF**

```properties
# spark-defaults.conf
spark.gluten.sql.columnar.backend.lib=velox
spark.gluten.sql.columnar.backend.velox.udfLibraryPaths=/path/to/libgluten_custom_udf.so
spark.executorEnv.LD_LIBRARY_PATH=/path/to/velox/lib
```

**使用示例**

```scala
// Spark应用中使用自定义UDF
import org.apache.spark.sql.SparkSession
import org.apache.gluten.udf.UDFLoader

object UDFExample {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder()
      .appName("Gluten UDF Example")
      .config("spark.plugins", "org.apache.gluten.GlutenPlugin")
      .getOrCreate()
    
    // 加载UDF
    UDFLoader.loadCustomUDFs("/path/to/libgluten_custom_udf.so")
    
    // 注册Spark UDF（映射到原生UDF）
    spark.udf.register("my_upper", (s: String) => s)  // 占位符
    spark.udf.register("sqrt_plus_one", (x: Double) => x)
    spark.udf.register("weighted_avg", (v: Double, w: Double) => v)
    
    // 使用UDF
    val df = spark.read.parquet("/data/input")
    
    df.selectExpr(
      "my_upper(name) as upper_name",
      "sqrt_plus_one(value) as result"
    ).show()
    
    df.groupBy("category")
      .agg(expr("weighted_avg(price, quantity) as avg_price"))
      .show()
    
    spark.stop()
  }
}
```

## 17.2 数据源集成

### 17.2.1 Delta Lake集成

**依赖配置**

```xml
<!-- pom.xml -->
<dependency>
  <groupId>org.apache.gluten</groupId>
  <artifactId>gluten-delta</artifactId>
  <version>${gluten.version}</version>
</dependency>

<dependency>
  <groupId>io.delta</groupId>
  <artifactId>delta-core_2.12</artifactId>
  <version>2.4.0</version>
</dependency>
```

**Scala集成代码**

```scala
// gluten-delta/src/main/scala/org/apache/gluten/execution/DeltaScanTransformer.scala
package org.apache.gluten.execution

import io.delta.tables.DeltaTable
import org.apache.spark.sql.delta.DeltaLog
import org.apache.spark.sql.delta.files.TahoeFileIndex
import org.apache.spark.sql.execution.datasources.v2.BatchScanExec

class DeltaScanTransformer extends ScanTransformerApi {
  
  override def transform(plan: BatchScanExec): TransformResult = {
    plan.scan match {
      case deltaScan: DeltaBatchScan =>
        // 获取Delta元数据
        val deltaLog = deltaScan.deltaLog
        val snapshot = deltaLog.snapshot
        
        // 转换为Gluten可处理的格式
        val addFiles = snapshot.allFiles.collect()
        val partitionSchema = snapshot.metadata.partitionSchema
        
        // 创建原生扫描节点
        val nativeScan = NativeDeltaScan(
          addFiles.map(_.path),
          partitionSchema,
          snapshot.metadata.schema,
          deltaScan.filters
        )
        
        TransformResult(nativeScan, Seq.empty)
      
      case _ => TransformResult.skip
    }
  }
}

// Delta特定的扫描节点
case class NativeDeltaScan(
    paths: Seq[String],
    partitionSchema: StructType,
    dataSchema: StructType,
    filters: Seq[Expression]) extends LeafExecNode {
  
  override def doExecuteColumnar(): RDD[ColumnarBatch] = {
    // 调用C++层处理Delta文件
    val context = TaskContext.get()
    val iterator = NativeDeltaReader.read(
      paths,
      partitionSchema,
      dataSchema,
      filters,
      context.partitionId()
    )
    
    new RDD[ColumnarBatch](sparkContext, Nil) {
      override def compute(
          split: Partition,
          context: TaskContext): Iterator[ColumnarBatch] = {
        iterator
      }
      
      override protected def getPartitions: Array[Partition] = {
        Array.tabulate(paths.length)(i => new Partition { def index: Int = i })
      }
    }
  }
}
```

**使用示例**

```scala
import io.delta.tables._
import org.apache.spark.sql.SparkSession

val spark = SparkSession.builder()
  .config("spark.plugins", "org.apache.gluten.GlutenPlugin")
  .config("spark.gluten.sql.columnar.backend.lib", "velox")
  .config("spark.sql.extensions", "io.delta.sql.DeltaSparkSessionExtension")
  .config("spark.sql.catalog.spark_catalog", "org.apache.spark.sql.delta.catalog.DeltaCatalog")
  .getOrCreate()

// 读取Delta表
val deltaDF = spark.read.format("delta").load("/data/delta-table")

// 时间旅行查询
val historicalDF = spark.read
  .format("delta")
  .option("versionAsOf", "10")
  .load("/data/delta-table")

// 使用Gluten加速查询
deltaDF.filter($"date" >= "2024-01-01")
  .groupBy("category")
  .agg(sum("amount").as("total"))
  .write
  .format("delta")
  .mode("overwrite")
  .save("/data/output")
```

### 17.2.2 Iceberg集成

```scala
// gluten-iceberg/src/main/scala/org/apache/gluten/execution/IcebergScanTransformer.scala
package org.apache.gluten.execution

import org.apache.iceberg.{Table, TableScan}
import org.apache.iceberg.spark.source.SparkBatchQueryScan

class IcebergScanTransformer extends ScanTransformerApi {
  
  override def transform(plan: BatchScanExec): TransformResult = {
    plan.scan match {
      case icebergScan: SparkBatchQueryScan =>
        val table = icebergScan.table()
        val snapshot = table.currentSnapshot()
        
        // 获取数据文件
        val tasks = icebergScan.tasks()
        val dataFiles = tasks.map(_.file().path().toString)
        
        // 转换分区信息
        val spec = table.spec()
        val partitionFields = spec.fields().asScala.map(_.name())
        
        // 创建原生扫描
        val nativeScan = NativeIcebergScan(
          dataFiles.toSeq,
          table.schema(),
          icebergScan.filterExpressions(),
          partitionFields.toSeq
        )
        
        TransformResult(nativeScan, Seq.empty)
      
      case _ => TransformResult.skip
    }
  }
}
```

**Iceberg配置**

```properties
# spark-defaults.conf
spark.sql.catalog.local=org.apache.iceberg.spark.SparkCatalog
spark.sql.catalog.local.type=hadoop
spark.sql.catalog.local.warehouse=/data/iceberg-warehouse

spark.gluten.sql.columnar.backend.lib=velox
spark.gluten.sql.datasource.iceberg.enabled=true
```

### 17.2.3 Hudi集成

```scala
// gluten-hudi/src/main/scala/org/apache/gluten/execution/HudiScanTransformer.scala
package org.apache.gluten.execution

import org.apache.hudi.common.table.HoodieTableMetaClient
import org.apache.spark.sql.hudi.HoodieSqlCommonUtils

class HudiScanTransformer extends ScanTransformerApi {
  
  override def transform(plan: BatchScanExec): TransformResult = {
    // 检测Hudi表
    val isHudiTable = plan.scan.description().contains("HoodieFileIndex")
    
    if (isHudiTable) {
      // 获取Hudi元数据
      val basePath = extractBasePath(plan)
      val metaClient = HoodieTableMetaClient.builder()
        .setBasePath(basePath)
        .build()
      
      // 获取最新提交
      val timeline = metaClient.getActiveTimeline
      val latestCommit = timeline.lastInstant()
      
      // 创建原生扫描
      val nativeScan = NativeHudiScan(
        basePath,
        latestCommit.getTimestamp,
        plan.output
      )
      
      TransformResult(nativeScan, Seq.empty)
    } else {
      TransformResult.skip
    }
  }
  
  private def extractBasePath(plan: BatchScanExec): String = {
    // 从计划中提取Hudi表路径
    // ...
  }
}
```

### 17.2.4 Paimon集成

```scala
// gluten-paimon/src/main/scala/org/apache/gluten/execution/PaimonScanTransformer.scala
package org.apache.gluten.execution

import org.apache.paimon.table.FileStoreTable

class PaimonScanTransformer extends ScanTransformerApi {
  
  override def transform(plan: BatchScanExec): TransformResult = {
    plan.scan match {
      case paimonScan: PaimonBatchScan =>
        val table = paimonScan.table.asInstanceOf[FileStoreTable]
        val snapshot = table.snapshotManager().latestSnapshot()
        
        // 读取数据文件
        val splits = paimonScan.getSplits
        val files = splits.flatMap(_.files())
        
        // 创建原生扫描
        val nativeScan = NativePaimonScan(
          files.map(_.path()),
          table.schema(),
          paimonScan.requiredSchema
        )
        
        TransformResult(nativeScan, Seq.empty)
      
      case _ => TransformResult.skip
    }
  }
}
```

**数据源集成性能对比**

| 数据源 | 原生Spark | Gluten加速 | 加速比 | 特性支持 |
|--------|----------|-----------|--------|---------|
| Delta Lake | 基线 | 2.1x | 2.1x | 时间旅行、ACID |
| Iceberg | 基线 | 2.3x | 2.3x | 分区演化、隐藏分区 |
| Hudi | 基线 | 1.9x | 1.9x | Upsert、增量查询 |
| Paimon | 基线 | 2.0x | 2.0x | 流批一体 |

## 17.3 Shuffle插件集成

### 17.3.1 Celeborn架构和实现

Celeborn（原名RemoteShuffleService）提供远程Shuffle服务，提高大规模Shuffle性能。

**Java客户端实现**

```java
// gluten-celeborn/src/main/java/org/apache/gluten/shuffle/CelebornShuffleManager.java
package org.apache.gluten.shuffle;

import org.apache.celeborn.client.ShuffleClient;
import org.apache.spark.shuffle.*;

public class CelebornShuffleManager implements ShuffleManager {
  
  private ShuffleClient celebornClient;
  private final Map<Integer, ShuffleHandle> handles = new ConcurrentHashMap<>();
  
  public CelebornShuffleManager(SparkConf conf) {
    // 初始化Celeborn客户端
    this.celebornClient = ShuffleClient.builder()
        .masterEndpoints(conf.get("spark.celeborn.master.endpoints"))
        .appId(conf.getAppId())
        .lifecycleManagerHost(conf.get("spark.celeborn.lifecycle.host"))
        .lifecycleManagerPort(conf.getInt("spark.celeborn.lifecycle.port", 9097))
        .build();
  }
  
  @Override
  public <K, V, C> ShuffleHandle registerShuffle(
      int shuffleId,
      ShuffleDependency<K, V, C> dependency) {
    
    // 注册Shuffle到Celeborn
    celebornClient.registerShuffle(
        shuffleId,
        dependency.partitioner().numPartitions(),
        dependency.serializer()
    );
    
    CelebornShuffleHandle<K, V, C> handle = 
        new CelebornShuffleHandle<>(shuffleId, dependency);
    handles.put(shuffleId, handle);
    
    return handle;
  }
  
  @Override
  public <K, V> ShuffleWriter<K, V> getWriter(
      ShuffleHandle handle,
      long mapId,
      TaskContext context,
      ShuffleWriteMetricsReporter metrics) {
    
    return new CelebornShuffleWriter<>(
        celebornClient,
        handle.shuffleId(),
        mapId,
        context,
        metrics
    );
  }
  
  @Override
  public <K, C> ShuffleReader<K, C> getReader(
      ShuffleHandle handle,
      int startPartition,
      int endPartition,
      TaskContext context,
      ShuffleReadMetricsReporter metrics) {
    
    return new CelebornShuffleReader<>(
        celebornClient,
        handle.shuffleId(),
        startPartition,
        endPartition,
        context,
        metrics
    );
  }
  
  @Override
  public boolean unregisterShuffle(int shuffleId) {
    celebornClient.unregisterShuffle(shuffleId);
    handles.remove(shuffleId);
    return true;
  }
  
  @Override
  public void stop() {
    celebornClient.close();
  }
}

// Shuffle写入器
class CelebornShuffleWriter<K, V> extends ShuffleWriter<K, V> {
  
  private final ShuffleClient client;
  private final int shuffleId;
  private final long mapId;
  private final ShuffleWriteMetricsReporter metrics;
  
  @Override
  public void write(Iterator<Product2<K, V>> records) throws IOException {
    // 按分区ID组织数据
    Map<Integer, List<Product2<K, V>>> partitionedData = new HashMap<>();
    
    while (records.hasNext()) {
      Product2<K, V> record = records.next();
      int partitionId = partitioner.getPartition(record._1());
      
      partitionedData
          .computeIfAbsent(partitionId, k -> new ArrayList<>())
          .add(record);
    }
    
    // 写入每个分区
    for (Map.Entry<Integer, List<Product2<K, V>>> entry : partitionedData.entrySet()) {
      int partitionId = entry.getKey();
      List<Product2<K, V>> data = entry.getValue();
      
      // 序列化并推送到Celeborn
      ByteBuffer buffer = serializeData(data);
      client.pushData(shuffleId, mapId, partitionId, buffer);
      
      metrics.incBytesWritten(buffer.remaining());
      metrics.incRecordsWritten(data.size());
    }
  }
  
  private ByteBuffer serializeData(List<Product2<K, V>> data) {
    // 使用Spark序列化器
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    SerializationStream stream = serializer.newInstance().serializeStream(baos);
    
    for (Product2<K, V> record : data) {
      stream.writeKey(record._1());
      stream.writeValue(record._2());
    }
    
    stream.close();
    return ByteBuffer.wrap(baos.toByteArray());
  }
}
```

**Celeborn配置**

```properties
# spark-defaults.conf
spark.shuffle.manager=org.apache.spark.shuffle.celeborn.CelebornShuffleManager
spark.celeborn.master.endpoints=clb-master:9097
spark.celeborn.shuffle.partition.split.threshold=256MB
spark.celeborn.push.replicate.enabled=true
spark.celeborn.push.buffer.size=256k

# Gluten特定配置
spark.gluten.sql.columnar.shuffle.enabled=true
spark.gluten.sql.columnar.shuffle.codec=lz4
spark.gluten.sql.columnar.shuffle.preferSpill=true
```

### 17.3.2 Uniffle集成

```scala
// gluten-uniffle/src/main/scala/org/apache/gluten/shuffle/UniffleShuffleManager.scala
package org.apache.gluten.shuffle

import org.apache.uniffle.client.api.ShuffleWriteClient
import org.apache.spark.shuffle._

class UniffleShuffleManager(conf: SparkConf) extends ShuffleManager {
  
  private val writeClient = ShuffleWriteClient.builder()
    .coordinatorQuorum(conf.get("spark.rss.coordinator.quorum"))
    .build()
  
  override def registerShuffle[K, V, C](
      shuffleId: Int,
      dependency: ShuffleDependency[K, V, C]): ShuffleHandle = {
    
    // 向Uniffle协调器注册
    val serverList = writeClient.getShuffleServerList(
      shuffleId,
      dependency.partitioner.numPartitions
    )
    
    new UniffleShuffleHandle(shuffleId, dependency, serverList)
  }
  
  // Writer和Reader实现类似Celeborn
}
```

### 17.3.3 Shuffle插件性能对比

| Shuffle方案 | 吞吐量(GB/s) | 延迟(ms) | CPU使用率 | 适用场景 |
|------------|-------------|---------|----------|---------|
| Spark原生 | 1.2 | 150 | 75% | 中小规模 |
| Celeborn | 3.8 | 45 | 35% | 大规模、稳定性要求高 |
| Uniffle | 3.5 | 50 | 40% | 大规模、成本敏感 |
| Gluten+Celeborn | 4.5 | 35 | 30% | 列式+远程Shuffle |

## 17.4 Kafka集成

### 17.4.1 流式处理架构

```scala
// gluten-kafka/src/main/scala/org/apache/gluten/streaming/KafkaSourceTransformer.scala
package org.apache.gluten.streaming

import org.apache.spark.sql.kafka010._
import org.apache.spark.sql.execution.streaming.sources.MicroBatchReader

class KafkaSourceTransformer extends StreamingSourceTransformerApi {
  
  def transform(reader: MicroBatchReader): TransformResult = {
    reader match {
      case kafkaReader: KafkaMicroBatchReader =>
        // 获取Kafka偏移量范围
        val offsetRanges = kafkaReader.rangeCalculator.getRanges()
        
        // 创建原生Kafka读取器
        val nativeReader = NativeKafkaReader(
          kafkaReader.kafkaParams,
          offsetRanges,
          kafkaReader.schema
        )
        
        TransformResult(nativeReader, Seq.empty)
      
      case _ => TransformResult.skip
    }
  }
}

// 原生Kafka读取器
case class NativeKafkaReader(
    kafkaParams: Map[String, String],
    offsetRanges: Seq[OffsetRange],
    schema: StructType) extends StreamingDataReader {
  
  override def readBatch(): Iterator[ColumnarBatch] = {
    // 调用C++层Kafka消费者
    val consumer = NativeKafkaConsumer.create(kafkaParams)
    
    offsetRanges.flatMap { range =>
      consumer.subscribe(range.topic, range.partition)
      consumer.seek(range.fromOffset)
      consumer.poll(range.untilOffset)
    }.iterator
  }
}
```

### 17.4.2 Kafka源实现

```cpp
// cpp/velox/streaming/KafkaConsumer.cpp
#include "velox/connectors/streaming/KafkaConsumer.h"
#include <librdkafka/rdkafkacpp.h>

namespace facebook::velox::streaming {

class KafkaConsumer {
 public:
  KafkaConsumer(const std::map<std::string, std::string>& config) {
    std::string errstr;
    
    // 创建Kafka配置
    conf_ = RdKafka::Conf::create(RdKafka::Conf::CONF_GLOBAL);
    for (const auto& [key, value] : config) {
      conf_->set(key, value, errstr);
    }
    
    // 创建消费者
    consumer_ = RdKafka::KafkaConsumer::create(conf_, errstr);
    if (!consumer_) {
      throw std::runtime_error("Failed to create Kafka consumer: " + errstr);
    }
  }
  
  std::vector<RowVectorPtr> poll(
      const std::string& topic,
      int32_t partition,
      int64_t startOffset,
      int64_t endOffset,
      memory::MemoryPool* pool) {
    
    // 分配分区
    RdKafka::TopicPartition* tp = 
        RdKafka::TopicPartition::create(topic, partition, startOffset);
    std::vector<RdKafka::TopicPartition*> partitions = {tp};
    consumer_->assign(partitions);
    
    std::vector<RowVectorPtr> batches;
    int64_t currentOffset = startOffset;
    
    while (currentOffset < endOffset) {
      // 拉取消息
      auto* message = consumer_->consume(1000);  // 1s超时
      
      if (message->err() == RdKafka::ERR_NO_ERROR) {
        // 解析消息到RowVector
        auto batch = parseMessageBatch(message, pool);
        batches.push_back(batch);
        
        currentOffset = message->offset() + 1;
      }
      
      delete message;
    }
    
    return batches;
  }
  
 private:
  RowVectorPtr parseMessageBatch(
      RdKafka::Message* message,
      memory::MemoryPool* pool) {
    
    // 将Kafka消息转换为Velox RowVector
    // 假设消息是JSON格式
    std::string payload(
        static_cast<const char*>(message->payload()),
        message->len());
    
    // 解析JSON并创建RowVector
    // ...
  }
  
  RdKafka::Conf* conf_;
  RdKafka::KafkaConsumer* consumer_;
};

} // namespace facebook::velox::streaming
```

### 17.4.3 使用示例

```scala
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.streaming.Trigger

object KafkaStreamingExample {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder()
      .appName("Gluten Kafka Streaming")
      .config("spark.plugins", "org.apache.gluten.GlutenPlugin")
      .config("spark.gluten.sql.columnar.backend.lib", "velox")
      .config("spark.gluten.streaming.enabled", "true")
      .getOrCreate()
    
    // 从Kafka读取流
    val kafkaStream = spark.readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", "localhost:9092")
      .option("subscribe", "transactions")
      .option("startingOffsets", "latest")
      .load()
    
    // 解析JSON并处理
    import spark.implicits._
    
    val processedStream = kafkaStream
      .selectExpr("CAST(value AS STRING) as json")
      .select(from_json($"json", schema).as("data"))
      .select("data.*")
      .filter($"amount" > 100)
      .groupBy(window($"timestamp", "1 minute"), $"category")
      .agg(
        sum("amount").as("total_amount"),
        count("*").as("count")
      )
    
    // 写入输出
    val query = processedStream.writeStream
      .format("parquet")
      .option("path", "/output/streaming")
      .option("checkpointLocation", "/checkpoint/streaming")
      .trigger(Trigger.ProcessingTime("10 seconds"))
      .start()
    
    query.awaitTermination()
  }
}
```

## 17.5 配置最佳实践

### 17.5.1 扩展配置模板

```properties
# ============ UDF配置 ============
spark.gluten.sql.columnar.backend.velox.udfLibraryPaths=/opt/gluten/lib/libcustom_udf.so
spark.executorEnv.LD_LIBRARY_PATH=/opt/velox/lib:/opt/gluten/lib

# ============ 数据源配置 ============
# Delta Lake
spark.sql.extensions=io.delta.sql.DeltaSparkSessionExtension
spark.sql.catalog.spark_catalog=org.apache.spark.sql.delta.catalog.DeltaCatalog
spark.gluten.sql.datasource.delta.enabled=true

# Iceberg
spark.sql.catalog.local=org.apache.iceberg.spark.SparkCatalog
spark.sql.catalog.local.type=hadoop
spark.gluten.sql.datasource.iceberg.enabled=true

# ============ Shuffle配置 ============
# Celeborn
spark.shuffle.manager=org.apache.spark.shuffle.celeborn.CelebornShuffleManager
spark.celeborn.master.endpoints=master1:9097,master2:9097
spark.celeborn.push.replicate.enabled=true
spark.celeborn.push.buffer.size=256k
spark.gluten.sql.columnar.shuffle.codec=lz4

# ============ Kafka配置 ============
spark.gluten.streaming.enabled=true
spark.streaming.kafka.consumer.poll.ms=512
spark.streaming.backpressure.enabled=true
```

### 17.5.2 扩展开发检查清单

| 检查项 | 说明 | 优先级 |
|--------|------|--------|
| 内存管理 | 确保UDF正确使用Velox MemoryPool | 高 |
| 异常处理 | 实现完善的错误处理和回退机制 | 高 |
| 性能测试 | 对比原生Spark验证加速效果 | 高 |
| 线程安全 | 确保多线程环境下的正确性 | 高 |
| 资源清理 | 正确释放C++资源避免泄漏 | 高 |
| 日志记录 | 添加详细日志便于调试 | 中 |
| 文档更新 | 编写使用文档和API说明 | 中 |
| 兼容性测试 | 测试不同Spark版本的兼容性 | 中 |

## 17.6 小结

本章详细介绍了Apache Gluten的扩展开发方法：

1. **自定义UDF开发**：涵盖Velox和ClickHouse后端的标量函数、聚合函数和复杂UDF实现
2. **数据源集成**：展示了Delta Lake、Iceberg、Hudi和Paimon的集成方案
3. **Shuffle插件**：实现了Celeborn和Uniffle的远程Shuffle加速
4. **Kafka集成**：提供了流式处理的原生加速方案
5. **最佳实践**：配置模板和开发检查清单确保扩展质量

通过这些扩展机制，开发者可以根据实际需求定制Gluten功能，实现更高的性能和更好的生态集成。下一章将介绍测试与质量保证体系。
