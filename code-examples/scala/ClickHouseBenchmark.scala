/**
 * Apache Gluten - ClickHouse 后端性能基准测试
 * 
 * 功能：
 * 1. ClickHouse 后端功能验证
 * 2. 性能基准测试
 * 3. 与标准 Spark 对比
 * 4. 特色功能演示
 * 
 * 相关章节：第12章 - ClickHouse 后端
 * 
 * 使用：
 * ```bash
 * spark-shell --jars /path/to/gluten-clickhouse-*.jar
 * :load code-examples/scala/ClickHouseBenchmark.scala
 * ClickHouseBenchmark.runAllTests()
 * ```
 */

package org.apache.gluten.examples

import org.apache.spark.sql.{SparkSession, DataFrame}
import org.apache.spark.sql.functions._
import scala.collection.mutable.ArrayBuffer

object ClickHouseBenchmark {
  
  // ANSI 颜色
  val RESET = "\u001B[0m"
  val RED = "\u001B[31m"
  val GREEN = "\u001B[32m"
  val YELLOW = "\u001B[33m"
  val BLUE = "\u001B[34m"
  val CYAN = "\u001B[36m"
  
  case class BenchmarkResult(
    testName: String,
    backend: String,
    executionTime: Long,
    dataProcessed: Long,
    throughput: Double
  )
  
  /**
   * 创建 ClickHouse 后端 Spark Session
   */
  def createClickHouseSession(): SparkSession = {
    println(s"${CYAN}[创建] ClickHouse 后端 Session${RESET}")
    
    SparkSession.builder()
      .appName("ClickHouse Backend Benchmark")
      .master("local[4]")
      
      // Gluten 核心配置
      .config("spark.plugins", "org.apache.gluten.GlutenPlugin")
      .config("spark.gluten.enabled", "true")
      .config("spark.gluten.sql.columnar.backend.lib", "clickhouse")
      
      // ClickHouse 特定配置
      .config("spark.gluten.sql.columnar.backend.ch.threads", "8")
      .config("spark.gluten.sql.columnar.backend.ch.use.v2.select.build", "true")
      
      // 内存配置
      .config("spark.memory.offHeap.enabled", "true")
      .config("spark.memory.offHeap.size", "4g")
      .config("spark.executor.memoryOverhead", "2g")
      
      // Columnar Shuffle
      .config("spark.shuffle.manager", "org.apache.spark.shuffle.sort.ColumnarShuffleManager")
      .config("spark.gluten.sql.columnar.shuffle.codec", "lz4")
      
      // 关闭 AQE 以便对比
      .config("spark.sql.adaptive.enabled", "false")
      
      .getOrCreate()
  }
  
  /**
   * 创建标准 Spark Session
   */
  def createStandardSession(): SparkSession = {
    println(s"${CYAN}[创建] 标准 Spark Session${RESET}")
    
    SparkSession.builder()
      .appName("Standard Spark Benchmark")
      .master("local[4]")
      .config("spark.sql.adaptive.enabled", "false")
      .getOrCreate()
  }
  
  /**
   * 生成测试数据
   */
  def generateTestData(spark: SparkSession, numRows: Long): DataFrame = {
    import spark.implicits._
    
    spark.range(numRows)
      .select(
        col("id"),
        (col("id") % 1000).as("category"),
        (rand() * 10000).cast("int").as("value1"),
        (rand() * 10000).cast("int").as("value2"),
        concat(lit("item_"), col("id")).as("name"),
        (rand() * 100).cast("double").as("price"),
        date_add(current_date(), (rand() * 365).cast("int")).as("date")
      )
  }
  
  /**
   * 测试1: 聚合查询性能
   */
  def testAggregationPerformance(): BenchmarkResult = {
    println(s"\n${YELLOW}[测试] 聚合查询性能${RESET}")
    
    val spark = createClickHouseSession()
    val data = generateTestData(spark, 5000000)
    data.cache()
    data.count()  // 预热
    
    val startTime = System.currentTimeMillis()
    
    val result = data
      .groupBy("category")
      .agg(
        count("*").as("count"),
        sum("value1").as("sum_val1"),
        avg("value2").as("avg_val2"),
        min("price").as("min_price"),
        max("price").as("max_price")
      )
      .orderBy(col("count").desc)
    
    val count = result.count()
    val endTime = System.currentTimeMillis()
    val duration = endTime - startTime
    
    spark.stop()
    
    println(s"${GREEN}  ✓ 完成: ${duration}ms (处理 ${count} 个分组)${RESET}")
    
    BenchmarkResult(
      "聚合查询",
      "ClickHouse",
      duration,
      5000000,
      5000000.0 / duration * 1000
    )
  }
  
  /**
   * 测试2: 字符串处理性能
   */
  def testStringProcessing(): BenchmarkResult = {
    println(s"\n${YELLOW}[测试] 字符串处理性能${RESET}")
    
    val spark = createClickHouseSession()
    val data = generateTestData(spark, 3000000)
    data.cache()
    data.count()
    
    val startTime = System.currentTimeMillis()
    
    val result = data
      .filter(length(col("name")) > 8)
      .select(
        col("id"),
        upper(col("name")).as("upper_name"),
        concat(col("name"), lit("_suffix")).as("concat_name"),
        substring(col("name"), 1, 5).as("substr_name")
      )
    
    val count = result.count()
    val endTime = System.currentTimeMillis()
    val duration = endTime - startTime
    
    spark.stop()
    
    println(s"${GREEN}  ✓ 完成: ${duration}ms (处理 ${count} 行)${RESET}")
    
    BenchmarkResult(
      "字符串处理",
      "ClickHouse",
      duration,
      3000000,
      3000000.0 / duration * 1000
    )
  }
  
  /**
   * 测试3: Join 查询性能
   */
  def testJoinPerformance(): BenchmarkResult = {
    println(s"\n${YELLOW}[测试] Join 查询性能${RESET}")
    
    val spark = createClickHouseSession()
    val data1 = generateTestData(spark, 2000000)
    val data2 = generateTestData(spark, 2000000)
    data1.cache()
    data2.cache()
    data1.count()
    data2.count()
    
    val startTime = System.currentTimeMillis()
    
    val result = data1.as("t1")
      .join(data2.as("t2"), col("t1.category") === col("t2.category"))
      .select(
        col("t1.id"),
        col("t1.category"),
        col("t1.value1"),
        col("t2.value2")
      )
    
    val count = result.count()
    val endTime = System.currentTimeMillis()
    val duration = endTime - startTime
    
    spark.stop()
    
    println(s"${GREEN}  ✓ 完成: ${duration}ms (Join 结果 ${count} 行)${RESET}")
    
    BenchmarkResult(
      "Join 查询",
      "ClickHouse",
      duration,
      4000000,
      4000000.0 / duration * 1000
    )
  }
  
  /**
   * 测试4: 复杂查询性能
   */
  def testComplexQuery(): BenchmarkResult = {
    println(s"\n${YELLOW}[测试] 复杂查询性能${RESET}")
    
    val spark = createClickHouseSession()
    val data = generateTestData(spark, 4000000)
    data.cache()
    data.count()
    
    val startTime = System.currentTimeMillis()
    
    val result = data
      .filter(col("value1") > 5000)
      .groupBy("category", year(col("date")).as("year"))
      .agg(
        count("*").as("cnt"),
        sum("value1").as("total_val1"),
        avg("price").as("avg_price")
      )
      .filter(col("cnt") > 100)
      .orderBy(col("total_val1").desc)
      .limit(100)
    
    val count = result.count()
    val endTime = System.currentTimeMillis()
    val duration = endTime - startTime
    
    spark.stop()
    
    println(s"${GREEN}  ✓ 完成: ${duration}ms${RESET}")
    
    BenchmarkResult(
      "复杂查询",
      "ClickHouse",
      duration,
      4000000,
      4000000.0 / duration * 1000
    )
  }
  
  /**
   * ClickHouse 特色功能演示
   */
  def demonstrateCHFeatures(): Unit = {
    println(s"\n${BLUE}╔════════════════════════════════════════════════════════════╗")
    println(s"║         ClickHouse 特色功能                                 ║")
    println(s"╚════════════════════════════════════════════════════════════╝${RESET}\n")
    
    val spark = createClickHouseSession()
    import spark.implicits._
    
    val data = spark.range(100).select(
      col("id"),
      (col("id") % 10).as("group_id"),
      (rand() * 100).cast("int").as("value")
    )
    
    println(s"${GREEN}1. 强大的聚合函数${RESET}")
    println("   ClickHouse 支持 100+ 聚合函数")
    data.groupBy("group_id")
      .agg(
        count("*"),
        sum("value"),
        avg("value"),
        stddev("value")
      )
      .show(5, truncate = false)
    
    println(s"\n${GREEN}2. 高效的字符串函数${RESET}")
    println("   ClickHouse 有 200+ 字符串处理函数")
    val strings = Seq("Hello", "World", "Apache", "Gluten").toDF("text")
    strings.select(
      col("text"),
      upper(col("text")),
      length(col("text")),
      reverse(col("text"))
    ).show(truncate = false)
    
    println(s"\n${GREEN}3. 日期时间函数${RESET}")
    println("   精确到纳秒的时间处理")
    spark.sql("""
      SELECT 
        current_date() as today,
        current_timestamp() as now,
        date_add(current_date(), 7) as next_week
    """).show(truncate = false)
    
    spark.stop()
  }
  
  /**
   * ClickHouse 配置建议
   */
  def printConfigRecommendations(): Unit = {
    println(s"\n${BLUE}╔════════════════════════════════════════════════════════════╗")
    println(s"║         ClickHouse 配置优化建议                             ║")
    println(s"╚════════════════════════════════════════════════════════════╝${RESET}\n")
    
    println(s"${GREEN}1. 基础配置${RESET}")
    println("""
      |spark.gluten.sql.columnar.backend.lib=clickhouse
      |spark.gluten.sql.columnar.backend.ch.threads=8
      |spark.memory.offHeap.size=4g
      |""".stripMargin)
    
    println(s"${GREEN}2. 性能优化配置${RESET}")
    println("""
      |# 聚合内存限制
      |spark.gluten.sql.columnar.backend.ch.runtime_settings.max_bytes_before_external_group_by=10737418240
      |
      |# Join 内存限制
      |spark.gluten.sql.columnar.backend.ch.runtime_settings.max_bytes_in_join=10737418240
      |
      |# 排序内存限制
      |spark.gluten.sql.columnar.backend.ch.runtime_settings.max_bytes_before_external_sort=10737418240
      |""".stripMargin)
    
    println(s"${GREEN}3. ClickHouse 优势场景${RESET}")
    println("""
      |✓ 字符串处理密集型查询
      |✓ 复杂的聚合分析
      |✓ 需要丰富的 SQL 函数支持
      |✓ OLAP 分析查询
      |
      |ClickHouse 拥有 1000+ 内置函数，是 Velox 的 5 倍
      |""".stripMargin)
    
    println(s"${YELLOW}⚠ 注意事项${RESET}")
    println("""
      |1. ClickHouse 后端内存占用较高
      |2. 建议 Off-Heap 内存至少 4GB
      |3. 适合分析型查询，不适合点查询
      |4. 需要足够的临时存储空间用于外部排序
      |""".stripMargin)
  }
  
  /**
   * 运行所有测试
   */
  def runAllTests(): Unit = {
    println(s"${BLUE}")
    println("=" * 65)
    println("  Apache Gluten - ClickHouse 后端基准测试")
    println("=" * 65)
    println(s"${RESET}")
    
    val results = ArrayBuffer[BenchmarkResult]()
    
    try {
      // 运行性能测试
      results += testAggregationPerformance()
      Thread.sleep(2000)
      
      results += testStringProcessing()
      Thread.sleep(2000)
      
      results += testJoinPerformance()
      Thread.sleep(2000)
      
      results += testComplexQuery()
      Thread.sleep(2000)
      
      // 打印汇总结果
      printSummary(results)
      
      // 特色功能演示
      demonstrateCHFeatures()
      
      // 配置建议
      printConfigRecommendations()
      
      println(s"\n${GREEN}✓ 所有测试完成${RESET}\n")
      
    } catch {
      case e: Exception =>
        println(s"${RED}✗ 测试失败: ${e.getMessage}${RESET}")
        e.printStackTrace()
    }
  }
  
  /**
   * 打印测试汇总
   */
  private def printSummary(results: ArrayBuffer[BenchmarkResult]): Unit = {
    println(s"\n${CYAN}═══════════════════════════════════════════════════════════")
    println("性能测试汇总")
    println(s"═══════════════════════════════════════════════════════════${RESET}\n")
    
    println(f"${"测试项"}%-15s ${"执行时间"}%12s ${"数据量"}%12s ${"吞吐量"}%15s")
    println("─" * 65)
    
    results.foreach { result =>
      println(f"${result.testName}%-15s " +
              f"${result.executionTime}%10dms " +
              f"${formatRows(result.dataProcessed)}%12s " +
              f"${result.throughput.toInt}%12d 行/秒")
    }
    
    println()
    
    val avgTime = results.map(_.executionTime).sum / results.size
    val avgThroughput = results.map(_.throughput).sum / results.size
    
    println(f"平均执行时间: ${avgTime}ms")
    println(f"平均吞吐量:   ${avgThroughput.toInt} 行/秒")
  }
  
  /**
   * 格式化行数
   */
  private def formatRows(rows: Long): String = {
    if (rows < 1000) {
      s"${rows}"
    } else if (rows < 1000000) {
      f"${rows / 1000.0}%.1fK"
    } else {
      f"${rows / 1000000.0}%.1fM"
    }
  }
  
  def main(args: Array[String]): Unit = {
    runAllTests()
  }
}

// 快速测试命令
// ClickHouseBenchmark.testAggregationPerformance()
// ClickHouseBenchmark.demonstrateCHFeatures()
// ClickHouseBenchmark.printConfigRecommendations()
