/**
 * Apache Gluten - Velox Cache 使用演示
 * 
 * 功能：
 * 1. 演示 Velox File Cache 的使用
 * 2. 对比 Cache 启用前后的性能
 * 3. Cache 命中率监控
 * 4. Cache 配置优化建议
 * 
 * 相关章节：第11章 - Velox 后端
 * 
 * 使用：
 * ```bash
 * spark-shell --jars /path/to/gluten-*.jar
 * :load code-examples/scala/VeloxCacheDemo.scala
 * VeloxCacheDemo.runAllTests()
 * ```
 */

package org.apache.gluten.examples

import org.apache.spark.sql.{SparkSession, DataFrame}
import org.apache.spark.sql.functions._
import scala.collection.mutable.ArrayBuffer

object VeloxCacheDemo {
  
  // ANSI 颜色
  val RESET = "\u001B[0m"
  val RED = "\u001B[31m"
  val GREEN = "\u001B[32m"
  val YELLOW = "\u001B[33m"
  val BLUE = "\u001B[34m"
  val CYAN = "\u001B[36m"
  
  case class CacheTestResult(
    testName: String,
    cacheEnabled: Boolean,
    firstRunTime: Long,
    secondRunTime: Long,
    cacheHitRate: Double
  )
  
  /**
   * 创建启用 Velox Cache 的 Spark Session
   */
  def createCacheEnabledSession(cacheSize: String = "10g"): SparkSession = {
    println(s"${CYAN}[创建] Velox Cache Session (Cache: ${cacheSize})${RESET}")
    
    SparkSession.builder()
      .appName("Velox Cache Demo")
      .master("local[4]")
      
      // Gluten 基础配置
      .config("spark.plugins", "org.apache.gluten.GlutenPlugin")
      .config("spark.gluten.enabled", "true")
      .config("spark.gluten.sql.columnar.backend.lib", "velox")
      
      // Off-Heap 内存
      .config("spark.memory.offHeap.enabled", "true")
      .config("spark.memory.offHeap.size", "4g")
      
      // 启用 Velox File Cache
      .config("spark.gluten.sql.columnar.backend.velox.cacheEnabled", "true")
      .config("spark.gluten.sql.columnar.backend.velox.cacheSize", cacheSize)
      .config("spark.gluten.sql.columnar.backend.velox.cachePath", "/tmp/velox-cache")
      .config("spark.gluten.sql.columnar.backend.velox.cachePageSize", "1048576")  // 1MB
      
      // Cache 策略
      .config("spark.gluten.sql.columnar.backend.velox.cacheEvictionPolicy", "LRU")
      
      .getOrCreate()
  }
  
  /**
   * 创建不启用 Cache 的 Session
   */
  def createNoCacheSession(): SparkSession = {
    println(s"${CYAN}[创建] No Cache Session${RESET}")
    
    SparkSession.builder()
      .appName("No Cache Demo")
      .master("local[4]")
      .config("spark.plugins", "org.apache.gluten.GlutenPlugin")
      .config("spark.gluten.enabled", "true")
      .config("spark.gluten.sql.columnar.backend.lib", "velox")
      .config("spark.memory.offHeap.enabled", "true")
      .config("spark.memory.offHeap.size", "4g")
      
      // 禁用 Cache
      .config("spark.gluten.sql.columnar.backend.velox.cacheEnabled", "false")
      
      .getOrCreate()
  }
  
  /**
   * 生成测试数据并保存为 Parquet
   */
  def prepareTestData(spark: SparkSession, path: String, numRows: Long): Unit = {
    import spark.implicits._
    
    println(s"${YELLOW}生成测试数据: ${numRows} 行 -> ${path}${RESET}")
    
    spark.range(numRows)
      .select(
        col("id"),
        (col("id") % 100).as("category"),
        (rand() * 1000).cast("int").as("value"),
        concat(lit("data_"), col("id")).as("description"),
        (rand() * 100).cast("double").as("score")
      )
      .write
      .mode("overwrite")
      .parquet(path)
    
    println(s"${GREEN}  ✓ 数据生成完成${RESET}")
  }
  
  /**
   * 运行查询并测量时间
   */
  def runQuery(spark: SparkSession, dataPath: String): Long = {
    val startTime = System.currentTimeMillis()
    
    val df = spark.read.parquet(dataPath)
    
    val result = df
      .filter(col("value") > 500)
      .groupBy("category")
      .agg(
        count("*").as("count"),
        avg("value").as("avg_value"),
        max("score").as("max_score")
      )
      .orderBy(col("count").desc)
    
    // 触发执行
    val count = result.count()
    
    val endTime = System.currentTimeMillis()
    endTime - startTime
  }
  
  /**
   * 对比 Cache 启用前后的性能
   */
  def compareCachePerformance(): Unit = {
    println(s"\n${BLUE}╔════════════════════════════════════════════════════════════╗")
    println(s"║         Velox Cache 性能对比                                ║")
    println(s"╚════════════════════════════════════════════════════════════╝${RESET}\n")
    
    val dataPath = "/tmp/velox-cache-test-data"
    val dataSize = 5000000L  // 500万行
    
    val results = ArrayBuffer[CacheTestResult]()
    
    // 准备测试数据
    val prepSpark = SparkSession.builder()
      .appName("Prep")
      .master("local[4]")
      .getOrCreate()
    prepareTestData(prepSpark, dataPath, dataSize)
    prepSpark.stop()
    Thread.sleep(1000)
    
    // 测试1: 不启用 Cache
    println(s"\n${YELLOW}[测试1] 不启用 Cache${RESET}")
    val noCacheSpark = createNoCacheSession()
    
    val noCache1 = runQuery(noCacheSpark, dataPath)
    println(s"  第1次运行: ${noCache1}ms")
    
    val noCache2 = runQuery(noCacheSpark, dataPath)
    println(s"  第2次运行: ${noCache2}ms")
    
    results += CacheTestResult(
      "No Cache",
      false,
      noCache1,
      noCache2,
      0.0
    )
    
    noCacheSpark.stop()
    Thread.sleep(2000)
    
    // 测试2: 启用 Cache
    println(s"\n${YELLOW}[测试2] 启用 Velox Cache (10GB)${RESET}")
    val cacheSpark = createCacheEnabledSession("10g")
    
    val cache1 = runQuery(cacheSpark, dataPath)
    println(s"  第1次运行 (Cold Cache): ${cache1}ms")
    
    val cache2 = runQuery(cacheSpark, dataPath)
    println(s"  第2次运行 (Warm Cache): ${cache2}ms")
    
    val hitRate = if (cache1 > 0) (1.0 - cache2.toDouble / cache1) * 100 else 0.0
    
    results += CacheTestResult(
      "With Cache",
      true,
      cache1,
      cache2,
      hitRate
    )
    
    cacheSpark.stop()
    
    // 打印对比结果
    printCacheComparison(results)
  }
  
  /**
   * 测试不同 Cache 大小的影响
   */
  def testCacheSizes(): Unit = {
    println(s"\n${BLUE}╔════════════════════════════════════════════════════════════╗")
    println(s"║         Cache 大小影响测试                                  ║")
    println(s"╚════════════════════════════════════════════════════════════╝${RESET}\n")
    
    val dataPath = "/tmp/velox-cache-size-test"
    val dataSize = 3000000L
    
    // 准备数据
    val prepSpark = SparkSession.builder()
      .appName("Prep")
      .master("local[4]")
      .getOrCreate()
    prepareTestData(prepSpark, dataPath, dataSize)
    prepSpark.stop()
    Thread.sleep(1000)
    
    val cacheSizes = Seq("1g", "5g", "10g", "20g")
    
    println(f"${"Cache大小"}%12s ${"第1次(ms)"}%12s ${"第2次(ms)"}%12s ${"提升"}%10s ${"评价"}%10s")
    println("─" * 65)
    
    cacheSizes.foreach { cacheSize =>
      val spark = createCacheEnabledSession(cacheSize)
      
      val time1 = runQuery(spark, dataPath)
      val time2 = runQuery(spark, dataPath)
      
      val improvement = (1.0 - time2.toDouble / time1) * 100
      val rating = if (improvement > 50) s"${GREEN}优秀${RESET}"
                   else if (improvement > 30) s"${YELLOW}良好${RESET}"
                   else s"${RED}一般${RESET}"
      
      println(f"${cacheSize}%12s ${time1}%10dms ${time2}%10dms " +
              f"${improvement}%9.1f%% ${rating}")
      
      spark.stop()
      Thread.sleep(1000)
    }
    
    println(s"\n${CYAN}═══ Cache 大小建议 ═══${RESET}\n")
    println("""
      |• 小数据集 (<10GB):   1-5GB Cache
      |• 中数据集 (10-100GB): 5-20GB Cache
      |• 大数据集 (>100GB):   20-50GB Cache
      |
      |经验公式: Cache Size = 热数据大小 × 1.5
      |""".stripMargin)
  }
  
  /**
   * Cache 配置优化建议
   */
  def printCacheConfigGuide(): Unit = {
    println(s"\n${BLUE}╔════════════════════════════════════════════════════════════╗")
    println(s"║         Velox Cache 配置指南                                ║")
    println(s"╚════════════════════════════════════════════════════════════╝${RESET}\n")
    
    println(s"${GREEN}1. 基础配置${RESET}")
    println("""
      |# 启用 Cache
      |spark.gluten.sql.columnar.backend.velox.cacheEnabled=true
      |
      |# Cache 大小（根据可用内存）
      |spark.gluten.sql.columnar.backend.velox.cacheSize=10737418240  # 10GB
      |
      |# Cache 路径（建议使用 SSD）
      |spark.gluten.sql.columnar.backend.velox.cachePath=/mnt/ssd/velox-cache
      |""".stripMargin)
    
    println(s"${GREEN}2. 高级配置${RESET}")
    println("""
      |# Cache 页面大小
      |spark.gluten.sql.columnar.backend.velox.cachePageSize=1048576  # 1MB
      |
      |# 淘汰策略（LRU 或 LFU）
      |spark.gluten.sql.columnar.backend.velox.cacheEvictionPolicy=LRU
      |
      |# Cache 预热
      |spark.gluten.sql.columnar.backend.velox.cacheWarmupEnabled=true
      |""".stripMargin)
    
    println(s"${GREEN}3. 性能调优${RESET}")
    println("""
      |# Cache 并发访问
      |spark.gluten.sql.columnar.backend.velox.cacheConcurrency=16
      |
      |# Cache 刷新间隔
      |spark.gluten.sql.columnar.backend.velox.cacheRefreshInterval=3600  # 1小时
      |""".stripMargin)
    
    println(s"${YELLOW}⚠ 最佳实践${RESET}")
    println("""
      |1. Cache 路径使用 SSD 或 NVMe 磁盘
      |2. Cache 大小不超过可用内存的 50%
      |3. 监控 Cache 命中率，目标 >70%
      |4. 定期清理 Cache 目录
      |5. 生产环境建议启用 Cache 预热
      |""".stripMargin)
    
    println(s"${CYAN}═══ Cache 监控指标 ═══${RESET}\n")
    println("""
      |关键指标：
      |  • Cache 命中率 (Hit Rate)
      |  • Cache 使用率 (Usage)
      |  • 淘汰次数 (Evictions)
      |  • 加载时间 (Load Time)
      |
      |监控命令：
      |  # 查看 Cache 统计
      |  spark.sql("SHOW CACHE STATS")
      |  
      |  # 清空 Cache
      |  spark.sql("CLEAR CACHE")
      |""".stripMargin)
  }
  
  /**
   * 打印 Cache 对比结果
   */
  private def printCacheComparison(results: ArrayBuffer[CacheTestResult]): Unit = {
    println(s"\n${CYAN}═══════════════════════════════════════════════════════════")
    println("Cache 性能对比")
    println(s"═══════════════════════════════════════════════════════════${RESET}\n")
    
    println(f"${"配置"}%-15s ${"第1次"}%10s ${"第2次"}%10s ${"提升"}%12s ${"命中率"}%10s")
    println("─" * 65)
    
    results.foreach { result =>
      val improvement = if (result.firstRunTime > 0) {
        (1.0 - result.secondRunTime.toDouble / result.firstRunTime) * 100
      } else 0.0
      
      val color = if (improvement > 40) GREEN else if (improvement > 20) YELLOW else RED
      
      println(f"${result.testName}%-15s " +
              f"${result.firstRunTime}%8dms " +
              f"${result.secondRunTime}%8dms " +
              f"${color}${improvement}%10.1f%%${RESET} " +
              f"${result.cacheHitRate}%9.1f%%")
    }
    
    println()
    
    if (results.size >= 2) {
      val withCache = results.last
      val noCache = results.head
      
      val overall = (noCache.secondRunTime - withCache.secondRunTime).toDouble / noCache.secondRunTime * 100
      println(s"${GREEN}✓ Velox Cache 整体性能提升: ${overall.toInt}%${RESET}\n")
    }
  }
  
  /**
   * 运行所有测试
   */
  def runAllTests(): Unit = {
    println(s"${BLUE}")
    println("=" * 65)
    println("  Apache Gluten - Velox Cache 完整演示")
    println("=" * 65)
    println(s"${RESET}")
    
    try {
      // 测试1: Cache 性能对比
      compareCachePerformance()
      Thread.sleep(2000)
      
      // 测试2: Cache 大小影响
      testCacheSizes()
      Thread.sleep(2000)
      
      // 测试3: 配置指南
      printCacheConfigGuide()
      
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
// VeloxCacheDemo.compareCachePerformance()
// VeloxCacheDemo.testCacheSizes()
// VeloxCacheDemo.printCacheConfigGuide()
