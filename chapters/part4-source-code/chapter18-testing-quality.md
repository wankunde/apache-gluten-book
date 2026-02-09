# 第18章：测试与质量保证

测试是保证Apache Gluten代码质量的关键环节。本章详细介绍Gluten的测试体系，包括单元测试、集成测试、性能测试和CI/CD流程，帮助开发者编写高质量的测试代码。

## 18.1 测试框架概览

### 18.1.1 测试金字塔

Gluten遵循经典的测试金字塔模型：

```
        ┌──────────────┐
        │  手工测试     │  少量
        ├──────────────┤
        │  端到端测试   │  适中
        ├──────────────┤
        │  集成测试     │  较多
        ├──────────────┤
        │  单元测试     │  大量
        └──────────────┘
```

**测试分层策略**：

| 测试层级 | 比例 | 执行时间 | 覆盖范围 | 主要工具 |
|---------|------|---------|---------|---------|
| 单元测试 | 70% | 秒级 | 函数/类 | ScalaTest, Google Test |
| 集成测试 | 20% | 分钟级 | 模块间交互 | gluten-ut, TPC-H |
| 端到端测试 | 8% | 小时级 | 完整流程 | TPC-DS, 实际workload |
| 手工测试 | 2% | 天级 | 探索性测试 | 手工验证 |

### 18.1.2 测试工具链

**Scala/Java测试**

```xml
<!-- pom.xml -->
<dependency>
  <groupId>org.scalatest</groupId>
  <artifactId>scalatest_2.12</artifactId>
  <version>3.2.15</version>
  <scope>test</scope>
</dependency>

<dependency>
  <groupId>junit</groupId>
  <artifactId>junit</artifactId>
  <version>4.13.2</version>
  <scope>test</scope>
</dependency>

<dependency>
  <groupId>org.mockito</groupId>
  <artifactId>mockito-core</artifactId>
  <version>5.3.1</version>
  <scope>test</scope>
</dependency>
```

**C++测试**

```cmake
# CMakeLists.txt
find_package(GTest REQUIRED)
find_package(GMock REQUIRED)

add_executable(velox_tests
  test/VeloxRuntimeTest.cpp
  test/MemoryPoolTest.cpp
)

target_link_libraries(velox_tests
  PRIVATE
  GTest::gtest
  GTest::gtest_main
  GTest::gmock
  velox_core
)

gtest_discover_tests(velox_tests)
```

## 18.2 单元测试

### 18.2.1 Scala单元测试示例

**测试表达式转换**

```scala
// gluten-core/src/test/scala/org/apache/gluten/expression/ExpressionTransformerSuite.scala
package org.apache.gluten.expression

import org.apache.spark.sql.catalyst.expressions._
import org.apache.spark.sql.types._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ExpressionTransformerSuite extends AnyFunSuite with Matchers {
  
  test("transform Add expression") {
    val left = Literal(1, IntegerType)
    val right = Literal(2, IntegerType)
    val add = Add(left, right)
    
    val transformer = ExpressionTransformer.create(add)
    
    transformer shouldBe a[AddTransformer]
    transformer.original shouldBe add
  }
  
  test("transform complex expression tree") {
    // (a + b) * (c - d)
    val a = AttributeReference("a", IntegerType)()
    val b = AttributeReference("b", IntegerType)()
    val c = AttributeReference("c", IntegerType)()
    val d = AttributeReference("d", IntegerType)()
    
    val add = Add(a, b)
    val subtract = Subtract(c, d)
    val multiply = Multiply(add, subtract)
    
    val transformer = ExpressionTransformer.create(multiply)
    
    transformer shouldBe a[MultiplyTransformer]
    transformer.children should have size 2
  }
  
  test("handle null values correctly") {
    val nullExpr = Literal(null, StringType)
    val concat = Concat(Seq(nullExpr, Literal("test", StringType)))
    
    val transformer = ExpressionTransformer.create(concat)
    
    // 验证null处理逻辑
    transformer.doTransform(null) should contain("test")
  }
  
  test("throw exception for unsupported expression") {
    val unsupportedExpr = new Expression {
      override def nullable: Boolean = false
      override def eval(input: InternalRow): Any = null
      override def dataType: DataType = StringType
      override def children: Seq[Expression] = Seq.empty
      override protected def doGenCode(ctx: CodegenContext, ev: ExprCode): ExprCode = ???
      override protected def withNewChildrenInternal(newChildren: IndexedSeq[Expression]): Expression = this
    }
    
    assertThrows[UnsupportedOperationException] {
      ExpressionTransformer.create(unsupportedExpr)
    }
  }
}
```

**测试算子转换**

```scala
// gluten-core/src/test/scala/org/apache/gluten/execution/OperatorTransformerSuite.scala
package org.apache.gluten.execution

import org.apache.spark.sql.catalyst.plans.logical._
import org.apache.spark.sql.execution._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.BeforeAndAfterEach

class OperatorTransformerSuite extends AnyFunSuite with BeforeAndAfterEach {
  
  var transformer: OperatorTransformer = _
  
  override def beforeEach(): Unit = {
    transformer = new OperatorTransformer(BackendType.VELOX)
  }
  
  test("transform ProjectExec to ProjectTransformer") {
    val project = ProjectExec(
      Seq(AttributeReference("col1", IntegerType)()),
      RDDScanExec(Seq.empty, null, "test")
    )
    
    val result = transformer.transform(project)
    
    result shouldBe a[ProjectTransformer]
    result.asInstanceOf[ProjectTransformer].projectList should have size 1
  }
  
  test("transform FilterExec with complex predicate") {
    val predicate = And(
      GreaterThan(
        AttributeReference("age", IntegerType)(),
        Literal(18, IntegerType)
      ),
      LessThan(
        AttributeReference("age", IntegerType)(),
        Literal(65, IntegerType)
      )
    )
    
    val filter = FilterExec(predicate, RDDScanExec(Seq.empty, null, "test"))
    
    val result = transformer.transform(filter)
    
    result shouldBe a[FilterTransformer]
    result.asInstanceOf[FilterTransformer].condition shouldBe predicate
  }
  
  test("skip transformation for unsupported operator") {
    val window = WindowExec(
      Seq.empty,
      Seq.empty,
      Seq.empty,
      RDDScanExec(Seq.empty, null, "test")
    )
    
    val result = transformer.transform(window)
    
    // 不支持的算子应该返回原算子
    result shouldBe window
  }
}
```

### 18.2.2 C++单元测试示例

**测试Velox运行时**

```cpp
// cpp/velox/test/VeloxRuntimeTest.cpp
#include <gtest/gtest.h>
#include <gmock/gmock.h>
#include "velox/exec/Driver.h"
#include "velox/vector/tests/VectorTestBase.h"

namespace facebook::velox::exec::test {

class VeloxRuntimeTest : public testing::Test, public test::VectorTestBase {
 protected:
  void SetUp() override {
    executor_ = std::make_unique<folly::CPUThreadPoolExecutor>(4);
    driver_ = nullptr;
  }
  
  void TearDown() override {
    executor_->join();
  }
  
  std::unique_ptr<folly::CPUThreadPoolExecutor> executor_;
  std::unique_ptr<Driver> driver_;
};

TEST_F(VeloxRuntimeTest, BasicProjection) {
  // 创建输入向量
  auto inputVector = makeRowVector({
    makeFlatVector<int32_t>({1, 2, 3, 4, 5}),
    makeFlatVector<std::string>({"a", "b", "c", "d", "e"})
  });
  
  // 创建投影表达式：col0 * 2
  auto expression = makeExpression("col0 * 2", asRowType(inputVector->type()));
  
  // 执行投影
  auto result = evaluate(*expression, makeRowVector({inputVector}));
  
  // 验证结果
  auto expected = makeFlatVector<int32_t>({2, 4, 6, 8, 10});
  test::assertEqualVectors(expected, result);
}

TEST_F(VeloxRuntimeTest, FilterWithPredicate) {
  // 创建输入数据
  auto inputVector = makeRowVector({
    makeFlatVector<int32_t>({10, 20, 30, 40, 50}),
    makeFlatVector<std::string>({"foo", "bar", "baz", "qux", "quux"})
  });
  
  // 创建过滤表达式：col0 > 25
  auto predicate = makeExpression("col0 > 25", asRowType(inputVector->type()));
  
  // 执行过滤
  SelectivityVector rows(inputVector->size());
  evaluate(*predicate, makeRowVector({inputVector}), rows);
  
  // 验证选中的行
  EXPECT_EQ(rows.countSelected(), 3);  // 30, 40, 50
  EXPECT_FALSE(rows.isValid(0));  // 10
  EXPECT_FALSE(rows.isValid(1));  // 20
  EXPECT_TRUE(rows.isValid(2));   // 30
  EXPECT_TRUE(rows.isValid(3));   // 40
  EXPECT_TRUE(rows.isValid(4));   // 50
}

TEST_F(VeloxRuntimeTest, AggregateSum) {
  // 创建输入数据
  auto inputVector = makeRowVector({
    makeFlatVector<int32_t>({1, 1, 2, 2, 3}),
    makeFlatVector<double>({10.0, 20.0, 30.0, 40.0, 50.0})
  });
  
  // 创建聚合计划：SUM(col1) GROUP BY col0
  auto plan = PlanBuilder()
      .values({inputVector})
      .singleAggregation({"col0"}, {"sum(col1)"})
      .planNode();
  
  // 执行
  auto results = AssertQueryBuilder(plan).copyResults(pool());
  
  // 验证结果
  auto expected = makeRowVector({
    makeFlatVector<int32_t>({1, 2, 3}),
    makeFlatVector<double>({30.0, 70.0, 50.0})
  });
  
  test::assertEqualVectors(expected, results);
}

TEST_F(VeloxRuntimeTest, HandleNullValues) {
  // 创建包含NULL的输入
  auto inputVector = makeRowVector({
    makeNullableFlatVector<int32_t>({1, std::nullopt, 3, std::nullopt, 5}),
    makeFlatVector<std::string>({"a", "b", "c", "d", "e"})
  });
  
  // 测试NULL处理
  auto expression = makeExpression("col0 + 10", asRowType(inputVector->type()));
  auto result = evaluate(*expression, makeRowVector({inputVector}));
  
  // 验证NULL传播
  auto expected = makeNullableFlatVector<int32_t>(
      {11, std::nullopt, 13, std::nullopt, 15});
  
  test::assertEqualVectors(expected, result);
}

} // namespace facebook::velox::exec::test
```

**测试内存池**

```cpp
// cpp/velox/test/MemoryPoolTest.cpp
#include <gtest/gtest.h>
#include "velox/common/memory/Memory.h"

namespace facebook::velox::memory::test {

class MemoryPoolTest : public testing::Test {
 protected:
  void SetUp() override {
    manager_ = MemoryManager::getInstance();
    rootPool_ = manager_->addRootPool("test_root", 1 << 30);  // 1GB
  }
  
  void TearDown() override {
    rootPool_.reset();
  }
  
  std::shared_ptr<MemoryManager> manager_;
  std::shared_ptr<MemoryPool> rootPool_;
};

TEST_F(MemoryPoolTest, BasicAllocation) {
  auto childPool = rootPool_->addLeafChild("child");
  
  // 分配内存
  void* buffer = childPool->allocate(1024);
  EXPECT_NE(buffer, nullptr);
  EXPECT_EQ(childPool->currentBytes(), 1024);
  
  // 释放内存
  childPool->free(buffer, 1024);
  EXPECT_EQ(childPool->currentBytes(), 0);
}

TEST_F(MemoryPoolTest, ExceedLimit) {
  auto childPool = rootPool_->addLeafChild("child", 1024);  // 1KB限制
  
  // 尝试分配超过限制
  EXPECT_THROW(
    childPool->allocate(2048),
    VeloxRuntimeError
  );
}

TEST_F(MemoryPoolTest, MultipleChildren) {
  auto child1 = rootPool_->addLeafChild("child1");
  auto child2 = rootPool_->addLeafChild("child2");
  
  void* buf1 = child1->allocate(1024);
  void* buf2 = child2->allocate(2048);
  
  EXPECT_EQ(rootPool_->currentBytes(), 3072);
  EXPECT_EQ(child1->currentBytes(), 1024);
  EXPECT_EQ(child2->currentBytes(), 2048);
  
  child1->free(buf1, 1024);
  child2->free(buf2, 2048);
  
  EXPECT_EQ(rootPool_->currentBytes(), 0);
}

TEST_F(MemoryPoolTest, ArenaAllocation) {
  auto childPool = rootPool_->addLeafChild("child");
  
  // 使用Arena分配器
  std::vector<void*> allocations;
  for (int i = 0; i < 100; ++i) {
    allocations.push_back(childPool->allocate(64));
  }
  
  // 验证总内存使用
  EXPECT_GE(childPool->currentBytes(), 6400);
  
  // 批量释放
  for (auto* ptr : allocations) {
    childPool->free(ptr, 64);
  }
  
  EXPECT_EQ(childPool->currentBytes(), 0);
}

} // namespace facebook::velox::memory::test
```

### 18.2.3 Mock和Fixture使用

**Scala Mock示例**

```scala
// gluten-core/src/test/scala/org/apache/gluten/backend/BackendApiMockSuite.scala
package org.apache.gluten.backend

import org.mockito.Mockito._
import org.mockito.ArgumentMatchers._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.mockito.MockitoSugar

class BackendApiMockSuite extends AnyFunSuite with MockitoSugar {
  
  test("mock backend API calls") {
    // 创建mock对象
    val mockBackend = mock[BackendApi]
    
    // 设置mock行为
    when(mockBackend.initialize(any())).thenReturn(true)
    when(mockBackend.getBackendName).thenReturn("MockBackend")
    
    // 执行测试
    val result = mockBackend.initialize(Map.empty)
    
    // 验证
    assert(result === true)
    verify(mockBackend, times(1)).initialize(any())
    
    // 验证方法调用
    assert(mockBackend.getBackendName === "MockBackend")
  }
  
  test("mock with argument capture") {
    val mockBackend = mock[BackendApi]
    val captor = ArgumentCaptor.forClass(classOf[Map[String, String]])
    
    mockBackend.initialize(Map("key" -> "value"))
    
    verify(mockBackend).initialize(captor.capture())
    assert(captor.getValue.get("key") === Some("value"))
  }
}
```

**C++ Mock示例**

```cpp
// cpp/velox/test/MockTest.cpp
#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include "velox/exec/Operator.h"

namespace facebook::velox::exec::test {

// Mock Operator
class MockOperator : public Operator {
 public:
  MOCK_METHOD(BlockingReason, isBlocked, (ContinueFuture*), (override));
  MOCK_METHOD(bool, needsInput, (), (const, override));
  MOCK_METHOD(void, addInput, (RowVectorPtr input), (override));
  MOCK_METHOD(RowVectorPtr, getOutput, (), (override));
  MOCK_METHOD(bool, isFinished, (), (override));
};

TEST(MockOperatorTest, TestOperatorInteraction) {
  using ::testing::Return;
  using ::testing::_;
  
  MockOperator mockOp;
  
  // 设置期望
  EXPECT_CALL(mockOp, needsInput())
      .Times(2)
      .WillOnce(Return(true))
      .WillOnce(Return(false));
  
  EXPECT_CALL(mockOp, addInput(_))
      .Times(1);
  
  // 执行测试
  EXPECT_TRUE(mockOp.needsInput());
  mockOp.addInput(nullptr);
  EXPECT_FALSE(mockOp.needsInput());
}

} // namespace facebook::velox::exec::test
```

### 18.2.4 测试覆盖率分析

**Jacoco配置（Java/Scala）**

```xml
<!-- pom.xml -->
<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
  <version>0.8.10</version>
  <executions>
    <execution>
      <goals>
        <goal>prepare-agent</goal>
      </goals>
    </execution>
    <execution>
      <id>report</id>
      <phase>test</phase>
      <goals>
        <goal>report</goal>
      </goals>
    </execution>
    <execution>
      <id>jacoco-check</id>
      <goals>
        <goal>check</goal>
      </goals>
      <configuration>
        <rules>
          <rule>
            <element>PACKAGE</element>
            <limits>
              <limit>
                <counter>LINE</counter>
                <value>COVEREDRATIO</value>
                <minimum>0.70</minimum>
              </limit>
            </limits>
          </rule>
        </rules>
      </configuration>
    </execution>
  </executions>
</plugin>
```

**运行覆盖率测试**

```bash
#!/bin/bash
# run-coverage.sh

set -e

echo "Running Scala/Java tests with coverage..."
mvn clean test jacoco:report

echo "Coverage report generated at:"
echo "  target/site/jacoco/index.html"

# 检查覆盖率阈值
mvn jacoco:check || {
  echo "Coverage below threshold!"
  exit 1
}

echo "Coverage check passed!"
```

**C++覆盖率（gcov/lcov）**

```bash
#!/bin/bash
# cpp-coverage.sh

set -e

BUILD_DIR="build-coverage"

# 编译启用覆盖率
mkdir -p "$BUILD_DIR"
cd "$BUILD_DIR"

cmake .. \
  -DCMAKE_BUILD_TYPE=Debug \
  -DCMAKE_CXX_FLAGS="--coverage" \
  -DCMAKE_EXE_LINKER_FLAGS="--coverage"

make -j$(nproc)

# 运行测试
ctest --output-on-failure

# 生成覆盖率报告
lcov --capture --directory . --output-file coverage.info
lcov --remove coverage.info '/usr/*' --output-file coverage.info
lcov --list coverage.info

# 生成HTML报告
genhtml coverage.info --output-directory coverage-html

echo "Coverage report: coverage-html/index.html"
```

## 18.3 集成测试

### 18.3.1 gluten-ut模块解析

gluten-ut是Gluten的集成测试模块，包含大量端到端测试用例。

**目录结构**

```bash
gluten-ut/
├── spark32/              # Spark 3.2测试
├── spark33/              # Spark 3.3测试
├── spark34/              # Spark 3.4测试
└── common/
    ├── src/test/scala/
    │   └── org/apache/gluten/
    │       ├── utils/
    │       │   └── UTSystemParameters.scala
    │       └── execution/
    │           ├── TestOperator.scala
    │           ├── WholeStageTransformerSuite.scala
    │           └── VeloxTPCHSuite.scala
```

**基础测试套件**

```scala
// gluten-ut/common/src/test/scala/org/apache/gluten/execution/GlutenTestBase.scala
package org.apache.gluten.execution

import org.apache.spark.SparkConf
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite

abstract class GlutenTestBase extends AnyFunSuite with BeforeAndAfterAll {
  
  protected var spark: SparkSession = _
  
  override def beforeAll(): Unit = {
    super.beforeAll()
    
    val conf = new SparkConf()
      .setAppName("GlutenTest")
      .setMaster("local[4]")
      .set("spark.plugins", "org.apache.gluten.GlutenPlugin")
      .set("spark.gluten.sql.columnar.backend.lib", backendType())
      .set("spark.memory.offHeap.enabled", "true")
      .set("spark.memory.offHeap.size", "1g")
    
    additionalSparkConf().foreach { case (k, v) => conf.set(k, v) }
    
    spark = SparkSession.builder().config(conf).getOrCreate()
  }
  
  override def afterAll(): Unit = {
    if (spark != null) {
      spark.stop()
      spark = null
    }
    super.afterAll()
  }
  
  protected def backendType(): String = "velox"
  
  protected def additionalSparkConf(): Map[String, String] = Map.empty
  
  protected def runQuery(sql: String): DataFrame = {
    spark.sql(sql)
  }
  
  protected def compareResult(actual: DataFrame, expected: DataFrame): Unit = {
    val actualRows = actual.collect().sortBy(_.toString())
    val expectedRows = expected.collect().sortBy(_.toString())
    
    assert(actualRows.length === expectedRows.length,
      s"Row count mismatch: ${actualRows.length} vs ${expectedRows.length}")
    
    actualRows.zip(expectedRows).foreach { case (a, e) =>
      assert(a === e, s"Row mismatch:\nActual:   $a\nExpected: $e")
    }
  }
}
```

### 18.3.2 TPC-H测试套件

```scala
// gluten-ut/common/src/test/scala/org/apache/gluten/execution/VeloxTPCHSuite.scala
package org.apache.gluten.execution

import org.apache.spark.sql.DataFrame

class VeloxTPCHSuite extends GlutenTestBase {
  
  private val tpchDataPath = sys.env.getOrElse("TPCH_DATA_PATH", "/data/tpch/sf1")
  
  override def beforeAll(): Unit = {
    super.beforeAll()
    
    // 创建TPC-H表
    createTPCHTables()
  }
  
  private def createTPCHTables(): Unit = {
    // Customer表
    spark.read.parquet(s"$tpchDataPath/customer")
      .createOrReplaceTempView("customer")
    
    // Orders表
    spark.read.parquet(s"$tpchDataPath/orders")
      .createOrReplaceTempView("orders")
    
    // Lineitem表
    spark.read.parquet(s"$tpchDataPath/lineitem")
      .createOrReplaceTempView("lineitem")
    
    // Part表
    spark.read.parquet(s"$tpchDataPath/part")
      .createOrReplaceTempView("part")
    
    // Supplier表
    spark.read.parquet(s"$tpchDataPath/supplier")
      .createOrReplaceTempView("supplier")
    
    // Partsupp表
    spark.read.parquet(s"$tpchDataPath/partsupp")
      .createOrReplaceTempView("partsupp")
    
    // Nation表
    spark.read.parquet(s"$tpchDataPath/nation")
      .createOrReplaceTempView("nation")
    
    // Region表
    spark.read.parquet(s"$tpchDataPath/region")
      .createOrReplaceTempView("region")
  }
  
  test("TPC-H Q1: Pricing Summary Report") {
    val sql = """
      |SELECT
      |  l_returnflag,
      |  l_linestatus,
      |  SUM(l_quantity) AS sum_qty,
      |  SUM(l_extendedprice) AS sum_base_price,
      |  SUM(l_extendedprice * (1 - l_discount)) AS sum_disc_price,
      |  SUM(l_extendedprice * (1 - l_discount) * (1 + l_tax)) AS sum_charge,
      |  AVG(l_quantity) AS avg_qty,
      |  AVG(l_extendedprice) AS avg_price,
      |  AVG(l_discount) AS avg_disc,
      |  COUNT(*) AS count_order
      |FROM lineitem
      |WHERE l_shipdate <= DATE '1998-09-02'
      |GROUP BY l_returnflag, l_linestatus
      |ORDER BY l_returnflag, l_linestatus
      """.stripMargin
    
    val result = runQuery(sql)
    
    // 验证结果
    assert(result.count() > 0)
    
    // 验证列数
    assert(result.columns.length === 10)
    
    // 验证使用了Gluten加速
    val plan = result.queryExecution.executedPlan
    assert(plan.find(_.isInstanceOf[WholeStageTransformer]).isDefined,
      "Query should use Gluten transformer")
  }
  
  test("TPC-H Q3: Shipping Priority") {
    val sql = """
      |SELECT
      |  l_orderkey,
      |  SUM(l_extendedprice * (1 - l_discount)) AS revenue,
      |  o_orderdate,
      |  o_shippriority
      |FROM customer, orders, lineitem
      |WHERE c_mktsegment = 'BUILDING'
      |  AND c_custkey = o_custkey
      |  AND l_orderkey = o_orderkey
      |  AND o_orderdate < DATE '1995-03-15'
      |  AND l_shipdate > DATE '1995-03-15'
      |GROUP BY l_orderkey, o_orderdate, o_shippriority
      |ORDER BY revenue DESC, o_orderdate
      |LIMIT 10
      """.stripMargin
    
    val result = runQuery(sql)
    
    assert(result.count() === 10)
    
    // 验证JOIN使用了Gluten
    val plan = result.queryExecution.executedPlan
    assert(plan.find(_.isInstanceOf[HashJoinExecTransformer]).isDefined,
      "Query should use Gluten hash join")
  }
  
  test("TPC-H Q6: Forecasting Revenue Change") {
    val sql = """
      |SELECT SUM(l_extendedprice * l_discount) AS revenue
      |FROM lineitem
      |WHERE l_shipdate >= DATE '1994-01-01'
      |  AND l_shipdate < DATE '1995-01-01'
      |  AND l_discount BETWEEN 0.05 AND 0.07
      |  AND l_quantity < 24
      """.stripMargin
    
    val result = runQuery(sql)
    
    assert(result.count() === 1)
    
    // 验证Filter下推
    val plan = result.queryExecution.executedPlan
    assert(plan.find(_.isInstanceOf[FilterTransformer]).isDefined)
  }
}
```

### 18.3.3 TPC-DS测试套件

```scala
// gluten-ut/common/src/test/scala/org/apache/gluten/execution/VeloxTPCDSSuite.scala
package org.apache.gluten.execution

class VeloxTPCDSSuite extends GlutenTestBase {
  
  private val tpcdsDataPath = sys.env.getOrElse("TPCDS_DATA_PATH", "/data/tpcds/sf1")
  
  test("TPC-DS Q1: Store Sales Analysis") {
    val sql = """
      |WITH customer_total_return AS (
      |  SELECT sr_customer_sk AS ctr_customer_sk,
      |    sr_store_sk AS ctr_store_sk,
      |    SUM(sr_return_amt) AS ctr_total_return
      |  FROM store_returns, date_dim
      |  WHERE sr_returned_date_sk = d_date_sk
      |    AND d_year = 2000
      |  GROUP BY sr_customer_sk, sr_store_sk
      |)
      |SELECT c_customer_id
      |FROM customer_total_return ctr1, store, customer
      |WHERE ctr1.ctr_total_return > (
      |  SELECT AVG(ctr_total_return) * 1.2
      |  FROM customer_total_return ctr2
      |  WHERE ctr1.ctr_store_sk = ctr2.ctr_store_sk
      |)
      |AND s_store_sk = ctr1.ctr_store_sk
      |AND s_state = 'TN'
      |AND ctr1.ctr_customer_sk = c_customer_sk
      |ORDER BY c_customer_id
      |LIMIT 100
      """.stripMargin
    
    val result = runQuery(sql)
    
    // 验证查询执行成功
    assert(result.count() <= 100)
  }
}
```

### 18.3.4 自定义测试用例编写

```scala
// gluten-ut/common/src/test/scala/org/apache/gluten/execution/CustomQuerySuite.scala
package org.apache.gluten.execution

class CustomQuerySuite extends GlutenTestBase {
  
  test("complex aggregation with multiple group keys") {
    import spark.implicits._
    
    // 准备测试数据
    val df = Seq(
      ("A", "X", 10),
      ("A", "Y", 20),
      ("B", "X", 30),
      ("B", "Y", 40),
      ("A", "X", 15)
    ).toDF("category", "subcategory", "value")
    
    df.createOrReplaceTempView("test_data")
    
    // 执行聚合查询
    val result = spark.sql("""
      |SELECT category, subcategory,
      |  SUM(value) as total,
      |  AVG(value) as average,
      |  COUNT(*) as count
      |FROM test_data
      |GROUP BY category, subcategory
      |ORDER BY category, subcategory
      |""".stripMargin)
    
    // 验证结果
    val rows = result.collect()
    assert(rows.length === 4)
    
    // 验证特定结果
    val ax = rows.find(r => r.getString(0) == "A" && r.getString(1) == "X").get
    assert(ax.getLong(2) === 25)  // SUM
    assert(math.abs(ax.getDouble(3) - 12.5) < 0.01)  // AVG
    assert(ax.getLong(4) === 2)  // COUNT
  }
  
  test("window function with partitioning") {
    import spark.implicits._
    
    val df = Seq(
      ("dept1", "emp1", 5000),
      ("dept1", "emp2", 6000),
      ("dept2", "emp3", 5500),
      ("dept2", "emp4", 7000),
      ("dept1", "emp5", 5500)
    ).toDF("department", "employee", "salary")
    
    df.createOrReplaceTempView("employees")
    
    val result = spark.sql("""
      |SELECT department, employee, salary,
      |  RANK() OVER (PARTITION BY department ORDER BY salary DESC) as rank,
      |  AVG(salary) OVER (PARTITION BY department) as avg_salary
      |FROM employees
      |""".stripMargin)
    
    val rows = result.collect()
    assert(rows.length === 5)
    
    // 验证排名
    val dept1Emp2 = rows.find(r => r.getString(1) == "emp2").get
    assert(dept1Emp2.getInt(3) === 1)  // 最高工资排名第1
  }
}
```

## 18.4 微基准测试

### 18.4.1 JMH基准测试

```java
// gluten-core/src/jmh/java/org/apache/gluten/benchmarks/ExpressionBenchmark.java
package org.apache.gluten.benchmarks;

import org.openjdk.jmh.annotations.*;
import org.apache.spark.sql.catalyst.expressions.*;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 5, time = 10)
@Fork(1)
public class ExpressionBenchmark {
  
  private Expression addExpr;
  private InternalRow row;
  
  @Setup
  public void setup() {
    Literal left = Literal.create(100, DataTypes.IntegerType);
    Literal right = Literal.create(200, DataTypes.IntegerType);
    addExpr = new Add(left, right);
    
    row = InternalRow.empty();
  }
  
  @Benchmark
  public Object benchmarkAdd() {
    return addExpr.eval(row);
  }
  
  @Benchmark
  public Object benchmarkComplexExpression() {
    // ((a + b) * c) - d
    Expression expr = new Subtract(
        new Multiply(
            new Add(
                Literal.create(10, DataTypes.IntegerType),
                Literal.create(20, DataTypes.IntegerType)
            ),
            Literal.create(5, DataTypes.IntegerType)
        ),
        Literal.create(50, DataTypes.IntegerType)
    );
    
    return expr.eval(row);
  }
}
```

**运行JMH基准测试**

```bash
#!/bin/bash
# run-jmh.sh

mvn clean install -DskipTests

# 运行基准测试
java -jar gluten-core/target/benchmarks.jar \
  -rf json \
  -rff results.json \
  ExpressionBenchmark

# 查看结果
cat results.json | jq '.[] | {benchmark: .benchmark, score: .primaryMetric.score}'
```

### 18.4.2 Google Benchmark

```cpp
// cpp/velox/benchmarks/FilterBenchmark.cpp
#include <benchmark/benchmark.h>
#include "velox/exec/Driver.h"
#include "velox/vector/tests/VectorMaker.h"

namespace facebook::velox::exec::test {

class FilterBenchmark {
 public:
  FilterBenchmark() {
    // 创建测试数据
    vectorMaker_ = std::make_unique<test::VectorMaker>(pool_.get());
    
    inputData_ = vectorMaker_->rowVector({
      vectorMaker_->flatVector<int32_t>(100000, [](auto row) { return row % 1000; }),
      vectorMaker_->flatVector<double>(100000, [](auto row) { return row * 1.5; })
    });
  }
  
  std::unique_ptr<memory::MemoryPool> pool_ = memory::getDefaultMemoryPool();
  std::unique_ptr<test::VectorMaker> vectorMaker_;
  RowVectorPtr inputData_;
};

static void BM_SimpleFilter(benchmark::State& state) {
  FilterBenchmark setup;
  
  // 创建过滤表达式: col0 < 500
  auto predicate = makeExpression("col0 < 500", asRowType(setup.inputData_->type()));
  
  for (auto _ : state) {
    SelectivityVector rows(setup.inputData_->size());
    evaluate(*predicate, makeRowVector({setup.inputData_}), rows);
    benchmark::DoNotOptimize(rows);
  }
  
  state.SetItemsProcessed(state.iterations() * setup.inputData_->size());
}

static void BM_ComplexFilter(benchmark::State& state) {
  FilterBenchmark setup;
  
  // 复杂过滤: col0 < 500 AND col1 > 100
  auto predicate = makeExpression(
      "col0 < 500 AND col1 > 100.0",
      asRowType(setup.inputData_->type()));
  
  for (auto _ : state) {
    SelectivityVector rows(setup.inputData_->size());
    evaluate(*predicate, makeRowVector({setup.inputData_}), rows);
    benchmark::DoNotOptimize(rows);
  }
  
  state.SetItemsProcessed(state.iterations() * setup.inputData_->size());
}

BENCHMARK(BM_SimpleFilter)->Unit(benchmark::kMicrosecond);
BENCHMARK(BM_ComplexFilter)->Unit(benchmark::kMicrosecond);

} // namespace facebook::velox::exec::test

BENCHMARK_MAIN();
```

**编译运行**

```bash
#!/bin/bash
# run-cpp-benchmark.sh

mkdir -p build-benchmark
cd build-benchmark

cmake .. \
  -DCMAKE_BUILD_TYPE=Release \
  -DBUILD_BENCHMARKS=ON

make -j$(nproc) FilterBenchmark

# 运行基准测试
./FilterBenchmark \
  --benchmark_out=results.json \
  --benchmark_out_format=json

# 分析结果
python3 << 'PYTHON'
import json

with open('results.json') as f:
    data = json.load(f)

for benchmark in data['benchmarks']:
    name = benchmark['name']
    time = benchmark['real_time']
    items = benchmark.get('items_per_second', 0)
    
    print(f"{name}:")
    print(f"  Time: {time:.2f} us")
    print(f"  Throughput: {items/1e6:.2f} M items/sec")
PYTHON
```

### 18.4.3 性能回归检测

```python
# tools/detect_regression.py
import json
import sys

def load_results(filepath):
    with open(filepath) as f:
        return json.load(f)

def compare_results(baseline, current, threshold=0.05):
    """
    比较基准测试结果，检测性能回归
    threshold: 允许的性能下降百分比
    """
    regressions = []
    
    baseline_dict = {b['benchmark']: b for b in baseline}
    
    for curr in current:
        name = curr['benchmark']
        if name not in baseline_dict:
            continue
        
        base = baseline_dict[name]
        
        curr_score = curr['primaryMetric']['score']
        base_score = base['primaryMetric']['score']
        
        # 计算性能变化
        change = (curr_score - base_score) / base_score
        
        if change < -threshold:  # 性能下降
            regressions.append({
                'benchmark': name,
                'baseline': base_score,
                'current': curr_score,
                'change': change * 100
            })
    
    return regressions

def main():
    if len(sys.argv) != 3:
        print("Usage: detect_regression.py <baseline.json> <current.json>")
        sys.exit(1)
    
    baseline = load_results(sys.argv[1])
    current = load_results(sys.argv[2])
    
    regressions = compare_results(baseline, current)
    
    if regressions:
        print("⚠️  Performance regressions detected:")
        for reg in regressions:
            print(f"\n{reg['benchmark']}:")
            print(f"  Baseline: {reg['baseline']:.2f}")
            print(f"  Current:  {reg['current']:.2f}")
            print(f"  Change:   {reg['change']:.1f}%")
        sys.exit(1)
    else:
        print("✓ No performance regressions detected")
        sys.exit(0)

if __name__ == '__main__':
    main()
```

## 18.5 CI/CD流程

### 18.5.1 GitHub Actions workflow配置

```yaml
# .github/workflows/ci.yml
name: Gluten CI

on:
  push:
    branches: [ main, master ]
  pull_request:
    branches: [ main, master ]

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        spark-version: ['3.2.4', '3.3.2', '3.4.1']
        backend: ['velox', 'clickhouse']
    
    steps:
    - uses: actions/checkout@v3
      with:
        submodules: recursive
    
    - name: Set up JDK 8
      uses: actions/setup-java@v3
      with:
        java-version: '8'
        distribution: 'temurin'
    
    - name: Cache Maven packages
      uses: actions/cache@v3
      with:
        path: ~/.m2
        key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
        restore-keys: ${{ runner.os }}-m2
    
    - name: Build Velox
      if: matrix.backend == 'velox'
      run: |
        cd ep/build-velox/src
        ./get_velox.sh
        ./build_velox.sh --enable_ep_cache
    
    - name: Build Gluten
      run: |
        mvn clean install \
          -Pspark-${{ matrix.spark-version }} \
          -Pbackends-${{ matrix.backend }} \
          -DskipTests
    
    - name: Run Unit Tests
      run: |
        mvn test \
          -Pspark-${{ matrix.spark-version }} \
          -Pbackends-${{ matrix.backend }}
    
    - name: Run Integration Tests
      run: |
        cd gluten-ut
        mvn test \
          -Pspark-${{ matrix.spark-version }} \
          -Pbackends-${{ matrix.backend }}
    
    - name: Generate Coverage Report
      run: mvn jacoco:report
    
    - name: Upload Coverage to Codecov
      uses: codecov/codecov-action@v3
      with:
        files: ./target/site/jacoco/jacoco.xml
        flags: unittests
        name: codecov-${{ matrix.spark-version }}-${{ matrix.backend }}
    
    - name: Archive Test Results
      if: always()
      uses: actions/upload-artifact@v3
      with:
        name: test-results-${{ matrix.spark-version }}-${{ matrix.backend }}
        path: |
          **/target/surefire-reports/
          **/target/failsafe-reports/

  performance-test:
    runs-on: ubuntu-latest
    needs: build-and-test
    if: github.event_name == 'pull_request'
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Run Benchmarks
      run: |
        mvn clean install -DskipTests
        java -jar gluten-core/target/benchmarks.jar \
          -rf json \
          -rff current-results.json
    
    - name: Download Baseline
      run: |
        wget https://example.com/baseline-results.json
    
    - name: Detect Regression
      run: |
        python3 tools/detect_regression.py \
          baseline-results.json \
          current-results.json
```

### 18.5.2 质量门禁配置

```yaml
# .github/workflows/quality-gate.yml
name: Quality Gate

on:
  pull_request:
    branches: [ main, master ]

jobs:
  quality-checks:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Check Code Style
      run: mvn spotless:check
    
    - name: Run Static Analysis
      run: mvn compile spotbugs:check
    
    - name: Check Test Coverage
      run: |
        mvn clean test jacoco:report jacoco:check
    
    - name: Check License Headers
      run: mvn apache-rat:check
    
    - name: Verify Dependencies
      run: mvn dependency:analyze-only
```

## 18.6 质量保证最佳实践

### 18.6.1 测试金字塔实践

| 实践项 | 要求 | 说明 |
|--------|------|------|
| 单元测试占比 | 70%+ | 快速反馈，隔离问题 |
| 集成测试占比 | 20%+ | 验证模块协作 |
| 端到端测试占比 | 10%- | 验证整体功能 |
| 代码覆盖率 | 70%+ | 确保关键路径覆盖 |
| 测试执行时间 | <15分钟 | 快速CI反馈 |

### 18.6.2 测试编写原则

1. **FIRST原则**
   - Fast：测试应该快速执行
   - Independent：测试之间相互独立
   - Repeatable：可重复执行
   - Self-Validating：自动验证结果
   - Timely：及时编写测试

2. **测试命名**
   ```scala
   test("filterExec should transform to FilterTransformer when condition is supported")
   test("projectExec with UDF should fallback to vanilla Spark")
   ```

3. **测试组织**
   - 一个测试类专注一个功能单元
   - 使用describe/context组织相关测试
   - Setup和Teardown正确管理资源

## 18.7 小结

本章全面介绍了Apache Gluten的测试与质量保证体系：

1. **测试框架**：ScalaTest、Google Test、JMH等工具链
2. **单元测试**：Scala和C++单元测试最佳实践
3. **集成测试**：TPC-H、TPC-DS等标准测试套件
4. **性能测试**：微基准测试和性能回归检测
5. **CI/CD**：自动化测试和质量门禁
6. **最佳实践**：测试金字塔和编写原则

完善的测试体系是保证Gluten代码质量的基石。下一章将介绍性能分析与调优技术。
