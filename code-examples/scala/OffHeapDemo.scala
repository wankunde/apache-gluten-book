/**
 * Apache Gluten 堆外内存演示
 * 
 * 功能：
 * 1. 对比 On-Heap vs Off-Heap 内存使用
 * 2. 演示不同 Off-Heap 大小的性能影响
 * 3. 模拟内存溢出场景
 * 4. Off-Heap 内存配置最佳实践
 * 
 * 相关章节：第6章 - 内存管理
 * 
 * 编译运行：
 * ```bash
 * spark-shell --jars /path/to/gluten-*.jar \
 *   --conf spark.memory.offHeap.enabled=true \
 *   --conf spark.memory.offHeap.size=2g
 * 
 * :load code-examples/scala/OffHeapDemo.scala
 * OffHeapDemo.runAllTests()
 * ```
 */

package org.apache.gluten.examples

import org.apache.spark.sql.{SparkSession, DataFrame}
import org.apache.spark.sql.functions._
import scala.collection.mutable.ArrayBuffer

object OffHeapDemo {
  
  // ANSI 颜色代码
  val RESET = "\u001B[0m"
  val RED = "\u001B[31m"
  val GREEN = "\u001B[32m"
  val YELLOW = "\u001B[33m"
  val BLUE = "\u001B[34m"
  val CYAN = "\u001B[36m"
  
  /**
   * 创建启用 Gluten 和 Off-Heap 的 Spark Session
   */
  def createGlutenSession(offHeapSize: String): SparkSession = {
    val builder = SparkSession.builder()
      .appName(s"Gluten Off-Heap Demo - ${offHeapSize}")
      .master("local[4]")
      
      // Gluten 配置
      .config("spark.plugins", "org.apache.gluten.GlutenPlugin")
      .config("spark.gluten.enabled", "true")
      .config("spark.gluten.sql.columnar.backend.lib", "velox")
      
      // Off-Heap 内存配置
      .config("spark.memory.offHeap.enabled", "true")
      .config("spark.memory.offHeap.size", offHeapSize)
      
      // Executor 内存配置
      .config("spark.executor.memory", "4g")
      .config("spark.executor.memoryOverhead", "1g")
      
      // 关闭自适应查询以便对比
      .config("spark.sql.adaptive.enabled", "false")
    
    val spark = builder.getOrCreate()
    spark.sparkContext.setLogLevel("WARN")
    spark
  }
  
  /**
   * 创建标准 Spark Session（不启用 Off-Heap）
   */
  def createStandardSession(): SparkSession = {
    val builder = SparkSession.builder()
      .appName("Standard Spark - Heap Only")
      .master("local[4]")
      
      // 禁用 Off-Heap
      .config("spark.memory.offHeap.enabled", "false")
      .config("spark.executor.memory", "4g")
      .config("spark.sql.adaptive.enabled", "false")
    
    val spark = builder.getOrCreate()
    spark.sparkContext.setLogLevel("WARN")
    spark
  }
  
  /**
   * 生成测试数据
   */
  def generateTestData(spark: SparkSession, numRows: Long): DataFrame = {
    import spark.implicits._
    
    spark.range(numRows)
      .select(
        col("id"),
        (rand() * 1000).cast("int").as("value1"),
        (rand() * 1000).cast("int").as("value2"),
        concat(lit("user_"), col("id")).as("user_name"),
        (rand() * 100).cast("double").as("score")
      )
  }
  
  /**
   * 执行聚合查询并测量性能
   */
  def runAggregationQuery(spark: SparkSession, df: DataFrame): (Long, Map[String, Any]) = {
    val startTime = System.currentTimeMillis()
    
    val result = df
      .groupBy(col("value1") % 100)
      .agg(
        count("*").as("count"),
        avg("score").as("avg_score"),
        sum("value2").as("sum_value2"),
        max("score").as("max_score")
      )
      .orderBy(col("count").desc)
    
    // 触发执行
    val count = result.count()
    
    val endTime = System.currentTimeMillis()
    val duration = endTime - startTime
    
    // 获取内存使用情况
    val runtime = Runtime.getRuntime
    val memoryInfo = Map(
      "used_heap_mb" -> ((runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024),
      "total_heap_mb" -> (runtime.totalMemory() / 1024 / 1024),
      "max_heap_mb" -> (runtime.maxMemory() / 1024 / 1024)
    )
    
    (duration, memoryInfo)
  }
  
  /**
   * 对比 Heap vs Off-Heap 性能
   */
  def compareHeapVsOffHeap(): Unit = {
    println(s"\n${CYAN}╔════════════════════════════════════════════════════════════╗")
    println(s"║         On-Heap vs Off-Heap 性能对比                       ║")
    println(s"╚════════════════════════════════════════════════════════════╝${RESET}\n")
    
    val dataSize = 10000000L // 1000万行
    val results = ArrayBuffer[(String, Long, Map[String, Any])]()
    
    // 测试1: 标准 Spark (仅 Heap)
    println(s"${YELLOW}[测试1] 标准 Spark (仅 On-Heap)${RESET}")
    val standardSpark = createStandardSession()
    val standardData = generateTestData(standardSpark, dataSize)
    standardData.cache()
    standardData.count() // 预热
    
    val (standardTime, standardMemory) = runAggregationQuery(standardSpark, standardData)
    results += (("Standard Spark", standardTime, standardMemory))
    
    println(s"${GREEN}  ✓ 执行时间: ${standardTime}ms${RESET}")
    println(s"  • Heap 使用: ${standardMemory("used_heap_mb")}MB / ${standardMemory("total_heap_mb")}MB")
    
    standardSpark.stop()
    Thread.sleep(2000) // 等待资源释放
    
    // 测试2: Gluten + 小 Off-Heap (512MB)
    println(s"\n${YELLOW}[测试2] Gluten + 小 Off-Heap (512MB)${RESET}")
    val smallOffHeapSpark = createGlutenSession("512m")
    val smallOffHeapData = generateTestData(smallOffHeapSpark, dataSize)
    smallOffHeapData.cache()
    smallOffHeapData.count()
    
    val (smallOffHeapTime, smallOffHeapMemory) = runAggregationQuery(smallOffHeapSpark, smallOffHeapData)
    results += (("Gluten 512MB Off-Heap", smallOffHeapTime, smallOffHeapMemory))
    
    println(s"${GREEN}  ✓ 执行时间: ${smallOffHeapTime}ms${RESET}")
    println(s"  • Heap 使用: ${smallOffHeapMemory("used_heap_mb")}MB")
    
    smallOffHeapSpark.stop()
    Thread.sleep(2000)
    
    // 测试3: Gluten + 大 Off-Heap (2GB)
    println(s"\n${YELLOW}[测试3] Gluten + 大 Off-Heap (2GB)${RESET}")
    val largeOffHeapSpark = createGlutenSession("2g")
    val largeOffHeapData = generateTestData(largeOffHeapSpark, dataSize)
    largeOffHeapData.cache()
    largeOffHeapData.count()
    
    val (largeOffHeapTime, largeOffHeapMemory) = runAggregationQuery(largeOffHeapSpark, largeOffHeapData)
    results += (("Gluten 2GB Off-Heap", largeOffHeapTime, largeOffHeapMemory))
    
    println(s"${GREEN}  ✓ 执行时间: ${largeOffHeapTime}ms${RESET}")
    println(s"  • Heap 使用: ${largeOffHeapMemory("used_heap_mb")}MB")
    
    largeOffHeapSpark.stop()
    
    // 显示对比结果
    println(s"\n${CYAN}═══════════════════════════════════════════════════════════")
    println("性能对比汇总")
    println(s"═══════════════════════════════════════════════════════════${RESET}\n")
    
    println(f"${"配置"}%-25s ${"执行时间"}%12s ${"Heap使用"}%12s ${"加速比"}%10s")
    println("─" * 65)
    
    val baselineTime = results(0)._2.toDouble
    results.foreach { case (name, time, memory) =>
      val speedup = baselineTime / time
      val color = if (speedup > 1.5) GREEN else if (speedup > 1.2) YELLOW else RED
      println(f"${name}%-25s ${time}%10dms ${memory("used_heap_mb")}%9dMB ${color}${speedup}%9.2fx${RESET}")
    }
    
    println()
  }
  
  /**
   * 演示不同数据量下的 Off-Heap 内存使用
   */
  def testOffHeapScalability(): Unit = {
    println(s"\n${CYAN}╔════════════════════════════════════════════════════════════╗")
    println(s"║         Off-Heap 可扩展性测试                               ║")
    println(s"╚════════════════════════════════════════════════════════════╝${RESET}\n")
    
    val spark = createGlutenSession("2g")
    val dataSizes = Seq(1000000L, 5000000L, 10000000L, 20000000L) // 100万 到 2000万
    
    println(f"${"数据行数"}%12s ${"执行时间"}%12s ${"Heap使用"}%12s ${"状态"}%10s")
    println("─" * 55)
    
    dataSizes.foreach { size =>
      try {
        val df = generateTestData(spark, size)
        df.cache()
        df.count()
        
        val (duration, memory) = runAggregationQuery(spark, df)
        
        val status = if (duration < 10000) s"${GREEN}✓ 正常${RESET}" 
                     else if (duration < 30000) s"${YELLOW}⚠ 较慢${RESET}"
                     else s"${RED}✗ 慢${RESET}"
        
        println(f"${size}%12d ${duration}%10dms ${memory("used_heap_mb")}%9dMB ${status}")
        
        df.unpersist()
      } catch {
        case e: Exception =>
          println(f"${size}%12d ${RED}✗ 内存溢出: ${e.getMessage}${RESET}")
      }
    }
    
    spark.stop()
    println()
  }
  
  /**
   * 模拟内存溢出场景
   */
  def simulateOOM(): Unit = {
    println(s"\n${CYAN}╔════════════════════════════════════════════════════════════╗")
    println(s"║         内存溢出场景模拟                                    ║")
    println(s"╚════════════════════════════════════════════════════════════╝${RESET}\n")
    
    println(s"${YELLOW}⚠ 注意: 此测试可能导致 JVM 内存溢出${RESET}\n")
    
    // 创建一个 Off-Heap 较小的 Spark Session
    val spark = createGlutenSession("256m")
    
    println("尝试处理超出 Off-Heap 容量的数据...")
    
    try {
      val largeData = generateTestData(spark, 50000000L) // 5000万行
      largeData.cache()
      
      val result = largeData
        .groupBy((col("id") % 1000).as("group_id"))
        .agg(
          count("*").as("count"),
          sum("value1").as("sum1"),
          sum("value2").as("sum2"),
          avg("score").as("avg_score")
        )
      
      val count = result.count()
      println(s"${GREEN}✓ 成功处理 ${count} 个分组${RESET}")
      
    } catch {
      case e: OutOfMemoryError =>
        println(s"${RED}✗ 捕获内存溢出错误:${RESET}")
        println(s"   ${e.getMessage}")
        println(s"\n${YELLOW}建议:${RESET}")
        println("   1. 增加 spark.memory.offHeap.size")
        println("   2. 减少数据分区大小")
        println("   3. 增加 executor 数量")
        
      case e: Exception =>
        println(s"${RED}✗ 其他错误: ${e.getMessage}${RESET}")
    } finally {
      spark.stop()
    }
  }
  
  /**
   * Off-Heap 配置建议
   */
  def printConfigRecommendations(): Unit = {
    println(s"\n${CYAN}╔════════════════════════════════════════════════════════════╗")
    println(s"║         Off-Heap 配置最佳实践                               ║")
    println(s"╚════════════════════════════════════════════════════════════╝${RESET}\n")
    
    println(s"${GREEN}1. 基本配置${RESET}")
    println("""
      |spark.memory.offHeap.enabled=true
      |spark.memory.offHeap.size=2g
      |""".stripMargin)
    
    println(s"${GREEN}2. Off-Heap 大小建议${RESET}")
    println("""
      |• 小数据集 (<10GB):   512MB - 1GB
      |• 中数据集 (10-100GB): 2GB - 4GB
      |• 大数据集 (>100GB):   4GB - 8GB
      |
      |经验公式: Off-Heap = Executor Memory × 60%
      |""".stripMargin)
    
    println(s"${GREEN}3. 内存分配示例${RESET}")
    println("""
      |场景1: 轻量级 ETL
      |  spark.executor.memory=4g
      |  spark.memory.offHeap.size=2g
      |
      |场景2: 复杂分析查询
      |  spark.executor.memory=8g
      |  spark.memory.offHeap.size=5g
      |
      |场景3: 大规模数据处理
      |  spark.executor.memory=16g
      |  spark.memory.offHeap.size=10g
      |""".stripMargin)
    
    println(s"${YELLOW}⚠ 注意事项${RESET}")
    println("""
      |1. Off-Heap 不包含在 executor.memory 中
      |2. 需要确保容器有足够内存 (executor.memory + offHeap.size + overhead)
      |3. Kubernetes: 设置 memoryOverhead 至少为 offHeap.size 的 120%
      |4. 过大的 Off-Heap 可能导致 GC 压力减小但内存利用率降低
      |""".stripMargin)
  }
  
  /**
   * 运行所有测试
   */
  def runAllTests(): Unit = {
    println(s"${BLUE}")
    println("=" * 65)
    println("  Apache Gluten - Off-Heap 内存完整演示")
    println("=" * 65)
    println(s"${RESET}")
    
    try {
      // 测试1: Heap vs Off-Heap 对比
      compareHeapVsOffHeap()
      Thread.sleep(3000)
      
      // 测试2: 可扩展性测试
      testOffHeapScalability()
      Thread.sleep(3000)
      
      // 测试3: 配置建议
      printConfigRecommendations()
      
      // 可选: OOM 模拟 (注释掉以避免崩溃)
      // println(s"\n${YELLOW}[可选] 是否运行 OOM 模拟? (可能导致进程崩溃)${RESET}")
      // simulateOOM()
      
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
// OffHeapDemo.compareHeapVsOffHeap()
// OffHeapDemo.testOffHeapScalability()
// OffHeapDemo.printConfigRecommendations()
