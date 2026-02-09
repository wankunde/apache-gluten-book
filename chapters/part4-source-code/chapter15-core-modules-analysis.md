# 第15章：核心模块源码分析

> 本章要点：
> - Gluten 核心模块架构设计
> - gluten-core 模块源码解析
> - gluten-substrait 计划转换实现
> - gluten-arrow 数据格式桥接
> - JNI 层实现机制
> - Plugin 注册和加载流程

## 引言

在搭建好开发环境后，本章将深入分析 Gluten 的核心模块源码。我们将从模块架构开始，逐步剖析各个核心组件的实现原理，包括 Gluten 插件机制、Substrait 计划转换、Arrow 数据桥接以及 JNI 层的设计。通过本章的学习，你将理解 Gluten 如何将 Spark SQL 查询转换为 Native 执行，以及各个模块之间如何协作。

## 15.1 模块架构概览

### 15.1.1 模块依赖关系

```
┌─────────────────────────────────────────────┐
│         Spark SQL (未修改)                   │
└────────────────┬────────────────────────────┘
                 │ SparkPlugin SPI
┌────────────────▼────────────────────────────┐
│          gluten-core                         │
│  ┌──────────────────────────────────────┐   │
│  │  GlutenPlugin (入口)                  │   │
│  │  GlutenPlan (物理计划扩展)            │   │
│  │  BackendFactory (后端抽象)            │   │
│  └──────────────────────────────────────┘   │
└────────────┬────────────────────────────────┘
             │
    ┌────────┴────────┐
    │                 │
┌───▼──────────┐ ┌───▼────────────────────────┐
│ gluten-      │ │  gluten-arrow               │
│ substrait    │ │  (Arrow 数据桥接)           │
│ (计划转换)    │ └────────────────────────────┘
└───┬──────────┘
    │
┌───▼──────────────────────────────────────────┐
│  backends-velox / backends-clickhouse         │
│  (后端实现 - JVM 侧)                          │
└───┬───────────────────────────────────────────┘
    │ JNI
┌───▼───────────────────────────────────────────┐
│  cpp/core (JNI 桥接层 - C++)                  │
│  ┌────────────────────────────────────────┐   │
│  │  GlutenBackend                         │   │
│  │  MemoryAllocator                       │   │
│  │  DataBridge                            │   │
│  └────────────────────────────────────────┘   │
└───┬────────────────────────────────────────────┘
    │
┌───▼───────────────────────────────────────────┐
│  cpp/velox or cpp-ch/clickhouse               │
│  (Native 执行引擎)                            │
└────────────────────────────────────────────────┘
```

### 15.1.2 核心模块列表

| 模块 | 语言 | 职责 | 关键类 |
|------|------|------|--------|
| **gluten-core** | Scala | 插件入口、计划扩展、后端管理 | `GlutenPlugin`, `GlutenPlan` |
| **gluten-substrait** | Scala/Java | Spark Plan → Substrait | `SparkPlanToSubstrait` |
| **gluten-arrow** | Scala/Java | Arrow 数据格式支持 | `ArrowColumnarBatch` |
| **backends-velox** | Scala | Velox 后端 JVM 侧 | `VeloxBackend`, `VeloxListenerApi` |
| **backends-clickhouse** | Scala | ClickHouse 后端 JVM 侧 | `CHBackend`, `CHListenerApi` |
| **cpp/core** | C++ | JNI 桥接、内存管理 | `gluten::VeloxBackend` |
| **cpp/velox** | C++ | Velox 集成 | `VeloxPlanConverter` |
| **shims** | Scala | Spark 版本兼容 | `SparkShim` |

## 15.2 gluten-core 模块

### 15.2.1 GlutenPlugin：插件入口

**位置**：`gluten-core/src/main/scala/org/apache/gluten/GlutenPlugin.scala`

GlutenPlugin 是 Gluten 的入口点，实现了 Spark 的 `SparkPlugin` 接口。

```scala
class GlutenPlugin extends SparkPlugin {
  
  override def driverPlugin(): DriverPlugin = {
    new GlutenDriverPlugin()
  }
  
  override def executorPlugin(): ExecutorPlugin = {
    new GlutenExecutorPlugin()
  }
}

class GlutenDriverPlugin extends DriverPlugin {
  
  override def init(sc: SparkContext, pluginContext: PluginContext): ju.Map[String, String] = {
    // 1. 初始化后端工厂
    val backendFactory = BackendFactory.createBackendFactory(sc.getConf)
    
    // 2. 注册 Gluten 策略规则
    GlutenRuleManager.initialize(sc.getConf)
    
    // 3. 注册列式扩展
    ColumnarOverrides.registerBuilders(backendFactory)
    
    // 4. 初始化 UI
    if (GlutenConfig.getConf.enableGlutenUi) {
      GlutenUI.initialize(sc)
    }
    
    Map.empty[String, String].asJava
  }
}

class GlutenExecutorPlugin extends ExecutorPlugin {
  
  override def init(pluginContext: PluginContext, extraConf: ju.Map[String, String]): Unit = {
    // 1. 初始化 Native 后端
    val backend = BackendFactory.getBackend
    backend.initialize()
    
    // 2. 配置内存管理
    val memoryManager = new GlutenMemoryManager(
      offHeapSize = GlutenConfig.getConf.memoryOffHeapSize
    )
    memoryManager.initialize()
    
    // 3. 注册 Shuffle Manager
    if (GlutenConfig.getConf.enableColumnarShuffle) {
      ColumnarShuffleManager.initialize()
    }
  }
  
  override def shutdown(): Unit = {
    // 清理 Native 资源
    BackendFactory.getBackend.shutdown()
  }
}
```

**关键流程**：

1. **Driver 侧**：
   - 创建后端工厂（VeloxBackend 或 ClickHouseBackend）
   - 注册 Gluten 的优化规则和扩展
   - 初始化 Gluten UI

2. **Executor 侧**：
   - 初始化 Native 后端（加载动态库）
   - 配置 Off-Heap 内存管理
   - 启用 Columnar Shuffle（如果配置）

### 15.2.2 GlutenPlan：物理计划扩展

**位置**：`gluten-core/src/main/scala/org/apache/gluten/execution/`

Gluten 通过扩展 Spark 的 `SparkPlan` 来实现列式执行。

#### ColumnarToRowExec

```scala
case class ColumnarToRowExec(child: SparkPlan) extends UnaryExecNode 
    with GlutenPlan {
  
  override def doExecute(): RDD[InternalRow] = {
    child.executeColumnar().mapPartitions { batches =>
      val converter = new ColumnarToRowConverter(schema)
      batches.flatMap { batch =>
        converter.convert(batch)
      }
    }
  }
  
  override def executeColumnar(): RDD[ColumnarBatch] = {
    throw new UnsupportedOperationException("ColumnarToRowExec does not support columnar output")
  }
}
```

#### RowToColumnarExec

```scala
case class RowToColumnarExec(child: SparkPlan) extends UnaryExecNode 
    with GlutenPlan {
  
  override def doExecute(): RDD[InternalRow] = {
    throw new UnsupportedOperationException("RowToColumnarExec does not support row output")
  }
  
  override def executeColumnar(): RDD[ColumnarBatch] = {
    child.execute().mapPartitions { rows =>
      val converter = new RowToColumnarConverter(schema)
      converter.convert(rows)
    }
  }
}
```

#### TransformSupport

所有 Gluten 算子都实现 `TransformSupport` trait：

```scala
trait TransformSupport extends SparkPlan {
  
  /**
   * 判断当前算子是否可以转换为 Native 执行
   */
  def doValidate(): ValidationResult = {
    // 检查算子是否支持
    if (!BackendFactory.getBackend.supportOperator(this)) {
      return ValidationResult.failed("Operator not supported by backend")
    }
    
    // 检查子节点
    children.foreach { child =>
      child match {
        case t: TransformSupport =>
          val childResult = t.doValidate()
          if (!childResult.isValid) {
            return childResult
          }
        case _ => // Vanilla Spark plan, needs C2R
      }
    }
    
    ValidationResult.ok()
  }
  
  /**
   * 将 Spark Plan 转换为 Substrait Plan
   */
  def doTransform(context: SubstraitContext): SubstraitRel
  
  /**
   * 执行列式计算
   */
  override def executeColumnar(): RDD[ColumnarBatch] = {
    // 1. 转换为 Substrait
    val substraitPlan = doTransform(new SubstraitContext())
    
    // 2. 序列化 Substrait
    val serializedPlan = substraitPlan.toByteArray
    
    // 3. 通过 JNI 调用 Native 执行
    val backend = BackendFactory.getBackend
    child.executeColumnar().mapPartitions { batches =>
      backend.executeNative(serializedPlan, batches)
    }
  }
}
```

### 15.2.3 BackendFactory：后端抽象

**位置**：`gluten-core/src/main/scala/org/apache/gluten/backendsapi/BackendFactory.scala`

```scala
object BackendFactory {
  
  private var backend: Backend = _
  
  def createBackendFactory(conf: SparkConf): Backend = {
    val backendLib = conf.get("spark.gluten.sql.columnar.backend.lib", "velox")
    
    backend = backendLib match {
      case "velox" =>
        Class.forName("org.apache.gluten.backend.velox.VeloxBackend")
          .getDeclaredConstructor()
          .newInstance()
          .asInstanceOf[Backend]
          
      case "clickhouse" | "ch" =>
        Class.forName("org.apache.gluten.backend.clickhouse.CHBackend")
          .getDeclaredConstructor()
          .newInstance()
          .asInstanceOf[Backend]
          
      case _ =>
        throw new IllegalArgumentException(s"Unknown backend: $backendLib")
    }
    
    backend.initialize(conf)
    backend
  }
  
  def getBackend: Backend = {
    if (backend == null) {
      throw new IllegalStateException("Backend not initialized")
    }
    backend
  }
}

trait Backend {
  
  /**
   * 初始化后端
   */
  def initialize(conf: SparkConf): Unit
  
  /**
   * 初始化 Native 层
   */
  def initializeNative(): Unit
  
  /**
   * 检查算子是否支持
   */
  def supportOperator(plan: SparkPlan): Boolean
  
  /**
   * 执行 Native 计算
   */
  def executeNative(plan: Array[Byte], input: Iterator[ColumnarBatch]): Iterator[ColumnarBatch]
  
  /**
   * 释放资源
   */
  def shutdown(): Unit
}
```

## 15.3 gluten-substrait 模块

### 15.3.1 Substrait 协议简介

Substrait 是跨语言的查询计划表示协议，使用 Protobuf 定义。

**核心消息类型**：

```protobuf
// substrait/proto/plan.proto

message Plan {
  repeated Rel relations = 1;
  repeated extensions.SimpleExtensionURI extension_uris = 2;
  repeated extensions.SimpleExtensionDeclaration extensions = 3;
}

message Rel {
  oneof rel_type {
    ReadRel read = 1;
    FilterRel filter = 2;
    ProjectRel project = 3;
    JoinRel join = 4;
    AggregateRel aggregate = 5;
    SortRel sort = 6;
    // ... 更多算子
  }
}

message FilterRel {
  Rel input = 1;
  Expression condition = 2;
}

message ProjectRel {
  Rel input = 1;
  repeated Expression expressions = 2;
}
```

### 15.3.2 SparkPlanToSubstrait：核心转换器

**位置**：`gluten-substrait/src/main/scala/org/apache/gluten/substrait/plan/PlanBuilder.scala`

```scala
object SparkPlanToSubstrait {
  
  def toSubstraitPlan(plan: SparkPlan, context: SubstraitContext): Plan = {
    // 1. 转换根节点
    val rootRel = toSubstraitRel(plan, context)
    
    // 2. 构建 Substrait Plan
    val planBuilder = Plan.newBuilder()
      .addRelations(RelRoot.newBuilder().setInput(rootRel))
    
    // 3. 添加扩展信息（函数映射）
    context.registeredFunctions.foreach { case (sparkName, substraitId) =>
      planBuilder.addExtensions(
        SimpleExtensionDeclaration.newBuilder()
          .setExtensionFunction(
            SimpleExtensionDeclaration.ExtensionFunction.newBuilder()
              .setFunctionAnchor(substraitId)
              .setName(sparkName)
          )
      )
    }
    
    planBuilder.build()
  }
  
  private def toSubstraitRel(plan: SparkPlan, context: SubstraitContext): Rel = {
    plan match {
      case FilterExec(condition, child) =>
        convertFilter(condition, child, context)
        
      case ProjectExec(projectList, child) =>
        convertProject(projectList, child, context)
        
      case HashAggregateExec(_, groupingExpressions, aggregateExpressions, _, _, child) =>
        convertAggregate(groupingExpressions, aggregateExpressions, child, context)
        
      case ShuffledHashJoinExec(leftKeys, rightKeys, joinType, _, _, left, right, _) =>
        convertJoin(leftKeys, rightKeys, joinType, left, right, context)
        
      case SortExec(sortOrder, global, child, _) =>
        convertSort(sortOrder, global, child, context)
        
      case _ =>
        throw new UnsupportedOperationException(s"Unsupported plan: ${plan.getClass.getSimpleName}")
    }
  }
}
```

#### Filter 转换

```scala
private def convertFilter(
    condition: Expression,
    child: SparkPlan,
    context: SubstraitContext): Rel = {
  
  // 1. 递归转换子节点
  val childRel = toSubstraitRel(child, context)
  
  // 2. 转换 Filter 条件
  val substraitCondition = ExpressionConverter.toSubstraitExpression(condition, child.output, context)
  
  // 3. 构建 FilterRel
  Rel.newBuilder()
    .setFilter(
      FilterRel.newBuilder()
        .setInput(childRel)
        .setCondition(substraitCondition)
    )
    .build()
}
```

#### Aggregate 转换

```scala
private def convertAggregate(
    groupingExpressions: Seq[NamedExpression],
    aggregateExpressions: Seq[AggregateExpression],
    child: SparkPlan,
    context: SubstraitContext): Rel = {
  
  // 1. 转换子节点
  val childRel = toSubstraitRel(child, context)
  
  // 2. 转换 Grouping Keys
  val groupings = groupingExpressions.map { expr =>
    ExpressionConverter.toSubstraitExpression(expr, child.output, context)
  }
  
  // 3. 转换聚合函数
  val measures = aggregateExpressions.map { aggExpr =>
    val functionName = aggExpr.aggregateFunction match {
      case _: Sum => "sum"
      case _: Avg => "avg"
      case _: Count => "count"
      case _: Max => "max"
      case _: Min => "min"
      case other => throw new UnsupportedOperationException(s"Unsupported aggregate: $other")
    }
    
    // 注册函数
    val functionId = context.registerFunction(functionName)
    
    // 构建 AggregateFunction
    AggregateFunction.newBuilder()
      .setFunctionReference(functionId)
      .addAllArguments(aggExpr.children.map(
        ExpressionConverter.toSubstraitExpression(_, child.output, context)
      ).asJava)
      .build()
  }
  
  // 4. 构建 AggregateRel
  Rel.newBuilder()
    .setAggregate(
      AggregateRel.newBuilder()
        .setInput(childRel)
        .addAllGroupings(groupings.asJava)
        .addAllMeasures(measures.asJava)
    )
    .build()
}
```

### 15.3.3 ExpressionConverter：表达式转换

```scala
object ExpressionConverter {
  
  def toSubstraitExpression(
      expr: Expression,
      inputSchema: Seq[Attribute],
      context: SubstraitContext): substrait.Expression = {
    
    expr match {
      // 1. 字段引用
      case attr: AttributeReference =>
        val fieldIndex = inputSchema.indexWhere(_.exprId == attr.exprId)
        substrait.Expression.newBuilder()
          .setSelection(
            FieldReference.newBuilder()
              .setDirectReference(
                Reference.newBuilder()
                  .setStructField(
                    Reference.StructField.newBuilder()
                      .setField(fieldIndex)
                  )
              )
          )
          .build()
      
      // 2. 字面量
      case Literal(value, dataType) =>
        substrait.Expression.newBuilder()
          .setLiteral(convertLiteral(value, dataType))
          .build()
      
      // 3. 二元运算符
      case Add(left, right, _) =>
        createScalarFunction("add", Seq(left, right), inputSchema, context)
      
      case Subtract(left, right, _) =>
        createScalarFunction("subtract", Seq(left, right), inputSchema, context)
      
      case Multiply(left, right, _) =>
        createScalarFunction("multiply", Seq(left, right), inputSchema, context)
      
      case Divide(left, right, _) =>
        createScalarFunction("divide", Seq(left, right), inputSchema, context)
      
      // 4. 比较运算符
      case EqualTo(left, right) =>
        createScalarFunction("equal", Seq(left, right), inputSchema, context)
      
      case LessThan(left, right) =>
        createScalarFunction("lt", Seq(left, right), inputSchema, context)
      
      case GreaterThan(left, right) =>
        createScalarFunction("gt", Seq(left, right), inputSchema, context)
      
      // 5. 逻辑运算符
      case And(left, right) =>
        createScalarFunction("and", Seq(left, right), inputSchema, context)
      
      case Or(left, right) =>
        createScalarFunction("or", Seq(left, right), inputSchema, context)
      
      case Not(child) =>
        createScalarFunction("not", Seq(child), inputSchema, context)
      
      // 6. 字符串函数
      case Upper(child) =>
        createScalarFunction("upper", Seq(child), inputSchema, context)
      
      case Lower(child) =>
        createScalarFunction("lower", Seq(child), inputSchema, context)
      
      case Substring(str, pos, len) =>
        createScalarFunction("substring", Seq(str, pos, len), inputSchema, context)
      
      // 7. 日期函数
      case Year(child) =>
        createScalarFunction("year", Seq(child), inputSchema, context)
      
      case Month(child) =>
        createScalarFunction("month", Seq(child), inputSchema, context)
      
      case _ =>
        throw new UnsupportedOperationException(s"Unsupported expression: ${expr.getClass.getSimpleName}")
    }
  }
  
  private def createScalarFunction(
      name: String,
      args: Seq[Expression],
      inputSchema: Seq[Attribute],
      context: SubstraitContext): substrait.Expression = {
    
    // 注册函数
    val functionId = context.registerFunction(name)
    
    // 转换参数
    val substraitArgs = args.map(toSubstraitExpression(_, inputSchema, context))
    
    // 构建 ScalarFunction
    substrait.Expression.newBuilder()
      .setScalarFunction(
        substrait.Expression.ScalarFunction.newBuilder()
          .setFunctionReference(functionId)
          .addAllArguments(substraitArgs.asJava)
      )
      .build()
  }
  
  private def convertLiteral(value: Any, dataType: DataType): substrait.Expression.Literal = {
    val builder = substrait.Expression.Literal.newBuilder()
    
    (value, dataType) match {
      case (null, _) =>
        builder.setNull(convertDataType(dataType))
      
      case (v: Boolean, BooleanType) =>
        builder.setBoolean(v)
      
      case (v: Byte, ByteType) =>
        builder.setI8(v)
      
      case (v: Short, ShortType) =>
        builder.setI16(v)
      
      case (v: Int, IntegerType) =>
        builder.setI32(v)
      
      case (v: Long, LongType) =>
        builder.setI64(v)
      
      case (v: Float, FloatType) =>
        builder.setFp32(v)
      
      case (v: Double, DoubleType) =>
        builder.setFp64(v)
      
      case (v: String, StringType) =>
        builder.setString(v)
      
      case (v: java.sql.Date, DateType) =>
        builder.setDate(v.toLocalDate.toEpochDay.toInt)
      
      case _ =>
        throw new UnsupportedOperationException(s"Unsupported literal: $value of type $dataType")
    }
    
    builder.build()
  }
}
```

### 15.3.4 SubstraitContext：上下文管理

```scala
class SubstraitContext {
  private val functionRegistry = mutable.Map[String, Int]()
  private var nextFunctionId = 0
  
  /**
   * 注册函数并返回 ID
   */
  def registerFunction(name: String): Int = {
    functionRegistry.getOrElseUpdate(name, {
      val id = nextFunctionId
      nextFunctionId += 1
      id
    })
  }
  
  /**
   * 获取所有已注册的函数
   */
  def registeredFunctions: Map[String, Int] = functionRegistry.toMap
  
  /**
   * 类型映射（Spark → Substrait）
   */
  def convertDataType(sparkType: DataType): substrait.Type = {
    sparkType match {
      case BooleanType => substrait.Type.newBuilder().setBool(substrait.Type.Boolean.newBuilder()).build()
      case ByteType => substrait.Type.newBuilder().setI8(substrait.Type.I8.newBuilder()).build()
      case ShortType => substrait.Type.newBuilder().setI16(substrait.Type.I16.newBuilder()).build()
      case IntegerType => substrait.Type.newBuilder().setI32(substrait.Type.I32.newBuilder()).build()
      case LongType => substrait.Type.newBuilder().setI64(substrait.Type.I64.newBuilder()).build()
      case FloatType => substrait.Type.newBuilder().setFp32(substrait.Type.FP32.newBuilder()).build()
      case DoubleType => substrait.Type.newBuilder().setFp64(substrait.Type.FP64.newBuilder()).build()
      case StringType => substrait.Type.newBuilder().setString(substrait.Type.String.newBuilder()).build()
      case DateType => substrait.Type.newBuilder().setDate(substrait.Type.Date.newBuilder()).build()
      case TimestampType => substrait.Type.newBuilder().setTimestamp(substrait.Type.Timestamp.newBuilder()).build()
      case DecimalType.Fixed(precision, scale) =>
        substrait.Type.newBuilder().setDecimal(
          substrait.Type.Decimal.newBuilder()
            .setPrecision(precision)
            .setScale(scale)
        ).build()
      case _ =>
        throw new UnsupportedOperationException(s"Unsupported Spark type: $sparkType")
    }
  }
}
```

## 15.4 gluten-arrow 模块

### 15.4.1 ArrowColumnarBatch：数据桥接

**位置**：`gluten-arrow/src/main/java/org/apache/gluten/vectorized/ArrowColumnarBatch.java`

```java
public class ArrowColumnarBatch implements AutoCloseable {
  private final long nativeHandle;  // Native ArrowRecordBatch 指针
  private final StructType schema;
  private final int numRows;
  
  public ArrowColumnarBatch(long nativeHandle, StructType schema) {
    this.nativeHandle = nativeHandle;
    this.schema = schema;
    this.numRows = nativeGetNumRows(nativeHandle);
  }
  
  /**
   * 获取 ColumnarBatch（Spark 格式）
   */
  public ColumnarBatch toColumnarBatch() {
    ArrowWritableColumnVector[] columns = new ArrowWritableColumnVector[schema.size()];
    
    for (int i = 0; i < schema.size(); i++) {
      long columnHandle = nativeGetColumn(nativeHandle, i);
      columns[i] = new ArrowWritableColumnVector(columnHandle, schema.apply(i).dataType());
    }
    
    return new ColumnarBatch(columns, numRows);
  }
  
  /**
   * 从 ColumnarBatch 创建（JVM → Native）
   */
  public static ArrowColumnarBatch fromColumnarBatch(ColumnarBatch batch) {
    // 1. 分配 Native 内存
    long nativeHandle = nativeAllocate(batch.numRows(), batch.numCols());
    
    // 2. 拷贝数据
    for (int colIdx = 0; colIdx < batch.numCols(); colIdx++) {
      ColumnVector column = batch.column(colIdx);
      if (column instanceof ArrowWritableColumnVector) {
        // Zero-copy: 直接传递 Arrow 指针
        ArrowWritableColumnVector arrowColumn = (ArrowWritableColumnVector) column;
        nativeSetColumnZeroCopy(nativeHandle, colIdx, arrowColumn.getNativeHandle());
      } else {
        // Copy: 从 OnHeapColumnVector 拷贝
        nativeCopyColumn(nativeHandle, colIdx, column);
      }
    }
    
    return new ArrowColumnarBatch(nativeHandle, batch.schema());
  }
  
  @Override
  public void close() {
    nativeRelease(nativeHandle);
  }
  
  // JNI 方法
  private static native int nativeGetNumRows(long handle);
  private static native long nativeGetColumn(long handle, int index);
  private static native long nativeAllocate(int numRows, int numCols);
  private static native void nativeSetColumnZeroCopy(long handle, int colIdx, long columnHandle);
  private static native void nativeCopyColumn(long handle, int colIdx, ColumnVector column);
  private static native void nativeRelease(long handle);
}
```

### 15.4.2 ArrowWritableColumnVector

```java
public class ArrowWritableColumnVector extends WritableColumnVector {
  private final long nativeHandle;  // Arrow FieldVector 指针
  private final ArrowBuf dataBuf;
  private final ArrowBuf validityBuf;
  private final ArrowBuf offsetBuf;  // For variable-length types
  
  public ArrowWritableColumnVector(long nativeHandle, DataType type) {
    super(0, type);  // numRows will be set later
    this.nativeHandle = nativeHandle;
    
    // 从 Native 获取 Arrow 缓冲区指针（零拷贝）
    this.dataBuf = nativeGetDataBuffer(nativeHandle);
    this.validityBuf = nativeGetValidityBuffer(nativeHandle);
    if (isVariableLength(type)) {
      this.offsetBuf = nativeGetOffsetBuffer(nativeHandle);
    }
  }
  
  @Override
  public boolean isNullAt(int rowId) {
    // 直接读取 validity buffer
    return BitVectorHelper.isNull(validityBuf, rowId);
  }
  
  @Override
  public int getInt(int rowId) {
    // 直接从 data buffer 读取（零拷贝）
    return dataBuf.getInt(rowId * 4);
  }
  
  @Override
  public long getLong(int rowId) {
    return dataBuf.getLong(rowId * 8);
  }
  
  @Override
  public UTF8String getUTF8String(int rowId) {
    // 变长类型：从 offset buffer 获取偏移量
    int startOffset = offsetBuf.getInt(rowId * 4);
    int endOffset = offsetBuf.getInt((rowId + 1) * 4);
    int length = endOffset - startOffset;
    
    // 零拷贝：直接引用 data buffer
    byte[] bytes = new byte[length];
    dataBuf.getBytes(startOffset, bytes);
    return UTF8String.fromBytes(bytes);
  }
  
  public long getNativeHandle() {
    return nativeHandle;
  }
  
  // JNI 方法
  private static native ArrowBuf nativeGetDataBuffer(long handle);
  private static native ArrowBuf nativeGetValidityBuffer(long handle);
  private static native ArrowBuf nativeGetOffsetBuffer(long handle);
}
```

## 15.5 JNI 层实现

### 15.5.1 JNI 接口定义

**位置**：`cpp/core/jni/JniWrapper.h`

```cpp
#ifndef GLUTEN_JNI_WRAPPER_H
#define GLUTEN_JNI_WRAPPER_H

#include <jni.h>
#include <memory>
#include <string>
#include <vector>

namespace gluten {

/**
 * JNI 包装器：管理 JNI 调用和对象生命周期
 */
class JniWrapper {
 public:
  JniWrapper(JavaVM* vm, jobject java_object);
  ~JniWrapper();
  
  // 初始化后端
  void initialize(const std::unordered_map<std::string, std::string>& config);
  
  // 执行 Native 计算
  std::shared_ptr<ArrowRecordBatch> execute(
      const uint8_t* plan_data,
      size_t plan_size,
      const std::vector<std::shared_ptr<ArrowRecordBatch>>& inputs);
  
  // 内存管理
  int64_t allocateMemory(int64_t size);
  void freeMemory(int64_t address, int64_t size);
  
 private:
  JavaVM* vm_;
  jobject java_object_;
  JNIEnv* getEnv();
};

} // namespace gluten

#endif
```

### 15.5.2 JNI 实现

**位置**：`cpp/core/jni/JniWrapper.cc`

```cpp
#include "JniWrapper.h"
#include <glog/logging.h>
#include "substrait/plan.pb.h"
#include "VeloxBackend.h"

namespace gluten {

// 全局后端实例
static std::unique_ptr<VeloxBackend> g_backend;

extern "C" {

/**
 * 初始化 Native 后端
 * Java 方法: VeloxBackend.nativeInit(Map<String, String> config)
 */
JNIEXPORT void JNICALL
Java_org_apache_gluten_backend_velox_VeloxBackend_nativeInit(
    JNIEnv* env,
    jobject obj,
    jobject j_config_map) {
  
  try {
    // 1. 解析配置（Java Map → C++ unordered_map）
    std::unordered_map<std::string, std::string> config;
    
    jclass map_class = env->GetObjectClass(j_config_map);
    jmethodID entry_set_method = env->GetMethodID(map_class, "entrySet", "()Ljava/util/Set;");
    jobject entry_set = env->CallObjectMethod(j_config_map, entry_set_method);
    
    jclass set_class = env->GetObjectClass(entry_set);
    jmethodID iterator_method = env->GetMethodID(set_class, "iterator", "()Ljava/util/Iterator;");
    jobject iterator = env->CallObjectMethod(entry_set, iterator_method);
    
    jclass iterator_class = env->GetObjectClass(iterator);
    jmethodID has_next_method = env->GetMethodID(iterator_class, "hasNext", "()Z");
    jmethodID next_method = env->GetMethodID(iterator_class, "next", "()Ljava/lang/Object;");
    
    while (env->CallBooleanMethod(iterator, has_next_method)) {
      jobject entry = env->CallObjectMethod(iterator, next_method);
      jclass entry_class = env->GetObjectClass(entry);
      jmethodID get_key_method = env->GetMethodID(entry_class, "getKey", "()Ljava/lang/Object;");
      jmethodID get_value_method = env->GetMethodID(entry_class, "getValue", "()Ljava/lang/Object;");
      
      jstring j_key = (jstring)env->CallObjectMethod(entry, get_key_method);
      jstring j_value = (jstring)env->CallObjectMethod(entry, get_value_method);
      
      const char* key_chars = env->GetStringUTFChars(j_key, nullptr);
      const char* value_chars = env->GetStringUTFChars(j_value, nullptr);
      
      config[key_chars] = value_chars;
      
      env->ReleaseStringUTFChars(j_key, key_chars);
      env->ReleaseStringUTFChars(j_value, value_chars);
    }
    
    // 2. 初始化 Velox 后端
    g_backend = std::make_unique<VeloxBackend>(config);
    g_backend->initialize();
    
    LOG(INFO) << "Velox backend initialized successfully";
    
  } catch (const std::exception& e) {
    LOG(ERROR) << "Failed to initialize Velox backend: " << e.what();
    env->ThrowNew(env->FindClass("java/lang/RuntimeException"), e.what());
  }
}

/**
 * 执行 Native 计算
 * Java 方法: long nativeExecute(byte[] plan, long[] inputHandles)
 */
JNIEXPORT jlong JNICALL
Java_org_apache_gluten_backend_velox_VeloxBackend_nativeExecute(
    JNIEnv* env,
    jobject obj,
    jbyteArray j_plan,
    jlongArray j_input_handles) {
  
  try {
    // 1. 解析 Substrait Plan
    jsize plan_size = env->GetArrayLength(j_plan);
    jbyte* plan_data = env->GetByteArrayElements(j_plan, nullptr);
    
    substrait::Plan substrait_plan;
    if (!substrait_plan.ParseFromArray(plan_data, plan_size)) {
      throw std::runtime_error("Failed to parse Substrait plan");
    }
    
    env->ReleaseByteArrayElements(j_plan, plan_data, JNI_ABORT);
    
    // 2. 获取输入 Batch 句柄
    jsize num_inputs = env->GetArrayLength(j_input_handles);
    jlong* input_handles = env->GetLongArrayElements(j_input_handles, nullptr);
    
    std::vector<std::shared_ptr<ArrowRecordBatch>> inputs;
    for (int i = 0; i < num_inputs; i++) {
      auto* batch = reinterpret_cast<ArrowRecordBatch*>(input_handles[i]);
      inputs.push_back(std::shared_ptr<ArrowRecordBatch>(batch, [](ArrowRecordBatch*){}));  // Non-owning
    }
    
    env->ReleaseLongArrayElements(j_input_handles, input_handles, JNI_ABORT);
    
    // 3. 执行 Velox 计算
    auto result_batch = g_backend->execute(substrait_plan, inputs);
    
    // 4. 返回结果 Batch 句柄（Java 侧负责释放）
    return reinterpret_cast<jlong>(new std::shared_ptr<ArrowRecordBatch>(result_batch));
    
  } catch (const std::exception& e) {
    LOG(ERROR) << "Native execution failed: " << e.what();
    env->ThrowNew(env->FindClass("java/lang/RuntimeException"), e.what());
    return 0;
  }
}

/**
 * 释放 Native Batch
 * Java 方法: void nativeRelease(long handle)
 */
JNIEXPORT void JNICALL
Java_org_apache_gluten_vectorized_ArrowColumnarBatch_nativeRelease(
    JNIEnv* env,
    jclass clazz,
    jlong handle) {
  
  try {
    auto* batch_ptr = reinterpret_cast<std::shared_ptr<ArrowRecordBatch>*>(handle);
    delete batch_ptr;
  } catch (const std::exception& e) {
    LOG(ERROR) << "Failed to release native batch: " << e.what();
  }
}

/**
 * 分配 Off-Heap 内存
 * Java 方法: long nativeAllocate(long size)
 */
JNIEXPORT jlong JNICALL
Java_org_apache_gluten_memory_MemoryAllocator_nativeAllocate(
    JNIEnv* env,
    jclass clazz,
    jlong size) {
  
  try {
    void* ptr = g_backend->allocateMemory(size);
    return reinterpret_cast<jlong>(ptr);
  } catch (const std::exception& e) {
    LOG(ERROR) << "Failed to allocate memory: " << e.what();
    return 0;
  }
}

/**
 * 释放 Off-Heap 内存
 * Java 方法: void nativeFree(long address, long size)
 */
JNIEXPORT void JNICALL
Java_org_apache_gluten_memory_MemoryAllocator_nativeFree(
    JNIEnv* env,
    jclass clazz,
    jlong address,
    jlong size) {
  
  try {
    void* ptr = reinterpret_cast<void*>(address);
    g_backend->freeMemory(ptr, size);
  } catch (const std::exception& e) {
    LOG(ERROR) << "Failed to free memory: " << e.what();
  }
}

} // extern "C"

} // namespace gluten
```

### 15.5.3 JNI 对象生命周期管理

```cpp
/**
 * JNI 全局引用管理器
 */
class JniGlobalRefManager {
 public:
  static JniGlobalRefManager& getInstance() {
    static JniGlobalRefManager instance;
    return instance;
  }
  
  jobject newGlobalRef(JNIEnv* env, jobject obj) {
    jobject global_ref = env->NewGlobalRef(obj);
    std::lock_guard<std::mutex> lock(mutex_);
    global_refs_.insert(global_ref);
    return global_ref;
  }
  
  void deleteGlobalRef(JNIEnv* env, jobject global_ref) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (global_refs_.erase(global_ref) > 0) {
      env->DeleteGlobalRef(global_ref);
    }
  }
  
  ~JniGlobalRefManager() {
    // 清理所有全局引用（在 JVM 关闭时）
    for (auto ref : global_refs_) {
      // 注意：这里无法获取 JNIEnv*，需要在适当时机清理
      LOG(WARNING) << "Leaked JNI global reference: " << ref;
    }
  }
  
 private:
  JniGlobalRefManager() = default;
  std::mutex mutex_;
  std::unordered_set<jobject> global_refs_;
};
```

## 15.6 Plugin 注册和加载机制

### 15.6.1 Spark Plugin SPI

Spark 通过 `ServiceLoader` 机制加载插件：

**文件**：`gluten-core/src/main/resources/META-INF/services/org.apache.spark.api.plugin.SparkPlugin`

```
org.apache.gluten.GlutenPlugin
```

### 15.6.2 动态库加载

```scala
object NativeLibraryLoader {
  
  private val loaded = new AtomicBoolean(false)
  
  def loadLibrary(): Unit = {
    if (loaded.compareAndSet(false, true)) {
      val libName = s"libgluten_$backendName.so"
      
      // 1. 尝试从 JAR 中解压
      val tempDir = Files.createTempDirectory("gluten-native-")
      val libFile = new File(tempDir.toFile, libName)
      
      val inputStream = getClass.getClassLoader.getResourceAsStream(s"native/$libName")
      Files.copy(inputStream, libFile.toPath, StandardCopyOption.REPLACE_EXISTING)
      
      // 2. 加载动态库
      System.load(libFile.getAbsolutePath)
      
      LOG.info(s"Loaded native library: $libName")
    }
  }
}
```

## 本章小结

本章深入分析了 Gluten 核心模块的源码实现：

1. **gluten-core**：插件入口、计划扩展、后端抽象
2. **gluten-substrait**：Spark Plan → Substrait 转换，表达式映射
3. **gluten-arrow**：Arrow 数据格式桥接，零拷贝优化
4. **JNI 层**：Java ↔ C++ 交互，对象生命周期管理
5. **Plugin 机制**：ServiceLoader 注册，动态库加载

通过本章学习，你应该能够：
- ✅ 理解 Gluten 模块架构和依赖关系
- ✅ 掌握 Substrait 计划转换流程
- ✅ 了解 Arrow 数据在 JVM 和 Native 间的传递
- ✅ 理解 JNI 层的设计和实现
- ✅ 理解 Spark Plugin 的加载机制

下一章将深入剖析具体算子的实现。

## 参考资料

1. Gluten 源码：https://github.com/apache/incubator-gluten
2. Substrait 规范：https://substrait.io/
3. Apache Arrow Java：https://arrow.apache.org/docs/java/
4. JNI 编程指南：https://docs.oracle.com/javase/8/docs/technotes/guides/jni/
5. Spark Plugin API：https://spark.apache.org/docs/latest/api/java/org/apache/spark/api/plugin/SparkPlugin.html
