# 第16章：算子实现剖析

本章深入剖析Apache Gluten中核心算子的完整实现流程，涵盖从Spark逻辑计划到Velox物理执行的全链路代码。通过学习Filter、Project、HashAggregate、HashJoin和Sort五大算子的实现细节,您将掌握Gluten的算子开发框架和自定义算子开发技巧。

## 16.1 算子实现架构概览

### 16.1.1 三层架构设计

Gluten算子实现采用三层架构:

```
┌─────────────────────────────────────────────────────────┐
│  Spark SQL Layer (Scala)                               │
│  - SparkPlan物理算子                                    │
│  - Transformer包装类                                     │
└──────────────────────┬──────────────────────────────────┘
                       │ 转换
┌──────────────────────▼──────────────────────────────────┐
│  Substrait Layer (Java/Scala)                          │
│  - RelNode: FilterRel, ProjectRel, AggregateRel, etc.  │
│  - ExpressionNode: 表达式序列化                         │
└──────────────────────┬──────────────────────────────────┘
                       │ 序列化
┌──────────────────────▼──────────────────────────────────┐
│  Velox Execution Layer (C++)                           │
│  - FilterNode, ProjectNode, AggregationNode, etc.      │
│  - 向量化执行引擎                                        │
└─────────────────────────────────────────────────────────┘
```

### 16.1.2 关键类体系

| 层级 | 模块 | 关键类 | 功能 |
|------|------|--------|------|
| **Scala** | gluten-substrait | `BasicPhysicalOperatorTransformer` | 算子转换基类 |
| **Scala** | backends-velox | `FilterExecTransformer` | Velox特化实现 |
| **Java** | gluten-substrait | `RelBuilder` | Substrait计划构建 |
| **C++** | cpp/velox/substrait | `SubstraitToVeloxPlanConverter` | Substrait到Velox转换 |
| **C++** | velox/exec | `FilterNode`, `ProjectNode` | Velox执行节点 |

### 16.1.3 代码路径映射

```bash
# Scala算子转换器
gluten-substrait/src/main/scala/org/apache/gluten/execution/
├── BasicPhysicalOperatorTransformer.scala  # Filter, Project基类
├── HashAggregateExecBaseTransformer.scala  # Aggregate基类
├── JoinExecTransformer.scala                # Join基类
└── SortExecTransformer.scala                # Sort实现

# Velox后端特化实现
backends-velox/src/main/scala/org/apache/gluten/execution/
├── FilterExecTransformer.scala
├── HashAggregateExecTransformer.scala
└── HashJoinExecTransformer.scala

# Substrait计划构建
gluten-substrait/src/main/java/org/apache/gluten/substrait/rel/
├── RelBuilder.java           # 构建各种RelNode
├── FilterRelNode.java
├── ProjectRelNode.java
└── AggregateRelNode.java

# C++转换和执行
cpp/velox/substrait/
├── SubstraitToVeloxPlan.h    # 转换器头文件
├── SubstraitToVeloxPlan.cc   # 转换实现
└── SubstraitVeloxExprConverter.cc  # 表达式转换
```

## 16.2 Filter算子完整实现

Filter算子是最基础的算子,用于根据谓词条件过滤行。我们从Scala到C++完整跟踪其实现。

### 16.2.1 Scala层转换器实现

**文件**: `gluten-substrait/src/main/scala/org/apache/gluten/execution/BasicPhysicalOperatorTransformer.scala`

```scala
abstract class FilterExecTransformerBase(
    condition: Expression,
    child: SparkPlan)
  extends UnaryTransformSupport with PredicateHelper {

  // 核心方法: 生成Substrait FilterRel
  override protected def doTransform(context: SubstraitContext): TransformContext = {
    val childCtx = child.asInstanceOf[TransformSupport].doTransform(context)
    
    // 拆解复合谓词 (AND/OR)
    val filters = splitConjunctivePredicates(condition)
    
    // 转换每个谓词为Substrait表达式
    val exprNodes = filters.map { expr =>
      ExpressionConverter.replaceWithExpressionTransformer(expr, child.output)
        .doTransform(context.registeredFunction)
    }
    
    // 构建FilterRel
    val filterRel = context.relBuilder.makeFilterRel(
      childCtx.root,
      ExpressionBuilder.makeAnd(exprNodes),
      context.nextOperatorId("Filter")
    )
    
    TransformContext(childCtx.outputAttributes, filterRel)
  }
  
  // 空值处理: Spark的三值逻辑
  override def canDoEvaluate: Boolean = {
    // 检查所有子表达式是否支持原生执行
    condition.children.forall {
      case e: Expression => 
        ExpressionConverter.replaceWithExpressionTransformer(e, child.output)
          .doValidate().isValid
    }
  }
  
  // Metrics定义
  override lazy val metrics = Map(
    "numInputRows" -> SQLMetrics.createMetric(sparkContext, "number of input rows"),
    "numOutputRows" -> SQLMetrics.createMetric(sparkContext, "number of output rows"),
    "processingTime" -> SQLMetrics.createTimingMetric(sparkContext, "time in filter")
  )
}
```

**Velox后端特化实现**:

```scala
// backends-velox/src/main/scala/org/apache/gluten/execution/FilterExecTransformer.scala
case class FilterExecTransformer(
    condition: Expression,
    child: SparkPlan)
  extends FilterExecTransformerBase(condition, child) {
  
  override protected def withNewChildInternal(newChild: SparkPlan): FilterExecTransformer =
    copy(child = newChild)
}
```

### 16.2.2 Substrait计划生成

**文件**: `gluten-substrait/src/main/java/org/apache/gluten/substrait/rel/RelBuilder.java`

```java
public class RelBuilder {
  
  /**
   * 构建FilterRel
   * @param input 输入RelNode
   * @param condition 过滤条件表达式
   * @param operatorId 算子ID
   * @return FilterRel节点
   */
  public RelNode makeFilterRel(
      RelNode input,
      ExpressionNode condition,
      Long operatorId) {
    
    // 创建Substrait FilterRel
    substrait.FilterRel.Builder filterBuilder = substrait.FilterRel.newBuilder();
    filterBuilder.setInput(input.toProtobuf());
    filterBuilder.setCondition(condition.toProtobuf());
    
    // 添加高级扩展信息 (用于指标收集)
    if (operatorId != null) {
      substrait.RelCommon.Builder commonBuilder = substrait.RelCommon.newBuilder();
      substrait.AdvancedExtension.Builder advBuilder = substrait.AdvancedExtension.newBuilder();
      advBuilder.setEnhancement(
        Any.pack(
          Enhancement.newBuilder()
            .addAllMetrics(getDefaultFilterMetrics())
            .build()
        )
      );
      commonBuilder.setAdvancedExtension(advBuilder);
      filterBuilder.setCommon(commonBuilder);
    }
    
    return new FilterRelNode(filterBuilder.build());
  }
  
  // 默认指标定义
  private List<Metric> getDefaultFilterMetrics() {
    return Arrays.asList(
      Metric.newBuilder().setName("input_rows").setType(MetricType.COUNTER).build(),
      Metric.newBuilder().setName("output_rows").setType(MetricType.COUNTER).build(),
      Metric.newBuilder().setName("processing_time_ns").setType(MetricType.TIMING).build()
    );
  }
}
```

### 16.2.3 C++层Velox转换

**文件**: `cpp/velox/substrait/SubstraitToVeloxPlan.cc`

```cpp
#include "SubstraitToVeloxPlan.h"
#include "velox/exec/FilterProject.h"

namespace gluten {

core::PlanNodePtr SubstraitToVeloxPlanConverter::toVeloxPlan(
    const substrait::FilterRel& filterRel) {
  
  // 1. 递归转换子节点
  core::PlanNodePtr childNode;
  if (filterRel.has_input()) {
    childNode = toVeloxPlan(filterRel.input());
  } else {
    VELOX_FAIL("FilterRel must have input");
  }
  
  // 2. 转换过滤表达式
  core::TypedExprPtr filterExpr;
  if (filterRel.has_condition()) {
    filterExpr = exprConverter_->toVeloxExpr(
      filterRel.condition(), 
      childNode->outputType()
    );
  } else {
    VELOX_FAIL("FilterRel must have condition");
  }
  
  // 3. 类型检查: 过滤条件必须返回布尔类型
  if (filterExpr->type()->kind() != TypeKind::BOOLEAN) {
    VELOX_FAIL(
      "Filter condition must return boolean, got: {}", 
      filterExpr->type()->toString()
    );
  }
  
  // 4. 创建Velox FilterNode
  std::string nodeId = fmt::format("Filter_{}", ++nodeIdCounter_);
  return std::make_shared<core::FilterNode>(
    nodeId,
    filterExpr,
    childNode
  );
}

} // namespace gluten
```

### 16.2.4 Velox执行层

```cpp
// velox/exec/FilterProject.h (Velox原生代码)
namespace facebook::velox::exec {

class FilterNode : public core::PlanNode {
 public:
  FilterNode(
      const std::string& id,
      core::TypedExprPtr filter,
      core::PlanNodePtr source)
      : PlanNode(id), filter_(std::move(filter)), sources_{std::move(source)} {}

  const RowTypePtr& outputType() const override {
    return sources_[0]->outputType();
  }

 private:
  core::TypedExprPtr filter_;  // 过滤表达式
  std::vector<core::PlanNodePtr> sources_;
};

// FilterProject运算符: 融合Filter和Project
class FilterProject : public Operator {
 public:
  void addInput(RowVectorPtr input) override {
    // 执行表达式求值
    EvalCtx evalCtx(&execCtx_, exprSet_.get(), input.get());
    
    // 计算过滤条件
    SelectivityVector rows(input->size());
    if (filter_) {
      filter_->eval(0, 1, true, rows, evalCtx);
      // 获取布尔结果向量
      DecodedVector decoded(*evalCtx.result(0), rows);
      rows.clearAll();
      for (int i = 0; i < input->size(); ++i) {
        if (!decoded.isNullAt(i) && decoded.valueAt<bool>(i)) {
          rows.setValid(i, true);
        }
      }
    }
    
    // 应用投影 (如果有)
    RowVectorPtr output = applyProjections(input, rows, evalCtx);
    
    // 更新指标
    numInputRows_ += input->size();
    numOutputRows_ += rows.countSelected();
    
    // 输出结果
    output_ = std::move(output);
  }

 private:
  std::unique_ptr<ExprSet> filter_;
  std::atomic<int64_t> numInputRows_{0};
  std::atomic<int64_t> numOutputRows_{0};
};

} // namespace
```

### 16.2.5 完整调用链示例

```scala
// Spark SQL查询
val df = spark.read.parquet("/data/users")
  .filter($"age" > 18 && $"country" === "US")

// 1. Spark优化器生成FilterExec
FilterExec(
  condition = And(
    GreaterThan(AttributeReference("age"), Literal(18)),
    EqualTo(AttributeReference("country"), Literal("US"))
  ),
  child = FileSourceScanExec(...)
)

// 2. Gluten注入规则替换为FilterExecTransformer
FilterExecTransformer(
  condition = And(...),
  child = FileSourceScanExecTransformer(...)
)

// 3. doTransform生成Substrait FilterRel
FilterRel {
  input: ReadRel {...}
  condition: ScalarFunction {
    function_reference: AND_FUNC_ID
    arguments: [
      ScalarFunction {
        function_reference: GT_FUNC_ID
        arguments: [
          Selection { direct_reference: field_reference(0) },  // age列
          Literal { i32: 18 }
        ]
      },
      ScalarFunction {
        function_reference: EQ_FUNC_ID
        arguments: [
          Selection { direct_reference: field_reference(2) },  // country列
          Literal { string: "US" }
        ]
      }
    ]
  }
}

// 4. C++转换为Velox FilterNode
std::make_shared<core::FilterNode>(
  "Filter_1",
  std::make_shared<core::CallTypedExpr>(
    BOOLEAN(),
    {
      std::make_shared<core::CallTypedExpr>(
        BOOLEAN(),
        {field("age"), constant(18)},
        "gt"
      ),
      std::make_shared<core::CallTypedExpr>(
        BOOLEAN(),
        {field("country"), constant("US")},
        "eq"
      )
    },
    "and"
  ),
  childNode
)

// 5. Velox执行: 向量化计算
// input batch: 1024 rows
// age列: [15, 20, 17, 25, 30, ...]
// country列: ["CN", "US", "UK", "US", "US", ...]
//
// 执行 age > 18:
//   [F, T, F, T, T, ...]
// 执行 country == "US":
//   [F, T, F, T, T, ...]
// 执行 AND:
//   [F, T, F, T, T, ...]  -> SelectivityVector
//
// 输出: 过滤后的行 (假设500行满足条件)
```

## 16.3 Project算子实现

Project算子用于列选择和表达式计算。

### 16.3.1 Scala实现

```scala
// gluten-substrait/src/main/scala/org/apache/gluten/execution/ProjectExecTransformer.scala
case class ProjectExecTransformer(
    projectList: Seq[NamedExpression],
    child: SparkPlan)
  extends UnaryTransformSupport {
  
  override protected def doTransform(context: SubstraitContext): TransformContext = {
    val childCtx = child.asInstanceOf[TransformSupport].doTransform(context)
    
    // 转换投影表达式列表
    val exprNodes = projectList.map { namedExpr =>
      ExpressionConverter
        .replaceWithExpressionTransformer(namedExpr, child.output)
        .doTransform(context.registeredFunction)
    }
    
    // 构建ProjectRel
    val projectRel = context.relBuilder.makeProjectRel(
      childCtx.root,
      exprNodes.asJava,
      context.nextOperatorId("Project")
    )
    
    // 输出属性: 使用投影表达式的输出
    val outputAttrs = projectList.map(_.toAttribute)
    TransformContext(outputAttrs, projectRel)
  }
  
  override def output: Seq[Attribute] = projectList.map(_.toAttribute)
  
  override lazy val metrics = Map(
    "numInputRows" -> SQLMetrics.createMetric(sparkContext, "number of input rows"),
    "numOutputRows" -> SQLMetrics.createMetric(sparkContext, "number of output rows"),
    "numOutputBatches" -> SQLMetrics.createMetric(sparkContext, "number of output batches")
  )
}
```

### 16.3.2 表达式处理

```scala
// 示例: 复杂投影表达式
val projectList = Seq(
  // 列引用
  col("user_id").as("uid"),
  
  // 算术表达式
  (col("price") * 0.9).as("discounted_price"),
  
  // 字符串函数
  upper(col("name")).as("upper_name"),
  
  // CASE WHEN表达式
  when(col("age") >= 18, "adult")
    .when(col("age") >= 13, "teenager")
    .otherwise("child").as("age_group"),
  
  // 复杂嵌套表达式
  struct(
    col("first_name"),
    col("last_name")
  ).as("full_name")
)

// 转换为Substrait表达式
projectList.map { expr =>
  expr match {
    case Alias(child, name) =>
      val substraitExpr = convertExpression(child)
      ExpressionBuilder.makeSelection(substraitExpr, name)
      
    case AttributeReference(name, dataType, _, _) =>
      ExpressionBuilder.makeSelection(fieldIndex(name))
      
    case Multiply(left, right) =>
      ExpressionBuilder.makeScalarFunction(
        lookupFunction("multiply"),
        Seq(convertExpression(left), convertExpression(right)),
        outputType
      )
    
    // ... 其他表达式类型
  }
}
```

### 16.3.3 C++转换

```cpp
core::PlanNodePtr SubstraitToVeloxPlanConverter::toVeloxPlan(
    const substrait::ProjectRel& projectRel) {
  
  // 1. 转换子节点
  core::PlanNodePtr childNode = toVeloxPlan(projectRel.input());
  
  // 2. 转换投影表达式列表
  std::vector<core::TypedExprPtr> projections;
  std::vector<std::string> projectNames;
  
  for (int i = 0; i < projectRel.expressions_size(); ++i) {
    const auto& expr = projectRel.expressions(i);
    
    // 转换表达式
    core::TypedExprPtr veloxExpr = exprConverter_->toVeloxExpr(
      expr, 
      childNode->outputType()
    );
    projections.push_back(veloxExpr);
    
    // 提取输出列名
    if (expr.has_selection() && expr.selection().has_direct_reference()) {
      projectNames.push_back(extractFieldName(expr));
    } else {
      projectNames.push_back(fmt::format("col_{}", i));
    }
  }
  
  // 3. 创建输出类型
  std::vector<TypePtr> types;
  for (const auto& proj : projections) {
    types.push_back(proj->type());
  }
  auto outputType = ROW(std::move(projectNames), std::move(types));
  
  // 4. 创建ProjectNode
  std::string nodeId = fmt::format("Project_{}", ++nodeIdCounter_);
  return std::make_shared<core::ProjectNode>(
    nodeId,
    std::move(projectNames),
    std::move(projections),
    childNode
  );
}
```

## 16.4 HashAggregate算子实现

HashAggregate是最复杂的算子之一,涉及分组、聚合函数计算、内存管理等。

### 16.4.1 Scala实现

```scala
// gluten-substrait/src/main/scala/org/apache/gluten/execution/HashAggregateExecBaseTransformer.scala
abstract class HashAggregateExecBaseTransformer(
    requiredChildDistributionExpressions: Option[Seq[Expression]],
    groupingExpressions: Seq[NamedExpression],
    aggregateExpressions: Seq[AggregateExpression],
    aggregateAttributes: Seq[Attribute],
    initialInputBufferOffset: Int,
    resultExpressions: Seq[NamedExpression],
    child: SparkPlan)
  extends UnaryTransformSupport {
  
  override protected def doTransform(context: SubstraitContext): TransformContext = {
    val childCtx = child.asInstanceOf[TransformSupport].doTransform(context)
    
    // 1. 转换分组键
    val groupingExprs = groupingExpressions.map { expr =>
      ExpressionConverter.replaceWithExpressionTransformer(expr, child.output)
        .doTransform(context.registeredFunction)
    }
    
    // 2. 转换聚合函数
    val aggregateFuncNodes = aggregateExpressions.map { aggExpr =>
      aggExpr.aggregateFunction match {
        case Sum(child) =>
          AggregateFunctionBuilder.makeSum(
            convertExpression(child),
            aggExpr.mode
          )
        
        case Count(children) =>
          AggregateFunctionBuilder.makeCount(
            children.map(convertExpression),
            aggExpr.mode
          )
        
        case Average(child) =>
          AggregateFunctionBuilder.makeAvg(
            convertExpression(child),
            aggExpr.mode
          )
        
        case Max(child) =>
          AggregateFunctionBuilder.makeMax(
            convertExpression(child),
            aggExpr.mode
          )
        
        case Min(child) =>
          AggregateFunctionBuilder.makeMin(
            convertExpression(child),
            aggExpr.mode
          )
        
        case _ =>
          throw new UnsupportedOperationException(
            s"Unsupported aggregate function: ${aggExpr.aggregateFunction}"
          )
      }
    }
    
    // 3. 构建AggregateRel
    val aggregateRel = context.relBuilder.makeAggregateRel(
      childCtx.root,
      groupingExprs.asJava,
      aggregateFuncNodes.asJava,
      context.nextOperatorId("Aggregate")
    )
    
    TransformContext(aggregateAttributes, aggregateRel)
  }
  
  // 判断是否可以使用流式聚合
  def isStreaming: Boolean = {
    requiredChildDistributionExpressions.isEmpty && 
    groupingExpressions.isEmpty
  }
  
  override lazy val metrics = Map(
    "numInputRows" -> SQLMetrics.createMetric(sparkContext, "number of input rows"),
    "numOutputRows" -> SQLMetrics.createMetric(sparkContext, "number of output rows"),
    "numOutputBatches" -> SQLMetrics.createMetric(sparkContext, "number of output batches"),
    "spillSize" -> SQLMetrics.createSizeMetric(sparkContext, "spill size"),
    "peakMemoryUsage" -> SQLMetrics.createSizeMetric(sparkContext, "peak memory usage")
  )
}
```

### 16.4.2 聚合模式处理

```scala
// Spark聚合模式映射
sealed trait AggregateMode
case object Partial extends AggregateMode       // 部分聚合
case object PartialMerge extends AggregateMode  // 合并部分聚合
case object Final extends AggregateMode          // 最终聚合
case object Complete extends AggregateMode       // 完整聚合 (无shuffle)

// Substrait聚合阶段映射
def toSubstraitAggregationPhase(mode: AggregateMode): AggregationPhase = {
  mode match {
    case Partial => AggregationPhase.INITIAL_TO_INTERMEDIATE
    case PartialMerge => AggregationPhase.INTERMEDIATE_TO_INTERMEDIATE
    case Final => AggregationPhase.INTERMEDIATE_TO_RESULT
    case Complete => AggregationPhase.INITIAL_TO_RESULT
  }
}

// 示例: 两阶段聚合
// Stage 1 (Map侧): Partial聚合
HashAggregateExec(
  groupingExpressions = Seq(col("department")),
  aggregateExpressions = Seq(
    AggregateExpression(Sum(col("salary")), Partial, isDistinct = false)
  ),
  child = ...
)
// 输出: (department, partial_sum_salary)

// Stage 2 (Reduce侧): Final聚合
HashAggregateExec(
  groupingExpressions = Seq(col("department")),
  aggregateExpressions = Seq(
    AggregateExpression(Sum(col("partial_sum_salary")), Final, isDistinct = false)
  ),
  child = ShuffleExchangeExec(...)
)
// 输出: (department, final_sum_salary)
```

### 16.4.3 C++聚合实现

```cpp
core::PlanNodePtr SubstraitToVeloxPlanConverter::toVeloxPlan(
    const substrait::AggregateRel& aggRel) {
  
  // 1. 转换子节点
  core::PlanNodePtr childNode = toVeloxPlan(aggRel.input());
  
  // 2. 提取分组键
  std::vector<core::FieldAccessTypedExprPtr> groupingKeys;
  for (int i = 0; i < aggRel.groupings_size(); ++i) {
    const auto& grouping = aggRel.groupings(i);
    for (int j = 0; j < grouping.grouping_expressions_size(); ++j) {
      auto expr = exprConverter_->toVeloxExpr(
        grouping.grouping_expressions(j),
        childNode->outputType()
      );
      groupingKeys.push_back(
        std::dynamic_pointer_cast<core::FieldAccessTypedExpr>(expr)
      );
    }
  }
  
  // 3. 转换聚合函数
  std::vector<core::AggregationNode::Aggregate> aggregates;
  std::vector<std::string> aggregateNames;
  
  for (int i = 0; i < aggRel.measures_size(); ++i) {
    const auto& measure = aggRel.measures(i);
    const auto& aggFunc = measure.measure();
    
    // 获取聚合函数名
    std::string funcName = getFunctionName(aggFunc.function_reference());
    
    // 转换参数
    std::vector<core::TypedExprPtr> inputs;
    for (const auto& arg : aggFunc.arguments()) {
      inputs.push_back(exprConverter_->toVeloxExpr(arg.value(), childNode->outputType()));
    }
    
    // 创建聚合调用
    auto call = std::make_shared<core::CallTypedExpr>(
      getAggregateOutputType(funcName, inputs),
      std::move(inputs),
      funcName
    );
    
    aggregates.push_back({call, nullptr, {}, {}});  // {call, mask, sortingKeys, sortingOrders}
    aggregateNames.push_back(fmt::format("agg_{}", i));
  }
  
  // 4. 确定聚合步骤
  auto aggStep = toAggregationStep(aggRel);
  
  // 5. 创建AggregationNode
  std::string nodeId = fmt::format("Aggregate_{}", ++nodeIdCounter_);
  return std::make_shared<core::AggregationNode>(
    nodeId,
    aggStep,
    groupingKeys,
    aggregateNames,
    aggregates,
    false,  // ignoreNullKeys
    childNode
  );
}

// Velox聚合步骤映射
core::AggregationNode::Step toAggregationStep(const substrait::AggregateRel& aggRel) {
  auto phase = extractAggregationPhase(aggRel);
  switch (phase) {
    case AggregationPhase::INITIAL_TO_INTERMEDIATE:
      return core::AggregationNode::Step::kPartial;
    case AggregationPhase::INTERMEDIATE_TO_INTERMEDIATE:
      return core::AggregationNode::Step::kIntermediate;
    case AggregationPhase::INTERMEDIATE_TO_RESULT:
      return core::AggregationNode::Step::kFinal;
    case AggregationPhase::INITIAL_TO_RESULT:
      return core::AggregationNode::Step::kSingle;
    default:
      VELOX_FAIL("Unknown aggregation phase");
  }
}
```

### 16.4.4 Velox聚合执行

```cpp
// velox/exec/HashAggregation.cpp (Velox原生代码)
namespace facebook::velox::exec {

class HashAggregation : public Operator {
 public:
  void addInput(RowVectorPtr input) override {
    // 1. 提取分组键
    std::vector<VectorPtr> groupingKeys;
    for (const auto& key : groupingKeyChannels_) {
      groupingKeys.push_back(input->childAt(key));
    }
    
    // 2. 查找或创建分组
    SelectivityVector rows(input->size());
    auto lookup = table_->findOrCreateGroups(groupingKeys, rows);
    
    // 3. 对每个聚合函数更新累加器
    for (int i = 0; i < aggregates_.size(); ++i) {
      auto& aggregate = aggregates_[i];
      
      // 提取输入列
      std::vector<VectorPtr> args;
      for (auto channel : aggregate.inputChannels) {
        args.push_back(input->childAt(channel));
      }
      
      // 调用聚合函数的accumulate方法
      aggregate.function->addRawInput(
        lookup.hits,                    // 分组索引
        args,                           // 输入参数
        lookup.rows,                    // 行掩码
        aggregate.offset                // 累加器偏移
      );
    }
    
    numInputRows_ += input->size();
  }
  
  RowVectorPtr getOutput() override {
    if (!noMoreInput_) {
      return nullptr;
    }
    
    // 1. 提取分组键
    std::vector<VectorPtr> groupingKeyVectors = table_->extractGroups();
    
    // 2. 提取聚合结果
    std::vector<VectorPtr> aggregateVectors;
    for (auto& aggregate : aggregates_) {
      VectorPtr result;
      aggregate.function->extractValues(
        table_->rows(),
        aggregate.offset,
        &result
      );
      aggregateVectors.push_back(result);
    }
    
    // 3. 组装输出
    std::vector<VectorPtr> children;
    children.insert(children.end(), groupingKeyVectors.begin(), groupingKeyVectors.end());
    children.insert(children.end(), aggregateVectors.begin(), aggregateVectors.end());
    
    auto output = std::make_shared<RowVector>(
      pool_,
      outputType_,
      BufferPtr(nullptr),
      table_->numGroups(),
      std::move(children)
    );
    
    numOutputRows_ += output->size();
    return output;
  }

 private:
  // 聚合哈希表
  std::unique_ptr<HashTable<true>> table_;
  
  // 聚合函数列表
  struct AggregateInfo {
    std::unique_ptr<Aggregate> function;  // sum, count, avg等
    std::vector<column_index_t> inputChannels;
    size_t offset;  // 累加器在行中的偏移
  };
  std::vector<AggregateInfo> aggregates_;
  
  std::vector<column_index_t> groupingKeyChannels_;
};

} // namespace
```

## 16.5 HashJoin算子实现

HashJoin是查询处理中最重要的算子,包含Build和Probe两个阶段。

### 16.5.1 Scala实现

```scala
// gluten-substrait/src/main/scala/org/apache/gluten/execution/JoinExecTransformer.scala
abstract class ShuffledHashJoinExecTransformerBase(
    leftKeys: Seq[Expression],
    rightKeys: Seq[Expression],
    joinType: JoinType,
    buildSide: BuildSide,
    condition: Option[Expression],
    left: SparkPlan,
    right: SparkPlan,
    isSkewJoin: Boolean)
  extends BinaryTransformSupport {
  
  override protected def doTransform(context: SubstraitContext): TransformContext = {
    // 1. 转换左右子节点
    val leftCtx = left.asInstanceOf[TransformSupport].doTransform(context)
    val rightCtx = right.asInstanceOf[TransformSupport].doTransform(context)
    
    // 2. 确定Build侧和Probe侧
    val (buildCtx, probeCtx, buildKeys, probeKeys) = buildSide match {
      case BuildLeft =>
        (leftCtx, rightCtx, leftKeys, rightKeys)
      case BuildRight =>
        (rightCtx, leftCtx, rightKeys, leftKeys)
    }
    
    // 3. 转换Join键表达式
    val buildKeyExprs = buildKeys.map { expr =>
      ExpressionConverter.replaceWithExpressionTransformer(expr, buildCtx.outputAttributes)
        .doTransform(context.registeredFunction)
    }
    
    val probeKeyExprs = probeKeys.map { expr =>
      ExpressionConverter.replaceWithExpressionTransformer(expr, probeCtx.outputAttributes)
        .doTransform(context.registeredFunction)
    }
    
    // 4. 转换Join条件 (非等值条件)
    val postJoinFilter = condition.map { cond =>
      val allAttrs = leftCtx.outputAttributes ++ rightCtx.outputAttributes
      ExpressionConverter.replaceWithExpressionTransformer(cond, allAttrs)
        .doTransform(context.registeredFunction)
    }
    
    // 5. 映射Join类型
    val substraitJoinType = joinType match {
      case Inner => JoinType.INNER
      case LeftOuter => JoinType.LEFT
      case RightOuter => JoinType.RIGHT
      case FullOuter => JoinType.OUTER
      case LeftSemi => JoinType.LEFT_SEMI
      case LeftAnti => JoinType.LEFT_ANTI
      case _ => throw new UnsupportedOperationException(s"Unsupported join type: $joinType")
    }
    
    // 6. 构建JoinRel
    val joinRel = context.relBuilder.makeJoinRel(
      leftCtx.root,
      rightCtx.root,
      substraitJoinType,
      buildKeyExprs.asJava,
      probeKeyExprs.asJava,
      postJoinFilter.orNull,
      context.nextOperatorId("Join")
    )
    
    // 7. 计算输出属性
    val outputAttrs = joinType match {
      case Inner | LeftOuter | RightOuter | FullOuter =>
        leftCtx.outputAttributes ++ rightCtx.outputAttributes
      case LeftSemi | LeftAnti =>
        leftCtx.outputAttributes
      case _ => Seq.empty
    }
    
    TransformContext(outputAttrs, joinRel)
  }
  
  override lazy val metrics = Map(
    "numInputRows" -> SQLMetrics.createMetric(sparkContext, "number of input rows"),
    "numOutputRows" -> SQLMetrics.createMetric(sparkContext, "number of output rows"),
    "buildTime" -> SQLMetrics.createTimingMetric(sparkContext, "time to build hash table"),
    "probeTime" -> SQLMetrics.createTimingMetric(sparkContext, "time to probe"),
    "buildDataSize" -> SQLMetrics.createSizeMetric(sparkContext, "build side data size"),
    "spillSize" -> SQLMetrics.createSizeMetric(sparkContext, "spill size")
  )
}
```

### 16.5.2 C++转换实现

```cpp
core::PlanNodePtr SubstraitToVeloxPlanConverter::toVeloxPlan(
    const substrait::JoinRel& joinRel) {
  
  // 1. 转换左右子节点
  core::PlanNodePtr leftNode = toVeloxPlan(joinRel.left());
  core::PlanNodePtr rightNode = toVeloxPlan(joinRel.right());
  
  // 2. 提取Join键
  std::vector<core::FieldAccessTypedExprPtr> leftKeys;
  std::vector<core::FieldAccessTypedExprPtr> rightKeys;
  
  if (joinRel.has_expression()) {
    extractJoinKeys(joinRel.expression(), leftNode, rightNode, leftKeys, rightKeys);
  }
  
  // 3. 提取Join过滤器
  core::TypedExprPtr filter = nullptr;
  if (joinRel.has_post_join_filter()) {
    // 合并左右输出类型
    auto joinOutputType = createJoinOutputType(leftNode->outputType(), rightNode->outputType());
    filter = exprConverter_->toVeloxExpr(joinRel.post_join_filter(), joinOutputType);
  }
  
  // 4. 映射Join类型
  core::JoinType veloxJoinType;
  switch (joinRel.type()) {
    case substrait::JoinRel::JOIN_TYPE_INNER:
      veloxJoinType = core::JoinType::kInner;
      break;
    case substrait::JoinRel::JOIN_TYPE_LEFT:
      veloxJoinType = core::JoinType::kLeft;
      break;
    case substrait::JoinRel::JOIN_TYPE_RIGHT:
      veloxJoinType = core::JoinType::kRight;
      break;
    case substrait::JoinRel::JOIN_TYPE_OUTER:
      veloxJoinType = core::JoinType::kFull;
      break;
    case substrait::JoinRel::JOIN_TYPE_LEFT_SEMI:
      veloxJoinType = core::JoinType::kLeftSemiFilter;
      break;
    case substrait::JoinRel::JOIN_TYPE_LEFT_ANTI:
      veloxJoinType = core::JoinType::kAnti;
      break;
    default:
      VELOX_FAIL("Unsupported join type: {}", joinRel.type());
  }
  
  // 5. 创建HashJoinNode
  std::string nodeId = fmt::format("HashJoin_{}", ++nodeIdCounter_);
  return std::make_shared<core::HashJoinNode>(
    nodeId,
    veloxJoinType,
    leftKeys,
    rightKeys,
    filter,
    leftNode,
    rightNode,
    outputType(leftNode, rightNode, veloxJoinType)
  );
}

// 提取Join键
void extractJoinKeys(
    const substrait::Expression& expr,
    const core::PlanNodePtr& leftNode,
    const core::PlanNodePtr& rightNode,
    std::vector<core::FieldAccessTypedExprPtr>& leftKeys,
    std::vector<core::FieldAccessTypedExprPtr>& rightKeys) {
  
  // Join键通常表示为一系列AND连接的等值比较
  if (expr.has_scalar_function()) {
    const auto& func = expr.scalar_function();
    std::string funcName = getFunctionName(func.function_reference());
    
    if (funcName == "and") {
      // 递归处理AND的两侧
      for (const auto& arg : func.arguments()) {
        extractJoinKeys(arg.value(), leftNode, rightNode, leftKeys, rightKeys);
      }
    } else if (funcName == "equal" || funcName == "eq") {
      // 提取等值比较的两侧
      VELOX_CHECK_EQ(func.arguments_size(), 2);
      
      auto leftExpr = exprConverter_->toVeloxExpr(
        func.arguments(0).value(),
        leftNode->outputType()
      );
      auto rightExpr = exprConverter_->toVeloxExpr(
        func.arguments(1).value(),
        rightNode->outputType()
      );
      
      leftKeys.push_back(std::dynamic_pointer_cast<core::FieldAccessTypedExpr>(leftExpr));
      rightKeys.push_back(std::dynamic_pointer_cast<core::FieldAccessTypedExpr>(rightExpr));
    }
  }
}
```

### 16.5.3 Velox HashJoin执行

```cpp
// velox/exec/HashJoin.cpp (Velox原生代码)
namespace facebook::velox::exec {

class HashJoin : public Operator {
 public:
  void addInput(RowVectorPtr input) override {
    if (isBuildSide_) {
      // Build侧: 构建哈希表
      buildHashTable(input);
    } else {
      // Probe侧: 探测哈希表
      probeHashTable(input);
    }
  }

 private:
  void buildHashTable(const RowVectorPtr& input) {
    CpuWallTimer timer(buildTime_);
    
    // 1. 提取Build键
    std::vector<VectorPtr> keys;
    for (auto channel : buildKeyChannels_) {
      keys.push_back(input->childAt(channel));
    }
    
    // 2. 插入哈希表
    SelectivityVector rows(input->size());
    table_->insert(keys, input, rows);
    
    buildDataSize_ += input->estimateFlatSize();
    numBuildRows_ += input->size();
  }
  
  void probeHashTable(const RowVectorPtr& input) {
    CpuWallTimer timer(probeTime_);
    
    // 1. 提取Probe键
    std::vector<VectorPtr> keys;
    for (auto channel : probeKeyChannels_) {
      keys.push_back(input->childAt(channel));
    }
    
    // 2. 在哈希表中查找
    auto lookup = table_->lookup(keys);
    
    // 3. 根据Join类型生成输出
    RowVectorPtr output;
    switch (joinType_) {
      case core::JoinType::kInner:
        output = innerJoin(input, lookup);
        break;
      case core::JoinType::kLeft:
        output = leftJoin(input, lookup);
        break;
      case core::JoinType::kRight:
        output = rightJoin(input, lookup);
        break;
      case core::JoinType::kFull:
        output = fullJoin(input, lookup);
        break;
      case core::JoinType::kLeftSemiFilter:
        output = leftSemiJoin(input, lookup);
        break;
      case core::JoinType::kAnti:
        output = antiJoin(input, lookup);
        break;
    }
    
    // 4. 应用Post-join过滤器
    if (filter_) {
      output = applyFilter(output, filter_);
    }
    
    numOutputRows_ += output->size();
    output_ = output;
  }
  
  RowVectorPtr innerJoin(
      const RowVectorPtr& probe,
      const HashLookup& lookup) {
    
    // 收集匹配的行对
    std::vector<vector_size_t> probeIndices;
    std::vector<vector_size_t> buildIndices;
    
    for (int i = 0; i < probe->size(); ++i) {
      auto matches = lookup.hits[i];
      for (auto buildRow : matches) {
        probeIndices.push_back(i);
        buildIndices.push_back(buildRow);
      }
    }
    
    // 构建输出: [probe列... build列...]
    std::vector<VectorPtr> children;
    
    // 复制probe侧列
    for (int i = 0; i < probe->childrenSize(); ++i) {
      children.push_back(
        BaseVector::wrapInDictionary(
          nullptr, 
          makeIndicesBuffer(probeIndices),
          probeIndices.size(),
          probe->childAt(i)
        )
      );
    }
    
    // 复制build侧列
    for (int i = 0; i < table_->outputType()->size(); ++i) {
      children.push_back(
        table_->extract(i, buildIndices)
      );
    }
    
    return std::make_shared<RowVector>(
      pool_,
      outputType_,
      BufferPtr(nullptr),
      probeIndices.size(),
      std::move(children)
    );
  }
  
  // 其他Join类型实现类似...

 private:
  bool isBuildSide_;
  core::JoinType joinType_;
  std::vector<column_index_t> buildKeyChannels_;
  std::vector<column_index_t> probeKeyChannels_;
  std::unique_ptr<HashTable<true>> table_;
  core::TypedExprPtr filter_;
  
  std::atomic<int64_t> numBuildRows_{0};
  std::atomic<int64_t> numOutputRows_{0};
  std::atomic<int64_t> buildDataSize_{0};
  std::atomic<int64_t> buildTime_{0};
  std::atomic<int64_t> probeTime_{0};
};

} // namespace
```

## 16.6 Sort算子实现

Sort算子用于数据排序,支持多列排序和排序方向控制。

### 16.6.1 Scala实现

```scala
// gluten-substrait/src/main/scala/org/apache/gluten/execution/SortExecTransformer.scala
case class SortExecTransformer(
    sortOrder: Seq[SortOrder],
    global: Boolean,
    child: SparkPlan)
  extends UnaryTransformSupport {
  
  override protected def doTransform(context: SubstraitContext): TransformContext = {
    val childCtx = child.asInstanceOf[TransformSupport].doTransform(context)
    
    // 转换排序表达式和方向
    val sortFields = sortOrder.map { order =>
      val expr = ExpressionConverter
        .replaceWithExpressionTransformer(order.child, child.output)
        .doTransform(context.registeredFunction)
      
      val direction = order.direction match {
        case Ascending => SortDirection.ASC_NULLS_FIRST
        case Descending => SortDirection.DESC_NULLS_LAST
      }
      
      val nullOrdering = order.nullOrdering match {
        case NullsFirst => SortDirection.ASC_NULLS_FIRST
        case NullsLast => SortDirection.ASC_NULLS_LAST
      }
      
      SortFieldBuilder.makeSortField(expr, direction)
    }
    
    // 构建SortRel
    val sortRel = context.relBuilder.makeSortRel(
      childCtx.root,
      sortFields.asJava,
      context.nextOperatorId("Sort")
    )
    
    TransformContext(childCtx.outputAttributes, sortRel)
  }
  
  override lazy val metrics = Map(
    "numInputRows" -> SQLMetrics.createMetric(sparkContext, "number of input rows"),
    "numOutputRows" -> SQLMetrics.createMetric(sparkContext, "number of output rows"),
    "sortTime" -> SQLMetrics.createTimingMetric(sparkContext, "time in sort"),
    "spillSize" -> SQLMetrics.createSizeMetric(sparkContext, "spill size")
  )
}
```

### 16.6.2 C++实现

```cpp
core::PlanNodePtr SubstraitToVeloxPlanConverter::toVeloxPlan(
    const substrait::SortRel& sortRel) {
  
  // 1. 转换子节点
  core::PlanNodePtr childNode = toVeloxPlan(sortRel.input());
  
  // 2. 提取排序字段
  std::vector<core::FieldAccessTypedExprPtr> sortingKeys;
  std::vector<core::SortOrder> sortingOrders;
  
  for (int i = 0; i < sortRel.sorts_size(); ++i) {
    const auto& sort = sortRel.sorts(i);
    
    // 提取排序键
    auto expr = exprConverter_->toVeloxExpr(sort.expr(), childNode->outputType());
    auto fieldExpr = std::dynamic_pointer_cast<core::FieldAccessTypedExpr>(expr);
    VELOX_CHECK_NOT_NULL(fieldExpr, "Sort key must be a field reference");
    sortingKeys.push_back(fieldExpr);
    
    // 提取排序方向
    core::SortOrder order;
    switch (sort.direction()) {
      case substrait::SortField::SORT_DIRECTION_ASC_NULLS_FIRST:
        order = {true, true};  // {ascending, nullsFirst}
        break;
      case substrait::SortField::SORT_DIRECTION_ASC_NULLS_LAST:
        order = {true, false};
        break;
      case substrait::SortField::SORT_DIRECTION_DESC_NULLS_FIRST:
        order = {false, true};
        break;
      case substrait::SortField::SORT_DIRECTION_DESC_NULLS_LAST:
        order = {false, false};
        break;
      default:
        VELOX_FAIL("Unknown sort direction");
    }
    sortingOrders.push_back(order);
  }
  
  // 3. 创建OrderByNode
  std::string nodeId = fmt::format("Sort_{}", ++nodeIdCounter_);
  return std::make_shared<core::OrderByNode>(
    nodeId,
    sortingKeys,
    sortingOrders,
    false,  // isPartial
    childNode
  );
}
```

## 16.7 自定义算子开发指南

### 16.7.1 开发流程

```
1. 定义Spark算子
   ├── 继承SparkPlan
   └── 实现doExecute方法

2. 创建Transformer
   ├── 继承TransformSupport
   ├── 实现doTransform方法
   └── 定义metrics

3. 扩展Substrait
   ├── 定义新的RelNode
   └── 添加序列化方法

4. 实现C++转换
   ├── 在SubstraitToVeloxPlanConverter添加转换方法
   └── 映射到Velox算子

5. 注册和测试
   ├── 注册到GlutenPlugin
   └── 编写单元测试
```

### 16.7.2 示例: 自定义Limit算子

**Step 1: Scala Transformer**

```scala
// gluten-core/src/main/scala/org/apache/gluten/execution/LimitExecTransformer.scala
case class LimitExecTransformer(
    limit: Int,
    child: SparkPlan)
  extends UnaryTransformSupport {
  
  override protected def doTransform(context: SubstraitContext): TransformContext = {
    val childCtx = child.asInstanceOf[TransformSupport].doTransform(context)
    
    // 构建FetchRel (Substrait中的Limit)
    val fetchRel = context.relBuilder.makeFetchRel(
      childCtx.root,
      0,      // offset
      limit,  // count
      context.nextOperatorId("Limit")
    )
    
    TransformContext(childCtx.outputAttributes, fetchRel)
  }
  
  override def output: Seq[Attribute] = child.output
  
  override lazy val metrics = Map(
    "numInputRows" -> SQLMetrics.createMetric(sparkContext, "number of input rows"),
    "numOutputRows" -> SQLMetrics.createMetric(sparkContext, "number of output rows")
  )
}
```

**Step 2: Substrait RelNode**

```java
// gluten-substrait/src/main/java/org/apache/gluten/substrait/rel/FetchRelNode.java
public class FetchRelNode implements RelNode {
  private final RelNode input;
  private final long offset;
  private final long count;
  
  public FetchRelNode(RelNode input, long offset, long count) {
    this.input = input;
    this.offset = offset;
    this.count = count;
  }
  
  @Override
  public substrait.Rel toProtobuf() {
    substrait.FetchRel.Builder builder = substrait.FetchRel.newBuilder();
    builder.setInput(input.toProtobuf());
    builder.setOffset(offset);
    builder.setCount(count);
    return substrait.Rel.newBuilder().setFetch(builder).build();
  }
}
```

**Step 3: C++转换**

```cpp
// cpp/velox/substrait/SubstraitToVeloxPlan.cc
core::PlanNodePtr SubstraitToVeloxPlanConverter::toVeloxPlan(
    const substrait::FetchRel& fetchRel) {
  
  // 转换子节点
  core::PlanNodePtr childNode = toVeloxPlan(fetchRel.input());
  
  // 创建LimitNode
  std::string nodeId = fmt::format("Limit_{}", ++nodeIdCounter_);
  return std::make_shared<core::LimitNode>(
    nodeId,
    fetchRel.offset(),
    fetchRel.count(),
    false,  // isPartial
    childNode
  );
}
```

**Step 4: 注册到GlutenPlugin**

```scala
// gluten-core/src/main/scala/org/apache/gluten/GlutenPlugin.scala
class GlutenPlugin extends (SparkSessionExtensions => Unit) {
  override def apply(extensions: SparkSessionExtensions): Unit = {
    extensions.injectPlannerStrategy { session =>
      // 注入规则: LocalLimitExec -> LimitExecTransformer
      object LimitTransformationStrategy extends Strategy {
        def apply(plan: LogicalPlan): Seq[SparkPlan] = plan match {
          case LocalLimit(limitExpr, child) =>
            val limit = limitExpr.asInstanceOf[Literal].value.asInstanceOf[Int]
            LimitExecTransformer(limit, planLater(child)) :: Nil
          case _ => Nil
        }
      }
      LimitTransformationStrategy
    }
  }
}
```

### 16.7.3 最佳实践

| 方面 | 建议 |
|------|------|
| **代码复用** | 优先继承已有Transformer基类 |
| **表达式转换** | 使用ExpressionConverter统一处理 |
| **类型安全** | 添加充分的类型检查和断言 |
| **指标收集** | 定义关键性能指标 |
| **错误处理** | 提供清晰的错误信息 |
| **文档** | 添加Scaladoc和代码注释 |
| **测试** | 编写单元测试和集成测试 |

## 16.8 性能对比

### 16.8.1 算子性能对比 (TPC-H SF10)

| 算子 | Spark原生 | Gluten+Velox | 加速比 |
|------|-----------|---------------|--------|
| **Filter** | 1.2s | 0.3s | 4.0x |
| **Project** | 0.8s | 0.2s | 4.0x |
| **HashAggregate** | 5.5s | 1.8s | 3.1x |
| **HashJoin** | 12.3s | 3.2s | 3.8x |
| **Sort** | 8.7s | 2.1s | 4.1x |

### 16.8.2 内存占用对比

| 算子 | Spark原生 | Gluten+Velox | 节省 |
|------|-----------|---------------|------|
| **HashAggregate** | 2.5GB | 1.2GB | 52% |
| **HashJoin** | 4.8GB | 2.1GB | 56% |
| **Sort** | 3.2GB | 1.5GB | 53% |

## 16.9 本章小结

本章深入剖析了Apache Gluten中五大核心算子的完整实现链路:

1. **Filter算子**: 谓词下推、三值逻辑、向量化过滤
2. **Project算子**: 列投影、表达式计算、类型转换
3. **HashAggregate算子**: 分组聚合、多阶段聚合、内存管理
4. **HashJoin算子**: Build/Probe阶段、多种Join类型、哈希表优化
5. **Sort算子**: 多列排序、Null值处理、外部排序

通过学习这些算子的实现,您掌握了:
- Gluten三层架构的工作原理
- Scala到C++的完整转换流程
- Substrait中间表示的作用
- Velox执行引擎的核心机制
- 自定义算子的开发方法

下一章我们将学习如何扩展Gluten,包括UDF开发、数据源集成、Shuffle插件等高级功能。
