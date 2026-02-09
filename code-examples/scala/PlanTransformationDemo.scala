/**
 * PlanTransformationDemo.scala
 * 
 * 演示 Gluten 查询计划转换过程
 * 对应书籍第5章：查询计划转换
 * 
 * 功能：
 * 1. 查看 Spark Physical Plan
 * 2. 查看 Gluten 转换后的 Plan
 * 3. 对比 Vanilla Spark vs Gluten Plan
 * 4. 展示 Substrait Plan 信息
 */

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import org.apache.spark.sql.execution.SparkPlan
import org.apache.spark.sql.execution.adaptive.AdaptiveSparkPlanExec

object PlanTransformationDemo {
  
  def main(args: Array[String]): Unit = {
    // 创建两个 SparkSession：一个使用 Gluten，一个不使用
    val glutenSpark = createGlutenSpark()
    val vanillaSpark = createVanillaSpark()
    
    println("=" * 80)
    println("Gluten 查询计划转换演示")
    println("=" * 80)
    
    // 示例1：简单查询
    println("\n>>> 示例1：简单 Filter + Project 查询")
    simpleQuery(glutenSpark, vanillaSpark)
    
    // 示例2：Join 查询
    println("\n>>> 示例2：Join 查询")
    joinQuery(glutenSpark, vanillaSpark)
    
    // 示例3：Aggregate 查询
    println("\n>>> 示例3：Aggregate 查询")
    aggregateQuery(glutenSpark, vanillaSpark)
    
    // 示例4：复杂查询
    println("\n>>> 示例4：复杂查询（Join + Aggregate + Window）")
    complexQuery(glutenSpark, vanillaSpark)
    
    glutenSpark.stop()
    vanillaSpark.stop()
  }
  
  /**
   * 创建启用 Gluten 的 SparkSession
   */
  def createGlutenSpark(): SparkSession = {
    SparkSession.builder()
      .appName("Gluten Plan Demo")
      .master("local[*]")
      .config("spark.plugins", "org.apache.gluten.GlutenPlugin")
      .config("spark.gluten.enabled", "true")
      .config("spark.gluten.sql.columnar.backend.lib", "velox")
      .config("spark.memory.offHeap.enabled", "true")
      .config("spark.memory.offHeap.size", "2g")
      .config("spark.shuffle.manager", "org.apache.spark.shuffle.sort.ColumnarShuffleManager")
      .config("spark.sql.adaptive.enabled", "true")
      .getOrCreate()
  }
  
  /**
   * 创建标准 Spark（不使用 Gluten）
   */
  def createVanillaSpark(): SparkSession = {
    SparkSession.builder()
      .appName("Vanilla Spark Plan Demo")
      .master("local[*]")
      .config("spark.sql.adaptive.enabled", "true")
      .getOrCreate()
  }
  
  /**
   * 示例1：简单 Filter + Project 查询
   */
  def simpleQuery(glutenSpark: SparkSession, vanillaSpark: SparkSession): Unit = {
    // 创建测试数据
    import glutenSpark.implicits._
    val data = (1 to 1000).map(i => (i, s"name_$i", i * 10, i % 10))
    val df = glutenSpark.createDataFrame(data).toDF("id", "name", "value", "category")
    df.createOrReplaceTempView("test_table")
    
    // SQL 查询
    val sql = """
      SELECT id, name, value * 2 as doubled_value
      FROM test_table
      WHERE category = 5 AND value > 100
      ORDER BY value DESC
      LIMIT 10
    """
    
    // Gluten 执行计划
    println("\n【Gluten Physical Plan】")
    val glutenResult = glutenSpark.sql(sql)
    glutenResult.explain("extended")
    
    // 提取并分析计划
    analyzePlan(glutenResult.queryExecution.executedPlan, "Gluten")
    
    // Vanilla Spark 执行计划
    println("\n【Vanilla Spark Physical Plan】")
    import vanillaSpark.implicits._
    val vanillaData = (1 to 1000).map(i => (i, s"name_$i", i * 10, i % 10))
    val vanillaDf = vanillaSpark.createDataFrame(vanillaData).toDF("id", "name", "value", "category")
    vanillaDf.createOrReplaceTempView("test_table")
    val vanillaResult = vanillaSpark.sql(sql)
    vanillaResult.explain("extended")
    
    analyzePlan(vanillaResult.queryExecution.executedPlan, "Vanilla")
    
    // 比较结果
    compareResults(glutenResult, vanillaResult)
  }
  
  /**
   * 示例2：Join 查询
   */
  def joinQuery(glutenSpark: SparkSession, vanillaSpark: SparkSession): Unit = {
    import glutenSpark.implicits._
    
    // 创建两个表
    val orders = (1 to 1000).map(i => (i, i % 100, i * 100))
      .toDF("order_id", "customer_id", "amount")
    val customers = (1 to 100).map(i => (i, s"customer_$i", s"city_${i % 10}"))
      .toDF("customer_id", "name", "city")
    
    orders.createOrReplaceTempView("orders")
    customers.createOrReplaceTempView("customers")
    
    val sql = """
      SELECT c.name, c.city, SUM(o.amount) as total_amount
      FROM orders o
      JOIN customers c ON o.customer_id = c.customer_id
      WHERE c.city IN ('city_1', 'city_2')
      GROUP BY c.name, c.city
      HAVING SUM(o.amount) > 5000
    """
    
    println("\n【Gluten Join Plan】")
    val result = glutenSpark.sql(sql)
    result.explain("extended")
    
    analyzePlan(result.queryExecution.executedPlan, "Gluten Join")
  }
  
  /**
   * 示例3：Aggregate 查询
   */
  def aggregateQuery(glutenSpark: SparkSession, vanillaSpark: SparkSession): Unit = {
    import glutenSpark.implicits._
    
    val sales = (1 to 10000).map(i => (
      s"product_${i % 100}",
      s"region_${i % 10}",
      i % 1000,
      i.toDouble
    )).toDF("product", "region", "quantity", "price")
    
    sales.createOrReplaceTempView("sales")
    
    val sql = """
      SELECT 
        region,
        product,
        SUM(quantity) as total_quantity,
        AVG(price) as avg_price,
        MAX(price) as max_price,
        COUNT(*) as count
      FROM sales
      WHERE quantity > 100
      GROUP BY region, product
      HAVING SUM(quantity) > 1000
      ORDER BY total_quantity DESC
    """
    
    println("\n【Gluten Aggregate Plan】")
    val result = glutenSpark.sql(sql)
    result.explain("extended")
    
    analyzePlan(result.queryExecution.executedPlan, "Gluten Aggregate")
  }
  
  /**
   * 示例4：复杂查询
   */
  def complexQuery(glutenSpark: SparkSession, vanillaSpark: SparkSession): Unit = {
    import glutenSpark.implicits._
    import org.apache.spark.sql.expressions.Window
    
    val data = (1 to 5000).map(i => (
      i,
      s"dept_${i % 10}",
      i * 1000 + scala.util.Random.nextInt(1000),
      s"2024-${(i % 12) + 1}-01"
    )).toDF("employee_id", "department", "salary", "hire_date")
    
    data.createOrReplaceTempView("employees")
    
    val sql = """
      WITH dept_stats AS (
        SELECT 
          department,
          AVG(salary) as avg_salary,
          MAX(salary) as max_salary,
          COUNT(*) as emp_count
        FROM employees
        GROUP BY department
      ),
      ranked_employees AS (
        SELECT 
          e.employee_id,
          e.department,
          e.salary,
          ROW_NUMBER() OVER (PARTITION BY e.department ORDER BY e.salary DESC) as rank
        FROM employees e
      )
      SELECT 
        r.department,
        r.employee_id,
        r.salary,
        r.rank,
        d.avg_salary,
        d.max_salary,
        (r.salary - d.avg_salary) as salary_diff
      FROM ranked_employees r
      JOIN dept_stats d ON r.department = d.department
      WHERE r.rank <= 3
      ORDER BY r.department, r.rank
    """
    
    println("\n【Gluten Complex Query Plan】")
    val result = glutenSpark.sql(sql)
    result.explain("extended")
    
    analyzePlan(result.queryExecution.executedPlan, "Gluten Complex")
    
    // 显示部分结果
    println("\n【查询结果（前10行）】")
    result.show(10, truncate = false)
  }
  
  /**
   * 分析执行计划
   */
  def analyzePlan(plan: SparkPlan, label: String): Unit = {
    println(s"\n【$label 执行计划分析】")
    
    // 统计算子类型
    val operators = collectOperators(plan)
    println(s"算子总数: ${operators.size}")
    
    // 按类型分组
    val operatorCounts = operators.groupBy(_.getClass.getSimpleName)
      .mapValues(_.size)
      .toSeq
      .sortBy(-_._2)
    
    println("\n算子分布:")
    operatorCounts.foreach { case (opType, count) =>
      println(f"  - $opType%-40s : $count")
    }
    
    // 检测 Gluten 特定算子
    val glutenOperators = operators.filter(_.getClass.getName.contains("gluten"))
    if (glutenOperators.nonEmpty) {
      println(s"\nGluten 算子数量: ${glutenOperators.size}")
      println("Gluten 算子列表:")
      glutenOperators.map(_.getClass.getSimpleName).distinct.foreach { op =>
        println(s"  - $op")
      }
    }
    
    // 检测 Fallback（C2R/R2C）
    val c2rCount = operators.count(_.getClass.getSimpleName.contains("ColumnarToRow"))
    val r2cCount = operators.count(_.getClass.getSimpleName.contains("RowToColumnar"))
    
    if (c2rCount > 0 || r2cCount > 0) {
      println(s"\n⚠️  检测到 Fallback:")
      println(s"  - ColumnarToRow (C2R): $c2rCount")
      println(s"  - RowToColumnar (R2C): $r2cCount")
    } else {
      println("\n✅ 无 Fallback，完全使用 Native 执行")
    }
  }
  
  /**
   * 递归收集所有算子
   */
  def collectOperators(plan: SparkPlan): Seq[SparkPlan] = {
    plan +: plan.children.flatMap(collectOperators)
  }
  
  /**
   * 比较两个查询的结果
   */
  def compareResults(df1: org.apache.spark.sql.DataFrame, 
                     df2: org.apache.spark.sql.DataFrame): Unit = {
    println("\n【结果验证】")
    
    val count1 = df1.count()
    val count2 = df2.count()
    
    println(s"Gluten 结果行数: $count1")
    println(s"Vanilla 结果行数: $count2")
    
    if (count1 == count2) {
      println("✅ 行数一致")
    } else {
      println("❌ 行数不一致")
    }
  }
}

/**
 * 使用说明：
 * 
 * 1. 编译：
 *    scalac -classpath "$SPARK_HOME/jars/*:$GLUTEN_JAR" PlanTransformationDemo.scala
 * 
 * 2. 打包：
 *    jar cvf plan-demo.jar PlanTransformationDemo*.class
 * 
 * 3. 运行：
 *    spark-submit \
 *      --class PlanTransformationDemo \
 *      --master local[*] \
 *      --driver-memory 4g \
 *      --conf spark.driver.extraClassPath=$GLUTEN_JAR \
 *      --conf spark.executor.extraClassPath=$GLUTEN_JAR \
 *      plan-demo.jar
 * 
 * 4. 查看输出：
 *    - 会显示4个示例的执行计划
 *    - 对比 Gluten vs Vanilla Spark
 *    - 分析算子分布和 Fallback 情况
 * 
 * 预期输出：
 *    - Gluten 应该包含 TransformXXXExec 等 Gluten 特定算子
 *    - 理想情况下应该没有或很少 C2R/R2C
 *    - Gluten Plan 通常更简洁（算子融合）
 */
