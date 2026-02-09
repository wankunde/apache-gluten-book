// 文件名：GlutenBenchmark.scala
// 用途：性能基准测试（Scala 版本）

import org.apache.spark.sql.SparkSession
import scala.collection.mutable

object GlutenBenchmark {
  
  case class BenchmarkResult(queryName: String, time: Double)
  
  def runBenchmark(useGluten: Boolean, numIterations: Int = 3): Map[String, Double] = {
    val appName = if (useGluten) "Benchmark with Gluten" else "Benchmark without Gluten"
    val builder = SparkSession.builder()
      .appName(appName)
      .master("local[*]")
    
    val spark = if (useGluten) {
      val glutenJar = sys.env.getOrElse("GLUTEN_JAR",
        throw new RuntimeException("请设置 GLUTEN_JAR 环境变量"))
      
      builder
        .config("spark.plugins", "org.apache.gluten.GlutenPlugin")
        .config("spark.memory.offHeap.enabled", "true")
        .config("spark.memory.offHeap.size", "2g")
        .config("spark.shuffle.manager", "org.apache.spark.shuffle.sort.ColumnarShuffleManager")
        .config("spark.driver.extraClassPath", glutenJar)
        .config("spark.executor.extraClassPath", glutenJar)
        .getOrCreate()
    } else {
      builder.getOrCreate()
    }
    
    try {
      val dataPath = sys.env.getOrElse("DATA_PATH", "data/test_data.parquet")
      val df = spark.read.parquet(dataPath)
      df.createOrReplaceTempView("test_table")
      
      // 定义测试查询
      val queries = Map(
        "简单聚合" -> 
          """
            |SELECT category, COUNT(*), AVG(value1) 
            |FROM test_table 
            |GROUP BY category
          """.stripMargin,
        
        "过滤+聚合" -> 
          """
            |SELECT category, COUNT(*), SUM(value2) 
            |FROM test_table 
            |WHERE value1 > 500 
            |GROUP BY category
          """.stripMargin,
        
        "复杂聚合" -> 
          """
            |SELECT 
            |    category,
            |    COUNT(*) as cnt,
            |    AVG(value1) as avg1,
            |    AVG(value2) as avg2,
            |    SUM(value3) as sum3,
            |    MAX(value1) as max1,
            |    MIN(value2) as min2
            |FROM test_table
            |WHERE value1 > 300 AND value2 < 700
            |GROUP BY category
            |HAVING cnt > 100000
          """.stripMargin
      )
      
      val results = mutable.Map[String, Double]()
      
      // 运行每个查询
      for ((queryName, sql) <- queries) {
        val times = mutable.ArrayBuffer[Double]()
        
        // 预热
        spark.sql(sql).count()
        
        // 多次运行取平均值
        for (_ <- 0 until numIterations) {
          val start = System.currentTimeMillis()
          spark.sql(sql).count()
          val elapsed = (System.currentTimeMillis() - start) / 1000.0
          times += elapsed
        }
        
        val avgTime = times.sum / times.length
        results(queryName) = avgTime
      }
      
      results.toMap
      
    } finally {
      spark.stop()
    }
  }
  
  def main(args: Array[String]): Unit = {
    println("=" * 70)
    println("Gluten vs 原生 Spark 性能对比 (Scala)")
    println("=" * 70)
    println()
    
    // 运行不使用 Gluten 的基准测试
    println("第1步: 运行原生 Spark 基准测试...")
    val vanillaResults = runBenchmark(useGluten = false)
    
    println("\n第2步: 运行 Gluten 基准测试...")
    val glutenResults = runBenchmark(useGluten = true)
    
    // 显示结果
    println("\n" + "=" * 70)
    println("性能对比结果")
    println("=" * 70)
    println()
    println(f"${"查询"}%-20s ${"原生Spark"}%-15s ${"Gluten"}%-15s ${"加速比"}%-10s")
    println("-" * 70)
    
    var totalVanilla = 0.0
    var totalGluten = 0.0
    
    for (queryName <- vanillaResults.keys) {
      val vanillaTime = vanillaResults(queryName)
      val glutenTime = glutenResults(queryName)
      val speedup = vanillaTime / glutenTime
      
      totalVanilla += vanillaTime
      totalGluten += glutenTime
      
      println(f"$queryName%-20s ${vanillaTime}%10.2fs    ${glutenTime}%10.2fs    ${speedup}%6.2fx")
    }
    
    println("-" * 70)
    val overallSpeedup = totalVanilla / totalGluten
    println(f"${"总计"}%-20s ${totalVanilla}%10.2fs    ${totalGluten}%10.2fs    ${overallSpeedup}%6.2fx")
    println()
    println("=" * 70)
    
    if (overallSpeedup > 1.5) {
      println("✅ Gluten 显著提升性能！")
    } else if (overallSpeedup > 1.1) {
      println("✅ Gluten 有性能提升")
    } else {
      println("⚠️  性能提升不明显，请检查配置")
    }
  }
}
