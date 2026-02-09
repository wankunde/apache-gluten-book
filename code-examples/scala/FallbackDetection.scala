/**
 * Apache Gluten Fallback 自动检测工具
 * 
 * 功能：
 * 1. 自动检测查询中的 Fallback 位置
 * 2. 统计 Fallback 比例和类型
 * 3. 分析 Fallback 原因
 * 4. 提供优化建议
 * 
 * 相关章节：第9章 - Fallback 机制
 * 
 * 使用：
 * ```bash
 * spark-shell --jars /path/to/gluten-*.jar
 * :load code-examples/scala/FallbackDetection.scala
 * 
 * val detector = new FallbackDetector(spark)
 * detector.analyzeQuery("SELECT * FROM table WHERE col > 100")
 * detector.generateReport()
 * ```
 */

package org.apache.gluten.examples

import org.apache.spark.sql.{SparkSession, DataFrame}
import org.apache.spark.sql.execution.SparkPlan
import org.apache.spark.sql.functions._
import scala.collection.mutable.{ArrayBuffer, HashMap}

class FallbackDetector(spark: SparkSession) {
  
  // ANSI 颜色
  val RESET = "\u001B[0m"
  val RED = "\u001B[31m"
  val GREEN = "\u001B[32m"
  val YELLOW = "\u001B[33m"
  val BLUE = "\u001B[34m"
  val CYAN = "\u001B[36m"
  
  case class FallbackInfo(
    operatorName: String,
    reason: String,
    location: String,
    suggestion: String
  )
  
  private val fallbackRecords = ArrayBuffer[FallbackInfo]()
  private val queryStats = HashMap[String, Int]()
  
  /**
   * 分析执行计划，检测 Fallback
   */
  def analyzePlan(plan: SparkPlan): Unit = {
    plan.foreach { p =>
      val className = p.getClass.getSimpleName
      
      // 检测 ColumnarToRow (C2R) 和 RowToColumnar (R2C)
      if (className.contains("ColumnarToRow")) {
        val reason = detectC2RReason(p)
        fallbackRecords += FallbackInfo(
          operatorName = className,
          reason = reason,
          location = p.toString.take(100),
          suggestion = getSuggestionForC2R(reason)
        )
        queryStats("c2r_count") = queryStats.getOrElse("c2r_count", 0) + 1
      }
      
      if (className.contains("RowToColumnar")) {
        fallbackRecords += FallbackInfo(
          operatorName = className,
          reason = "从行格式转为列格式",
          location = p.toString.take(100),
          suggestion = "检查上游算子是否支持列式执行"
        )
        queryStats("r2c_count") = queryStats.getOrElse("r2c_count", 0) + 1
      }
      
      // 检测其他 Fallback 标志
      if (className.contains("Fallback")) {
        fallbackRecords += FallbackInfo(
          operatorName = className,
          reason = "算子不支持 Gluten 加速",
          location = p.toString.take(100),
          suggestion = "检查 Gluten 兼容性列表"
        )
        queryStats("fallback_count") = queryStats.getOrElse("fallback_count", 0) + 1
      }
      
      // 统计算子类型
      queryStats("total_operators") = queryStats.getOrElse("total_operators", 0) + 1
    }
  }
  
  /**
   * 检测 ColumnarToRow 的原因
   */
  private def detectC2RReason(operator: SparkPlan): String = {
    val opString = operator.toString
    
    if (opString.contains("UDF")) {
      "UDF 不支持列式执行"
    } else if (opString.contains("Window")) {
      "窗口函数可能不支持"
    } else if (opString.contains("Sort")) {
      "排序算子 Fallback"
    } else if (opString.contains("Aggregate")) {
      "聚合算子 Fallback"
    } else if (opString.contains("Join")) {
      "Join 算子 Fallback"
    } else {
      "下游算子需要行格式数据"
    }
  }
  
  /**
   * 根据原因提供建议
   */
  private def getSuggestionForC2R(reason: String): String = reason match {
    case r if r.contains("UDF") => 
      "使用 Velox UDF 或 ClickHouse UDF 替代 Scala/Java UDF"
    case r if r.contains("窗口") => 
      "升级到支持窗口函数的 Gluten 版本"
    case r if r.contains("排序") => 
      "检查排序列数据类型，某些类型可能不支持"
    case r if r.contains("聚合") => 
      "检查聚合函数，使用 Gluten 支持的函数"
    case r if r.contains("Join") => 
      "检查 Join 类型，BroadcastHashJoin 通常支持更好"
    case _ => 
      "参考 Gluten 文档，检查算子兼容性"
  }
  
  /**
   * 分析 SQL 查询
   */
  def analyzeQuery(sql: String): Unit = {
    println(s"\n${CYAN}[分析查询] ${sql}${RESET}\n")
    
    val df = spark.sql(sql)
    val plan = df.queryExecution.executedPlan
    
    analyzePlan(plan)
  }
  
  /**
   * 分析 DataFrame
   */
  def analyzeDataFrame(df: DataFrame): Unit = {
    val plan = df.queryExecution.executedPlan
    analyzePlan(plan)
  }
  
  /**
   * 生成检测报告
   */
  def generateReport(): Unit = {
    println(s"\n${BLUE}╔════════════════════════════════════════════════════════════╗")
    println(s"║         Gluten Fallback 检测报告                            ║")
    println(s"╚════════════════════════════════════════════════════════════╝${RESET}\n")
    
    // 统计信息
    val totalOps = queryStats.getOrElse("total_operators", 0)
    val c2rCount = queryStats.getOrElse("c2r_count", 0)
    val r2cCount = queryStats.getOrElse("r2c_count", 0)
    val fallbackCount = queryStats.getOrElse("fallback_count", 0)
    val totalFallback = c2rCount + r2cCount + fallbackCount
    
    println(s"${CYAN}═══ 统计概览 ═══${RESET}\n")
    println(f"  总算子数: ${totalOps}%d")
    println(f"  Fallback 总数: ${totalFallback}%d")
    println(f"  - C2R (ColumnarToRow): ${c2rCount}%d")
    println(f"  - R2C (RowToColumnar): ${r2cCount}%d")
    println(f"  - 其他 Fallback: ${fallbackCount}%d")
    
    if (totalOps > 0) {
      val fallbackRatio = (totalFallback.toDouble / totalOps * 100)
      val color = if (fallbackRatio < 10) GREEN 
                  else if (fallbackRatio < 30) YELLOW 
                  else RED
      println(f"  Fallback 比例: ${color}${fallbackRatio}%.1f%%${RESET}")
    }
    
    // 详细信息
    if (fallbackRecords.nonEmpty) {
      println(s"\n${CYAN}═══ Fallback 详情 ═══${RESET}\n")
      
      fallbackRecords.zipWithIndex.foreach { case (fb, idx) =>
        println(s"${YELLOW}[${idx + 1}] ${fb.operatorName}${RESET}")
        println(s"  原因: ${fb.reason}")
        println(s"  位置: ${fb.location}")
        println(s"  ${GREEN}建议: ${fb.suggestion}${RESET}")
        println()
      }
    } else {
      println(s"\n${GREEN}✓ 未检测到 Fallback，查询完全在 Native 引擎执行！${RESET}\n")
    }
    
    // 综合建议
    printOptimizationSuggestions(totalFallback, totalOps)
  }
  
  /**
   * 打印优化建议
   */
  private def printOptimizationSuggestions(fallbackCount: Int, totalOps: Int): Unit = {
    println(s"${CYAN}═══ 优化建议 ═══${RESET}\n")
    
    if (fallbackCount == 0) {
      println(s"${GREEN}✓ 查询已充分优化，无需调整${RESET}\n")
      return
    }
    
    val ratio = fallbackCount.toDouble / totalOps
    
    if (ratio > 0.5) {
      println(s"${RED}⚠ 严重: Fallback 比例过高 (>${50}%)${RESET}")
      println("""
        |建议采取以下措施：
        |1. 检查是否使用了不支持的 UDF
        |2. 审查复杂的嵌套查询，简化逻辑
        |3. 升级到最新版本的 Gluten
        |4. 考虑切换到兼容性更好的后端 (Velox vs ClickHouse)
        |5. 拆分复杂查询为多个简单查询
        |""".stripMargin)
    } else if (ratio > 0.2) {
      println(s"${YELLOW}⚠ 警告: Fallback 比例较高 (>20%)${RESET}")
      println("""
        |建议检查：
        |1. 使用 Gluten 原生 UDF 替代自定义 UDF
        |2. 避免使用不支持的算子
        |3. 检查数据类型，某些类型可能不支持
        |""".stripMargin)
    } else {
      println(s"${GREEN}✓ Fallback 比例在可接受范围内${RESET}")
      println("""
        |继续优化：
        |1. 关注 Fallback 最多的算子类型
        |2. 参考 Gluten 最佳实践文档
        |""".stripMargin)
    }
  }
  
  /**
   * 重置检测器
   */
  def reset(): Unit = {
    fallbackRecords.clear()
    queryStats.clear()
  }
  
  /**
   * 导出报告为 JSON
   */
  def exportToJson(): String = {
    val json = new StringBuilder()
    json.append("{\n")
    json.append(s"""  "total_operators": ${queryStats.getOrElse("total_operators", 0)},\n""")
    json.append(s"""  "c2r_count": ${queryStats.getOrElse("c2r_count", 0)},\n""")
    json.append(s"""  "r2c_count": ${queryStats.getOrElse("r2c_count", 0)},\n""")
    json.append(s"""  "fallback_count": ${queryStats.getOrElse("fallback_count", 0)},\n""")
    json.append("""  "fallbacks": [\n""")
    
    fallbackRecords.zipWithIndex.foreach { case (fb, idx) =>
      json.append(s"""    {\n""")
      json.append(s"""      "operator": "${fb.operatorName}",\n""")
      json.append(s"""      "reason": "${fb.reason}",\n""")
      json.append(s"""      "suggestion": "${fb.suggestion}"\n""")
      json.append(s"""    }${if (idx < fallbackRecords.size - 1) "," else ""}\n""")
    }
    
    json.append("""  ]\n""")
    json.append("}")
    json.toString
  }
}

/**
 * 示例用法
 */
object FallbackDetectionDemo {
  
  def createTestData(spark: SparkSession): Unit = {
    import spark.implicits._
    
    // 创建测试表
    val data = spark.range(10000)
      .select(
        col("id"),
        (rand() * 100).cast("int").as("category"),
        (rand() * 1000).cast("double").as("amount"),
        concat(lit("user_"), col("id")).as("user_name")
      )
    
    data.createOrReplaceTempView("transactions")
  }
  
  def runExamples(spark: SparkSession): Unit = {
    createTestData(spark)
    
    val detector = new FallbackDetector(spark)
    
    println(s"\n${detector.BLUE}═══════════════════════════════════════════════════════")
    println("  Gluten Fallback 检测示例")
    println(s"═══════════════════════════════════════════════════════${detector.RESET}\n")
    
    // 示例1: 简单查询（预期无 Fallback）
    println(s"${detector.YELLOW}[示例1] 简单过滤和聚合${detector.RESET}")
    detector.analyzeQuery("""
      SELECT category, COUNT(*), SUM(amount)
      FROM transactions
      WHERE amount > 500
      GROUP BY category
    """)
    detector.generateReport()
    detector.reset()
    
    Thread.sleep(2000)
    
    // 示例2: 包含 UDF 的查询（预期有 Fallback）
    println(s"\n${detector.YELLOW}[示例2] 使用 UDF 的查询${detector.RESET}")
    spark.udf.register("custom_udf", (x: Int) => x * 2)
    detector.analyzeQuery("""
      SELECT category, custom_udf(category) as doubled
      FROM transactions
    """)
    detector.generateReport()
    detector.reset()
    
    Thread.sleep(2000)
    
    // 示例3: 复杂 Join 查询
    println(s"\n${detector.YELLOW}[示例3] Join 查询${detector.RESET}")
    createTestData(spark)
    spark.sql("CREATE OR REPLACE TEMP VIEW transactions2 AS SELECT * FROM transactions")
    
    detector.analyzeQuery("""
      SELECT t1.category, COUNT(DISTINCT t1.user_name)
      FROM transactions t1
      JOIN transactions2 t2 ON t1.category = t2.category
      WHERE t1.amount > t2.amount
      GROUP BY t1.category
    """)
    detector.generateReport()
    
    // 导出 JSON
    println(s"\n${detector.CYAN}═══ JSON 导出 ═══${detector.RESET}\n")
    println(detector.exportToJson())
  }
  
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder()
      .appName("Fallback Detection Demo")
      .master("local[*]")
      .config("spark.plugins", "org.apache.gluten.GlutenPlugin")
      .config("spark.gluten.enabled", "true")
      .getOrCreate()
    
    spark.sparkContext.setLogLevel("WARN")
    
    try {
      runExamples(spark)
    } finally {
      spark.stop()
    }
  }
}

// 快速使用
// val detector = new FallbackDetector(spark)
// detector.analyzeQuery("SELECT * FROM table")
// detector.generateReport()
