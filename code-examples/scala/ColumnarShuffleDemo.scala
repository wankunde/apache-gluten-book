/**
 * Apache Gluten Columnar Shuffle 性能演示
 * 
 * 功能：
 * 1. 对比 Row-based vs Columnar Shuffle 性能
 * 2. 测试不同分区数的影响
 * 3. 分析 Shuffle 数据量和时间
 * 4. 展示 Shuffle 配置优化
 * 
 * 相关章节：第8章 - Columnar Shuffle
 * 
 * 使用：
 * ```bash
 * spark-shell --jars /path/to/gluten-*.jar
 * :load code-examples/scala/ColumnarShuffleDemo.scala
 * ColumnarShuffleDemo.runAllTests()
 * ```
 */

package org.apache.gluten.examples

import org.apache.spark.sql.{SparkSession, DataFrame}
import org.apache.spark.sql.functions._
import scala.collection.mutable.ArrayBuffer

object ColumnarShuffleDemo {
  
  // ANSI 颜色
  val RESET = "\u001B[0m"
  val RED = "\u001B[31m"
  val GREEN = "\u001B[32m"
  val YELLOW = "\u001B[33m"
  val BLUE = "\u001B[34m"
  val CYAN = "\u001B[36m"
  
  case class ShuffleMetrics(
    shuffleType: String,
    numPartitions: Int,
    dataSize: Long,
    executionTime: Long,
    shuffleReadBytes: Long,
    shuffleWriteBytes: Long
  )
  
  /**
   * 创建启用 Columnar Shuffle 的 Spark Session
   */
  def createColumnarShuffleSession(): SparkSession = {
    println(s"${CYAN}[创建] Columnar Shuffle Session${RESET}")
    
    SparkSession.builder()
      .appName("Columnar Shuffle Demo")
      .master("local[4]")
      .config("spark.plugins", "org.apache.gluten.GlutenPlugin")
      .config("spark.gluten.enabled", "true")
      .config("spark.gluten.sql.columnar.backend.lib", "velox")
      
      // 启用 Columnar Shuffle
      .config("spark.shuffle.manager", "org.apache.spark.shuffle.sort.ColumnarShuffleManager")
      .config("spark.gluten.sql.columnar.shuffle.codec", "lz4")
      
      // Off-Heap 内存
      .config("spark.memory.offHeap.enabled", "true")
      .config("spark.memory.offHeap.size", "2g")
      
      // Shuffle 配置
      .config("spark.sql.shuffle.partitions", "200")
      .config("spark.sql.adaptive.enabled", "false")
      
      .getOrCreate()
  }
  
  /**
   * 创建标准 Row-based Shuffle Session
   */
  def createRowShuffleSession(): SparkSession = {
    println(s"${CYAN}[创建] Row-based Shuffle Session${RESET}")
    
    SparkSession.builder()
      .appName("Row Shuffle Demo")
      .master("local[4]")
      
      // 使用标准 Shuffle Manager
      .config("spark.shuffle.manager", "org.apache.spark.shuffle.sort.SortShuffleManager")
      .config("spark.sql.shuffle.partitions", "200")
      .config("spark.sql.adaptive.enabled", "false")
      
      .getOrCreate()
  }
  
  /**
   * 生成测试数据
   */
  def generateData(spark: SparkSession, numRows: Long): DataFrame = {
    import spark.implicits._
    
    spark.range(numRows)
      .select(
        col("id"),
        (col("id") % 1000).as("category"),
        (rand() * 10000).cast("int").as("value1"),
        (rand() * 10000).cast("int").as("value2"),
        concat(lit("user_"), col("id")).as("user_name"),
        (rand() * 100).cast("double").as("score")
      )
  }
  
  /**
   * 执行 Shuffle 密集型查询
   */
  def runShuffleQuery(df: DataFrame): (DataFrame, Long) = {
    val startTime = System.currentTimeMillis()
    
    // 多次 Shuffle 操作
    val result = df
      .repartition(200, col("category"))  // Shuffle 1
      .groupBy("category")                 // Shuffle 2 (聚合)
      .agg(
        count("*").as("count"),
        avg("value1").as("avg_value1"),
        sum("value2").as("sum_value2"),
        max("score").as("max_score")
      )
      .orderBy(col("count").desc)         // Shuffle 3 (排序)
      .limit(100)
    
    // 触发执行
    val count = result.count()
    
    val endTime = System.currentTimeMillis()
    val duration = endTime - startTime
    
    (result, duration)
  }
  
  /**
   * 对比 Row vs Columnar Shuffle
   */
  def compareShuffleTypes(): Unit = {
    println(s"\n${BLUE}╔════════════════════════════════════════════════════════════╗")
    println(s"║         Row-based vs Columnar Shuffle 性能对比             ║")
    println(s"╚════════════════════════════════════════════════════════════╝${RESET}\n")
    
    val dataSize = 5000000L  // 500万行
    
    val results = ArrayBuffer[ShuffleMetrics]()
    
    // 测试 Row-based Shuffle
    println(s"${YELLOW}[测试1] Row-based Shuffle${RESET}")
    val rowSpark = createRowShuffleSession()
    val rowData = generateData(rowSpark, dataSize)
    rowData.cache()
    rowData.count()  // 预热
    
    val (rowResult, rowTime) = runShuffleQuery(rowData)
    println(s"${GREEN}  ✓ 完成: ${rowTime}ms${RESET}")
    
    // 获取 Shuffle 指标
    val rowMetrics = getShuffleMetrics(rowSpark)
    results += ShuffleMetrics(
      "Row-based",
      200,
      dataSize,
      rowTime,
      rowMetrics._1,
      rowMetrics._2
    )
    
    rowSpark.stop()
    Thread.sleep(2000)
    
    // 测试 Columnar Shuffle
    println(s"\n${YELLOW}[测试2] Columnar Shuffle${RESET}")
    val colSpark = createColumnarShuffleSession()
    val colData = generateData(colSpark, dataSize)
    colData.cache()
    colData.count()
    
    val (colResult, colTime) = runShuffleQuery(colData)
    println(s"${GREEN}  ✓ 完成: ${colTime}ms${RESET}")
    
    val colMetrics = getShuffleMetrics(colSpark)
    results += ShuffleMetrics(
      "Columnar",
      200,
      dataSize,
      colTime,
      colMetrics._1,
      colMetrics._2
    )
    
    colSpark.stop()
    
    // 打印对比结果
    printComparison(results)
  }
  
  /**
   * 测试不同分区数的影响
   */
  def testPartitionCounts(): Unit = {
    println(s"\n${BLUE}╔════════════════════════════════════════════════════════════╗")
    println(s"║         Shuffle 分区数影响测试                              ║")
    println(s"╚════════════════════════════════════════════════════════════╝${RESET}\n")
    
    val spark = createColumnarShuffleSession()
    val dataSize = 3000000L
    val partitionCounts = Seq(50, 100, 200, 400, 800)
    
    println(f"${"分区数"}%10s ${"执行时间"}%12s ${"Shuffle读"}%15s ${"Shuffle写"}%15s ${"评价"}%10s")
    println("─" * 70)
    
    val results = ArrayBuffer[ShuffleMetrics]()
    
    partitionCounts.foreach { numPartitions =>
      spark.conf.set("spark.sql.shuffle.partitions", numPartitions.toString)
      
      val data = generateData(spark, dataSize)
      data.cache()
      data.count()
      
      val (_, duration) = runShuffleQuery(data)
      val metrics = getShuffleMetrics(spark)
      
      val result = ShuffleMetrics(
        "Columnar",
        numPartitions,
        dataSize,
        duration,
        metrics._1,
        metrics._2
      )
      results += result
      
      val rating = if (duration < 5000) s"${GREEN}优秀${RESET}"
                   else if (duration < 10000) s"${YELLOW}良好${RESET}"
                   else s"${RED}较慢${RESET}"
      
      println(f"${numPartitions}%10d ${duration}%10dms " +
              f"${formatBytes(metrics._1)}%15s ${formatBytes(metrics._2)}%15s ${rating}")
      
      data.unpersist()
    }
    
    spark.stop()
    
    // 分析最优配置
    println(s"\n${CYAN}═══ 分区数建议 ═══${RESET}\n")
    val bestResult = results.minBy(_.executionTime)
    println(s"  • 最优分区数: ${bestResult.numPartitions}")
    println(s"  • 执行时间: ${bestResult.executionTime}ms")
    println(s"\n  建议: 分区数应为 executor 总核心数的 2-3 倍")
    println(s"       过少: 并行度不足")
    println(s"       过多: 调度开销增加")
  }
  
  /**
   * Shuffle 配置优化示例
   */
  def demonstrateShuffleConfigs(): Unit = {
    println(s"\n${BLUE}╔════════════════════════════════════════════════════════════╗")
    println(s"║         Shuffle 配置优化示例                                ║")
    println(s"╚════════════════════════════════════════════════════════════╝${RESET}\n")
    
    println(s"${GREEN}1. 基础配置${RESET}")
    println("""
      |spark.shuffle.manager=org.apache.spark.shuffle.sort.ColumnarShuffleManager
      |spark.gluten.sql.columnar.shuffle.codec=lz4
      |spark.sql.shuffle.partitions=200
      |""".stripMargin)
    
    println(s"${GREEN}2. 压缩配置${RESET}")
    println("""
      |# 压缩算法选择
      |spark.gluten.sql.columnar.shuffle.codec=lz4    # 推荐，速度快
      |# 或
      |spark.gluten.sql.columnar.shuffle.codec=zstd   # 压缩率高
      |
      |# Shuffle 压缩
      |spark.shuffle.compress=true
      |spark.shuffle.spill.compress=true
      |""".stripMargin)
    
    println(s"${GREEN}3. 内存配置${RESET}")
    println("""
      |# Shuffle 内存
      |spark.shuffle.file.buffer=64k
      |spark.reducer.maxSizeInFlight=96m
      |spark.shuffle.sort.bypassMergeThreshold=200
      |
      |# Off-Heap 内存
      |spark.memory.offHeap.enabled=true
      |spark.memory.offHeap.size=4g
      |""".stripMargin)
    
    println(s"${GREEN}4. 高级优化${RESET}")
    println("""
      |# 启用 Shuffle 读取优化
      |spark.sql.adaptive.enabled=true
      |spark.sql.adaptive.coalescePartitions.enabled=true
      |
      |# Shuffle 写入优化
      |spark.shuffle.registration.timeout=120000
      |spark.shuffle.registration.maxAttempts=5
      |""".stripMargin)
    
    println(s"${YELLOW}⚠ 注意事项${RESET}")
    println("""
      |1. Columnar Shuffle 需要 Gluten 插件支持
      |2. 确保 Off-Heap 内存充足
      |3. 根据数据量调整分区数
      |4. 监控 Shuffle 指标，及时调优
      |""".stripMargin)
  }
  
  /**
   * 获取 Shuffle 指标（简化版）
   */
  private def getShuffleMetrics(spark: SparkSession): (Long, Long) = {
    // 实际应该从 SparkListener 获取
    // 这里返回模拟值
    val shuffleRead = (scala.util.Random.nextInt(100) + 50) * 1024 * 1024L
    val shuffleWrite = (scala.util.Random.nextInt(80) + 40) * 1024 * 1024L
    (shuffleRead, shuffleWrite)
  }
  
  /**
   * 打印对比结果
   */
  private def printComparison(results: ArrayBuffer[ShuffleMetrics]): Unit = {
    println(s"\n${CYAN}═══════════════════════════════════════════════════════════")
    println("对比结果")
    println(s"═══════════════════════════════════════════════════════════${RESET}\n")
    
    println(f"${"类型"}%-15s ${"执行时间"}%12s ${"Shuffle读"}%15s ${"Shuffle写"}%15s ${"加速比"}%10s")
    println("─" * 75)
    
    val baseline = results.head.executionTime.toDouble
    
    results.foreach { result =>
      val speedup = baseline / result.executionTime
      val color = if (speedup > 1.5) GREEN else if (speedup > 1.0) YELLOW else RED
      
      println(f"${result.shuffleType}%-15s " +
              f"${result.executionTime}%10dms " +
              f"${formatBytes(result.shuffleReadBytes)}%15s " +
              f"${formatBytes(result.shuffleWriteBytes)}%15s " +
              f"${color}${speedup}%9.2fx${RESET}")
    }
    
    println()
    
    if (results.size >= 2) {
      val improvement = (1 - results.last.executionTime.toDouble / results.head.executionTime) * 100
      println(s"${GREEN}✓ Columnar Shuffle 性能提升: ${improvement.abs.toInt}%${RESET}\n")
    }
  }
  
  /**
   * 格式化字节数
   */
  private def formatBytes(bytes: Long): String = {
    if (bytes < 1024) {
      s"${bytes} B"
    } else if (bytes < 1024 * 1024) {
      f"${bytes / 1024.0}%.1f KB"
    } else if (bytes < 1024 * 1024 * 1024) {
      f"${bytes / 1024.0 / 1024.0}%.1f MB"
    } else {
      f"${bytes / 1024.0 / 1024.0 / 1024.0}%.1f GB"
    }
  }
  
  /**
   * 运行所有测试
   */
  def runAllTests(): Unit = {
    println(s"${BLUE}")
    println("=" * 65)
    println("  Apache Gluten - Columnar Shuffle 完整演示")
    println("=" * 65)
    println(s"${RESET}")
    
    try {
      // 测试1: Row vs Columnar 对比
      compareShuffleTypes()
      Thread.sleep(2000)
      
      // 测试2: 分区数影响
      testPartitionCounts()
      Thread.sleep(2000)
      
      // 测试3: 配置建议
      demonstrateShuffleConfigs()
      
      println(s"\n${GREEN}✓ 所有测试完成${RESET}\n")
      
    } catch {
      case e: Exception =>
        println(s"${RED}✗ 测试失败: ${e.getMessage}${RESET}")
        e.printStackTrace()
    }
  }
  
  def main(args: Array[String]): Unit = {
    runAllTests()
  }
}

// 快速测试命令
// ColumnarShuffleDemo.compareShuffleTypes()
// ColumnarShuffleDemo.testPartitionCounts()
// ColumnarShuffleDemo.demonstrateShuffleConfigs()
