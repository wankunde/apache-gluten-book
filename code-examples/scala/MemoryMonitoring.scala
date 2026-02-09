/**
 * MemoryMonitoring.scala
 * 
 * Gluten 内存监控和分析工具
 * 对应书籍第6章：内存管理
 * 
 * 功能：
 * 1. 监控 Off-Heap 内存使用情况
 * 2. 分析内存池分配
 * 3. 检测内存泄漏
 * 4. 对比不同内存配置的性能
 */

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import org.apache.spark.memory.MemoryManager
import org.apache.spark.storage.StorageLevel
import java.lang.management.ManagementFactory
import scala.collection.JavaConverters._

object MemoryMonitoring {
  
  // ANSI 颜色代码
  val RED = "\u001B[31m"
  val GREEN = "\u001B[32m"
  val YELLOW = "\u001B[33m"
  val BLUE = "\u001B[34m"
  val RESET = "\u001B[35m"
  
  def main(args: Array[String]): Unit = {
    println(s"${BLUE}=" * 80 + RESET)
    println(s"${BLUE}Gluten 内存监控演示${RESET}")
    println(s"${BLUE}=" * 80 + RESET)
    
    // 创建 SparkSession with Gluten
    val spark = createGlutenSpark()
    
    // 打印初始内存状态
    printMemoryStatus(spark, "初始状态")
    
    // 示例1：监控查询执行过程中的内存
    println(s"\n${GREEN}>>> 示例1：查询执行过程中的内存监控${RESET}")
    monitorQueryMemory(spark)
    
    // 示例2：内存池分析
    println(s"\n${GREEN}>>> 示例2：内存池详细分析${RESET}")
    analyzeMemoryPools(spark)
    
    // 示例3：内存泄漏检测
    println(s"\n${GREEN}>>> 示例3：内存泄漏检测${RESET}")
    detectMemoryLeaks(spark)
    
    // 示例4：不同数据量下的内存使用
    println(s"\n${GREEN}>>> 示例4：不同数据量下的内存使用${RESET}")
    compareDataSizes(spark)
    
    // 打印最终内存状态
    printMemoryStatus(spark, "最终状态")
    
    // 生成内存使用报告
    generateMemoryReport(spark)
    
    spark.stop()
  }
  
  /**
   * 创建启用 Gluten 的 SparkSession
   */
  def createGlutenSpark(): SparkSession = {
    SparkSession.builder()
      .appName("Gluten Memory Monitoring")
      .master("local[*]")
      .config("spark.plugins", "org.apache.gluten.GlutenPlugin")
      .config("spark.gluten.enabled", "true")
      .config("spark.gluten.sql.columnar.backend.lib", "velox")
      // 内存配置
      .config("spark.driver.memory", "4g")
      .config("spark.executor.memory", "8g")
      .config("spark.executor.memoryOverhead", "2g")
      .config("spark.memory.offHeap.enabled", "true")
      .config("spark.memory.offHeap.size", "4g")
      // 内存隔离模式
      .config("spark.gluten.memory.isolation", "false")
      .config("spark.shuffle.manager", "org.apache.spark.shuffle.sort.ColumnarShuffleManager")
      .getOrCreate()
  }
  
  /**
   * 打印内存状态
   */
  def printMemoryStatus(spark: SparkSession, label: String): Unit = {
    println(s"\n${BLUE}【$label】${RESET}")
    
    // JVM 内存
    val runtime = Runtime.getRuntime
    val maxMemory = runtime.maxMemory() / (1024 * 1024)
    val totalMemory = runtime.totalMemory() / (1024 * 1024)
    val freeMemory = runtime.freeMemory() / (1024 * 1024)
    val usedMemory = totalMemory - freeMemory
    
    println(s"\n${YELLOW}JVM 内存:${RESET}")
    println(f"  最大内存 (Max):    $maxMemory%8d MB")
    println(f"  已分配 (Total):    $totalMemory%8d MB")
    println(f"  已使用 (Used):     $usedMemory%8d MB")
    println(f"  空闲 (Free):       $freeMemory%8d MB")
    println(f"  使用率:            ${usedMemory * 100.0 / maxMemory}%5.1f%%")
    
    // Off-Heap 内存（从配置获取）
    val offHeapSize = spark.conf.get("spark.memory.offHeap.size")
    val offHeapEnabled = spark.conf.get("spark.memory.offHeap.enabled")
    
    println(s"\n${YELLOW}Off-Heap 内存:${RESET}")
    println(s"  启用状态: $offHeapEnabled")
    println(s"  配置大小: $offHeapSize")
    
    // Gluten 特定配置
    val isolation = spark.conf.getOption("spark.gluten.memory.isolation")
      .getOrElse("未配置")
    
    println(s"\n${YELLOW}Gluten 内存配置:${RESET}")
    println(s"  内存隔离模式: $isolation")
    
    // 内存管理器统计
    try {
      val sc = spark.sparkContext
      val conf = sc.getConf
      val storageMemory = conf.get("spark.storage.memoryFraction", "0.6")
      val executionMemory = conf.get("spark.memory.fraction", "0.6")
      
      println(s"\n${YELLOW}内存分配策略:${RESET}")
      println(s"  Storage 内存比例: $storageMemory")
      println(s"  Execution 内存比例: $executionMemory")
    } catch {
      case e: Exception => 
        println(s"  无法获取内存管理器信息: ${e.getMessage}")
    }
  }
  
  /**
   * 监控查询执行过程中的内存
   */
  def monitorQueryMemory(spark: SparkSession): Unit = {
    import spark.implicits._
    
    // 创建测试数据
    println("生成测试数据...")
    val dataSize = 1000000
    val df = spark.range(0, dataSize)
      .select(
        col("id"),
        (col("id") % 1000).as("category"),
        (rand() * 10000).as("value"),
        concat(lit("item_"), col("id")).as("name")
      )
    
    df.createOrReplaceTempView("test_data")
    
    // 记录查询前的内存
    val memBefore = getMemorySnapshot()
    println(s"\n查询前内存: ${memBefore._1} MB (JVM), ${memBefore._2} MB (Off-Heap)")
    
    // 执行复杂查询
    val query = """
      SELECT 
        category,
        COUNT(*) as count,
        AVG(value) as avg_value,
        MAX(value) as max_value,
        MIN(value) as min_value,
        STDDEV(value) as stddev_value
      FROM test_data
      WHERE value > 100
      GROUP BY category
      HAVING COUNT(*) > 10
      ORDER BY count DESC
    """
    
    println("\n执行查询...")
    val startTime = System.currentTimeMillis()
    val result = spark.sql(query)
    val count = result.count()  // 触发执行
    val endTime = System.currentTimeMillis()
    
    // 记录查询后的内存
    val memAfter = getMemorySnapshot()
    println(s"查询后内存: ${memAfter._1} MB (JVM), ${memAfter._2} MB (Off-Heap)")
    
    // 计算差异
    val jvmDiff = memAfter._1 - memBefore._1
    val offHeapDiff = memAfter._2 - memBefore._2
    
    println(s"\n${YELLOW}内存变化:${RESET}")
    println(f"  JVM 内存增长:      $jvmDiff%+8d MB")
    println(f"  Off-Heap 增长:     $offHeapDiff%+8d MB")
    println(f"  执行时间:          ${endTime - startTime}%8d ms")
    println(f"  结果行数:          $count%8d")
    
    // 显示前几行结果
    println(s"\n查询结果（前10行）:")
    result.show(10, truncate = false)
  }
  
  /**
   * 分析内存池
   */
  def analyzeMemoryPools(spark: SparkSession): Unit = {
    val memoryMXBeans = ManagementFactory.getMemoryPoolMXBeans.asScala
    
    println(s"\n${YELLOW}内存池详情:${RESET}")
    println(s"${"池名称":-40s} ${"类型":-15s} ${"已用":-10s} ${"最大":-10s} ${"使用率":-10s}")
    println("-" * 90)
    
    for (pool <- memoryMXBeans) {
      val name = pool.getName
      val poolType = pool.getType.toString
      val usage = pool.getUsage
      
      if (usage != null) {
        val used = usage.getUsed / (1024 * 1024)
        val max = usage.getMax / (1024 * 1024)
        val percent = if (max > 0) (used * 100.0 / max) else 0.0
        
        val percentStr = if (max > 0) f"$percent%6.2f%%" else "N/A"
        val maxStr = if (max > 0) f"$max%8d MB" else "无限制"
        
        // 根据使用率着色
        val color = if (percent > 80) RED 
                   else if (percent > 60) YELLOW 
                   else GREEN
        
        println(f"$color${name}%-40s${RESET} ${poolType}%-15s ${used}%8d MB  $maxStr  $percentStr")
      }
    }
  }
  
  /**
   * 检测内存泄漏
   */
  def detectMemoryLeaks(spark: SparkSession): Unit = {
    import spark.implicits._
    
    println("\n运行内存泄漏检测...")
    
    val iterations = 10
    val memorySnapshots = scala.collection.mutable.ArrayBuffer[(Long, Long)]()
    
    for (i <- 1 to iterations) {
      // 创建并处理数据
      val df = spark.range(0, 100000).select(
        col("id"),
        (col("id") * 2).as("doubled"),
        concat(lit("item_"), col("id")).as("name")
      )
      
      // 执行一些操作
      val count = df.filter(col("doubled") > 1000).count()
      
      // 记录内存快照
      System.gc()  // 建议 GC
      Thread.sleep(100)  // 等待 GC 完成
      val snapshot = getMemorySnapshot()
      memorySnapshots += snapshot
      
      if (i % 2 == 0) {
        println(f"  迭代 $i%2d: JVM=${snapshot._1}%6d MB, Off-Heap=${snapshot._2}%6d MB")
      }
    }
    
    // 分析趋势
    if (memorySnapshots.size >= 2) {
      val firstSnap = memorySnapshots.head
      val lastSnap = memorySnapshots.last
      
      val jvmGrowth = lastSnap._1 - firstSnap._1
      val offHeapGrowth = lastSnap._2 - firstSnap._2
      
      println(s"\n${YELLOW}泄漏检测结果:${RESET}")
      println(f"  JVM 内存增长:      $jvmGrowth%+6d MB")
      println(f"  Off-Heap 增长:     $offHeapGrowth%+6d MB")
      
      // 判断是否可能泄漏
      if (jvmGrowth > 100 || offHeapGrowth > 100) {
        println(s"\n  ${RED}⚠️  警告: 检测到可能的内存泄漏！${RESET}")
        println(s"  建议：检查是否正确释放资源")
      } else {
        println(s"\n  ${GREEN}✅ 内存使用正常${RESET}")
      }
    }
  }
  
  /**
   * 对比不同数据量下的内存使用
   */
  def compareDataSizes(spark: SparkSession): Unit = {
    import spark.implicits._
    
    val sizes = Seq(10000, 50000, 100000, 500000, 1000000)
    
    println(s"\n${"数据量":-15s} ${"执行时间":-15s} ${"内存使用":-15s} ${"峰值内存":-15s}")
    println("-" * 65)
    
    for (size <- sizes) {
      System.gc()
      Thread.sleep(500)
      
      val memBefore = getMemorySnapshot()
      val startTime = System.currentTimeMillis()
      
      // 执行查询
      val df = spark.range(0, size).select(
        col("id"),
        (col("id") % 100).as("group"),
        (rand() * 1000).as("value")
      )
      
      val result = df.groupBy("group")
        .agg(
          count("*").as("count"),
          avg("value").as("avg_value"),
          max("value").as("max_value")
        )
        .count()
      
      val endTime = System.currentTimeMillis()
      val memAfter = getMemorySnapshot()
      
      val duration = endTime - startTime
      val memUsed = memAfter._1 - memBefore._1
      
      println(f"${size}%-15d ${duration}%-15d ms ${memUsed}%-15d MB ${memAfter._1}%-15d MB")
    }
  }
  
  /**
   * 获取内存快照
   */
  def getMemorySnapshot(): (Long, Long) = {
    val runtime = Runtime.getRuntime
    val jvmUsed = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    
    // Off-Heap 内存（简化示例，实际需要通过 JMX 或其他方式获取）
    val offHeapUsed = 0L  // 实际应用中需要实现
    
    (jvmUsed, offHeapUsed)
  }
  
  /**
   * 生成内存使用报告
   */
  def generateMemoryReport(spark: SparkSession): Unit = {
    println(s"\n${BLUE}=" * 80 + RESET)
    println(s"${BLUE}内存使用报告${RESET}")
    println(s"${BLUE}=" * 80 + RESET)
    
    // 获取最终统计
    val runtime = Runtime.getRuntime
    val maxMemory = runtime.maxMemory() / (1024 * 1024)
    val totalMemory = runtime.totalMemory() / (1024 * 1024)
    val usedMemory = (totalMemory - runtime.freeMemory()) / (1024 * 1024)
    
    println(s"\n${YELLOW}总体统计:${RESET}")
    println(f"  配置的最大内存:    $maxMemory%8d MB")
    println(f"  实际使用内存:      $usedMemory%8d MB")
    println(f"  内存利用率:        ${usedMemory * 100.0 / maxMemory}%5.1f%%")
    
    println(s"\n${YELLOW}优化建议:${RESET}")
    val utilizationRate = usedMemory * 100.0 / maxMemory
    
    if (utilizationRate > 90) {
      println(s"  ${RED}⚠️  内存使用率过高（>90%）${RESET}")
      println("  建议：增加 executor 内存或 off-heap 内存")
    } else if (utilizationRate < 30) {
      println(s"  ${YELLOW}ℹ️  内存使用率较低（<30%）${RESET}")
      println("  建议：可以适当减少内存配置以节省资源")
    } else {
      println(s"  ${GREEN}✅ 内存配置合理${RESET}")
    }
    
    println(s"\n${YELLOW}推荐配置:${RESET}")
    val recommendedOffHeap = (usedMemory * 1.5).toLong
    println(s"  spark.memory.offHeap.size = ${recommendedOffHeap}m")
    println(s"  spark.executor.memoryOverhead = ${(recommendedOffHeap * 0.15).toLong}m")
    
    println(s"\n${BLUE}=" * 80 + RESET)
  }
}

/**
 * 使用说明：
 * 
 * 1. 编译：
 *    scalac -classpath "$SPARK_HOME/jars/*:$GLUTEN_JAR" MemoryMonitoring.scala
 * 
 * 2. 打包：
 *    jar cvf memory-monitoring.jar MemoryMonitoring*.class
 * 
 * 3. 运行：
 *    spark-submit \
 *      --class MemoryMonitoring \
 *      --master local[*] \
 *      --driver-memory 4g \
 *      --executor-memory 8g \
 *      --conf spark.memory.offHeap.enabled=true \
 *      --conf spark.memory.offHeap.size=4g \
 *      --conf spark.driver.extraClassPath=$GLUTEN_JAR \
 *      --conf spark.executor.extraClassPath=$GLUTEN_JAR \
 *      memory-monitoring.jar
 * 
 * 4. 高级监控（使用 JMX）：
 *    添加 JVM 参数：
 *    --conf spark.driver.extraJavaOptions="-Dcom.sun.management.jmxremote ..."
 *    
 *    使用 jconsole 或 VisualVM 连接监控
 * 
 * 预期输出：
 *    - 初始和最终内存状态对比
 *    - 查询执行过程中的内存变化
 *    - 内存池详细信息
 *    - 内存泄漏检测结果
 *    - 不同数据量的内存使用对比
 *    - 内存优化建议
 */
