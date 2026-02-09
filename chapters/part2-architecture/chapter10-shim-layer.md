# 第10章：多版本兼容（Shim Layer）

> **本章要点**：
> - 理解为什么需要 Shim Layer
> - 掌握 Shim Layer 的架构设计
> - 学习多 Spark 版本的支持机制
> - 理解版本差异的处理策略
> - 掌握如何添加新 Spark 版本支持

## 引言

Spark 在快速演进，每个大版本都会引入 API 变更。Gluten 需要同时支持多个 Spark 版本（3.2, 3.3, 3.4, 3.5），这就需要一个抽象层来隔离版本差异。Shim Layer 就是解决这个问题的关键设计。

## 10.1 为什么需要 Shim Layer

### 10.1.1 Spark 版本演进

**Spark 3.x 版本时间线**：

```
2020.09 - Spark 3.0.0  (Columnar API 引入)
2021.03 - Spark 3.1.0
2021.10 - Spark 3.2.0  ← Gluten 支持
2022.06 - Spark 3.3.0  ← Gluten 支持
2023.04 - Spark 3.4.0  ← Gluten 支持
2024.02 - Spark 3.5.0  ← Gluten 支持
```

### 10.1.2 API 变更示例

**示例1：ColumnarBatch 构造器**

```scala
// Spark 3.2
class ColumnarBatch(
  val columns: Array[ColumnVector],
  val numRows: Int
)

// Spark 3.3+ (添加了新参数)
class ColumnarBatch(
  val columns: Array[ColumnVector],
  val numRows: Int,
  val capacity: Int  // 新增！
)
```

**示例2：ShuffleManager 接口**

```scala
// Spark 3.2
abstract class ShuffleManager {
  def getWriter[K, V](
    handle: ShuffleHandle,
    mapId: Int,  // Int 类型
    context: TaskContext
  ): ShuffleWriter[K, V]
}

// Spark 3.3+
abstract class ShuffleManager {
  def getWriter[K, V](
    handle: ShuffleHandle,
    mapId: Long,  // 改为 Long！
    context: TaskContext,
    metrics: ShuffleWriteMetrics  // 新增参数！
  ): ShuffleWriter[K, V]
}
```

**示例3：内部类位置变更**

```scala
// Spark 3.2
import org.apache.spark.sql.execution.datasources.FileFormat

// Spark 3.3
import org.apache.spark.sql.execution.datasources.v2.FileFormat
// 包路径改变！
```

### 10.1.3 不使用 Shim 的问题

**问题1：编译错误**

```scala
// 针对 Spark 3.2 编写的代码
class MyShuffleManager extends ShuffleManager {
  override def getWriter[K, V](
    handle: ShuffleHandle,
    mapId: Int,
    context: TaskContext
  ): ShuffleWriter[K, V] = {
    // ...
  }
}

// 在 Spark 3.3 上编译：
// Error: method getWriter has wrong number of parameters
```

**问题2：运行时错误**

```scala
// 使用反射绕过编译
val constructor = classOf[ColumnarBatch]
  .getConstructor(classOf[Array[ColumnVector]], classOf[Int])

constructor.newInstance(columns, numRows)

// Spark 3.3+: NoSuchMethodException!
```

**问题3：维护困难**

```scala
// 到处都是版本判断
if (sparkVersion == "3.2") {
  // Spark 3.2 代码
} else if (sparkVersion == "3.3") {
  // Spark 3.3 代码
} else if (sparkVersion == "3.4") {
  // Spark 3.4 代码
}
// 代码重复，难以维护
```

## 10.2 Shim Layer 架构设计

### 10.2.1 整体架构

```
┌─────────────────────────────────────┐
│   Gluten Core (通用逻辑)            │
│   - 算子 Transformer                │
│   - 内存管理                        │
│   - JNI Bridge                      │
└──────────────┬──────────────────────┘
               │ 调用
               ↓
┌─────────────────────────────────────┐
│   Shim API (抽象接口)               │
│   interface ShimDescriptor          │
│   interface SparkShim               │
└──────────────┬──────────────────────┘
               │ 实现
        ┌──────┴──────┬────────┬──────┐
        ↓             ↓        ↓      ↓
  ┌─────────┐   ┌─────────┐  ...    ...
  │ Spark32 │   │ Spark33 │
  │  Shim   │   │  Shim   │
  └─────────┘   └─────────┘
```

### 10.2.2 Shim 接口定义

```scala
// gluten-core/src/main/scala/io/glutenproject/shims/SparkShim.scala
trait SparkShim {
  // Spark 版本信息
  def getSparkVersion: String
  def majorVersion: String
  def minorVersion: String
  
  // ColumnarBatch 创建
  def createColumnarBatch(
    columns: Array[ColumnVector],
    numRows: Int
  ): ColumnarBatch
  
  // ShuffleManager 相关
  def getShuffleWriter[K, V](
    manager: ShuffleManager,
    handle: ShuffleHandle,
    mapId: Long,
    context: TaskContext,
    metrics: ShuffleWriteMetrics
  ): ShuffleWriter[K, V]
  
  // Expression 转换
  def transformExpression(expr: Expression): Expression
  
  // Physical Plan 优化规则
  def getExtraStrategies: Seq[Strategy]
  
  // 数据类型兼容
  def dataTypeFromString(typeString: String): DataType
  
  // Metrics 相关
  def createTaskMetrics(): TaskMetrics
  
  // 文件扫描相关
  def createFileScan(
    sparkSession: SparkSession,
    fileFormat: FileFormat,
    paths: Seq[String],
    dataSchema: StructType,
    partitionSchema: StructType
  ): SparkPlan
}
```

### 10.2.3 Shim 加载机制

```scala
// gluten-core/src/main/scala/io/glutenproject/shims/ShimLoader.scala
object ShimLoader {
  
  private lazy val sparkShim: SparkShim = loadShim()
  
  def getSparkShim: SparkShim = sparkShim
  
  private def loadShim(): SparkShim = {
    val sparkVersion = org.apache.spark.SPARK_VERSION
    
    // 解析版本号
    val Array(major, minor, patch) = sparkVersion.split('.')
    
    // 根据版本加载对应的 Shim
    val shimClassName = s"io.glutenproject.shims.spark${major}${minor}.Spark${major}${minor}Shim"
    
    try {
      val shimClass = Class.forName(shimClassName)
      val shim = shimClass.newInstance().asInstanceOf[SparkShim]
      
      println(s"Loaded Shim: ${shimClassName} for Spark ${sparkVersion}")
      shim
      
    } catch {
      case e: ClassNotFoundException =>
        throw new RuntimeException(
          s"No Shim found for Spark ${sparkVersion}. " +
          s"Supported versions: 3.2, 3.3, 3.4, 3.5",
          e
        )
    }
  }
}
```

### 10.2.4 使用 Shim

```scala
// 在 Gluten 代码中使用 Shim
import io.glutenproject.shims.ShimLoader

class MyColumnarOperator {
  private val shim = ShimLoader.getSparkShim
  
  def createBatch(columns: Array[ColumnVector], numRows: Int): ColumnarBatch = {
    // 通过 Shim 创建，自动适配版本
    shim.createColumnarBatch(columns, numRows)
  }
  
  def getWriter[K, V](
    manager: ShuffleManager,
    handle: ShuffleHandle,
    mapId: Long,
    context: TaskContext,
    metrics: ShuffleWriteMetrics
  ): ShuffleWriter[K, V] = {
    // 通过 Shim 调用，自动适配参数
    shim.getShuffleWriter(manager, handle, mapId, context, metrics)
  }
}
```

## 10.3 多 Spark 版本支持（3.2, 3.3, 3.4, 3.5）

### 10.3.1 目录结构

```
gluten/
├── gluten-core/                     # 通用核心
│   └── src/main/scala/io/glutenproject/
│       ├── execution/               # 算子实现
│       └── shims/
│           ├── ShimLoader.scala     # Shim 加载器
│           └── SparkShim.scala      # Shim 接口
│
├── shims/                           # Shim 实现
│   ├── spark32/                     # Spark 3.2
│   │   └── src/main/scala/io/glutenproject/shims/spark32/
│   │       └── Spark32Shim.scala
│   │
│   ├── spark33/                     # Spark 3.3
│   │   └── src/main/scala/io/glutenproject/shims/spark33/
│   │       └── Spark33Shim.scala
│   │
│   ├── spark34/                     # Spark 3.4
│   │   └── src/main/scala/io/glutenproject/shims/spark34/
│   │       └── Spark34Shim.scala
│   │
│   └── spark35/                     # Spark 3.5
│       └── src/main/scala/io/glutenproject/shims/spark35/
│           └── Spark35Shim.scala
```

### 10.3.2 Spark 3.2 Shim 实现

```scala
// shims/spark32/src/main/scala/.../Spark32Shim.scala
package io.glutenproject.shims.spark32

class Spark32Shim extends SparkShim {
  
  override def getSparkVersion: String = "3.2"
  
  override def majorVersion: String = "3"
  override def minorVersion: String = "2"
  
  // Spark 3.2 的 ColumnarBatch 只有 2 个参数
  override def createColumnarBatch(
    columns: Array[ColumnVector],
    numRows: Int
  ): ColumnarBatch = {
    new ColumnarBatch(columns, numRows)
  }
  
  // Spark 3.2 的 mapId 是 Int 类型
  override def getShuffleWriter[K, V](
    manager: ShuffleManager,
    handle: ShuffleHandle,
    mapId: Long,
    context: TaskContext,
    metrics: ShuffleWriteMetrics
  ): ShuffleWriter[K, V] = {
    // 调用 Spark 3.2 API（mapId 转为 Int）
    manager.getWriter(handle, mapId.toInt, context)
  }
  
  // Spark 3.2 特定的 Expression 转换
  override def transformExpression(expr: Expression): Expression = {
    expr match {
      case cast: Cast =>
        // Spark 3.2 的 Cast 构造器
        Cast(cast.child, cast.dataType)
      
      case _ => expr
    }
  }
  
  // Spark 3.2 的优化规则
  override def getExtraStrategies: Seq[Strategy] = {
    Seq(
      Spark32SpecificStrategy1,
      Spark32SpecificStrategy2
    )
  }
}
```

### 10.3.3 Spark 3.3 Shim 实现

```scala
// shims/spark33/src/main/scala/.../Spark33Shim.scala
package io.glutenproject.shims.spark33

class Spark33Shim extends SparkShim {
  
  override def getSparkVersion: String = "3.3"
  
  override def majorVersion: String = "3"
  override def minorVersion: String = "3"
  
  // Spark 3.3+ 的 ColumnarBatch 有 3 个参数
  override def createColumnarBatch(
    columns: Array[ColumnVector],
    numRows: Int
  ): ColumnarBatch = {
    new ColumnarBatch(columns, numRows, numRows)  // capacity = numRows
  }
  
  // Spark 3.3 的 mapId 是 Long 类型，且多了 metrics 参数
  override def getShuffleWriter[K, V](
    manager: ShuffleManager,
    handle: ShuffleHandle,
    mapId: Long,
    context: TaskContext,
    metrics: ShuffleWriteMetrics
  ): ShuffleWriter[K, V] = {
    // 直接调用 Spark 3.3 API
    manager.getWriter(handle, mapId, context, metrics)
  }
  
  // Spark 3.3 特定的 Expression 转换
  override def transformExpression(expr: Expression): Expression = {
    expr match {
      case cast: Cast =>
        // Spark 3.3 的 Cast 增加了 timeZoneId 参数
        Cast(cast.child, cast.dataType, cast.timeZoneId)
      
      case _ => expr
    }
  }
  
  // Spark 3.3 的优化规则
  override def getExtraStrategies: Seq[Strategy] = {
    Seq(
      Spark33SpecificStrategy1,
      Spark33SpecificStrategy2
    )
  }
}
```

### 10.3.4 编译配置

**Maven POM 配置**：

```xml
<!-- pom.xml -->
<project>
  <properties>
    <spark.version>3.3.1</spark.version>
  </properties>
  
  <profiles>
    <!-- Spark 3.2 Profile -->
    <profile>
      <id>spark-3.2</id>
      <properties>
        <spark.version>3.2.2</spark.version>
        <spark.major.version>3.2</spark.major.version>
      </properties>
      <dependencies>
        <dependency>
          <groupId>io.glutenproject</groupId>
          <artifactId>gluten-shims-spark32</artifactId>
          <version>${project.version}</version>
        </dependency>
      </dependencies>
    </profile>
    
    <!-- Spark 3.3 Profile -->
    <profile>
      <id>spark-3.3</id>
      <activation>
        <activeByDefault>true</activeByDefault>
      </activation>
      <properties>
        <spark.version>3.3.1</spark.version>
        <spark.major.version>3.3</spark.major.version>
      </properties>
      <dependencies>
        <dependency>
          <groupId>io.glutenproject</groupId>
          <artifactId>gluten-shims-spark33</artifactId>
          <version>${project.version}</version>
        </dependency>
      </dependencies>
    </profile>
    
    <!-- Spark 3.4 Profile -->
    <profile>
      <id>spark-3.4</id>
      <properties>
        <spark.version>3.4.0</spark.version>
        <spark.major.version>3.4</spark.major.version>
      </properties>
      <dependencies>
        <dependency>
          <groupId>io.glutenproject</groupId>
          <artifactId>gluten-shims-spark34</artifactId>
          <version>${project.version}</version>
        </dependency>
      </dependencies>
    </profile>
    
    <!-- Spark 3.5 Profile -->
    <profile>
      <id>spark-3.5</id>
      <properties>
        <spark.version>3.5.0</spark.version>
        <spark.major.version>3.5</spark.major.version>
      </properties>
      <dependencies>
        <dependency>
          <groupId>io.glutenproject</groupId>
          <artifactId>gluten-shims-spark35</artifactId>
          <version>${project.version}</version>
        </dependency>
      </dependencies>
    </profile>
  </profiles>
</project>
```

**编译命令**：

```bash
# 编译 Spark 3.2 版本
mvn clean package -Pspark-3.2

# 编译 Spark 3.3 版本
mvn clean package -Pspark-3.3

# 编译 Spark 3.4 版本
mvn clean package -Pspark-3.4

# 编译 Spark 3.5 版本
mvn clean package -Pspark-3.5
```

## 10.4 版本差异处理

### 10.4.1 常见版本差异

**1. 方法签名变更**

```scala
// Spark 3.2
def doExecuteColumnar(): RDD[ColumnarBatch]

// Spark 3.3+
def doExecuteColumnar(): RDD[ColumnarBatch] = {
  // 新增 metrics 参数
  doExecuteColumnarInternal(
    context.taskMetrics().createShuffleReadMetrics()
  )
}
```

**处理方式**：在 Shim 中封装

```scala
trait SparkShim {
  def executeColumnarWithMetrics(
    operator: SparkPlan,
    context: TaskContext
  ): RDD[ColumnarBatch]
}

// Spark 3.2 Shim
class Spark32Shim extends SparkShim {
  override def executeColumnarWithMetrics(
    operator: SparkPlan,
    context: TaskContext
  ): RDD[ColumnarBatch] = {
    operator.doExecuteColumnar()  // 无 metrics 参数
  }
}

// Spark 3.3 Shim
class Spark33Shim extends SparkShim {
  override def executeColumnarWithMetrics(
    operator: SparkPlan,
    context: TaskContext
  ): RDD[ColumnarBatch] = {
    operator.doExecuteColumnarInternal(
      context.taskMetrics().createShuffleReadMetrics()
    )
  }
}
```

**2. 类位置变更**

```scala
// Spark 3.2
import org.apache.spark.sql.execution.FileSourceScanExec

// Spark 3.3
import org.apache.spark.sql.execution.datasources.v2.FileSourceScanExec
```

**处理方式**：使用 Type Alias

```scala
// Shim 接口
trait SparkShim {
  type FileScanType <: SparkPlan
  
  def createFileScan(...): FileScanType
}

// Spark 3.2 Shim
class Spark32Shim extends SparkShim {
  override type FileScanType = 
    org.apache.spark.sql.execution.FileSourceScanExec
}

// Spark 3.3 Shim
class Spark33Shim extends SparkShim {
  override type FileScanType = 
    org.apache.spark.sql.execution.datasources.v2.FileSourceScanExec
}
```

**3. 新增功能**

```scala
// Spark 3.4 新增 AQE 特性
if (conf.adaptiveExecutionEnabled) {
  // 只在 Spark 3.4+ 可用
}
```

**处理方式**：提供默认实现

```scala
trait SparkShim {
  def supportsAdaptiveExecution: Boolean = false
  
  def applyAdaptiveOptimization(plan: SparkPlan): SparkPlan = plan
}

// Spark 3.4 Shim
class Spark34Shim extends SparkShim {
  override def supportsAdaptiveExecution: Boolean = true
  
  override def applyAdaptiveOptimization(plan: SparkPlan): SparkPlan = {
    // 应用 Spark 3.4 的 AQE 优化
    AdaptiveSparkPlanExec(plan)
  }
}
```

### 10.4.2 版本兼容性测试

```scala
// 测试所有支持的 Spark 版本
class ShimCompatibilityTest extends FunSuite {
  
  test("Spark 3.2 compatibility") {
    withSparkVersion("3.2.2") {
      val shim = ShimLoader.getSparkShim
      assert(shim.getSparkVersion == "3.2")
      
      // 测试各项功能
      testColumnarBatch(shim)
      testShuffleWriter(shim)
      testExpressionTransform(shim)
    }
  }
  
  test("Spark 3.3 compatibility") {
    withSparkVersion("3.3.1") {
      val shim = ShimLoader.getSparkShim
      assert(shim.getSparkVersion == "3.3")
      
      testColumnarBatch(shim)
      testShuffleWriter(shim)
      testExpressionTransform(shim)
    }
  }
  
  test("Spark 3.4 compatibility") {
    withSparkVersion("3.4.0") {
      val shim = ShimLoader.getSparkShim
      assert(shim.getSparkVersion == "3.4")
      
      testColumnarBatch(shim)
      testShuffleWriter(shim)
      testExpressionTransform(shim)
      testAdaptiveExecution(shim)  // 新功能
    }
  }
  
  private def testColumnarBatch(shim: SparkShim): Unit = {
    val columns = Array(new IntegerColumnVector(1000))
    val batch = shim.createColumnarBatch(columns, 1000)
    
    assert(batch.numRows() == 1000)
    assert(batch.numCols() == 1)
  }
  
  private def testShuffleWriter(shim: SparkShim): Unit = {
    // 测试 Shuffle Writer 创建
    val writer = shim.getShuffleWriter(...)
    assert(writer != null)
  }
}
```

## 10.5 添加新 Spark 版本支持

### 10.5.1 步骤指南

**Step 1：创建新 Shim 模块**

```bash
# 假设添加 Spark 3.6 支持
mkdir -p shims/spark36/src/main/scala/io/glutenproject/shims/spark36
```

**Step 2：实现 Shim 接口**

```scala
// shims/spark36/src/main/scala/.../Spark36Shim.scala
package io.glutenproject.shims.spark36

class Spark36Shim extends SparkShim {
  override def getSparkVersion: String = "3.6"
  
  // 实现所有接口方法...
}
```

**Step 3：添加编译配置**

```xml
<!-- pom.xml -->
<profile>
  <id>spark-3.6</id>
  <properties>
    <spark.version>3.6.0</spark.version>
    <spark.major.version>3.6</spark.major.version>
  </properties>
  <dependencies>
    <dependency>
      <groupId>io.glutenproject</groupId>
      <artifactId>gluten-shims-spark36</artifactId>
      <version>${project.version}</version>
    </dependency>
  </dependencies>
</profile>
```

**Step 4：测试**

```bash
# 编译
mvn clean package -Pspark-3.6

# 测试
mvn test -Pspark-3.6
```

**Step 5：更新文档**

```markdown
## 支持的 Spark 版本

- Spark 3.2.x
- Spark 3.3.x
- Spark 3.4.x
- Spark 3.5.x
- Spark 3.6.x (新增)
```

### 10.5.2 迁移检查清单

- [ ] 检查 API 变更（参考 Spark Release Notes）
- [ ] 实现 SparkShim 接口的所有方法
- [ ] 处理废弃的 API
- [ ] 处理新增的 API
- [ ] 添加版本特定的优化规则
- [ ] 编写单元测试
- [ ] 编写集成测试
- [ ] 更新文档
- [ ] 更新 CI/CD 配置

## 本章小结

本章深入学习了 Shim Layer：

1. ✅ **设计理由**：理解了为什么需要 Shim Layer
2. ✅ **架构设计**：掌握了 Shim 的分层架构和接口设计
3. ✅ **多版本支持**：学习了如何支持 Spark 3.2-3.5
4. ✅ **差异处理**：掌握了版本差异的处理策略
5. ✅ **添加新版本**：了解了如何添加新 Spark 版本支持

至此，Part 2 架构篇全部完成！我们已经深入学习了：
- 查询计划转换
- 内存管理
- 数据格式与传输
- Columnar Shuffle
- Fallback 机制
- Shim Layer

下一部分（Part 3）将学习后端引擎，深入 Velox 和 ClickHouse 的实现细节。

## 参考资料

- [Spark Release Notes](https://spark.apache.org/releases/)
- [Spark API Evolution](https://spark.apache.org/versioning-policy.html)
- [Gluten Shim Design](https://github.com/apache/incubator-gluten/tree/main/shims)
- [Strategy Pattern](https://en.wikipedia.org/wiki/Strategy_pattern)

---

**下一章预告**：[第11章：Velox 后端详解](../part3-backends/chapter11-velox-backend.md) - 深入 Velox 执行引擎
